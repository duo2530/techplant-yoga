package com.techplant.yoga.store.enums;

/**
 * 门店类型枚举（门店管理详细设计 §1.2.2）。
 *
 * <p>{@code store_type}：{@code 1} 主力店、{@code 2} 精品店。本枚举是门店类型<b>唯一</b>的
 * 取值校验与「码 → 名」翻译落点；接口返回时 {@code storeType} 与 {@code storeTypeName} 成对给出
 * （详细设计总览 §2「枚举返回」）。</p>
 *
 * <p><b>注意：</b>「所在区域」{@code region_code} <b>不建 Java 枚举</b>，取值校验与区名翻译都走
 * 字典 {@code store_region}（详细设计总览 §5 决策 9）。</p>
 */
public enum StoreTypeEnum
{
    /** 主力店 */
    MAIN(1, "主力店"),

    /** 精品店 */
    BOUTIQUE(2, "精品店");

    /** 落库编码 */
    private final int code;

    /** 中文名称 */
    private final String name;

    StoreTypeEnum(int code, String name)
    {
        this.code = code;
        this.name = name;
    }

    public int getCode()
    {
        return code;
    }

    public String getName()
    {
        return name;
    }

    /**
     * 按落库编码取枚举
     *
     * @param code 门店类型编码，可为 null
     * @return 匹配的枚举；null 或越界时返回 {@code null}
     */
    public static StoreTypeEnum of(Integer code)
    {
        if (code == null)
        {
            return null;
        }
        for (StoreTypeEnum storeType : values())
        {
            if (storeType.code == code.intValue())
            {
                return storeType;
            }
        }
        return null;
    }

    /**
     * 编码是否合法（取值 ∈ {1, 2}）
     */
    public static boolean isValid(Integer code)
    {
        return of(code) != null;
    }

    /**
     * 码 → 名翻译；未知编码或 null 返回<b>空字符串</b>（不返回 null）
     */
    public static String nameOf(Integer code)
    {
        StoreTypeEnum storeType = of(code);
        return storeType == null ? "" : storeType.getName();
    }
}
