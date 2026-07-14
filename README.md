# Smart Courier Delivery Management System
### High-Level Project Planning Document (Microservices Architecture)

**Training Program:** Java Spring Boot Refresher – Capgemini
**Stack:** Java 21, Spring Boot 3, Spring Data JPA (Hibernate), PostgreSQL, Spring Security (JWT), Bean Validation, Lombok, SLF4J + Logback, Global Exception Handling, Swagger/OpenAPI, JUnit 5 + Mockito, Maven, **Spring AI** (structured output extraction)
**Architecture Style:** Microservices (3 services, database-per-service, synchronous REST communication)

---

## 1. Project Overview

### 1.1 Problem Statement
Courier and parcel delivery businesses need a way to manage customer bookings, assign deliveries to available couriers, and track the status of each shipment from pickup to delivery. This project builds a simplified, microservice-based backend that decomposes the system into independently deployable services, demonstrating service decomposition, inter-service communication, and distributed data ownership — while staying scoped for a single developer to complete in a few weeks.

### 1.2 Objectives
- Decompose the system into a small, manageable set of microservices (max 3) with clear bounded contexts.
- Apply database-per-service data ownership using PostgreSQL.
- Implement JWT-based authentication centrally and propagate identity across services.
- Practice inter-service communication using REST (WebClient/OpenFeign).
- Apply clean coding practices within each service: DTOs, validation, global exception handling, logging.
- Document each service's API using Swagger/OpenAPI and test service logic using JUnit/Mockito.
- Demonstrate applied AI integration using Spring AI: converting a free-text booking request into a structured, validated booking DTO.

### 1.3 Scope
**In scope:**
- 3 independently runnable Spring Boot microservices, each with its own PostgreSQL database/schema.
- Customer booking of courier requests.
- Admin assignment of bookings to couriers.
- Courier status updates and customer tracking.
- Synchronous REST-based inter-service calls (no message broker, to keep scope manageable).
- Centralized JWT issuance with stateless validation in each service.
- AI-assisted booking creation (Spring AI) in the Booking Service: customer submits a free-text description of what they want to send, and the system extracts structured booking fields from it.

**Out of scope (kept simple intentionally):**
- Service discovery (Eureka), centralized config server, or full Spring Cloud ecosystem.
- Message brokers (Kafka/RabbitMQ) — synchronous REST is sufficient at this scale.
- API Gateway is optional/stretch goal only, not a core deliverable.
- Real-time GPS tracking, payment integration, SMS/email notifications.
- Containers/Kubernetes orchestration (Docker Compose is enough if needed for local run).

> **Note on scale:** A true production microservice system would include service discovery, a config server, an API gateway, and async messaging. These are intentionally omitted here to keep the assignment realistic for a single developer within a few weeks. The focus is on service decomposition, data ownership, and inter-service REST calls — the core microservice concepts.

---

## 2. User Roles

| Role | Responsibilities |
|------|-------------------|
| **Admin** | Manages courier accounts, views all bookings, assigns bookings to couriers, monitors overall system activity. |
| **Customer** | Registers/logs in, creates courier booking requests, tracks status of their own deliveries, views booking history. |
| **Courier (Delivery Agent)** | Views assigned deliveries, updates delivery status (picked up, in transit, delivered). |

---

## 3. Core Features

- User registration and login (JWT-based authentication), issued centrally by the User & Auth Service
- Role-based access control (Admin / Customer / Courier), enforced independently in each service
- Create, view, and cancel courier booking requests
- Admin assignment of bookings to available couriers
- Courier delivery status updates
- Customer delivery tracking (status history)
- Address management for pickup/drop locations
- Courier availability management
- Search/filter bookings (by status, date, customer)
- **AI-powered smart booking creation** — customer types a free-text request (e.g., *"Send a 2kg fragile package from Pune to Mumbai tomorrow morning"*) and Spring AI extracts it into a structured booking (weight, addresses, fragile flag, preferred time) for the customer to review and confirm
- Global exception handling with meaningful error responses in each service
- Independent Swagger/OpenAPI documentation per service

