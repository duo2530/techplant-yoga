package com.techplant.yoga.schedule.dao;

import java.time.LocalDate;
import java.time.LocalDateTime;
import org.springframework.stereotype.Repository;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.techplant.yoga.schedule.domain.ScheduleDO;
import com.techplant.yoga.schedule.enums.ScheduleStatusEnum;
import com.techplant.yoga.schedule.mapper.ScheduleMapper;
import com.techplant.yoga.schedule.query.PublicScheduleQuery;
import com.techplant.yoga.schedule.query.ScheduleQuery;

/**
 * 排课数据访问实现（排课管理详细设计 §3.4 第 2、3、7 条）。
 *
 * <p><b>本表没有 {@code deleted} 列</b>：{@code deleteById} 是真正的 {@code DELETE}，
 * 查询也不用追加任何逻辑删除条件。</p>
 */
@Repository
public class ScheduleDaoImpl implements ScheduleDao
{
    private final ScheduleMapper scheduleMapper;

    public ScheduleDaoImpl(ScheduleMapper scheduleMapper)
    {
        this.scheduleMapper = scheduleMapper;
    }

    @Override
    public IPage<ScheduleDO> selectPage(ScheduleQuery query)
    {
        LambdaQueryWrapper<ScheduleDO> wrapper = Wrappers.<ScheduleDO>lambdaQuery();
        wrapper.eq(query.getStoreId() != null, ScheduleDO::getStoreId, query.getStoreId());
        wrapper.eq(query.getCourseId() != null, ScheduleDO::getCourseId, query.getCourseId());
        wrapper.eq(query.getCourseType() != null, ScheduleDO::getCourseType, query.getCourseType());
        wrapper.eq(query.getCoachId() != null, ScheduleDO::getCoachId, query.getCoachId());
        wrapper.eq(query.getStatus() != null, ScheduleDO::getStatus, query.getStatus());
        // 日期区间一律筛派生列 schedule_date，不用 start_time 拼（BR-排课-022）
        wrapper.ge(query.getStartDate() != null, ScheduleDO::getScheduleDate, query.getStartDate());
        wrapper.le(query.getEndDate() != null, ScheduleDO::getScheduleDate, query.getEndDate());
        // 稳定排序：分页不重复、不漏项
        wrapper.orderByAsc(ScheduleDO::getStartTime);
        wrapper.orderByAsc(ScheduleDO::getId);
        return scheduleMapper.selectPage(
                new Page<ScheduleDO>(query.pageNumOrDefault(), query.pageSizeOrDefault()), wrapper);
    }

    @Override
    public IPage<ScheduleDO> selectPublicPage(PublicScheduleQuery query, LocalDate scheduleDate)
    {
        // 可见口径（status <> 1）与详情共用 buildVisibleCondition()
        LambdaQueryWrapper<ScheduleDO> wrapper = buildVisibleCondition();
        wrapper.eq(ScheduleDO::getStoreId, query.getStoreId());
        // 课种筛快照列，不回读课程（BR-排课-021）
        wrapper.eq(ScheduleDO::getCourseType, query.getCourseType());
        // 日期筛派生列 schedule_date，不用 start_time 拼（BR-排课-022）
        wrapper.eq(ScheduleDO::getScheduleDate, scheduleDate);
        wrapper.orderByAsc(ScheduleDO::getStartTime);
        wrapper.orderByAsc(ScheduleDO::getId);
        return scheduleMapper.selectPage(
                new Page<ScheduleDO>(query.pageNumOrDefault(), query.pageSizeOrDefault()), wrapper);
    }

    @Override
    public ScheduleDO selectById(Long id)
    {
        return scheduleMapper.selectById(id);
    }

    @Override
    public ScheduleDO selectVisibleById(Long id)
    {
        return scheduleMapper.selectOne(buildVisibleCondition().eq(ScheduleDO::getId, id));
    }

    @Override
    public int insert(ScheduleDO schedule)
    {
        return scheduleMapper.insert(schedule);
    }

    @Override
    public int updateById(ScheduleDO schedule)
    {
        // 显式 SET 业务列：不含 status（走状态流转接口）与 booked_persons（只属于预约模块）
        return scheduleMapper.update(null, Wrappers.<ScheduleDO>lambdaUpdate()
                .eq(ScheduleDO::getId, schedule.getId())
                .set(ScheduleDO::getStoreId, schedule.getStoreId())
                .set(ScheduleDO::getCourseId, schedule.getCourseId())
                .set(ScheduleDO::getCourseType, schedule.getCourseType())
                .set(ScheduleDO::getCoachId, schedule.getCoachId())
                .set(ScheduleDO::getClassroomId, schedule.getClassroomId())
                .set(ScheduleDO::getScheduleDate, schedule.getScheduleDate())
                .set(ScheduleDO::getStartTime, schedule.getStartTime())
                .set(ScheduleDO::getEndTime, schedule.getEndTime())
                .set(ScheduleDO::getMaxPersons, schedule.getMaxPersons())
                .set(ScheduleDO::getMinPersons, schedule.getMinPersons())
                .set(ScheduleDO::getUpdateBy, schedule.getUpdateBy())
                .set(ScheduleDO::getUpdateTime, LocalDateTime.now()));
    }

