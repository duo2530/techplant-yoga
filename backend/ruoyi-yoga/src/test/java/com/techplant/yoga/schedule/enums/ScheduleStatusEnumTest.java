package com.techplant.yoga.schedule.enums;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 排课状态枚举的单元测试（排课管理详细设计 §1.2.2、§3.4 第 2 条）。
 *
 * <p>重点：「已结束」的推导（{@code status=2 && end_time<=now}）、两套文案不串味
 * （用户端绝不出现「待上架／已上架」）、{@code bookable} 口径。</p>
 */
@DisplayName("ScheduleStatusEnum 单元测试")
class ScheduleStatusEnumTest
{
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 3, 12, 0);

    @Test
    @DisplayName("状态码与取值校验：1/2/3 合法，其它与 null 不合法")
    void isValid_shouldAcceptOnlyThreeCodes()
    {
        assertTrue(ScheduleStatusEnum.isValid(1));
        assertTrue(ScheduleStatusEnum.isValid(2));
        assertTrue(ScheduleStatusEnum.isValid(3));
        assertFalse(ScheduleStatusEnum.isValid(0));
        assertFalse(ScheduleStatusEnum.isValid(4));
        assertFalse(ScheduleStatusEnum.isValid(null));
        assertSame(ScheduleStatusEnum.PUBLISHED, ScheduleStatusEnum.of(2));
        assertNull(ScheduleStatusEnum.of(9));
    }

    @Test
    @DisplayName("已结束推导：只有 status=2 且 end_time<=now 才算已结束")
    void isFinished_shouldDeriveFromStatusAndEndTime()
    {
        assertTrue(ScheduleStatusEnum.isFinished(2, NOW.minusMinutes(1), NOW), "刚结束也算已结束");
        assertTrue(ScheduleStatusEnum.isFinished(2, NOW, NOW), "end_time == now 算已结束");
        assertFalse(ScheduleStatusEnum.isFinished(2, NOW.plusMinutes(1), NOW));
        assertFalse(ScheduleStatusEnum.isFinished(1, NOW.minusHours(1), NOW), "待上架不算已结束");
        assertFalse(ScheduleStatusEnum.isFinished(3, NOW.minusHours(1), NOW), "已取消不算已结束");
        assertFalse(ScheduleStatusEnum.isFinished(null, NOW.minusHours(1), NOW));
        assertFalse(ScheduleStatusEnum.isFinished(2, null, NOW));
    }

    @Test
    @DisplayName("管理端文案：待上架／已上架／已取消／已结束")
    void adminTextOf_shouldUseAdminWording()
    {
        assertEquals("待上架", ScheduleStatusEnum.adminTextOf(1, NOW.plusHours(1), NOW));
        assertEquals("已上架", ScheduleStatusEnum.adminTextOf(2, NOW.plusHours(1), NOW));
        assertEquals("已结束", ScheduleStatusEnum.adminTextOf(2, NOW.minusHours(1), NOW));
        assertEquals("已取消", ScheduleStatusEnum.adminTextOf(3, NOW.plusHours(1), NOW));
        assertEquals("", ScheduleStatusEnum.adminTextOf(9, NOW.plusHours(1), NOW));
    }

    @Test
    @DisplayName("用户端文案：只有 可约／已结束／已取消，绝不出现「待上架」「已上架」")
    void publicTextOf_shouldNeverLeakAdminWording()
    {
        assertEquals("可约", ScheduleStatusEnum.publicTextOf(2, NOW.plusHours(1), NOW));
        assertEquals("已结束", ScheduleStatusEnum.publicTextOf(2, NOW.minusHours(1), NOW));
        assertEquals("已取消", ScheduleStatusEnum.publicTextOf(3, NOW.plusHours(1), NOW));
        // 待上架对用户端不可见，兜底为空字符串
        assertEquals("", ScheduleStatusEnum.publicTextOf(1, NOW.plusHours(1), NOW));
        assertEquals("", ScheduleStatusEnum.publicTextOf(9, NOW.plusHours(1), NOW));
    }

    @Test
    @DisplayName("bookable：status=2 且 end_time>now 才为 true")
    void isBookable_shouldRequirePublishedAndNotFinished()
    {
        assertTrue(ScheduleStatusEnum.isBookable(2, NOW.plusMinutes(1), NOW));
        assertFalse(ScheduleStatusEnum.isBookable(2, NOW, NOW));
        assertFalse(ScheduleStatusEnum.isBookable(2, NOW.minusMinutes(1), NOW));
        assertFalse(ScheduleStatusEnum.isBookable(3, NOW.plusHours(1), NOW));
        assertFalse(ScheduleStatusEnum.isBookable(1, NOW.plusHours(1), NOW));
    }
}
