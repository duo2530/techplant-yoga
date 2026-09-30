package com.techplant.yoga.course.vo;

import java.time.LocalDateTime;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

/**
 * 课程列表项（详细设计 §2.3.3）。
 *
 * <p>列表项**不含** {@code intro}、{@code durationMin} —— 避免列表接口返回长字段（§4.1.3.1）。</p>
 */
@ApiModel("课程列表项")
public class CourseListItemVO
{
    @ApiModelProperty(value = "课程ID（雪花ID，字符串）", example = "1856739201475235840")
    private Long id;

    @ApiModelProperty(value = "所属门店ID", example = "1856739201475235901")
    private Long storeId;

    @ApiModelProperty(value = "课程名称", example = "哈他瑜伽")
    private String name;

    @ApiModelProperty(value = "课程类型：1团课 2精品课 3私教课 4特色课", example = "1")
    private Integer type;

    @ApiModelProperty(value = "课程难度：1~5 星", example = "2")
    private Integer difficulty;

    @ApiModelProperty(value = "课程封面图URL")
    private String coverUrl;

    @ApiModelProperty(value = "课程状态：1启用 0停用", example = "1")
    private Integer status;

    @ApiModelProperty(value = "展示排序，值越小越靠前", example = "10")
    private Integer sortNo;

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

    public Long getStoreId()
    {
        return storeId;
    }

    public void setStoreId(Long storeId)
    {
        this.storeId = storeId;
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

    public Integer getStatus()
    {
        return status;
    }

    public void setStatus(Integer status)
    {
        this.status = status;
    }

    public Integer getSortNo()
    {
        return sortNo;
    }

    public void setSortNo(Integer sortNo)
    {
        this.sortNo = sortNo;
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
