package com.techplant.yoga.member.vo;

import java.time.LocalDateTime;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

/**
 * 会员详情：字段与列表项一致（会员字段不多，列表不裁剪）。
 *
 * <p>性别与等级「码与名成对返回」；{@code registered} 只表示是否已注册，<b>不回显密码哈希</b>。</p>
 */
@ApiModel("会员详情")
public class MemberDetailVO
{
    @ApiModelProperty(value = "会员ID（雪花ID，字符串）", example = "1856739201475235901")
    private Long id;

    @ApiModelProperty(value = "手机号", example = "13800138000")
    private String phone;

    @ApiModelProperty(value = "会员显示名（系统生成，允许重复）", example = "忻会员8000")
    private String name;

    @ApiModelProperty(value = "性别：0 未知 1 男 2 女", example = "1")
    private Integer gender;

    @ApiModelProperty(value = "性别名称（由 MemberGenderEnum 翻译）", example = "男")
    private String genderText;

    @ApiModelProperty(value = "会员等级码，取值见字典 member_level", example = "0")
    private Integer level;

    @ApiModelProperty(value = "等级名称（按字典 member_level 翻译）", example = "V0 普通会员")
    private String levelText;

    @ApiModelProperty(value = "是否已注册（password 非空）", example = "false")
    private Boolean registered;

    @ApiModelProperty(value = "创建时间", example = "2026-10-03 10:00:00")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    @ApiModelProperty(value = "更新时间", example = "2026-10-03 10:00:00")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;

    public Long getId()
    {
        return id;
    }

    public void setId(Long id)
    {
        this.id = id;
    }

    public String getPhone()
    {
        return phone;
    }

    public void setPhone(String phone)
    {
        this.phone = phone;
    }

    public String getName()
    {
        return name;
    }

    public void setName(String name)
    {
        this.name = name;
    }

    public Integer getGender()
    {
        return gender;
    }

    public void setGender(Integer gender)
    {
        this.gender = gender;
    }

    public String getGenderText()
    {
        return genderText;
    }

    public void setGenderText(String genderText)
    {
        this.genderText = genderText;
    }

    public Integer getLevel()
    {
        return level;
    }

    public void setLevel(Integer level)
    {
        this.level = level;
    }

    public String getLevelText()
    {
        return levelText;
    }

    public void setLevelText(String levelText)
    {
        this.levelText = levelText;
    }

    public Boolean getRegistered()
    {
        return registered;
    }

    public void setRegistered(Boolean registered)
    {
        this.registered = registered;
    }

    public LocalDateTime getCreateTime()
    {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime)
    {
        this.createTime = createTime;
    }

    public LocalDateTime getUpdateTime()
    {
        return updateTime;
    }

    public void setUpdateTime(LocalDateTime updateTime)
    {
        this.updateTime = updateTime;
    }
}
