-- 智慧烟感监测系统 数据库建表脚本

CREATE DATABASE IF NOT EXISTS smart_smoke
    DEFAULT CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;
USE smart_smoke;

-- 设备表
CREATE TABLE IF NOT EXISTS device (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    device_id VARCHAR(64) UNIQUE NOT NULL COMMENT '设备编号',
    device_name VARCHAR(100) COMMENT '设备名称',
    location VARCHAR(200) COMMENT '安装位置',
    status TINYINT DEFAULT 0 COMMENT '0-离线 1-在线',
    smoke_threshold INT DEFAULT 2000 COMMENT '烟雾阈值(ppm)',
    battery INT COMMENT '电量百分比',
    last_heartbeat DATETIME COMMENT '最后心跳时间',
    bind_time DATETIME COMMENT '绑定时间',
    bound TINYINT NOT NULL DEFAULT 1 COMMENT '0-已解绑 1-已绑定',
    unbind_time DATETIME COMMENT '解绑时间',
    device_token_hash VARCHAR(128) COMMENT '设备接入令牌的 SHA-256 摘要，不保存明文',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_device_status_heartbeat (status, last_heartbeat),
    CONSTRAINT chk_device_status CHECK (status IN (0, 1)),
    CONSTRAINT chk_device_bound CHECK (bound IN (0, 1)),
    CONSTRAINT chk_device_battery CHECK (battery IS NULL OR battery BETWEEN 0 AND 100),
    CONSTRAINT chk_device_threshold CHECK (smoke_threshold > 0)
);

-- 烟雾数据表
CREATE TABLE IF NOT EXISTS smoke_data (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    device_id VARCHAR(64) NOT NULL,
    message_id VARCHAR(64) COMMENT '设备消息唯一编号，用于幂等去重',
    concentration DECIMAL(12,2) NOT NULL COMMENT '烟雾浓度(ppm，保留两位小数)',
    temperature DECIMAL(12,2) COMMENT '环境温度',
    humidity DECIMAL(12,2) COMMENT '环境湿度',
    current_value DECIMAL(12,2) COMMENT '设备电流',
    wire_temperature DECIMAL(12,2) COMMENT '线缆温度',
    co_value DECIMAL(12,2) COMMENT '一氧化碳值',
    beep_status VARCHAR(16) COMMENT '蜂鸣器状态',
    timestamp DATETIME NOT NULL COMMENT '数据时间',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_device_time (device_id, timestamp),
    UNIQUE INDEX uk_smoke_message (device_id, message_id),
    CONSTRAINT fk_smoke_device FOREIGN KEY (device_id) REFERENCES device(device_id),
    CONSTRAINT chk_smoke_concentration CHECK (concentration >= 0)
);

-- 告警记录表
CREATE TABLE IF NOT EXISTS alert_record (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    device_id VARCHAR(64) NOT NULL,
    alert_type TINYINT NOT NULL COMMENT '1-烟雾告警 2-离线告警',
    concentration DECIMAL(12,2) COMMENT '触发时的浓度(烟雾告警时，保留两位小数)',
    threshold INT COMMENT '触发阈值',
    status TINYINT DEFAULT 0 COMMENT '0-未处理 1-已确认 2-已处理',
    false_alarm TINYINT NOT NULL DEFAULT 0 COMMENT '0-非误报 1-误报',
    confirmed_by VARCHAR(64) COMMENT '确认人',
    confirmed_at DATETIME COMMENT '确认时间',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    active_marker TINYINT GENERATED ALWAYS AS (
        CASE WHEN status IN (0, 1) THEN 1 ELSE NULL END
    ) STORED,
    INDEX idx_device_time (device_id, created_at),
    INDEX idx_alert_active (device_id, alert_type, status, created_at),
    UNIQUE INDEX uk_alert_active (device_id, alert_type, active_marker),
    CONSTRAINT fk_alert_device FOREIGN KEY (device_id) REFERENCES device(device_id),
    CONSTRAINT chk_alert_type CHECK (alert_type IN (1, 2)),
    CONSTRAINT chk_alert_status CHECK (status IN (0, 1, 2))
);

-- 告警复核记录
CREATE TABLE IF NOT EXISTS alert_review (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    alert_id BIGINT NOT NULL,
    review_type VARCHAR(32) NOT NULL,
    review_result VARCHAR(500) NOT NULL,
    operator_name VARCHAR(64) NOT NULL,
    created_at DATETIME NOT NULL,
    INDEX idx_alert_review_alert_time (alert_id, created_at),
    CONSTRAINT fk_alert_review_alert FOREIGN KEY (alert_id) REFERENCES alert_record(id)
);

-- 告警通知记录（当前 APP/短信通道为系统模拟）
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
);

-- 用户表
CREATE TABLE IF NOT EXISTS user_account (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    username VARCHAR(64) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    display_name VARCHAR(64) NOT NULL,
    role_code VARCHAR(32) NOT NULL COMMENT 'RESIDENT/COMMUNITY_ADMIN/SYSTEM_ADMIN/FIREFIGHTER',
    enabled TINYINT NOT NULL DEFAULT 1,
    phone VARCHAR(20),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_user_enabled CHECK (enabled IN (0, 1)),
    CONSTRAINT chk_user_role CHECK (
        role_code IN ('RESIDENT', 'COMMUNITY_ADMIN', 'SYSTEM_ADMIN', 'FIREFIGHTER')
    )
);

-- 广播记录表
CREATE TABLE IF NOT EXISTS broadcast_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    device_id VARCHAR(64) NOT NULL,
    content TEXT COMMENT '广播内容',
    trigger_alert_id BIGINT COMMENT '触发的告警ID',
    status TINYINT DEFAULT 0 COMMENT '0-下发中 1-成功 2-失败',
    executed_at DATETIME,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_broadcast_device_time (device_id, created_at),
    INDEX idx_broadcast_status_time (status, created_at),
    CONSTRAINT fk_broadcast_device FOREIGN KEY (device_id) REFERENCES device(device_id),
    CONSTRAINT fk_broadcast_alert FOREIGN KEY (trigger_alert_id) REFERENCES alert_record(id),
    CONSTRAINT chk_broadcast_status CHECK (status IN (0, 1, 2))
);
