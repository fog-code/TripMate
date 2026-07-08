package com.tripmate.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 用户注册请求 DTO。
 *
 * <p>使用 Jakarta Validation 对输入做校验，错误消息为中文。</p>
 */
@Data
public class RegisterRequest {

    /** 用户名，3-50 位且不可重复 */
    @NotBlank(message = "用户名不能为空")
    @Size(min = 3, max = 50, message = "用户名长度需在3-50位之间")
    private String username;

    /** 密码，至少 8 位，存储前通过 BCrypt 加密 */
    @NotBlank(message = "密码不能为空")
    @Size(min = 8, message = "密码长度不能小于8位")
    private String password;

    /** 邮箱，格式校验且不可重复 */
    @NotBlank(message = "邮箱不能为空")
    @Email(message = "邮箱格式不正确")
    private String email;
}
