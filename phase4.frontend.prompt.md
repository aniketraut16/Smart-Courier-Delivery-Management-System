# Prompt: Build `web-ui-service` — Standalone Thymeleaf Frontend (All Three Roles)

Copy everything below the line into your AI coding assistant, in a session with access to the actual repo (needs to read the DTOs *and* the README/API docs from `user-auth-service`, `booking-service`, `tracking-service`, and `notification-service` to mirror their exact shapes — it doesn't call those modules' code directly, just needs to see the field names and routes).


## Context — what already exists

Spring Boot 3 / Java 21 microservices project. Four backend services are fully built and running (User & Auth, Booking, Delivery & Tracking, Notification), sitting behind a basic API Gateway that currently acts as a pure proxy (no JWT handling at the gateway yet — that's a future change and **must not** alter any request/response shape this frontend relies on).

You are now building a **fifth, standalone module**: `web-ui-service` — a separate Spring Boot application with Thymeleaf, that is a *server-rendered client* of the existing REST APIs (called through the gateway), not a new backend service with its own database. It has no database of its own and no JPA.

**Do not modify any existing service.** This is a brand-new module only.

Base package: `com.couriersystem.courier.webui`

## 0. Module setup

New Maven module `web-ui-service`, `pom.xml` dependencies: `spring-boot-starter-web`, `spring-boot-starter-thymeleaf`, `spring-boot-starter-validation`, a JWT library for **decoding only** (e.g. `jjwt-api` + `jjwt-impl` + `jjwt-jackson`, or `java-jwt` — pick one, no signature verification needed client-side since the gateway/backend already verifies every call), Lombok. **Do not add** `spring-boot-starter-data-jpa`, any database driver, or `spring-boot-starter-security` — this module never touches a database directly and doesn't need full Spring Security (see Section 4).

`application.yml`:
```yaml
server:
  port: 8090
gateway:
  base-url: "http://localhost:8080"   # replace with the real running gateway address
app:
  jwt-cookie-name: "courier_jwt"
  cookie-secure: false   # set true in a prod profile — requires HTTPS
```

## 0.5. Read the real API contracts before writing anything

Before writing DTOs, routes, or a single controller: open and read `README.md` (and any `openapi.yaml`/Swagger UI export, if present) in each of `user-auth-service`, `booking-service`, `tracking-service`, and `notification-service`. Extract and note, for each service:
- Every route this frontend needs to call, with exact path, HTTP method, and whether it's behind the gateway with a path prefix (e.g. `/api/booking/...` vs `/api/...` — don't assume, confirm from the README/gateway route config).
- Every DTO field name and type used in requests/responses this frontend touches.
- Any enum value lists (roles, booking statuses, availability statuses) — get these from the source, not from memory or the earlier draft of this prompt.

If a route or field genuinely isn't documented anywhere and you have to infer it from controller code instead, that's fine — just record it in the "guessed fields" list requested at the end of this prompt. What's not fine is silently assuming a shape without checking.

## 1. Auth — JWT held client-side, in a cookie the server controls

The JWT is stored **in the browser**, not in server memory or a session. Specifically:

- On successful login, `web-ui-service` receives the token from `POST /api/auth/login` (via the gateway) and sets it in a cookie named per `app.jwt-cookie-name` with:
  - **`HttpOnly`** — page JavaScript can never read it. This is the one non-negotiable flag: storing a raw JWT somewhere JS can reach it (`localStorage`, `sessionStorage`, a non-HttpOnly cookie) means any XSS bug on the page can steal it. HttpOnly keeps it "in the browser" (satisfying the requirement that this module holds no server-side session state) while keeping it out of reach of script.
  - `SameSite=Lax` (CSRF mitigation for a cookie-based flow).
  - `Secure` when `app.cookie-secure=true` (enable in any profile served over HTTPS).
  - `Max-Age` matching the token's own expiry (decode the `exp` claim to set it).
