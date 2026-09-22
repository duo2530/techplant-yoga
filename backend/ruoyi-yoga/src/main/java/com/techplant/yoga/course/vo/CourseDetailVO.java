package com.techplant.yoga.course.vo;

import java.time.LocalDateTime;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

/**
 * 课程详情（详细设计 §2.3.4），用于详情查看与编辑回显。
 *
 * <p>{@code create_by} / {@code update_by} **不返回**给前端 —— 它们只用于数据留痕（§2.3.4 说明）。</p>
 */
@ApiModel("课程详情")
public class CourseDetailVO
{
    @ApiModelProperty(value = "课程ID（雪花ID，字符串）", example = "1856739201475235840")
    private Long id;

    @ApiModelProperty(value = "课程名称", example = "哈他瑜伽")
    private String name;

    @ApiModelProperty(value = "课程类型：1团课 2精品课 3私教课 4特色课", example = "1")
    private Integer type;

    @ApiModelProperty(value = "课程难度：1~5 星", example = "2")
    private Integer difficulty;

    @ApiModelProperty(value = "课程封面图URL")
    private String coverUrl;

    @ApiModelProperty(value = "课程介绍")
    private String intro;

    @ApiModelProperty(value = "单节时长（分钟）", example = "60")
    private Integer durationMin;

    @ApiModelProperty(value = "展示排序，值越小越靠前", example = "10")
    private Integer sortNo;

    @ApiModelProperty(value = "课程状态：1启用 0停用", example = "1")
    private Integer status;

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

    public Integer getType()
    {
        return type;
    }

    public void setType(Integer type)
    {
        this.type = type;
    }

    public Integer getDifficulty()
    {
        return difficulty;
    }

    public void setDifficulty(Integer difficulty)
    {
        this.difficulty = difficulty;
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

    public Integer getDurationMin()
    {
        return durationMin;
    }

    public void setDurationMin(Integer durationMin)
    {
        this.durationMin = durationMin;
    }

    public Integer getSortNo()
    {
        return sortNo;
    }

    public void setSortNo(Integer sortNo)
    {
        this.sortNo = sortNo;
    }

    public Integer getStatus()
    {
        return status;
    }

    public void setStatus(Integer status)
    {
        this.status = status;
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
