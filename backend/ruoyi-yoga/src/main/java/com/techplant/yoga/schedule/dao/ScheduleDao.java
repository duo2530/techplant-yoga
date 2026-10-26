package com.techplant.yoga.schedule.dao;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.techplant.yoga.schedule.domain.ScheduleDO;
import com.techplant.yoga.schedule.query.PublicScheduleQuery;
import com.techplant.yoga.schedule.query.ScheduleQuery;

/** 排课数据访问语义。 */
public interface ScheduleDao {
    /** 管理端分页：日期区间筛 {@code schedule_date}，排序 {@code start_time ASC, id ASC} */
    IPage<ScheduleDO> selectPage(ScheduleQuery query);

    /**
     * 用户端分页：固定 {@code status <> 1} ＋ 门店 ＋ 课种快照 ＋ 上课日期
     *
     * @param query        查询条件（{@code date} 是原始字符串，由 service 解析后传入）
     * @param scheduleDate 解析后的上课日期
     */
    IPage<ScheduleDO> selectPublicPage(PublicScheduleQuery query, LocalDate scheduleDate);

    /**
     * 教练页分页：固定 {@code status <> 1} ＋ 教练 ＋ 门店 ＋ 课种快照 ＋ 上课日期窗口（含首尾日）
     *
     * @param startDate 窗口首日（含）
     * @param endDate   窗口末日（含）
     */
    IPage<ScheduleDO> selectPublicCoachPage(Long coachId, Long storeId, Integer courseType, LocalDate startDate,
            LocalDate endDate, int pageNum, int pageSize);

    /**
     * 首页今日可约团课：门店 ＋ 团课快照 ＋ 当日 ＋ 已上架 ＋ 未开始 ＋ 仍有名额，按开始时间升序取前 N 条
     *
     * @param now   判定「未开始」的基准时间
     * @param limit 最多返回条数
     */
    List<ScheduleDO> selectTodayBookable(Long storeId, LocalDate today, LocalDateTime now, int limit);

    /** 按主键查（管理端，物理删除后查不到） */
    ScheduleDO selectById(Long id);

    /** 按主键集合批量查（预约列表批量装配课程与门店信息） */
    List<ScheduleDO> selectByIds(Collection<Long> ids);

    /** 按主键做当前读（{@code SELECT ... FOR UPDATE}）取排课；不存在返回 {@code null} */
    ScheduleDO selectByIdForUpdate(Long id);

    /** 用户端详情：未删除且 {@code status <> 1} 才返回，否则 null（不区分「不存在」与「不可见」） */
    ScheduleDO selectVisibleById(Long id);

    /** 新增（主键与审计字段由公共机制填充） */
    int insert(ScheduleDO schedule);

    /** 修改：**显式指定更新列**，不含 {@code status} 与 {@code booked_persons} */
    int updateById(ScheduleDO schedule);

    /** 只更新 {@code status} 列（状态流转的唯一写入口） */
    int updateStatus(Long id, Integer status, Long updateBy);

    /**
     * 写入已预约人数（只由预约事务的条件更新调用）
     *
     * @param bookedPersons 写入后的已预约人数；超过 {@code max_persons} 时不更新
     */
    int updateBookedPersons(Long id, Integer bookedPersons);

    /** 物理删除 */
    int deleteById(Long id);

    /**
     * 同一门店 ＋ 同一课程 ＋ **完全相同的起止时间**（{@code status <> 3}）是否已存在
     *
     * @param excludeId 修改时排除自身；新增传 null
     */
    boolean existsSameSlot(Long storeId, Long courseId, LocalDateTime startTime, LocalDateTime endTime, Long excludeId);

    /**
     * 同一教练**时间区间相交**（{@code status <> 3}）是否已存在
     *
     * <p>相交判定：{@code start_time < :end AND end_time > :start}。</p>
     *
     * @param excludeId 修改时排除自身；新增传 null
     */
    boolean existsCoachOverlap(Long coachId, LocalDateTime startTime, LocalDateTime endTime, Long excludeId);

    // ------------------------------------------------------------------
    // 四个主数据的删除门禁统计（跨模块调用；口径完全一致）
    // ------------------------------------------------------------------

    /** 门店删除门禁统计 */
    long countActiveByStoreId(Long storeId);

    /** 教室删除门禁统计 */
    long countActiveByClassroomId(Long classroomId);

    /** 教练删除门禁统计 */
    long countActiveByCoachId(Long coachId);

    /** 课程删除门禁统计 */
    long countActiveByCourseId(Long courseId);
}
