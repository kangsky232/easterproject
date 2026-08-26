package com.smoke.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("alert_record")
public class AlertRecord {

    public static final int TYPE_SMOKE = 1;
    public static final int TYPE_OFFLINE = 2;
    public static final int STATUS_PENDING = 0;
    public static final int STATUS_CONFIRMED = 1;
    public static final int STATUS_RESOLVED = 2;

    @TableId(type = IdType.AUTO)
    private Long id;
    private String deviceId;
    private Integer alertType;
    private BigDecimal concentration;
    private Integer threshold;
    private Integer status;
    private Integer falseAlarm;
    private String confirmedBy;
    private LocalDateTime confirmedAt;
    private LocalDateTime createdAt;
}
