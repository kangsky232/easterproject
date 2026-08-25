package com.smoke.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.ToString;

import java.time.LocalDateTime;

@Data
@TableName("device")
public class Device {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String deviceId;
    private String deviceName;
    private String location;
    private Integer status;
    private Integer smokeThreshold;
    private Integer battery;
    private LocalDateTime lastHeartbeat;
    private LocalDateTime bindTime;
    private Integer bound;
    private LocalDateTime unbindTime;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @JsonIgnore
    @ToString.Exclude
    private String deviceTokenHash;

    @TableField(exist = false)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @ToString.Exclude
    private String deviceAccessToken;
}
