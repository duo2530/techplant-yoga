package com.techplant.yoga.coach.dto;

import java.util.List;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

/** 新增教练请求（详细设计 §2.2.3）。 */
@ApiModel("新增教练请求")
public class CoachCreateDTO
{
    @ApiModelProperty(value = "教练简介", example = "专注基础体式与呼吸练习。")
    @Size(max = 5000, message = "教练简介长度不能超过 5000")
    private String intro;

    @ApiModelProperty(value = "教练名称", required = true, example = "林教练")
    @NotBlank(message = "教练名称不能为空")
    @Size(max = 64, message = "教练名称长度不能超过 64")
    private String name;

    @ApiModelProperty(value = "教练头衔", example = "金牌教练")
    @Size(max = 64, message = "教练头衔长度不能超过 64")
    private String title;

    @ApiModelProperty(value = "教练头像URL")
    @Size(max = 255, message = "教练头像URL长度不能超过 255")
    private String avatarUrl;

    @ApiModelProperty(value = "教练相册，最多 5 张")
    @Size(max = 5, message = "教练相册最多只能有 5 张图片")
    private List<String> albumUrls;

    public String getIntro() { return intro; }
    public void setIntro(String intro) { this.intro = intro; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getAvatarUrl() { return avatarUrl; }
    public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }
    public List<String> getAlbumUrls() { return albumUrls; }
    public void setAlbumUrls(List<String> albumUrls) { this.albumUrls = albumUrls; }
}