- **No `HttpSession` is used anywhere in this module.** There is no server-side session store, no session-scoped bean holding user state across requests.
- A `JwtCookieFilter` (a `OncePerRequestFilter`, registered as a `@Bean` `FilterRegistrationBean`, or a `HandlerInterceptor` — either is fine, pick one) runs on every request, reads the cookie if present, decodes (does not re-verify signature — that's the backend's job on every actual API call) the JWT payload, and populates a **request-scoped** `CurrentUser` object (`@RequestScope` bean, or attach to the request as an attribute and expose via a `@ModelAttribute`-annotated method so every template can reach `currentUser.role` / `currentUser.fullName` without extra plumbing). This object exists only for the lifetime of the request — nothing is cached across requests server-side.
- `CurrentUser` needs `userId`, `role`, and ideally `email`/`fullName` from the token claims. Confirm in Section 0.5 whether the backend's JWT actually carries `fullName`/`email` claims. If it doesn't (tokens are often kept minimal — just `sub`/`userId` and `role`), set a **second, non-HttpOnly cookie** (e.g. `courier_display`) at login time containing only non-sensitive display fields (`fullName`, `email`) as a small JSON blob, populated from a one-time `GET /api/users/me` call right after login. This second cookie is for **display only** — never use it for any authorization decision; role-based route guarding always reads the role claim out of the HttpOnly JWT cookie, never the display cookie, since the display cookie is trivially editable by the client.
- `ApiClient` (Section 2) reads the raw token string from the JWT cookie on every outbound call — it never re-requests, re-derives, or caches it separately.
- **Logout** clears both cookies (sets `Max-Age=0`) and redirects to `/`. No session to invalidate.
- **Important framing:** this interceptor-level role check is a UX convenience (redirect a customer away from `/admin/**` before wasting a round trip), not the source of truth for security — the gateway/backend still authorizes every real API call independently and will return `401`/`403` regardless of what this module's redirect logic does. Say this explicitly in a code comment where the filter is defined, so nobody later assumes client-side role checks are the actual security boundary.

## 2. `ApiClient` (`client` package) — one shared HTTP client, all requests through the gateway

Use `RestTemplate` (a single `@Bean`, default config is fine) — not `WebClient` — since every controller here is a normal blocking Spring MVC controller.

Build one small wrapper class, `ApiClient`, with generic helper methods used by every page-specific client below:
```java
<T> T get(String path, String bearerToken, Class<T> responseType);
<T> T post(String path, String bearerToken, Object body, Class<T> responseType);
<T> T put(String path, String bearerToken, Object body, Class<T> responseType);
<T> T patch(String path, String bearerToken, Object body, Class<T> responseType);
```
Every call is prefixed with `gateway.base-url` from config. **Every call must catch `RestClientResponseException`** (thrown by `RestTemplate` on any 4xx/5xx), parse the response body as the backend's standard `ErrorResponse` shape (`timestamp`, `status`, `error`, `message`, `path` — recreate this DTO locally, confirming its exact fields from Section 0.5), and rethrow it wrapped as a local unchecked `ApiException(int statusCode, String message)`. On a `401` specifically, clear both auth cookies and redirect to `/login` with a flash message ("Your session expired, please log in again") instead of showing the error inline — this is the module's entire "token expired" handling, there is no refresh-token flow. Every controller below catches other `ApiException`s and shows `message` to the user via a **toast** (never a raw stack trace, never the gateway's raw JSON — see Section 8.4).

## 3. DTOs (`dto` package) — mirror the backend's shapes exactly, field for field

Using what you read in Step 0.5 (not guesses), create local copies of: `RegisterRequest`, `LoginRequest`, `AuthResponse`, `UserProfileResponse`, `UpdateAvailabilityRequest`, `CourierAvailabilityResponse`, `AddressDto`, `PackageDetailDto`, `BookingQuoteRequest`, `BookingQuoteResponse`, `CourierFareOption`, `CreateBookingRequest`, `BookingResponse`, `StatusUpdateRequest`, `StatusUpdateResponse`, `BookingTrackingResponse`.

Additionally create thin **form-backing objects** for Thymeleaf (`th:object`) where the real request DTO doesn't map cleanly to a single HTML form — e.g. `RegisterForm` (same fields as `RegisterRequest` plus a `confirmPassword` field with a matching-password check in the controller, not bean validation, since cross-field validation isn't a single annotation), and `BookingDetailsForm` (same fields as `BookingQuoteRequest`, reused for both the quote step and, combined with a selected `courierId`, to build the final `CreateBookingRequest`).

## 4. Auth guard — `HandlerInterceptor`, not Spring Security

