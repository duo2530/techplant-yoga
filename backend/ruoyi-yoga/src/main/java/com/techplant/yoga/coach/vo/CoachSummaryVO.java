package com.techplant.yoga.coach.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

/**
 * 教练摘要（跨模块出参）。
 *
 * <p>供排课模块按 ID <b>批量补齐</b>教练姓名与头像、校验教练是否存在使用（详细设计总览 §6）。
 * 只包含排课需要的四个字段，<b>不带</b>审计时间与相册。</p>
 */
@ApiModel("教练摘要")
public class CoachSummaryVO
{
    @ApiModelProperty(value = "教练ID（雪花ID，字符串）", example = "1856739201475237001")
    private Long id;

    @ApiModelProperty(value = "姓名", example = "林教练")
    private String name;

    @ApiModelProperty(value = "头像URL")
    private String avatarUrl;

    @ApiModelProperty(value = "简介")
    private String intro;

    public CoachSummaryVO() { }

    public CoachSummaryVO(Long id, String name, String avatarUrl, String intro)
    {
        this.id = id;
        this.name = name;
        this.avatarUrl = avatarUrl;
        this.intro = intro;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getAvatarUrl() { return avatarUrl; }
    public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }
    public String getIntro() { return intro; }
    public void setIntro(String intro) { this.intro = intro; }
}