    @Override
    public int updateStatus(Long id, Integer status, Long updateBy)
    {
        return scheduleMapper.update(null, Wrappers.<ScheduleDO>lambdaUpdate()
                .eq(ScheduleDO::getId, id)
                .set(ScheduleDO::getStatus, status)
                .set(ScheduleDO::getUpdateBy, updateBy)
                .set(ScheduleDO::getUpdateTime, LocalDateTime.now()));
    }

    @Override
    public int deleteById(Long id)
    {
        // 物理删除：本表没有 @TableLogic，也没有 deleted 列
        return scheduleMapper.deleteById(id);
    }

    @Override
    public boolean existsSameSlot(Long storeId, Long courseId, LocalDateTime startTime, LocalDateTime endTime,
            Long excludeId)
    {
        LambdaQueryWrapper<ScheduleDO> wrapper = Wrappers.<ScheduleDO>lambdaQuery();
        wrapper.eq(ScheduleDO::getStoreId, storeId);
        wrapper.eq(ScheduleDO::getCourseId, courseId);
        wrapper.eq(ScheduleDO::getStartTime, startTime);
        wrapper.eq(ScheduleDO::getEndTime, endTime);
        // 已取消（3）不参与冲突判定：取消后同一时段还能排回来（§1.2.3 第 9 条）
        wrapper.ne(ScheduleDO::getStatus, ScheduleStatusEnum.CANCELLED.getCode());
        wrapper.ne(excludeId != null, ScheduleDO::getId, excludeId);
        return scheduleMapper.selectCount(wrapper) > 0;
    }

    @Override
    public boolean existsCoachOverlap(Long coachId, LocalDateTime startTime, LocalDateTime endTime, Long excludeId)
    {
        LambdaQueryWrapper<ScheduleDO> wrapper = Wrappers.<ScheduleDO>lambdaQuery();
        wrapper.eq(ScheduleDO::getCoachId, coachId);
        wrapper.ne(ScheduleDO::getStatus, ScheduleStatusEnum.CANCELLED.getCode());
        // 区间相交：start_time < :end AND end_time > :start
        wrapper.lt(ScheduleDO::getStartTime, endTime);
        wrapper.gt(ScheduleDO::getEndTime, startTime);
        wrapper.ne(excludeId != null, ScheduleDO::getId, excludeId);
        return scheduleMapper.selectCount(wrapper) > 0;
    }

    @Override
    public long countActiveByStoreId(Long storeId)
    {
        return scheduleMapper.selectCount(buildActiveCondition().eq(ScheduleDO::getStoreId, storeId));
    }

    @Override
    public long countActiveByClassroomId(Long classroomId)
    {
        return scheduleMapper.selectCount(buildActiveCondition().eq(ScheduleDO::getClassroomId, classroomId));
    }

    @Override
    public long countActiveByCoachId(Long coachId)
    {
        return scheduleMapper.selectCount(buildActiveCondition().eq(ScheduleDO::getCoachId, coachId));
    }

    @Override
    public long countActiveByCourseId(Long courseId)
    {
        return scheduleMapper.selectCount(buildActiveCondition().eq(ScheduleDO::getCourseId, courseId));
    }

    /**
     * 删除门禁口径：{@code end_time > NOW() AND status IN (1, 2)}（§3.4 第 2、7 条）
     *
     * <p><b>这是「已结束」表达式的唯一例外</b>：刻意把「未结束」写进 <b>SQL 条件</b>，
     * 不能把行拉回内存再筛；四个 {@code countActiveByXxx} 共用本方法，口径不允许各自偏离。</p>
     *
     * <p>已取消（3）与已结束的历史排课不阻塞删除（{@code BR-全局-007}）。</p>
     */
    private LambdaQueryWrapper<ScheduleDO> buildActiveCondition()
    {
        return Wrappers.<ScheduleDO>lambdaQuery()
                .apply("end_time > NOW()")
                .in(ScheduleDO::getStatus, ScheduleStatusEnum.PENDING.getCode(),
                        ScheduleStatusEnum.PUBLISHED.getCode());
    }

    /**
     * 用户端可见口径：{@code status <> 1}（非「待上架」）
     *
     * <p>列表与详情**共用本方法**，不要一处写 {@code <> 1}、另一处写 {@code IN (2,3)}
     * （《用户端接口详细设计》§5.3 第 1 条）。这条口径与删除门禁口径是**两套不同条件**，不要合并。</p>
     */
    private LambdaQueryWrapper<ScheduleDO> buildVisibleCondition()
    {
        return Wrappers.<ScheduleDO>lambdaQuery()
                .ne(ScheduleDO::getStatus, ScheduleStatusEnum.PENDING.getCode());
    }
}
