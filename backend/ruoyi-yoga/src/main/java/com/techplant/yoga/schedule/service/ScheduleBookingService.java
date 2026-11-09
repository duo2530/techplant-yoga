package com.techplant.yoga.schedule.service;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import com.techplant.yoga.schedule.vo.ScheduleBookingSnapshot;

/** 排课为预约模块提供的读取与写入能力。 */
public interface ScheduleBookingService {
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

    /**
     * 人数不足待取消的排课 ID（不加锁），调用方须在持有排课行锁后复查
     *
     * @param latestStart 开始时间不晚于该时刻的排课才计入
     */
    List<Long> understaffedIds(LocalDateTime now, LocalDateTime latestStart);

    /**
     * 在已锁定的排课行上把状态改为已取消
     *
     * @return 影响行数，0 表示排课已被删除
     */
    int cancel(Long scheduleId);
}
