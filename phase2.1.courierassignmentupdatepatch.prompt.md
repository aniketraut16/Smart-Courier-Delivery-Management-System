# Prompt: Revise Booking Service — Remove Auto-Assignment, Add Courier Quote Endpoint, Manual Courier Selection

Copy everything below the line into your AI coding assistant, in a session with access to the actual repo (`booking-service` module, plus the outer project-root README for the doc update in section 10).

---

## Context — what already exists

`booking-service` (Phase 2) is already fully built: `Booking`/`BookingAddress`/`PackageDetail` entities, `BookingRepository`, DTOs, `UserAuthServiceClient`, `NotificationServiceClient`, `FareCalculationService` (haversine distance + flat base-fare/rate-per-km), `GlobalExceptionHandler`, JWT security, and controllers for `POST /api/bookings`, `GET /api/bookings/{id}`, `GET /api/bookings`, `POST /api/bookings/{id}/cancel`, `GET /internal/bookings/{id}`, `PATCH /internal/bookings/{id}/status`. Read the existing code first — this prompt only tells you what to change; everything not mentioned below stays exactly as it is.

**Do not touch** `user-auth-service`, `tracking-service`, or `notification-service` — every change in this prompt is scoped to `booking-service` and the two README files named in section 10.

## Summary of what's changing (read this before touching code)

1. **Auto-assignment is removed entirely.** Booking creation no longer picks a courier automatically.
2. **A new read-only quote endpoint** lets the customer submit booking details (no courier yet) and get back the list of currently available couriers with a per-vehicle-type fare estimate, before committing to a booking.
3. **`courierId` becomes a required field** on the booking-creation request — the customer picks a courier from the quote results and submits that ID.
4. **Fare now varies by vehicle type**, not a single flat rate — same vehicle type always produces the same fare for the same distance.
5. **Couriers are never auto-marked `BUSY`.** A courier can carry multiple active deliveries at once. Remove every call this service makes to the assign/release internal endpoints — they contradict this rule now.
6. **A freshness check at placement time** re-verifies the chosen courier is still `AVAILABLE` right before saving, to catch the case where the courier went `OFFLINE` in the window between quote and placement.

## 1. Entities — no changes needed

`Booking.courierId` is already a nullable `UUID` column at the database level — leave the schema and entity untouched. The column simply gets populated at creation time now instead of via async assignment; no migration needed.

## 2. Config changes — `application.yml`

**Remove** the old flat fare config:
```yaml
booking:
  fare:
    base-fare: 50.00
    rate-per-km: 12.00
```
**Replace with** a per-vehicle-type structure:
```yaml
booking:
  fare:
    vehicle-rates:
      BIKE:
        base-fare: 20.00
        rate-per-km: 8.00
      SCOOTER:
        base-fare: 25.00
        rate-per-km: 9.00
      CAR:
        base-fare: 50.00
        rate-per-km: 12.00
      VAN:
        base-fare: 80.00
        rate-per-km: 18.00
```
Everything else in `application.yml` (DB connection, JWT secret, internal API key, service base-URLs) stays untouched.

## 3. `FareCalculationService` — modify, don't rewrite from scratch

