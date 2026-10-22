package com.techplant.yoga.membercard.enums;

import lombok.Getter;

import com.ruoyi.common.exception.ServiceException;

/**
 * 卡类型固定枚举，同时承载「卡类型 → 有效期映射」与用户端的卡片分类文案。
 *
 * <p>新增卡类型（如周卡）只需在这里加一条枚举与它的单位／每单位天数；
 * 有效期天数 = 有效期数量 × 每单位天数。</p>
 */
public enum MemberCardTypeEnum {
    /** 次数卡：按月计，每月 30 天，有效期数量只能取 3／6／12 */
    TIMES(1, "次数卡", "次数卡", ValidPeriodUnitEnum.MONTH, 30),

    /** 年卡：按年计，固定 365 天，有效期数量固定 1；用户端按期限卡展示 */
    ANNUAL(2, "年卡", "期限卡", ValidPeriodUnitEnum.YEAR, 365);

    /** 取值非法时统一使用的参数校验提示语（业务码 400） */
    public static final String INVALID_MESSAGE = "卡类型取值为 1 或 2";

    /** 次数卡可选的有效期数量（月） */
    private static final int[] TIMES_PERIOD_COUNTS = {3, 6, 12};

    /** 年卡固定的有效期数量（年） */
    public static final int ANNUAL_PERIOD_COUNT = 1;

    /** 卡类型码 */
    @Getter
    private final Integer code;

    /** 卡类型名称 */
    @Getter
    private final String name;

    /** 用户端卡片分类文案（UI 角标）：次数卡／期限卡 */
    @Getter
    private final String categoryName;

    /** 开卡时写入的有效期单位 */
    @Getter
    private final ValidPeriodUnitEnum validPeriodUnit;

    /** 每个有效期单位对应的天数 */
    private final int daysPerUnit;

    MemberCardTypeEnum(Integer code, String name, String categoryName, ValidPeriodUnitEnum validPeriodUnit,
            int daysPerUnit) {
        this.code = code;
        this.name = name;
        this.categoryName = categoryName;
        this.validPeriodUnit = validPeriodUnit;
        this.daysPerUnit = daysPerUnit;
    }

    /** 有效期天数：即使有效期数量为空也按 0 处理，由调用方先做必填校验 */
    public int validDays(Integer validPeriodCount) {
        return validPeriodCount == null ? 0 : validPeriodCount * daysPerUnit;
    }

    /** 是否是次数卡（次数类字段与适用门店只在次数卡上出现） */
    public boolean isTimesCard() {
        return this == TIMES;
    }

    /** 次数卡的有效期数量是否合法（仅 3／6／12 个月） */
    public static boolean isValidTimesPeriodCount(Integer validPeriodCount) {
        if (validPeriodCount == null) {
            return false;
        }
        for (int allowed : TIMES_PERIOD_COUNTS) {
            if (allowed == validPeriodCount) {
                return true;
            }
        }
        return false;
    }

    /** 按码取枚举，非法或空返回 {@code null} */
    public static MemberCardTypeEnum of(Integer code) {
        if (code == null) {
            return null;
        }
        for (MemberCardTypeEnum item : values()) {
            if (item.code.equals(code)) {
                return item;
            }
        }
        return null;
    }

    /** 「码 → 名称」翻译：列表与详情成对返回 {@code cardType} + {@code cardTypeText} */
    public static String nameOf(Integer code) {
        MemberCardTypeEnum item = of(code);
        return item == null ? null : item.name;
    }

    /** 「码 → 卡片分类文案」翻译：用户端角标成对返回 {@code cardType} + {@code cardCategoryText} */
    public static String categoryNameOf(Integer code) {
        MemberCardTypeEnum item = of(code);
        return item == null ? null : item.categoryName;
    }

    /**
     * 取值校验：非法即抛 {@code ServiceException}（业务码 400）
     *
     * @return 校验通过的枚举
     */
    public static MemberCardTypeEnum validate(Integer code) {
        MemberCardTypeEnum item = of(code);
        if (item == null) {
            throw new ServiceException(code == null ? "卡类型不能为空" : INVALID_MESSAGE, 400);
        }
        return item;
    }
}
