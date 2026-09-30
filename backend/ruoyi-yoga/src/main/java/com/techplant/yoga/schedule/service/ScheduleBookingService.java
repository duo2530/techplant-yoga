package com.techplant.yoga.schedule.service;

import com.techplant.yoga.schedule.vo.ScheduleBookingSnapshot;

/** 排班为预约模块提供的事务内能力。 */
public interface ScheduleBookingService
{
    /** 锁定可预约排班行，调用方必须处于事务中。 */
    ScheduleBookingSnapshot lockForBooking(Long scheduleId);

    /** 在已锁定的排班行上写入最新预约人数。 */
    void updateBookingCount(Long scheduleId, Integer bookingCount);
}
