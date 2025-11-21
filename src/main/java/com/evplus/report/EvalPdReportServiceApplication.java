package com.evplus.report;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

/**
 * Main Spring Boot Application for EV+ Async Reporting Service.
 *
 * This microservice generates and delivers district-level reports asynchronously
 * using Spring Boot 3.5 and Java 21.
 *
 * @author EV+ Development Team
 * @version 1.0.0
 */
@SpringBootApplication
@EnableCaching
public class EvalPdReportServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(EvalPdReportServiceApplication.class, args);
    }

}
