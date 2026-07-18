# Prompt: Build Delivery & Tracking Service + Notification Service (Phase 3)

Copy everything below the line into your AI coding assistant, in a session with access to the actual repo (`user-auth-service`, `booking-service`, `tracking-service`, `notification-service` modules).

---

## Context — what already exists, don't rebuild it

Spring Boot 3 / Java 21 microservices project. Already built and running:

1. **Database schema** — all four service databases already exist exactly as defined in `DATABASE.md`. No table/column changes except the one field addition in section 0.1.
2. **JPA entities** — already exist in each service's `model` package, matching `DATABASE.md` exactly: `StatusUpdate`, `CurrentBookingStatus` in `tracking-service`; `Notification`, `NotificationDeliveryAttempt` in `notification-service`. Read them first, reuse exact field/enum names.
3. **User & Auth Service (Phase 1)** — fully built: JWT issuance/validation, `/api/auth/**`, `/api/users/me`, `/api/couriers/me/availability`, `/api/admin/users`, and internal endpoints `/internal/customers/{id}`, `/internal/couriers/available`, `/internal/couriers/{id}/assign`, `/internal/couriers/{id}/release`.
4. **Booking Service (Phase 2)** — fully built: booking creation with haversine fare calc and auto-assignment, cancellation, listing, and internal endpoints `/internal/bookings/{id}` (GET) and `/internal/bookings/{id}/status` (PATCH — enforces the forward-only state machine `ASSIGNED → PICKED_UP → IN_TRANSIT → DELIVERED`, releases the courier on `DELIVERED`). **This PATCH endpoint does not change in this phase** — Phase 3 is where something finally calls it.

Now build Phase 3: **Notification Service** (build first, since Tracking and Booking both depend on it) and **Delivery & Tracking Service** on top of all of it. Base packages (adjust only if the real ones differ — verify against existing entity files): `com.couriersystem.courier.notification` and `com.couriersystem.courier.tracking`.

## 0. Required additions to already-built services — do these first, nothing else in those services changes

### 0.1 — User & Auth Service: add `email` to the internal customer response

Add one field, `email (String)`, to the existing `InternalCustomerResponse` DTO, and populate it from `user.getEmail()` in whatever service method currently builds that response. Do not add `email` to `InternalCourierResponse` — courier notifications are out of scope for this phase (see section 9).

### 0.2 — Booking Service: add a fire-and-forget `NotificationServiceClient` and three call sites

