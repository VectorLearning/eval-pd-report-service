package com.evplus.report.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Unit tests for CorrelationIdFilter.
 */
@ExtendWith(MockitoExtension.class)
class CorrelationIdFilterTest {

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain filterChain;

    private CorrelationIdFilter filter;

    @BeforeEach
    void setUp() {
        filter = new CorrelationIdFilter();
        MDC.clear();
    }

    @Test
    void doFilter_WhenCorrelationIdHeaderPresent_UsesThatId() throws ServletException, IOException {
        // Arrange
        String existingCorrelationId = "existing-correlation-id-123";
        when(request.getHeader("X-Correlation-ID")).thenReturn(existingCorrelationId);

        // Act
        filter.doFilter(request, response, filterChain);

        // Assert
        ArgumentCaptor<String> headerCaptor = ArgumentCaptor.forClass(String.class);
        verify(response).setHeader(eq("X-Correlation-ID"), headerCaptor.capture());
        assertThat(headerCaptor.getValue()).isEqualTo(existingCorrelationId);
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void doFilter_WhenNoCorrelationIdHeader_GeneratesNewId() throws ServletException, IOException {
        // Arrange
        when(request.getHeader("X-Correlation-ID")).thenReturn(null);

        // Act
        filter.doFilter(request, response, filterChain);

        // Assert
        ArgumentCaptor<String> headerCaptor = ArgumentCaptor.forClass(String.class);
        verify(response).setHeader(eq("X-Correlation-ID"), headerCaptor.capture());
        assertThat(headerCaptor.getValue()).isNotNull();
        assertThat(headerCaptor.getValue()).isNotEmpty();
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void doFilter_WhenEmptyCorrelationIdHeader_GeneratesNewId() throws ServletException, IOException {
        // Arrange
        when(request.getHeader("X-Correlation-ID")).thenReturn("");

        // Act
        filter.doFilter(request, response, filterChain);

        // Assert
        ArgumentCaptor<String> headerCaptor = ArgumentCaptor.forClass(String.class);
        verify(response).setHeader(eq("X-Correlation-ID"), headerCaptor.capture());
        assertThat(headerCaptor.getValue()).isNotNull();
        assertThat(headerCaptor.getValue()).isNotEmpty();
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void doFilter_ClearsMDCAfterFilterChain() throws ServletException, IOException {
        // Arrange
        String correlationId = "test-correlation-id";
        when(request.getHeader("X-Correlation-ID")).thenReturn(correlationId);

        // Act
        filter.doFilter(request, response, filterChain);

        // Assert
        assertThat(MDC.get("correlationId")).isNull();
    }

    @Test
    void doFilter_ClearsMDCEvenWhenExceptionOccurs() throws ServletException, IOException {
        // Arrange
        String correlationId = "test-correlation-id";
        when(request.getHeader("X-Correlation-ID")).thenReturn(correlationId);
        doThrow(new ServletException("Test exception")).when(filterChain).doFilter(request, response);

        // Act & Assert
        try {
            filter.doFilter(request, response, filterChain);
        } catch (ServletException e) {
            // Expected exception
        }

        assertThat(MDC.get("correlationId")).isNull();
    }

    @Test
    void doFilter_AlwaysCallsFilterChain() throws ServletException, IOException {
        // Arrange
        when(request.getHeader("X-Correlation-ID")).thenReturn(null);

        // Act
        filter.doFilter(request, response, filterChain);

        // Assert
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void doFilter_GeneratedIdIsUUID() throws ServletException, IOException {
        // Arrange
        when(request.getHeader("X-Correlation-ID")).thenReturn(null);

        // Act
        filter.doFilter(request, response, filterChain);

        // Assert
        ArgumentCaptor<String> headerCaptor = ArgumentCaptor.forClass(String.class);
        verify(response).setHeader(eq("X-Correlation-ID"), headerCaptor.capture());
        String generatedId = headerCaptor.getValue();

        // UUID format check (8-4-4-4-12 pattern)
        assertThat(generatedId).matches("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$");
    }

    @Test
    void doFilter_AddsCorrelationIdToResponseHeader() throws ServletException, IOException {
        // Arrange
        String correlationId = "test-correlation-id-456";
        when(request.getHeader("X-Correlation-ID")).thenReturn(correlationId);

        // Act
        filter.doFilter(request, response, filterChain);

        // Assert
        verify(response).setHeader("X-Correlation-ID", correlationId);
    }
}
