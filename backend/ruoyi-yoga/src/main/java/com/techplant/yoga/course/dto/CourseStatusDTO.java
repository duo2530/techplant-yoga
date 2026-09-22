package com.techplant.yoga.course.dto;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

/**
 * 设置课程状态的请求体（详细设计 §2.3.8）。
 */
@ApiModel("设置课程状态请求")
public class CourseStatusDTO
{
    @ApiModelProperty(value = "目标状态：1 启用、0 停用", required = true, example = "0")
    @NotNull(message = "课程状态不能为空")
    @Min(value = 0, message = "课程状态取值为 0（停用）或 1（启用）")
    @Max(value = 1, message = "课程状态取值为 0（停用）或 1（启用）")
    private Integer status;

    public Integer getStatus()
    {
        return status;
    }

    public void setStatus(Integer status)
    {
        this.status = status;
    }
}
