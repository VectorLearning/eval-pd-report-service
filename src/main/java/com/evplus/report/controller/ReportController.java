package com.evplus.report.controller;

import com.evplus.report.exception.ReportJobNotFoundException;
import com.evplus.report.model.dto.ReportRequest;
import com.evplus.report.model.dto.ReportResponse;
import com.evplus.report.model.entity.ReportJob;
import com.evplus.report.model.enums.ReportStatus;
import com.evplus.report.repository.ReportJobRepository;
import com.evplus.report.security.UserPrincipal;
import com.evplus.report.service.ReportGeneratorService;
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
import org.springframework.http.HttpStatus;
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
}
