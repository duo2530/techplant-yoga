package com.techplant.yoga.schedule.task;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import com.ruoyi.common.constant.Constants;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.common.utils.spring.SpringUtils;
import com.ruoyi.quartz.domain.SysJob;
import com.ruoyi.quartz.util.JobInvokeUtil;
import com.ruoyi.quartz.util.ScheduleUtils;
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
import com.techplant.yoga.reservation.dto.ReservationCreateDTO;
import com.techplant.yoga.reservation.query.ReservationAdminQuery;
import com.techplant.yoga.reservation.query.ReservationPublicQuery;
import com.techplant.yoga.reservation.service.ReservationService;
import com.techplant.yoga.reservation.service.impl.ReservationServiceImpl;
import com.techplant.yoga.reservation.vo.ReservationAdminItemVO;
import com.techplant.yoga.reservation.vo.ReservationPublicItemVO;
import com.techplant.yoga.schedule.dao.ScheduleDaoImpl;
import com.techplant.yoga.schedule.query.PublicScheduleQuery;
import com.techplant.yoga.schedule.service.ScheduleBookingService;
import com.techplant.yoga.schedule.service.ScheduleService;
import com.techplant.yoga.schedule.service.impl.ScheduleBookingServiceImpl;
import com.techplant.yoga.schedule.service.impl.ScheduleServiceImpl;
import com.techplant.yoga.schedule.vo.ScheduleCardVO;
import com.techplant.yoga.store.dao.StoreDaoImpl;
import com.techplant.yoga.store.dao.StoreRegionDictDaoImpl;
import com.techplant.yoga.store.service.impl.StoreServiceImpl;

/**
 * 人数不足自动取消的集成测试（Testcontainers MySQL，真实 Service 与 SQL，不连开发库）。
 *
 * <p>预约经真实预约服务写入，扣次流水与占位由业务生成；定时任务按若依调用目标执行。</p>
 */
@ContextConfiguration(classes = {YogaMyBatisTestConfig.class, SpringUtils.class, StoreDaoImpl.class,
    StoreRegionDictDaoImpl.class, StoreServiceImpl.class, ClassroomDaoImpl.class, ClassroomServiceImpl.class,
    CoachDaoImpl.class, CoachServiceImpl.class, CourseDaoImpl.class, CourseServiceImpl.class, MemberDaoImpl.class,
    MemberLevelDictDaoImpl.class, MemberServiceImpl.class, MemberCardDaoImpl.class, MemberCardTransactionDaoImpl.class,
    MemberCardTransactionServiceImpl.class, CardNoGenerator.class, MemberCardServiceImpl.class, ScheduleDaoImpl.class,
    ScheduleBookingServiceImpl.class, ScheduleServiceImpl.class, ReservationDaoImpl.class, ReservationServiceImpl.class,
    ScheduleAutoCancelTask.class})
@DisplayName("人数不足自动取消定时任务（Testcontainers MySQL）")
class ScheduleAutoCancelTaskTest extends AbstractYogaIntegrationTest {
    private static final String INVOKE_TARGET = "yogaScheduleTask.cancelUnderstaffed";

    private static final Long STORE_ID = 1000L;

    private static final Long COURSE_ID = 3000L;

    private static final Long COACH_ID = 4000L;

    private static final Long CLASSROOM_ID = 5000L;

    private static final Long MEMBER_A = 6000L;

    private static final Long MEMBER_B = 6001L;

    private static final Long MEMBER_C = 6002L;

    private static final Long CARD_A = 7000L;

    private static final Long CARD_B = 7001L;

    private static final Long CARD_C = 7002L;

    private static final Long UNDERSTAFFED_ID = 8000L;

    private static final Long ENOUGH_ID = 8001L;

    private static final Long OUTSIDE_WINDOW_ID = 8002L;

    private static final Long STARTED_ID = 8003L;

    private static final Long PENDING_ID = 8004L;

    private static final Long CANCELLED_ID = 8005L;

