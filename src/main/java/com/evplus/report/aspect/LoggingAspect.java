package com.evplus.report.aspect;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Arrays;

/**
 * Logging Aspect for Controller methods.
 *
 * Automatically logs:
 * - Request details (method, path, parameters)
 * - Response details (status, duration)
 * - Execution duration
 *
 * Uses AOP to intercept all controller methods.
 */
@Aspect
@Component
public class LoggingAspect {

    private static final Logger logger = LoggerFactory.getLogger(LoggingAspect.class);

    /**
     * Pointcut for all controller methods.
     */
    @Pointcut("within(@org.springframework.web.bind.annotation.RestController *)")
    public void controllerMethods() {
    }

    /**
     * Around advice to log controller method execution.
     */
    @Around("controllerMethods()")
    public Object logAround(ProceedingJoinPoint joinPoint) throws Throwable {
        long startTime = System.currentTimeMillis();

        // Get HTTP request details
        ServletRequestAttributes attributes =
            (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();

        if (attributes != null) {
            HttpServletRequest request = attributes.getRequest();

            // Log request details
            logger.info("Request: {} {} from {}",
                request.getMethod(),
                request.getRequestURI(),
                request.getRemoteAddr()
            );

            // Log parameters if present
            String queryString = request.getQueryString();
            if (queryString != null) {
                logger.debug("Query Parameters: {}", queryString);
            }
        }

        // Log method and arguments
        String methodName = joinPoint.getSignature().toShortString();
        logger.debug("Executing: {} with arguments: {}",
            methodName,
            Arrays.toString(joinPoint.getArgs())
        );

        Object result;
        try {
            // Execute the actual method
            result = joinPoint.proceed();

            long executionTime = System.currentTimeMillis() - startTime;

            // Log successful execution
            logger.info("Response: {} completed in {}ms",
                methodName,
                executionTime
            );

            return result;

        } catch (Exception e) {
            long executionTime = System.currentTimeMillis() - startTime;

            // Log error
            logger.error("Request failed: {} after {}ms - Error: {}",
                methodName,
                executionTime,
                e.getMessage()
            );

            throw e;
        }
    }

    /**
     * Pointcut for service layer methods.
     */
    @Pointcut("within(@org.springframework.stereotype.Service *)")
    public void serviceMethods() {
    }

    /**
     * Around advice for service layer - logs with DEBUG level.
     */
    @Around("serviceMethods()")
    public Object logServiceMethods(ProceedingJoinPoint joinPoint) throws Throwable {
        String methodName = joinPoint.getSignature().toShortString();
        logger.debug("Service call: {}", methodName);

        long startTime = System.currentTimeMillis();

        try {
            Object result = joinPoint.proceed();
            long executionTime = System.currentTimeMillis() - startTime;

            logger.debug("Service completed: {} in {}ms", methodName, executionTime);

            return result;

        } catch (Exception e) {
            long executionTime = System.currentTimeMillis() - startTime;

            logger.error("Service failed: {} after {}ms - Error: {}",
                methodName,
                executionTime,
                e.getMessage()
            );

            throw e;
        }
    }
}