---

## 4. Microservice Architecture Overview

The system is split into **3 services**, each owning its own data and exposing its own REST API. Services communicate synchronously over REST using `WebClient` or `OpenFeign`.

### 4.1 Service Breakdown

| Service | Responsibility |
|---------|-----------------|
| **1. User & Auth Service** | Owns user identity: registration, login, JWT issuance, user/customer/courier profiles, addresses, and courier availability status. Acts as the single source of truth for "who is this user and what role do they have." |
| **2. Booking Service** | Owns courier booking requests: creation, cancellation, listing, and booking status. Calls the User & Auth Service to validate customers and fetch/validate addresses. Also hosts the **Spring AI-powered Smart Booking extraction** feature (Section 4.5). |
| **3. Delivery & Tracking Service** | Owns delivery assignment and tracking: assigning bookings to couriers, courier status updates, and status history. Calls the Booking Service (to fetch/update booking status) and the User & Auth Service (to fetch available couriers). |

### 4.2 Inter-Service Communication
- All calls are **synchronous REST** (JSON over HTTP), using Spring's `WebClient` (or `OpenFeign` as an alternative) — no message broker is used, to keep the project scoped appropriately.
- Each service validates the JWT independently (shared signing secret/public key), so no service needs to call another just to authenticate a request.
- Example service-to-service calls:
  - Booking Service → User & Auth Service: `GET /internal/customers/{id}` (validate customer, fetch default address)
  - Delivery & Tracking Service → Booking Service: `GET /internal/bookings/{id}`, `PUT /internal/bookings/{id}/status`
  - Delivery & Tracking Service → User & Auth Service: `GET /internal/couriers/available`

### 4.3 Data Ownership
- Each service has its **own PostgreSQL database (or schema)** — no service directly queries another's database.
- Cross-service data needs are met via REST calls, not shared tables or joins.

### 4.4 Optional/Stretch Component
- **API Gateway** (Spring Cloud Gateway): a single entry point routing client requests to the correct service. Not required for core assignment completion but can be added as a stretch goal if time permits.

### 4.5 AI Integration — Smart Booking Creation (Spring AI)

**Where:** Booking Service only. No new microservice is introduced — AI logic lives in a dedicated `ai` package/module within the Booking Service so it stays isolated and optional.

**What it does:** Instead of (or in addition to) filling a structured booking form, the customer submits a single free-text sentence describing what they want shipped. Spring AI's `ChatClient`, combined with a **structured output converter** (`BeanOutputConverter`/`.entity(BookingExtraction.class)`), converts that text into a structured DTO matching the existing `BookingRequest` shape — package weight, pickup/drop address hints, fragile/urgent flags, preferred pickup time.

**How it fits the existing flow:**
1. Customer sends free text to a new endpoint (`POST /api/bookings/ai-draft`).
2. Spring AI parses it into a structured `BookingRequest` DTO.
3. The draft is returned to the customer **for review/confirmation** — it is never auto-submitted as a real booking.
4. On confirmation, the customer calls the existing `POST /api/bookings` endpoint as normal, so all existing validation and business rules still apply unchanged.

**Why this design:**
- Keeps AI strictly at the "input parsing" edge of the system — it never bypasses validation, never talks to other services, and never writes to the database directly. This keeps the blast radius of an AI mistake (e.g., misreading "2kg" as "20kg") limited to a draft the human must confirm.
- Demonstrates Spring AI's structured/entity extraction capability cleanly, without needing a vector store, RAG pipeline, or external tool calling — appropriately scoped for a training project.
- Reuses the existing `BookingRequest` DTO and validation (Bean Validation), so no duplicate business logic is needed.

**Prompting notes:**
- Use a prompt template with clear field instructions and a few example inputs/outputs (few-shot) to improve extraction consistency.
- Missing/ambiguous fields (e.g., no explicit weight) should return `null` rather than a guessed value, and the UI should prompt the customer to fill the gap manually.

---

## 5. Database Planning (Database-per-Service)

