package com.techplant.yoga.schedule.dao;

import java.time.LocalDate;
import java.time.LocalDateTime;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.techplant.yoga.schedule.domain.ScheduleDO;
import com.techplant.yoga.schedule.query.PublicScheduleQuery;
import com.techplant.yoga.schedule.query.ScheduleQuery;

/**
 * 排课数据访问语义（排课管理详细设计 §3.2）。
 *
 * <p><b>两条口径在 SQL 里各写一处、都不出现在内存筛：</b></p>
 * <ul>
 *   <li><b>删除门禁（未结束）</b>：{@code end_time > NOW() AND status IN (1,2)} —— 四个
 *       {@code countActiveByXxx} 共用同一个私有条件构造（§3.4 第 2、7 条）；</li>
 *   <li><b>用户端可见</b>：{@code status <> 1} —— 用户端列表与用户端详情共用同一段条件（《用户端接口详细设计》§5.3 第 1 条）。</li>
 * </ul>
 *
 * <p>本表<b>物理删除</b>：{@code deleteById} 就是 {@code DELETE}，没有逻辑删除条件。</p>
 */
public interface ScheduleDao
{
    /** 管理端分页：日期区间筛 {@code schedule_date}，排序 {@code start_time ASC, id ASC} */
    IPage<ScheduleDO> selectPage(ScheduleQuery query);

    /**
     * 用户端分页：固定 {@code status <> 1} ＋ 门店 ＋ 课种快照 ＋ 上课日期
     *
     * @param query        查询条件（{@code date} 是原始字符串，由 service 解析后传入）
     * @param scheduleDate 解析后的上课日期
     */
    IPage<ScheduleDO> selectPublicPage(PublicScheduleQuery query, LocalDate scheduleDate);

    /** 按主键查（管理端，物理删除后查不到） */
    ScheduleDO selectById(Long id);

    /** 用户端详情：未删除且 {@code status <> 1} 才返回，否则 null（不区分「不存在」与「不可见」） */
    ScheduleDO selectVisibleById(Long id);

    /** 新增（主键与审计字段由公共机制填充） */
    int insert(ScheduleDO schedule);

    /** 修改：**显式指定更新列**，不含 {@code status} 与 {@code booked_persons}（§3.4 第 6 条） */
    int updateById(ScheduleDO schedule);

    /** 只更新 {@code status} 列（状态流转的唯一写入口） */
    int updateStatus(Long id, Integer status, Long updateBy);

    /** 物理删除 */
    int deleteById(Long id);

    /**
     * 同一门店 ＋ 同一课程 ＋ **完全相同的起止时间**（{@code status <> 3}）是否已存在
     *
     * @param excludeId 修改时排除自身；新增传 null
     */
    boolean existsSameSlot(Long storeId, Long courseId, LocalDateTime startTime, LocalDateTime endTime,
            Long excludeId);

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
