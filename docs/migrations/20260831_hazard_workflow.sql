-- 2026-08-31：新增安全隐患上报、整改、复核与闭环留痕。
USE smart_smoke;

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