### 5.1 User & Auth Service — Database
| Entity | Description |
|--------|--------------|
| **User** | Stores login credentials and role (ADMIN, CUSTOMER, COURIER). Base identity for authentication. |
| **CustomerProfile** | Stores customer-specific details (name, phone) linked to a User. |
| **CourierProfile** | Stores courier-specific details (name, phone, vehicle type, availability status) linked to a User. |
| **Address** | Stores reusable pickup/drop address details (street, city, pincode, landmark) linked to a Customer. |

**Relationships:** `User` (1)—(1) `CustomerProfile`/`CourierProfile`; `CustomerProfile` (1)—(many) `Address`.

### 5.2 Booking Service — Database
| Entity | Description |
|--------|--------------|
| **Booking** | Represents a customer's courier request — customer ID (reference), pickup/drop address snapshot, package details, status, timestamps. |

**Relationships:** `Booking` references `customerId` and `addressId` logically (as IDs, not foreign keys) — actual customer/address data lives in the User & Auth Service and is fetched via REST when needed.

### 5.3 Delivery & Tracking Service — Database
| Entity | Description |
|--------|--------------|
| **DeliveryAssignment** | Links a Booking (by `bookingId` reference) to an assigned courier (`courierId` reference); stores assignment date and current delivery status. |
| **DeliveryStatusHistory** | Stores a log of status changes for an assignment (e.g., CREATED → PICKED_UP → IN_TRANSIT → DELIVERED) with timestamps. |

**Relationships:** `DeliveryAssignment` (1)—(many) `DeliveryStatusHistory`; `bookingId` and `courierId` are logical references resolved via REST calls to the Booking and User & Auth services respectively.

> **Note:** Since each service owns its own database, cross-service "relationships" are maintained as ID references (not DB foreign keys) and resolved at runtime through REST calls — this is a standard microservices data-ownership pattern.

---

## 6. Main Application Flows

| Flow | Description | Services Involved |
|------|--------------|---------------------|
| **User Registration/Login** | User registers as Customer or Courier; logs in and receives a JWT. | User & Auth Service |
| **Book Courier** | Customer submits a booking; Booking Service validates the customer by calling User & Auth Service, then creates booking with status `PENDING`. | Booking Service → User & Auth Service |
| **Assign Delivery** | Admin views pending bookings (from Booking Service) and available couriers (from User & Auth Service), then Delivery & Tracking Service creates an assignment and updates booking status to `ASSIGNED`. | Delivery & Tracking Service → Booking Service, User & Auth Service |
| **Update Delivery Status** | Courier updates delivery status via Delivery & Tracking Service (`PICKED_UP` → `IN_TRANSIT` → `DELIVERED`/`FAILED`); status is also synced back to the Booking Service. | Delivery & Tracking Service → Booking Service |
| **Track Courier** | Customer queries Delivery & Tracking Service (via booking ID) to view current status and history timeline. | Delivery & Tracking Service → Booking Service |
| **Cancel Booking** | Customer cancels a booking before assignment; Booking Service updates status to `CANCELLED`. | Booking Service |
| **AI-Assisted Booking Draft** | Customer submits a free-text description of their shipment; Spring AI extracts a structured draft booking for the customer to review before confirming via the normal Book Courier flow. | Booking Service (Spring AI) |
| **Manage Courier Availability** | Courier/Admin toggles courier's availability in the User & Auth Service so it appears (or not) in the assignment pool. | User & Auth Service |

---

## 7. REST API Planning (Grouped by Service)

### 7.1 User & Auth Service

**Auth**
1. `POST /api/auth/register` – Register a new user (Customer/Courier)
2. `POST /api/auth/login` – Authenticate and issue JWT

**User/Profile**
3. `GET /api/users/me` – Get current logged-in user profile
4. `PUT /api/users/me` – Update current user profile
5. `GET /api/admin/users` – Admin: list all users
6. `PUT /api/admin/users/{id}/status` – Admin: activate/deactivate a user

**Address**
7. `POST /api/addresses` – Add a new address
8. `GET /api/addresses` – List current user's addresses
9. `PUT /api/addresses/{id}` – Update an address
10. `DELETE /api/addresses/{id}` – Delete an address

