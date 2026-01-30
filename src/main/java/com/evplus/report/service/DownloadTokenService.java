package com.evplus.report.service;

import com.evplus.report.exception.ReportGenerationException;
import com.evplus.report.model.entity.DownloadToken;
import com.evplus.report.repository.DownloadTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for managing download tokens.
 *
 * Provides functionality to:
 * - Generate secure download URLs with UUID + JWT
 * - Store UUID-to-URL mappings in database
 * - Retrieve presigned URLs from UUIDs (after JWT validation)
 * - Clean up expired tokens
 *
 * Security Architecture:
 * - Short UUID in URL path: /r/{uuid}
 * - JWT as query parameter: ?token={jwt}
 * - JWT validates at controller (fast rejection)
 * - Database stores only UUID (not JWT - avoiding redundancy)
 * - Both JWT and database expiration checks provide defense in depth
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DownloadTokenService {

    private final DownloadTokenRepository downloadTokenRepository;
    private final DownloadTokenJwtService jwtService;

    @Value("${app.base-url}")
    private String appBaseUrl;

    /**
     * Generate a download token URL with UUID path + JWT query parameter.
     *
     * URL Format: https://service.com/r/{uuid}?token={jwt}
     *
     * Security Flow:
     * 1. Generate random UUID for URL path (short, clean)
     * 2. Generate signed JWT token with expiration and claims
     * 3. Store UUID + metadata in database (NOT the JWT - avoiding redundancy)
     * 4. Return redirect URL with UUID path + JWT query parameter
     *
     * Benefits:
     * - Short, clean URL path (UUID instead of long JWT)
     * - No redundant JWT storage in database
     * - JWT validation at controller level (fast rejection)
     *
     * @param presignedUrl S3 presigned URL
     * @param reportId Report job ID
     * @param userId User who requested the report
     * @param districtId District ID
     * @param expiration Duration until token expires
     * @return Redirect URL (e.g., https://service.com/r/{uuid}?token={jwt})
     */
    @Transactional
    public String generateDownloadToken(
            String presignedUrl,
            String reportId,
            Integer userId,
            Integer districtId,
            Duration expiration
    ) {
        try {
            // Generate random UUID for URL path (short, clean)
            String tokenUuid = UUID.randomUUID().toString();

            // Generate signed JWT token with expiration
            String jwtToken = jwtService.generateToken(reportId, userId, districtId, expiration);

            // Calculate expiration time for database record
            LocalDateTime expiresAt = LocalDateTime.now().plus(expiration);

            // Create and save token entity
            // Store UUID (not JWT) - JWT is passed as query parameter, not stored
            DownloadToken downloadToken = DownloadToken.builder()
                .token(tokenUuid)                          // Store short UUID
                .presignedUrl(presignedUrl)
                .reportId(reportId)
                .userId(userId)
                .districtId(districtId)
                .expiresAt(expiresAt)
                .accessCount(0)
                .build();

            downloadTokenRepository.save(downloadToken);

            // Build redirect URL with UUID path + JWT query parameter
            String redirectUrl = String.format("%s/ev-pd-report/v1/r/%s?token=%s",
                appBaseUrl, tokenUuid, jwtToken);

            log.info("Generated download token: reportId={}, uuid={}, expiresAt={}",
                reportId, tokenUuid, expiresAt);

            return redirectUrl;

        } catch (Exception e) {
            log.error("Failed to generate download token: reportId={}", reportId, e);
            throw new ReportGenerationException("Failed to generate download token: " + e.getMessage(), e);
        }
    }

    /**
     * Retrieve presigned URL from UUID token and record access.
     *
     * @param tokenUuid The UUID token from URL path
     * @return Optional containing presigned URL if token is valid and not expired
     */
    @Transactional
    public Optional<String> getPresignedUrl(String tokenUuid) {
        Optional<DownloadToken> downloadToken = downloadTokenRepository.findByToken(tokenUuid);

        if (downloadToken.isEmpty()) {
            log.warn("Download token not found: uuid={}", tokenUuid);
            return Optional.empty();
        }

        DownloadToken dt = downloadToken.get();

        // Check if token is expired in database
        if (dt.isExpired()) {
            log.warn("Download token expired: uuid={}, expiresAt={}", tokenUuid, dt.getExpiresAt());
            return Optional.empty();
        }

        // Record access
        dt.recordAccess();
        downloadTokenRepository.save(dt);

        log.info("Download token accessed: uuid={}, reportId={}, accessCount={}",
            tokenUuid, dt.getReportId(), dt.getAccessCount());

        return Optional.of(dt.getPresignedUrl());
    }


    /**
     * Scheduled job to clean up expired tokens.
     * Runs daily at 2 AM.
     */
    @Scheduled(cron = "0 0 2 * * *")
    @Transactional
    public void cleanupExpiredTokens() {
        try {
            int deletedCount = downloadTokenRepository.deleteExpiredTokens(LocalDateTime.now());
            log.info("Cleaned up expired download tokens: deletedCount={}", deletedCount);
        } catch (Exception e) {
            log.error("Failed to cleanup expired download tokens", e);
        }
    }

    /**
     * Delete all tokens for a specific report.
     *
     * @param reportId Report job ID
     */
    @Transactional
    public void deleteTokensForReport(String reportId) {
        try {
            int deletedCount = downloadTokenRepository.deleteByReportId(reportId);
            log.info("Deleted download tokens for report: reportId={}, deletedCount={}", reportId, deletedCount);
        } catch (Exception e) {
            log.error("Failed to delete download tokens for report: reportId={}", reportId, e);
        }
    }
}
