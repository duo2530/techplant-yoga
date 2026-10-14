package com.techplant.yoga.membercard.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

/** 停卡请求。 */
@ApiModel("停卡请求")
public class MemberCardStopDTO
{
    @ApiModelProperty(value = "停卡原因", required = true, example = "客户申请停卡")
    @NotBlank(message = "停卡原因不能为空")
    @Size(max = 255, message = "停卡原因长度不能超过 255")
    private String stopReason;

    public String getStopReason() { return stopReason; }
    public void setStopReason(String stopReason) { this.stopReason = stopReason; }
}
