package com.smoke.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("broadcast_log")
public class BroadcastLog {

    public static final int STATUS_PENDING = 0;
    public static final int STATUS_SUCCESS = 1;
    public static final int STATUS_FAILED = 2;

    @TableId(type = IdType.AUTO)
    private Long id;
    private String deviceId;
    private String content;
    private Long triggerAlertId;
    private Integer status;
    private LocalDateTime executedAt;
    private LocalDateTime createdAt;
}
