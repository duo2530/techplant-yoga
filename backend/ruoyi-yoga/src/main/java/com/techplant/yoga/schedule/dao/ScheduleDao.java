package com.techplant.yoga.schedule.dao;

import java.time.LocalDateTime;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.techplant.yoga.schedule.domain.ScheduleDO;
import com.techplant.yoga.schedule.query.ScheduleQuery;

/** 排班数据访问语义。 */
public interface ScheduleDao
{
    IPage<ScheduleDO> selectPage(ScheduleQuery query);
    ScheduleDO selectById(Long id);
    ScheduleDO selectForUpdate(Long id);
    boolean existsSameSlot(Long storeId, Long courseId, LocalDateTime startTime, LocalDateTime endTime, Long excludeId);
    int insert(ScheduleDO schedule);
    int updateById(ScheduleDO schedule);
    int updateStatus(Long id, Integer status, Long updateBy);
    int updateBookingCount(Long id, Integer bookingCount);
    long countUnfinishedByCourseId(Long courseId, LocalDateTime now);
    long countByCourseId(Long courseId);
}
