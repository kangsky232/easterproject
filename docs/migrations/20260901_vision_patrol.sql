USE smart_smoke;

CREATE TABLE IF NOT EXISTS vision_event (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    event_no VARCHAR(32) NOT NULL,
    camera_code VARCHAR(64) NOT NULL,
    location VARCHAR(200) NOT NULL,
    building_code VARCHAR(32) NOT NULL,
    floor_no INT NOT NULL,
    frame_key VARCHAR(64) NOT NULL,
    image_url VARCHAR(500) NOT NULL,
    detection_mode VARCHAR(32) NOT NULL,
    model_name VARCHAR(100) NOT NULL,
    risk_level VARCHAR(16) NOT NULL,
    confidence DECIMAL(6,4) NOT NULL,
    summary VARCHAR(500) NOT NULL,
    evidence VARCHAR(1000) NOT NULL,
    status VARCHAR(24) NOT NULL,
    dingtalk_status VARCHAR(16) NOT NULL,
    dingtalk_recipients INT NULL,
    dingtalk_error VARCHAR(500) NULL,
    reviewer_username VARCHAR(64) NULL,
    review_remark VARCHAR(500) NULL,
    reviewed_at DATETIME NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    active_marker TINYINT GENERATED ALWAYS AS (
        CASE WHEN status = 'PENDING_REVIEW' THEN 1 ELSE NULL END
    ) STORED,
    UNIQUE KEY uk_vision_event_no (event_no),
    UNIQUE KEY uk_vision_camera_active (camera_code, active_marker),
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
