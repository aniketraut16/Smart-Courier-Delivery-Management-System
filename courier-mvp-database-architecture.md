# Smart Courier Delivery Management System — Database Architecture (Database-per-Service)

Target stack: **Spring Boot microservices + PostgreSQL**, one physical database per service, no shared schema, no cross-database foreign keys.

## Global Design Decisions

**ID strategy — UUID everywhere.**
All primary keys use `UUID` (PostgreSQL native `uuid` type), generated with `gen_random_uuid()` (from the built-in `pgcrypto` extension). Reasons:
- Cross-service references (`customer_id`, `courier_id`, `booking_id`, etc.) are stored as plain columns with no database-level constraint. UUIDs let each service generate IDs independently with no risk of collision, and let a client (or the Booking Service) generate a booking ID before it's even persisted, if needed for event correlation.
- UUIDs don't leak sequential business volume (e.g., "how many bookings exist") the way an auto-increment `BIGINT` would, which matters once this becomes a real product.
- The one trade-off — slightly larger index size and marginally worse insert locality than `BIGSERIAL` — is acceptable at MVP scale and is the standard choice for distributed microservice IDs.

If you'd rather optimize for raw insert throughput at large scale, `BIGINT GENERATED ALWAYS AS IDENTITY` is a reasonable alternative; the schema below works the same way with that substitution, you'd just carry `BIGINT` instead of `UUID` in the cross-service reference columns.

**Timestamps.** Every table gets `created_at`; every mutable table gets `updated_at`, maintained via a trigger (defined once per database) rather than relying on application code to set it.

**Enums.** Modeled as `VARCHAR` + `CHECK` constraint rather than native PostgreSQL `ENUM` types. This is deliberate: native enums require a schema migration (`ALTER TYPE ... ADD VALUE`) to add a new status later, which is more disruptive in a Flyway/Liquibase-driven Spring Boot pipeline than editing a `CHECK` constraint. `CHECK` gives the same integrity guarantee with easier evolution.

**Cross-service references.** Any column that conceptually points to an entity owned by another service (e.g., `bookings.customer_id` pointing to `user_service_db.users`) is stored as a plain `UUID` column — indexed, but with **no** `REFERENCES` clause and no `FOREIGN KEY` constraint. Referential integrity for these is enforced at the application layer (Spring Boot service calls / validation), which is the correct pattern for Database-per-Service.

---

# 1. User Service — `user_service_db`

### Purpose
Owns identity, authentication data, and role-specific profile info for both customers and couriers. This is the single source of truth for "who is this person" and "is this courier available."

### Tables
- `users` — core identity for both customers and couriers
- `courier_profiles` — courier-only extension data (1:1 with `users`)

## Table Design

### `users`
| Column | Type | Null | Default | Notes |
|---|---|---|---|---|
| id | UUID | NOT NULL | `gen_random_uuid()` | **PK** |
| full_name | VARCHAR(150) | NOT NULL | — | |
| email | VARCHAR(150) | NOT NULL | — | **UNIQUE** |
| phone_number | VARCHAR(20) | NOT NULL | — | **UNIQUE** |
| password_hash | VARCHAR(255) | NOT NULL | — | bcrypt/argon2 hash, never plaintext |
| role | VARCHAR(20) | NOT NULL | — | CHECK IN ('CUSTOMER','COURIER') |
| account_status | VARCHAR(20) | NOT NULL | `'ACTIVE'` | CHECK IN ('ACTIVE','INACTIVE','SUSPENDED') |
| created_at | TIMESTAMPTZ | NOT NULL | `now()` | |
| updated_at | TIMESTAMPTZ | NOT NULL | `now()` | trigger-maintained |

Indexes: `email` (unique, also indexed for login lookups), `phone_number` (unique), `role` (frequently filtered — e.g., "find couriers").

