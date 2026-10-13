package com.techplant.yoga.member.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

/**
 * 忘记密码请求（用户端接口详细设计 §2.2.7、§2.6.5）。
 *
 * <p>免登录：仅已注册手机号可重置，经验证码校验后覆盖 BCrypt 哈希，不需要旧密码；
 * 本阶段不批量撤销已有登录态。</p>
 */
@ApiModel("忘记密码请求")
public class ResetPasswordDTO
{
    @ApiModelProperty(value = "已注册手机号", required = true, example = "13800138000")
    @NotBlank(message = "手机号不能为空")
    @Size(max = 32, message = "手机号长度不能超过 32")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;

    @ApiModelProperty(value = "6 位数字验证码", required = true, example = "888888")
    @NotBlank(message = "验证码不能为空")
    @Pattern(regexp = "^\\d{6}$", message = "验证码为 6 位数字")
    private String smsCode;

    @ApiModelProperty(value = "新密码：8～20 位，同时包含字母与数字", required = true, example = "new12345")
    @NotBlank(message = "密码不能为空")
    @Size(min = 8, max = 20, message = "密码长度需为 8～20 位")
    @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).{8,20}$", message = "密码需同时包含字母与数字")
    private String password;

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getSmsCode() { return smsCode; }
    public void setSmsCode(String smsCode) { this.smsCode = smsCode; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
}
