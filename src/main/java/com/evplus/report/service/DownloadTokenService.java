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

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Optional;

/**
 * Service for managing download tokens.
 *
 * Provides functionality to:
 * - Generate secure random tokens
 * - Store token-to-URL mappings
 * - Retrieve presigned URLs from tokens
 * - Clean up expired tokens
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DownloadTokenService {

    private final DownloadTokenRepository downloadTokenRepository;
    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${app.base-url}")
    private String appBaseUrl;

    private static final int TOKEN_LENGTH = 24; // 24 bytes = 32 chars in base64

    /**
     * Generate a download token for a presigned URL.
     *
     * @param presignedUrl S3 presigned URL
     * @param reportId Report job ID
     * @param userId User who requested the report
     * @param districtId District ID
     * @param expiration Duration until token expires
     * @return Short redirect URL (e.g., https://service.com/r/abc123)
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
            // Generate secure random token
            String token = generateSecureToken();

            // Calculate expiration time
            LocalDateTime expiresAt = LocalDateTime.now().plus(expiration);

            // Create and save token entity
            DownloadToken downloadToken = DownloadToken.builder()
                .token(token)
                .presignedUrl(presignedUrl)
                .reportId(reportId)
                .userId(userId)
                .districtId(districtId)
                .expiresAt(expiresAt)
                .accessCount(0)
                .build();

            downloadTokenRepository.save(downloadToken);

            // Build redirect URL
            String redirectUrl = String.format("%s/r/%s", appBaseUrl, token);

            log.info("Generated download token: reportId={}, token={}, expiresAt={}, redirectUrl={}",
                reportId, token, expiresAt, redirectUrl);

            return redirectUrl;

        } catch (Exception e) {
            log.error("Failed to generate download token: reportId={}", reportId, e);
            throw new ReportGenerationException("Failed to generate download token: " + e.getMessage(), e);
        }
    }

    /**
     * Retrieve presigned URL from token and record access.
     *
     * @param token The download token
     * @return Optional containing presigned URL if token is valid and not expired
     */
    @Transactional
    public Optional<String> getPresignedUrl(String token) {
        Optional<DownloadToken> downloadToken = downloadTokenRepository.findByToken(token);

        if (downloadToken.isEmpty()) {
            log.warn("Download token not found: token={}", token);
            return Optional.empty();
        }

        DownloadToken dt = downloadToken.get();

        // Check if token is expired
        if (dt.isExpired()) {
            log.warn("Download token expired: token={}, expiresAt={}", token, dt.getExpiresAt());
            return Optional.empty();
        }

        // Record access
        dt.recordAccess();
        downloadTokenRepository.save(dt);

        log.info("Download token accessed: token={}, reportId={}, accessCount={}",
            token, dt.getReportId(), dt.getAccessCount());

        return Optional.of(dt.getPresignedUrl());
    }

    /**
     * Generate a secure random token.
     * Uses SecureRandom and Base64 URL-safe encoding.
     *
     * @return URL-safe random token string
     */
    private String generateSecureToken() {
        byte[] randomBytes = new byte[TOKEN_LENGTH];
        secureRandom.nextBytes(randomBytes);

        // Use URL-safe Base64 encoding (no padding)
        return Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(randomBytes);
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
