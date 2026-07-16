package com.smartcourier.bookingservice.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "bookings")
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    // Logical reference to user-service-db.users.id — no FK, cross-service
    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    // Logical reference to user-service-db.users.id — no FK, cross-service
    @Column(name = "courier_id")
    private UUID courierId;

    // Note: The @PositiveOrZero annotation is for application-level validation and does not replace the database-level CHECK constraint.
    @PositiveOrZero
    @Column(name = "fare_estimate", precision = 10, scale = 2, nullable = false)
    private BigDecimal fareEstimate;

    // Note: The @PositiveOrZero annotation is for application-level validation and does not replace the database-level CHECK constraint.
    @PositiveOrZero
    @Column(name = "distance_km", precision = 8, scale = 2)
    private BigDecimal distanceKm;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private BookingStatus status = BookingStatus.PLACED;

    @Column(name = "assigned_at")
    private OffsetDateTime assignedAt;

    @Column(name = "delivered_at")
    private OffsetDateTime deliveredAt;

    @Column(name = "cancelled_at")
    private OffsetDateTime cancelledAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime updatedAt;
}
