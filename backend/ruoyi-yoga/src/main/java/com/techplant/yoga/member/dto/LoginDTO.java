package com.techplant.yoga.member.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

/**
 * 登录请求。
 *
 * <p>免登录：手机号 ＋ 密码。密码只校验非空，<b>不在登录时重复执行设置密码的强度规则</b>
 * （老数据／被改过的哈希不该因为规则升级而登录不了）。</p>
 */
@ApiModel("登录请求")
public class LoginDTO
{
    @ApiModelProperty(value = "手机号", required = true, example = "13800138000")
    @NotBlank(message = "手机号不能为空")
    @Size(max = 32, message = "手机号长度不能超过 32")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;

    @ApiModelProperty(value = "登录密码（非空）", required = true, example = "yoga1234")
    @NotBlank(message = "密码不能为空")
    private String password;

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
}