**Courier Profile & Availability**
11. `GET /api/admin/couriers` – List all couriers
12. `GET /api/couriers/available` – List currently available couriers
13. `PUT /api/couriers/me/availability` – Courier: toggle own availability
14. `PUT /api/admin/couriers/{id}/status` – Admin: activate/deactivate a courier

**Internal APIs (service-to-service only)**
15. `GET /internal/customers/{id}` – Fetch customer details (used by Booking Service)
16. `GET /internal/couriers/{id}` – Fetch courier details (used by Delivery & Tracking Service)
17. `GET /internal/addresses/{id}` – Fetch address details (used by Booking Service)

### 7.2 Booking Service

18. `POST /api/bookings` – Create a new courier booking
19. `GET /api/bookings` – List current customer's bookings
20. `GET /api/bookings/{id}` – Get booking details
21. `PUT /api/bookings/{id}/cancel` – Cancel a pending booking
22. `POST /api/bookings/ai-draft` – **(Spring AI)** Extract a structured booking draft from a free-text shipment description, for customer review before confirmation
23. `GET /api/admin/bookings` – Admin: list all bookings (filter by status/date)
24. `GET /api/admin/bookings/{id}` – Admin: get any booking's details

**Internal APIs (service-to-service only)**
25. `GET /internal/bookings/{id}` – Fetch booking details (used by Delivery & Tracking Service)
26. `PUT /internal/bookings/{id}/status` – Update booking status (called by Delivery & Tracking Service)

### 7.3 Delivery & Tracking Service

**Assignment (Admin)**
27. `POST /api/admin/assignments` – Assign a booking to a courier
28. `GET /api/admin/assignments` – List all assignments
29. `GET /api/admin/assignments/{id}` – Get assignment details
30. `PUT /api/admin/assignments/{id}/reassign` – Reassign booking to a different courier

**Delivery (Courier)**
31. `GET /api/couriers/me/assignments` – Courier: list assigned deliveries
32. `PUT /api/couriers/me/assignments/{id}/status` – Courier: update delivery status

**Tracking (Customer)**
33. `GET /api/bookings/{bookingId}/tracking` – Get status history for a booking

*(~33 APIs total across 3 services, including the Spring AI booking-draft endpoint, plus internal-only APIs used for service-to-service calls.)*

---

## 8. Security

| Role | Permissions |
|------|-------------|
| **ADMIN** | Full access: manage users, view/manage all bookings, assign/reassign deliveries, manage couriers. |
| **CUSTOMER** | Manage own profile and addresses; create/view/cancel own bookings; track own deliveries. |
| **COURIER** | View/update only assignments allocated to them; update own availability. |

**Security implementation notes:**
- JWT is issued **only** by the User & Auth Service; all three services validate the JWT independently using a shared signing secret (or public key, if using asymmetric signing) — no service calls another purely to authenticate.
- Passwords stored using BCrypt hashing (User & Auth Service only).
- Method-level authorization using `@PreAuthorize` based on role, applied in each service independently.
- **Internal APIs** (`/internal/**`) are not exposed to end clients — they are restricted to service-to-service calls only (e.g., via a shared internal API key, or by network-level restriction in a real deployment; for this training project, a simple internal-header check is sufficient).
- Global exception handler in each service returns structured `401/403` responses for auth failures.
- The `POST /api/bookings/ai-draft` endpoint requires the same authenticated CUSTOMER role as normal booking creation. The AI only ever returns a **draft** — Bean Validation and existing business rules still apply in full when the customer confirms and calls `POST /api/bookings`, so the AI step cannot bypass authorization or validation.

---

## 9. Testing

Unit tests (JUnit 5 + Mockito) should be written for the **service layer** in each microservice:

**User & Auth Service**
- **AuthService** – registration validation, login credential checks, JWT generation logic
- **UserService** – profile update logic, role-based access rules
- **CourierProfileService** – availability toggle logic, activation/deactivation rules

