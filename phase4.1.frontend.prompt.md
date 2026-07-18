# Prompt 1 of 3 — `web-ui-service`: Foundation, Auth, Shared UI Kit, Public Pages

Copy everything below the line into your AI coding assistant. **This prompt is fully self-contained** — every request/response shape, field, enum value, and error message it needs is embedded below. Do not read any other service's source code, README, or Swagger UI to discover a shape; if something here seems ambiguous, treat the embedded reference as authoritative rather than inferring from elsewhere. **Run and fully verify this prompt's output (checklist at the bottom) before starting Prompt 2.**

This is a Thymeleaf frontend module for an existing Spring Boot 3 / Java 21 microservices project. It calls the backend only through the API Gateway at `gateway.base-url` — never a service's own port directly. It has no database, no JPA, no Spring Security.

---

## 0. Module setup

New Maven module `web-ui-service`, base package `com.couriersystem.courier.webui`.

Dependencies: `spring-boot-starter-web`, `spring-boot-starter-thymeleaf`, `spring-boot-starter-validation`, a JWT **decoding-only** library (`jjwt-api`+`jjwt-impl`+`jjwt-jackson`, or `java-jwt`) — never re-verify the signature here, the backend already does. Lombok. **Do not add** `spring-boot-starter-data-jpa`, any DB driver, or `spring-boot-starter-security`.

`application.yml`:
```yaml
server:
  port: 8090
gateway:
  base-url: "http://localhost:8080"
app:
  jwt-cookie-name: "courier_jwt"
  display-cookie-name: "courier_display"
  cookie-secure: false   # true behind HTTPS
```

---

## 1. Embedded API Reference — shapes shared by every prompt in this series

These three things are identical across every backend service and defined **once here**; Prompts 2 and 3 reference this section instead of redefining them.

### 1.1 Error response shape (same on all services)
```json
{
  "timestamp": "2026-07-17T10:30:00.000Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Human-readable reason",
  "path": "/api/bookings"
}
```
```java
@JsonIgnoreProperties(ignoreUnknown = true)
public class ErrorResponse {
    private String timestamp;
    private int status;
    private String error;
    private String message;
    private String path;
    // getters/setters
}
```
Every `ApiException` shown to a user displays this `message` field verbatim (via toast) — never a stack trace, never raw JSON.

### 1.2 Paginated list response shape — confirmed real shape, do not assume the generic Spring Data default
Confirmed from a real `GET /api/admin/users` response:
```json
{
  "content": [ { "...": "one item, shape varies by endpoint" } ],
  "pageable": { "pageNumber": 0, "pageSize": 10 },
  "totalElements": 42,
  "totalPages": 5,
  "last": false
}
```
Note what is **not** present: no top-level `number`, `size`, `first`, or `empty` field. Build pagination logic only from what's actually here:
- Current page index → `pageable.pageNumber`
- "Is this the first page" → compute as `pageable.pageNumber == 0`, there is no `first` flag to read
- "Is there a next page" → `last == false`
```java
@JsonIgnoreProperties(ignoreUnknown = true)
public class PageResponse<T> {
    private List<T> content;
    private PageableInfo pageable;
    private long totalElements;
    private int totalPages;
    private boolean last;
    // getters/setters
}

@JsonIgnoreProperties(ignoreUnknown = true)
public class PageableInfo {
    private int pageNumber;
    private int pageSize;
    // getters/setters
}
```
`@JsonIgnoreProperties(ignoreUnknown = true)` is mandatory on **every** response DTO in this entire project, not just this one — if the real backend response ever includes one field your local copy doesn't, deserialization throws outright without it.

### 1.3 Shared enums (exact spellings — used for status badges, dropdowns, form options across all three prompts)
| Enum | Values |
|------|--------|
| `Role` | `CUSTOMER`, `COURIER`, `ADMIN` |
| `AccountStatus` | `ACTIVE`, `INACTIVE`, `SUSPENDED` |
| `VehicleType` | `BIKE`, `SCOOTER`, `CAR`, `VAN` |
| `AvailabilityStatus` | `AVAILABLE`, `BUSY`, `OFFLINE` |
| `BookingStatus` | `PLACED`, `ASSIGNED`, `PICKED_UP`, `IN_TRANSIT`, `DELIVERED`, `CANCELLED` |
| `PackageType` (Prompt 2) | `DOCUMENT`, `GENERAL`, `FRAGILE`, `ELECTRONICS`, `FOOD` |

