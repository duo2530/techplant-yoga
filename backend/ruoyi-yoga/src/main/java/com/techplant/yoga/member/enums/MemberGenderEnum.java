package com.techplant.yoga.member.enums;

import lombok.Getter;

import com.ruoyi.common.exception.ServiceException;

/** 会员性别固定枚举：取值范围与「码 → 名」翻译的唯一来源。 */
@Getter
public enum MemberGenderEnum {
    /** 未知 */
    UNKNOWN(0, "未知"),

    /** 男 */
    MALE(1, "男"),

    /** 女 */
    FEMALE(2, "女");

    /** 最小合法取值 */
    public static final int MIN_CODE = 0;

    /** 最大合法取值 */
    public static final int MAX_CODE = 2;

    /** 取值非法时统一使用的参数校验提示语 */
    public static final String INVALID_MESSAGE = "性别取值为 0~2";

    /** 参数校验失败 */
    private static final int PARAM_INVALID = 500;

    /** 性别码 */
    private final Integer code;

    /** 性别名称 */
    private final String name;

    MemberGenderEnum(Integer code, String name) {
        this.code = code;
        this.name = name;
    }

    /**
     * 按码取枚举，非法码或空返回 {@code null}
     */
    public static MemberGenderEnum of(Integer code) {
        if (code == null) {
            return null;
        }
        for (MemberGenderEnum item : values()) {
            if (item.code.equals(code)) {
                return item;
            }
        }
        return null;
    }

    /**
     * 「码 → 名称」翻译，供出参成对返回 {@code gender} ＋ {@code genderText}；非法码或空返回 {@code null}
     */
    public static String nameOf(Integer code) {
        MemberGenderEnum item = of(code);
        return item == null ? null : item.name;
    }

    /**
     * 取值校验：空表示未提交（新增缺省取 0、修改保留原值），非法码抛参数校验异常
     *
     * @throws ServiceException 取值不在 0~2
     */
    public static void validateIfPresent(Integer code) {
        if (code != null && of(code) == null) {
            throw new ServiceException(INVALID_MESSAGE, PARAM_INVALID);
        }
    }
}
