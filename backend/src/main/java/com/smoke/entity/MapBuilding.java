package com.smoke.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("map_building")
public class MapBuilding {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String buildingCode;
    private String buildingName;
    private BigDecimal positionX;
    private BigDecimal positionZ;
    private BigDecimal width;
    private BigDecimal depth;
    private Integer floors;
    private Integer enabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
