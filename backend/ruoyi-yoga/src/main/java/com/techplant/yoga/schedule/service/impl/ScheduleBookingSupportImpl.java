package com.techplant.yoga.schedule.service.impl;

import org.springframework.stereotype.Service;
import com.ruoyi.common.exception.ServiceException;
import com.techplant.yoga.schedule.service.ScheduleBookingService;
import com.techplant.yoga.schedule.vo.ScheduleBookingSnapshot;

/**
 * 预约模块的排课锁定入口（一期保留接口、拒绝实现）。
 *
 * <p><b>为什么需要这个类：</b>一期<b>不启用预约</b>（{@code BR-全局-005}，{@code t_booking} 表都不建），
 * 但 {@code booking} 模块的 {@code BookingServiceImpl} 仍按构造器注入 {@link ScheduleBookingService}，
 * 而新的 {@code ScheduleServiceImpl} 只实现《排课管理详细设计》冻结的 {@link com.techplant.yoga.schedule.service.ScheduleService}
 * 契约（不再兼任预约侧的 {@code lockForBooking}／{@code updateBookingCount}）。
 * 没有这个 Bean，Spring 启动时会因找不到 {@code ScheduleBookingService} 而失败，因此这里
 * 提供一个**什么都不做、直接拒绝**的实现：既不碰 {@code booked_persons}（一期写路径不容许），
 * 也不动 {@code booking} 模块的任何代码。</p>
 *
 * <p>二期接入预约时：删除本类，把这两个方法按 {@code BR-排课-022} 的等价口径实现到预约事务里。</p>
 */
@Service
public class ScheduleBookingSupportImpl implements ScheduleBookingService
{
    /** 参数校验失败／系统异常 */
    private static final int PARAM_ERROR = 500;

    @Override
    public ScheduleBookingSnapshot lockForBooking(Long scheduleId)
    {
        throw unsupported();
    }

    @Override
    public void updateBookingCount(Long scheduleId, Integer bookingCount)
    {
        throw unsupported();
    }

    private ServiceException unsupported()
    {
        return new ServiceException("一期暂不开放预约功能", PARAM_ERROR);
    }
}
