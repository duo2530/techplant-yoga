package com.techplant.yoga.membercard.enums;

/** 会员卡次数流水类型。 */
public enum MemberCardChangeTypeEnum
{
    /** 预约扣次：change_times 为负数 */
    DEDUCT(1, "预约扣次"),

    /** 取消返次：change_times 为正数 */
    REFUND(2, "取消返次");

    /** 流水类型码 */
    private final Integer code;

    /** 流水类型文案 */
    private final String name;

    MemberCardChangeTypeEnum(Integer code, String name)
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

    /** 按码取枚举，非法或空返回 {@code null} */
    public static MemberCardChangeTypeEnum of(Integer code)
    {
        if (code == null)
        {
            return null;
        }
        for (MemberCardChangeTypeEnum item : values())
        {
            if (item.code.equals(code))
            {
                return item;
            }
        }
        return null;
    }
}
