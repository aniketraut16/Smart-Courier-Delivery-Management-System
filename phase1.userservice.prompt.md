You are working inside the existing `user-auth-service` module of a Spring Boot 3 / Java 21 microservices project. The `pom.xml`, `application.yml`, and the two JPA entities (`User`, `CourierProfile`) already exist and are correct — **do not modify entities, do not modify `pom.xml`, do not touch existing database-connection properties in `application.yml`.** Everywhere below, use exactly the field names, enum names, and package these entities already have — read them first before writing anything else. Do not invent, rename, or restructure anything not explicitly listed here. If something below is ambiguous or you think a different approach is better, stop and ask me — do not silently decide.

Base package to use (adjust only if the real package in the repo differs — verify against the existing entity files, don't assume): `com.couriersystem.courier.auth`

## 0. Prerequisite you must confirm, not perform

I have already run this against the database myself:

```sql
ALTER TABLE users DROP CONSTRAINT chk_users_role;
ALTER TABLE users ADD CONSTRAINT chk_users_role CHECK (role IN ('CUSTOMER','COURIER','ADMIN'));
```

Your one and only permitted entity change: add `ADMIN` as a third constant to the existing `Role` enum (wherever it's currently declared — nested in `User.java` or standalone in the `model` package, match its existing location). Change nothing else in `User.java` or `CourierProfile.java`.

## 1. Existing entities — reference only, reuse exactly as-is

`User`: `id (UUID)`, `fullName (String)`, `email (String)`, `phoneNumber (String)`, `passwordHash (String)`, `role (Role enum: CUSTOMER, COURIER, ADMIN)`, `accountStatus (AccountStatus enum: ACTIVE, INACTIVE, SUSPENDED)`, `createdAt`, `updatedAt`.

`CourierProfile`: `id (UUID)`, `user (User, via @OneToOne @JoinColumn(name="user_id"))` — access the raw ID via `courierProfile.getUser().getId()`, never add a duplicate `userId` field — `vehicleType (VehicleType enum: BIKE, SCOOTER, CAR, VAN)`, `vehicleNumber (String, nullable)`, `availabilityStatus (AvailabilityStatus enum: AVAILABLE, BUSY, OFFLINE)`, `activeBookingCount (Integer)`, `createdAt`, `updatedAt`.

Reuse `Role`, `AccountStatus`, `VehicleType`, `AvailabilityStatus` exactly as already defined. Do not create duplicate enum classes anywhere else in the codebase.

## 2. `application.yml` additions — the only file changes outside `model`/new packages

Add these three properties (with placeholder values), leaving every existing property untouched:

```yaml
jwt:
  secret-key: "REPLACE_WITH_A_BASE64_ENCODED_256_BIT_SECRET"
  expiration-ms: 86400000 # 24 hours
internal:
  api-key: "REPLACE_WITH_AN_INTERNAL_SERVICE_KEY"
```

Check `pom.xml` for a JWT library (`io.jsonwebtoken:jjwt-api` + `jjwt-impl` + `jjwt-jackson`, version 0.12.x). If it's missing, **do not add it yourself — tell me it's missing and stop for that part**; every other part of this prompt should still be completed.

## 3. Repository layer (`repository` package)

`UserRepository extends JpaRepository<User, UUID>`:

- `Optional<User> findByEmail(String email)`
- `boolean existsByEmail(String email)`
- `boolean existsByPhoneNumber(String phoneNumber)`
- `Page<User> findAll(Pageable pageable)` — already inherited, just confirm it's usable as-is

`CourierProfileRepository extends JpaRepository<CourierProfile, UUID>`:

- `Optional<CourierProfile> findByUserId(UUID userId)` — derived query navigating the `user` relationship (Spring Data supports `findByUser_Id`; alias it as `findByUserId` via `@Query` if the derived name resolution is ambiguous)

## 4. Enums (`enums` package) — new, do not touch existing ones from step 1

None needed beyond what already exists — do not create new enums for this phase.

## 5. DTOs (`dto` package) — exact fields, exact validation, nothing extra

All DTOs use Lombok: `@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder`.

**`RegisterRequest`**

- `fullName` — `@NotBlank @Size(max = 150)`
- `email` — `@NotBlank @Email @Size(max = 150)`
- `phoneNumber` — `@NotBlank @Size(max = 20)`
- `password` — `@NotBlank @Size(min = 8, max = 100)`
- `role` — `@NotNull` (type `Role`)
- `vehicleType` — nullable (type `VehicleType`), no bean-validation annotation (conditional requirement enforced in the service layer, not here)
- `vehicleNumber` — nullable `String`, `@Size(max = 20)`

**`LoginRequest`**

- `email` — `@NotBlank @Email`
- `password` — `@NotBlank`

**`AuthResponse`**

- `accessToken (String)`, `tokenType (String, always "Bearer")`, `expiresInMs (long)`, `userId (UUID)`, `role (Role)`, `fullName (String)`

**`UserProfileResponse`**

- `id (UUID)`, `fullName (String)`, `email (String)`, `phoneNumber (String)`, `role (Role)`, `accountStatus (AccountStatus)`, `createdAt (Instant/OffsetDateTime — match the entity's type exactly)`

**`UpdateProfileRequest`**

- `fullName` — `@NotBlank @Size(max = 150)`
- `phoneNumber` — `@NotBlank @Size(max = 20)`
  (Email is intentionally not updatable via this endpoint in this phase.)

**`CourierAvailabilityResponse`**

- `userId (UUID)`, `vehicleType (VehicleType)`, `vehicleNumber (String)`, `availabilityStatus (AvailabilityStatus)`, `activeBookingCount (Integer)`

**`UpdateAvailabilityRequest`**

- `availabilityStatus` — `@NotNull` (type `AvailabilityStatus`)

**`InternalCustomerResponse`** (for the internal API, minimal fields only)

- `id (UUID)`, `fullName (String)`, `phoneNumber (String)`, `accountStatus (AccountStatus)`

**`InternalCourierResponse`** (for the internal API)

- `userId (UUID)`, `fullName (String)`, `vehicleType (VehicleType)`, `vehicleNumber (String)`

**`ErrorResponse`**

- `timestamp (Instant)`, `status (int)`, `error (String)`, `message (String)`, `path (String)`

Do not add a `PageResponse` wrapper class — use Spring's built-in `Page<T>` / `PagedModel<T>` directly as the controller return type for the admin listing endpoint.

## 6. Business rules to enforce in the service layer (not the controller, not bean validation)

- Registration only accepts `role = CUSTOMER` or `role = COURIER`. If `role = ADMIN` is submitted, throw a custom `InvalidRegistrationRoleException` → mapped to `400 Bad Request`.
- If `role = COURIER` at registration and `vehicleType` is null, throw `MissingCourierDetailsException` → `400 Bad Request`.
- If `email` already exists → `EmailAlreadyExistsException` → `409 Conflict`.
- If `phoneNumber` already exists → `PhoneAlreadyExistsException` → `409 Conflict`.
- Registering a `COURIER` creates both the `User` row and a `CourierProfile` row (default `availabilityStatus = OFFLINE`, `activeBookingCount = 0`) in a single `@Transactional` method — never a `User` without its `CourierProfile` for courier accounts.
- Login: if email not found or password doesn't match the stored hash → `InvalidCredentialsException` → `401 Unauthorized` (same exception/message for both cases — don't reveal which one failed).
- Login: if `accountStatus != ACTIVE` → `AccountNotActiveException` → `403 Forbidden`.
- Updating availability: if the request tries to set `availabilityStatus = BUSY` directly, reject with `InvalidAvailabilityTransitionException` → `400 Bad Request` (message: `BUSY` is set automatically by the Delivery & Tracking Service when a booking is assigned, not settable by the courier directly). Only `AVAILABLE` and `OFFLINE` are settable via this endpoint.
- Any `/api/**` request with a missing/invalid/expired JWT → `401 Unauthorized` with a structured `ErrorResponse`, produced by the JWT filter itself, not by reaching a controller.
- Any `/internal/**` request missing or with a wrong `X-Internal-Api-Key` header → `401 Unauthorized`, short-circuited before reaching a controller.

## 7. `JwtService` (`security` package) — exact method signatures

```java
String generateToken(User user);              // HS256, claims: sub=user.getId().toString(), role=user.getRole().name(), email=user.getEmail(); expiry from jwt.expiration-ms
UUID extractUserId(String token);
Role extractRole(String token);
boolean isTokenValid(String token);            // signature + expiry check, returns false rather than throwing on any failure
```

Sign with the HS256 algorithm using `jwt.secret-key` (base64-decoded) from `application.yml` (`@Value` or a `@ConfigurationProperties` class — either is fine, pick one and use it consistently).

## 8. Security configuration (`config`/`security` package)

- `SecurityFilterChain` bean:
  - `permitAll()`: `POST /api/auth/register`, `POST /api/auth/login`, `/swagger-ui/**`, `/v3/api-docs/**`
  - `/internal/**`: handled entirely by a separate `InternalApiKeyFilter` (see below) — exclude from the standard JWT requirement, but do not mark as fully open; the API-key filter enforces its own check
  - everything else: `.authenticated()`
  - CSRF disabled (stateless JWT API), session management `STATELESS`
  - `@EnableMethodSecurity` enabled so `@PreAuthorize` works on controller methods
- `JwtAuthenticationFilter extends OncePerRequestFilter`: reads `Authorization: Bearer <token>` header; if present and valid (via `JwtService.isTokenValid`), sets `SecurityContextHolder` with an authority of `ROLE_<role>` (e.g. `ROLE_CUSTOMER`) and the extracted user ID as principal; if the header is present but invalid, respond `401` immediately with an `ErrorResponse` body and do not continue the filter chain; if the header is simply absent, continue the chain unauthenticated (so `permitAll()` routes still work).
- `InternalApiKeyFilter extends OncePerRequestFilter`: applies only to paths starting with `/internal/`; compares the `X-Internal-Api-Key` header against `internal.api-key`; on mismatch or absence, writes a `401` `ErrorResponse` body directly and stops the chain; on match, continues the chain (no `SecurityContext` authentication needed for this path — it's excluded from `.authenticated()` above).
- Register both filters with `addFilterBefore(..., UsernamePasswordAuthenticationFilter.class)`.
- Password encoding: `BCryptPasswordEncoder` bean, default strength.

## 9. Custom exceptions (`exception` package) + Global handler

Create these as plain `RuntimeException` subclasses, one per case from section 6: `InvalidRegistrationRoleException`, `MissingCourierDetailsException`, `EmailAlreadyExistsException`, `PhoneAlreadyExistsException`, `InvalidCredentialsException`, `AccountNotActiveException`, `InvalidAvailabilityTransitionException`, plus `UserNotFoundException` and `CourierProfileNotFoundException` for any lookup-by-ID failures.

`@RestControllerAdvice GlobalExceptionHandler` — one `@ExceptionHandler` per exception above, each returning the matching HTTP status (as listed in section 6) with an `ErrorResponse` body. Also add a fallback `@ExceptionHandler(MethodArgumentNotValidException.class)` → `400` with the first field-validation error message, and a fallback `@ExceptionHandler(Exception.class)` → `500` with a generic message (never leak stack traces or exception class names in the response body).

## 10. Controllers — exact endpoints, exact access rules

| Method | Path                            | Access                                | Request                     | Success Response                                                                |
| ------ | ------------------------------- | ------------------------------------- | --------------------------- | ------------------------------------------------------------------------------- |
| POST   | `/api/auth/register`            | public                                | `RegisterRequest`           | `201` + `UserProfileResponse`                                                   |
| POST   | `/api/auth/login`               | public                                | `LoginRequest`              | `200` + `AuthResponse`                                                          |
| GET    | `/api/users/me`                 | authenticated (any role)              | —                           | `200` + `UserProfileResponse`                                                   |
| PUT    | `/api/users/me`                 | authenticated (any role)              | `UpdateProfileRequest`      | `200` + `UserProfileResponse`                                                   |
| GET    | `/api/couriers/me/availability` | `@PreAuthorize("hasRole('COURIER')")` | —                           | `200` + `CourierAvailabilityResponse`                                           |
| PUT    | `/api/couriers/me/availability` | `@PreAuthorize("hasRole('COURIER')")` | `UpdateAvailabilityRequest` | `200` + `CourierAvailabilityResponse`                                           |
| GET    | `/api/admin/users`              | `@PreAuthorize("hasRole('ADMIN')")`   | query params `page`, `size` | `200` + `Page<UserProfileResponse>`                                             |
| GET    | `/internal/customers/{id}`      | internal API key only                 | path variable `id`          | `200` + `InternalCustomerResponse`, or `404` if not found                       |
| GET    | `/internal/couriers/available`  | internal API key only                 | —                           | `200` + `List<InternalCourierResponse>` (only `availabilityStatus = AVAILABLE`) |

"Own" resources (`/api/users/me`, `/api/couriers/me/**`) are resolved from the authenticated principal (the user ID set by `JwtAuthenticationFilter`), never from a request parameter — there is no way for a caller to fetch or modify another user's profile through these endpoints.

Add `@Tag` and `@Operation` Swagger annotations on every controller/method with a one-line description matching the table above. Do not add new springdoc configuration — only annotate, assuming springdoc is already on the classpath (check `pom.xml`; if it's missing, tell me and skip annotating rather than adding the dependency).

## 11. What NOT to do

- Don't add refresh tokens, token blacklisting, or logout endpoints — out of scope for this phase.
- Don't add rate limiting.
- Don't create an `Address` entity or endpoint — explicitly deferred (see note above this prompt).
- Don't add a public endpoint to create `ADMIN` users — that role is provisioned directly in the database, not through the API.
- Don't touch `BookingService` or `Delivery & Tracking Service` code — this prompt is scoped to `user-auth-service` only.

## After generating

List anything from this prompt you couldn't implement exactly as specified, and why — don't silently approximate or skip.
