# Prompt: Build Booking Service (Phase 2) — Repositories, DTOs, Assignment Logic, JWT Validation, Client, Controllers

Copy everything below the line into your AI coding assistant, in a session with access to the actual repo (both `booking-service` and `user-auth-service` modules).

---

## Context — what already exists, don't rebuild it

This is a Spring Boot 3 / Java 21 microservices project. Three things are already built and working — do not modify them except for the one explicit addition in section 0 below:

1. **Database schema** — `booking_service_db` (tables `bookings`, `booking_addresses`, `package_details`) and `user_service_db` (tables `users`, `courier_profiles`) already exist in Postgres, exactly as defined in `DATABASE.md`. No table or column changes.
2. **JPA entities** — `Booking`, `BookingAddress`, `PackageDetail` already exist in `booking-service`'s `model` package, matching the DB schema exactly. Read them first, reuse their exact field/enum names — do not recreate or rename anything.
3. **User & Auth Service (Phase 1) — fully built and running**, at `user-auth-service`, exposing:
   - `POST /api/auth/register`, `POST /api/auth/login` (JWT issuance)
   - `GET /api/users/me`, `PUT /api/users/me`
   - `GET/PUT /api/couriers/me/availability`
   - `GET /api/admin/users`
   - `GET /internal/customers/{id}`, `GET /internal/couriers/available` (internal-API-key protected)
   - JWTs are signed HS256 with a shared secret (`jwt.secret-key`), so any service holding the same secret can validate a token without calling User & Auth Service per request.

Now build Booking Service (Phase 2) on top of this. Base package to use (adjust only if the real one differs — verify against the existing entity files): `com.couriersystem.courier.booking`

## 0. Required addition to the already-built User & Auth Service — do this first

