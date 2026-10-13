package com.techplant.yoga.member.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

/**
 * 发送短信验证码请求（用户端接口详细设计 §2.2.4、§2.6.4）。
 *
 * <p>免登录；发送间隔 60 秒、有效期 5 分钟；发送新码作废旧码，响应不返回验证码。</p>
 */
@ApiModel("发送短信验证码请求")
public class SmsCodeSendDTO
{
    @ApiModelProperty(value = "手机号", required = true, example = "13800138000")
    @NotBlank(message = "手机号不能为空")
    @Size(max = 32, message = "手机号长度不能超过 32")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;

    @ApiModelProperty(value = "验证码场景：register 注册／reset 忘记密码", required = true, example = "register")
    @NotBlank(message = "验证码场景不能为空")
    @Pattern(regexp = "register|reset", message = "验证码场景取值为 register 或 reset")
    private String scene;

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getScene() { return scene; }
    public void setScene(String scene) { this.scene = scene; }
}
