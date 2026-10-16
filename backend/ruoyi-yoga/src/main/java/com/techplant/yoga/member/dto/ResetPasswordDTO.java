package com.techplant.yoga.member.dto;

import lombok.Getter;
import lombok.Setter;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

/** 忘记密码请求。 */
@ApiModel("忘记密码请求")
@Getter
@Setter
public class ResetPasswordDTO {
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
}
