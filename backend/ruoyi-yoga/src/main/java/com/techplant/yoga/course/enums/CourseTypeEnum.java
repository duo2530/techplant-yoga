package com.techplant.yoga.course.enums;

import com.ruoyi.common.exception.ServiceException;

/** 课种固定枚举。 */
public enum CourseTypeEnum
{
    /** 团课 */
    GROUP(1, "团课"),

    /** 精品课 */
    BOUTIQUE(2, "精品课"),

    /** 私教课 */
    PERSONAL(3, "私教课"),

    /** 特色课 */
    SPECIAL(4, "特色课");

    /** 最小合法取值 */
    public static final int MIN_CODE = 1;

    /** 最大合法取值 */
    public static final int MAX_CODE = 4;

    /** 取值非法时统一使用的参数校验提示语（业务码 500） */
    public static final String INVALID_MESSAGE = "课程类型取值为 1~4";

    /** 课种码 */
    private final Integer code;

    /** 课种名称 */
    private final String name;

    CourseTypeEnum(Integer code, String name)
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

    /**
     * 取值是否合法（1~4）；{@code null} 视为「未传」，由必填校验负责
     */
    public static boolean isValid(Integer code)
    {
        if (code == null)
        {
            return true;
        }
        for (CourseTypeEnum item : values())
        {
            if (item.code.equals(code))
            {
                return true;
            }
        }
        return false;
    }

    /**
     * 按码取枚举，非法或空返回 {@code null}
     */
    public static CourseTypeEnum of(Integer code)
    {
        if (code == null)
        {
            return null;
        }
        for (CourseTypeEnum item : values())
        {
            if (item.code.equals(code))
            {
                return item;
            }
        }
        return null;
    }

    /** 「码 → 名称」翻译：列表与详情必须成对返回 {@code courseType} + {@code courseTypeName}。非法码返回 {@code null}，由调用方做兜底。 */
    public static String nameOf(Integer code)
    {
        CourseTypeEnum item = of(code);
        return item == null ? null : item.name;
    }

    /**
     * 取值校验：非法即抛 {@code ServiceException}（业务码 500，与框架的参数校验口径一致）
     *
     * @throws ServiceException 取值不在 1~4
     */
    public static void validate(Integer code)
    {
        if (code == null)
        {
            throw new ServiceException("课程类型不能为空", 500);
        }
        if (!isValid(code))
        {
            throw new ServiceException(INVALID_MESSAGE, 500);
        }
    }
}
