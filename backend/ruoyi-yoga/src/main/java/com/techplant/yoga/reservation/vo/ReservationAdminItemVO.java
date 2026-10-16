package com.techplant.yoga.reservation.vo;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

/**
 * 管理端预约列表项。
 *
 * <p>上课时间读取预约快照；课程、教练、门店、教室、会员与会员卡的展示字段读取当前关联资料，
 * 关联资料已物理删除时对应字段为 {@code null}。</p>
 */
@ApiModel("管理端预约列表项")
@Getter
@Setter
public class ReservationAdminItemVO {
    @ApiModelProperty(value = "预约ID（雪花ID，字符串）", example = "1856739201475240001")
    private Long id;

    @ApiModelProperty(value = "排课ID（雪花ID，字符串）", example = "1856739201475239001")
    private Long scheduleId;

    @ApiModelProperty(value = "关联排课的课程ID")
    private Long courseId;

    @ApiModelProperty(value = "当前课程名称")
    private String courseName;

    @ApiModelProperty(value = "当前课程封面URL")
    private String courseCoverUrl;

    @ApiModelProperty(value = "排课课种：1团课 2精品课 3私教课 4特色课", example = "1")
    private Integer courseType;

    @ApiModelProperty(value = "课种名称", example = "团课")
    private String courseTypeName;

    @ApiModelProperty(value = "当前课程难度，1~5", example = "2")
    private Integer difficulty;

    @ApiModelProperty(value = "当前教练姓名")
    private String coachName;

    @ApiModelProperty(value = "排课门店ID")
    private Long storeId;

    @ApiModelProperty(value = "当前门店名称")
    private String storeName;

    @ApiModelProperty(value = "当前门店地址")
    private String storeAddress;

    @ApiModelProperty(value = "当前教室名称")
    private String classroomName;

    @ApiModelProperty(value = "上课日期，由预约开始时间生成", example = "2026-10-15")
    private String scheduleDate;

    @ApiModelProperty(value = "上课开始时间快照")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startTime;

    @ApiModelProperty(value = "上课结束时间快照")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endTime;

    @ApiModelProperty(value = "起止时间差的整分钟数", example = "60")
    private Integer durationMinutes;

    @ApiModelProperty(value = "原预约人数", example = "2")
    private Integer persons;

    @ApiModelProperty(value = "结算会员卡ID")
    private Long memberCardId;

    @ApiModelProperty(value = "当前会员卡名称")
    private String cardName;

    @ApiModelProperty(value = "关联会员卡类型：1次数卡 2年卡")
    private Integer cardType;

    @ApiModelProperty(value = "卡类型名称", example = "次数卡")
    private String cardTypeText;

    @ApiModelProperty(value = "原实际扣次；无扣次流水为 0", example = "2")
    private Integer deductTimes;

    @ApiModelProperty(value = "预约状态：1已预约 2已取消 3已签到", example = "1")
    private Integer status;

    @ApiModelProperty(value = "预约状态文案", example = "已预约")
    private String statusText;

    @ApiModelProperty(value = "排课状态：1待上架 2已上架 3已取消 4已结束")
    private Integer scheduleStatus;

    @ApiModelProperty(value = "排课状态文案")
    private String scheduleStatusText;

    @ApiModelProperty(value = "预约成功时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime reservationTime;

    @ApiModelProperty(value = "取消时间，未取消为空")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime cancelTime;

    @ApiModelProperty(value = "取消原因：1会员主动取消 2排课取消，未取消为空")
    private Integer cancelReason;

    @ApiModelProperty(value = "签到时间，未签到为空")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime checkInTime;

    @ApiModelProperty(value = "预约会员ID")
    private Long memberId;

    @ApiModelProperty(value = "当前会员姓名", example = "忻会员8000")
    private String memberName;

    @ApiModelProperty(value = "当前会员手机号", example = "13800138000")
    private String memberPhone;

    @ApiModelProperty(value = "当前会员卡卡号", example = "202610140001100000")
    private String cardNo;

    @ApiModelProperty(value = "排课教练ID")
    private Long coachId;

    @ApiModelProperty(value = "排课教室ID")
    private Long classroomId;
}
