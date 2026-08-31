package com.smoke.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("hazard_ticket")
public class HazardTicket {

    public static final String STATUS_REPORTED = "REPORTED";
    public static final String STATUS_PROCESSING = "PROCESSING";
    public static final String STATUS_PENDING_REVIEW = "PENDING_REVIEW";
    public static final String STATUS_CLOSED = "CLOSED";

    public static final String PRIORITY_LOW = "LOW";
    public static final String PRIORITY_MEDIUM = "MEDIUM";
    public static final String PRIORITY_HIGH = "HIGH";
    public static final String PRIORITY_URGENT = "URGENT";

    @TableId(type = IdType.AUTO)
    private Long id;
    private String ticketNo;
    private String title;
    private String description;
    private String location;
    private String priority;
    private String status;
    private String reporterUsername;
    private String assigneeUsername;
    private String resolution;
    private String reviewerUsername;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime closedAt;
}
