package com.techplant.yoga.course.dto;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import com.techplant.yoga.course.enums.CourseTypeValid;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

/**
 * 修改课程的请求体（课程管理详细设计 §2.3.2、§2.2.4）：PUT 全量提交，字段与新增一致。
 *
 * <p><b>不做引用检查</b>：已被排课引用时仍允许修改；改课种不影响既有排课（课种已快照在排课上），
 * 改难度会影响既有排课的展示（§2.2.4、BR-课程-009）。</p>
 *
 * <p><b>清空语义：</b>{@code coverUrl / intro} 传 {@code null} 表示<b>清空</b>，不是「不更新」。</p>
 */
@ApiModel("修改课程请求")
public class CourseUpdateDTO
{
    @ApiModelProperty(value = "课程名称", required = true, example = "哈他瑜伽（初级）")
    @NotBlank(message = "课程名称不能为空")
    @Size(max = 64, message = "课程名称长度不能超过 64")
    private String name;

    @ApiModelProperty(value = "课种：1团课 2精品课 3私教课 4特色课", required = true, example = "1")
    @NotNull(message = "课程类型不能为空")
    @CourseTypeValid
    private Integer courseType;

    @ApiModelProperty(value = "课程封面URL（单图）；传 null 表示清空")
    @Size(max = 255, message = "课程封面URL长度不能超过 255")
    private String coverUrl;

    @ApiModelProperty(value = "课程介绍；传 null 表示清空")
    @Size(max = 1024, message = "课程介绍长度不能超过 1024")
    private String intro;

    @ApiModelProperty(value = "课程难度：1~5 星", required = true, example = "2")
    @NotNull(message = "课程难度不能为空")
    @Min(value = 1, message = "课程难度取值为 1~5")
    @Max(value = 5, message = "课程难度取值为 1~5")
    private Integer difficulty;

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