Add to `booking-service`'s `application.yml`:
```yaml
services:
  notification:
    base-url: "http://localhost:8084"   # replace with the real running address
```
(`internal.api-key` already exists in this service from Phase 2 — reuse it, don't duplicate the property.)

Create `NotificationServiceClient` in the `client` package:
```java
void notify(UUID recipientCustomerId, UUID bookingId, EventType eventType);
```
Recreate a local `EventType` enum in Booking Service (`BOOKING_PLACED, COURIER_ASSIGNED, PICKED_UP, IN_TRANSIT, DELIVERED, CANCELLED`) — matching Notification Service's enum values exactly (see section 1). This method calls `POST {notification.base-url}/internal/notifications/send` with the `X-Internal-Api-Key` header and body `{recipientId, bookingId, eventType}`. **Critically: this method must catch every exception internally (network failure, timeout, non-2xx response) and only log it — it must never throw, and a notification failure must never cause a booking operation to fail or roll back.**

Wire in exactly three call sites in Booking Service's existing service-layer logic:
- After a booking is successfully persisted (the existing creation flow's step 4) → `notify(customerId, booking.getId(), EventType.BOOKING_PLACED)`.
- After auto-assignment succeeds within that same creation flow (the existing step 5, only on the success path where a courier was actually assigned) → `notify(customerId, booking.getId(), EventType.COURIER_ASSIGNED)`.
- After a booking is successfully cancelled → `notify(customerId, booking.getId(), EventType.CANCELLED)`.

Do not add any notification call for `PICKED_UP`, `IN_TRANSIT`, or `DELIVERED` inside Booking Service — those three are Tracking Service's responsibility (section 8 below), since Booking Service's `PATCH /internal/bookings/{id}/status` endpoint is called *by* Tracking Service, not by the customer or courier directly.

## PART A — Notification Service

## 1. Existing entities — reference only

`Notification`: `id`, `recipientId (UUID, logical ref)`, `bookingId (UUID, logical ref)`, `notificationType (NotificationType enum: EMAIL, SMS, PUSH)`, `eventType (EventType enum: BOOKING_PLACED, COURIER_ASSIGNED, PICKED_UP, IN_TRANSIT, DELIVERED, CANCELLED)`, `messageContent (String)`, `sendStatus (SendStatus enum: PENDING, SENT, FAILED)`, `sentAt (nullable)`, `createdAt`, `updatedAt`.

`NotificationDeliveryAttempt`: `id`, `notification (Notification, @ManyToOne @JoinColumn(name="notification_id"))`, `attemptNumber (Integer)`, `provider (String, nullable)`, `status (AttemptStatus enum: SUCCESS, FAILED)`, `errorMessage (String, nullable)`, `attemptedAt`.

Reuse these enums exactly as already defined — do not create duplicates. In this phase, `notificationType` is always set to `EMAIL` (SMS/PUSH are defined in the schema for future use but never selected by any code path here).

**One permitted entity change:** if `Notification` does not already expose `@OneToMany(mappedBy = "notification", cascade = CascadeType.ALL, orphanRemoval = true) List<NotificationDeliveryAttempt> deliveryAttempts`, add it now, so a single `save()` can persist the notification and its first delivery attempt together. Change nothing else.

## 2. `application.yml` additions

```yaml
spring:
  mail:
    host: smtp.gmail.com
    port: 587
    username: ${MAIL_USERNAME:dummy.courier.app@gmail.com}
    password: ${MAIL_APP_PASSWORD:REPLACE_WITH_A_GOOGLE_APP_PASSWORD}
    properties:
      mail:
        smtp:
          auth: true
          starttls:
            enable: true
notification:
  mail:
    from-address: ${MAIL_USERNAME:dummy.courier.app@gmail.com}
    from-name: "Smart Courier Delivery"
internal:
  api-key: "MUST_MATCH_EVERY_OTHER_SERVICE_INTERNAL_API_KEY"
services:
  user-auth:
    base-url: "http://localhost:8081"
```
Check `pom.xml` for `spring-boot-starter-mail`. If missing, tell me and stop that part — don't add the dependency yourself.

A Google App Password (not the normal account password) is required since this uses SMTP auth directly — I will generate that myself; you're only wiring the config keys, not generating credentials.

## 3. Repository layer (`repository` package)

`NotificationRepository extends JpaRepository<Notification, UUID>`:
- `List<Notification> findByBookingIdOrderByCreatedAtAsc(UUID bookingId)`

No separate repository for `NotificationDeliveryAttempt` — managed as part of the `Notification` aggregate via cascade.

## 4. DTOs (`dto` package)

Lombok on all: `@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder`.

**`NotificationSendRequest`**: `recipientId (@NotNull UUID)`, `bookingId (@NotNull UUID)`, `eventType (@NotNull EventType)`. There is no `message` field — message content is never client-supplied, only generated server-side from the fixed templates in section 5, so callers can't inject arbitrary email content.

**`NotificationResponse`**: `id`, `recipientId`, `bookingId`, `notificationType`, `eventType`, `sendStatus`, `sentAt (nullable)`, `createdAt`.

**`ErrorResponse`**: same shape as prior phases (`timestamp`, `status`, `error`, `message`, `path`) — recreate locally.

## 5. Email templates — exact wording, not left to the AI's judgment

Implement as a single method `buildMessage(EventType eventType, String customerFullName, UUID bookingId)` returning a `Subject`/`Body` pair (a small local record or two strings), using this exact mapping:

| Event | Subject | Body |
|---|---|---|
| `BOOKING_PLACED` | `Booking Confirmed - #{bookingId}` | `Hi {fullName}, your delivery booking #{bookingId} has been placed successfully. We'll notify you once a courier is assigned.` |
| `COURIER_ASSIGNED` | `Courier Assigned - #{bookingId}` | `Hi {fullName}, a courier has been assigned to your booking #{bookingId} and will pick up your package soon.` |
| `PICKED_UP` | `Package Picked Up - #{bookingId}` | `Hi {fullName}, your package for booking #{bookingId} has been picked up by the courier.` |
| `IN_TRANSIT` | `Package In Transit - #{bookingId}` | `Hi {fullName}, your package for booking #{bookingId} is on its way to the destination.` |
| `DELIVERED` | `Package Delivered - #{bookingId}` | `Hi {fullName}, your package for booking #{bookingId} has been delivered successfully. Thank you for using our service!` |
| `CANCELLED` | `Booking Cancelled - #{bookingId}` | `Hi {fullName}, your booking #{bookingId} has been cancelled.` |

`{bookingId}` is the full UUID; `{fullName}` is the customer's `fullName` as returned by User & Auth Service. Plain text email only (`SimpleMailMessage`) — no HTML templating in this phase.

## 6. `UserAuthServiceClient` (`client` package)

```java
Optional<InternalCustomerResponse> getCustomer(UUID recipientId);   // GET {user-auth.base-url}/internal/customers/{id}, X-Internal-Api-Key header
```
Recreate `InternalCustomerResponse` locally (`id`, `fullName`, `phoneNumber`, `accountStatus`, `email` — matching section 0.1's addition exactly). Configure the WebClient's JSON deserialization to ignore unknown properties (`@JsonIgnoreProperties(ignoreUnknown = true)` on the DTO, or an equivalent `ObjectMapper` setting), since this is intentionally a partial view of User & Auth Service's full response shape.

## 7. Business rules — `NotificationService` (service layer)

**Sending a notification** (`POST /internal/notifications/send`):
1. Call `getCustomer(recipientId)`. If empty, persist a `Notification` row anyway with `sendStatus = FAILED`, `messageContent = "Recipient not found"` (skip actually calling `buildMessage`), one `NotificationDeliveryAttempt` (`attemptNumber = 1`, `status = FAILED`, `errorMessage = "Recipient not found: " + recipientId`), and return `201` with that response — **do not throw an error back to the caller for this case**; a missing recipient is a recorded business outcome, not a service failure.
2. Otherwise, build subject/body via section 5's exact mapping using the customer's `fullName`.
3. Persist the `Notification` row first, `sendStatus = PENDING`, `notificationType = EMAIL`, `messageContent` = the body text.
4. Attempt to send via `JavaMailSender` (`SimpleMailMessage`, `from` = `notification.mail.from-address`, `to` = customer's email, the built subject/body).
5. On success: update `sendStatus = SENT`, `sentAt = now()`; add one `NotificationDeliveryAttempt` (`attemptNumber = 1`, `provider = "Gmail SMTP"`, `status = SUCCESS`).
6. On any `MailException`: update `sendStatus = FAILED`; add one `NotificationDeliveryAttempt` (`attemptNumber = 1`, `provider = "Gmail SMTP"`, `status = FAILED`, `errorMessage` = the exception's message, truncated to 255 chars if needed).
7. Both outcomes return `201` + `NotificationResponse` — **a failed email send is not an HTTP error**, it's a normal, recorded outcome. Only a genuine unexpected server error (e.g. a database failure while persisting) should ever produce a `5xx` from this endpoint.
8. No retry logic in this phase — exactly one `NotificationDeliveryAttempt` per send request, always `attemptNumber = 1`.

**Fetching one notification** (`GET /internal/notifications/{id}`) — `200` + `NotificationResponse`, or `404`.

**Fetching a booking's notifications** (`GET /internal/notifications/booking/{bookingId}`) — `200` + `List<NotificationResponse>` ordered oldest-first, empty list if none exist (not a `404`).

## 8. Security configuration — Notification Service has no public or JWT-authenticated endpoints at all

Every endpoint in this service lives under `/internal/**` and is protected only by the API key — there is no `JwtAuthenticationFilter`, no `Role` enum, no `@PreAuthorize` anywhere in this service.

- `InternalApiKeyFilter extends OncePerRequestFilter`: applies to all `/internal/**` paths; compares `X-Internal-Api-Key` header against `internal.api-key`; on mismatch/absence, writes a `401` `ErrorResponse` directly and stops the chain.
- `SecurityFilterChain`: `permitAll()` only for `/swagger-ui/**`, `/v3/api-docs/**`; require the API-key filter to have set request as validated for everything else (or simply rely entirely on the filter short-circuiting invalid requests — don't add Spring Security `.authenticated()` role logic here, since there's no principal/JWT concept in this service at all). CSRF disabled, stateless.

## 9. What NOT to do (Notification Service)

- Don't expose any endpoint outside `/internal/**`.
- Don't add SMS or push notification logic — `notificationType` stays `EMAIL` always in this phase; the enum values exist in the schema for later, not for you to implement now.
- Don't add a retry worker/scheduler.
- Don't notify couriers — `recipientId` is always a customer ID in this phase.
- Don't let a mail-send failure become an HTTP error response — see section 7, step 7.

---

## PART B — Delivery & Tracking Service

## 1. Existing entities — reference only

`StatusUpdate`: `id`, `bookingId (UUID, logical ref)`, `status (BookingStatus enum: PLACED, ASSIGNED, PICKED_UP, IN_TRANSIT, DELIVERED, CANCELLED)`, `note (String, nullable)`, `updatedByCourierId (UUID, nullable, logical ref)`, `createdAt` (no `updatedAt` — immutable row).

`CurrentBookingStatus`: `bookingId (UUID, @Id — this is the PK, not a generated one)`, `status (BookingStatus enum)`, `latestStatusUpdateId (UUID/StatusUpdate relation — @ManyToOne @JoinColumn(name="latest_status_update_id") per `DATABASE.md`)`, `updatedAt`.

Reuse `BookingStatus` exactly as already defined in this service's `model` package — do not create a second copy inside `dto` or `client`. This is a separate Java enum from Booking Service's own `BookingStatus` (different service, different codebase) but must have identical constant names.

**Do not add any entity relationships or fields beyond what already exists** — unlike Notification Service, no permitted entity change is needed here.

## 2. `application.yml` additions

```yaml
jwt:
  secret-key: "MUST_BE_BYTE_FOR_BYTE_IDENTICAL_TO_USER_AUTH_SERVICE_SECRET"
internal:
  api-key: "MUST_MATCH_EVERY_OTHER_SERVICE_INTERNAL_API_KEY"
services:
  booking:
    base-url: "http://localhost:8082"
  notification:
    base-url: "http://localhost:8084"
```
Check `pom.xml` for the same JWT (`jjwt-api`/`jjwt-impl`/`jjwt-jackson`) and WebClient (`spring-boot-starter-webflux`) dependencies used in Phases 1–2. If missing, tell me rather than adding them.

## 3. Repository layer (`repository` package)

`StatusUpdateRepository extends JpaRepository<StatusUpdate, UUID>`:
- `List<StatusUpdate> findByBookingIdOrderByCreatedAtAsc(UUID bookingId)`

`CurrentBookingStatusRepository extends JpaRepository<CurrentBookingStatus, UUID>`:
- Use the inherited `findById(bookingId)` directly — `bookingId` is already the primary key, no custom query method needed.

**Do not write any code that manually inserts or updates `CurrentBookingStatus` rows.** That table is kept in sync exclusively by the existing database trigger on `status_updates` (already defined per `DATABASE.md`) — this service only ever reads it, never writes it. If a `CurrentBookingStatus` row doesn't exist yet for a booking (no status update has ever been recorded through this service), treat that as "no tracking history yet," not an error — fall back to the booking's own `status` field from Booking Service's response in that case (see section 7).

## 4. DTOs (`dto` package)

Lombok on all.

**`StatusUpdateRequest`**: `status (@NotNull BookingStatus)`, `note (nullable, @Size(max = 255))`.

**`StatusUpdateResponse`**: `id`, `bookingId`, `status`, `note`, `updatedByCourierId (nullable)`, `createdAt`.

**`BookingTrackingResponse`**: `bookingId`, `customerId`, `courierId (nullable)`, `currentStatus (BookingStatus)`, `history (List<StatusUpdateResponse>, oldest first)`.

**`BookingResponse`** (local, partial copy of Booking Service's shape — only the fields Tracking Service actually needs): `id`, `customerId`, `courierId (nullable)`, `status (BookingStatus)`. Annotate with `@JsonIgnoreProperties(ignoreUnknown = true)` since Booking Service's real response has more fields than this.

**`ErrorResponse`**: same shape as prior phases, recreated locally.

## 5. `BookingServiceClient` (`client` package)

```java
Optional<BookingResponse> getBooking(UUID bookingId);                          // GET {booking.base-url}/internal/bookings/{id}, X-Internal-Api-Key header; empty on 404
BookingResponse updateStatus(UUID bookingId, BookingStatus newStatus);         // PATCH {booking.base-url}/internal/bookings/{id}/status, body {"status": "..."}
```
`updateStatus` must propagate a `409` response from Booking Service as this service's own `InvalidStatusTransitionException` (see section 8) rather than swallowing it — that response means Booking Service's own state machine rejected the transition (e.g. a race condition), and the caller (courier) needs to see that as a real error, not a silent success. Any connection failure or `5xx` → `DownstreamServiceException` → `503`.

## 6. `NotificationServiceClient` (`client` package) — fire-and-forget, identical pattern to section 0.2

```java
void notify(UUID recipientCustomerId, UUID bookingId, EventType eventType);
```
Recreate `EventType` locally (`BOOKING_PLACED, COURIER_ASSIGNED, PICKED_UP, IN_TRANSIT, DELIVERED, CANCELLED`) matching Notification Service's values exactly. Same rule as section 0.2: **catch every exception internally, never throw, never block or fail the calling status-update flow.**

## 7. Business rules — service layer

**Recording a status update** (`POST /api/tracking/bookings/{id}/status`, courier only):
1. Call `getBooking(id)`. If empty → `BookingNotFoundException` → `404`.
2. If `booking.courierId != authenticatedUserId` → `403 Forbidden` (a courier can only update bookings assigned to them).
3. Reject the request outright if `status` is anything other than `PICKED_UP`, `IN_TRANSIT`, or `DELIVERED` → `400 Bad Request` (`PLACED`, `ASSIGNED`, and `CANCELLED` are never set through this endpoint).
4. Validate the requested transition against `booking.status` (the value just fetched from Booking Service — the actual source of truth): only `ASSIGNED → PICKED_UP`, `PICKED_UP → IN_TRANSIT`, and `IN_TRANSIT → DELIVERED` are valid. Anything else → `InvalidStatusTransitionException` → `409 Conflict`, message naming both the current and requested status.
5. Call `updateStatus(id, newStatus)` on Booking Service. If this throws `InvalidStatusTransitionException` (propagated per section 5), let it surface as `409` — don't record a `StatusUpdate` row if this call fails.
6. On success, persist a new `StatusUpdate` row (`bookingId = id`, `status = newStatus`, `note` from the request, `updatedByCourierId = authenticatedUserId`) — do **not** touch `CurrentBookingStatus` directly; the database trigger handles it.
7. Call `notificationServiceClient.notify(booking.customerId, id, EventType matching newStatus)` — fire-and-forget, per section 6.
8. Return `200` + `StatusUpdateResponse`.

**Getting full tracking info** (`GET /api/tracking/bookings/{id}`):
1. Call `getBooking(id)`. If empty → `404`.
2. Ownership: `CUSTOMER` must match `booking.customerId`; `COURIER` must match `booking.courierId`; `ADMIN` always allowed. Otherwise `403`.
3. Look up `CurrentBookingStatusRepository.findById(id)`. If present, use its `status` as `currentStatus`. If absent (no tracking-recorded transition has happened yet — booking is still `PLACED` or was just `ASSIGNED` with no courier action yet), use `booking.status` from the Booking Service response instead as `currentStatus`.
4. Fetch full history via `findByBookingIdOrderByCreatedAtAsc(id)` (may be empty).
5. Return `200` + `BookingTrackingResponse`.

**Getting only the history** (`GET /api/tracking/bookings/{id}/history`): same ownership check as above, then return `200` + `List<StatusUpdateResponse>` (possibly empty).

## 8. Custom exceptions + Global handler

`BookingNotFoundException` (`404`), `InvalidStatusTransitionException` (`409`), `DownstreamServiceException` (`503`), plus the standard `MethodArgumentNotValidException` → `400` and fallback `Exception` → `500` handlers, same `@RestControllerAdvice GlobalExceptionHandler` pattern as prior phases, recreated in this service.

## 9. Security configuration — Delivery & Tracking Service, JWT-validated, no inbound internal endpoints

This service has customer/courier-facing endpoints only (no `/internal/**` endpoints of its own — it's purely a *caller* of Booking's and Notification's internal APIs, never a callee). Recreate the exact same `JwtAuthenticationFilter` + `JwtService` (`extractUserId`, `extractRole`, `isTokenValid`) pattern as Phases 1–2, validating tokens signed by User & Auth Service's shared secret. `Role` enum recreated locally (`CUSTOMER, COURIER, ADMIN`). `SecurityFilterChain`: `permitAll()` for `/swagger-ui/**`, `/v3/api-docs/**`; everything else `.authenticated()`. `@EnableMethodSecurity` enabled, `@PreAuthorize("hasRole('COURIER')")` on the status-update endpoint. Stateless, CSRF disabled. **Do not add an `InternalApiKeyFilter` to this service** — it has no internal endpoints to protect; it only sends the API key as an outbound header when calling Booking/Notification.

## 10. Controllers — Delivery & Tracking Service

| Method | Path | Access | Request | Success Response |
|---|---|---|---|---|
| POST | `/api/tracking/bookings/{id}/status` | `@PreAuthorize("hasRole('COURIER')")` (ownership enforced in service layer) | `StatusUpdateRequest` | `200` + `StatusUpdateResponse` |
| GET | `/api/tracking/bookings/{id}` | authenticated (ownership/admin enforced in service layer) | — | `200` + `BookingTrackingResponse` |
| GET | `/api/tracking/bookings/{id}/history` | authenticated (ownership/admin enforced in service layer) | — | `200` + `List<StatusUpdateResponse>` |

Add `@Tag`/`@Operation` Swagger annotations matching this table, only if springdoc is already on the classpath (check first).

## 11. What NOT to do (Delivery & Tracking Service)

- Don't write to `CurrentBookingStatus` from application code — the DB trigger owns that table.
- Don't let a Notification Service failure affect the status-update response in any way.
- Don't allow a customer to call the status-update endpoint — courier only.
- Don't implement GPS/location pinging — explicitly out of MVP scope per the original architecture plan.
- Don't touch Booking Service beyond the additions in section 0.2, and don't touch User & Auth Service beyond section 0.1.

## After generating (both services)

List anything from this prompt you couldn't implement exactly as specified, and why — don't silently approximate or skip.
