package com.techplant.yoga.coach.dto;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

/** 设置教练状态请求（详细设计 §2.2.5）。 */
@ApiModel("设置教练状态请求")
public class CoachStatusDTO
{
    @ApiModelProperty(value = "目标状态：1 启用、0 停用", required = true, example = "1")
    @NotNull(message = "教练状态不能为空")
    @Min(value = 0, message = "教练状态取值为 0（停用）或 1（启用）")
    @Max(value = 1, message = "教练状态取值为 0（停用）或 1（启用）")
    private Integer status;

    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
}
