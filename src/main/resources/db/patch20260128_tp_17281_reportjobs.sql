-- =====================================================
-- Patch: TP-17281 - Async Reporting Service
-- Description: Create tables for report job tracking and threshold configuration
-- Author: Claude Sonnet
-- Date: 2025-01-28
-- =====================================================

-- Table 1: reportjobs
-- Tracks all report generation requests (both sync and async)
CREATE TABLE IF NOT EXISTS reportjobs (
    report_id VARCHAR(36) NOT NULL PRIMARY KEY COMMENT 'UUID for report job',
    district_id INT NOT NULL COMMENT 'Foreign key to districts table',
    user_id INT NOT NULL COMMENT 'Foreign key to users table',
    report_type VARCHAR(50) NOT NULL COMMENT 'Type of report (e.g., USER_ACTIVITY, DUMMY_TEST)',
    report_params TEXT COMMENT 'JSON string containing report criteria and filters',
    status INT NOT NULL DEFAULT 0 COMMENT 'Job status: 0=QUEUED, 1=PROCESSING, 2=COMPLETED, 3=FAILED',
    requested_date DATETIME NOT NULL COMMENT 'When the report was requested',
    started_date DATETIME NULL COMMENT 'When processing started',
    completed_date DATETIME NULL COMMENT 'When processing completed or failed',
    s3_url VARCHAR(500) NULL COMMENT 'Presigned S3 URL for download',
    filename VARCHAR(255) NULL COMMENT 'Generated filename',
    error_message TEXT NULL COMMENT 'Error details if status=FAILED',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Record creation timestamp',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Record update timestamp',
    INDEX idx_district_status (district_id, status) COMMENT 'For district admin queries',
    INDEX idx_user_date (user_id, requested_date DESC) COMMENT 'For user report history',
    INDEX idx_status_requested (status, requested_date) COMMENT 'For job processing queue'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Report generation job tracking';

-- Table 2: threshold_configs
-- Configuration for sync vs async threshold determination
CREATE TABLE IF NOT EXISTS threshold_configs (
    report_type VARCHAR(50) NOT NULL PRIMARY KEY COMMENT 'Report type identifier',
    max_records INT NOT NULL DEFAULT 5000 COMMENT 'Maximum record count before switching to async',
    max_duration_seconds INT NOT NULL DEFAULT 10 COMMENT 'Maximum estimated duration (seconds) before async',
    description VARCHAR(255) NULL COMMENT 'Human-readable description',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Last update timestamp'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Report threshold configuration';

-- Insert default threshold configurations
INSERT INTO threshold_configs (report_type, max_records, max_duration_seconds, description) VALUES
('USER_ACTIVITY', 5000, 10, 'User activity report - generates ~5K rows in 3-5 seconds'),
('DUMMY_TEST', 5000, 10, 'Dummy test report for async pipeline testing')
ON DUPLICATE KEY UPDATE
    max_records = VALUES(max_records),
    max_duration_seconds = VALUES(max_duration_seconds),
    description = VALUES(description);

-- =====================================================
-- Verification Queries (for manual testing)
-- =====================================================
-- SELECT * FROM reportjobs;
-- SELECT * FROM threshold_configs;
-- SHOW INDEX FROM reportjobs;
