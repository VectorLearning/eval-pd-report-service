-- Create download_tokens table for email-safe redirect URLs
-- This table stores mappings from short tokens to S3 presigned URLs
-- Solves the issue where email clients (Outlook Safe Links) modify S3 URLs

CREATE TABLE IF NOT EXISTS download_tokens (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    token VARCHAR(32) NOT NULL UNIQUE COMMENT 'Short random token used in redirect URL',
    presigned_url VARCHAR(2048) NOT NULL COMMENT 'Full S3 presigned URL with AWS signatures',
    report_id VARCHAR(36) NOT NULL COMMENT 'Report job ID this token is associated with',
    user_id INT NOT NULL COMMENT 'User who requested the report',
    district_id INT NOT NULL COMMENT 'District ID for the report',
    expires_at DATETIME NOT NULL COMMENT 'Token expiration timestamp (matches presigned URL expiration)',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Record creation timestamp',
    access_count INT NOT NULL DEFAULT 0 COMMENT 'Number of times token has been accessed',
    last_accessed_at DATETIME NULL COMMENT 'Last access timestamp',

    INDEX idx_token (token),
    INDEX idx_expires_at (expires_at),
    INDEX idx_report_id (report_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='Download token to presigned URL mappings for email-safe download links';
