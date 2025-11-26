package com.evplus.report.service.handler;

import com.evplus.report.exception.UnsupportedReportTypeException;
import com.evplus.report.model.enums.ReportType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Registry for report handlers.
 * Auto-registers all ReportHandler implementations using Spring's dependency injection.
 * Provides lookup functionality to get the appropriate handler for a report type.
 *
 * This class is a Spring component that receives all ReportHandler beans via
 * constructor injection and builds a lookup map for efficient handler retrieval.
 */
@Component
@Slf4j
public class HandlerRegistry {

    private final Map<ReportType, ReportHandler> handlers = new HashMap<>();

    /**
     * Constructor with dependency injection.
     * Spring automatically injects all beans that implement ReportHandler interface.
     *
     * @param handlerList list of all ReportHandler implementations found by Spring
     */
    public HandlerRegistry(List<ReportHandler> handlerList) {
        // Build the registry map
        for (ReportHandler handler : handlerList) {
            ReportType reportType = handler.getReportType();

            // Check for duplicate handlers
            if (handlers.containsKey(reportType)) {
                log.warn("Duplicate handler found for report type: {}. Overwriting with: {}",
                    reportType, handler.getClass().getSimpleName());
            }

            handlers.put(reportType, handler);
            log.info("Registered report handler: {} for type: {}",
                handler.getClass().getSimpleName(), reportType);
        }

        log.info("Handler registry initialized with {} handlers", handlers.size());
    }

    /**
     * Get the handler for a specific report type.
     *
     * @param reportType the type of report to generate
     * @return the corresponding ReportHandler implementation
     * @throws UnsupportedReportTypeException if no handler exists for this type
     */
    public ReportHandler getHandler(ReportType reportType) {
        ReportHandler handler = handlers.get(reportType);

        if (handler == null) {
            throw new UnsupportedReportTypeException(
                String.format("No handler registered for report type: %s. Available types: %s",
                    reportType, getSupportedReportTypes())
            );
        }

        return handler;
    }

    /**
     * Get all supported report types.
     * Useful for displaying available options to users or for validation.
     *
     * @return set of all registered ReportType values
     */
    public Set<ReportType> getSupportedReportTypes() {
        return handlers.keySet();
    }

    /**
     * Check if a specific report type is supported.
     *
     * @param reportType the report type to check
     * @return true if a handler exists for this type, false otherwise
     */
    public boolean isSupported(ReportType reportType) {
        return handlers.containsKey(reportType);
    }
}
