package com.techplant.yoga.course.enums;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.ruoyi.common.exception.ServiceException;

/**
 * 课种枚举的单元测试（课程管理详细设计 §1.2.2、§3.4 第 5 条）。
 *
 * <p>取值校验与「码 → 名」翻译都只在这里，所以这里把四类课种、边界与越界分支全部覆盖。</p>
 */
@DisplayName("CourseTypeEnum 单元测试")
class CourseTypeEnumTest
{
    @Test
    @DisplayName("码 → 名：1团课 2精品课 3私教课 4特色课")
    void nameOf_shouldTranslateAllCodes()
    {
        assertEquals("团课", CourseTypeEnum.nameOf(1));
        assertEquals("精品课", CourseTypeEnum.nameOf(2));
        assertEquals("私教课", CourseTypeEnum.nameOf(3));
        assertEquals("特色课", CourseTypeEnum.nameOf(4));
    }

    @Test
    @DisplayName("码 → 名：null 与越界不抛异常，返回 null（读路径兜底用）")
    void nameOf_shouldReturnNullForInvalidCode()
    {
        assertNull(CourseTypeEnum.nameOf(null));
        assertNull(CourseTypeEnum.nameOf(0));
        assertNull(CourseTypeEnum.nameOf(5));
        assertNull(CourseTypeEnum.nameOf(-1));
    }

    @Test
    @DisplayName("取值校验：1~4 合法；null 视为未传（由必填校验负责）")
    void isValid_shouldAcceptOneToFourAndNull()
    {
        assertTrue(CourseTypeEnum.isValid(1));
        assertTrue(CourseTypeEnum.isValid(4));
        assertTrue(CourseTypeEnum.isValid(null), "null 视为未传，不在这里报错");
        assertFalse(CourseTypeEnum.isValid(0));
        assertFalse(CourseTypeEnum.isValid(5));
    }

    @Test
    @DisplayName("强校验：越界抛 500，null 抛「课程类型不能为空」")
    void validate_shouldThrowServiceException()
    {
        CourseTypeEnum.validate(1);
        CourseTypeEnum.validate(4);

        ServiceException invalid = assertThrows(ServiceException.class, () -> CourseTypeEnum.validate(5));
        assertEquals(500, invalid.getCode().intValue());
        assertEquals("课程类型取值为 1~4", invalid.getMessage());

        ServiceException empty = assertThrows(ServiceException.class, () -> CourseTypeEnum.validate(null));
        assertEquals(500, empty.getCode().intValue());
        assertEquals("课程类型不能为空", empty.getMessage());
    }

    @Test
    @DisplayName("边界常量与枚举项一一对应")
    void constants_shouldMatchEnumValues()
    {
        assertEquals(4, CourseTypeEnum.values().length);
        assertEquals(1, CourseTypeEnum.MIN_CODE);
        assertEquals(4, CourseTypeEnum.MAX_CODE);
        assertEquals(Integer.valueOf(1), CourseTypeEnum.of(1).getCode());
        assertEquals("特色课", CourseTypeEnum.of(4).getName());
        assertNull(CourseTypeEnum.of(7));
    }
}
