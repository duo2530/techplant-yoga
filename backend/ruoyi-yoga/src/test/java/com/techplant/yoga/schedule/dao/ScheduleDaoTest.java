package com.techplant.yoga.schedule.dao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.techplant.yoga.common.testcontainers.AbstractYogaIntegrationTest;
import com.techplant.yoga.common.testcontainers.YogaMyBatisTestConfig;
import com.techplant.yoga.common.testcontainers.YogaSchema;
import com.techplant.yoga.schedule.domain.ScheduleDO;
import com.techplant.yoga.schedule.query.PublicScheduleQuery;
import com.techplant.yoga.schedule.query.ScheduleQuery;

/** 排课数据访问的**真实 SQL 口径**集成测试（Testcontainers MySQL，表结构取自 backend/sql/yoga.sql，不连开发库）。 */
@ContextConfiguration(classes = { YogaMyBatisTestConfig.class, ScheduleDaoImpl.class })
@DisplayName("ScheduleDao 集成测试（Testcontainers MySQL：状态口径 / 冲突校验 / 稳定排序）")
class ScheduleDaoTest extends AbstractYogaIntegrationTest
{
    private static final Long STORE_ID = 100L;

    private static final Long OTHER_STORE_ID = 200L;

    private static final Long COURSE_ID = 300L;

    private static final Long OTHER_COURSE_ID = 301L;

    private static final Long COACH_ID = 400L;

    private static final Long CLASSROOM_ID = 500L;

    private static final int COURSE_TYPE_GROUP = 1;

    private static final int COURSE_TYPE_FEATURE = 4;

    @Autowired
    private ScheduleDao scheduleDao;

    @BeforeEach
    void resetTable()
    {
        YogaSchema.reset(dataSource, "t_schedule");
    }

    @Test
    @DisplayName("四个 countActiveByXxx 共用口径：end_time > NOW() AND status IN (1,2)，已结束与已取消都不阻塞")
    void countActive_shouldShareOneConditionInSql() throws Exception
    {
        LocalDateTime now = LocalDateTime.now();
        LocalDate today = LocalDate.now();
        // 未结束（end_time > now）且状态 1/2 → 计数
        insertRow(9001L, STORE_ID, COURSE_ID, COURSE_TYPE_GROUP, COACH_ID, CLASSROOM_ID, today,
                now.plusHours(1), now.plusHours(2), 1);
        insertRow(9002L, STORE_ID, COURSE_ID, COURSE_TYPE_GROUP, COACH_ID, CLASSROOM_ID, today,
                now.plusHours(3), now.plusHours(4), 2);
        // status=2 但已结束（end_time <= now）→ 不计数
        insertRow(9003L, STORE_ID, COURSE_ID, COURSE_TYPE_GROUP, COACH_ID, CLASSROOM_ID, today,
                now.minusHours(2), now.minusHours(1), 2);
        // status=3 已取消，即使未结束 → 不计数
        insertRow(9004L, STORE_ID, COURSE_ID, COURSE_TYPE_GROUP, COACH_ID, CLASSROOM_ID, today,
                now.plusHours(5), now.plusHours(6), 3);
        // status=1 但已结束（end_time <= now）→ 不计数（证明时间条件真的进了 SQL）
        insertRow(9005L, STORE_ID, COURSE_ID, COURSE_TYPE_GROUP, COACH_ID, CLASSROOM_ID, today,
                now.minusHours(4), now.minusHours(3), 1);

        assertEquals(2L, scheduleDao.countActiveByStoreId(STORE_ID));
        assertEquals(2L, scheduleDao.countActiveByClassroomId(CLASSROOM_ID));
        assertEquals(2L, scheduleDao.countActiveByCoachId(COACH_ID));
        assertEquals(2L, scheduleDao.countActiveByCourseId(COURSE_ID));

        // 其它对象没有任何未结束排课 → 0（门禁放行）
        assertEquals(0L, scheduleDao.countActiveByStoreId(OTHER_STORE_ID));
        assertEquals(0L, scheduleDao.countActiveByCourseId(OTHER_COURSE_ID));
    }

