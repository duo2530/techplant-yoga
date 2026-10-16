package com.techplant.yoga.membercard.dto;

import lombok.Getter;
import lombok.Setter;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

/**
 * 发卡请求（创建待开卡）。
 *
 * <p>{@code storeId}、{@code purchaseTimes}、{@code giftTimes}、{@code validPeriodCount}
 * 只在次数卡上出现：次数卡必填，年卡必须为空，字段组合由 service 校验。</p>
 */
@ApiModel("发卡请求")
@Getter
@Setter
public class MemberCardCreateDTO {
    @ApiModelProperty(value = "所属会员 ID", required = true, example = "1856739201475235902")
    @NotNull(message = "所属会员不能为空")
    private Long memberId;

    @ApiModelProperty(value = "开卡门店 ID（用于生成卡号）", required = true, example = "1856739201475235901")
    @NotNull(message = "开卡门店不能为空")
    private Long openStoreId;

    @ApiModelProperty(value = "卡名称", required = true, example = "普拉提次卡")
    @NotBlank(message = "卡名称不能为空")
    @Size(max = 64, message = "卡名称长度不能超过 64")
    private String cardName;

    @ApiModelProperty(value = "卡类型：1次数卡 2年卡", required = true, example = "1")
    @NotNull(message = "卡类型不能为空")
    @Min(value = 1, message = "卡类型取值为 1 或 2")
    @Max(value = 2, message = "卡类型取值为 1 或 2")
    private Integer cardType;

    @ApiModelProperty(value = "适用课种范围：1团课类 2私教课", required = true, example = "1")
    @NotNull(message = "适用课种范围不能为空")
    @Min(value = 1, message = "适用课种范围取值为 1 或 2")
    @Max(value = 2, message = "适用课种范围取值为 1 或 2")
    private Integer courseScope;

    @ApiModelProperty(value = "适用门店 ID（次数卡必填，年卡必须为空）", example = "1856739201475235901")
    private Long storeId;

    @ApiModelProperty(value = "购买次数（次数卡必填，≥ 0）", example = "10")
    @Min(value = 0, message = "购买次数不能小于 0")
    private Integer purchaseTimes;

    @ApiModelProperty(value = "赠送次数（次数卡必填，≥ 0）", example = "2")
    @Min(value = 0, message = "赠送次数不能小于 0")
    private Integer giftTimes;

    @ApiModelProperty(value = "有效期数量（次数卡必填，仅 3／6／12 个月；年卡不填）", example = "12")
    private Integer validPeriodCount;
}
