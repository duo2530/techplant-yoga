package com.techplant.yoga.schedule.enums;

import lombok.Getter;

/**
 * 用户端排课展示状态：列表卡片与详情共用，决定状态文案、能否预约与能否排队。
 *
 * <p>待上架排课对用户端不可见，没有对应的展示状态。</p>
 */
@Getter
public enum PublicScheduleStateEnum {
    /** 已上架、未开始、在 14 天窗口内且有剩余名额 */
    BOOKABLE("可约"),

    /** 满员，排队未满且未过停止排队时间 */
    QUEUEABLE("排队"),

    /** 满员，且排队已满、不开放排队或已过停止排队时间 */
    FULL("已约满"),

    /** 已上架且已到开始时间：用户端开课即视为结束 */
    FINISHED("已结束"),

    /** 排课已取消 */
    CANCELLED("已取消"),

    /** 已上架、未开始，但上课日期不在今天起 14 天内 */
    NOT_OPEN("未开放");

    private final String text;

    PublicScheduleStateEnum(String text) {
        this.text = text;
    }
}
