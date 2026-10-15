package com.techplant.yoga.schedule.service.impl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import com.techplant.yoga.schedule.convert.ScheduleConverter;
import com.techplant.yoga.schedule.dao.ScheduleDao;
import com.techplant.yoga.schedule.domain.ScheduleDO;
import com.techplant.yoga.schedule.service.ScheduleBookingService;
import com.techplant.yoga.schedule.vo.ScheduleBookingSnapshot;

/** 排课为预约模块提供的读取与写入实现。 */
@Service
public class ScheduleBookingServiceImpl implements ScheduleBookingService
{
    private final ScheduleDao scheduleDao;

    public ScheduleBookingServiceImpl(ScheduleDao scheduleDao)
    {
        this.scheduleDao = scheduleDao;
    }

    @Override
    public ScheduleBookingSnapshot lockForBooking(Long scheduleId)
    {
        ScheduleDO schedule = scheduleDao.selectByIdForUpdate(scheduleId);
        return schedule == null ? null : ScheduleConverter.toBookingSnapshot(schedule);
    }

    @Override
    public List<ScheduleBookingSnapshot> snapshots(Collection<Long> scheduleIds)
    {
        List<ScheduleBookingSnapshot> snapshots = new ArrayList<ScheduleBookingSnapshot>();
        if (scheduleIds == null || scheduleIds.isEmpty())
        {
            return snapshots;
        }
        Set<Long> ids = new HashSet<Long>();
        for (Long scheduleId : scheduleIds)
        {
            if (scheduleId != null)
            {
                ids.add(scheduleId);
            }
        }
        if (ids.isEmpty())
        {
            return snapshots;
        }
        for (ScheduleDO schedule : scheduleDao.selectByIds(ids))
        {
            snapshots.add(ScheduleConverter.toBookingSnapshot(schedule));
        }
        return snapshots;
    }

    @Override
    public int updateBookingCount(Long scheduleId, Integer bookedPersons)
    {
        if (scheduleId == null || bookedPersons == null || bookedPersons < 0)
        {
            return 0;
        }
        return scheduleDao.updateBookedPersons(scheduleId, bookedPersons);
    }
}
