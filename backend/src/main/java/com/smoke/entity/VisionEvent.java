package com.smoke.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("vision_event")
public class VisionEvent {

    public static final String STATUS_PENDING_REVIEW = "PENDING_REVIEW";
    public static final String STATUS_CONFIRMED_FIRE = "CONFIRMED_FIRE";
    public static final String STATUS_FALSE_ALARM = "FALSE_ALARM";

    public static final String MODE_DEEPSEEK = "DEEPSEEK_VISION";
    public static final String MODE_SIMULATION = "SIMULATION_FALLBACK";

    public static final String NOTICE_PENDING = "PENDING";
    public static final String NOTICE_SENT = "SENT";
    public static final String NOTICE_FAILED = "FAILED";
    public static final String NOTICE_SKIPPED = "SKIPPED";

    @TableId(type = IdType.AUTO)
    private Long id;
    private String eventNo;
    private String cameraCode;
    private String location;
    private String buildingCode;
    private Integer floorNo;
    private String frameKey;
    private String imageUrl;
    private String detectionMode;
    private String modelName;
    private String riskLevel;
    private BigDecimal confidence;
    private String summary;
    private String evidence;
    private String status;
    private String dingtalkStatus;
    private Integer dingtalkRecipients;
    private String dingtalkError;
    private String reviewerUsername;
    private String reviewRemark;
    private LocalDateTime reviewedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