Keep the existing haversine distance method exactly as-is (same formula, same rounding: `BigDecimal`, `RoundingMode.HALF_UP`, 2 decimals) — just make sure it's callable on its own:
```java
BigDecimal calculateDistanceKm(BigDecimal pickupLat, BigDecimal pickupLon, BigDecimal dropLat, BigDecimal dropLon);
```
Replace the old flat fare formula with a vehicle-type-aware one, bound to the new config from section 2 (a `@ConfigurationProperties(prefix = "booking.fare")` class with a `Map<VehicleType, VehicleRate>` field, `VehicleRate` holding `baseFare` and `ratePerKm`, is the cleanest way to bind this — use that approach):
```java
BigDecimal calculateFare(VehicleType vehicleType, BigDecimal distanceKm);
// = vehicleRates.get(vehicleType).baseFare + distanceKm * vehicleRates.get(vehicleType).ratePerKm
// same BigDecimal/HALF_UP/2-decimal rounding as before
```
If a `vehicleType` has no matching entry in config (shouldn't happen given the fixed enum, but guard anyway), throw an `IllegalStateException` with a clear message naming the missing vehicle type — this is a configuration bug, not a user-facing error.

## 4. DTO changes

**`CreateBookingRequest`** — add one field: `courierId (@NotNull UUID)`. Everything else on this DTO (`pickupAddress`, `dropAddress`, `packageDetail`) is unchanged.

**New: `BookingQuoteRequest`** — identical shape to `CreateBookingRequest` minus `courierId`: `pickupAddress (@NotNull @Valid AddressDto)`, `dropAddress (@NotNull @Valid AddressDto)`, `packageDetail (@NotNull @Valid PackageDetailDto)`.

**New: `CourierFareOption`**: `courierId (UUID)`, `courierName (String)`, `vehicleType (VehicleType)`, `vehicleNumber (String, nullable)`, `estimatedFare (BigDecimal)`.

**New: `BookingQuoteResponse`**: `distanceKm (BigDecimal)`, `options (List<CourierFareOption>)` — `options` is an empty list (not an error, not `null`) when no couriers are currently available.

`BookingResponse` is unchanged — it already has a `courierId` field from Phase 2.

## 5. `UserAuthServiceClient` — remove two methods entirely

**Delete** `assignCourier(UUID courierId)` and `releaseCourier(UUID courierId)` completely from this client class — they are never called anywhere anymore. Keep `getCustomer(UUID customerId)` and `getAvailableCouriers()` exactly as they are; both are reused (and `getAvailableCouriers()` is now called twice as often — once for quoting, once for the placement-time freshness check).

## 6. Service layer — exact replacement logic

### New method: quoting a booking (backs the new endpoint)

1. Validate `BookingQuoteRequest` (bean validation).
2. Call `getCustomer(authenticatedUserId)`; if empty or `accountStatus != ACTIVE`, throw the existing `CustomerNotEligibleException` → `403` (reuse, don't recreate).
3. Compute `distanceKm` via `calculateDistanceKm(...)` using the request's pickup/drop coordinates.
4. Call `getAvailableCouriers()`. If empty, return a `BookingQuoteResponse` with the computed `distanceKm` and an empty `options` list — this is a normal, successful outcome, not an error.
5. Otherwise, for each available courier, compute `calculateFare(courier.vehicleType, distanceKm)` and build one `CourierFareOption` per courier.
6. Return `200` + `BookingQuoteResponse`. **This endpoint never persists anything** — no booking, no quote record, nothing written to the database.

### Rewritten method: creating a booking (replaces the old auto-assign flow entirely)

1. Validate `CreateBookingRequest` (now including the required `courierId`).
2. Call `getCustomer(authenticatedUserId)`; same eligibility check as before (`CustomerNotEligibleException` → `403`).
3. Call `getAvailableCouriers()` **fresh** (do not reuse any earlier quote response — there is no stored quote to reuse) and search for an entry matching the submitted `courierId`. **If no match is found, throw a new `CourierNotAvailableException` → `409 Conflict`**, message: `"Selected courier is no longer available. Please request a new quote and choose another courier."` This is the freshness check from the context summary — it's what catches a courier going `OFFLINE` in the window between quote and placement.
4. Compute `distanceKm` via `calculateDistanceKm(...)`.
5. Compute `fareEstimate` via `calculateFare(matchedCourier.vehicleType, distanceKm)` — **never accept a client-supplied fare; there is no fare field on `CreateBookingRequest` at all.**
6. Persist the `Booking` **directly with `status = ASSIGNED`**, `courierId` set to the submitted ID, `assignedAt = now()`, along with its `BookingAddress` (×2) and `PackageDetail`, as one `@Transactional` aggregate save — there is no more intermediate unassigned `PLACED` state for a newly created booking, since a courier is now always chosen up front.
7. Fire `notify(customerId, booking.getId(), EventType.BOOKING_PLACED)`, then unconditionally fire `notify(customerId, booking.getId(), EventType.COURIER_ASSIGNED)` — both always fire now, since assignment always succeeds by the time this line is reached (step 3 already would have thrown if it couldn't).
8. Return `201` + `BookingResponse`.

**Remove entirely:** the old "attempt assignment, fall back to unassigned `PLACED` if no courier available" branch — there is no fallback path anymore; an unavailable courier is now a `409`, not a silent unassigned booking.

### Modified method: cancelling a booking

Keep every existing rule (only from `PLACED`-or-`ASSIGNED`, ownership check, sets `CANCELLED`/`cancelledAt`, fires the `CANCELLED` notification) **except**: remove the call to `releaseCourier(courierId)` entirely — delete that line, nothing replaces it. A cancelled booking simply no longer references an active job for that courier; there is no availability state to release since the courier was never marked busy in the first place.

Note: since every booking is now created with `status = ASSIGNED` (never `PLACED`), the "only cancellable from `PLACED` or `ASSIGNED`" rule in practice now only ever applies to `ASSIGNED` — leave the check exactly as it already handles both values, don't narrow it; it's harmless and future-proof if that ever changes.

### Modified method: internal status transition (`PATCH /internal/bookings/{id}/status`)

Keep the exact same forward-only state machine (`ASSIGNED → PICKED_UP → IN_TRANSIT → DELIVERED`) and the same `409` behavior for invalid transitions — **except**: on transition to `DELIVERED`, remove the call to `releaseCourier(courierId)` entirely. Still set `deliveredAt = now()` on that transition; just delete the release call.

## 7. New exception

`CourierNotAvailableException` — add to the existing exceptions package, mapped in `GlobalExceptionHandler` to `409 Conflict` with an `ErrorResponse` body, same pattern as the other exceptions already handled there.

## 8. Controller — one new endpoint, one modified endpoint

| Method | Path | Access | Request | Success Response |
|---|---|---|---|---|
| POST | `/api/bookings/quote` | `@PreAuthorize("hasRole('CUSTOMER')")` | `BookingQuoteRequest` | `200` + `BookingQuoteResponse` |
| POST | `/api/bookings` | `@PreAuthorize("hasRole('CUSTOMER')")` | `CreateBookingRequest` (now includes `courierId`) | `201` + `BookingResponse`, or `409` if the courier is no longer available |

`/api/bookings/quote` uses `POST` rather than `GET` despite being read-only, because the request needs a body — add a one-line Swagger `@Operation` description noting it performs no writes, to make that clear to anyone reading the API docs. Every other existing endpoint in this service (`GET /api/bookings/{id}`, `GET /api/bookings`, `POST /api/bookings/{id}/cancel`, `GET /internal/bookings/{id}`, `PATCH /internal/bookings/{id}/status`) is unchanged.

## 9. What NOT to do

- Don't reintroduce any form of automatic courier assignment.
- Don't call `assignCourier`/`releaseCourier` anywhere — those methods no longer exist on the client.
- Don't add a concurrency lock, reservation table, or distributed lock for courier selection — the freshness re-check in section 6 is the intended and sufficient mechanism, given couriers have unlimited concurrent capacity.
- Don't persist quote requests or return a reusable "quote ID" — quoting is fully stateless; the real fare is always recomputed independently at placement time.
- Don't accept a client-supplied fare value anywhere.
- Don't add a cap on how many active bookings one courier can hold — that's explicitly unlimited by design.

## 10. Documentation updates — both README files

**First, locate the current section in each file that describes booking creation/assignment** (likely titled something like "Booking Flow", "Assignment Logic", or similar — read the file to find the right heading rather than assuming exact wording) and replace it with the following, adapted only in heading level/style to match the surrounding document:

> ### Booking Creation Flow (updated)
>
> Booking Service no longer auto-assigns couriers. The flow is now:
>
> 1. **Get a quote** — `POST /api/bookings/quote` with pickup address, drop address, and package details. Returns the computed distance and a list of currently available couriers, each with a fare estimate based on their vehicle type. Two couriers with the same vehicle type always quote the same fare for the same trip. This call is read-only and does not create anything.
> 2. **Place the order** — `POST /api/bookings` with the same details plus the chosen `courierId`. The service re-verifies that courier is still available at the exact moment of booking (in case they went offline in the meantime) and rejects with `409 Conflict` if not, prompting the customer to request a new quote. The booking is created directly in `ASSIGNED` status — there is no unassigned state.
> 3. Fare is always calculated server-side, by vehicle type and distance — never trusted from the client.
>
> **Courier capacity:** couriers are never automatically marked busy. A single courier can carry multiple active deliveries at once. The only way a courier stops receiving new assignments is by manually setting their own status to `OFFLINE` via `PUT /api/couriers/me/availability` — the system never does this on their behalf.

Apply this to:
- **`booking-service`'s internal `README.md`** — update the relevant section, and also update the service's endpoint list/table to include `POST /api/bookings/quote` and to note `courierId` is now a required field on `POST /api/bookings`.
- **The project-root `README.md`** — update the Booking Service section the same way, keeping the surrounding architecture description (services, tech stack, other sections) untouched.

## After generating

List anything from this prompt you couldn't implement exactly as specified, and why — don't silently approximate or skip.
