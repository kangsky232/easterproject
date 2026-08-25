package com.smoke.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("alert_review")
public class AlertReview {

    public static final String TYPE_CONTEXT_REVIEW = "CONTEXT_REVIEW";

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long alertId;
    private String reviewType;
    private String reviewResult;
    private String operatorName;
    private LocalDateTime createdAt;
}
