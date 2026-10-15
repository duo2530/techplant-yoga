package com.techplant.yoga.membercard.domain;

import java.time.LocalDateTime;
import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/** 会员卡次数流水数据对象，与 {@code t_member_card_transaction} 一一对应；本表只追加。 */
@TableName("t_member_card_transaction")
public class MemberCardTransactionDO
{
    /** 流水ID（雪花ID，应用侧生成） */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    /** 会员卡ID */
    private Long memberCardId;

    /** 会员ID */
    private Long memberId;

    /** 预约ID */
    private Long reservationId;

    /** 变动类型：1预约扣次 2取消返次 */
    private Integer changeType;

    /** 预约人数 */
    private Integer persons;

    /** 变动次数：扣次为负、返次为正 */
    private Integer changeTimes;

    /** 变动前剩余次数 */
    private Integer beforeTimes;

    /** 变动后剩余次数 */
    private Integer afterTimes;

    /** 变动时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getMemberCardId() { return memberCardId; }
    public void setMemberCardId(Long memberCardId) { this.memberCardId = memberCardId; }
    public Long getMemberId() { return memberId; }
    public void setMemberId(Long memberId) { this.memberId = memberId; }
    public Long getReservationId() { return reservationId; }
    public void setReservationId(Long reservationId) { this.reservationId = reservationId; }
    public Integer getChangeType() { return changeType; }
    public void setChangeType(Integer changeType) { this.changeType = changeType; }
    public Integer getPersons() { return persons; }
    public void setPersons(Integer persons) { this.persons = persons; }
    public Integer getChangeTimes() { return changeTimes; }
    public void setChangeTimes(Integer changeTimes) { this.changeTimes = changeTimes; }
    public Integer getBeforeTimes() { return beforeTimes; }
    public void setBeforeTimes(Integer beforeTimes) { this.beforeTimes = beforeTimes; }
    public Integer getAfterTimes() { return afterTimes; }
    public void setAfterTimes(Integer afterTimes) { this.afterTimes = afterTimes; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
}
