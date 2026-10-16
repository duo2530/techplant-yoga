package com.techplant.yoga.reservation.query;

import lombok.Getter;
import lombok.Setter;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

/**
 * 我的预约查询条件。
 *
 * <p>会员身份由登录态决定，模型不包含 {@code memberId} 或门店筛选字段；排序由 DAO 固定为
 * {@code reservation_time DESC, id DESC}。</p>
 */
@ApiModel("我的预约查询条件")
@Getter
@Setter
public class ReservationPublicQuery {
    /** 每页条数上限 */
    public static final int MAX_PAGE_SIZE = 100;

    @ApiModelProperty(value = "预约状态：1已预约 2已取消 3已签到；不传为全部", example = "1")
    @Min(value = 1, message = "预约状态取值为 1、2 或 3")
    @Max(value = 3, message = "预约状态取值为 1、2 或 3")
    private Integer status;

    @ApiModelProperty(value = "关联会员卡类型：1次数卡 2年卡；不传为全部", example = "1")
    @Min(value = 1, message = "卡类型取值为 1 或 2")
    @Max(value = 2, message = "卡类型取值为 1 或 2")
    private Integer cardType;

    @ApiModelProperty(value = "页码，从 1 开始", required = true, example = "1")
    @NotNull(message = "页码不能为空")
    @Min(value = 1, message = "页码不能小于 1")
    private Integer pageNum;

    @ApiModelProperty(value = "每页条数，1~100", required = true, example = "10")
    @NotNull(message = "每页条数不能为空")
    @Min(value = 1, message = "每页条数不能小于 1")
    @Max(value = MAX_PAGE_SIZE, message = "每页条数不能超过 100")
    private Integer pageSize;
}