    @Test
    @DisplayName("用户端列表：固定 status<>1 ＋ 门店 ＋ 课种快照 ＋ schedule_date，且排序 start_time ASC, id ASC")
    void selectPublicPage_shouldApplyFixedFiltersAndStableOrder() throws Exception
    {
        LocalDate today = LocalDate.now();
        LocalDate tomorrow = today.plusDays(1);
        LocalDateTime base = today.atTime(9, 0);
        // status=1 待上架 → 用户端不可见
        insertRow(9101L, STORE_ID, COURSE_ID, COURSE_TYPE_GROUP, COACH_ID, CLASSROOM_ID, today,
                base, base.plusHours(1), 1);
        // 可见（已上架）：10:00
        insertRow(9102L, STORE_ID, COURSE_ID, COURSE_TYPE_GROUP, COACH_ID, CLASSROOM_ID, today,
                base.plusHours(1), base.plusHours(2), 2);
        // 可见（已取消）：11:00，排在 9102 之后
        insertRow(9103L, STORE_ID, COURSE_ID, COURSE_TYPE_GROUP, COACH_ID, CLASSROOM_ID, today,
                base.plusHours(2), base.plusHours(3), 3);
        // 别的日期 → 不返回（日期筛的是派生列 schedule_date）
        insertRow(9104L, STORE_ID, COURSE_ID, COURSE_TYPE_GROUP, COACH_ID, CLASSROOM_ID, tomorrow,
                base.plusHours(4), base.plusHours(5), 2);
        // 别的课种 → 不返回（课种筛的是快照列 course_type）
        insertRow(9105L, STORE_ID, COURSE_ID, COURSE_TYPE_FEATURE, COACH_ID, CLASSROOM_ID, today,
                base.plusHours(6), base.plusHours(7), 2);

        PublicScheduleQuery query = new PublicScheduleQuery();
        query.setStoreId(STORE_ID);
        query.setCourseType(COURSE_TYPE_GROUP);
        query.setDate(today.toString());

        IPage<ScheduleDO> page = scheduleDao.selectPublicPage(query, today);

        List<ScheduleDO> records = page.getRecords();
        assertEquals(2L, page.getTotal(), "只应返回「非待上架 ＋ 本店 ＋ 本课种 ＋ 本日期」的 2 条");
        assertEquals(9102L, records.get(0).getId(), "start_time 早的排前面");
        assertEquals(9103L, records.get(1).getId());
    }

    @Test
    @DisplayName("用户端详情：未删除且 status<>1 才可见；待上架与不存在都查不到（同一响应）")
    void selectVisibleById_shouldHidePendingRows() throws Exception
    {
        LocalDate today = LocalDate.now();
        LocalDateTime base = today.atTime(9, 0);
        insertRow(9201L, STORE_ID, COURSE_ID, COURSE_TYPE_GROUP, COACH_ID, CLASSROOM_ID, today,
                base, base.plusHours(1), 1);
        insertRow(9202L, STORE_ID, COURSE_ID, COURSE_TYPE_GROUP, COACH_ID, CLASSROOM_ID, today,
                base.plusHours(1), base.plusHours(2), 2);
        insertRow(9203L, STORE_ID, COURSE_ID, COURSE_TYPE_GROUP, COACH_ID, CLASSROOM_ID, today,
                base.plusHours(2), base.plusHours(3), 3);

        assertNull(scheduleDao.selectVisibleById(9201L), "待上架对用户端不可见 → 按不存在处理");
        assertNull(scheduleDao.selectVisibleById(9999L), "不存在 → null");
        assertNotNull(scheduleDao.selectVisibleById(9202L), "已上架可见");
        assertNotNull(scheduleDao.selectVisibleById(9203L), "已取消可见（卡片置灰但能进详情）");
    }

    @Test
    @DisplayName("删除是物理删除：DELETE 之后行真的没了")
    void deleteById_shouldPhysicallyRemoveRow() throws Exception
    {
        LocalDate today = LocalDate.now();
        LocalDateTime base = today.atTime(9, 0);
        insertRow(9301L, STORE_ID, COURSE_ID, COURSE_TYPE_GROUP, COACH_ID, CLASSROOM_ID, today,
                base, base.plusHours(1), 1);
        assertNotNull(scheduleDao.selectById(9301L));

        assertEquals(1, scheduleDao.deleteById(9301L));

        assertNull(scheduleDao.selectById(9301L), "物理删除后按主键查不到");
        assertEquals(0L, rawCountById(9301L), "数据库里该行确实不存在（不是逻辑删除）");
    }

