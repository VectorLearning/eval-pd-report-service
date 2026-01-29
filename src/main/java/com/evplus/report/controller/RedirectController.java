package com.evplus.report.controller;

import com.evplus.report.service.DownloadTokenService;
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
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.view.RedirectView;

/**
 * Redirect Controller for download token resolution.
 *
 * This controller provides email-safe download links that redirect to S3 presigned URLs.
 * Solves the issue where email clients (e.g., Outlook Safe Links) modify S3 presigned URLs,
 * breaking the AWS signature validation.
 *
 * Flow:
 * 1. Email contains: https://service.com/r/{token}
 * 2. User clicks -> this controller handles the request
 * 3. Controller looks up token -> retrieves S3 presigned URL
 * 4. Controller redirects (302) to S3 URL
 * 5. Browser downloads file from S3
 *
 * Benefits:
 * - Email-safe URL (no AWS signature parameters in email)
 * - Short, clean URLs
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

    /**
     * Redirect to S3 presigned URL using a download token.
     *
     * This endpoint does NOT require authentication - the token itself provides
     * time-limited access security (similar to S3 presigned URLs).
     *
     * @param token The download token from the email link
     * @return RedirectView to S3 presigned URL
     * @throws ResponseStatusException if token is invalid or expired
     */
    @GetMapping("/{token}")
    @Operation(
        summary = "Redirect to download URL",
        description = "Resolves a download token to an S3 presigned URL and redirects. " +
                     "This endpoint is used in email links to avoid Outlook Safe Links issues. " +
                     "No authentication required - token provides time-limited access."
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "302",
            description = "Redirecting to S3 download URL"
        ),
        @ApiResponse(
            responseCode = "404",
            description = "Token not found or expired"
        ),
        @ApiResponse(
            responseCode = "500",
            description = "Internal server error"
        )
    })
    public RedirectView redirectToDownload(
        @Parameter(description = "Download token from email link", example = "abc123xyz789")
        @PathVariable String token
    ) {
        log.info("Download redirect request: token={}", token);

        // Retrieve presigned URL from token
        String presignedUrl = downloadTokenService.getPresignedUrl(token)
            .orElseThrow(() -> {
                log.warn("Invalid or expired download token: token={}", token);
                return new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Download link is invalid or has expired. Please request a new report."
                );
            });

        log.info("Redirecting to S3 download: token={}", token);

        // Create redirect view (HTTP 302)
        RedirectView redirectView = new RedirectView(presignedUrl);
        redirectView.setStatusCode(HttpStatus.FOUND); // 302 redirect
        return redirectView;
    }
}
