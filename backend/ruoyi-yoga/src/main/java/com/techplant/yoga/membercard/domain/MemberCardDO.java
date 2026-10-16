package com.techplant.yoga.membercard.domain;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/** 会员卡数据对象，与 {@code t_member_card} 一一对应。 */
@TableName("t_member_card")
@Getter
@Setter
public class MemberCardDO {
    /** 会员卡ID（雪花ID，应用侧生成） */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    /** 所属会员ID（跨模块引用 t_member.id，不建物理外键） */
    private Long memberId;

    /** 开卡门店ID（发卡时录入；卡号里的门店编号取自它） */
    private Long openStoreId;

    /** 卡号（服务端生成，全平台唯一） */
    private String cardNo;

    /** 卡名称 */
    private String cardName;

    /** 卡类型：1次数卡 2年卡 */
    private Integer cardType;

    /** 适用课种范围：1团课类 2私教课 */
    private Integer courseScope;

    /** 适用门店ID（次数卡必填，年卡为空；与开卡门店是两个字段） */
    private Long storeId;

    /** 购买次数（仅次数卡） */
    private Integer purchaseTimes;

    /** 赠送次数（仅次数卡） */
    private Integer giftTimes;

    /** 剩余次数（仅次数卡，发卡时 = 购买 + 赠送） */
    private Integer remainingTimes;

    /** 有效期单位：1月 2年（开卡前为空） */
    private Integer validPeriodUnit;

    /** 有效期数量（次数卡 3／6／12 月；年卡固定 1 年） */
    private Integer validPeriodCount;

    /** 有效期起（开卡时写入，开卡日 00:00:00） */
    private LocalDateTime validStartTime;

    /** 有效期止（开卡时写入，含首尾日） */
    private LocalDateTime validEndTime;

    /** 开卡时间 */
    private LocalDateTime activateTime;

    /** 停卡时间 */
    private LocalDateTime stopTime;

    /** 停卡原因 */
    private String stopReason;

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
