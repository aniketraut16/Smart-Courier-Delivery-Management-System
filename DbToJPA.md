# Prompt: Generate JPA Entities from DATABASE.md

Copy everything below the line into your AI coding assistant, in a session that has access to the actual project repo (so it can check pom.xml/application.yml itself rather than guess).

---

You are working inside an existing Spring Boot microservices project. The project structure, `pom.xml`, and `application.yml` for every service are already complete and working — **do not touch build files, configuration, or anything outside each service's `model` package.**

Your only task: create JPA entity classes inside the `model` package of each service, so that they exactly match the schema defined in the attached `DATABASE.md` file. The PostgreSQL databases and tables described in `DATABASE.md` **already exist and cannot be changed** — you are writing code to match the database, never the other way around. Do not add, rename, or drop any table or column, and do not assume `spring.jpa.hibernate.ddl-auto` is anything other than `validate` or `none` — check each service's `application.yml` and flag it to me if it's set to `update` or `create`, but don't change it yourself.

## Before you write any code

1. Read `DATABASE.md` fully — it is the single source of truth for every table, column, type, constraint, index, and relationship.
2. Check one service's `pom.xml` to determine:
   - Spring Boot version, and therefore whether to use `jakarta.persistence.*` (Spring Boot 3+) or `javax.persistence.*` (Spring Boot 2.x).
   - Whether Lombok is a dependency. If yes, use `@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder` on every entity. If no, write explicit getters/setters/constructors instead — don't add the Lombok dependency yourself, just tell me if you think the entities would benefit from it.
3. Confirm the base package name for each service (e.g. `com.courier.userservice`) from its existing structure, and place entities in `<base-package>.model`.

## Non-negotiable mapping rules

