package com.techplant.yoga.membercard.enums;

import lombok.Getter;

/** 有效期单位固定枚举：开卡前为空，开卡时按卡类型写入。 */
@Getter
public enum ValidPeriodUnitEnum {
    /** 月（次数卡） */
    MONTH(1, "月"),

    /** 年（年卡） */
    YEAR(2, "年");

    /** 有效期单位码 */
    private final Integer code;

    /** 有效期单位名称 */
    private final String name;

    ValidPeriodUnitEnum(Integer code, String name) {
        this.code = code;
        this.name = name;
    }
}
