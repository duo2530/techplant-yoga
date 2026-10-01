package com.techplant.yoga.store.enums;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 门店类型枚举单元测试（门店管理详细设计 §1.2.2）。
 *
 * <p>枚举是门店类型唯一的取值校验与「码 → 名」翻译落点；「所在区域」不建 Java 枚举。</p>
 */
@DisplayName("StoreTypeEnum 单元测试")
class StoreTypeEnumTest
{
    @Test
    @DisplayName("1 主力店、2 精品店")
    void of_shouldMapCodeToName()
    {
        assertEquals("主力店", StoreTypeEnum.of(1).getName());
        assertEquals("精品店", StoreTypeEnum.of(2).getName());
        assertEquals(1, StoreTypeEnum.MAIN.getCode());
        assertEquals(2, StoreTypeEnum.BOUTIQUE.getCode());
    }

    @Test
    @DisplayName("越界或 null 不是合法取值")
    void isValid_shouldRejectIllegalValues()
    {
        assertTrue(StoreTypeEnum.isValid(1));
        assertTrue(StoreTypeEnum.isValid(2));
        assertFalse(StoreTypeEnum.isValid(0));
        assertFalse(StoreTypeEnum.isValid(3));
        assertFalse(StoreTypeEnum.isValid(null));
        assertNull(StoreTypeEnum.of(99));
    }

    @Test
    @DisplayName("码 → 名翻译对未知值返回空字符串，不返回 null")
    void nameOf_shouldFallbackToEmptyString()
    {
        assertEquals("主力店", StoreTypeEnum.nameOf(1));
        assertEquals("精品店", StoreTypeEnum.nameOf(2));
        assertEquals("", StoreTypeEnum.nameOf(3));
        assertEquals("", StoreTypeEnum.nameOf(null));
    }
}