- **Every column in `DATABASE.md` gets a mapped field. No column is skipped, no field is invented that isn't in the schema.**
- Always use explicit `@Column(name = "...")` for every field — never rely on Hibernate's implicit naming strategy to guess `snake_case` from a `camelCase` field name.
- Always use explicit `@Table(name = "...")` on every entity.
- **Primary keys**: all IDs are `UUID`. The database column default is `gen_random_uuid()`, so let the database generate it — map the field as `@Id @Column(name = "id", updatable = false, nullable = false)` with `@GeneratedValue` using whatever UUID generation strategy matches the Hibernate version you detected in step 2 (Hibernate 6 / Spring Boot 3 supports `@GeneratedValue(strategy = GenerationType.UUID)` directly; older Hibernate needs `@GeneratedValue(generator = "UUID") @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")`).
- **`created_at`**: map with `@CreationTimestamp`. Since the DB column also has a `DEFAULT now()`, this is redundant but harmless — do not mark it `insertable = false`.
- **`updated_at`**: every service maintains this via a database trigger (`set_updated_at()`), not the application. Map it as `@Column(name = "updated_at", insertable = false, updatable = false)` with no Hibernate timestamp annotation, so JPA never fights the trigger — the entity will simply reflect whatever value the DB last wrote after a refresh/reload.
- **Enums**: every `CHECK (... IN (...))` constraint becomes a proper Java `enum`, mapped with `@Enumerated(EnumType.STRING)` and `@Column(length = <same as VARCHAR length>)`. Do not use ordinal enums. The exact enums needed, taken directly from `DATABASE.md`'s constraints:
  - `users.role` → `CUSTOMER, COURIER`
  - `users.account_status` → `ACTIVE, INACTIVE, SUSPENDED`
  - `courier_profiles.vehicle_type` → `BIKE, SCOOTER, CAR, VAN`
  - `courier_profiles.availability_status` → `AVAILABLE, BUSY, OFFLINE`
  - `bookings.status` → `PLACED, ASSIGNED, PICKED_UP, IN_TRANSIT, DELIVERED, CANCELLED`
  - `booking_addresses.address_type` → `PICKUP, DROP`
  - `package_details.package_type` → `DOCUMENT, GENERAL, FRAGILE, ELECTRONICS, FOOD`
  - `status_updates.status` and `current_booking_status.status` → same 6 lifecycle values as `bookings.status`
  - `notifications.notification_type` → `EMAIL, SMS, PUSH`
  - `notifications.event_type` → `BOOKING_PLACED, COURIER_ASSIGNED, PICKED_UP, IN_TRANSIT, DELIVERED, CANCELLED`
  - `notifications.send_status` → `PENDING, SENT, FAILED`
  - `notification_delivery_attempts.status` → `SUCCESS, FAILED`

  Booking Service and Tracking Service both need the booking-lifecycle enum, but they're separate deployable services with separate codebases — **define it independently in each service's `model` package** (don't import across service boundaries). If the project already has a shared-kernel/common module for cross-service constants, use that instead — check for one before duplicating.

- **Real foreign keys (same database — use actual JPA relationships):**
  - `courier_profiles.user_id → users.id` (User Service) — `@OneToOne` with `@JoinColumn(name = "user_id", unique = true, nullable = false)`
  - `booking_addresses.booking_id → bookings.id` (Booking Service) — `@ManyToOne` with `@JoinColumn(name = "booking_id", nullable = false)`
  - `package_details.booking_id → bookings.id` (Booking Service) — `@OneToOne` with `@JoinColumn(name = "booking_id", unique = true, nullable = false)`
  - `current_booking_status.latest_status_update_id → status_updates.id` (Tracking Service) — `@ManyToOne` with `@JoinColumn(name = "latest_status_update_id", nullable = false)`
  - `notification_delivery_attempts.notification_id → notifications.id` (Notification Service) — `@ManyToOne` with `@JoinColumn(name = "notification_id", nullable = false)`

  On the "one" side of each of these (`Booking`, `Notification`, etc.), you may add the inverse `@OneToMany(mappedBy = ...)` collection **only if it's actually useful for the service's use cases** — don't add bidirectional mappings reflexively; a unidirectional `@ManyToOne`/`@OneToOne` from the owning side is enough unless I ask for the reverse navigation.

- **Cross-service references (logical only — never a JPA relationship):** any column that points at another *service's* database — `bookings.customer_id`, `bookings.courier_id`, `status_updates.booking_id`, `status_updates.updated_by_courier_id`, `current_booking_status.booking_id`, `notifications.recipient_id`, `notifications.booking_id` — must be a **plain `UUID` field** with a normal `@Column(name = "...")`, with **no** `@ManyToOne`, `@JoinColumn`, or entity reference of any kind, since the referenced entity lives in a different service's database entirely. Add a one-line code comment above each such field naming which service/table it logically refers to, e.g.:
  ```java
  // Logical reference to user-service-db.users.id — no FK, cross-service
  @Column(name = "customer_id", nullable = false)
  private UUID customerId;
  ```

- **Unique constraints**: single-column uniques via `@Column(unique = true)`; composite ones (e.g. `booking_addresses (booking_id, address_type)`, `notification_delivery_attempts (notification_id, attempt_number)`) via `@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"...", "..."}))`.
- **Check constraints on numeric/text ranges** (e.g. `fare_estimate >= 0`, `weight_kg > 0`, `active_booking_count >= 0`) aren't enforceable by JPA itself — mirror them with Bean Validation annotations (`@PositiveOrZero`, `@Positive`, `@DecimalMin`, etc.) as an application-layer safety net. Make clear in a comment that these do **not** replace the DB-level `CHECK` constraints, which remain the actual source of enforcement.
- **Indexes** already exist in the database — don't redeclare them via `@Table(indexes = ...)` unless you're also relying on `ddl-auto=validate` to confirm schema drift; if so, mirror them exactly as named in `DATABASE.md`.

## Deliverables — one file per entity, full code, no omissions or "add remaining fields" placeholders

**User Service** (`model` package): `User.java`, `CourierProfile.java`

**Booking Service** (`model` package): `Booking.java`, `BookingAddress.java` (+ `AddressType` enum), `PackageDetail.java` (+ `PackageType` enum)

**Tracking Service** (`model` package): `StatusUpdate.java` (+ `BookingStatus` enum), `CurrentBookingStatus.java`

**Notification Service** (`model` package): `Notification.java` (+ `NotificationType`, `EventType`, `SendStatus` enums), `NotificationDeliveryAttempt.java` (+ `AttemptStatus` enum)

Each file should have its complete package declaration, all imports, and the full class — nothing truncated.

## After generating

List, explicitly, any column or constraint from `DATABASE.md` you were unable to map cleanly and why — don't silently drop or approximate anything.