    @Test
    @DisplayName("修改只更新业务列：status 与 booked_persons 不被写入")
    void updateById_shouldNotTouchStatusAndBookedPersons() throws Exception
    {
        LocalDate today = LocalDate.now();
        LocalDateTime base = today.atTime(9, 0);
        insertRow(9401L, STORE_ID, COURSE_ID, COURSE_TYPE_GROUP, COACH_ID, CLASSROOM_ID, today,
                base, base.plusHours(1), 1);

        ScheduleDO update = new ScheduleDO();
        update.setId(9401L);
        update.setStoreId(STORE_ID);
        update.setCourseId(COURSE_ID);
        update.setCourseType(COURSE_TYPE_FEATURE);
        update.setCoachId(COACH_ID);
        update.setClassroomId(CLASSROOM_ID);
        update.setScheduleDate(today.plusDays(1));
        update.setStartTime(today.plusDays(1).atTime(15, 0));
        update.setEndTime(today.plusDays(1).atTime(16, 0));
        update.setMaxPersons(30);
        update.setMinPersons(3);
        // 故意塞进来，验证不会被写库
        update.setStatus(3);
        update.setBookedPersons(9);
        update.setUpdateBy(1L);

        assertEquals(1, scheduleDao.updateById(update));

        ScheduleDO latest = scheduleDao.selectById(9401L);
        assertEquals(Integer.valueOf(1), latest.getStatus(), "status 只能走状态流转接口，修改不得触碰");
        assertEquals(Integer.valueOf(0), latest.getBookedPersons(), "booked_persons 只属于预约模块");
        assertEquals(Integer.valueOf(30), latest.getMaxPersons());
        assertEquals(Integer.valueOf(3), latest.getMinPersons());
        assertEquals(today.plusDays(1), latest.getScheduleDate(), "开始时间变了，派生日期同步刷新");
        assertEquals(Integer.valueOf(COURSE_TYPE_FEATURE), latest.getCourseType());
    }

    @Test
    @DisplayName("完全同时段校验：同门店＋同课程＋完全相同的起止时间（排除已取消、排除自身）")
    void existsSameSlot_shouldExcludeCancelledAndSelf() throws Exception
    {
        LocalDate today = LocalDate.now();
        LocalDateTime start = today.atTime(19, 0);
        LocalDateTime end = today.atTime(20, 0);
        insertRow(9501L, STORE_ID, COURSE_ID, COURSE_TYPE_GROUP, COACH_ID, CLASSROOM_ID, today, start, end, 2);

        assertTrue(scheduleDao.existsSameSlot(STORE_ID, COURSE_ID, start, end, null), "完全相同起止时间应命中");
        assertFalse(scheduleDao.existsSameSlot(STORE_ID, COURSE_ID, start, end, 9501L), "修改时要排除自身");
        assertFalse(scheduleDao.existsSameSlot(STORE_ID, COURSE_ID, start, end.plusMinutes(30), null), "结束时间不同不算");
        assertFalse(scheduleDao.existsSameSlot(OTHER_STORE_ID, COURSE_ID, start, end, null), "不同门店不算");

        // 已取消（3）不参与冲突判定：取消后同一时段还能排回来
        insertRow(9502L, STORE_ID, OTHER_COURSE_ID, COURSE_TYPE_GROUP, COACH_ID, CLASSROOM_ID, today, start, end, 3);
        assertFalse(scheduleDao.existsSameSlot(STORE_ID, OTHER_COURSE_ID, start, end, null), "已取消的排课不阻塞同店同课程");
    }