**Note on `PLACED`:** the Booking Service creates every booking directly in `ASSIGNED` status — there is no unassigned state in this system's actual booking-creation flow (confirmed later in Prompt 2's embedded docs). `PLACED` still appears in the shared enum and in some historical status-transition documentation, and *may* still appear in tracking history entries in practice — so keep it in the badge/status mapping and don't special-case its absence, just don't expect it to be the norm.

### 1.4 Internal endpoints — out of scope, always
Some services expose `/internal/**` routes protected by an `X-Internal-Api-Key` header instead of a JWT, for service-to-service calls only (e.g. Booking Service calling User & Auth Service to validate a customer). **This module never calls any `/internal/**` route, under any circumstance.** They're mentioned here only so you recognize and ignore them if you see them referenced anywhere.

---

## 2. Embedded API Reference — User & Auth Service (used in this prompt)

Reference base URL `http://localhost:8081` is behind the gateway — this module calls `gateway.base-url + path`, never the port directly.

### 2.1 POST /api/auth/register
**Access:** Public. Registers `CUSTOMER` or `COURIER` only — `ADMIN` is rejected (admins are provisioned directly in the DB, no page in this app ever creates one).

Request:
| Field | Type | Required | Notes |
|---|---|---|---|
| fullName | string | yes | max 150 chars |
| email | string | yes | valid email, max 150, must be unique |
| phoneNumber | string | yes | max 20 chars, must be unique |
| password | string | yes | 8–100 chars |
| role | string | yes | `CUSTOMER` or `COURIER` |
| vehicleType | string | required only if role=COURIER | `BIKE`,`SCOOTER`,`CAR`,`VAN` |
| vehicleNumber | string | no | max 20 chars, optional even for couriers |

Example (courier):
```json
{
  "fullName": "Ravi Kumar",
  "email": "ravi@example.com",
  "phoneNumber": "9123456789",
  "password": "secret123",
  "role": "COURIER",
  "vehicleType": "BIKE",
  "vehicleNumber": "MH12AB1234"
}
```

Success `201`:
```json
{
  "id": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "fullName": "Jane Doe",
  "email": "jane@example.com",
  "phoneNumber": "9876543210",
  "role": "CUSTOMER",
  "accountStatus": "ACTIVE",
  "createdAt": "2025-01-15T10:30:00+05:30"
}
```

Error cases — display `message` as-is via toast, no special handling needed for any of these:
| Status | Message |
|---|---|
| 400 | `role: must not be null` |
| 400 | `password: size must be between 8 and 100` |
| 400 | `Registration with role ADMIN is not permitted` |
| 400 | `vehicleType is required for COURIER registration` |
| 409 | `Email already in use: <email>` |
| 409 | `Phone number already in use: <phone>` |

### 2.2 POST /api/auth/login
**Access:** Public.

Request: `{ "email": string, "password": string }`

Success `200` — **this is the entire identity payload this module ever gets; there is no follow-up call**:
```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
  "tokenType": "Bearer",
  "expiresInMs": 86400000,
  "userId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "role": "CUSTOMER",
  "fullName": "Jane Doe"
}
```
**Important — this shape drives Section 3's cookie design:** `fullName` is here, in the login body. `email` is **not** here — it lives only in the JWT's own claims. There is no need to call `GET /api/users/me` after login; everything required is already in this one response plus the token's claims.

JWT claim payload (decode-only, never re-verify): `sub` (userId), `role`, `email`. **No `fullName` claim in the token** — that's why it must come from the login response body instead, per Section 3.

