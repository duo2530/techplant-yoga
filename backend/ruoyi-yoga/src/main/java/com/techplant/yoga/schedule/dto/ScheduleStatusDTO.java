package com.techplant.yoga.schedule.dto;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

/** 修改排班状态请求。 */
public class ScheduleStatusDTO
{
    @NotNull(message = "排班状态不能为空")
    @Min(value = 1, message = "排班状态取值为 1~3")
    @Max(value = 3, message = "排班状态取值为 1~3")
    private Integer status;
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
}
