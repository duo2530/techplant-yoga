package com.techplant.yoga.member.dto;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;
import com.techplant.yoga.member.enums.MemberGenderEnum;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

/**
 * 新增会员的请求体。
 *
 * <p>只建立会员档案：不含密码、微信标识等登录凭证，也不含显示名（由服务端按手机号后 4 位生成）。
 * 会员等级取值由字典 {@code member_level} 校验，缺省取 0。</p>
 */
@ApiModel("新增会员请求")
public class MemberCreateDTO
{
    @ApiModelProperty(value = "手机号", required = true, example = "13800138000")
    @NotBlank(message = "手机号不能为空")
    @Size(max = 32, message = "手机号长度不能超过 32")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;

    @ApiModelProperty(value = "性别：0 未知 1 男 2 女；缺省为 0", example = "0")
    @Min(value = MemberGenderEnum.MIN_CODE, message = MemberGenderEnum.INVALID_MESSAGE)
    @Max(value = MemberGenderEnum.MAX_CODE, message = MemberGenderEnum.INVALID_MESSAGE)
    private Integer gender;

    @ApiModelProperty(value = "会员等级，取值见字典 member_level；缺省为 0", example = "0")
    private Integer level;

    public String getPhone()
    {
        return phone;
    }

    public void setPhone(String phone)
    {
        this.phone = phone;
    }

    public Integer getGender()
    {
        return gender;
    }

    public void setGender(Integer gender)
    {
        this.gender = gender;
    }

    public Integer getLevel()
    {
        return level;
    }

    public void setLevel(Integer level)
    {
        this.level = level;
    }
}
