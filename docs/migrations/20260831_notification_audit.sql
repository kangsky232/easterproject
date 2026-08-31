USE smart_smoke;

ALTER TABLE notification_log
    ADD COLUMN audit_status VARCHAR(16) NOT NULL DEFAULT 'PENDING' AFTER sent_at,
    ADD COLUMN audit_result VARCHAR(24) NULL AFTER audit_status,
    ADD COLUMN auditor_username VARCHAR(64) NULL AFTER audit_result,
    ADD COLUMN audit_remark VARCHAR(500) NULL AFTER auditor_username,
    ADD COLUMN audited_at DATETIME NULL AFTER audit_remark,
    ADD INDEX idx_notification_audit (audit_status, status, created_at),
    ADD CONSTRAINT chk_notification_audit_status
        CHECK (audit_status IN ('PENDING', 'COMPLETED')),
    ADD CONSTRAINT chk_notification_audit_result
        CHECK (audit_result IS NULL OR audit_result IN ('NORMAL', 'FOLLOWED_UP'));