    @Test
    @DisplayName("教练区间相交校验：start_time < :end AND end_time > :start（相邻不算相交，排除自身与已取消）")
    void existsCoachOverlap_shouldUseIntervalIntersection() throws Exception
    {
        LocalDate today = LocalDate.now();
        LocalDateTime start = today.atTime(10, 0);
        LocalDateTime end = today.atTime(11, 0);
        insertRow(9601L, STORE_ID, COURSE_ID, COURSE_TYPE_GROUP, COACH_ID, CLASSROOM_ID, today, start, end, 2);

        assertTrue(scheduleDao.existsCoachOverlap(COACH_ID, today.atTime(10, 30), today.atTime(11, 30), null), "部分相交");
        assertTrue(scheduleDao.existsCoachOverlap(COACH_ID, today.atTime(9, 0), today.atTime(12, 0), null), "完全包含");
        assertTrue(scheduleDao.existsCoachOverlap(COACH_ID, today.atTime(10, 30), today.atTime(10, 45), null), "被包含");
        assertFalse(scheduleDao.existsCoachOverlap(COACH_ID, end, today.atTime(12, 0), null), "首尾相接不算相交");
        assertFalse(scheduleDao.existsCoachOverlap(COACH_ID, today.atTime(9, 0), start, null), "首尾相接不算相交");
        assertFalse(scheduleDao.existsCoachOverlap(COACH_ID, today.atTime(10, 30), today.atTime(11, 30), 9601L),
                "修改时要排除自身");

        // 已取消的排课不参与教练冲突判定
        insertRow(9602L, STORE_ID, COURSE_ID, COURSE_TYPE_GROUP, 999L, CLASSROOM_ID, today, start, end, 3);
        assertFalse(scheduleDao.existsCoachOverlap(999L, start, end, null), "已取消的排课不阻塞该教练时段");
    }

    @Test
    @DisplayName("管理端列表：日期区间按派生列 schedule_date 筛，排序 start_time ASC, id ASC")
    void selectPage_shouldFilterByScheduleDateAndKeepStableOrder() throws Exception
    {
        LocalDate today = LocalDate.now();
        LocalDate tomorrow = today.plusDays(1);
        // 同一天两条，故意让 start_time 早的 id 更大
        insertRow(9702L, STORE_ID, COURSE_ID, COURSE_TYPE_GROUP, COACH_ID, CLASSROOM_ID, tomorrow,
                tomorrow.atTime(15, 0), tomorrow.atTime(16, 0), 1);
        insertRow(9701L, STORE_ID, COURSE_ID, COURSE_TYPE_GROUP, COACH_ID, CLASSROOM_ID, tomorrow,
                tomorrow.atTime(9, 0), tomorrow.atTime(10, 0), 1);
        insertRow(9703L, STORE_ID, COURSE_ID, COURSE_TYPE_GROUP, COACH_ID, CLASSROOM_ID, today,
                today.atTime(9, 0), today.atTime(10, 0), 1);

        ScheduleQuery query = new ScheduleQuery();
        query.setStartDate(tomorrow);
        query.setEndDate(tomorrow);

        IPage<ScheduleDO> page = scheduleDao.selectPage(query);

        assertEquals(2L, page.getTotal(), "日期区间按 schedule_date 筛，只命中明天那两条");
        assertEquals(9701L, page.getRecords().get(0).getId(), "start_time 早的排前面");
        assertEquals(9702L, page.getRecords().get(1).getId(), "start_time 相同时按 id ASC（稳定排序）");
    }

    /** 直插测试数据（避开审计填充与雪花 ID，断言才稳定） */
    private void insertRow(Long id, Long storeId, Long courseId, int courseType, Long coachId, Long classroomId,
            LocalDate scheduleDate, LocalDateTime startTime, LocalDateTime endTime, int status) throws Exception
    {
        String sql = "INSERT INTO t_schedule (id, store_id, course_id, course_type, coach_id, classroom_id,"
                + " schedule_date, start_time, end_time, max_persons, min_persons, booked_persons, status)"
                + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql))
        {
            statement.setLong(1, id);
            statement.setLong(2, storeId);
            statement.setLong(3, courseId);
            statement.setInt(4, courseType);
            statement.setLong(5, coachId);
            statement.setLong(6, classroomId);
            statement.setDate(7, Date.valueOf(scheduleDate));
            statement.setTimestamp(8, Timestamp.valueOf(startTime));
            statement.setTimestamp(9, Timestamp.valueOf(endTime));
            statement.setInt(10, 20);
            statement.setInt(11, 2);
            statement.setInt(12, 0);
            statement.setInt(13, status);
            statement.executeUpdate();
        }
    }

    private long rawCountById(Long id) throws Exception
    {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement("SELECT COUNT(*) FROM t_schedule WHERE id = ?"))
        {
            statement.setLong(1, id);
            try (ResultSet resultSet = statement.executeQuery())
            {
                resultSet.next();
                return resultSet.getLong(1);
            }
        }
    }
}