Do **not** add `spring-boot-starter-security` to this module — full Spring Security is overkill for a cookie-presence + role check. Implement one `AuthInterceptor implements HandlerInterceptor`:
- Reads `CurrentUser` (populated by the `JwtCookieFilter` from Section 1) from the request.
- If absent and the requested path isn't `/login`, `/register`, or static resources → redirect to `/login`.
- If present but `role` doesn't match the path's required role prefix (`/customer/**` requires `CUSTOMER`, `/courier/**` requires `COURIER`, `/admin/**` requires `ADMIN`) → redirect to a generic `/access-denied` page.
Register it in a `WebMvcConfigurer`, applied to `/customer/**`, `/courier/**`, `/admin/**` (not to `/login`, `/register`, `/logout`, `/`, `/access-denied`, or static assets).

## 5. Post/Redirect/Get, flash messages, and where toasts fit in

Use the Post/Redirect/Get pattern everywhere a form submits (redirect after a successful `POST`, never render a result directly from the `POST` handler) so refreshing a result page never resubmits a form. Use `RedirectAttributes.addFlashAttribute("toast", new ToastMessage(type, message))` — a small local record with `type` (`SUCCESS`, `ERROR`, `INFO`) and `message` — for every confirmation and error. These render as **toasts**, not static banners (full spec in Section 8.4).

## 6. Shared layout

