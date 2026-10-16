package com.techplant.yoga.membercard.domain;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/** 会员卡次数流水数据对象，与 {@code t_member_card_transaction} 一一对应；本表只追加。 */
@TableName("t_member_card_transaction")
@Getter
@Setter
public class MemberCardTransactionDO {
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
}
