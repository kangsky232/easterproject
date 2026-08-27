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
    public static final int TYPE_TEMPERATURE = 3;
    public static final int TYPE_HUMIDITY = 4;
    public static final int TYPE_CURRENT = 5;
    public static final int TYPE_WIRE_TEMPERATURE = 6;
    public static final int TYPE_CO = 7;
    public static final String SEVERITY_WARNING = "WARNING";
    public static final String SEVERITY_DANGER = "DANGER";
    public static final int STATUS_PENDING = 0;
    public static final int STATUS_CONFIRMED = 1;
    public static final int STATUS_RESOLVED = 2;

    @TableId(type = IdType.AUTO)
    private Long id;
    private String deviceId;
    private Integer alertType;
    private BigDecimal concentration;
    private Integer threshold;
    private String severity;
    private String ruleDescription;
    private Integer status;
    private Integer falseAlarm;
    private String confirmedBy;
    private LocalDateTime confirmedAt;
    private LocalDateTime createdAt;

    public static boolean isSensorAlert(int type) {
        return type != TYPE_OFFLINE;
    }

    public static String typeLabel(int type) {
        return switch (type) {
            case TYPE_SMOKE -> "烟雾浓度";
            case TYPE_OFFLINE -> "设备离线";
            case TYPE_TEMPERATURE -> "环境温度";
            case TYPE_HUMIDITY -> "环境湿度";
            case TYPE_CURRENT -> "电气电流";
            case TYPE_WIRE_TEMPERATURE -> "线缆温度";
            case TYPE_CO -> "一氧化碳浓度";
            default -> "未知指标";
        };
    }

    public static String unit(int type) {
        return switch (type) {
            case TYPE_SMOKE, TYPE_CO -> "ppm";
            case TYPE_TEMPERATURE, TYPE_WIRE_TEMPERATURE -> "℃";
            case TYPE_HUMIDITY -> "%";
            case TYPE_CURRENT -> "A";
            default -> "";
        };
    }
}
