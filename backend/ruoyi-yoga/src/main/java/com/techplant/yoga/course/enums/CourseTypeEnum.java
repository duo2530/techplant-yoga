package com.techplant.yoga.course.enums;

import com.ruoyi.common.exception.ServiceException;

/**
 * 课种固定枚举（课程管理详细设计 §1.2.2、§3.4 第 5 条）。
 *
 * <p><b>取值校验与「码 → 名称」翻译都只在这一个类里</b>：controller、DTO、SQL、前端都不得
 * 再各写一遍 1~4 的判断或「1 = 团课」的映射（§3.4 第 5 条）。</p>
 *
 * <table>
 * <caption>取值表</caption>
 * <tr><td>1</td><td>团课</td></tr>
 * <tr><td>2</td><td>精品课</td></tr>
 * <tr><td>3</td><td>私教课（枚举保留，一期不排课）</td></tr>
 * <tr><td>4</td><td>特色课</td></tr>
 * </table>
 */
public enum CourseTypeEnum
{
    /** 团课 */
    GROUP(1, "团课"),

    /** 精品课 */
    BOUTIQUE(2, "精品课"),

    /** 私教课：枚举保留，一期不使用（无任何排课数据） */
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

    /**
     * 「码 → 名称」翻译：列表与详情必须成对返回 {@code courseType} + {@code courseTypeName}
     * （详细设计总览 §2「枚举返回」约定）。非法码返回 {@code null}，由调用方做兜底。
     */
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
