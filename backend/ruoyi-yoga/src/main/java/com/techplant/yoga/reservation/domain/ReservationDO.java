package com.techplant.yoga.reservation.domain;

import java.time.LocalDateTime;
import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/** 课程预约单数据对象，与 {@code t_reservation} 一一对应。 */
@TableName("t_reservation")
public class ReservationDO
{
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

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public Long getMemberId() { return memberId; }
    public void setMemberId(Long memberId) { this.memberId = memberId; }
    public Long getScheduleId() { return scheduleId; }
    public void setScheduleId(Long scheduleId) { this.scheduleId = scheduleId; }
    public LocalDateTime getStartTime() { return startTime; }
    public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }
    public LocalDateTime getEndTime() { return endTime; }
    public void setEndTime(LocalDateTime endTime) { this.endTime = endTime; }
    public Integer getPersons() { return persons; }
    public void setPersons(Integer persons) { this.persons = persons; }
    public Long getMemberCardId() { return memberCardId; }
    public void setMemberCardId(Long memberCardId) { this.memberCardId = memberCardId; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public LocalDateTime getReservationTime() { return reservationTime; }
    public void setReservationTime(LocalDateTime reservationTime) { this.reservationTime = reservationTime; }
    public LocalDateTime getCancelTime() { return cancelTime; }
    public void setCancelTime(LocalDateTime cancelTime) { this.cancelTime = cancelTime; }
    public Integer getCancelReason() { return cancelReason; }
    public void setCancelReason(Integer cancelReason) { this.cancelReason = cancelReason; }
    public LocalDateTime getCheckInTime() { return checkInTime; }
    public void setCheckInTime(LocalDateTime checkInTime) { this.checkInTime = checkInTime; }
    public Long getCreateBy() { return createBy; }
    public void setCreateBy(Long createBy) { this.createBy = createBy; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
    public Long getUpdateBy() { return updateBy; }
    public void setUpdateBy(Long updateBy) { this.updateBy = updateBy; }
    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }
}
