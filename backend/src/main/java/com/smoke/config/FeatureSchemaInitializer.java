package com.smoke.config;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class FeatureSchemaInitializer implements InitializingBean {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void afterPropertiesSet() {
        addFalseAlarmColumn();
        addAlertMetadataColumns();
        expandAlertTypeConstraint();
        addDeviceTokenHashColumn();
        migrateSmokeThresholdDefault();
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS alert_review (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    alert_id BIGINT NOT NULL,
                    review_type VARCHAR(32) NOT NULL,
                    review_result VARCHAR(500) NOT NULL,
                    operator_name VARCHAR(64) NOT NULL,
                    created_at DATETIME NOT NULL,
                    INDEX idx_alert_review_alert_time (alert_id, created_at),
                    CONSTRAINT fk_alert_review_alert FOREIGN KEY (alert_id) REFERENCES alert_record(id)
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS notification_log (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    alert_id BIGINT NOT NULL,
                    device_id VARCHAR(64) NOT NULL,
                    channel VARCHAR(16) NOT NULL,
                    receiver VARCHAR(64) NOT NULL,
                    content VARCHAR(500) NOT NULL,
                    status VARCHAR(16) NOT NULL,
                    sent_at DATETIME NULL,
                    created_at DATETIME NOT NULL,
                    INDEX idx_notification_time (created_at),
                    INDEX idx_notification_alert (alert_id),
                    CONSTRAINT fk_notification_alert FOREIGN KEY (alert_id) REFERENCES alert_record(id)
                )
                """);
        makeNotificationSentAtNullable();
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS dingtalk_recipient (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    user_id VARCHAR(128) NOT NULL,
                    display_name VARCHAR(100) NULL,
                    enabled TINYINT NOT NULL DEFAULT 1,
                    first_seen_at DATETIME NOT NULL,
                    last_seen_at DATETIME NOT NULL,
                    created_at DATETIME NOT NULL,
                    updated_at DATETIME NOT NULL,
                    UNIQUE KEY uk_dingtalk_recipient_user (user_id),
                    INDEX idx_dingtalk_recipient_enabled (enabled)
                )
                """);
    }

    private void addFalseAlarmColumn() {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.columns
                WHERE table_schema = DATABASE() AND table_name = 'alert_record' AND column_name = 'false_alarm'
                """, Integer.class);
        if (count != null && count == 0) {
            jdbcTemplate.execute("ALTER TABLE alert_record ADD COLUMN false_alarm TINYINT NOT NULL DEFAULT 0 AFTER status");
        }
    }

    private void addDeviceTokenHashColumn() {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.columns
                WHERE table_schema = DATABASE() AND table_name = 'device' AND column_name = 'device_token_hash'
                """, Integer.class);
        if (count != null && count == 0) {
            jdbcTemplate.execute("ALTER TABLE device ADD COLUMN device_token_hash VARCHAR(128) NULL AFTER unbind_time");
        }
    }

    private void addAlertMetadataColumns() {
        if (!columnExists("alert_record", "severity")) {
            jdbcTemplate.execute(
                    "ALTER TABLE alert_record ADD COLUMN severity VARCHAR(16) NULL AFTER threshold");
        }
        if (!columnExists("alert_record", "rule_description")) {
            jdbcTemplate.execute(
                    "ALTER TABLE alert_record ADD COLUMN rule_description VARCHAR(255) NULL AFTER severity");
        }
    }

    private void expandAlertTypeConstraint() {
        java.util.List<String> clauses = jdbcTemplate.queryForList("""
                SELECT cc.check_clause
                FROM information_schema.table_constraints tc
                JOIN information_schema.check_constraints cc
                  ON cc.constraint_schema = tc.constraint_schema
                 AND cc.constraint_name = tc.constraint_name
                WHERE tc.table_schema = DATABASE()
                  AND tc.table_name = 'alert_record'
                  AND tc.constraint_name = 'chk_alert_type'
                  AND tc.constraint_type = 'CHECK'
                """, String.class);
        if (clauses.stream().anyMatch(clause -> clause != null && clause.contains("7"))) {
            return;
        }
        if (!clauses.isEmpty()) {
            jdbcTemplate.execute("ALTER TABLE alert_record DROP CHECK chk_alert_type");
        }
        jdbcTemplate.execute("""
                ALTER TABLE alert_record
                ADD CONSTRAINT chk_alert_type CHECK (alert_type IN (1, 2, 3, 4, 5, 6, 7))
                """);
    }

    private void migrateSmokeThresholdDefault() {
        String currentDefault = jdbcTemplate.queryForObject("""
                SELECT column_default
                FROM information_schema.columns
                WHERE table_schema = DATABASE()
                  AND table_name = 'device'
                  AND column_name = 'smoke_threshold'
                """, String.class);
        if (!"100".equals(currentDefault)) {
            jdbcTemplate.execute("ALTER TABLE device ALTER COLUMN smoke_threshold SET DEFAULT 100");
        }
        jdbcTemplate.update("UPDATE device SET smoke_threshold = 100 WHERE smoke_threshold <> 100");
    }

    private void makeNotificationSentAtNullable() {
        String nullable = jdbcTemplate.queryForObject("""
                SELECT is_nullable
                FROM information_schema.columns
                WHERE table_schema = DATABASE()
                  AND table_name = 'notification_log'
                  AND column_name = 'sent_at'
                """, String.class);
        if (!"YES".equalsIgnoreCase(nullable)) {
            jdbcTemplate.execute("ALTER TABLE notification_log MODIFY COLUMN sent_at DATETIME NULL");
        }
    }

    private boolean columnExists(String table, String column) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.columns
                WHERE table_schema = DATABASE() AND table_name = ? AND column_name = ?
                """, Integer.class, table, column);
        return count != null && count > 0;
    }
}
