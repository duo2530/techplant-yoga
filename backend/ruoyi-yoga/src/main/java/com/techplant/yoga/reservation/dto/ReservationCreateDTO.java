package com.techplant.yoga.reservation.dto;

import lombok.Getter;
import lombok.Setter;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

/** 预约课程请求。 */
@ApiModel("预约课程请求")
@Getter
@Setter
public class ReservationCreateDTO {
    @ApiModelProperty(value = "请求幂等标识（客户端 UUID，同一次提交与网络重试沿用）", required = true)
    @NotBlank(message = "请求幂等标识不能为空")
    @Size(max = 36, message = "请求幂等标识长度不能超过 36")
    private String requestId;

    @ApiModelProperty(value = "排课ID", required = true, example = "1856739201475239001")
    @NotNull(message = "排课ID不能为空")
    private Long scheduleId;

    @ApiModelProperty(value = "会员卡ID", required = true, example = "1856739201475235912")
    @NotNull(message = "会员卡ID不能为空")
    private Long memberCardId;

    @ApiModelProperty(value = "预约人数（年卡固定为 1）", required = true, example = "1")
    @NotNull(message = "预约人数不能为空")
    @Min(value = 1, message = "预约人数必须大于 0")
    private Integer persons;
}
