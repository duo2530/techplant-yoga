package com.techplant.yoga.course.dto;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

/**
 * 修改课程的请求体（详细设计 §2.3.7）。
 *
 * <p>字段与新增一致（PUT 全量提交），<b>同样不含 status</b>。</p>
 *
 * <p><b>清空语义：</b>{@code coverUrl / intro / durationMin} 传 {@code null} 表示<b>清空</b>，
 * 不是「不更新」—— 见 §4.1.3.3 的实现说明。</p>
 */
@ApiModel("修改课程请求")
public class CourseUpdateDTO
{
    @ApiModelProperty(value = "所属门店ID", required = true, example = "1856739201475235901")
    @NotNull(message = "所属门店不能为空")
    @Min(value = 1, message = "所属门店ID必须大于 0")
    private Long storeId;

    @ApiModelProperty(value = "课程名称", required = true, example = "哈他瑜伽（初级）")
    @NotBlank(message = "课程名称不能为空")
    @Size(max = 64, message = "课程名称长度不能超过 64")
    private String name;

    @ApiModelProperty(value = "课程类型：1团课 2精品课 3私教课 4特色课", required = true, example = "1")
    @NotNull(message = "课程类型不能为空")
    @Min(value = 1, message = "课程类型取值为 1~4")
    @Max(value = 4, message = "课程类型取值为 1~4")
    private Integer type;

    @ApiModelProperty(value = "课程难度：1~5 星", required = true, example = "2")
    @NotNull(message = "课程难度不能为空")
    @Min(value = 1, message = "课程难度取值为 1~5")
    @Max(value = 5, message = "课程难度取值为 1~5")
    private Integer difficulty;

    @ApiModelProperty(value = "课程封面图URL；传 null 表示清空")
    @Size(max = 255, message = "课程封面图URL长度不能超过 255")
    private String coverUrl;

    @ApiModelProperty(value = "课程介绍；传 null 表示清空")
    private String intro;

    @ApiModelProperty(value = "单节时长（分钟）；传 null 表示清空", example = "75")
    @Min(value = 1, message = "单节时长不能小于 1 分钟")
    private Integer durationMin;

    @ApiModelProperty(value = "展示排序", example = "10")
    @Min(value = 0, message = "展示排序不能小于 0")
    private Integer sortNo;

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
}
