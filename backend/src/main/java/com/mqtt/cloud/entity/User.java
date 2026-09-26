package com.mqtt.cloud.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 系统用户实体
 * 对应数据库表：sys_user
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("sys_user")
public class User {
    /** 主键ID */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 登录用户名，全局唯一 */
    @TableField("username")
    private String username;

    /** BCrypt加密密码，永不对外序列化 */
    @JsonIgnore
    @TableField("password")
    private String password;

    /** 用户邮箱 */
    @TableField("email")
    private String email;

    /** 手机号 */
    @TableField("phone")
    private String phone;

    /** 用户角色：ADMIN/OPERATOR/VIEWER */
    @TableField("role")
    private String role;

    /** 账号状态：ACTIVE/DISABLED/LOCKED */
    @TableField("status")
    private String status;

    /** 最后登录时间 */
    @TableField("last_login")
    private LocalDateTime lastLogin;

    /** 创建时间（自动填充） */
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /** 更新时间（自动填充） */
    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    /** 逻辑删除标记（0=未删除，1=已删除） */
    @TableField("deleted")
    private Integer deleted;
}