Error cases:
| Status | Message | Cause |
|---|---|---|
| 400 | `email: must not be blank` | missing field |
| 401 | `Invalid email or password` | wrong credentials (same message for both cases, intentional — don't try to distinguish them in the UI) |
| 403 | `Account is not active` | `accountStatus` is `INACTIVE`/`SUSPENDED` |

---

## 3. Auth — JWT held client-side in a cookie, not a server session

**No `HttpSession` is used anywhere in this module.**

- On successful login (Section 2.2's response), set a cookie named per `app.jwt-cookie-name` holding `accessToken`, with **`HttpOnly`** (non-negotiable — keeps it unreadable to page JS so an XSS bug can't steal it), `SameSite=Lax`, `Secure` when `app.cookie-secure=true`, and `Max-Age` derived from `expiresInMs`.
- Since the JWT itself lacks `fullName` and the login response lacks `email`, set a **second, non-HttpOnly** display cookie (`app.display-cookie-name`) holding `{ "fullName": ..., "email": ... }` as JSON — `fullName` straight from the login response, `email` from decoding the fresh token's claims once at login time. This cookie is **display only** — never use it for any authorization decision, since it's client-editable; role checks always come from decoding the JWT cookie's `role` claim.
- A `JwtCookieFilter` (`OncePerRequestFilter`, registered via `FilterRegistrationBean`) runs on every request: reads the JWT cookie if present, decodes it, and sets a request-scoped `CurrentUser` (userId, role, email from the token; fullName from the display cookie) as a request attribute, exposed to controllers/templates via a `@ModelAttribute` method.
- `ApiClient` (Section 4) reads the raw token string from the JWT cookie for every outbound call.
- Logout clears both cookies (`Max-Age=0`), redirects to `/`.
- Comment on the filter stating plainly: this is a UX convenience for redirecting people to the right page, **not** the security boundary — the gateway/backend authorizes every real call independently regardless of what this filter decides.

## 4. `ApiClient`

One `RestTemplate` `@Bean` (default config). Build `ApiClient` with two method families:

```java
// Single-object responses (login, one booking, etc.)
<T> T get(String path, String bearerToken, Class<T> responseType);
<T> T post(String path, String bearerToken, Object body, Class<T> responseType);
<T> T put(String path, String bearerToken, Object body, Class<T> responseType);
<T> T patch(String path, String bearerToken, Object body, Class<T> responseType);

// Generic responses — a List<...> or the PageResponse<...> from Section 1.2.
// Class<T> alone cannot deserialize a generic type correctly; use this overload
// for every such call, without exception.
<T> T get(String path, String bearerToken, ParameterizedTypeReference<T> responseType);
```
Example: `apiClient.get("/api/admin/users?page=0&size=10", token, new ParameterizedTypeReference<PageResponse<UserProfileResponse>>() {})`.

Every method catches `RestClientResponseException`, parses the body as `ErrorResponse` (Section 1.1), rethrows as unchecked `ApiException(int statusCode, String message)`. On `401` specifically: clear both auth cookies, redirect `/login` with a flash toast ("Your session expired, please log in again") instead of surfacing the raw error. Every controller catches other `ApiException`s and shows `message` via toast.

## 5. DTOs for this prompt

`RegisterRequest`, `LoginRequest`, `AuthResponse`, `UserProfileResponse` (fields: `id, fullName, email, phoneNumber, role, accountStatus, createdAt` — matches Section 2.1's success shape) — all with `@JsonIgnoreProperties(ignoreUnknown = true)`.

Form-backing object: `RegisterForm` (same fields as `RegisterRequest` + `confirmPassword`, checked for equality in the controller, not via annotation).

## 6. Auth guard

No Spring Security. One `AuthInterceptor implements HandlerInterceptor`:
- Reads `CurrentUser` from the request attribute.
- Absent + path not `/login`, `/register`, or static → redirect `/login`.
- Present but role doesn't match path prefix (`/customer/**`→CUSTOMER, `/courier/**`→COURIER, `/admin/**`→ADMIN) → redirect `/access-denied`.

Register via `WebMvcConfigurer` on `/customer/**`, `/courier/**`, `/admin/**` only — explicitly exclude `/css/**`, `/js/**`, `/images/**`, `/webjars/**` defensively.

## 7. Shared UI kit — build each of these once, reused verbatim by Prompts 2 and 3

**Toasts** (`fragments/toast-container.html` + `app.js`): flash a `ToastMessage(type, message)` record via `RedirectAttributes.addFlashAttribute("toast", ...)`. Fixed top-right stack, auto-dismiss ~4s + manual close. No `alert()`/`confirm()`/`prompt()` anywhere, ever.

**Confirm modal** (`fragments/confirm-modal.html` + `app.js` `openConfirmModal(message, formIdToSubmit)`): generic shell, used starting Prompt 2.

**Status badge** (`fragments/status-badge.html`): status string → colored pill + icon, per Section 1.3's exact enum spellings:
- `PLACED`/`ASSIGNED`→info, `PICKED_UP`/`IN_TRANSIT`→warning, `DELIVERED`→success, `CANCELLED`→danger
- Availability: `AVAILABLE`→success, `BUSY`→warning, `OFFLINE`→neutral

**Note/info bar** (`fragments/note.html`, type: info/warning): short persistent inline context.

**Pagination** (`fragments/pagination.html`): takes `pageable.pageNumber`, `totalPages`, `last`, and a base URL pattern. Prev disabled when `pageNumber==0`; Next disabled when `last==true` — per Section 1.2, do not reference `first`/`number`/`empty`, they don't exist in the real response.

**Loading button** (`app.js`, generic, all `<form>` submits): disable + spinner + "Saving…" on submit, prevents double-submit.

## 8. Base layout + complete CSS

`templates/layout/base.html`: role-aware nav, includes toast container + confirm modal fragments, links `static/css/style.css` + `static/js/app.js`. Every page extends this consistently via one chosen mechanism (`th:replace`/`th:insert`).

Use this `static/css/style.css` in full — deliberately complete so nothing is left unstyled by omission:

```css
:root {
  --color-bg: #F8F9FB;
  --color-surface: #FFFFFF;
  --color-border: #E2E5EA;
  --color-text-primary: #1A1D23;
  --color-text-secondary: #5C6270;
  --color-accent: #2F6FED;
  --color-accent-hover: #2558C4;
  --color-success: #1D8A5A; --color-success-bg: #E8F6EF;
  --color-warning: #B5750B; --color-warning-bg: #FDF3E3;
  --color-danger: #C4302B;  --color-danger-bg: #FBEAEA;
  --color-info: #2F6FED;    --color-info-bg: #EAF1FE;
  --color-neutral: #5C6270; --color-neutral-bg: #EEF0F3;
  --radius: 8px;
  --shadow-sm: 0 1px 2px rgba(0,0,0,0.06);
  --shadow-md: 0 4px 12px rgba(0,0,0,0.10);
}
* { box-sizing: border-box; }
body {
  margin: 0;
  font-family: -apple-system, "Segoe UI", Inter, Roboto, sans-serif;
  font-size: 14px; line-height: 1.5;
  color: var(--color-text-primary);
  background: var(--color-bg);
}
h1 { font-size: 28px; font-weight: 600; margin: 0 0 16px; }
h2 { font-size: 20px; font-weight: 600; margin: 0 0 12px; }
a { color: var(--color-accent); text-decoration: none; }
a:hover { text-decoration: underline; }
.container { max-width: 1100px; margin: 0 auto; padding: 24px 16px; }

.navbar { background: var(--color-surface); border-bottom: 1px solid var(--color-border); padding: 12px 24px; display: flex; align-items: center; justify-content: space-between; }
.navbar .nav-links { display: flex; gap: 20px; align-items: center; }
.navbar .nav-links a { color: var(--color-text-secondary); font-weight: 600; }
.navbar .nav-links a.active { color: var(--color-accent); }
.navbar .nav-user { display: flex; align-items: center; gap: 8px; color: var(--color-text-secondary); }

.btn { display: inline-flex; align-items: center; gap: 6px; padding: 10px 16px; border-radius: var(--radius); border: 1px solid transparent; font-size: 14px; font-weight: 600; cursor: pointer; font-family: inherit; }
.btn:focus-visible { outline: 2px solid var(--color-accent); outline-offset: 2px; }
.btn-primary { background: var(--color-accent); color: #fff; }
.btn-primary:hover { background: var(--color-accent-hover); }
.btn-secondary { background: var(--color-surface); color: var(--color-text-primary); border-color: var(--color-border); }
.btn-secondary:hover { background: var(--color-bg); }
.btn-danger { background: var(--color-danger); color: #fff; }
.btn-danger:hover { opacity: 0.9; }
.btn:disabled { opacity: 0.6; cursor: not-allowed; }

.form-group { margin-bottom: 16px; }
label { display: block; font-weight: 600; font-size: 14px; margin-bottom: 6px; color: var(--color-text-primary); }
input[type="text"], input[type="email"], input[type="password"], input[type="number"], input[type="tel"], select, textarea {
  width: 100%; padding: 10px 12px; border: 1px solid var(--color-border); border-radius: var(--radius);
  font-size: 14px; font-family: inherit; background: var(--color-surface); color: var(--color-text-primary);
}
input:focus, select:focus, textarea:focus { outline: none; border-color: var(--color-accent); box-shadow: 0 0 0 3px rgba(47,111,237,0.15); }
textarea { min-height: 80px; resize: vertical; }
.form-error { color: var(--color-danger); font-size: 13px; margin-top: 4px; }

.card { background: var(--color-surface); border: 1px solid var(--color-border); border-radius: var(--radius); padding: 20px; box-shadow: var(--shadow-sm); }
.card-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(180px, 1fr)); gap: 16px; margin-bottom: 24px; }
.card-stat .stat-value { font-size: 24px; font-weight: 600; }
.card-stat .stat-label { color: var(--color-text-secondary); font-size: 13px; }

.table { width: 100%; border-collapse: collapse; background: var(--color-surface); border-radius: var(--radius); overflow: hidden; }
.table th, .table td { padding: 12px 16px; text-align: left; border-bottom: 1px solid var(--color-border); }
.table th { font-size: 12px; text-transform: uppercase; color: var(--color-text-secondary); background: var(--color-bg); }
.table tr:hover td { background: var(--color-bg); }

.badge { display: inline-flex; align-items: center; gap: 4px; padding: 4px 10px; border-radius: 999px; font-size: 12px; font-weight: 600; }
.badge-info { background: var(--color-info-bg); color: var(--color-info); }
.badge-warning { background: var(--color-warning-bg); color: var(--color-warning); }
.badge-success { background: var(--color-success-bg); color: var(--color-success); }
.badge-danger { background: var(--color-danger-bg); color: var(--color-danger); }
.badge-neutral { background: var(--color-neutral-bg); color: var(--color-neutral); }

.toast-container { position: fixed; top: 16px; right: 16px; display: flex; flex-direction: column; gap: 8px; z-index: 1000; }
.toast { min-width: 260px; max-width: 360px; padding: 12px 16px; border-radius: var(--radius); box-shadow: var(--shadow-md); display: flex; align-items: center; gap: 8px; font-size: 14px; background: var(--color-surface); border: 1px solid var(--color-border); animation: toast-in 0.2s ease-out; }
.toast-success { border-left: 4px solid var(--color-success); }
.toast-error { border-left: 4px solid var(--color-danger); }
.toast-info { border-left: 4px solid var(--color-info); }
.toast .toast-close { margin-left: auto; cursor: pointer; color: var(--color-text-secondary); background: none; border: none; }
@keyframes toast-in { from { opacity: 0; transform: translateY(-6px); } to { opacity: 1; transform: translateY(0); } }

.modal-overlay { position: fixed; inset: 0; background: rgba(26,29,35,0.4); display: none; align-items: center; justify-content: center; z-index: 1100; }
.modal-overlay.open { display: flex; }
.modal { background: var(--color-surface); border-radius: var(--radius); padding: 24px; max-width: 420px; width: 90%; box-shadow: var(--shadow-md); }
.modal-actions { display: flex; justify-content: flex-end; gap: 8px; margin-top: 20px; }

.note { display: flex; gap: 10px; padding: 12px 16px; border-radius: var(--radius); font-size: 13px; margin-bottom: 16px; }
.note-info { background: var(--color-info-bg); color: var(--color-info); }
.note-warning { background: var(--color-warning-bg); color: var(--color-warning); }

.pagination { display: flex; gap: 12px; align-items: center; margin-top: 16px; font-size: 14px; }
.pagination .disabled { color: var(--color-text-secondary); pointer-events: none; opacity: 0.5; }

.timeline { list-style: none; padding: 0; margin: 0; }
.timeline-item { display: flex; gap: 12px; padding-bottom: 20px; position: relative; }
.timeline-item:not(:last-child)::before { content: ""; position: absolute; left: 9px; top: 22px; bottom: -4px; width: 2px; background: var(--color-border); }
.timeline-dot { width: 20px; height: 20px; border-radius: 50%; background: var(--color-accent); flex-shrink: 0; }

.empty-state { text-align: center; padding: 48px 16px; color: var(--color-text-secondary); }

@media (max-width: 640px) {
  .navbar { flex-direction: column; gap: 10px; align-items: flex-start; }
  .card-grid { grid-template-columns: 1fr; }
}
:focus-visible { outline: 2px solid var(--color-accent); outline-offset: 2px; }
```
Verify in devtools that `/css/style.css` returns `200`, not `404`/a redirect — if it redirects, Section 6's interceptor exclusion is wrong.

## 9. Public pages

| Method | Path | Template | Behavior |
|---|---|---|---|
| GET | `/` | `home.html` | Landing page, links to Login/Register |
| GET | `/login` | `auth/login.html` | Empty `LoginRequest`-backed form |
| POST | `/login` | — | Section 2.2; on success set cookies per Section 3, redirect to the role's dashboard stub (below); on failure flash error toast, redirect `/login` |
| GET | `/register` | `auth/register.html` | `RegisterForm`-backed; courier-only fields shown only when role=COURIER (plain JS); `note` fragment explaining why |
| POST | `/register` | — | Check password match in controller; Section 2.1; on success redirect `/login` with success toast |
| POST | `/logout` | — | Clear both cookies, redirect `/` |
| GET | `/access-denied` | `error/access-denied.html` | Icon + message, link to own dashboard if logged in |
| — | `/error` | `error/generic-error.html` | Friendly 404/500 — a bare Whitelabel page is what a broken template looks like from the outside, so make this a real, styled page |

**Dashboard stubs** (real versions in Prompts 2 & 3): `customer/dashboard.html`, `courier/dashboard.html`, `admin/dashboard.html` — each just says "coming in Prompt X", but extends the real base layout so nav/CSS/toast plumbing is exercised now.

## What NOT to do

- No `HttpSession`, no `spring-boot-starter-security`.
- No `localStorage`/`sessionStorage` for the token; no cookie without `HttpOnly`.
- No direct calls to any backend service's port — gateway only.
- No `alert()`/`confirm()`/`prompt()`. No emoji as icons.
- No CSS framework, npm, or bundler.
- No call to `GET /api/users/me` — Section 2.2's login response plus the JWT claims are sufficient; don't add a call that isn't needed.
- No call to any `/internal/**` route.

## Definition of Done

1. Register a CUSTOMER and a COURIER through the real UI — courier-only fields toggle correctly.
2. Log in as each — confirm in devtools the JWT cookie is `HttpOnly`; confirm the display cookie holds the right `fullName`/`email`.
3. Redirect lands on the correct dashboard stub per role; nav shows correct name/role; Logout works.
4. Bad login → toast with the exact backend message (`Invalid email or password`), not a generic fallback.
5. As CUSTOMER, manually navigate to `/admin/dashboard` → redirected to `/access-denied`, no crash.
6. `/css/style.css` returns 200 on every page; inputs/buttons visibly styled (bordered, focus glow). If any page looks unstyled, check server logs for a template exception first — don't assume it's a CSS bug.
7. Nonexistent URL → friendly error page, not Spring's default Whitelabel page.

List anything you couldn't implement exactly as specified and why.
