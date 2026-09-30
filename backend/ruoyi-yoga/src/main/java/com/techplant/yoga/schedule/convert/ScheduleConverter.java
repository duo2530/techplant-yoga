package com.techplant.yoga.schedule.convert;

import com.techplant.yoga.schedule.domain.ScheduleDO;
import com.techplant.yoga.schedule.dto.ScheduleCreateDTO;
import com.techplant.yoga.schedule.dto.ScheduleUpdateDTO;
import com.techplant.yoga.schedule.vo.ScheduleBookingSnapshot;
import com.techplant.yoga.schedule.vo.ScheduleDetailVO;
import com.techplant.yoga.schedule.vo.ScheduleListItemVO;

/** 排班对象转换。 */
public final class ScheduleConverter
{
    private ScheduleConverter() { }

    public static ScheduleDO toDO(ScheduleCreateDTO dto)
    {
        ScheduleDO schedule = new ScheduleDO();
        schedule.setStoreId(dto.getStoreId());
        schedule.setCourseId(dto.getCourseId());
        schedule.setStartTime(dto.getStartTime());
        schedule.setEndTime(dto.getEndTime());
        schedule.setCapacity(dto.getCapacity());
        schedule.setBookingCount(0);
        schedule.setStatus(1);
        schedule.setDeleted(0);
        return schedule;
    }

    public static void applyUpdate(ScheduleDO schedule, ScheduleUpdateDTO dto)
    {
        schedule.setStoreId(dto.getStoreId());
        schedule.setCourseId(dto.getCourseId());
        schedule.setStartTime(dto.getStartTime());
        schedule.setEndTime(dto.getEndTime());
        schedule.setCapacity(dto.getCapacity());
    }

    public static ScheduleListItemVO toListItemVO(ScheduleDO source)
    {
        ScheduleListItemVO vo = new ScheduleListItemVO();
        vo.setId(source.getId());
        vo.setStoreId(source.getStoreId());
        vo.setCourseId(source.getCourseId());
        vo.setStartTime(source.getStartTime());
        vo.setEndTime(source.getEndTime());
        vo.setCapacity(source.getCapacity());
        vo.setBookingCount(source.getBookingCount());
        vo.setStatus(source.getStatus());
        vo.setCreateTime(source.getCreateTime());
        return vo;
    }

    public static ScheduleDetailVO toDetailVO(ScheduleDO source)
    {
        ScheduleDetailVO vo = new ScheduleDetailVO();
        ScheduleListItemVO item = toListItemVO(source);
        vo.setId(item.getId());
        vo.setStoreId(item.getStoreId());
        vo.setCourseId(item.getCourseId());
        vo.setStartTime(item.getStartTime());
        vo.setEndTime(item.getEndTime());
        vo.setCapacity(item.getCapacity());
        vo.setBookingCount(item.getBookingCount());
        vo.setStatus(item.getStatus());
        vo.setCreateTime(item.getCreateTime());
        return vo;
    }

    public static ScheduleBookingSnapshot toBookingSnapshot(ScheduleDO source)
    {
        ScheduleBookingSnapshot snapshot = new ScheduleBookingSnapshot();
        snapshot.setId(source.getId());
        snapshot.setStoreId(source.getStoreId());
        snapshot.setCourseId(source.getCourseId());
        snapshot.setStartTime(source.getStartTime());
        snapshot.setEndTime(source.getEndTime());
        snapshot.setCapacity(source.getCapacity());
        snapshot.setBookingCount(source.getBookingCount());
        return snapshot;
    }
}
