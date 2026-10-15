package com.techplant.yoga.schedule.service;

import java.util.Collection;
import java.util.List;
import com.techplant.yoga.schedule.vo.ScheduleBookingSnapshot;

/** 排课为预约模块提供的读取与写入能力。 */
public interface ScheduleBookingService
{
    /**
     * 锁定排课行并返回快照，调用方必须处于事务中
     *
     * @return 排课不存在或已被删除时返回 {@code null}
     */
    ScheduleBookingSnapshot lockForBooking(Long scheduleId);

    /**
     * 批量读取排课快照（不加锁），供预约列表装配课程与门店信息
     *
     * @param scheduleIds 排课 ID 集合，可为 null／空
     * @return 命中的快照；入参为空时返回空列表（不返回 null）
     */
    List<ScheduleBookingSnapshot> snapshots(Collection<Long> scheduleIds);

    /**
     * 在已锁定的排课行上写入最新预约人数
     *
     * @param bookedPersons 写入后的已预约人数；超过最大人数时不更新
     * @return 影响行数，0 表示超出名额或排课已被删除
     */
    int updateBookingCount(Long scheduleId, Integer bookedPersons);
}
