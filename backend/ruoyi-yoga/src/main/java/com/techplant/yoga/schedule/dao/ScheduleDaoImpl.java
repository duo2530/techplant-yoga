package com.techplant.yoga.schedule.dao;

import java.time.LocalDateTime;
import org.springframework.stereotype.Repository;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.techplant.yoga.schedule.domain.ScheduleDO;
import com.techplant.yoga.schedule.mapper.ScheduleMapper;
import com.techplant.yoga.schedule.query.ScheduleQuery;

/** 排班数据访问实现。 */
@Repository
public class ScheduleDaoImpl implements ScheduleDao
{
    private final ScheduleMapper scheduleMapper;
    public ScheduleDaoImpl(ScheduleMapper scheduleMapper) { this.scheduleMapper = scheduleMapper; }

    @Override
    public IPage<ScheduleDO> selectPage(ScheduleQuery query)
    {
        LambdaQueryWrapper<ScheduleDO> wrapper = Wrappers.<ScheduleDO>lambdaQuery();
        wrapper.select(ScheduleDO::getId, ScheduleDO::getStoreId, ScheduleDO::getCourseId,
                ScheduleDO::getStartTime, ScheduleDO::getEndTime, ScheduleDO::getCapacity,
                ScheduleDO::getBookingCount, ScheduleDO::getStatus, ScheduleDO::getCreateTime);
        wrapper.eq(query.getStoreId() != null, ScheduleDO::getStoreId, query.getStoreId());
        wrapper.eq(query.getCourseId() != null, ScheduleDO::getCourseId, query.getCourseId());
        wrapper.eq(query.getStatus() != null, ScheduleDO::getStatus, query.getStatus());
        wrapper.ge(query.getStartTimeFrom() != null, ScheduleDO::getStartTime, query.getStartTimeFrom());
        wrapper.le(query.getStartTimeTo() != null, ScheduleDO::getStartTime, query.getStartTimeTo());
        wrapper.orderByAsc(ScheduleDO::getStartTime);
        wrapper.orderByDesc(ScheduleDO::getId);
        return scheduleMapper.selectPage(new Page<ScheduleDO>(query.pageNumOrDefault(), query.pageSizeOrDefault()), wrapper);
    }

    @Override
    public ScheduleDO selectById(Long id) { return scheduleMapper.selectById(id); }

    @Override
    public ScheduleDO selectForUpdate(Long id)
    {
        LambdaQueryWrapper<ScheduleDO> wrapper = Wrappers.<ScheduleDO>lambdaQuery();
        wrapper.eq(ScheduleDO::getId, id).last("FOR UPDATE");
        return scheduleMapper.selectOne(wrapper);
    }

    @Override
    public boolean existsSameSlot(Long storeId, Long courseId, LocalDateTime startTime, LocalDateTime endTime,
            Long excludeId)
    {
        LambdaQueryWrapper<ScheduleDO> wrapper = Wrappers.<ScheduleDO>lambdaQuery();
        wrapper.eq(ScheduleDO::getStoreId, storeId).eq(ScheduleDO::getCourseId, courseId)
                .eq(ScheduleDO::getStartTime, startTime).eq(ScheduleDO::getEndTime, endTime)
                .ne(excludeId != null, ScheduleDO::getId, excludeId);
        return scheduleMapper.selectCount(wrapper) > 0;
    }

    @Override
    public int insert(ScheduleDO schedule) { return scheduleMapper.insert(schedule); }

    @Override
    public int updateById(ScheduleDO schedule)
    {
        return scheduleMapper.update(null, Wrappers.<ScheduleDO>lambdaUpdate().eq(ScheduleDO::getId, schedule.getId())
                .set(ScheduleDO::getStoreId, schedule.getStoreId()).set(ScheduleDO::getCourseId, schedule.getCourseId())
                .set(ScheduleDO::getStartTime, schedule.getStartTime()).set(ScheduleDO::getEndTime, schedule.getEndTime())
                .set(ScheduleDO::getCapacity, schedule.getCapacity()).set(ScheduleDO::getUpdateBy, schedule.getUpdateBy())
                .set(ScheduleDO::getUpdateTime, LocalDateTime.now()));
    }

    @Override
    public int updateStatus(Long id, Integer status, Long updateBy)
    {
        return scheduleMapper.update(null, Wrappers.<ScheduleDO>lambdaUpdate().eq(ScheduleDO::getId, id)
                .set(ScheduleDO::getStatus, status).set(ScheduleDO::getUpdateBy, updateBy)
                .set(ScheduleDO::getUpdateTime, LocalDateTime.now()));
    }

    @Override
    public int updateBookingCount(Long id, Integer bookingCount)
    {
        return scheduleMapper.update(null, Wrappers.<ScheduleDO>lambdaUpdate().eq(ScheduleDO::getId, id)
                .set(ScheduleDO::getBookingCount, bookingCount).set(ScheduleDO::getUpdateTime, LocalDateTime.now()));
    }

    @Override
    public long countUnfinishedByCourseId(Long courseId, LocalDateTime now)
    {
        LambdaQueryWrapper<ScheduleDO> wrapper = Wrappers.<ScheduleDO>lambdaQuery();
        wrapper.eq(ScheduleDO::getCourseId, courseId).eq(ScheduleDO::getStatus, 1)
                .gt(ScheduleDO::getEndTime, now);
        return scheduleMapper.selectCount(wrapper);
    }

    @Override
    public long countByCourseId(Long courseId)
    {
        return scheduleMapper.selectCount(Wrappers.<ScheduleDO>lambdaQuery().eq(ScheduleDO::getCourseId, courseId));
    }
}
