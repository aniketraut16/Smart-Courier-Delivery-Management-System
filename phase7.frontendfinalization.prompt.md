# Prompt: Frontend Finalization — Premium Design System, Dark Mode, AI Chat Widget

Copy everything below the line into your AI coding assistant, in a session with access to the `web-ui-service` repo.

---

## Scope — this is a visual/UX revision, not a rebuild

Every existing page, route, and piece of functionality in `web-ui-service` stays exactly as it is — every `apiFetch` call, every screen's data and actions, every existing endpoint integration is unchanged. This prompt only touches: the design system (colors, type, spacing, radius, shadows), shared component styling, navigation shell, dashboard layouts, and adds one new capability (the AI chat widget). **Do not remove or alter any existing feature, route, or API call.**

## Resolved scope conflicts — read before building

1. **Dark mode is now in scope** (superseding an earlier "light mode only" instruction) — build it from the start, see section 3.
2. **The AI chat widget only offers booking assistance and packing guidance** — it calls the existing AI Assist Service (`/api/ai/chat`, `/api/ai/conversations`, `/api/ai/conversations/{id}/messages`) and nothing else. It does **not** offer shipment lookup or delivery analytics through chat — the AI Assist Service is deliberately non-agentic and never calls Booking or Tracking Service, so don't build a chat capability that implies otherwise. Shipment status and analytics stay exactly where they already are: the existing bookings list/detail pages, reached through normal navigation.
3. **No file attachment control in the chat panel** — no backend support exists for it; omit it entirely rather than adding a button that does nothing.
4. **No real notification center** — there's no customer-facing notifications endpoint. Build a lightweight client-side activity feed instead (see section 6) driven by status changes the browser itself observes while polling booking data — not a fabricated backend-driven inbox.

## 1. Design tokens — define once, use everywhere

Add Google Fonts Inter via CDN (`<link>` in the shared `<head>` fragment, weights 400/500/600/700/800). Define everything below as CSS custom properties in one shared stylesheet, scoped so a `data-theme="dark"` attribute on `<html>` swaps the values (see section 3) — no component should ever hardcode a color, radius, spacing, or shadow value directly.

**Colors — light theme:**
```css
--color-primary: #2563EB;
--color-primary-hover: #1D4ED8;
--color-secondary: #0F172A;
--color-accent: #14B8A6;
--color-success: #22C55E;
--color-warning: #F59E0B;
--color-danger: #EF4444;
--color-bg: #F8FAFC;
--color-surface: #FFFFFF;
--color-text-primary: #0F172A;
--color-text-secondary: #475569;
--color-border: #E2E8F0;
```

**Colors — dark theme** (adjusted for contrast on a dark background, not just the light values reused):
```css
--color-primary: #3B82F6;
--color-primary-hover: #60A5FA;
--color-secondary: #E2E8F0;
--color-accent: #2DD4BF;
--color-success: #4ADE80;
--color-warning: #FBBF24;
--color-danger: #F87171;
--color-bg: #0B1220;
--color-surface: #131C2E;
--color-text-primary: #F1F5F9;
--color-text-secondary: #94A3B8;
--color-border: #1E293B;
```

**Radius:** `--radius-sm: 8px; --radius-md: 10px; --radius-lg: 14px;`

**Spacing (8px grid, one extra half-step for tight spots):**
```css
--space-1: 4px; --space-2: 8px; --space-3: 16px; --space-4: 24px; --space-5: 32px; --space-6: 48px; --space-7: 64px;
```

**Type scale:**
```css
--font-xs: 12px; --font-sm: 14px; --font-base: 16px; --font-lg: 18px; --font-xl: 22px; --font-2xl: 28px; --font-3xl: 36px;
```
Page titles: 28–36px, weight 700–800. Section titles: 18–22px, weight 600. Body text: 14–16px, weight 400–500. Labels/captions: 12–13px, weight 500, slightly letter-spaced.

