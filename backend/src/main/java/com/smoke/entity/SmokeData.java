package com.smoke.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("smoke_data")
public class SmokeData {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String deviceId;
    private String messageId;
    private BigDecimal concentration;
    private LocalDateTime timestamp;
    private LocalDateTime createdAt;
}
