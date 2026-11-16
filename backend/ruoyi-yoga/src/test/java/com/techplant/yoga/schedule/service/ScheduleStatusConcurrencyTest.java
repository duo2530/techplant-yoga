package com.techplant.yoga.schedule.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.spring.SpringUtils;
import com.techplant.yoga.classroom.dao.ClassroomDaoImpl;
import com.techplant.yoga.classroom.service.impl.ClassroomServiceImpl;
import com.techplant.yoga.coach.dao.CoachDaoImpl;
import com.techplant.yoga.coach.service.impl.CoachServiceImpl;
import com.techplant.yoga.common.testcontainers.AbstractYogaIntegrationTest;
import com.techplant.yoga.common.testcontainers.YogaMyBatisTestConfig;
import com.techplant.yoga.common.testcontainers.YogaSchema;
import com.techplant.yoga.course.dao.CourseDaoImpl;
import com.techplant.yoga.course.service.impl.CourseServiceImpl;
import com.techplant.yoga.member.dao.MemberDaoImpl;
import com.techplant.yoga.member.dao.MemberLevelDictDaoImpl;
import com.techplant.yoga.member.service.impl.MemberServiceImpl;
import com.techplant.yoga.membercard.dao.MemberCardDaoImpl;
import com.techplant.yoga.membercard.dao.MemberCardTransactionDaoImpl;
import com.techplant.yoga.membercard.service.CardNoGenerator;
import com.techplant.yoga.membercard.service.impl.MemberCardServiceImpl;
import com.techplant.yoga.membercard.service.impl.MemberCardTransactionServiceImpl;
import com.techplant.yoga.reservation.dao.ReservationDaoImpl;
import com.techplant.yoga.reservation.service.ReservationService;
import com.techplant.yoga.reservation.service.impl.ReservationServiceImpl;
import com.techplant.yoga.schedule.dao.ScheduleDaoImpl;
import com.techplant.yoga.schedule.service.impl.ScheduleBookingServiceImpl;
import com.techplant.yoga.schedule.service.impl.ScheduleServiceImpl;
import com.techplant.yoga.store.dao.StoreDaoImpl;
import com.techplant.yoga.store.dao.StoreRegionDictDaoImpl;
import com.techplant.yoga.store.service.impl.StoreServiceImpl;

/**
 * 排课状态变更与删除的并发集成测试（Testcontainers MySQL，真实 Service 与 SQL）。
 *
 * <p>持有方事务先写入且不提交，竞争方读取后在排课行上等锁，确认进入锁等待后再让持有方提交。</p>
 */
@ContextConfiguration(classes = {YogaMyBatisTestConfig.class, SpringUtils.class, StoreDaoImpl.class,
    StoreRegionDictDaoImpl.class, StoreServiceImpl.class, ClassroomDaoImpl.class, ClassroomServiceImpl.class,
    CoachDaoImpl.class, CoachServiceImpl.class, CourseDaoImpl.class, CourseServiceImpl.class, MemberDaoImpl.class,
    MemberLevelDictDaoImpl.class, MemberServiceImpl.class, MemberCardDaoImpl.class, MemberCardTransactionDaoImpl.class,
    MemberCardTransactionServiceImpl.class, CardNoGenerator.class, MemberCardServiceImpl.class, ScheduleDaoImpl.class,
    ScheduleBookingServiceImpl.class, ScheduleServiceImpl.class, ReservationDaoImpl.class,
    ReservationServiceImpl.class})
@DisplayName("排课状态变更并发（Testcontainers MySQL）")
class ScheduleStatusConcurrencyTest extends AbstractYogaIntegrationTest {
    private static final Long STORE_ID = 1000L;

    private static final Long COURSE_ID = 3000L;

    private static final Long COACH_ID = 4000L;

    private static final Long CLASSROOM_ID = 5000L;

    private static final Long SCHEDULE_ID = 8000L;

    private static final int PENDING = 1;

    private static final int PUBLISHED = 2;

    private static final int CANCELLED = 3;

    private static final String RESULT_OK = "OK";

    @Autowired
    private ScheduleService scheduleService;

    @Autowired
    private ReservationService reservationService;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void resetTables() {
        YogaSchema.reset(dataSource, "t_reservation", "t_member_card_transaction", "t_member_card", "t_schedule",
                "t_course", "t_coach", "t_classroom", "t_store", "t_member", "sys_dict_data");
        jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.update("INSERT INTO t_store (id, store_no, name, store_type, region_code, phone, business_hours,"
                + " address, create_by, create_time, update_by, update_time) VALUES (?, '0001', '徐汇店', 1, '310104',"
                + " '021-12345678', '周一至周日 09:00-22:00', '漕溪北路 1 号', 1, NOW(), 1, NOW())", STORE_ID);
        jdbcTemplate.update("INSERT INTO t_classroom (id, store_id, name, create_by, create_time, update_by,"
                + " update_time) VALUES (?, ?, '大教室', 1, NOW(), 1, NOW())", CLASSROOM_ID, STORE_ID);
        jdbcTemplate.update("INSERT INTO t_coach (id, name, create_by, create_time, update_by, update_time)"
                + " VALUES (?, '林教练', 1, NOW(), 1, NOW())", COACH_ID);
        jdbcTemplate.update("INSERT INTO t_course (id, name, course_type, difficulty, create_by, create_time,"
                + " update_by, update_time) VALUES (?, '哈他瑜伽', 1, 2, 1, NOW(), 1, NOW())", COURSE_ID);
    }

