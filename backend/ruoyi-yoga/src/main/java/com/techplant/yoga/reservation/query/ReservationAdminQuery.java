package com.techplant.yoga.reservation.query;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import org.springframework.format.annotation.DateTimeFormat;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

/**
 * 管理端预约查询条件。
 *
 * <p>不限制当前门店；排序由 DAO 固定为 {@code reservation_time DESC, id DESC}。</p>
 */
@ApiModel("预约列表查询条件")
@Getter
@Setter
public class ReservationAdminQuery {
    /** 每页条数上限 */
    public static final int MAX_PAGE_SIZE = 100;

    @ApiModelProperty(value = "门店ID（雪花ID，字符串）", example = "1856739201475235901")
    private Long storeId;

    @ApiModelProperty(value = "课程ID（雪花ID，字符串）", example = "1856739201475238001")
    private Long courseId;

    @ApiModelProperty(value = "排课ID（雪花ID，字符串）", example = "1856739201475239001")
    private Long scheduleId;

    @ApiModelProperty(value = "会员ID（雪花ID，字符串）", example = "1856739201475235902")
    private Long memberId;

    @ApiModelProperty(value = "预约状态：1已预约 2已取消 3已签到", example = "1")
    @Min(value = 1, message = "预约状态取值为 1、2 或 3")
    @Max(value = 3, message = "预约状态取值为 1、2 或 3")
    private Integer status;

    @ApiModelProperty(value = "关联会员卡类型：1次数卡 2年卡", example = "1")
    @Min(value = 1, message = "卡类型取值为 1 或 2")
    @Max(value = 2, message = "卡类型取值为 1 或 2")
    private Integer cardType;

    @ApiModelProperty(value = "起始日期（含），按上课开始时间判定", example = "2026-10-01")
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private LocalDate startDate;

    @ApiModelProperty(value = "结束日期（含），按上课开始时间判定", example = "2026-10-07")
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private LocalDate endDate;

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
