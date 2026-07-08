package com.tripmate.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

/**
 * 用户实体类，映射 users 表。
 *
 * <p>使用 BCrypt 加密密码存储，角色分为 USER（普通用户）和 ADMIN（管理员）。
 * 创建时间和更新时间由 {@link MyMetaObjectHandler} 自动填充。</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("users")
public class User {

    /** 用户唯一 ID，数据库自增 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户名，全局唯一 */
    private String username;

    /** BCrypt 加密后的密码，永不明文存储 */
    private String password;

    /** 邮箱，全局唯一 */
    private String email;

    /** 角色：USER（普通用户）或 ADMIN（管理员），默认 USER */
    private String role;

    /** 账号状态：1=正常，0=禁用。禁用后无法登录 */
    private Integer status;

    /** 注册时间，插入时自动填充 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /** 最近修改时间，插入和更新时自动填充 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
