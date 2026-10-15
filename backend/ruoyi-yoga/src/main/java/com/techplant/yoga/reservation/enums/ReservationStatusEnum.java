package com.techplant.yoga.reservation.enums;

/** 预约状态枚举：1 已预约／2 已取消／3 已签到。 */
public enum ReservationStatusEnum
{
    /** 已预约：占用名额并已扣次 */
    RESERVED(1, "已预约"),

    /** 已取消：已释放名额并返次 */
    CANCELLED(2, "已取消"),

    /** 已签到：不重复扣次 */
    CHECKED_IN(3, "已签到");

    /** 预约状态码 */
    private final Integer code;

    /** 预约状态文案 */
    private final String text;

    ReservationStatusEnum(Integer code, String text)
    {
        this.code = code;
        this.text = text;
    }

    public Integer getCode()
    {
        return code;
    }

    public String getText()
    {
        return text;
    }

    /** 按码取枚举，非法或空返回 {@code null} */
    public static ReservationStatusEnum of(Integer code)
    {
        if (code == null)
        {
            return null;
        }
        for (ReservationStatusEnum item : values())
        {
            if (item.code.equals(code))
            {
                return item;
            }
        }
        return null;
    }

    /** 「码 → 文案」翻译：列表与详情成对返回 {@code status} + {@code statusText}；非法码返回空字符串 */
    public static String textOf(Integer code)
    {
        ReservationStatusEnum item = of(code);
        return item == null ? "" : item.text;
    }
}
