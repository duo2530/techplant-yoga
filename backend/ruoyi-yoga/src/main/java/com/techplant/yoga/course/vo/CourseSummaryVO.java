package com.techplant.yoga.course.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

/**
 * 课程摘要（课程管理详细设计 §3.2「对外提供批量取课程摘要」）。
 *
 * <p>供排课模块做名称补齐与用户端详情页的<b>实时字段</b>读取
 * （{@code courseCoverUrl / courseIntro / difficulty} 取课程当前值，课种取排课快照，见排课详细设计 §2.2.2）。</p>
 *
 * <p>字段：{@code id, name, courseType, courseTypeName, coverUrl, intro, difficulty}
 * —— 其中 {@code courseTypeName} 已由 {@code CourseTypeEnum} 翻好，调用方不要再自己映射。</p>
 */
@ApiModel("课程摘要")
public class CourseSummaryVO
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

    @ApiModelProperty(value = "课程介绍")
    private String intro;

    @ApiModelProperty(value = "课程难度：1~5 星", example = "2")
    private Integer difficulty;

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

    public String getIntro()
    {
        return intro;
    }

    public void setIntro(String intro)
    {
        this.intro = intro;
    }

    public Integer getDifficulty()
    {
        return difficulty;
    }

    public void setDifficulty(Integer difficulty)
    {
        this.difficulty = difficulty;
    }
}
