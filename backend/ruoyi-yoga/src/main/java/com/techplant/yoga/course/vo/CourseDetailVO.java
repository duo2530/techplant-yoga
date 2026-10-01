package com.techplant.yoga.course.vo;

import java.time.LocalDateTime;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

/**
 * 课程详情（课程管理详细设计 §2.3.1、§2.2.2）：在列表项字段之上多一个 {@code intro}。
 *
 * <p>同样<b>成对返回</b> {@code courseType} ＋ {@code courseTypeName}；不返回审计人字段。</p>
 */
@ApiModel("课程详情")
public class CourseDetailVO
{
    @ApiModelProperty(value = "课程ID（雪花ID，字符串）", example = "1856739201475235801")
    private Long id;

    @ApiModelProperty(value = "课程名称", example = "哈他瑜伽")
    private String name;

    @ApiModelProperty(value = "课种：1团课 2精品课 3私教课 4特色课", example = "1")
    private Integer courseType;

    @ApiModelProperty(value = "课种名称（由 CourseTypeEnum 翻译）", example = "团课")
    private String courseTypeName;

    @ApiModelProperty(value = "课程封面URL（单图，可空）")
    private String coverUrl;

    @ApiModelProperty(value = "课程难度：1~5 星", example = "2")
    private Integer difficulty;

    @ApiModelProperty(value = "课程介绍")
    private String intro;

    @ApiModelProperty(value = "创建时间", example = "2026-09-20 10:12:33")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    @ApiModelProperty(value = "更新时间", example = "2026-09-21 09:03:11")
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

    public String getName()
    {
        return name;
    }

    public void setName(String name)
    {
        this.name = name;
    }

    public Integer getCourseType()
    {
        return courseType;
    }

    public void setCourseType(Integer courseType)
    {
        this.courseType = courseType;
    }

    public String getCourseTypeName()
    {
        return courseTypeName;
    }

    public void setCourseTypeName(String courseTypeName)
    {
        this.courseTypeName = courseTypeName;
    }

    public String getCoverUrl()
    {
        return coverUrl;
    }

    public void setCoverUrl(String coverUrl)
    {
        this.coverUrl = coverUrl;
    }

    public Integer getDifficulty()
    {
        return difficulty;
    }

    public void setDifficulty(Integer difficulty)
    {
        this.difficulty = difficulty;
    }

    public String getIntro()
    {
        return intro;
    }

    public void setIntro(String intro)
    {
        this.intro = intro;
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
