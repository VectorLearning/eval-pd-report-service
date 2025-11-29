package com.evplus.report.controller;

import com.evplus.report.model.dto.DummyReportCriteria;
import com.evplus.report.model.dto.ReportRequest;
import com.evplus.report.model.entity.ReportJob;
import com.evplus.report.model.enums.ReportStatus;
import com.evplus.report.model.enums.ReportType;
import com.evplus.report.repository.ReportJobRepository;
import com.evplus.report.security.UserPrincipal;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * REST Controller tests for ReportController with DummyReportCriteria.
 * Tests the HTTP layer endpoints:
 * - POST /reports - Generate report
 * - GET /reports/{jobId} - Get report status
 * - GET /reports - List user's reports
 *
 * Uses @SpringBootTest with @ActiveProfiles("local") to load full application context.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class ReportControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ReportJobRepository reportJobRepository;

    private UserPrincipal testUser;

    @BeforeEach
    void setUp() {
        // Create test user principal
        testUser = UserPrincipal.builder()
                .userId(999)
                .username("testuser")
                .email("test@example.com")
                .firstName("Test")
                .lastName("User")
                .build();

        // Clean up any existing test data
        reportJobRepository.deleteAll();
    }

    @AfterEach
    void tearDown() {
        // Clean up test data
        reportJobRepository.deleteAll();
    }

    /**
     * Helper method to create authentication with UserPrincipal.
     */
    private RequestPostProcessor userPrincipal(UserPrincipal principal) {
        return authentication(new UsernamePasswordAuthenticationToken(
                principal, null, principal.getAuthorities()));
    }

    @Test
    void testGenerateReport_WithValidDummyReportCriteria_ReturnsAccepted() throws Exception {
        // Arrange
        DummyReportCriteria criteria = new DummyReportCriteria();
        criteria.setRecordCount(1000);
        criteria.setTestParameter("controller-test");

        ReportRequest request = new ReportRequest();
        request.setReportType(ReportType.DUMMY_TEST);
        request.setUserId(testUser.getUserId());
        request.setDistrictId(testUser.getUserId());
        request.setCriteria(criteria);

        String requestJson = objectMapper.writeValueAsString(request);

        // Act & Assert
        mockMvc.perform(post("/reports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson)
                        .with(userPrincipal(testUser)))
                .andExpect(status().isAccepted())  // Should be 202 ACCEPTED for async
                .andExpect(jsonPath("$.status", is("QUEUED")))
                .andExpect(jsonPath("$.jobId", notNullValue()))
                .andExpect(jsonPath("$.estimatedCompletionTime", notNullValue()));
    }

    @Test
    void testGenerateReport_WithSmallDataset_ReturnsAccepted() throws Exception {
        // Arrange
        DummyReportCriteria criteria = new DummyReportCriteria();
        criteria.setRecordCount(100);
        criteria.setTestParameter("small-dataset");

        ReportRequest request = new ReportRequest();
        request.setReportType(ReportType.DUMMY_TEST);
        request.setUserId(testUser.getUserId());
        request.setDistrictId(testUser.getUserId());
        request.setCriteria(criteria);

        String requestJson = objectMapper.writeValueAsString(request);

        // Act & Assert
        // Note: DummyReportHandler always exceeds threshold, so even small datasets go async
        mockMvc.perform(post("/reports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson)
                        .with(userPrincipal(testUser)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status", is("QUEUED")))
                .andExpect(jsonPath("$.jobId", notNullValue()));
    }

    @Test
    void testGenerateReport_WithInvalidCriteria_ReturnsBadRequest() throws Exception {
        // Arrange - Invalid recordCount (negative)
        DummyReportCriteria criteria = new DummyReportCriteria();
        criteria.setRecordCount(-100);
        criteria.setTestParameter("invalid-test");

        ReportRequest request = new ReportRequest();
        request.setReportType(ReportType.DUMMY_TEST);
        request.setUserId(testUser.getUserId());
        request.setDistrictId(testUser.getUserId());
        request.setCriteria(criteria);

        String requestJson = objectMapper.writeValueAsString(request);

        // Act & Assert
        mockMvc.perform(post("/reports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson)
                        .with(userPrincipal(testUser)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testGenerateReport_WithExcessiveRecordCount_ReturnsBadRequest() throws Exception {
        // Arrange - Exceeds max limit (1,000,000)
        DummyReportCriteria criteria = new DummyReportCriteria();
        criteria.setRecordCount(2000000);
        criteria.setTestParameter("excessive-test");

        ReportRequest request = new ReportRequest();
        request.setReportType(ReportType.DUMMY_TEST);
        request.setUserId(testUser.getUserId());
        request.setDistrictId(testUser.getUserId());
        request.setCriteria(criteria);

        String requestJson = objectMapper.writeValueAsString(request);

        // Act & Assert
        mockMvc.perform(post("/reports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson)
                        .with(userPrincipal(testUser)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testGenerateReport_WithNullCriteria_ReturnsBadRequest() throws Exception {
        // Arrange - Null criteria
        ReportRequest request = new ReportRequest();
        request.setReportType(ReportType.DUMMY_TEST);
        request.setUserId(testUser.getUserId());
        request.setDistrictId(testUser.getUserId());
        request.setCriteria(null);

        String requestJson = objectMapper.writeValueAsString(request);

        // Act & Assert
        mockMvc.perform(post("/reports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson)
                        .with(userPrincipal(testUser)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testGetReportStatus_WithValidJobId_ReturnsReportJob() throws Exception {
        // Arrange - Create a test report job in database
        ReportJob reportJob = new ReportJob();
        reportJob.setReportId(UUID.randomUUID().toString());
        reportJob.setUserId(testUser.getUserId());
        reportJob.setDistrictId(testUser.getUserId());  // Set required districtId
        reportJob.setReportType(ReportType.DUMMY_TEST);
        reportJob.setStatus(ReportStatus.QUEUED);
        reportJob.setRequestedDate(LocalDateTime.now());

        reportJobRepository.save(reportJob);

        // Act & Assert
        mockMvc.perform(get("/reports/" + reportJob.getReportId())
                        .with(userPrincipal(testUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reportId", is(reportJob.getReportId())))
                .andExpect(jsonPath("$.userId", is(testUser.getUserId())))
                .andExpect(jsonPath("$.reportType", is("DUMMY_TEST")))
                .andExpect(jsonPath("$.statusCode", is(0)));  // QUEUED = 0
    }

    @Test
    void testGetReportStatus_WithNonExistentJobId_ReturnsNotFound() throws Exception {
        // Arrange
        String nonExistentJobId = UUID.randomUUID().toString();

        // Act & Assert
        mockMvc.perform(get("/reports/" + nonExistentJobId)
                        .with(userPrincipal(testUser)))
                .andExpect(status().isNotFound());
    }

    @Test
    void testListUserReports_WithMultipleReports_ReturnsOrderedList() throws Exception {
        // Arrange - Create multiple test report jobs
        ReportJob job1 = new ReportJob();
        job1.setReportId(UUID.randomUUID().toString());
        job1.setUserId(testUser.getUserId());
        job1.setDistrictId(testUser.getUserId());
        job1.setReportType(ReportType.DUMMY_TEST);
        job1.setStatus(ReportStatus.COMPLETED);
        job1.setRequestedDate(LocalDateTime.now().minusHours(2));

        ReportJob job2 = new ReportJob();
        job2.setReportId(UUID.randomUUID().toString());
        job2.setUserId(testUser.getUserId());
        job2.setDistrictId(testUser.getUserId());
        job2.setReportType(ReportType.DUMMY_TEST);
        job2.setStatus(ReportStatus.PROCESSING);
        job2.setRequestedDate(LocalDateTime.now().minusHours(1));

        ReportJob job3 = new ReportJob();
        job3.setReportId(UUID.randomUUID().toString());
        job3.setUserId(testUser.getUserId());
        job3.setDistrictId(testUser.getUserId());
        job3.setReportType(ReportType.DUMMY_TEST);
        job3.setStatus(ReportStatus.QUEUED);
        job3.setRequestedDate(LocalDateTime.now());

        reportJobRepository.save(job1);
        reportJobRepository.save(job2);
        reportJobRepository.save(job3);

        // Act & Assert - Should return reports ordered by requestedDate DESC (most recent first)
        mockMvc.perform(get("/reports")
                        .with(userPrincipal(testUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].reportId", is(job3.getReportId())))  // Most recent
                .andExpect(jsonPath("$[1].reportId", is(job2.getReportId())))
                .andExpect(jsonPath("$[2].reportId", is(job1.getReportId())));  // Oldest
    }

    @Test
    void testListUserReports_WithNoReports_ReturnsEmptyList() throws Exception {
        // Act & Assert
        mockMvc.perform(get("/reports")
                        .with(userPrincipal(testUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    // Note: Security is disabled in local profile, so authentication tests are skipped
    // These tests would pass in dev/stage/prod profiles where security is enabled

    @Test
    void testGenerateReport_WithZeroRecordCount_ReturnsAccepted() throws Exception {
        // Arrange - Edge case: zero records (valid per validation)
        DummyReportCriteria criteria = new DummyReportCriteria();
        criteria.setRecordCount(0);
        criteria.setTestParameter("zero-records");

        ReportRequest request = new ReportRequest();
        request.setReportType(ReportType.DUMMY_TEST);
        request.setUserId(testUser.getUserId());
        request.setDistrictId(testUser.getUserId());
        request.setCriteria(criteria);

        String requestJson = objectMapper.writeValueAsString(request);

        // Act & Assert
        mockMvc.perform(post("/reports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson)
                        .with(userPrincipal(testUser)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status", is("QUEUED")))
                .andExpect(jsonPath("$.jobId", notNullValue()));
    }

    @Test
    void testGenerateReport_WithMaxRecordCount_ReturnsAccepted() throws Exception {
        // Arrange - Edge case: max records (1,000,000)
        DummyReportCriteria criteria = new DummyReportCriteria();
        criteria.setRecordCount(1000000);
        criteria.setTestParameter("max-records");

        ReportRequest request = new ReportRequest();
        request.setReportType(ReportType.DUMMY_TEST);
        request.setUserId(testUser.getUserId());
        request.setDistrictId(testUser.getUserId());
        request.setCriteria(criteria);

        String requestJson = objectMapper.writeValueAsString(request);

        // Act & Assert
        mockMvc.perform(post("/reports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson)
                        .with(userPrincipal(testUser)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status", is("QUEUED")))
                .andExpect(jsonPath("$.jobId", notNullValue()));
    }
}
