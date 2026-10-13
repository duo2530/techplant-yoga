package com.techplant.yoga.schedule.service.impl;

import org.springframework.stereotype.Service;
import com.ruoyi.common.exception.ServiceException;
import com.techplant.yoga.schedule.service.ScheduleBookingService;
import com.techplant.yoga.schedule.vo.ScheduleBookingSnapshot;

/** 预约排课接口的不可用实现。 */
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
