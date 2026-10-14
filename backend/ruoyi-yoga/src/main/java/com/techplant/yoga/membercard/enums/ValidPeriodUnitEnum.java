package com.techplant.yoga.membercard.enums;

/** 有效期单位固定枚举：开卡前为空，开卡时按卡类型写入。 */
public enum ValidPeriodUnitEnum
{
    /** 月（次数卡） */
    MONTH(1, "月"),

    /** 年（年卡） */
    YEAR(2, "年");

    /** 有效期单位码 */
    private final Integer code;

    /** 有效期单位名称 */
    private final String name;

    ValidPeriodUnitEnum(Integer code, String name)
    {
        this.code = code;
        this.name = name;
    }

    public Integer getCode()
    {
        return code;
    }

    public String getName()
    {
        return name;
    }
}