**Shadows (light theme):**
```css
--shadow-sm: 0 1px 2px rgba(15, 23, 42, 0.04);
--shadow-md: 0 4px 12px rgba(15, 23, 42, 0.08);
--shadow-lg: 0 12px 32px rgba(15, 23, 42, 0.12);
```
**In dark theme, don't reuse box-shadow for elevation** — shadows barely read on a dark background. Instead, use a 1px `var(--color-border)` border plus a slightly lighter `--color-surface` per elevation level (define a `--color-surface-raised` a shade lighter than `--color-surface` for modals/dropdowns/popovers).

## 2. Global assets

- Lucide icons (already in use — keep, just apply more consistently across every screen, no emoji anywhere, unchanged from before).
- Chart.js via CDN, for real-data charts only (section 7).
- `marked.js` (CDN) for rendering the AI assistant's markdown replies, and `DOMPurify` (CDN) to sanitize that rendered HTML before it's ever inserted into the DOM — **never skip sanitization**, since this is LLM-generated content being rendered as HTML.

## 3. Dark mode

A toggle button (sun/moon Lucide icon, swapping based on state) in the top bar. On load: check `localStorage` for a stored `theme` value; if none exists, fall back to the OS preference via `window.matchMedia('(prefers-color-scheme: dark)')`. Store the user's explicit choice in `localStorage` on toggle and set `data-theme="light"` or `data-theme="dark"` on `<html>` — every token in section 1 is scoped under that attribute so the whole app repaints instantly on toggle, no page reload.

## 4. Shared component restyle

Rebuild the look of every existing shared component from the earlier frontend build — **keep their exact JS behavior/API, restyle only**:
- **Buttons:** rounded (`--radius-md`), clear primary/secondary/danger/ghost variants, subtle lift-and-shadow on hover (150–200ms ease), visible focus ring for accessibility, disabled state clearly dimmed.
- **Inputs:** rounded (`--radius-sm`), clear focus state (border color shift to primary + soft glow), inline validation error text in `--color-danger` below the field.
- **Cards:** `--radius-lg`, `--shadow-sm` at rest, `--shadow-md` on hover where the card is clickable, `--color-surface` background.
- **Status badges:** keep the existing status→color mapping exactly, but restyle as small rounded pill badges with a colored dot; give `IN_TRANSIT` and courier `AVAILABLE` a subtle pulsing dot animation (2s ease-in-out infinite) to read as "actively happening," other statuses stay static.
- **Toasts:** restyle to match the new palette/radius/shadow, keep exact existing behavior (stacking, auto-dismiss, type variants) unchanged.
- **Confirm modal:** restyle only — rounded, `--shadow-lg`, `--color-surface-raised` background in dark mode — same focus-trap/Escape/backdrop-dismiss behavior as before.
- **Skeleton loaders:** replace flat gray placeholders with a shimmer effect — a light gradient sweep animating left-to-right across the placeholder shape (`background-position` animation on a linear-gradient), 1.5s loop.
- **Tables:** clean row separators using `--color-border`, subtle hover row highlight, comfortable row height, sticky header on scroll where a table can grow long.
- **Tracking timeline:** restyle the existing history list, and add a horizontal stepper variant at the top of booking detail pages showing the fixed sequence (Placed → Assigned → Picked Up → In Transit → Delivered), with completed steps filled/checked in `--color-success` or `--color-primary`, the current step highlighted, future steps in muted gray — this sits above the existing detailed timeline list, it doesn't replace it.

## 5. Navigation shell

- **Sidebar:** collapsible (toggle button; collapsed state shows icon-only with a tooltip on hover), smooth width transition, role-based nav items (same links as before, just restyled), active item shown with a left accent bar + tinted background in `--color-primary` at low opacity.
- **Top bar:** breadcrumbs reflecting the current page's position in the nav hierarchy, the dark-mode toggle, the client-side activity feed icon (see section 6), and a profile dropdown (avatar as a colored circle with the user's initials — no photo upload exists, don't invent one — showing name, role badge, a link to the profile page, and logout).
- **Quick action button** in the top bar: role-appropriate primary CTA always visible (Customer → "New Booking", Courier → link to their deliveries list, Admin → link to user management) — reuses whatever the dashboard's existing primary action already does, just also reachable from anywhere.

## 6. Client-side activity feed (replaces the "notification center" ask)

