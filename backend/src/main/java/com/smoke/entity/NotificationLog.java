package com.smoke.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("notification_log")
public class NotificationLog {

    public static final String CHANNEL_APP = "APP";
    public static final String CHANNEL_SMS = "SMS";
    public static final String CHANNEL_DINGTALK = "DINGTALK";
    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_SENT = "SENT";
    public static final String STATUS_FAILED = "FAILED";

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long alertId;
    private String deviceId;
    private String channel;
    private String receiver;
    private String content;
    private String status;
    private LocalDateTime sentAt;
    private LocalDateTime createdAt;
}
