package com.techplant.yoga.reservation.enums;

import lombok.Getter;

/** 取消原因枚举：1 会员主动取消／2 排课取消。 */
@Getter
public enum ReservationCancelReasonEnum {
    /** 会员主动取消 */
    MEMBER(1, "会员主动取消"),

    /** 排课取消 */
    SCHEDULE(2, "排课取消");

    /** 取消原因码 */
    private final Integer code;

    /** 取消原因文案 */
    private final String text;

    ReservationCancelReasonEnum(Integer code, String text) {
        this.code = code;
        this.text = text;
    }
}
