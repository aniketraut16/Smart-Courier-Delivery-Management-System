# Prompt: Centralize JWT Authentication at the API Gateway

Copy everything below the line into your AI coding assistant, in a session with access to the full repo (`api-gateway`, `user-auth-service`, `booking-service`, `tracking-service` — **not** `notification-service`, which needs no changes at all).

---

## Context — what already exists, and exactly what's changing

Right now, JWT validation is duplicated: `user-auth-service`, `booking-service`, and `tracking-service` each have their own `JwtAuthenticationFilter` + `JwtService` (`extractUserId`, `extractRole`, `isTokenValid`) decoding the `Authorization: Bearer <token>` header directly, all sharing one secret (`jwt.secret-key`). `api-gateway` currently only proxies requests with no auth logic of its own.

**What's changing:** the Gateway becomes the only place that validates a JWT. On a valid token, it forwards the caller's identity to the downstream service as headers instead of the raw token; the downstream service trusts those headers rather than re-decoding anything. **No route, no request/response shape, and no public-vs-protected endpoint behavior changes anywhere** — this is purely a relocation of *how* authentication happens, not *what* is authenticated.

**Do not touch `notification-service`** — every one of its endpoints is already `/internal/**`, API-key-protected, and never JWT-authenticated; nothing about it changes.

## Security design — read this before writing any filter

Simply trusting an `X-User-Id`/`X-User-Role` header from whoever sends it is not safe on its own — anyone who can reach a service directly (bypassing the gateway) could just set those headers themselves. To close that: **every identity header the gateway sets is only trusted by a downstream service if the request also carries a valid `X-Internal-Api-Key` header matching the existing shared `internal.api-key` value already used for `/internal/**` calls.** This reuses infrastructure that already exists in every service — no new secret to manage. The gateway must **strip any `X-User-Id`, `X-User-Role`, `X-User-Email`, or `X-Internal-Api-Key` headers already present on the incoming client request** before setting its own — on every request, public or protected — so a client can never inject or spoof these.

## 1. `api-gateway` changes

First, check `pom.xml` and the existing route configuration (`application.yml` route definitions or a `RouteLocator` bean, whichever this project uses) to see exactly how routes are currently defined — **do not change any route predicate, URI, or path** in this prompt; only add a filter.

Also check: does the gateway currently proxy `/internal/**` paths at all? **It should not.** If any route configuration currently exposes `/internal/**` externally through the gateway, remove that route — internal endpoints are service-to-service only and must never be reachable from outside. If no such route exists already, nothing to do here.

### `application.yml` additions
```yaml
jwt:
  secret-key: "MUST_BE_BYTE_FOR_BYTE_IDENTICAL_TO_USER_AUTH_SERVICE_SIGNING_SECRET"
internal:
  api-key: "MUST_MATCH_EVERY_SERVICE_INTERNAL_API_KEY"
security:
  public-paths:
    - "/api/auth/register"
    - "/api/auth/login"
    - "/**/swagger-ui/**"
    - "/**/v3/api-docs/**"
    - "/actuator/**"
```
(Check whether Actuator is actually in use anywhere before assuming that last entry is needed — harmless to leave in either way.)

### `GatewayJwtService` (new, gateway-local)
```java
boolean isTokenValid(String token);
UUID extractUserId(String token);
String extractRole(String token);
String extractEmail(String token);
```
Same HS256 validation logic as the existing per-service `JwtService` classes (reuse the same JWT library already in `pom.xml`, or tell me if it's missing rather than adding it) — this is the **only** place in the whole system that still needs to decode a token, since every downstream service stops doing so after this change.

### `JwtAuthenticationGlobalFilter implements GlobalFilter, Ordered`
Applies to every route uniformly (don't wire it per-route):
1. Strip `X-User-Id`, `X-User-Role`, `X-User-Email`, and `X-Internal-Api-Key` from the incoming request, regardless of path.
2. If the request path matches any entry in `security.public-paths` (use `AntPathMatcher`), forward the (now-stripped) request unchanged — no token check needed.
3. Otherwise, read `Authorization: Bearer <token>`. If missing, or `isTokenValid(token)` is false, **respond directly from the gateway** with `401` and a JSON body shaped like the existing `ErrorResponse` (`timestamp`, `status`, `error`, `message`, `path`) — do not proxy the request through.
4. If valid, extract `userId`, `role`, `email`, and mutate the outgoing request to add `X-User-Id`, `X-User-Role`, `X-User-Email`, and `X-Internal-Api-Key` (from `internal.api-key` config), then forward.

Set the filter's `getOrder()` early enough to run before routing.

## 2. Downstream service changes — identical pattern in `user-auth-service`, `booking-service`, `tracking-service`

### Remove
- The existing `JwtAuthenticationFilter` class entirely.
- From `JwtService`: remove `extractUserId`, `extractRole`, `isTokenValid` (and the whole class, if nothing else uses it) — **except in `user-auth-service`, where `generateToken` must stay** (it still issues tokens at login/register; it just no longer validates incoming ones).
- The `jwt.secret-key` property from `application.yml` in `booking-service` and `tracking-service` (no longer used there at all). **Keep it in `user-auth-service`** (still needed for signing new tokens).

### Add — `GatewayHeaderAuthenticationFilter extends OncePerRequestFilter`, recreated locally in each of the three services
1. Skip this filter entirely for any path already covered by the existing `permitAll()` list (unchanged) and for any `/internal/**` path (governed by the existing, untouched `InternalApiKeyFilter`).
2. For every other path: check `X-Internal-Api-Key` against the existing `internal.api-key` config value (same property already used for `/internal/**`, now doing double duty). If missing or mismatched → `401` with an `ErrorResponse` body, stop the chain — this is what proves the request actually came through the gateway.
3. If the key matches, read `X-User-Id` (parse as `UUID`) and `X-User-Role` from the request headers and populate `SecurityContextHolder` exactly as the old filter did: authority `ROLE_<role>`, principal = the user ID. If either header is unexpectedly missing on a non-public path, treat it as `401` (defensive — shouldn't happen if the gateway is working correctly).

### `SecurityFilterChain` changes
Replace the registration of the old `JwtAuthenticationFilter` with `GatewayHeaderAuthenticationFilter` at the same position in the chain (`addFilterBefore(..., UsernamePasswordAuthenticationFilter.class)`). Leave everything else in the security config untouched: the same `permitAll()` list, the same `InternalApiKeyFilter` registration, the same `@EnableMethodSecurity`, the same `@PreAuthorize` annotations on controllers. **No controller, service, DTO, or `@PreAuthorize` annotation changes anywhere** — only the authentication filter and its supporting classes change.

## 3. What NOT to do

- Don't change any endpoint path, request/response DTO, or `@PreAuthorize` role check in any service.
- Don't add gateway routing for `/internal/**` paths.
- Don't let a public path's request through with stale `X-User-*` headers still attached from the original client request — strip them universally, before the public/protected branch.
- Don't remove `jwt.secret-key` from `user-auth-service` — it still signs tokens.
- Don't touch `notification-service` at all.

## Expected behavior change worth testing for (not a bug)

After this change, calling an "authenticated" endpoint **directly on a service's own port** (bypassing the gateway) with a perfectly valid JWT in the `Authorization` header will now correctly return `401` — the service no longer looks at that header at all, only the gateway-supplied trusted headers matter. That's the intended effect of centralizing auth at the gateway; only gateway-routed traffic is a supported path going forward.

## After generating

List anything you couldn't implement exactly as specified, and why — don't silently approximate or skip.
