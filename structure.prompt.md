You are a senior Java Spring Boot architect.

Your task is to generate the COMPLETE initial project skeleton for my microservices project.

DO NOT explain anything.
DO NOT skip files.
DO NOT leave TODOs unless absolutely necessary.

Generate the entire folder structure with production-quality boilerplate.

==========================================================
PROJECT INFORMATION
==========================================================

Project Name:
Smart Courier

Architecture:
Maven Multi Module Project

Java:
21

Spring Boot:
4.0.7

Packaging:
Jar

Configuration Format:
application.yml

Root Package:
com.smartcourier

==========================================================
PROJECT STRUCTURE
==========================================================

Create this exact structure.

smart-courier/

├── pom.xml
│
├── discovery-server/
│ ├── pom.xml
│ └── src/main/
│
├── api-gateway/
│ ├── pom.xml
│ └── src/main/
│
├── user-service/
│ ├── pom.xml
│ └── src/main/
│
├── booking-service/
│ ├── pom.xml
│ └── src/main/
│
├── tracking-service/
│ ├── pom.xml
│ └── src/main/
│
└── notification-service/
├── pom.xml
└── src/main/

==========================================================
PARENT POM
==========================================================

Generate a proper parent pom.xml.

Packaging must be pom.

It should contain

- groupId
- artifactId
- version
- modules
- Java version
- Spring Boot version
- Spring Cloud BOM
- Dependency Management
- Plugin Management
- Compiler Plugin
- Surefire Plugin
- UTF-8 encoding
- Maven compiler source & target

Every child module must inherit from this parent.

No duplicate version declarations inside child pom.xml files unless absolutely required.

==========================================================
MODULES
==========================================================

Create these modules.

discovery-server

api-gateway

user-service

booking-service

tracking-service

notification-service

==========================================================
DEPENDENCIES
==========================================================

Discovery Server

- Eureka Server

---

API Gateway

- Spring Cloud Gateway
- Spring Security
- OAuth2 Resource Server
- Eureka Discovery Client

---

User Service

- Spring Web
- Spring Data JPA
- PostgreSQL Driver
- Validation
- Lombok
- Spring Security
- OAuth2 Resource Server

---

Booking Service

- Spring Web
- Spring Data JPA
- PostgreSQL Driver
- Validation
- Lombok
- Spring Security
- OAuth2 Resource Server
- OpenFeign

---

Tracking Service

- Spring Web
- Spring Data JPA
- PostgreSQL Driver
- Validation
- Lombok
- Spring Security
- OAuth2 Resource Server
- OpenFeign

---

Notification Service

- Spring Web
- Spring Data JPA
- PostgreSQL Driver
- Validation
- Lombok
- Spring Security
- OAuth2 Resource Server
- OpenFeign

==========================================================
MANUAL DEPENDENCIES
==========================================================

Add these manually inside pom.xml where required.

User Service

- JJWT
- SpringDoc OpenAPI

Booking Service

- JJWT
- SpringDoc OpenAPI
- Spring AI Starter

Tracking Service

- JJWT
- SpringDoc OpenAPI

Notification Service

- JJWT
- SpringDoc OpenAPI

Gateway

- JJWT
- SpringDoc OpenAPI WebFlux

Use compatible stable versions.

==========================================================
JAVA PACKAGE STRUCTURE
==========================================================

Create the proper Java package.

Discovery

com.smartcourier.discovery

Gateway

com.smartcourier.gateway

User

com.smartcourier.userservice

Booking

com.smartcourier.bookingservice

Tracking

com.smartcourier.trackingservice

Notification

com.smartcourier.notificationservice

==========================================================
MAIN CLASS
==========================================================

Generate the Spring Boot starter class inside every module.

Example

@SpringBootApplication
public class UserServiceApplication

Same for every module.

Use proper naming conventions.

==========================================================
RESOURCE STRUCTURE
==========================================================

Inside every module create

src/main/resources

application.yml

==========================================================
APPLICATION CONFIGURATION
==========================================================

Populate every application.yml.

Do NOT leave it empty.

Each service should have

spring:

application:

name:

server:

port:

logging:

level:

management:

endpoints:

web:

exposure:

include: health,info

---

Discovery Server

Port

8761

Configure Eureka Server properly.

---

Gateway

Port

8080

Configure

- Eureka Client
- Gateway
- Discovery Locator
- Actuator
- Placeholder routes for every service

---

User Service

Port

8081

Configure

- PostgreSQL datasource
- Hibernate
- JPA
- Eureka Client
- JWT placeholder properties
- Logging

---

Booking Service

Port

8082

Configure

- PostgreSQL datasource
- JPA
- Eureka Client
- Feign
- Spring AI placeholder configuration
- JWT placeholder

---

Tracking Service

Port

8083

Configure

- PostgreSQL datasource
- JPA
- Eureka Client
- Feign
- JWT placeholder

---

Notification Service

Port

8084

Configure

- PostgreSQL datasource
- JPA
- Eureka Client
- Feign
- JWT placeholder

==========================================================
DATABASE PLACEHOLDERS
==========================================================

Use dummy PostgreSQL configuration.

Example

Host

localhost

Port

5432

Username

dummy_user

Password

dummy_password

Database names

user_service_db

booking_service_db

tracking_service_db

notification_service_db

==========================================================
JWT PLACEHOLDER
==========================================================

Create placeholder configuration.

jwt:

secret:

expiration:

refresh-expiration:

==========================================================
SPRING AI PLACEHOLDER
==========================================================

Create placeholder values.

api-key

model

temperature

==========================================================
LOGGING
==========================================================

Configure useful logging.

==========================================================
ACTUATOR
==========================================================

Enable

health

info

==========================================================
BOILERPLATE
==========================================================

Generate a project that can be opened directly in IntelliJ IDEA.

Everything should compile.

No missing parent references.

No missing package declarations.

No placeholder Java files besides the Spring Boot main class.

No README.

No Docker.

No business logic.

No entities.

No controllers.

No services.

No repositories.

This task is ONLY for creating the complete project skeleton.

==========================================================
OUTPUT
==========================================================

Generate every directory and every file with full contents.

At the very end provide a section titled:

"DUMMY VALUES TO REPLACE"

List every dummy value, where it appears, and what it should eventually be replaced with, including:

- Database host
- Database username
- Database password
- JWT secret
- JWT expiration
- Spring AI API key
- Spring AI model
- Gateway route placeholders
- Any other placeholder values introduced