    @Test
    @DisplayName("取消先提交、下架后到：下架被拒绝，排课保持已取消")
    void unpublish_whenCancelCommitsFirst_shouldKeepCancelled() throws Exception {
        insertSchedule(LocalDateTime.now().plusDays(1), PUBLISHED);

        String result = runWhileHolding(() -> scheduleService.changeStatus(SCHEDULE_ID, CANCELLED),
                () -> scheduleService.changeStatus(SCHEDULE_ID, PENDING));

        assertEquals("409:已取消的排课不可再变更状态", result);
        assertEquals(Integer.valueOf(CANCELLED), scheduleStatus());
    }

    @Test
    @DisplayName("人数不足自动取消先持锁、下架后到：下架被拒绝，排课保持已取消")
    void unpublish_whenAutoCancelHoldsLockFirst_shouldKeepCancelled() throws Exception {
        insertSchedule(LocalDateTime.now().withNano(0).plusMinutes(60), PUBLISHED);

        String result = runWhileHolding(
                () -> assertTrue(reservationService.cancelUnderstaffedSchedule(SCHEDULE_ID), "自动取消应命中"),
                () -> scheduleService.changeStatus(SCHEDULE_ID, PENDING));

        assertEquals("409:已取消的排课不可再变更状态", result);
        assertEquals(Integer.valueOf(CANCELLED), scheduleStatus());
    }

    @Test
    @DisplayName("上架先提交、删除后到：删除被拒绝，已上架排课保留")
    void delete_whenPublishCommitsFirst_shouldKeepPublished() throws Exception {
        insertSchedule(LocalDateTime.now().plusDays(1), PENDING);

        String result = runWhileHolding(() -> scheduleService.changeStatus(SCHEDULE_ID, PUBLISHED),
                () -> scheduleService.delete(SCHEDULE_ID));

        assertEquals("409:已上架的排课须先下架才能删除", result);
        assertEquals(1, countOf("SELECT COUNT(*) FROM t_schedule WHERE id = ?", SCHEDULE_ID), "排课未被删除");
        assertEquals(Integer.valueOf(PUBLISHED), scheduleStatus());
    }

    /** 持有方在事务内完成写入后不提交；竞争方进入排课行锁等待后放行持有方，返回竞争方结果 */
    private String runWhileHolding(Runnable holder, Runnable contender) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch holderApplied = new CountDownLatch(1);
        CountDownLatch releaseHolder = new CountDownLatch(1);
        try {
            Future<?> holderFuture = pool.submit(() -> new TransactionTemplate(transactionManager).execute(s -> {
                holder.run();
                holderApplied.countDown();
                awaitLatch(releaseHolder);
                return null;
            }));
            assertTrue(holderApplied.await(10, TimeUnit.SECONDS), "持有方应完成写入");
            Future<String> contenderFuture = pool.submit(() -> {
                try {
                    contender.run();
                    return RESULT_OK;
                } catch (ServiceException e) {
                    return e.getCode() + ":" + e.getMessage();
                }
            });
            awaitContenderBlocked();
            releaseHolder.countDown();
            holderFuture.get(30, TimeUnit.SECONDS);
            return contenderFuture.get(30, TimeUnit.SECONDS);
        } finally {
            releaseHolder.countDown();
            pool.shutdownNow();
            assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS), "测试线程应结束，避免影响后续数据清理");
        }
    }

    /** 持有方连接空闲，另有访问排课表的语句已执行超过 1 秒，即竞争方在行锁上等待 */
    private void awaitContenderBlocked() throws InterruptedException {
        long deadline = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < deadline) {
            if (countOf("SELECT COUNT(*) FROM information_schema.PROCESSLIST WHERE ID <> CONNECTION_ID()"
                    + " AND COMMAND = 'Query' AND TIME >= 1 AND INFO LIKE '%t_schedule%'") > 0) {
                return;
            }
            Thread.sleep(20);
        }
        throw new AssertionError("竞争方应在排课行锁上等待");
    }

    private void awaitLatch(CountDownLatch latch) {
        try {
            assertTrue(latch.await(10, TimeUnit.SECONDS), "等待并发测试阶段超时");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private void insertSchedule(LocalDateTime startTime, int status) {
        jdbcTemplate.update("INSERT INTO t_schedule (id, store_id, course_id, course_type, coach_id, classroom_id,"
                + " schedule_date, start_time, end_time, max_persons, min_persons, booked_persons, status, create_by,"
                + " create_time, update_by, update_time) VALUES (?, ?, ?, 1, ?, ?, ?, ?, ?, 5, 3, 0, ?, 1, NOW(), 1,"
                + " NOW())", SCHEDULE_ID, STORE_ID, COURSE_ID, COACH_ID, CLASSROOM_ID, startTime.toLocalDate(),
                startTime, startTime.plusMinutes(60), status);
    }

    private Integer scheduleStatus() {
        return jdbcTemplate.queryForObject("SELECT status FROM t_schedule WHERE id = ?", Integer.class, SCHEDULE_ID);
    }

    private int countOf(String sql, Object... args) {
        Integer value = jdbcTemplate.queryForObject(sql, Integer.class, args);
        return value == null ? 0 : value;
    }
}
