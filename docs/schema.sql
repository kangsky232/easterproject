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
    audit_status VARCHAR(16) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING-待核查 COMPLETED-已核查',
    audit_result VARCHAR(24) COMMENT 'NORMAL-核查正常 FOLLOWED_UP-已跟进处理',
    auditor_username VARCHAR(64) COMMENT '核查账号',
    audit_remark VARCHAR(500) COMMENT '核查结论',
    audited_at DATETIME COMMENT '核查完成时间',
    created_at DATETIME NOT NULL,
    INDEX idx_notification_time (created_at),
    INDEX idx_notification_alert (alert_id),
    INDEX idx_notification_audit (audit_status, status, created_at),
    CONSTRAINT chk_notification_audit_status CHECK (audit_status IN ('PENDING', 'COMPLETED')),
    CONSTRAINT chk_notification_audit_result CHECK (
        audit_result IS NULL OR audit_result IN ('NORMAL', 'FOLLOWED_UP')
    ),
    CONSTRAINT fk_notification_alert FOREIGN KEY (alert_id) REFERENCES alert_record(id)
);

-- AI 视觉巡检事件；模型只负责发现疑似风险，最终结论必须由工作人员复核
CREATE TABLE IF NOT EXISTS vision_event (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    event_no VARCHAR(32) NOT NULL COMMENT '视觉事件编号',
    camera_code VARCHAR(64) NOT NULL,
    location VARCHAR(200) NOT NULL,
    building_code VARCHAR(32) NOT NULL,
    floor_no INT NOT NULL,
    frame_key VARCHAR(64) NOT NULL COMMENT '前端模拟图片键',
    image_url VARCHAR(500) NOT NULL COMMENT '提交给视觉模型的公开图片 URL',
    detection_mode VARCHAR(32) NOT NULL COMMENT 'DEEPSEEK_VISION/SIMULATION_FALLBACK',
    model_name VARCHAR(100) NOT NULL,
    risk_level VARCHAR(16) NOT NULL,
    confidence DECIMAL(6,4) NOT NULL,
    summary VARCHAR(500) NOT NULL,
    evidence VARCHAR(1000) NOT NULL,
    status VARCHAR(24) NOT NULL COMMENT 'PENDING_REVIEW/CONFIRMED_FIRE/FALSE_ALARM',
    dingtalk_status VARCHAR(16) NOT NULL COMMENT 'PENDING/SENT/FAILED/SKIPPED',
    dingtalk_recipients INT,
    dingtalk_error VARCHAR(500),
    reviewer_username VARCHAR(64),
    review_remark VARCHAR(500),
    reviewed_at DATETIME,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    active_marker TINYINT GENERATED ALWAYS AS (
        CASE WHEN status = 'PENDING_REVIEW' THEN 1 ELSE NULL END
    ) STORED,
    UNIQUE INDEX uk_vision_event_no (event_no),
    UNIQUE INDEX uk_vision_camera_active (camera_code, active_marker),
    INDEX idx_vision_status_time (status, created_at),
    INDEX idx_vision_location_time (building_code, floor_no, created_at),
    CONSTRAINT chk_vision_floor CHECK (floor_no > 0),
    CONSTRAINT chk_vision_confidence CHECK (confidence BETWEEN 0 AND 1),
    CONSTRAINT chk_vision_status CHECK (
        status IN ('PENDING_REVIEW', 'CONFIRMED_FIRE', 'FALSE_ALARM')
    ),
    CONSTRAINT chk_vision_mode CHECK (
        detection_mode IN ('DEEPSEEK_VISION', 'SIMULATION_FALLBACK')
    ),
    CONSTRAINT chk_vision_risk CHECK (risk_level IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    CONSTRAINT chk_vision_dingtalk CHECK (
        dingtalk_status IN ('PENDING', 'SENT', 'FAILED', 'SKIPPED')
    )
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

-- 安全隐患工单；居民只读取本人上报，工作人员读取全部
CREATE TABLE IF NOT EXISTS hazard_ticket (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    ticket_no VARCHAR(32) NOT NULL COMMENT '隐患工单编号',
    title VARCHAR(100) NOT NULL COMMENT '隐患标题',
    description VARCHAR(1000) NOT NULL COMMENT '隐患情况说明',
    location VARCHAR(200) NOT NULL COMMENT '发生位置',
    priority VARCHAR(16) NOT NULL COMMENT 'LOW/MEDIUM/HIGH/URGENT',
    status VARCHAR(24) NOT NULL COMMENT 'REPORTED/PROCESSING/PENDING_REVIEW/CLOSED',
    reporter_username VARCHAR(64) NOT NULL COMMENT '上报账号',
    assignee_username VARCHAR(64) COMMENT '整改接单账号',
    resolution VARCHAR(1000) COMMENT '最近一次提交的整改结果',
    reviewer_username VARCHAR(64) COMMENT '最近复核账号',
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    closed_at DATETIME,
    UNIQUE INDEX uk_hazard_ticket_no (ticket_no),
    INDEX idx_hazard_status_time (status, updated_at),
    INDEX idx_hazard_reporter_time (reporter_username, created_at),
    INDEX idx_hazard_assignee_status (assignee_username, status),
    CONSTRAINT chk_hazard_priority CHECK (priority IN ('LOW', 'MEDIUM', 'HIGH', 'URGENT')),
    CONSTRAINT chk_hazard_status CHECK (status IN ('REPORTED', 'PROCESSING', 'PENDING_REVIEW', 'CLOSED'))
);

-- 隐患流转记录；保存上报、接单、提交、通过与驳回全过程
CREATE TABLE IF NOT EXISTS hazard_action (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    ticket_id BIGINT NOT NULL,
    action_type VARCHAR(24) NOT NULL COMMENT 'REPORTED/CLAIMED/SUBMITTED/APPROVED/REJECTED',
    operator_name VARCHAR(64) NOT NULL,
    remark VARCHAR(1000) NOT NULL,
    created_at DATETIME NOT NULL,
    INDEX idx_hazard_action_ticket_time (ticket_id, created_at),
    CONSTRAINT fk_hazard_action_ticket FOREIGN KEY (ticket_id)
        REFERENCES hazard_ticket(id) ON DELETE CASCADE,
    CONSTRAINT chk_hazard_action_type CHECK (
        action_type IN ('REPORTED', 'CLAIMED', 'SUBMITTED', 'APPROVED', 'REJECTED')
    )
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

INSERT INTO map_building
    (building_code, building_name, position_x, position_z, width, depth, floors)
VALUES
    ('A1', '1号住宅楼', 16, 18, 18, 14, 6),
    ('A2', '2号住宅楼', 47, 12, 22, 16, 8),
    ('A3', '3号住宅楼', 75, 28, 17, 13, 5)
ON DUPLICATE KEY UPDATE building_name = VALUES(building_name);

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