`templates/layout/base.html` — a Thymeleaf layout fragment (top nav showing the logged-in user's name/role with an SVG account icon, and a logout link when `CurrentUser` is present, or Login/Register links when not) that every other page extends via `th:replace`/`th:insert` (Thymeleaf 3 layout dialect, or plain fragment includes — pick one approach and use it consistently across every page). The layout also includes:
- `fragments/toast-container.html` — an empty container the toast script populates from any flash `toast` model attribute present on page load (Section 8.4).
- The shared `static/css/style.css` and a small `static/js/app.js` for toast rendering, the confirm modal, and submit-button loading state (Section 8.6, 8.7, 8.8).

## 7. Controllers and templates — exact pages, exact behavior

### Public pages (no auth required)

| Controller method | Path | Template | Behavior |
|---|---|---|---|
| GET | `/` | `home.html` | Simple landing page with links to Login/Register |
| GET | `/login` | `auth/login.html` | Empty `LoginRequest`-backed form |
| POST | `/login` | — | Calls `POST /api/auth/login` via `ApiClient`; on success, sets the JWT cookie (and the display cookie, per Section 1) from the `AuthResponse` (+ a follow-up `GET /api/users/me` call if display fields aren't in the token), redirects to `/customer/dashboard`, `/courier/dashboard`, or `/admin/dashboard` based on `role`. On failure, flash a toast error and redirect back to `/login`. |
| GET | `/register` | `auth/register.html` | `RegisterForm`-backed; conditionally shows `vehicleType`/`vehicleNumber` fields only when the role dropdown is `COURIER` (plain JS show/hide by `<select>` value — no framework needed); include an info bar above the courier-only fields explaining they're only needed for courier accounts |
| POST | `/register` | — | Validates `password == confirmPassword` in the controller (flash toast error + redirect back to `/register` if they don't match); calls `POST /api/auth/register`; on success, redirects to `/login` with a flash success toast telling the user to log in |
| POST | `/logout` | — | Clears both auth cookies, redirects to `/` |
| GET | `/access-denied` | `error/access-denied.html` | Friendly message with an icon, link back to the user's own dashboard if logged in (Section 8.9) |
| — | `/error` (Spring Boot's default handler, mapped) | `error/generic-error.html` | Friendly 404/500 pages — see Section 8.9 |

### Customer pages (`/customer/**`)

| Controller method | Path | Template | Behavior |
|---|---|---|---|
| GET | `/customer/dashboard` | `customer/dashboard.html` | Summary cards (Section 8.9) at the top, then calls `GET /api/bookings?page=0&size=10`, shows a summary table with status badges (Section 8.5) and a link to each booking, and a "New Booking" button |
| GET | `/customer/bookings` | `customer/bookings-list.html` | Full paginated list, `GET /api/bookings?status=&page=&size=` — status filter as a dropdown, page controls as simple prev/next links; empty state (Section 8.9) when there are zero bookings |
| GET | `/customer/bookings/{id}` | `customer/booking-detail.html` | Calls `GET /api/bookings/{id}` and `GET /api/tracking/bookings/{id}` (merge both into the view model); shows full address/package/fare info plus the tracking history as a vertical status timeline (Section 8.9); shows a "Cancel Booking" button only when `status` is `PLACED` or `ASSIGNED`, which opens the confirm modal (Section 8.7), not a browser `confirm()` |
| POST | `/customer/bookings/{id}/cancel` | — | Calls `POST /api/bookings/{id}/cancel`; flash success/error toast; redirect back to `/customer/bookings/{id}` |
| GET | `/customer/bookings/new` | `customer/booking-new.html` | Empty `BookingDetailsForm` (pickup address, drop address, package detail) |
| POST | `/customer/bookings/quote` | `customer/booking-quote-results.html` | Calls `POST /api/bookings/quote` with the submitted `BookingDetailsForm` mapped to a `BookingQuoteRequest`; **renders results directly from this POST (an intentional exception to the PRG rule in this one case, since the quote result itself is not a persisted resource** — there's nothing to redirect to); the rendered page shows `distanceKm` and a radio-button list of `CourierFareOption`s (courier name, vehicle type, fare) as selectable cards rather than a bare radio list, plus **hidden fields carrying the original address/package data forward** so the next submission doesn't need the user to re-enter anything; include an info bar noting the quote is based on current availability and may change if you wait too long to confirm |
| POST | `/customer/bookings/create` | — | Reads the hidden address/package fields plus the selected `courierId` from the submitted form, builds a `CreateBookingRequest`, calls `POST /api/bookings`; on success redirect to `/customer/bookings/{new id}` with a success toast; on `409` (courier no longer available) flash that specific error and redirect back to `/customer/bookings/new` so the customer restarts the quote (their entered address/package data is not preserved across this specific failure case — that's acceptable, note it in a comment, don't build a complex retry-preserving-state mechanism for this edge case) |

### Courier pages (`/courier/**`)

| Controller method | Path | Template | Behavior |
|---|---|---|---|
| GET | `/courier/dashboard` | `courier/dashboard.html` | Summary cards, then calls `GET /api/couriers/me/availability` and `GET /api/bookings?page=0&size=10` (courier's own, per existing role-scoped filtering on the backend); shows current availability status as a badge with a toggle form, and a list of assigned bookings with status badges and links |
| POST | `/courier/availability` | — | Calls `PUT /api/couriers/me/availability` with the submitted `AVAILABLE`/`OFFLINE` choice (never expose `BUSY` as an option in this form at all — the dropdown/radio only ever offers those two values; add a short info note explaining `BUSY` is set automatically by the system while a delivery is active); flash success/error toast; redirect to `/courier/dashboard` |
| GET | `/courier/bookings/{id}` | `courier/booking-detail.html` | Calls `GET /api/bookings/{id}` and `GET /api/tracking/bookings/{id}/history`; shows the same status timeline as the customer view, plus a status-update form offering only the single valid next status given the booking's current status (`ASSIGNED` → offer only `PICKED_UP`; `PICKED_UP` → offer only `IN_TRANSIT`; `IN_TRANSIT` → offer only `DELIVERED`; any other current status → no form, just show history) — compute this "next valid status" mapping in the controller, don't just dump all four options and rely on the backend to reject invalid ones |
| POST | `/courier/bookings/{id}/status` | — | Calls `POST /api/tracking/bookings/{id}/status` with the submitted status and optional note; flash success/error toast; redirect back to `/courier/bookings/{id}` |

### Admin pages (`/admin/**`)

| Controller method | Path | Template | Behavior |
|---|---|---|---|
| GET | `/admin/dashboard` | `admin/dashboard.html` | Simple landing page with a link to the user list |
| GET | `/admin/users` | `admin/users-list.html` | Calls `GET /api/admin/users?page=&size=`; paginated table of `UserProfileResponse` (name, email, role, account status, created date) with role shown as a small icon+label rather than plain text; prev/next page links |

## 8. Design & UX Direction

This is a **light-mode-only, modern-standard business tool**, not a marketing site — think the restraint of an internal ops dashboard (Linear, Stripe's dashboard, Notion), not a landing page. No dark mode toggle, no gradients-and-glow aesthetic, no illustration-heavy empty states. The goal is "clearly, deliberately designed" — not "maximal."

### 8.1 Palette (light mode only)
A small, named set — don't introduce colors outside this list:
- `--color-bg`: `#F8F9FB` (page background, very light neutral gray, not pure white — reduces eye strain on data-heavy pages)
- `--color-surface`: `#FFFFFF` (cards, tables, form panels)
- `--color-border`: `#E2E5EA`
- `--color-text-primary`: `#1A1D23`
- `--color-text-secondary`: `#5C6270`
- `--color-accent`: `#2F6FED` (primary buttons, links, active nav item — a single restrained blue, not a "startup gradient" hue)
- `--color-success`: `#1D8A5A` / background tint `#E8F6EF`
- `--color-warning`: `#B5750B` / background tint `#FDF3E3`
- `--color-danger`: `#C4302B` / background tint `#FBEAEA`
- `--color-info`: `#2F6FED` / background tint `#EAF1FE` (reuses the accent hue at low opacity, keeps the palette tight)

### 8.2 Typography
- One system-first font stack for everything: `-apple-system, "Segoe UI", Inter, Roboto, sans-serif` — no display/body pairing needed for a utility app like this, and no third-party font request (keeps the "no build tooling" constraint honest).
- Scale: `12px` (captions/meta), `14px` (body/table default), `16px` (form labels, base), `20px` (card/section headers), `28px` (page titles). Line height `1.5` for body text.
- Weight: `400` body, `600` headers and button labels — don't reach for more than two weights.

### 8.3 Icons — SVG only, never emoji
Use a single consistent icon set, inlined as SVG (either hand-picked inline `<svg>` snippets or a static icon sprite from a permissively-licensed set like **Lucide** — vendor a handful of the specific icons you need locally rather than pulling in an icon font or a JS icon library). Emoji characters (📦, ✅, 🚚, etc.) are not acceptable anywhere in the UI — they render inconsistently across OSes and read as unpolished. Reserve icons for places they carry real meaning, not as decoration on every line:
- Nav: dashboard (grid), bookings (package), tracking (map-pin), users (admin only), logout (arrow-out).
- Status badges (8.5) each get a small matching icon.
- Toasts (8.4) get a check/alert/info icon per type.
- Buttons that perform a specific recognizable action (Cancel Booking → X-circle, New Booking → plus) — not every button needs one.

### 8.4 Toasts — replace all static "alert banner" behavior
No `alert()`, `confirm()`, or `prompt()` anywhere in this module (see Section 9). All success/error/info flash messages render as **toasts**: a small stack in a fixed corner (top-right), auto-dismissing after ~4 seconds with a manual close (×), color-coded per `ToastMessage.type` per the palette above, with the matching icon from 8.3. Implementation: `app.js` reads a hidden `<div>` populated by Thymeleaf from the `toast` flash attribute on page load (if present) and pushes it into the toast stack — no polling, no WebSocket, this is a one-shot render-on-load, consistent with the server-rendered, no-JS-framework constraint.

### 8.5 Status badges
A small reusable Thymeleaf fragment `fragments/status-badge.html` taking a status string, rendering a pill with the matching color + icon:
- `PLACED` → info tint, clock icon
- `ASSIGNED` → info tint, user-check icon
- `PICKED_UP` → warning tint, package icon
- `IN_TRANSIT` → warning tint, truck icon
- `DELIVERED` → success tint, check-circle icon
- `CANCELLED` → danger tint, x-circle icon
- Courier availability: `AVAILABLE` → success, `BUSY` → warning, `OFFLINE` → neutral gray
Confirm the exact enum value spellings against Section 0.5 before hardcoding this mapping.

### 8.6 Info / note bars
A reusable `fragments/note.html` fragment (`type`: `info`/`warning`) for **persistent, inline context** — different from toasts, which are transient and tied to an action just taken. Use these anywhere a user might be confused about a rule the UI is enforcing but not fully explaining, e.g.:
- Register page: courier-only fields explanation (7, Register row).
- Availability toggle: why `BUSY` isn't a manual option.
- Quote results: quote freshness note.
- Booking detail (customer): why the Cancel button disappears after a certain status.
Keep these short — one or two sentences, not a paragraph.

### 8.7 Confirm modal — replaces native `confirm()`
One reusable modal component (plain HTML/CSS + a small `app.js` function `openConfirmModal(message, formIdToSubmit)`), used for the one genuinely destructive action in this app: **Cancel Booking**. The Cancel button doesn't submit directly — it opens the modal with the booking reference and a "This can't be undone" note; only the modal's own confirm button submits the real form. No other action in this app needs a confirm step (logging out, submitting a status update, etc. are all easily reversible or low-stakes, so don't over-apply this pattern).

### 8.8 Loading / double-submit prevention
On every form submit, disable the submit button and swap its label for a small inline spinner + "Saving…"/"Submitting…" (`app.js`, generic — attach to all `<form>` submit events, not per-page code). This is a real UX/correctness improvement, not just polish: without it, a slow network plus an impatient double-click can fire two `POST /api/bookings` calls.

### 8.9 A few things I added beyond the original spec
Called out explicitly, as requested, so you know what's genuinely new vs. what was already in scope:
1. **Dashboard summary cards** (customer & courier dashboards): 2–3 small stat cards above the booking list — e.g. "Active bookings," "In transit," "Delivered this month" for customers; "Assigned to you," "Current status" for couriers. Computed client-side from the already-fetched booking list (no new API calls) — gives an at-a-glance read instead of making the user parse a table first.
2. **Vertical status timeline** for tracking history, instead of a plain list of status-change rows — makes the delivery's progress visually scannable (a common, well-understood pattern for exactly this kind of data) rather than a flat log.
3. **Friendly 404/500 pages** (not in the original spec at all) with an icon, plain-language message, and a link back to the user's dashboard — a bare Whitelabel Error Page is one of the fastest ways an otherwise solid app reads as unfinished.
4. **Submit-button loading state** (8.8) — a real correctness fix (prevents duplicate bookings from double-clicks), not just cosmetic.
5. **Selectable fare-option cards** instead of bare radio buttons on the quote results page — same data, meaningfully easier to compare at a glance (courier name, vehicle icon, fare, all in one card) than a radio list with text next to it.

If any of these five feel like scope you'd rather cut for time, they're the ones to drop first — everything in Sections 1–7 is the actual functional spec and shouldn't be trimmed.

### 8.10 Responsiveness & accessibility baseline
Not a stretch goal — table stakes for "looks deliberately built":
- Layout uses simple flexbox/grid that reflows to a single column below ~640px (courier role in particular will often be used on a phone).
- Every interactive element has a visible keyboard focus state (don't strip the default outline without replacing it with an equally visible one).
- Every form input has a real `<label>` (not just a placeholder).
- Color is never the only signal — status badges and toasts pair color with an icon and text, per above.

## 9. What NOT to do

- Don't add Spring Security to this module — the `HandlerInterceptor` from Section 4 is deliberately sufficient and simpler for a server-rendered, cookie-based app like this.
- Don't use `HttpSession` anywhere in this module.
- Don't store the JWT in `localStorage`, `sessionStorage`, or any cookie without `HttpOnly` set — see Section 1 for why.
- Don't let this module talk directly to any backend service's port — every call goes through `gateway.base-url`, since that's the whole point of having a gateway.
- Don't build a JavaScript SPA layer, `fetch()`-based AJAX calls, or a REST API of this module's own — every page is a full server-rendered round trip. The toast/modal/loading-state JS in Section 8 is small, page-load-triggered UI glue, not an AJAX layer.
- Don't add refresh-token handling — if the JWT cookie is expired/invalid mid-use, a call will fail with `401`; catch that in `ApiClient`/`ApiException` handling and redirect to `/login` with a flash toast ("Your session expired, please log in again") rather than trying to silently refresh.
- Don't use `alert()`, `confirm()`, or `prompt()` anywhere — see Sections 8.4 and 8.7 for the replacements.
- Don't use emoji characters as icons anywhere in the UI — see Section 8.3.
- Don't introduce a CSS framework, npm, a bundler, or any build tooling beyond what Maven/Spring Boot already does — one hand-written `style.css` and one small `app.js`, per the original constraint.

## After generating

List anything from this prompt you couldn't implement exactly as specified, and why — don't silently approximate or skip. Also list:
- Every DTO field name or route you had to guess at because it wasn't documented in a README/OpenAPI spec and you couldn't locate it in the other modules' code either.
- Whether the backend's JWT actually carries `fullName`/`email` claims, or whether you had to fall back to the second display cookie described in Section 1.
- The exact enum values you found for booking status and courier availability status, so Section 8.5's badge mapping can be double-checked against them.
