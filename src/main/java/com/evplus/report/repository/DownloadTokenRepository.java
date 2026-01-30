package com.evplus.report.repository;

import com.evplus.report.model.entity.DownloadToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Repository for DownloadToken entity.
 * Provides database access for token-to-URL mappings.
 */
@Repository
public interface DownloadTokenRepository extends JpaRepository<DownloadToken, Long> {

    /**
     * Find a download token by its token string.
     *
     * @param token the token string
     * @return Optional containing the DownloadToken if found
     */
    Optional<DownloadToken> findByToken(String token);

    /**
     * Check if a token exists and is not expired.
     *
     * @param token the token string
     * @param now current timestamp
     * @return true if token exists and is valid
     */
    @Query("SELECT CASE WHEN COUNT(dt) > 0 THEN true ELSE false END " +
           "FROM DownloadToken dt " +
           "WHERE dt.token = :token AND dt.expiresAt > :now")
    boolean existsByTokenAndNotExpired(@Param("token") String token, @Param("now") LocalDateTime now);

    /**
     * Delete all expired tokens.
     * This should be run periodically as a cleanup job.
     *
     * @param now current timestamp
     * @return number of deleted tokens
     */
    @Modifying
    @Query("DELETE FROM DownloadToken dt WHERE dt.expiresAt < :now")
    int deleteExpiredTokens(@Param("now") LocalDateTime now);

    /**
     * Delete all tokens for a specific report.
     * Useful when a report is deleted or regenerated.
     *
     * @param reportId the report job ID
     * @return number of deleted tokens
     */
    @Modifying
    @Query("DELETE FROM DownloadToken dt WHERE dt.reportId = :reportId")
    int deleteByReportId(@Param("reportId") String reportId);
}
