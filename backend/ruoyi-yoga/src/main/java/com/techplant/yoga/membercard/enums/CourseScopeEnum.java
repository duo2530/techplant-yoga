package com.techplant.yoga.membercard.enums;

import lombok.Getter;

import com.ruoyi.common.exception.ServiceException;
import com.techplant.yoga.course.enums.CourseTypeEnum;

/** 适用课种范围固定枚举。 */
@Getter
public enum CourseScopeEnum {
    /** 团课类：团课／精品课／特色课 */
    GROUP(1, "团课类"),

    /** 私教课 */
    PERSONAL(2, "私教课");

    /** 取值非法时统一使用的参数校验提示语（业务码 500） */
    public static final String INVALID_MESSAGE = "适用课种范围取值为 1 或 2";

    /** 课种范围码 */
    private final Integer code;

    /** 课种范围名称 */
    private final String name;

    CourseScopeEnum(Integer code, String name) {
        this.code = code;
        this.name = name;
    }

    /** 取值是否合法（1 或 2） */
    public static boolean isValid(Integer code) {
        if (code == null) {
            return true;
        }
        for (CourseScopeEnum item : values()) {
            if (item.code.equals(code)) {
                return true;
            }
        }
        return false;
    }

    /** 按码取枚举，非法或空返回 {@code null} */
    public static CourseScopeEnum of(Integer code) {
        if (code == null) {
            return null;
        }
        for (CourseScopeEnum item : values()) {
            if (item.code.equals(code)) {
                return item;
            }
        }
        return null;
    }

    /** 「码 → 名称」翻译：列表与详情成对返回 {@code courseScope} + {@code courseScopeName} */
    public static String nameOf(Integer code) {
        CourseScopeEnum item = of(code);
        return item == null ? null : item.name;
    }

    /**
     * 适用范围内是否包含给定课种
     *
     * <p>团课类含团课、精品课与特色课，私教课只含私教课；课种取值非法时不适用。</p>
     */
    public boolean covers(Integer courseType) {
        CourseTypeEnum type = CourseTypeEnum.of(courseType);
        if (type == null) {
            return false;
        }
        return this == PERSONAL ? type == CourseTypeEnum.PERSONAL : type != CourseTypeEnum.PERSONAL;
    }

    /**
     * 取值校验：非法即抛 {@code ServiceException}（业务码 500，与框架的参数校验口径一致）
     *
     * @return 校验通过的枚举
     */
    public static CourseScopeEnum validate(Integer code) {
        CourseScopeEnum item = of(code);
        if (item == null) {
            throw new ServiceException(code == null ? "适用课种范围不能为空" : INVALID_MESSAGE, 500);
        }
        return item;
    }
}