    private static final Long SECOND_UNDERSTAFFED_ID = 8006L;

    private static final int CARD_TIMES = 12;

    private static final String RESULT_OK = "OK";

    @Autowired
    private ScheduleAutoCancelTask task;

    @Autowired
    private ReservationService reservationService;

    @Autowired
    private ScheduleBookingService scheduleBookingService;

    @Autowired
    private ScheduleService scheduleService;

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
        insertMember(MEMBER_A, "13800000001", "张三");
        insertMember(MEMBER_B, "13800000002", "李四");
        insertMember(MEMBER_C, "13800000003", "王五");
        insertCard(CARD_A, MEMBER_A, 1);
        insertCard(CARD_B, MEMBER_B, 2);
        insertCard(CARD_C, MEMBER_C, 1);

        LocalDateTime now = LocalDateTime.now().withNano(0);
        insertSchedule(UNDERSTAFFED_ID, now.plusMinutes(60), 3, 2);
        insertSchedule(ENOUGH_ID, now.plusMinutes(70), 2, 2);
        insertSchedule(OUTSIDE_WINDOW_ID, now.plusMinutes(125), 3, 2);
        insertSchedule(STARTED_ID, now.minusMinutes(10), 3, 2);
        insertSchedule(PENDING_ID, now.plusMinutes(80), 3, 1);
        insertSchedule(CANCELLED_ID, now.plusMinutes(90), 3, 3);
        insertSchedule(SECOND_UNDERSTAFFED_ID, now.plusMinutes(100), 3, 2);
    }

    @Test
    @DisplayName("人数不足且进入开课前 120 分钟：排课取消，已预约按原扣次返次，排队一并取消，占位清零")
    void cancelUnderstaffed_shouldCancelScheduleRefundAndCancelQueue() {
        book(MEMBER_A, CARD_A, UNDERSTAFFED_ID);
        book(MEMBER_B, CARD_B, UNDERSTAFFED_ID);
        Long queuedId = insertQueued(MEMBER_C, CARD_C, UNDERSTAFFED_ID);

        task.cancelUnderstaffed();

        assertEquals(Integer.valueOf(3), scheduleStatus(UNDERSTAFFED_ID), "排课改为已取消");
        assertEquals(Integer.valueOf(0), intOf("SELECT booked_persons FROM t_schedule WHERE id = ?", UNDERSTAFFED_ID));
        assertEquals(3, countOf("SELECT COUNT(*) FROM t_reservation WHERE schedule_id = ? AND status = 2"
                + " AND cancel_reason = 2 AND cancel_time IS NOT NULL", UNDERSTAFFED_ID), "预约与排队都按排课取消");
        assertEquals(Integer.valueOf(2), intOf("SELECT status FROM t_reservation WHERE id = ?", queuedId));
        assertEquals(Integer.valueOf(CARD_TIMES), remainingTimes(CARD_A), "次数卡按原扣次返还");
        assertEquals(1,
                countOf("SELECT COUNT(*) FROM t_member_card_transaction WHERE member_card_id = ?"
                        + " AND change_type = 2 AND change_times = 1 AND before_times = 11 AND after_times = 12",
                        CARD_A),
                "写入返次流水");
        assertEquals(0, countOf("SELECT COUNT(*) FROM t_member_card_transaction WHERE member_card_id IN (?, ?)", CARD_B,
                CARD_C), "年卡与排队不产生流水");
        assertAccountingConsistent();
    }

    @Test
    @DisplayName("人数足够、未进入窗口、已开始、待上架与已取消的排课都不处理")
    void cancelUnderstaffed_shouldLeaveOtherSchedulesUntouched() {
        book(MEMBER_A, CARD_A, ENOUGH_ID);
        book(MEMBER_B, CARD_B, ENOUGH_ID);
        book(MEMBER_A, CARD_A, OUTSIDE_WINDOW_ID);

        task.cancelUnderstaffed();

        assertEquals(Integer.valueOf(2), scheduleStatus(ENOUGH_ID), "达到最低开课人数");
        assertEquals(Integer.valueOf(2), intOf("SELECT booked_persons FROM t_schedule WHERE id = ?", ENOUGH_ID));
        assertEquals(Integer.valueOf(2), scheduleStatus(OUTSIDE_WINDOW_ID), "距开课超过 120 分钟");
        assertEquals(Integer.valueOf(2), scheduleStatus(STARTED_ID), "已开始");
        assertEquals(Integer.valueOf(1), scheduleStatus(PENDING_ID), "待上架");
        assertEquals(Integer.valueOf(3), scheduleStatus(CANCELLED_ID), "已取消");
        assertEquals(0, countOf("SELECT COUNT(*) FROM t_reservation WHERE status = 2"), "这些排课上的预约不受影响");
        assertEquals(Integer.valueOf(CARD_TIMES - 2), remainingTimes(CARD_A));
        assertAccountingConsistent();
    }

    @Test
    @DisplayName("重复执行收敛：第二轮没有候选，不重复返次")
    void cancelUnderstaffed_twice_shouldConverge() {
        book(MEMBER_A, CARD_A, UNDERSTAFFED_ID);

        task.cancelUnderstaffed();
        LocalDateTime now = LocalDateTime.now();
        assertFalse(scheduleBookingService.understaffedIds(now, now.plusMinutes(120)).contains(UNDERSTAFFED_ID),
                "已取消的排课不再是候选");
        assertFalse(reservationService.cancelUnderstaffedSchedule(UNDERSTAFFED_ID), "锁内复查后不再处理");
        task.cancelUnderstaffed();

        assertEquals(Integer.valueOf(CARD_TIMES), remainingTimes(CARD_A));
        assertEquals(1, countOf("SELECT COUNT(*) FROM t_member_card_transaction WHERE change_type = 2"));
        assertAccountingConsistent();
    }

    @Test
    @DisplayName("一节排课处理失败整体回滚，不影响其它排课；下一轮补上")
    void cancelUnderstaffed_whenOneScheduleFails_shouldContinueWithOthers() {
        book(MEMBER_A, CARD_A, UNDERSTAFFED_ID);
        book(MEMBER_C, CARD_C, SECOND_UNDERSTAFFED_ID);
        Long failingId = jdbcTemplate.queryForObject("SELECT id FROM t_reservation WHERE schedule_id = ?", Long.class,
                UNDERSTAFFED_ID);
        jdbcTemplate.execute("ALTER TABLE t_member_card_transaction ADD CONSTRAINT reject_refund"
                + " CHECK (reservation_id <> " + failingId + " OR change_type <> 2)");
        try {
            task.cancelUnderstaffed();
        } finally {
            jdbcTemplate.execute("ALTER TABLE t_member_card_transaction DROP CHECK reject_refund");
        }

        assertEquals(Integer.valueOf(2), scheduleStatus(UNDERSTAFFED_ID), "失败的排课整体回滚");
        assertEquals(Integer.valueOf(1), intOf("SELECT status FROM t_reservation WHERE id = ?", failingId));
        assertEquals(Integer.valueOf(1), intOf("SELECT booked_persons FROM t_schedule WHERE id = ?", UNDERSTAFFED_ID));
        assertEquals(Integer.valueOf(CARD_TIMES - 1), remainingTimes(CARD_A));
        assertEquals(Integer.valueOf(3), scheduleStatus(SECOND_UNDERSTAFFED_ID), "其它排课照常取消");
        assertEquals(Integer.valueOf(CARD_TIMES), remainingTimes(CARD_C));
        assertAccountingConsistent();

        task.cancelUnderstaffed();
        assertEquals(Integer.valueOf(3), scheduleStatus(UNDERSTAFFED_ID), "下一轮补上");
        assertEquals(Integer.valueOf(CARD_TIMES), remainingTimes(CARD_A));
        assertAccountingConsistent();
    }

    @Test
    @DisplayName("自动取消先持锁：并发预约在锁上等待，取消提交后被拒绝且不扣次")
    void booking_whenAutoCancelHoldsLockFirst_shouldReject() throws Exception {
        book(MEMBER_A, CARD_A, UNDERSTAFFED_ID);
        List<String> results = runWithFirstTransactionHeld(
                () -> assertTrue(reservationService.cancelUnderstaffedSchedule(UNDERSTAFFED_ID)), snapshotTaken -> {
                    snapshotTaken.run();
                    book(MEMBER_C, CARD_C, UNDERSTAFFED_ID);
                    return RESULT_OK;
                });

        assertEquals(Arrays.asList(RESULT_OK, "409:排课已取消，不能预约"), results);
        assertEquals(Integer.valueOf(3), scheduleStatus(UNDERSTAFFED_ID));
        assertEquals(Integer.valueOf(CARD_TIMES), remainingTimes(CARD_C), "被拒绝的预约不扣次");
        assertEquals(0, countOf("SELECT COUNT(*) FROM t_reservation WHERE member_id = ?", MEMBER_C));
        assertAccountingConsistent();
    }

    @Test
    @DisplayName("预约先持锁并补足人数：任务用旧快照选中该排课，锁内复查后不取消")
    void autoCancel_whenBookingReachesMinimumFirst_shouldKeepSchedule() throws Exception {
        book(MEMBER_A, CARD_A, ENOUGH_ID);
        LocalDateTime now = LocalDateTime.now();
        List<String> results = runWithFirstTransactionHeld(() -> book(MEMBER_B, CARD_B, ENOUGH_ID), snapshotTaken -> {
            assertTrue(scheduleBookingService.understaffedIds(now, now.plusMinutes(120)).contains(ENOUGH_ID),
                    "预约提交前任务读到人数不足");
            snapshotTaken.run();
            return reservationService.cancelUnderstaffedSchedule(ENOUGH_ID) ? "CANCELLED" : "KEPT";
        });

        assertEquals(Arrays.asList(RESULT_OK, "KEPT"), results);
        assertEquals(Integer.valueOf(2), scheduleStatus(ENOUGH_ID));
        assertEquals(Integer.valueOf(2), intOf("SELECT booked_persons FROM t_schedule WHERE id = ?", ENOUGH_ID));
        assertAccountingConsistent();
    }

    @Test
    @DisplayName("自动取消后：我的预约显示已取消，管理端取消原因为排课取消，约课列表与详情显示已取消")
    void cancelUnderstaffed_shouldBeVisibleToMemberAdminAndList() {
        book(MEMBER_A, CARD_A, UNDERSTAFFED_ID);

        task.cancelUnderstaffed();

        ReservationPublicQuery mine = new ReservationPublicQuery();
        mine.setPageNum(1);
        mine.setPageSize(10);
        ReservationPublicItemVO item = reservationService.pageForMember(MEMBER_A, mine).getList().get(0);
        assertEquals("已取消", item.getStatusText());

        ReservationAdminQuery admin = new ReservationAdminQuery();
        admin.setScheduleId(UNDERSTAFFED_ID);
        admin.setPageNum(1);
        admin.setPageSize(10);
        ReservationAdminItemVO row = reservationService.pageForAdmin(admin).getList().get(0);
        assertEquals(Integer.valueOf(2), row.getCancelReason());
        assertEquals("已取消", row.getScheduleStatusText());

        PublicScheduleQuery list = new PublicScheduleQuery();
        list.setStoreId(STORE_ID);
        list.setCourseType(1);
        list.setDate(scheduleDate(UNDERSTAFFED_ID).toString());
        ScheduleCardVO card = scheduleService.publicPage(list).getList().stream()
                .filter(c -> UNDERSTAFFED_ID.equals(c.getId())).findFirst().orElse(null);
        assertNotNull(card, "已取消的排课仍在约课列表中");
        assertEquals("已取消", card.getStatusText());
        assertEquals("已取消", scheduleService.publicDetail(UNDERSTAFFED_ID).getStatusText());
    }

    @Test
    @DisplayName("若依定时任务：调用目标通过白名单校验，并能按调用目标执行")
    void invokeTarget_shouldPassWhitelistAndRun() throws Exception {
        assertFalse(StringUtils.containsAnyIgnoreCase(INVOKE_TARGET, Constants.JOB_ERROR_STR), "不含违规字符");
        assertTrue(ScheduleUtils.whiteList(INVOKE_TARGET), "定时任务新增与修改接口用同一校验");
        book(MEMBER_A, CARD_A, UNDERSTAFFED_ID);

        SysJob job = new SysJob();
        job.setInvokeTarget(INVOKE_TARGET);
        JobInvokeUtil.invokeMethod(job);

        assertEquals(Integer.valueOf(3), scheduleStatus(UNDERSTAFFED_ID));
    }

    /**
     * 首个事务完成写入后保持未提交；第二个事务调用 {@code snapshotTaken} 表示已完成提交前的读取，随后释放首个事务。
     * 只控制事务重叠与提交顺序，不断言第二个事务已进入锁等待。
     */
    private List<String> runWithFirstTransactionHeld(Runnable first, Function<Runnable, String> second)
            throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch firstApplied = new CountDownLatch(1);
        CountDownLatch secondSnapshot = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        try {
            Future<String> firstFuture = pool.submit(() -> new TransactionTemplate(transactionManager).execute(s -> {
                first.run();
                firstApplied.countDown();
                awaitLatch(releaseFirst);
                return RESULT_OK;
            }));
            Future<String> secondFuture = pool.submit(() -> {
                awaitLatch(firstApplied);
                try {
                    return new TransactionTemplate(transactionManager)
                            .execute(s -> second.apply(secondSnapshot::countDown));
                } catch (ServiceException e) {
                    return e.getCode() + ":" + e.getMessage();
                }
            });
            assertTrue(secondSnapshot.await(10, TimeUnit.SECONDS), "第二个事务应在首个提交之前完成读取");
            releaseFirst.countDown();
            return Arrays.asList(firstFuture.get(30, TimeUnit.SECONDS), secondFuture.get(30, TimeUnit.SECONDS));
        } finally {
            releaseFirst.countDown();
            pool.shutdownNow();
            assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS), "测试线程应结束，避免影响后续数据清理");
        }
    }

    private void awaitLatch(CountDownLatch latch) {
        try {
            assertTrue(latch.await(10, TimeUnit.SECONDS), "等待并发测试阶段超时");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private void book(Long memberId, Long cardId, Long scheduleId) {
        ReservationCreateDTO dto = new ReservationCreateDTO();
        dto.setRequestId(UUID.randomUUID().toString());
        dto.setScheduleId(scheduleId);
        dto.setMemberCardId(cardId);
        dto.setPersons(1);
        reservationService.create(memberId, dto);
    }

    private Long insertQueued(Long memberId, Long cardId, Long scheduleId) {
        Long id = 9000L + memberId;
        jdbcTemplate.update("INSERT INTO t_reservation (id, request_id, member_id, schedule_id, start_time, end_time,"
                + " persons, member_card_id, status, reservation_time, create_by, create_time, update_by, update_time)"
                + " SELECT ?, ?, ?, id, start_time, end_time, 1, ?, 4, NOW(), 1, NOW(), 1, NOW()"
                + " FROM t_schedule WHERE id = ?", id, UUID.randomUUID().toString(), memberId, cardId, scheduleId);
        return id;
    }

    private void insertMember(Long id, String phone, String name) {
        jdbcTemplate.update(
                "INSERT INTO t_member (id, phone, password, phone_last4, name, gender, level, create_by,"
                        + " create_time, update_by, update_time) VALUES (?, ?, NULL, ?, ?, 0, 0, 1, NOW(), 1, NOW())",
                id, phone, phone.substring(phone.length() - 4), name);
    }

    /** 团课类、本店适用、有效期内；次数卡剩余 12 次，年卡无次数 */
    private void insertCard(Long id, Long memberId, int cardType) {
        LocalDateTime now = LocalDateTime.now();
        Integer times = cardType == 1 ? CARD_TIMES : null;
        jdbcTemplate.update("INSERT INTO t_member_card (id, member_id, open_store_id, card_no, card_name, card_type,"
                + " course_scope, store_id, purchase_times, gift_times, remaining_times, valid_period_unit,"
                + " valid_period_count, valid_start_time, valid_end_time, activate_time, create_by, create_time,"
                + " update_by, update_time) VALUES (?, ?, ?, ?, '测试卡', ?, 1, ?, ?, 0, ?, 1, 12, ?, ?, ?, 1, NOW(),"
                + " 1, NOW())", id, memberId, STORE_ID, "20261109000" + id, cardType, STORE_ID, times, times,
                now.minusDays(1), now.plusDays(300), now.minusDays(1));
    }

    private void insertSchedule(Long id, LocalDateTime startTime, int minPersons, int status) {
        jdbcTemplate.update("INSERT INTO t_schedule (id, store_id, course_id, course_type, coach_id, classroom_id,"
                + " schedule_date, start_time, end_time, max_persons, min_persons, booked_persons, status, create_by,"
                + " create_time, update_by, update_time) VALUES (?, ?, ?, 1, ?, ?, ?, ?, ?, 5, ?, 0, ?, 1, NOW(), 1,"
                + " NOW())", id, STORE_ID, COURSE_ID, COACH_ID, CLASSROOM_ID, startTime.toLocalDate(), startTime,
                startTime.plusMinutes(60), minPersons, status);
    }

    private Integer scheduleStatus(Long scheduleId) {
        return intOf("SELECT status FROM t_schedule WHERE id = ?", scheduleId);
    }

    private LocalDate scheduleDate(Long scheduleId) {
        return jdbcTemplate.queryForObject("SELECT schedule_date FROM t_schedule WHERE id = ?", LocalDate.class,
                scheduleId);
    }

    private Integer remainingTimes(Long cardId) {
        return intOf("SELECT remaining_times FROM t_member_card WHERE id = ?", cardId);
    }

    private Integer intOf(String sql, Object... args) {
        return jdbcTemplate.queryForObject(sql, Integer.class, args);
    }

    private int countOf(String sql, Object... args) {
        Integer value = jdbcTemplate.queryForObject(sql, Integer.class, args);
        return value == null ? 0 : value;
    }

    private void assertAccountingConsistent() {
        assertEquals(0,
                countOf("SELECT COUNT(*) FROM t_schedule s WHERE s.booked_persons <>"
                        + " (SELECT COALESCE(SUM(r.persons), 0) FROM t_reservation r"
                        + " WHERE r.schedule_id = s.id AND r.status IN (1, 3))"),
                "排课占位应等于有效预约人数");
        assertEquals(0,
                countOf("SELECT COUNT(*) FROM t_member_card c WHERE c.card_type = 1 AND c.remaining_times <>"
                        + " ? + (SELECT COALESCE(SUM(t.change_times), 0) FROM t_member_card_transaction t"
                        + " WHERE t.member_card_id = c.id)", CARD_TIMES),
                "次数卡余额等于初始次数加流水");
        assertEquals(0, countOf("SELECT COUNT(*) FROM t_reservation r WHERE r.status = 2 AND EXISTS"
                + " (SELECT 1 FROM t_member_card_transaction d WHERE d.reservation_id = r.id AND d.change_type = 1)"
                + " AND NOT EXISTS (SELECT 1 FROM t_member_card_transaction f WHERE f.reservation_id = r.id"
                + " AND f.change_type = 2)"), "扣过次的已取消预约都有返次流水");
    }
}
