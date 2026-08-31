package com.smoke.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("hazard_action")
public class HazardAction {

    public static final String TYPE_REPORTED = "REPORTED";
    public static final String TYPE_CLAIMED = "CLAIMED";
    public static final String TYPE_SUBMITTED = "SUBMITTED";
    public static final String TYPE_APPROVED = "APPROVED";
    public static final String TYPE_REJECTED = "REJECTED";

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long ticketId;
    private String actionType;
    private String operatorName;
    private String remark;
    private LocalDateTime createdAt;
}
