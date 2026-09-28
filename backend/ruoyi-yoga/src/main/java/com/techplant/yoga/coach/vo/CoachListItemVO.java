package com.techplant.yoga.coach.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

/** 管理端教练列表项（详细设计 §2.3.1）。 */
@ApiModel("教练列表项")
public class CoachListItemVO
{
    @ApiModelProperty(value = "教练ID（雪花ID，字符串）", example = "1856739201475235901")
    private Long id;
    @ApiModelProperty(value = "教练名称", example = "林教练")
    private String name;
    @ApiModelProperty(value = "教练头衔", example = "金牌教练")
    private String title;
    @ApiModelProperty(value = "教练头像URL")
    private String avatarUrl;
    @ApiModelProperty(value = "教练状态：1启用 0停用", example = "1")
    private Integer status;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getAvatarUrl() { return avatarUrl; }
    public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
}
