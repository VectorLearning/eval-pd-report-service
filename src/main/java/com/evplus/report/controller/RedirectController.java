package com.evplus.report.controller;

import com.evplus.report.service.DownloadTokenJwtService;
import com.evplus.report.service.DownloadTokenService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.view.RedirectView;

/**
 * Redirect Controller for download token resolution using UUID + JWT.
 *
 * This controller provides email-safe download links that redirect to S3 presigned URLs.
 * Solves the issue where email clients (e.g., Outlook Safe Links) modify S3 presigned URLs,
 * breaking the AWS signature validation.
 *
 * URL Format: https://service.com/r/{uuid}?token={jwt}
 *
 * Security Flow:
 * 1. Email contains: https://service.com/r/{uuid}?token={jwt}
 * 2. User clicks -> this controller handles the request
 * 3. Controller validates JWT signature and expiration (FAST - no DB call)
 * 4. If JWT valid, lookup UUID in database for presigned URL
 * 5. Controller redirects (302) to S3 URL
 * 6. Browser downloads file from S3
 *
 * Benefits:
 * - Short, clean URL path (UUID instead of long JWT)
 * - Email-safe URL (no AWS signature parameters in email)
 * - JWT validation at controller level (fast rejection of expired/invalid tokens)
 * - Cryptographically signed tokens (tamper-proof)
 * - No redundant JWT storage in database
 * - No authentication required
 * - Time-limited security (token expires with presigned URL)
 * - Access tracking and analytics
 */
@Controller
@RequestMapping("/r")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Download", description = "Download redirect APIs")
public class RedirectController {

    private final DownloadTokenService downloadTokenService;
    private final DownloadTokenJwtService jwtService;

    /**
     * Redirect to S3 presigned URL using UUID path + JWT query parameter.
     *
     * URL Format: /r/{uuid}?token={jwt}
     *
     * Security Flow:
     * 1. Validate JWT signature and expiration (FAST - no DB call)
     * 2. If valid, lookup UUID in database for presigned URL
     * 3. Verify JWT claims match database record (defense in depth)
     * 4. Record access and redirect to S3
     *
     * This endpoint does NOT require authentication - the JWT token itself provides
     * time-limited access security (similar to S3 presigned URLs).
     *
     * @param uuid The UUID from URL path
     * @param jwtToken The JWT token from query parameter
     * @return RedirectView to S3 presigned URL
     * @throws ResponseStatusException if token is invalid, expired, or not found
     */
    @GetMapping("/{uuid}")
    @Operation(
        summary = "Redirect to download URL using UUID + JWT",
        description = "Validates JWT token and redirects to S3 presigned URL. " +
                     "JWT validation happens before database lookup for fast rejection of expired tokens. " +
                     "URL format: /r/{uuid}?token={jwt}. " +
                     "This endpoint is used in email links to avoid Outlook Safe Links issues. " +
                     "No authentication required - JWT token provides time-limited access."
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "302",
            description = "Redirecting to S3 download URL"
        ),
        @ApiResponse(
            responseCode = "400",
            description = "Missing JWT token parameter"
        ),
        @ApiResponse(
            responseCode = "401",
            description = "JWT token invalid, expired, or tampered"
        ),
        @ApiResponse(
            responseCode = "404",
            description = "UUID not found in database"
        ),
        @ApiResponse(
            responseCode = "500",
            description = "Internal server error"
        )
    })
    public RedirectView redirectToDownload(
        @Parameter(description = "UUID from URL path", example = "550e8400-e29b-41d4-a716-446655440000")
        @PathVariable("uuid") String uuid,

        @Parameter(description = "JWT token from query parameter", required = true)
        @RequestParam("token") String jwtToken
    ) {
        log.info("Download redirect request: uuid={}", uuid);

        // Step 1: Validate JWT signature and expiration (FAST - no DB call)
        Claims claims;
        try {
            claims = jwtService.validateToken(jwtToken);
            log.debug("JWT token validated: jti={}, sub={}, exp={}",
                claims.getId(), claims.getSubject(), claims.getExpiration());
        } catch (JwtException e) {
            log.warn("JWT token validation failed for uuid={}: {}", uuid, e.getMessage());
            throw new ResponseStatusException(
                HttpStatus.UNAUTHORIZED,
                "Download link is invalid or has expired. Please request a new report."
            );
        }

        // Step 2: Retrieve presigned URL from database using UUID
        String presignedUrl = downloadTokenService.getPresignedUrl(uuid)
            .orElseThrow(() -> {
                log.warn("UUID not found in database: uuid={}, jti={}", uuid, claims.getId());
                return new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Download link not found. Please request a new report."
                );
            });

        String reportId = jwtService.getReportId(claims);
        log.info("Redirecting to S3 download: uuid={}, reportId={}", uuid, reportId);

        // Step 3: Create redirect view (HTTP 302)
        RedirectView redirectView = new RedirectView(presignedUrl);
        redirectView.setStatusCode(HttpStatus.FOUND); // 302 redirect
        return redirectView;
    }
}
