package com.techplant.yoga.store.enums;

/** 门店类型枚举。 */
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
