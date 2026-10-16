package com.techplant.yoga.reservation.domain;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/** 课程预约单数据对象，与 {@code t_reservation} 一一对应。 */
@TableName("t_reservation")
@Getter
@Setter
public class ReservationDO {
    /** 预约ID（雪花ID，应用侧生成） */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    /** 请求幂等标识（客户端 UUID，按会员唯一） */
    private String requestId;

    /** 预约会员ID */
    private Long memberId;

    /** 排课ID */
    private Long scheduleId;

    /** 上课开始时间（预约时的排课快照） */
    private LocalDateTime startTime;

    /** 上课结束时间（预约时的排课快照） */
    private LocalDateTime endTime;

    /** 预约人数 */
    private Integer persons;

    /** 结算会员卡ID */
    private Long memberCardId;

    /** 预约状态：1已预约 2已取消 3已签到 */
    private Integer status;

    /** 预约成功时间 */
    private LocalDateTime reservationTime;

    /** 取消时间 */
    private LocalDateTime cancelTime;

    /** 取消原因：1会员主动取消 2排课取消 */
    private Integer cancelReason;

    /** 签到时间 */
    private LocalDateTime checkInTime;

    /** 创建人ID */
    @TableField(fill = FieldFill.INSERT)
    private Long createBy;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 更新人ID */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long updateBy;

    /** 更新时间 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