Booking Service needs to flip a courier's availability when it assigns/releases them, but Phase 1's `PUT /api/couriers/me/availability` deliberately rejects `BUSY` (that's a courier self-service endpoint, not meant for system use). Add exactly these two new internal endpoints to `user-auth-service` — nothing else in that service changes:

| Method | Path | Access | Behavior |
|---|---|---|---|
| POST | `/internal/couriers/{courierId}/assign` | internal API key only | Sets `availabilityStatus = BUSY`, increments `activeBookingCount` by 1. Returns `200` + updated `InternalCourierResponse`, or `404` if courier not found. |
| POST | `/internal/couriers/{courierId}/release` | internal API key only | Sets `availabilityStatus = AVAILABLE`, decrements `activeBookingCount` by 1 (floor at 0, never negative). Returns `200` + updated `InternalCourierResponse`, or `404` if courier not found. |

These reuse the existing `InternalApiKeyFilter`, `CourierProfileRepository`, and `InternalCourierResponse` DTO from Phase 1 — no new infrastructure needed there, just two new controller methods + one new service method each.

## 1. Existing entities — reference only

`Booking`: `id`, `customerId (UUID, logical ref — no relation)`, `courierId (UUID, nullable, logical ref — no relation)`, `fareEstimate (BigDecimal)`, `distanceKm (BigDecimal, nullable)`, `status (BookingStatus enum: PLACED, ASSIGNED, PICKED_UP, IN_TRANSIT, DELIVERED, CANCELLED)`, `assignedAt`, `deliveredAt`, `cancelledAt`, `createdAt`, `updatedAt`.

`BookingAddress`: `id`, `booking (Booking, @ManyToOne @JoinColumn(name="booking_id"))`, `addressType (AddressType enum: PICKUP, DROP)`, `addressLine`, `city`, `state (nullable)`, `postalCode (nullable)`, `latitude (BigDecimal, nullable)`, `longitude (BigDecimal, nullable)`, `createdAt`.

`PackageDetail`: `id`, `booking (Booking, @OneToOne @JoinColumn(name="booking_id"))`, `packageType (PackageType enum: DOCUMENT, GENERAL, FRAGILE, ELECTRONICS, FOOD)`, `weightKg (BigDecimal, nullable)`, `description (String, nullable)`, `isFragile (boolean)`, `createdAt`.

**One permitted entity change:** if `Booking` does not already expose `@OneToMany(mappedBy = "booking", cascade = CascadeType.ALL, orphanRemoval = true) List<BookingAddress> addresses` and `@OneToOne(mappedBy = "booking", cascade = CascadeType.ALL) PackageDetail packageDetail`, add both now — the service layer needs to persist a booking with its two addresses and package detail as one aggregate in a single `save()` call. Change nothing else in these three entity files.

## 2. `application.yml` additions

```yaml
jwt:
  secret-key: "MUST_BE_BYTE_FOR_BYTE_IDENTICAL_TO_USER_AUTH_SERVICE_SECRET"
internal:
  api-key: "MUST_MATCH_USER_AUTH_SERVICE_INTERNAL_API_KEY"
services:
  user-auth:
    base-url: "http://localhost:8081"   # replace with the real running address
booking:
  fare:
    base-fare: 50.00
    rate-per-km: 12.00
```
Leave every existing property (DB connection, server port, etc.) untouched. If a JWT library (`jjwt-api`/`jjwt-impl`/`jjwt-jackson`) and a WebClient dependency (`spring-boot-starter-webflux`, needed for `WebClient` even in a non-reactive MVC app) aren't already in `pom.xml`, tell me rather than adding them yourself.

## 3. Repository layer (`repository` package)

`BookingRepository extends JpaRepository<Booking, UUID>`:
- `Page<Booking> findByCustomerId(UUID customerId, Pageable pageable)`
- `Page<Booking> findByCustomerIdAndStatus(UUID customerId, BookingStatus status, Pageable pageable)`
- `Page<Booking> findByCourierId(UUID courierId, Pageable pageable)`
- `Page<Booking> findByCourierIdAndStatus(UUID courierId, BookingStatus status, Pageable pageable)`

No separate repositories for `BookingAddress` or `PackageDetail` — they're managed as part of the `Booking` aggregate via cascade, never queried independently.

## 4. DTOs (`dto` package) — exact fields

All DTOs use Lombok: `@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder`.

**`AddressDto`** (used for both request and response, pickup and drop): `addressLine (@NotBlank @Size(max=255))`, `city (@NotBlank @Size(max=100))`, `state (nullable, @Size(max=100))`, `postalCode (nullable, @Size(max=20))`, `latitude (@NotNull, BigDecimal)`, `longitude (@NotNull, BigDecimal)`.

**`PackageDetailDto`**: `packageType (@NotNull)`, `weightKg (nullable, @Positive)`, `description (nullable, @Size(max=255))`, `isFragile (boolean, default false)`.

**`CreateBookingRequest`**: `pickupAddress (@NotNull @Valid AddressDto)`, `dropAddress (@NotNull @Valid AddressDto)`, `packageDetail (@NotNull @Valid PackageDetailDto)`. There is no `customerId` field — the creating customer is always the authenticated principal, never client-supplied.

**`BookingResponse`**: `id`, `customerId`, `courierId (nullable)`, `status`, `fareEstimate`, `distanceKm`, `pickupAddress (AddressDto)`, `dropAddress (AddressDto)`, `packageDetail (PackageDetailDto)`, `assignedAt (nullable)`, `deliveredAt (nullable)`, `cancelledAt (nullable)`, `createdAt`, `updatedAt`.

**`ErrorResponse`**: same shape as Phase 1 — `timestamp`, `status`, `error`, `message`, `path`. Recreate it here (each service owns its own copy; don't try to share a class across service module boundaries).

Reuse `Page<BookingResponse>` directly as the list-endpoint return type — no custom pagination wrapper.

## 5. Fare calculation — exact formula, not left to the AI's judgment

Haversine distance in km, using `booking.fare.base-fare` and `booking.fare.rate-per-km` from config:

```
R = 6371.0  (Earth radius, km)
Δlat = radians(dropLat - pickupLat)
Δlon = radians(dropLon - pickupLon)
a = sin²(Δlat/2) + cos(radians(pickupLat)) * cos(radians(dropLat)) * sin²(Δlon/2)
c = 2 * asin(sqrt(a))
distance_km = R * c

fare_estimate = base_fare + (distance_km * rate_per_km)
```
Round `distance_km` and `fare_estimate` to 2 decimal places using `BigDecimal` with `RoundingMode.HALF_UP`. Implement this as a small stateless `FareCalculationService` (or a `@Component` utility) — not inlined ad hoc inside the booking service method.

## 6. `UserAuthServiceClient` (`client` package) — exact method signatures, using `WebClient`

```java
Optional<InternalCustomerResponse> getCustomer(UUID customerId);      // GET {base-url}/internal/customers/{id}, X-Internal-Api-Key header; empty Optional on 404
List<InternalCourierResponse> getAvailableCouriers();                 // GET {base-url}/internal/couriers/available
InternalCourierResponse assignCourier(UUID courierId);                 // POST {base-url}/internal/couriers/{courierId}/assign
InternalCourierResponse releaseCourier(UUID courierId);                // POST {base-url}/internal/couriers/{courierId}/release
```
Recreate `InternalCustomerResponse` (`id`, `fullName`, `phoneNumber`, `accountStatus`) and `InternalCourierResponse` (`userId`, `fullName`, `vehicleType`, `vehicleNumber`) as DTOs local to Booking Service, matching Phase 1's shape field-for-field — each service keeps its own copy of these classes, since sharing a Java class across two services' JARs isn't something Database-per-Service microservices should be doing.

If any call to User & Auth Service fails with a connection error or 5xx, throw a `DownstreamServiceException` → mapped to `503 Service Unavailable`. Don't let assignment failures silently leave a booking half-processed — see section 7.

## 7. Business rules — service layer

**Creating a booking** (`POST /api/bookings`, `CUSTOMER` only):
1. Validate the request (bean validation on `CreateBookingRequest`).
2. Call `getCustomer(authenticatedUserId)`; if empty or `accountStatus != ACTIVE`, throw `CustomerNotEligibleException` → `403 Forbidden`.
3. Compute `distanceKm` and `fareEstimate` per section 5.
4. Persist the `Booking` (status `PLACED`) with its `BookingAddress` (×2) and `PackageDetail` as one aggregate save, inside a `@Transactional` method.
5. Attempt assignment: call `getAvailableCouriers()`. If the list is non-empty, take the first courier, call `assignCourier(courierId)`, then update the just-created booking: `courierId` set, `status = ASSIGNED`, `assignedAt = now()`, and save again. If the list is empty, leave the booking as `PLACED` with no courier — this is a normal outcome, not an error.
6. If step 5's `assignCourier` call itself fails after a courier was already picked from the list, log the failure and leave the booking in `PLACED` (do not fail the whole booking creation — the customer still gets their booking, just unassigned for now).
7. Return `201` + `BookingResponse`.

**Fetching one booking** (`GET /api/bookings/{id}`):
- `CUSTOMER`: only if `booking.customerId == authenticatedUserId`, else `403`.
- `COURIER`: only if `booking.courierId == authenticatedUserId`, else `403`.
- `ADMIN`: always allowed.
- `404` if the booking doesn't exist at all.

**Listing bookings** (`GET /api/bookings`):
- `CUSTOMER`: always filtered to `customerId = authenticatedUserId`, regardless of any parameter — a customer can never list anyone else's bookings.
- `COURIER`: always filtered to `courierId = authenticatedUserId`.
- `ADMIN`: no filter, all bookings.
- Optional query param `status` (further filters within the above), plus standard `page`/`size` pagination.

**Cancelling a booking** (`POST /api/bookings/{id}/cancel`, `CUSTOMER` only, must own it):
- Allowed only from `PLACED` or `ASSIGNED` status. Any other current status → `InvalidBookingStateException` → `409 Conflict`.
- Set `status = CANCELLED`, `cancelledAt = now()`.
- If a courier was assigned (`courierId != null`), call `releaseCourier(courierId)` before returning.
- Return `200` + updated `BookingResponse`.

**Internal status transition** (`PATCH /internal/bookings/{id}/status`, internal API key only, body `{ "status": "PICKED_UP" | "IN_TRANSIT" | "DELIVERED" }`) — for the future Delivery & Tracking Service to call:
- Enforce this exact forward-only state machine, nothing else allowed: `ASSIGNED → PICKED_UP → IN_TRANSIT → DELIVERED`. Any other requested transition (skipping a step, moving from `PLACED`, moving from a terminal state, etc.) → `409 Conflict` with a message naming the current and requested status.
- On transition to `DELIVERED`: set `deliveredAt = now()` and call `releaseCourier(courierId)`.
- Return `200` + `BookingResponse`.

**Internal fetch** (`GET /internal/bookings/{id}`, internal API key only) — returns `200` + `BookingResponse`, or `404`.

## 8. JWT validation (`security` package) — validation only, no token generation here

Booking Service never issues tokens — only validates ones issued by User & Auth Service. Reuse the same approach as Phase 1: `JwtAuthenticationFilter extends OncePerRequestFilter` + a `JwtService` with only:
```java
UUID extractUserId(String token);
Role extractRole(String token);       // recreate the Role enum here too: CUSTOMER, COURIER, ADMIN — must match User & Auth Service's values exactly
boolean isTokenValid(String token);
```
Same `SecurityFilterChain` pattern as Phase 1: `permitAll()` for `/swagger-ui/**`, `/v3/api-docs/**`; `/internal/**` handled by its own `InternalApiKeyFilter` (recreate this too, identical logic to Phase 1's); everything else `.authenticated()`, with `@PreAuthorize` role checks on top where noted in section 7. `@EnableMethodSecurity` enabled. Stateless sessions, CSRF disabled.

## 9. Custom exceptions + Global handler

`CustomerNotEligibleException` (`403`), `BookingNotFoundException` (`404`), `InvalidBookingStateException` (`409`), `DownstreamServiceException` (`503`), plus reuse the `MethodArgumentNotValidException` → `400` and fallback `Exception` → `500` handlers from Phase 1's pattern. Same `@RestControllerAdvice GlobalExceptionHandler` structure, recreated in this service.

## 10. Controllers — exact endpoints

| Method | Path | Access | Request | Success Response |
|---|---|---|---|---|
| POST | `/api/bookings` | `@PreAuthorize("hasRole('CUSTOMER')")` | `CreateBookingRequest` | `201` + `BookingResponse` |
| GET | `/api/bookings/{id}` | authenticated (ownership enforced in service layer) | — | `200` + `BookingResponse` |
| GET | `/api/bookings` | authenticated (scoped per role) | query params `status` (optional), `page`, `size` | `200` + `Page<BookingResponse>` |
| POST | `/api/bookings/{id}/cancel` | `@PreAuthorize("hasRole('CUSTOMER')")` (ownership enforced in service layer) | — | `200` + `BookingResponse` |
| GET | `/internal/bookings/{id}` | internal API key only | path variable `id` | `200` + `BookingResponse`, or `404` |
| PATCH | `/internal/bookings/{id}/status` | internal API key only | `{ "status": "..." }` | `200` + `BookingResponse`, or `409` |

Add `@Tag`/`@Operation` Swagger annotations matching this table, same as Phase 1 (only if springdoc is already on the classpath — check first, tell me if it's missing rather than adding it).

## 11. What NOT to do

- Don't let the customer specify their own `customerId` or a courier's `courierId` in any request body — both always come from the authenticated principal or internal assignment logic.
- Don't implement a manual "retry assignment" endpoint for unassigned bookings — out of scope for this phase.
- Don't implement `status_updates`/tracking history here — that's Tracking's job in Phase 3; Booking Service only holds current `status`.
- Don't call the Maps/Distance external API — haversine only, per section 5.
- Don't touch `user-auth-service` beyond the two endpoints in section 0.

## After generating

List anything you couldn't implement exactly as specified, and why — don't silently approximate or skip.