A bell icon in the top bar opens a small dropdown/panel. This is populated **only from data the browser already fetches** — when the existing booking-detail polling (already built) detects a status change since the last check, push a lightweight local entry ("Booking #1234 is now In Transit") into this feed, kept in memory (or `sessionStorage`) for the current session only. No backend endpoint is called for this feature beyond what already exists for polling. If there's nothing to show, display a clear empty state — don't leave a panel that looks broken with nothing in it.

## 7. Dashboard redesigns (per role) — real data only, nothing fabricated

- **Gradient hero section** at the top of each dashboard: a large rounded card (`--radius-lg`) with a subtle diagonal gradient from `--color-primary` to `--color-secondary`, a welcome message using the user's real name (from the already-fetched profile), and the role's primary CTA button.
- **KPI cards:** icon in a soft tinted circle (the stat's color at ~10% opacity background), large bold number, small label underneath. Populate only with counts genuinely derivable from existing endpoints (e.g., active bookings count, completed deliveries count from the customer's own booking list; active deliveries count for a courier). **Do not add a trend arrow or percentage-change indicator unless it's computed from real data you actually have — no fabricated "+12% this week."**
- **Status-breakdown chart:** a simple donut or bar chart (Chart.js) of the user's own bookings grouped by status, computed client-side from the same list data the existing bookings table already fetches — not a new endpoint, just a different view of data already on the page.
- **Recent items table:** same data/columns as the existing recent-bookings list, restyled per section 4.
- **Admin dashboard stays minimal and honest**, per the earlier standing instruction — restyle its existing simple "go to Users" card, don't invent admin analytics that no endpoint provides.

## 8. AI Chat Widget

- **Floating action button:** fixed bottom-right, circular, `--color-primary` background, a Lucide icon (`sparkles` or `message-circle`), subtle idle pulse/glow to invite interaction — visible on every authenticated customer page (courier/admin don't get this, since the AI Assist Service is customer-only per its existing `@PreAuthorize`).
- **Expandable panel:** slides/fades in on click, `--radius-lg`, `--shadow-lg`, roughly 380–420px wide on desktop, full-screen overlay on mobile. Header: "AI Assistant" title, a history icon opening a slide-over list of past conversations (from `GET /api/ai/conversations`, selecting one loads it via `GET /api/ai/conversations/{id}/messages`), and a close/minimize button.
- **Suggested prompt chips** shown only when starting a new, empty conversation — a few starter chips tied to what the AI actually does (e.g. "Help me book a delivery", "What details do you need from me?", "Packing tips for a fragile item") — clicking one sends it as the first message. Don't include a chip implying shipment lookup or analytics.
- **Typing indicator:** an animated three-dot bubble shown while waiting for `/api/ai/chat`'s response.
- **Markdown rendering:** render each assistant `reply` through `marked.js` then `DOMPurify.sanitize()` before inserting into the DOM — every single time, no exceptions.
- **Suggestion card:** when a response's `suggestion` field is populated, render it as a distinct card within the chat thread (not just plain text) — editable fields for both addresses' latitude/longitude and the estimated weight, plus the packing guidance text, and a "Get Courier Options" button. That button calls the existing `POST /api/bookings/quote` endpoint directly with the (possibly edited) values — the chat widget itself never calls Booking Service for anything else, and never calls it to actually place the order; placing the order still only happens through the existing full booking flow/page.

## 9. What NOT to do

- Don't change any existing API call, route, or piece of functionality — this is styling and the one new AI widget, nothing else.
- Don't add a chat capability for shipment lookup or analytics — out of scope per the AI service's non-agentic design.
- Don't add a file-attachment control to the chat panel.
- Don't build a real backend-driven notification system — the activity feed in section 6 is client-side only, from data already being fetched.
- Don't fabricate KPI trends, admin analytics, or any other numbers not derivable from an existing endpoint's real response.
- Don't skip `DOMPurify` sanitization on any AI-generated content before rendering it as HTML.
- Don't hardcode a color, spacing, radius, or shadow value anywhere outside the token definitions in section 1 — every component pulls from the CSS variables.

## After generating

List anything you couldn't implement exactly as specified, and why — don't silently approximate or skip.
