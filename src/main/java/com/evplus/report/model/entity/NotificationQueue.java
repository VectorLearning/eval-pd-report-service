package com.evplus.report.model.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * JPA Entity for notification queue.
 * Maps to the existing 'notification_queue' table in the database.
 * Write-only entity used to trigger notification delivery.
 */
@Entity
@Table(name = "notification_queue")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class NotificationQueue {

    /**
     * Auto-generated primary key.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, columnDefinition = "INT")
    private Integer id;

    /**
     * District ID for the notification.
     */
    @Column(name = "district_id", nullable = false)
    private Integer districtId;

    /**
     * Priority level (e.g., 'IMMEDIATELY' for urgent notifications).
     */
    @Column(name = "level", length = 40)
    private String level;

    /**
     * Relationship type (optional).
     */
    @Column(name = "relationship", length = 40)
    private String relationship;

    /**
     * Recipient user ID (optional, can be derived from notification event).
     */
    @Column(name = "recipient_id")
    private Integer recipientId;

    /**
     * Foreign key to notification_events table.
     */
    @Column(name = "notification_event_id", nullable = false)
    private Integer notificationEventId;

    /**
     * Flag indicating if message has been queued to SQS.
     * 1 = queued, 0 = not queued.
     */
    @Column(name = "sqs_queued", nullable = false)
    private Boolean sqsQueued = false;
}
