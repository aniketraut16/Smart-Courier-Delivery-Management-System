package com.smartcourier.notificationservice.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "notifications")
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    // Logical reference to user-service-db.users.id — no FK, cross-service
    @Column(name = "recipient_id", nullable = false)
    private UUID recipientId;

    // Logical reference to booking-service-db.bookings.id — no FK, cross-service
    @Column(name = "booking_id", nullable = false)
    private UUID bookingId;

    @Enumerated(EnumType.STRING)
    @Column(name = "notification_type", length = 20, nullable = false)
    private NotificationType notificationType;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", length = 30, nullable = false)
    private EventType eventType;

    // TEXT in PostgreSQL maps to standard String in JPA
    @Column(name = "message_content", columnDefinition = "TEXT", nullable = false)
    private String messageContent;

    @Enumerated(EnumType.STRING)
    @Column(name = "send_status", length = 20, nullable = false)
    private SendStatus sendStatus = SendStatus.PENDING;

    @Column(name = "sent_at")
    private OffsetDateTime sentAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime updatedAt;
}
