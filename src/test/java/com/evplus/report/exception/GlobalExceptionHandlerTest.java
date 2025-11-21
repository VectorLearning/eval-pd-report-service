package com.evplus.report.exception;

import com.evplus.report.model.dto.ErrorResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Unit tests for GlobalExceptionHandler.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GlobalExceptionHandlerTest {

    @Mock
    private WebRequest webRequest;

    @Mock
    private ServletWebRequest servletWebRequest;

    @Mock
    private HttpServletRequest httpServletRequest;

    @Mock
    private MethodArgumentNotValidException methodArgumentNotValidException;

    @Mock
    private BindingResult bindingResult;

    private GlobalExceptionHandler exceptionHandler;

    @BeforeEach
    void setUp() {
        exceptionHandler = new GlobalExceptionHandler();
        ReflectionTestUtils.setField(exceptionHandler, "activeProfile", "local");

        when(webRequest.getDescription(false)).thenReturn("uri=/ev-pd-report/v1/test");
        when(servletWebRequest.getRequest()).thenReturn(httpServletRequest);
        when(httpServletRequest.getRequestURI()).thenReturn("/ev-pd-report/v1/test");
    }

    @Test
    void handleValidationException_ReturnsValidationError() {
        // Arrange
        List<String> errors = List.of("field1: error1", "field2: error2");
        ValidationException exception = new ValidationException("Validation failed", errors);

        // Act
        ResponseEntity<ErrorResponse> response = exceptionHandler.handleValidationException(
            exception, servletWebRequest);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).isEqualTo("Validation failed");
        assertThat(response.getBody().getError()).isEqualTo("VALIDATION_ERROR");
    }

    @Test
    void handleMethodArgumentNotValid_ReturnsBadRequestWithFieldErrors() {
        // Arrange
        FieldError fieldError1 = new FieldError("reportRequest", "districtId", "must not be null");
        FieldError fieldError2 = new FieldError("reportRequest", "reportType", "must not be blank");
        when(methodArgumentNotValidException.getBindingResult()).thenReturn(bindingResult);
        when(bindingResult.getAllErrors()).thenReturn(List.of(fieldError1, fieldError2));

        // Act
        ResponseEntity<ErrorResponse> response = exceptionHandler.handleMethodArgumentNotValid(
            methodArgumentNotValidException, servletWebRequest);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).isEqualTo("Invalid request body");
        assertThat(response.getBody().getError()).isEqualTo("VALIDATION_ERROR");
    }

    @Test
    void handleUnsupportedReportType_ReturnsBadRequestStatus() {
        // Arrange
        UnsupportedReportTypeException exception = new UnsupportedReportTypeException("INVALID_TYPE");

        // Act
        ResponseEntity<ErrorResponse> response = exceptionHandler.handleUnsupportedReportType(
            exception, servletWebRequest);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getError()).isEqualTo("UNSUPPORTED_REPORT_TYPE");
    }

    @Test
    void handleUnauthorizedException_ReturnsForbiddenStatus() {
        // Arrange
        UnauthorizedException exception = new UnauthorizedException("Access denied");

        // Act
        ResponseEntity<ErrorResponse> response = exceptionHandler.handleUnauthorizedException(
            exception, servletWebRequest);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).isEqualTo("Access denied");
        assertThat(response.getBody().getError()).isEqualTo("FORBIDDEN");
    }

    @Test
    void handleReportJobNotFoundException_ReturnsNotFoundStatus() {
        // Arrange
        ReportJobNotFoundException exception = new ReportJobNotFoundException("job-123");

        // Act
        ResponseEntity<ErrorResponse> response = exceptionHandler.handleReportJobNotFound(
            exception, servletWebRequest);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getError()).isEqualTo("REPORT_JOB_NOT_FOUND");
    }

    @Test
    void handleReportNotReadyException_ReturnsConflictStatus() {
        // Arrange
        ReportNotReadyException exception = new ReportNotReadyException("job-123", "PROCESSING");

        // Act
        ResponseEntity<ErrorResponse> response = exceptionHandler.handleReportNotReady(
            exception, servletWebRequest);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getError()).isEqualTo("REPORT_NOT_READY");
    }

    @Test
    void handleReportGenerationException_ReturnsInternalServerErrorStatus() {
        // Arrange
        ReportGenerationException exception = new ReportGenerationException("Failed to generate report");

        // Act
        ResponseEntity<ErrorResponse> response = exceptionHandler.handleReportGenerationException(
            exception, servletWebRequest);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getError()).isEqualTo("REPORT_GENERATION_FAILED");
    }

    @Test
    void handleGlobalException_ReturnsInternalServerErrorStatus() {
        // Arrange
        Exception exception = new Exception("Unexpected error occurred");

        // Act
        ResponseEntity<ErrorResponse> response = exceptionHandler.handleGlobalException(
            exception, servletWebRequest);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getError()).isEqualTo("INTERNAL_SERVER_ERROR");
    }

    @Test
    void errorResponse_IncludesTimestamp() {
        // Arrange
        ValidationException exception = new ValidationException("Test error");

        // Act
        ResponseEntity<ErrorResponse> response = exceptionHandler.handleValidationException(
            exception, servletWebRequest);

        // Assert
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getTimestamp()).isNotNull();
    }

    @Test
    void errorResponse_IncludesPath() {
        // Arrange
        ValidationException exception = new ValidationException("Test error");

        // Act
        ResponseEntity<ErrorResponse> response = exceptionHandler.handleValidationException(
            exception, servletWebRequest);

        // Assert
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getPath()).isEqualTo("/ev-pd-report/v1/test");
    }

    @Test
    void handleReportGenerationException_IncludesDebugMessageInLocalProfile() {
        // Arrange
        ReflectionTestUtils.setField(exceptionHandler, "activeProfile", "local");
        ReportGenerationException exception = new ReportGenerationException("Detailed error message");

        // Act
        ResponseEntity<ErrorResponse> response = exceptionHandler.handleReportGenerationException(
            exception, servletWebRequest);

        // Assert
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getDebugMessage()).isEqualTo("Detailed error message");
    }

    @Test
    void handleReportGenerationException_ExcludesDebugMessageInProdProfile() {
        // Arrange
        ReflectionTestUtils.setField(exceptionHandler, "activeProfile", "prod");
        ReportGenerationException exception = new ReportGenerationException("Detailed error message");

        // Act
        ResponseEntity<ErrorResponse> response = exceptionHandler.handleReportGenerationException(
            exception, servletWebRequest);

        // Assert
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getDebugMessage()).isNull();
    }
}
