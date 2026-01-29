package com.evplus.report.model.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * JPA Entity for download token to presigned URL mapping.
 *
 * This entity stores short-lived tokens that map to S3 presigned URLs.
 * Tokens are used to create email-safe download links that won't be
 * corrupted by email clients (e.g., Outlook Safe Links).
 *
 * Example flow:
 * 1. Service generates S3 presigned URL (valid for 7 days)
 * 2. Service creates short token and stores mapping
 * 3. Email contains: https://service.com/r/{token}
 * 4. User clicks -> service looks up token -> redirects to S3 URL
 */
@Entity
@Table(name = "download_tokens", indexes = {
    @Index(name = "idx_token", columnList = "token", unique = true),
    @Index(name = "idx_expires_at", columnList = "expires_at")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DownloadToken {

    /**
     * Auto-generated primary key.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    /**
     * Short random token (URL-safe, alphanumeric).
     * Used in the redirect URL: /r/{token}
     */
    @Column(name = "token", length = 32, nullable = false, unique = true)
    private String token;

    /**
     * The S3 presigned URL this token maps to.
     * This is the actual download URL with AWS signatures.
     */
    @Column(name = "presigned_url", length = 2048, nullable = false)
    private String presignedUrl;

    /**
     * Report job ID this token is associated with.
     * Used for auditing and cleanup.
     */
    @Column(name = "report_id", length = 36, nullable = false)
    private String reportId;

    /**
     * User ID who requested the report.
     * Used for auditing.
     */
    @Column(name = "user_id", nullable = false)
    private Integer userId;

    /**
     * District ID for the report.
     * Used for auditing and cleanup.
     */
    @Column(name = "district_id", nullable = false)
    private Integer districtId;

    /**
     * Token expiration timestamp.
     * Should match the presigned URL expiration (7 days).
     */
    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    /**
     * Record creation timestamp.
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * Number of times this token has been accessed.
     * Used for analytics and abuse detection.
     */
    @Column(name = "access_count", nullable = false)
    @Builder.Default
    private Integer accessCount = 0;

    /**
     * Last access timestamp.
     * Updated each time the token is used.
     */
    @Column(name = "last_accessed_at")
    private LocalDateTime lastAccessedAt;

    /**
     * JPA lifecycle callback to set timestamp before insert.
     */
    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    /**
     * Check if token is expired.
     */
    @Transient
    public boolean isExpired() {
        return expiresAt != null && LocalDateTime.now().isAfter(expiresAt);
    }

    /**
     * Increment access count and update last accessed time.
     */
    public void recordAccess() {
        this.accessCount = (this.accessCount == null ? 0 : this.accessCount) + 1;
        this.lastAccessedAt = LocalDateTime.now();
    }
}
