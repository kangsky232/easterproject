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
        addDeviceTokenHashColumn();
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
                    sent_at DATETIME NOT NULL,
                    created_at DATETIME NOT NULL,
                    INDEX idx_notification_time (created_at),
                    INDEX idx_notification_alert (alert_id),
                    CONSTRAINT fk_notification_alert FOREIGN KEY (alert_id) REFERENCES alert_record(id)
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
}