**Booking Service**
- **BookingService** – booking creation validation, cancellation rules (e.g., cannot cancel after assignment), REST client interaction with User & Auth Service (mocked)
- **AiBookingExtractionService** – prompt construction, correct mapping of the AI's structured response onto `BookingRequest` fields, and handling of missing/ambiguous fields (mock the Spring AI `ChatClient` response rather than calling the real LLM in unit tests)

**Delivery & Tracking Service**
- **AssignmentService** – assignment logic, courier availability checks (mocked REST call), reassignment rules
- **DeliveryTrackingService** – status transition validation (e.g., cannot go from `DELIVERED` back to `PICKED_UP`), status history creation

**Testing approach:**
- Mock repository dependencies using `@Mock` / `@InjectMocks`.
- Mock inter-service REST calls (`WebClient`/`Feign` clients) so each service's unit tests remain isolated and fast.
- Test both success paths and exception/edge cases (invalid status transitions, entity not found, downstream service unavailable).
- Optionally add a few `@WebMvcTest` controller tests per service, and one integration-style test (e.g., using WireMock to stub the other services) for a critical cross-service flow like book → assign → deliver.

---

## 10. Suggested Project Structure

Each microservice is a **separate Spring Boot application** (own `pom.xml`, own repository or module, own deployable JAR). A Maven multi-module parent project is recommended to keep things organized for a single developer.

```
courier-system/                     (parent Maven project - optional multi-module wrapper)
│
├── user-auth-service/
│   ├── src/main/java/com.capgemini.courier.auth
│   │   ├── config/                 # Security config, Swagger config, JWT config
│   │   ├── controller/             # Auth, User, Address, Courier controllers
│   │   ├── dto/ (request/response)
│   │   ├── entity/                 # User, CustomerProfile, CourierProfile, Address
│   │   ├── enums/                  # Role, CourierStatus
│   │   ├── exception/              # Custom exceptions + GlobalExceptionHandler
│   │   ├── repository/
│   │   ├── security/                # JWT filter, JWT util
│   │   ├── service/ (+ impl)
│   │   └── UserAuthServiceApplication.java
│   └── src/test/java/...           # Unit tests
│
├── booking-service/
│   ├── src/main/java/com.capgemini.courier.booking
│   │   ├── ai/                      # Spring AI: ChatClient config, prompt templates,
│   │   │                            #   AiBookingExtractionService, BookingExtraction DTO
│   │   ├── client/                 # WebClient/Feign client to User & Auth Service
│   │   ├── config/
│   │   ├── controller/
│   │   ├── dto/ (request/response)
│   │   ├── entity/                 # Booking
│   │   ├── enums/                  # BookingStatus
│   │   ├── exception/
│   │   ├── repository/
│   │   ├── security/                # JWT validation filter (shared secret)
│   │   ├── service/ (+ impl)
│   │   └── BookingServiceApplication.java
│   └── src/test/java/...
│
└── delivery-tracking-service/
    ├── src/main/java/com.capgemini.courier.delivery
    │   ├── client/                  # WebClient/Feign clients to Booking & User-Auth services
    │   ├── config/
    │   ├── controller/
    │   ├── dto/ (request/response)
    │   ├── entity/                  # DeliveryAssignment, DeliveryStatusHistory
    │   ├── enums/                   # DeliveryStatus
    │   ├── exception/
    │   ├── repository/
    │   ├── security/                 # JWT validation filter (shared secret)
    │   ├── service/ (+ impl)
    │   └── DeliveryTrackingServiceApplication.java
    └── src/test/java/...
```

**Notes:**
- Each service runs on its own port (e.g., `8081`, `8082`, `8083`) and has its own Swagger UI.
- A shared JWT secret (via environment variable/config) allows each service to validate tokens independently without calling the Auth service.
- A `docker-compose.yml` at the root (optional) can spin up 3 PostgreSQL databases + 3 services together for local testing.

---

**Next step:** Once this microservice-based plan is approved/reviewed, proceed to detailed low-level design per service — entity schema with columns/constraints, DTO structures, Feign/WebClient client interfaces, and API request/response contracts.
