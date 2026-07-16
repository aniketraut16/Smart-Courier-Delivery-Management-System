package com.smartcourier.trackingservice.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "current_booking_status")
public class CurrentBookingStatus {

    // Logical reference to booking-service-db.bookings.id — no cross-service FK, but this is the PK here
    @Id
    @Column(name = "booking_id", updatable = false, nullable = false)
    private UUID bookingId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private BookingStatus status;

    // The database constraint is a real FK: FOREIGN KEY (latest_status_update_id) REFERENCES status_updates(id)
    // We use @ManyToOne since it's pointing to one status update, which ensures standard JPA mapping
    @ManyToOne
    @JoinColumn(name = "latest_status_update_id", nullable = false)
    private StatusUpdate latestStatusUpdate;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime updatedAt;
}
