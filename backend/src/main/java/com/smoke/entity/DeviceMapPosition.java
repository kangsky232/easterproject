package com.smoke.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("device_map_position")
public class DeviceMapPosition {

    @TableId(value = "device_id", type = IdType.INPUT)
    private String deviceId;
    private String buildingCode;
    private Integer floorNo;
    private String roomLabel;
    private BigDecimal positionX;
    private BigDecimal positionZ;
    private LocalDateTime updatedAt;
}
