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
                    audit_status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
                    audit_result VARCHAR(24) NULL,
                    auditor_username VARCHAR(64) NULL,
                    audit_remark VARCHAR(500) NULL,
                    audited_at DATETIME NULL,
                    created_at DATETIME NOT NULL,
                    INDEX idx_notification_time (created_at),
                    INDEX idx_notification_alert (alert_id),
                    INDEX idx_notification_audit (audit_status, status, created_at),
                    CONSTRAINT chk_notification_audit_status CHECK (audit_status IN ('PENDING', 'COMPLETED')),
                    CONSTRAINT chk_notification_audit_result CHECK (audit_result IS NULL OR audit_result IN ('NORMAL', 'FOLLOWED_UP')),
                    CONSTRAINT fk_notification_alert FOREIGN KEY (alert_id) REFERENCES alert_record(id)
                )
                """);
        makeNotificationSentAtNullable();
        initializeNotificationAuditSchema();
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
        initializeMapSchema();
        initializeHazardSchema();
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

    private void initializeNotificationAuditSchema() {
        if (!columnExists("notification_log", "audit_status")) {
            jdbcTemplate.execute("ALTER TABLE notification_log ADD COLUMN audit_status VARCHAR(16) NOT NULL DEFAULT 'PENDING' AFTER sent_at");
        }
        if (!columnExists("notification_log", "audit_result")) {
            jdbcTemplate.execute("ALTER TABLE notification_log ADD COLUMN audit_result VARCHAR(24) NULL AFTER audit_status");
        }
        if (!columnExists("notification_log", "auditor_username")) {
            jdbcTemplate.execute("ALTER TABLE notification_log ADD COLUMN auditor_username VARCHAR(64) NULL AFTER audit_result");
        }
        if (!columnExists("notification_log", "audit_remark")) {
            jdbcTemplate.execute("ALTER TABLE notification_log ADD COLUMN audit_remark VARCHAR(500) NULL AFTER auditor_username");
        }
        if (!columnExists("notification_log", "audited_at")) {
            jdbcTemplate.execute("ALTER TABLE notification_log ADD COLUMN audited_at DATETIME NULL AFTER audit_remark");
        }
        if (!indexExists("notification_log", "idx_notification_audit")) {
            jdbcTemplate.execute("CREATE INDEX idx_notification_audit ON notification_log (audit_status, status, created_at)");
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

    private boolean indexExists(String table, String index) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.statistics
                WHERE table_schema = DATABASE() AND table_name = ? AND index_name = ?
                """, Integer.class, table, index);
        return count != null && count > 0;
    }

    private void initializeMapSchema() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS map_building (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    building_code VARCHAR(32) NOT NULL,
                    building_name VARCHAR(100) NOT NULL,
                    position_x DECIMAL(8,2) NOT NULL,
                    position_z DECIMAL(8,2) NOT NULL,
                    width DECIMAL(8,2) NOT NULL,
                    depth DECIMAL(8,2) NOT NULL,
                    floors INT NOT NULL,
                    enabled TINYINT NOT NULL DEFAULT 1,
                    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                    UNIQUE KEY uk_map_building_code (building_code),
                    CONSTRAINT chk_map_building_floors CHECK (floors > 0),
                    CONSTRAINT chk_map_building_enabled CHECK (enabled IN (0, 1))
                )
                """);
        jdbcTemplate.update("""
                INSERT INTO map_building
                    (building_code, building_name, position_x, position_z, width, depth, floors)
                VALUES
                    ('A1', '1号住宅楼', 16, 18, 18, 14, 6),
                    ('A2', '2号住宅楼', 47, 12, 22, 16, 8),
                    ('A3', '3号住宅楼', 75, 28, 17, 13, 5)
                ON DUPLICATE KEY UPDATE building_name = VALUES(building_name)
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS device_map_position (
                    device_id VARCHAR(64) PRIMARY KEY,
                    building_code VARCHAR(32) NOT NULL,
                    floor_no INT NOT NULL,
                    room_label VARCHAR(64) NOT NULL,
                    position_x DECIMAL(8,2) NOT NULL,
                    position_z DECIMAL(8,2) NOT NULL,
                    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                    INDEX idx_map_position_building_floor (building_code, floor_no),
                    CONSTRAINT fk_map_position_device FOREIGN KEY (device_id) REFERENCES device(device_id),
                    CONSTRAINT fk_map_position_building FOREIGN KEY (building_code) REFERENCES map_building(building_code),
                    CONSTRAINT chk_map_position_floor CHECK (floor_no > 0),
                    CONSTRAINT chk_map_position_coordinates CHECK (position_x >= 0 AND position_z >= 0)
                )
                """);
    }

    private void initializeHazardSchema() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS hazard_ticket (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    ticket_no VARCHAR(32) NOT NULL,
                    title VARCHAR(100) NOT NULL,
                    description VARCHAR(1000) NOT NULL,
                    location VARCHAR(200) NOT NULL,
                    priority VARCHAR(16) NOT NULL,
                    status VARCHAR(24) NOT NULL,
                    reporter_username VARCHAR(64) NOT NULL,
                    assignee_username VARCHAR(64) NULL,
                    resolution VARCHAR(1000) NULL,
                    reviewer_username VARCHAR(64) NULL,
                    created_at DATETIME NOT NULL,
                    updated_at DATETIME NOT NULL,
                    closed_at DATETIME NULL,
                    UNIQUE KEY uk_hazard_ticket_no (ticket_no),
                    INDEX idx_hazard_status_time (status, updated_at),
                    INDEX idx_hazard_reporter_time (reporter_username, created_at),
                    INDEX idx_hazard_assignee_status (assignee_username, status),
                    CONSTRAINT chk_hazard_priority CHECK (priority IN ('LOW', 'MEDIUM', 'HIGH', 'URGENT')),
                    CONSTRAINT chk_hazard_status CHECK (status IN ('REPORTED', 'PROCESSING', 'PENDING_REVIEW', 'CLOSED'))
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS hazard_action (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    ticket_id BIGINT NOT NULL,
                    action_type VARCHAR(24) NOT NULL,
                    operator_name VARCHAR(64) NOT NULL,
                    remark VARCHAR(1000) NOT NULL,
                    created_at DATETIME NOT NULL,
                    INDEX idx_hazard_action_ticket_time (ticket_id, created_at),
                    CONSTRAINT fk_hazard_action_ticket FOREIGN KEY (ticket_id)
                        REFERENCES hazard_ticket(id) ON DELETE CASCADE,
                    CONSTRAINT chk_hazard_action_type CHECK (
                        action_type IN ('REPORTED', 'CLAIMED', 'SUBMITTED', 'APPROVED', 'REJECTED')
                    )
                )
                """);
    }
}
