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
    smoke_threshold INT DEFAULT 100 COMMENT '烟雾预警阈值(ppm)',
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
    alert_type TINYINT NOT NULL COMMENT '1-烟雾 2-离线 3-温度 4-湿度 5-电流 6-线缆温度 7-一氧化碳',
    concentration DECIMAL(12,2) COMMENT '触发时的指标值（兼容字段名）',
    threshold INT COMMENT '触发阈值',
    severity VARCHAR(16) COMMENT 'WARNING-预警 DANGER-危险',
    rule_description VARCHAR(255) COMMENT '触发规则说明',
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
    CONSTRAINT chk_alert_type CHECK (alert_type IN (1, 2, 3, 4, 5, 6, 7)),
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

-- 告警通知记录（APP/短信为系统记录，钉钉配置后真实投递）
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

-- 钉钉机器人单聊接收人；员工首次私聊机器人时自动绑定
CREATE TABLE IF NOT EXISTS dingtalk_recipient (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id VARCHAR(128) NOT NULL,
    display_name VARCHAR(100),
    enabled TINYINT NOT NULL DEFAULT 1,
    first_seen_at DATETIME NOT NULL,
    last_seen_at DATETIME NOT NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    UNIQUE INDEX uk_dingtalk_recipient_user (user_id),
    INDEX idx_dingtalk_recipient_enabled (enabled),
    CONSTRAINT chk_dingtalk_recipient_enabled CHECK (enabled IN (0, 1))
);

-- 模拟 3D 地图楼栋
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
    UNIQUE INDEX uk_map_building_code (building_code),
    CONSTRAINT chk_map_building_floors CHECK (floors > 0),
    CONSTRAINT chk_map_building_enabled CHECK (enabled IN (0, 1))
);

INSERT IGNORE INTO map_building
    (building_code, building_name, position_x, position_z, width, depth, floors)
VALUES
    ('A1', '1号住宅楼', 16, 18, 18, 14, 6),
    ('A2', '2号住宅楼', 47, 12, 22, 16, 8),
    ('A3', '3号住宅楼', 75, 28, 17, 13, 5);

-- 设备在模拟楼栋中的楼层、房间和局部坐标
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
);