### `courier_profiles`
| Column | Type | Null | Default | Notes |
|---|---|---|---|---|
| id | UUID | NOT NULL | `gen_random_uuid()` | **PK** |
| user_id | UUID | NOT NULL | — | **FK → users(id)**, **UNIQUE** (enforces 1:1) |
| vehicle_type | VARCHAR(30) | NOT NULL | — | CHECK IN ('BIKE','SCOOTER','CAR','VAN') |
| vehicle_number | VARCHAR(20) | NULL | — | |
| availability_status | VARCHAR(20) | NOT NULL | `'OFFLINE'` | CHECK IN ('AVAILABLE','BUSY','OFFLINE') |
| active_booking_count | INTEGER | NOT NULL | `0` | CHECK (active_booking_count >= 0) |
| created_at | TIMESTAMPTZ | NOT NULL | `now()` | |
| updated_at | TIMESTAMPTZ | NOT NULL | `now()` | trigger-maintained |

Indexes: `user_id` (unique), `availability_status` (this is the column the Booking Service's "find an available courier" query will filter on hardest — composite index with `vehicle_type` optional for later).

## Relationships
- **One-to-One:** `users` ↔ `courier_profiles`, enforced by `UNIQUE` on `courier_profiles.user_id` plus an FK. Only rows where `users.role = 'COURIER'` are expected to have a matching profile (enforced in application logic, since cross-column conditional constraints tying `role` to profile existence aren't practical in plain SQL).
- **Real SQL FK:** `courier_profiles.user_id → users.id` (same database — legitimate FK).
- **Logical-only references (from other services):** `bookings.customer_id`, `bookings.courier_id`, `notifications.recipient_id`, `status_updates.updated_by_courier_id` all point here but carry no DB constraint.

## SQL Script

```sql
-- =====================================================
-- USER SERVICE DATABASE
-- =====================================================
CREATE DATABASE user_service_db;

\c user_service_db;

CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- Generic updated_at trigger function (one per database)
CREATE OR REPLACE FUNCTION set_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TABLE users (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    full_name       VARCHAR(150) NOT NULL,
    email           VARCHAR(150) NOT NULL,
    phone_number    VARCHAR(20)  NOT NULL,
    password_hash   VARCHAR(255) NOT NULL,
    role            VARCHAR(20)  NOT NULL,
    account_status  VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_users_email UNIQUE (email),
    CONSTRAINT uq_users_phone UNIQUE (phone_number),
    CONSTRAINT chk_users_role CHECK (role IN ('CUSTOMER','COURIER')),
    CONSTRAINT chk_users_account_status CHECK (account_status IN ('ACTIVE','INACTIVE','SUSPENDED'))
);

CREATE INDEX idx_users_role ON users(role);
CREATE INDEX idx_users_account_status ON users(account_status);

CREATE TRIGGER trg_users_updated_at
BEFORE UPDATE ON users
FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE courier_profiles (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id               UUID NOT NULL,
    vehicle_type          VARCHAR(30) NOT NULL,
    vehicle_number        VARCHAR(20),
    availability_status   VARCHAR(20) NOT NULL DEFAULT 'OFFLINE',
    active_booking_count  INTEGER NOT NULL DEFAULT 0,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_courier_profiles_user_id UNIQUE (user_id),
    CONSTRAINT fk_courier_profiles_user
        FOREIGN KEY (user_id) REFERENCES users(id)
        ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT chk_courier_vehicle_type CHECK (vehicle_type IN ('BIKE','SCOOTER','CAR','VAN')),
    CONSTRAINT chk_courier_availability CHECK (availability_status IN ('AVAILABLE','BUSY','OFFLINE')),
    CONSTRAINT chk_courier_active_count CHECK (active_booking_count >= 0)
);

CREATE INDEX idx_courier_profiles_availability ON courier_profiles(availability_status);
CREATE INDEX idx_courier_profiles_vehicle_type ON courier_profiles(vehicle_type);

CREATE TRIGGER trg_courier_profiles_updated_at
BEFORE UPDATE ON courier_profiles
FOR EACH ROW EXECUTE FUNCTION set_updated_at();
```

---

# 2. Booking Service — `booking_service_db`

### Purpose
Owns the full lifecycle of a delivery order: creation, fare estimate, courier assignment, and terminal state. This is the transactional core of the system.

### Tables
- `bookings`
- `booking_addresses` (pickup + drop, split out of `bookings` to keep it in 3NF and to allow an address to carry its own lat/long without repeating column groups)
- `package_details` (1:1 with `bookings`, split out because it's a distinct conceptual entity with its own optional attributes)

## Table Design

### `bookings`
| Column | Type | Null | Default | Notes |
|---|---|---|---|---|
| id | UUID | NOT NULL | `gen_random_uuid()` | **PK** |
| customer_id | UUID | NOT NULL | — | logical ref → User Service `users.id` |
| courier_id | UUID | NULL | — | logical ref → User Service `users.id`; null until assigned |
| fare_estimate | NUMERIC(10,2) | NOT NULL | — | CHECK (fare_estimate >= 0) |
| distance_km | NUMERIC(8,2) | NULL | — | CHECK (distance_km >= 0) |
| status | VARCHAR(20) | NOT NULL | `'PLACED'` | CHECK IN lifecycle values |
| assigned_at | TIMESTAMPTZ | NULL | — | |
| delivered_at | TIMESTAMPTZ | NULL | — | |
| cancelled_at | TIMESTAMPTZ | NULL | — | |
| created_at | TIMESTAMPTZ | NOT NULL | `now()` | |
| updated_at | TIMESTAMPTZ | NOT NULL | `now()` | trigger-maintained |

Indexes: `customer_id` (customer's booking history), `courier_id` (courier's active jobs), `status` (dashboards/queries by state).

### `booking_addresses`
| Column | Type | Null | Default | Notes |
|---|---|---|---|---|
| id | UUID | NOT NULL | `gen_random_uuid()` | **PK** |
| booking_id | UUID | NOT NULL | — | **FK → bookings(id)** |
| address_type | VARCHAR(10) | NOT NULL | — | CHECK IN ('PICKUP','DROP') |
| address_line | VARCHAR(255) | NOT NULL | — | |
| city | VARCHAR(100) | NOT NULL | — | |
| state | VARCHAR(100) | NULL | — | |
| postal_code | VARCHAR(20) | NULL | — | |
| latitude | NUMERIC(9,6) | NULL | — | |
| longitude | NUMERIC(9,6) | NULL | — | |
| created_at | TIMESTAMPTZ | NOT NULL | `now()` | |

Constraints: `UNIQUE (booking_id, address_type)` — guarantees exactly one PICKUP and one DROP row per booking, which is exactly the 1-booking-has-2-addresses rule from the plan, enforced declaratively instead of in app code.

Indexes: `booking_id`.

### `package_details`
| Column | Type | Null | Default | Notes |
|---|---|---|---|---|
| id | UUID | NOT NULL | `gen_random_uuid()` | **PK** |
| booking_id | UUID | NOT NULL | — | **FK → bookings(id)**, **UNIQUE** (1:1) |
| package_type | VARCHAR(30) | NOT NULL | `'GENERAL'` | CHECK IN ('DOCUMENT','GENERAL','FRAGILE','ELECTRONICS','FOOD') |
| weight_kg | NUMERIC(6,2) | NULL | — | CHECK (weight_kg > 0) |
| description | VARCHAR(255) | NULL | — | |
| is_fragile | BOOLEAN | NOT NULL | `false` | |
| created_at | TIMESTAMPTZ | NOT NULL | `now()` | |

Indexes: `booking_id` (unique).

## Relationships
- **One-to-Many (real FK):** `bookings` → `booking_addresses` (a booking has up to two address rows, constrained to exactly one per type via the unique pair).
- **One-to-One (real FK):** `bookings` ↔ `package_details`.
- **Logical-only references:** `customer_id`, `courier_id` → User Service; and (in the other direction) Tracking Service's `status_updates.booking_id` and Notification Service's `notifications.booking_id` point back at `bookings.id` with no constraint.

## SQL Script

```sql
-- =====================================================
-- BOOKING SERVICE DATABASE
-- =====================================================
CREATE DATABASE booking_service_db;

\c booking_service_db;

CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE OR REPLACE FUNCTION set_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TABLE bookings (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    customer_id     UUID NOT NULL,          -- logical ref: user_service_db.users.id
    courier_id      UUID,                   -- logical ref: user_service_db.users.id
    fare_estimate   NUMERIC(10,2) NOT NULL,
    distance_km     NUMERIC(8,2),
    status          VARCHAR(20) NOT NULL DEFAULT 'PLACED',
    assigned_at     TIMESTAMPTZ,
    delivered_at    TIMESTAMPTZ,
    cancelled_at    TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_bookings_fare CHECK (fare_estimate >= 0),
    CONSTRAINT chk_bookings_distance CHECK (distance_km IS NULL OR distance_km >= 0),
    CONSTRAINT chk_bookings_status CHECK (
        status IN ('PLACED','ASSIGNED','PICKED_UP','IN_TRANSIT','DELIVERED','CANCELLED')
    )
);

CREATE INDEX idx_bookings_customer_id ON bookings(customer_id);
CREATE INDEX idx_bookings_courier_id ON bookings(courier_id);
CREATE INDEX idx_bookings_status ON bookings(status);

CREATE TRIGGER trg_bookings_updated_at
BEFORE UPDATE ON bookings
FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE booking_addresses (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    booking_id    UUID NOT NULL,
    address_type  VARCHAR(10) NOT NULL,
    address_line  VARCHAR(255) NOT NULL,
    city          VARCHAR(100) NOT NULL,
    state         VARCHAR(100),
    postal_code   VARCHAR(20),
    latitude      NUMERIC(9,6),
    longitude     NUMERIC(9,6),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_booking_addresses_booking
        FOREIGN KEY (booking_id) REFERENCES bookings(id)
        ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT uq_booking_address_type UNIQUE (booking_id, address_type),
    CONSTRAINT chk_booking_address_type CHECK (address_type IN ('PICKUP','DROP'))
);

CREATE INDEX idx_booking_addresses_booking_id ON booking_addresses(booking_id);

CREATE TABLE package_details (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    booking_id    UUID NOT NULL,
    package_type  VARCHAR(30) NOT NULL DEFAULT 'GENERAL',
    weight_kg     NUMERIC(6,2),
    description   VARCHAR(255),
    is_fragile    BOOLEAN NOT NULL DEFAULT false,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_package_details_booking_id UNIQUE (booking_id),
    CONSTRAINT fk_package_details_booking
        FOREIGN KEY (booking_id) REFERENCES bookings(id)
        ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT chk_package_type CHECK (
        package_type IN ('DOCUMENT','GENERAL','FRAGILE','ELECTRONICS','FOOD')
    ),
    CONSTRAINT chk_package_weight CHECK (weight_kg IS NULL OR weight_kg > 0)
);

CREATE INDEX idx_package_details_booking_id ON package_details(booking_id);
```

---

# 3. Tracking Service — `tracking_service_db`

### Purpose
Append-only log of every status change a booking goes through, plus a fast-lookup table for "what's the current status right now" so the read path doesn't have to scan history on every customer-facing status check.

### Tables
- `status_updates` — full history (append-only)
- `current_booking_status` — denormalized latest-state pointer, one row per booking

## Table Design

### `status_updates`
| Column | Type | Null | Default | Notes |
|---|---|---|---|---|
| id | UUID | NOT NULL | `gen_random_uuid()` | **PK** |
| booking_id | UUID | NOT NULL | — | logical ref → Booking Service `bookings.id` |
| status | VARCHAR(20) | NOT NULL | — | CHECK IN lifecycle values |
| note | VARCHAR(255) | NULL | — | free-text courier note |
| updated_by_courier_id | UUID | NULL | — | logical ref → User Service `users.id` |
| created_at | TIMESTAMPTZ | NOT NULL | `now()` | this row is immutable — no `updated_at` |

Indexes: `booking_id`; composite `(booking_id, created_at)` for retrieving ordered history efficiently.

### `current_booking_status`
| Column | Type | Null | Default | Notes |
|---|---|---|---|---|
| booking_id | UUID | NOT NULL | — | **PK** (logical ref → Booking Service, but the PK here) |
| status | VARCHAR(20) | NOT NULL | — | CHECK IN lifecycle values |
| latest_status_update_id | UUID | NOT NULL | — | **FK → status_updates(id)** (same DB — real FK) |
| updated_at | TIMESTAMPTZ | NOT NULL | `now()` | trigger-maintained |

Indexes: `status` (e.g., ops queries like "all bookings currently IN_TRANSIT").

## Relationships
- **One-to-Many (logical):** one booking → many `status_updates` rows (booking lives in another database, so this is enforced only by application logic, not a DB constraint).
- **One-to-One (real FK):** `current_booking_status.latest_status_update_id → status_updates.id` — this is a genuine same-database FK since both tables live in `tracking_service_db`. It's how the "denormalized latest state" pattern stays consistent: every write to `current_booking_status` must point at a row that actually exists in the history table.

## SQL Script

```sql
-- =====================================================
-- TRACKING SERVICE DATABASE
-- =====================================================
CREATE DATABASE tracking_service_db;

\c tracking_service_db;

CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE OR REPLACE FUNCTION set_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TABLE status_updates (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    booking_id              UUID NOT NULL,   -- logical ref: booking_service_db.bookings.id
    status                  VARCHAR(20) NOT NULL,
    note                    VARCHAR(255),
    updated_by_courier_id   UUID,            -- logical ref: user_service_db.users.id
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_status_updates_status CHECK (
        status IN ('PLACED','ASSIGNED','PICKED_UP','IN_TRANSIT','DELIVERED','CANCELLED')
    )
);

CREATE INDEX idx_status_updates_booking_id ON status_updates(booking_id);
CREATE INDEX idx_status_updates_booking_created ON status_updates(booking_id, created_at);

CREATE TABLE current_booking_status (
    booking_id                UUID PRIMARY KEY,   -- logical ref: booking_service_db.bookings.id
    status                    VARCHAR(20) NOT NULL,
    latest_status_update_id   UUID NOT NULL,
    updated_at                TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_current_status_update
        FOREIGN KEY (latest_status_update_id) REFERENCES status_updates(id)
        ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT chk_current_booking_status CHECK (
        status IN ('PLACED','ASSIGNED','PICKED_UP','IN_TRANSIT','DELIVERED','CANCELLED')
    )
);

CREATE INDEX idx_current_booking_status_status ON current_booking_status(status);

CREATE TRIGGER trg_current_booking_status_updated_at
BEFORE UPDATE ON current_booking_status
FOR EACH ROW EXECUTE FUNCTION set_updated_at();
```

---

# 4. Notification Service — `notification_service_db`

### Purpose
Owns outbound customer communications and their delivery/retry state. Kept separate from Tracking Service because "did we successfully notify the customer" is an operational concern of its own (retries, provider failures) distinct from "what is the delivery's status."

### Tables
- `notifications` — one row per notification event
- `notification_delivery_attempts` — retry log, 1:many with `notifications`

## Table Design

### `notifications`
| Column | Type | Null | Default | Notes |
|---|---|---|---|---|
| id | UUID | NOT NULL | `gen_random_uuid()` | **PK** |
| recipient_id | UUID | NOT NULL | — | logical ref → User Service `users.id` |
| booking_id | UUID | NOT NULL | — | logical ref → Booking Service `bookings.id` |
| notification_type | VARCHAR(20) | NOT NULL | — | CHECK IN ('EMAIL','SMS','PUSH') |
| event_type | VARCHAR(30) | NOT NULL | — | CHECK IN lifecycle-driven event names |
| message_content | TEXT | NOT NULL | — | |
| send_status | VARCHAR(20) | NOT NULL | `'PENDING'` | CHECK IN ('PENDING','SENT','FAILED') |
| sent_at | TIMESTAMPTZ | NULL | — | |
| created_at | TIMESTAMPTZ | NOT NULL | `now()` | |
| updated_at | TIMESTAMPTZ | NOT NULL | `now()` | trigger-maintained |

Indexes: `recipient_id`, `booking_id`, `send_status` (for a retry worker to poll `PENDING`/`FAILED` rows).

### `notification_delivery_attempts`
| Column | Type | Null | Default | Notes |
|---|---|---|---|---|
| id | UUID | NOT NULL | `gen_random_uuid()` | **PK** |
| notification_id | UUID | NOT NULL | — | **FK → notifications(id)** |
| attempt_number | INTEGER | NOT NULL | `1` | CHECK (attempt_number > 0) |
| provider | VARCHAR(50) | NULL | — | e.g., 'SendGrid', 'Twilio' |
| status | VARCHAR(20) | NOT NULL | — | CHECK IN ('SUCCESS','FAILED') |
| error_message | VARCHAR(255) | NULL | — | |
| attempted_at | TIMESTAMPTZ | NOT NULL | `now()` | |

Constraints: `UNIQUE (notification_id, attempt_number)` — no duplicate attempt numbers per notification.
Indexes: `notification_id`.

## Relationships
- **One-to-Many (real FK):** `notifications` → `notification_delivery_attempts`.
- **Logical-only references:** `recipient_id` → User Service, `booking_id` → Booking Service.

## SQL Script

```sql
-- =====================================================
-- NOTIFICATION SERVICE DATABASE
-- =====================================================
CREATE DATABASE notification_service_db;

\c notification_service_db;

CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE OR REPLACE FUNCTION set_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TABLE notifications (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    recipient_id        UUID NOT NULL,   -- logical ref: user_service_db.users.id
    booking_id          UUID NOT NULL,   -- logical ref: booking_service_db.bookings.id
    notification_type   VARCHAR(20) NOT NULL,
    event_type          VARCHAR(30) NOT NULL,
    message_content     TEXT NOT NULL,
    send_status         VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    sent_at             TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_notification_type CHECK (notification_type IN ('EMAIL','SMS','PUSH')),
    CONSTRAINT chk_notification_event CHECK (
        event_type IN ('BOOKING_PLACED','COURIER_ASSIGNED','PICKED_UP','IN_TRANSIT','DELIVERED','CANCELLED')
    ),
    CONSTRAINT chk_notification_send_status CHECK (send_status IN ('PENDING','SENT','FAILED'))
);

CREATE INDEX idx_notifications_recipient_id ON notifications(recipient_id);
CREATE INDEX idx_notifications_booking_id ON notifications(booking_id);
CREATE INDEX idx_notifications_send_status ON notifications(send_status);

CREATE TRIGGER trg_notifications_updated_at
BEFORE UPDATE ON notifications
FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE notification_delivery_attempts (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    notification_id   UUID NOT NULL,
    attempt_number    INTEGER NOT NULL DEFAULT 1,
    provider          VARCHAR(50),
    status            VARCHAR(20) NOT NULL,
    error_message     VARCHAR(255),
    attempted_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_delivery_attempts_notification
        FOREIGN KEY (notification_id) REFERENCES notifications(id)
        ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT uq_delivery_attempt_number UNIQUE (notification_id, attempt_number),
    CONSTRAINT chk_delivery_attempt_number CHECK (attempt_number > 0),
    CONSTRAINT chk_delivery_attempt_status CHECK (status IN ('SUCCESS','FAILED'))
);

CREATE INDEX idx_delivery_attempts_notification_id ON notification_delivery_attempts(notification_id);
```

---

# 5. Design Justification Summary

| Table | Service | Why here, not elsewhere |
|---|---|---|
| `users` | User Service | Identity/auth is a cross-cutting concern every other service needs but none should own — classic single-responsibility split. |
| `courier_profiles` | User Service | Courier-specific attributes (vehicle, availability) are about *who the courier is*, not about any one delivery — belongs with identity, not with Booking. |
| `bookings` | Booking Service | The order/lifecycle aggregate root; Booking Service is explicitly the owner of "the lifecycle of a delivery order" per the plan. |
| `booking_addresses` | Booking Service | Addresses only make sense in the context of a specific booking (they're not reusable customer address-book entries in this MVP) — normalized out of `bookings` to avoid repeating a column group twice (pickup_*, drop_*) and to keep the table in 3NF. |
| `package_details` | Booking Service | Package attributes are booking-specific and optional; splitting keeps `bookings` lean and lets package data evolve independently. |
| `status_updates` | Tracking Service | The plan assigns "records and exposes status updates" to Tracking Service specifically, separate from Booking's ownership of the lifecycle *decision*. |
| `current_booking_status` | Tracking Service | Denormalized for the customer-facing "what's my status right now" read path — avoids scanning history on every poll; still validated against real history via FK. |
| `notifications` | Notification Service | Isolates "did we tell the customer" concerns (retries, provider errors) from delivery-state concerns owned by Tracking. |
| `notification_delivery_attempts` | Notification Service | Retry/audit trail is inherently a Notification Service concern (provider-level failures), not something Booking or Tracking should know about. |

---

# 6. Entity Ownership Map

| Entity | Owning Service | Services that only reference it | Reference stored as |
|---|---|---|---|
| User / Courier identity | **User Service** | Booking Service, Tracking Service, Notification Service | `customer_id`, `courier_id`, `recipient_id`, `updated_by_courier_id` (UUID, no FK) |
| Booking (order + addresses + package) | **Booking Service** | Tracking Service, Notification Service | `booking_id` (UUID, no FK) |
| Status history / current status | **Tracking Service** | (read via API by customer-facing clients; not referenced at the DB level by other services) | n/a |
| Notification records | **Notification Service** | none reference this back | n/a |

Rule of thumb applied throughout: **a UUID crossing a service boundary is data, never a constraint.** Only the owning service's database is allowed to declare a `FOREIGN KEY` on that entity's primary key.

---

# 7. Future Scalability

The schema is deliberately shaped so these can be added without reworking what exists:

- **Payment Service.** New `payment_service_db` with a `payments` table keyed by its own `id`, holding a logical `booking_id` reference (same pattern as everything else) plus `amount`, `payment_method`, `payment_status`. `bookings.fare_estimate` already exists as the number to reconcile against — no change needed to Booking Service.
- **Warehouse/Hub Management.** Add a `warehouses` table (new service or extend User Service's org model) and a logical `hub_id` column on `bookings` or on a new `booking_route_legs` table, without touching existing columns.
- **Route Optimization.** Introduce a `route_legs` table in a new or existing service, referencing `booking_id` logically; `booking_addresses` already stores `latitude`/`longitude`, so the geographic data a route optimizer needs is already present.
- **Real-time GPS Tracking.** Add a high-write `courier_location_pings` table (likely Tracking Service, possibly its own service given the write volume) with `courier_id`, `latitude`, `longitude`, `recorded_at` — additive, doesn't touch `status_updates`.
- **Ratings & Reviews.** New `ratings` table (new service or extend Booking Service) with logical `booking_id`, `rated_user_id`, `rating`, `comment` — one-to-one with a completed booking, purely additive.
- **Analytics.** Because every table already carries `created_at`/`updated_at` and status/event enums are consistent across services, an analytics service can consume this via CDC (e.g., Debezium on each Postgres instance) or scheduled ETL without any schema changes being required specifically *for* analytics.

---

# 8. Relationship Summary (all services)

**Real SQL foreign keys (same database only):**
- `courier_profiles.user_id → users.id` (User Service)
- `booking_addresses.booking_id → bookings.id` (Booking Service)
- `package_details.booking_id → bookings.id` (Booking Service)
- `current_booking_status.latest_status_update_id → status_updates.id` (Tracking Service)
- `notification_delivery_attempts.notification_id → notifications.id` (Notification Service)

**Logical-only references (no FK, cross-database):**
- `bookings.customer_id`, `bookings.courier_id` → `users.id`
- `status_updates.booking_id`, `current_booking_status.booking_id` → `bookings.id`
- `status_updates.updated_by_courier_id` → `users.id`
- `notifications.recipient_id` → `users.id`
- `notifications.booking_id` → `bookings.id`

**One-to-One:** `users` ↔ `courier_profiles`; `bookings` ↔ `package_details`.
**One-to-Many:** `bookings` → `booking_addresses`; `bookings` → `status_updates` (logical); `bookings` → `notifications` (logical); `notifications` → `notification_delivery_attempts`.
**Many-to-Many:** none required at MVP scope — nothing in the current entity set needs a join table.
