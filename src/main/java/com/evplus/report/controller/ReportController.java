package com.evplus.report.controller;

import com.evplus.report.exception.ReportJobNotFoundException;
import com.evplus.report.exception.ReportNotReadyException;
import com.evplus.report.exception.UnauthorizedException;
import com.evplus.report.model.dto.ActivityByUserCriteria;
import com.evplus.report.model.dto.ReportRequest;
import com.evplus.report.model.dto.ReportResponse;
import com.evplus.report.model.entity.ReportJob;
import com.evplus.report.model.enums.ReportStatus;
import com.evplus.report.repository.ReportJobRepository;
import com.evplus.report.security.UserPrincipal;
import com.evplus.report.service.ReportGeneratorService;
import com.evplus.report.service.S3Service;
import com.evplus.report.service.UserSelectionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST API Controller for report generation and management.
 * Provides endpoints for:
 * - Generating reports (sync or async)
 * - Checking report status
 * - Listing user's reports
 */
@RestController
@RequestMapping("/reports")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Reports", description = "Report generation and management APIs")
public class ReportController {

    private final ReportGeneratorService reportGeneratorService;
    private final ReportJobRepository reportJobRepository;
    private final S3Service s3Service;
    private final UserSelectionService userSelectionService;

    /**
     * Generate a new report.
     * Based on the criteria, the report will be processed either:
     * - Synchronously: Returns the complete report data immediately (HTTP 200)
     * - Asynchronously: Returns a job ID for tracking (HTTP 202)
     *
     * @param request the report request with type and criteria
     * @param userPrincipal the authenticated user (injected by Spring Security)
     * @return report response with status and data/jobId
     */
    @PostMapping
    @Operation(
        summary = "Generate a report",
        description = "Generates a report based on the provided criteria. " +
                     "Small reports are returned immediately (sync), large reports are queued (async)."
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "Report generated successfully (sync)",
            content = @Content(schema = @Schema(implementation = ReportResponse.class))
        ),
        @ApiResponse(
            responseCode = "202",
            description = "Report queued for processing (async)",
            content = @Content(schema = @Schema(implementation = ReportResponse.class))
        ),
        @ApiResponse(
            responseCode = "400",
            description = "Invalid request or validation failed"
        ),
        @ApiResponse(
            responseCode = "401",
            description = "User not authenticated"
        ),
        @ApiResponse(
            responseCode = "500",
            description = "Report generation failed"
        )
    })
    public ResponseEntity<ReportResponse> generateReport(
        @Valid @RequestBody ReportRequest request,
        @AuthenticationPrincipal UserPrincipal userPrincipal
    ) {
        // UserPrincipal is kept for JWT validation only (can be null in local profile)
        // TODO: Add authorization check - validate userId/districtId from request against UserPrincipal claims

        log.info("Report generation request: type={}, userId={}, districtId={}",
            request.getReportType(), request.getUserId(), request.getDistrictId());

        // User selection resolution (-2 for "All Users", -3 for "My Evaluees")
        // is now handled during report generation, not here.
        // This avoids storing thousands of user IDs in the job queue/database.

        ReportResponse response = reportGeneratorService.generateReport(
            request,
            request.getUserId(),
            request.getDistrictId()
        );

        // Return 202 Accepted for async, 200 OK for sync
        HttpStatus status = (response.getStatus() == ReportStatus.QUEUED)
            ? HttpStatus.ACCEPTED
            : HttpStatus.OK;

        return ResponseEntity.status(status).body(response);
    }

    /**
     * Get the status and details of a report job.
     * For completed reports, this includes the download URL.
     *
     * @param jobId the report job ID
     * @param userPrincipal the authenticated user
     * @return report job details
     */
    @GetMapping("/{jobId}")
    @Operation(
        summary = "Get report job status",
        description = "Retrieves the status and details of a report job. " +
                     "For completed reports, includes the download URL."
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "Report job found",
            content = @Content(schema = @Schema(implementation = ReportJob.class))
        ),
        @ApiResponse(
            responseCode = "401",
            description = "User not authenticated"
        ),
        @ApiResponse(
            responseCode = "404",
            description = "Report job not found"
        )
    })
    public ResponseEntity<ReportJob> getReportStatus(
        @Parameter(description = "Report job ID", example = "550e8400-e29b-41d4-a716-446655440000")
        @PathVariable String jobId,
        @AuthenticationPrincipal UserPrincipal userPrincipal
    ) {
        log.debug("Fetching report status: jobId={}, userId={}", jobId, userPrincipal.getUserId());

        ReportJob job = reportJobRepository.findById(jobId)
            .orElseThrow(() -> new ReportJobNotFoundException(
                String.format("Report job not found: %s", jobId)
            ));

        // TODO: Add authorization check - verify user owns this report
        // For now, any authenticated user can view any report

        return ResponseEntity.ok(job);
    }

    /**
     * List all reports for the current user.
     * Returns reports ordered by request date (most recent first).
     *
     * @param userPrincipal the authenticated user
     * @return list of user's report jobs
     */
    @GetMapping
    @Operation(
        summary = "List user's reports",
        description = "Retrieves all report jobs for the current user, " +
                     "ordered by request date (most recent first)."
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "Reports retrieved successfully"
        ),
        @ApiResponse(
            responseCode = "401",
            description = "User not authenticated"
        )
    })
    public ResponseEntity<List<ReportJob>> listUserReports(
        @AuthenticationPrincipal UserPrincipal userPrincipal
    ) {
        log.debug("Listing reports for user: {}", userPrincipal.getUserId());

        List<ReportJob> jobs = reportJobRepository.findByUserIdOrderByRequestedDateDesc(
            userPrincipal.getUserId()
        );

        return ResponseEntity.ok(jobs);
    }

    /**
     * Download a completed report file.
     * Streams the Excel file directly from S3 to the client.
     *
     * @param jobId the report job ID
     * @param userPrincipal the authenticated user
     * @return report file as byte array with download headers
     */
    @GetMapping("/download/{jobId}")
    @Operation(
        summary = "Download report file",
        description = "Downloads a completed report file as an Excel attachment. " +
                     "The report must be in COMPLETED status."
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "Report downloaded successfully",
            content = @Content(mediaType = "application/octet-stream")
        ),
        @ApiResponse(
            responseCode = "401",
            description = "User not authenticated"
        ),
        @ApiResponse(
            responseCode = "403",
            description = "User not authorized to download this report"
        ),
        @ApiResponse(
            responseCode = "404",
            description = "Report job not found"
        ),
        @ApiResponse(
            responseCode = "409",
            description = "Report not ready yet (still processing or queued)"
        ),
        @ApiResponse(
            responseCode = "500",
            description = "Report generation failed or download error"
        )
    })
    public ResponseEntity<byte[]> downloadReport(
        @Parameter(description = "Report job ID", example = "550e8400-e29b-41d4-a716-446655440000")
        @PathVariable String jobId,
        @AuthenticationPrincipal UserPrincipal userPrincipal
    ) {
        log.info("Download request: jobId={}, userId={}", jobId, userPrincipal.getUserId());

        // 1. Retrieve job record
        ReportJob job = reportJobRepository.findById(jobId)
            .orElseThrow(() -> new ReportJobNotFoundException(
                String.format("Report job not found: %s", jobId)
            ));

        // 2. Authorization check
        if (!job.getUserId().equals(userPrincipal.getUserId())) {
            log.warn("Unauthorized download attempt: jobId={}, requestingUserId={}, ownerUserId={}",
                jobId, userPrincipal.getUserId(), job.getUserId());
            throw new UnauthorizedException(
                String.format("You are not authorized to download report %s", jobId)
            );
        }

        // 3. Status validation
        ReportStatus status = job.getStatus();
        if (status != ReportStatus.COMPLETED) {
            log.warn("Report not ready for download: jobId={}, status={}", jobId, status);
            throw new ReportNotReadyException(jobId, status.name());
        }

        // 4. Extract S3 key from URL
        String s3Key = extractS3Key(job.getS3Url());
        log.debug("Extracted S3 key: {}", s3Key);

        // 5. Download from S3
        byte[] reportData = s3Service.downloadReport(s3Key);

        // 6. Build response with download headers
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + job.getFilename() + "\"");
        headers.setContentLength(reportData.length);

        log.info("Report downloaded successfully: jobId={}, filename={}, size={} bytes",
            jobId, job.getFilename(), reportData.length);

        return ResponseEntity
            .ok()
            .headers(headers)
            .body(reportData);
    }

    /**
     * NOTE: User selection resolution has been moved to the report handler level.
     * This method is no longer used but kept for reference.
     * Resolution now happens during report generation to avoid storing
     * thousands of user IDs in the job queue/database.
     *
     * @deprecated Use UserSelectionService.resolveUserIds() in the report handler instead
     */
    @Deprecated
    @SuppressWarnings("unused")
    private void resolveUserSelection_OLD(
            ActivityByUserCriteria criteria,
            Integer requesterId,
            Integer districtId,
            UserPrincipal userPrincipal) {
        // This method is deprecated and no longer used
        // User resolution now happens in ActivityByUserReportHandler
    }

    /**
     * Extract S3 object key from presigned URL.
     * Handles both presigned URLs and direct S3 keys.
     *
     * @param s3Url presigned S3 URL or direct key
     * @return S3 object key (e.g., "reports/123/uuid/file.xlsx")
     */
    private String extractS3Key(String s3Url) {
        if (s3Url == null) {
            throw new IllegalStateException("S3 URL is null");
        }

        // If it's already a key format (starts with "reports/"), return as-is
        if (s3Url.startsWith("reports/")) {
            return s3Url;
        }

        // Extract key from presigned URL
        // Format: https://bucket.s3.region.amazonaws.com/reports/123/uuid/file.xlsx?params
        // or: https://s3.region.amazonaws.com/bucket/reports/123/uuid/file.xlsx?params
        try {
            String path;
            int queryStart = s3Url.indexOf('?');
            String urlWithoutQuery = queryStart > 0 ? s3Url.substring(0, queryStart) : s3Url;

            // Find "reports/" in the URL
            int reportsIndex = urlWithoutQuery.indexOf("reports/");
            if (reportsIndex > 0) {
                path = urlWithoutQuery.substring(reportsIndex);
            } else {
                throw new IllegalArgumentException("Invalid S3 URL format: missing 'reports/' path");
            }

            return path;
        } catch (Exception e) {
            log.error("Failed to extract S3 key from URL: {}", s3Url, e);
            throw new IllegalStateException("Failed to extract S3 key from URL: " + e.getMessage());
        }
    }
}
