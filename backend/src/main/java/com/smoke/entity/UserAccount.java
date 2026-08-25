package com.smoke.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import lombok.ToString;

import java.time.LocalDateTime;

@Data
@TableName("user_account")
public class UserAccount {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String username;
    @JsonIgnore
    @ToString.Exclude
    private String passwordHash;
    private String displayName;
    private String roleCode;
    private Integer enabled;
    private String phone;
    private LocalDateTime createdAt;
}
