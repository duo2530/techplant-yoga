package com.techplant.yoga.reservation.service;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.function.Executable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.ContextConfiguration;
import com.ruoyi.common.exception.ServiceException;
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
import com.techplant.yoga.reservation.service.impl.ReservationServiceImpl;
import com.techplant.yoga.schedule.dao.ScheduleDaoImpl;
import com.techplant.yoga.schedule.service.impl.ScheduleBookingServiceImpl;
import com.techplant.yoga.schedule.service.impl.ScheduleServiceImpl;
import com.techplant.yoga.store.dao.StoreDaoImpl;
import com.techplant.yoga.store.dao.StoreRegionDictDaoImpl;
import com.techplant.yoga.store.service.impl.StoreServiceImpl;

/**
 * 预约并发正确性测试：多线程同时发起预约、排队与取消，每轮结束后按对账 SQL 校验名额、次数与流水。
 *
 * <p>默认跳过，传入 {@code -Dyoga.concurrency=true} 时执行；混合场景的随机种子可用 {@code -Dyoga.concurrency.seed} 固定。</p>
 */
@EnabledIfSystemProperty(named = "yoga.concurrency", matches = "true")
@ContextConfiguration(classes = {YogaMyBatisTestConfig.class, StoreDaoImpl.class, StoreRegionDictDaoImpl.class,
    StoreServiceImpl.class, ClassroomDaoImpl.class, ClassroomServiceImpl.class, CoachDaoImpl.class,
    CoachServiceImpl.class, CourseDaoImpl.class, CourseServiceImpl.class, MemberDaoImpl.class,
    MemberLevelDictDaoImpl.class, MemberServiceImpl.class, MemberCardDaoImpl.class, MemberCardTransactionDaoImpl.class,
    MemberCardTransactionServiceImpl.class, CardNoGenerator.class, MemberCardServiceImpl.class, ScheduleDaoImpl.class,
    ScheduleBookingServiceImpl.class, ScheduleServiceImpl.class, ReservationDaoImpl.class,
    ReservationServiceImpl.class})
@DisplayName("预约并发正确性测试（Testcontainers MySQL：抢名额 / 连点 / 幂等 / 共卡 / 跨课跨人 / 混合）")
class ReservationConcurrencyTest extends AbstractYogaIntegrationTest {
    private static final int ROUNDS = 10;

    private static final String ROUND_NAME = "{displayName} 第 {currentRepetition}/{totalRepetitions} 轮";

    private static final String OK = "OK";

    private static final String UNEXPECTED = "UNEXPECTED";

    private static final long TIMEOUT_SECONDS = 120L;

    private static final Long STORE_ID = 1000L;

    private static final Long COURSE_ID = 3000L;

    private static final Long COACH_ID = 4000L;

    private static final Long CLASSROOM_ID = 5000L;

    private static final long MEMBER_BASE = 10000L;

    private static final long CARD_BASE = 20000L;

    private static final long SCHEDULE_BASE = 30000L;

    private static final int QUEUE_LIMIT = 5;

    private static final String INSERT_MEMBER = "INSERT INTO t_member (id, phone, password, phone_last4, name,"
            + " gender, level, create_by, create_time, update_by, update_time)"
            + " VALUES (?, ?, NULL, ?, ?, 0, 0, 1, NOW(), 1, NOW())";

    private static final String INSERT_TIMES_CARD = "INSERT INTO t_member_card (id, member_id, open_store_id,"
            + " card_no, card_name, card_type, course_scope, store_id, purchase_times, gift_times, remaining_times,"
            + " valid_period_unit, valid_period_count, valid_start_time, valid_end_time, activate_time, stop_time,"
            + " stop_reason, create_by, create_time, update_by, update_time) VALUES (?, ?, ?, ?, '并发测试次数卡', 1, 1,"
            + " ?, ?, 0, ?, 1, 12, ?, ?, ?, NULL, NULL, 1, NOW(), 1, NOW())";

    /** 每轮结束后必须查不到任何行的对账检查：名称 → 查询违规记录的 SQL */
    private static final Map<String, String> INVARIANTS = invariants();

    /** 各场景跨轮次累计的结果分布 */
    private static final Map<String, Map<String, Long>> TOTALS = new TreeMap<String, Map<String, Long>>();

    @Autowired
    private ReservationService reservationService;

    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void resetTables() {
        YogaSchema.reset(dataSource, "t_reservation", "t_member_card_transaction", "t_member_card", "t_schedule",
                "t_course", "t_coach", "t_classroom", "t_store", "t_member");
        jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.update("INSERT INTO t_store (id, store_no, name, store_type, region_code, phone, business_hours,"
                + " address, create_by, create_time, update_by, update_time) VALUES (?, '0001', '徐汇店', 1, '310104',"
                + " '021-12345678', '周一至周日 09:00-22:00', '漕溪北路 88 号', 1, NOW(), 1, NOW())", STORE_ID);
        jdbcTemplate.update("INSERT INTO t_classroom (id, store_id, name, create_by, create_time, update_by,"
                + " update_time) VALUES (?, ?, '瑜伽团课大教室', 1, NOW(), 1, NOW())", CLASSROOM_ID, STORE_ID);
        jdbcTemplate.update("INSERT INTO t_coach (id, name, avatar_url, intro, phone, create_by, create_time,"
                + " update_by, update_time) VALUES (?, '林教练', NULL, NULL, NULL, 1, NOW(), 1, NOW())", COACH_ID);
        jdbcTemplate.update("INSERT INTO t_course (id, name, course_type, cover_url, intro, difficulty, create_by,"
                + " create_time, update_by, update_time) VALUES (?, '哈他瑜伽', 1, NULL, NULL, 2, 1, NOW(), 1, NOW())",
                COURSE_ID);
    }

    @AfterAll
    static void printTotals() {
        for (Map.Entry<String, Map<String, Long>> entry : TOTALS.entrySet()) {
            System.out.println("[累计][" + entry.getKey() + "] " + entry.getValue());
        }
    }

    @RepeatedTest(value = ROUNDS, name = ROUND_NAME)
    @DisplayName("抢名额：50 位会员同时预约 10 个名额的排课，恰好 10 人成功，不超卖")
    void grabSeats() throws Exception {
        long scheduleId = insertSchedule(0, 10);
        List<Runnable> actions = new ArrayList<Runnable>();
        for (long memberId : insertMembers(50, 10)) {
            actions.add(() -> create(memberId, newRequestId(), scheduleId, cardOf(memberId)));
        }

        Map<String, Long> outcomes = race("1 抢名额", actions);

        assertRound(outcomes, () -> assertEquals(10L, countOf(outcomes, OK::equals), "恰好 10 人预约成功：" + outcomes),
                () -> assertEquals(40L, countOf(outcomes, o -> o.startsWith("409:剩余名额不足")),
                        "其余 40 人提示名额不足：" + outcomes),
                () -> assertEquals(10, intOf("SELECT booked_persons FROM t_schedule WHERE id = ?", scheduleId),
                        "排课占满 10 人"),
                () -> assertEquals(10, intOf("SELECT COUNT(*) FROM t_reservation")));
    }

    @RepeatedTest(value = ROUNDS, name = ROUND_NAME)
    @DisplayName("同人连点：同一会员 20 个不同请求标识同时预约同一排课，只成功一次、只扣一次")
    void sameMemberRepeatedClicks() throws Exception {
        long scheduleId = insertSchedule(0, 30);
        long memberId = insertMembers(1, 10).get(0);
        List<Runnable> actions = new ArrayList<Runnable>();
        for (int i = 0; i < 20; i++) {
            actions.add(() -> create(memberId, newRequestId(), scheduleId, cardOf(memberId)));
        }

        Map<String, Long> outcomes = race("2 同人连点", actions);

        assertRound(outcomes, () -> assertEquals(1L, countOf(outcomes, OK::equals), "只成功一次：" + outcomes),
                () -> assertEquals(19L, countOf(outcomes, "409:您已预约该课程"::equals), "其余提示已预约：" + outcomes),
                () -> assertEquals(1, intOf("SELECT COUNT(*) FROM t_reservation"), "只写入一条预约"),
                () -> assertEquals(1, intOf("SELECT COUNT(*) FROM t_member_card_transaction"), "只写入一条扣次流水"),
                () -> assertEquals(9, intOf("SELECT remaining_times FROM t_member_card WHERE id = ?", cardOf(memberId)),
                        "只扣一次"));
    }

    @RepeatedTest(value = ROUNDS, name = ROUND_NAME)
    @DisplayName("幂等重试：同一会员 20 个线程用同一请求标识同时预约，全部成功且只落一条预约、只扣一次")
    void sameRequestIdRetries() throws Exception {
        long scheduleId = insertSchedule(0, 30);
        long memberId = insertMembers(1, 10).get(0);
        String requestId = newRequestId();
        List<Runnable> actions = new ArrayList<Runnable>();
        for (int i = 0; i < 20; i++) {
            actions.add(() -> create(memberId, requestId, scheduleId, cardOf(memberId)));
        }

        Map<String, Long> outcomes = race("3 幂等重试", actions);

        assertRound(outcomes, () -> assertEquals(20L, countOf(outcomes, OK::equals), "幂等重试都按成功返回：" + outcomes),
                () -> assertEquals(1, intOf("SELECT COUNT(*) FROM t_reservation"), "只写入一条预约"),
                () -> assertEquals(1, intOf("SELECT COUNT(*) FROM t_member_card_transaction"), "只写入一条扣次流水"),
                () -> assertEquals(1, intOf("SELECT booked_persons FROM t_schedule WHERE id = ?", scheduleId)),
                () -> assertEquals(9, intOf("SELECT remaining_times FROM t_member_card WHERE id = ?", cardOf(memberId)),
                        "只扣一次"));
    }

    @RepeatedTest(value = ROUNDS, name = ROUND_NAME)
    @DisplayName("共卡抢次数：剩 5 次的卡同时预约 10 节不同排课，恰好 5 节成功，余额扣到 0 不透支")
    void sharedCardBalance() throws Exception {
        long memberId = insertMembers(1, 5).get(0);
        List<Runnable> actions = new ArrayList<Runnable>();
        for (int i = 0; i < 10; i++) {
            long scheduleId = insertSchedule(i, 30);
            actions.add(() -> create(memberId, newRequestId(), scheduleId, cardOf(memberId)));
        }

        Map<String, Long> outcomes = race("4 共卡抢次数", actions);

        assertRound(outcomes, () -> assertEquals(5L, countOf(outcomes, OK::equals), "恰好 5 节预约成功：" + outcomes),
                () -> assertEquals(5L, countOf(outcomes, "409:会员卡剩余次数不足"::equals), "其余提示次数不足：" + outcomes),
                () -> assertEquals(0, intOf("SELECT remaining_times FROM t_member_card WHERE id = ?", cardOf(memberId)),
                        "余额扣到 0"),
                () -> assertEquals(5, intOf("SELECT COUNT(*) FROM t_reservation")));
    }

    @RepeatedTest(value = ROUNDS, name = ROUND_NAME)
    @DisplayName("跨课跨人：50 位会员在空预约表上同时预约 20 节排课（100 个互不重复的会员与排课组合），名额充足时全部成功")
    void crossMemberCrossSchedule() throws Exception {
        List<Long> memberIds = insertMembers(50, 10);
        List<Long> scheduleIds = new ArrayList<Long>();
        for (int i = 0; i < 20; i++) {
            scheduleIds.add(insertSchedule(i, 30));
        }
        List<Runnable> actions = new ArrayList<Runnable>();
        for (int k = 0; k < 100; k++) {
            long memberId = memberIds.get(k % memberIds.size());
            long scheduleId = scheduleIds.get(k % scheduleIds.size());
            actions.add(() -> create(memberId, newRequestId(), scheduleId, cardOf(memberId)));
        }

        Map<String, Long> outcomes = race("5 跨课跨人", actions);

        assertRound(outcomes, () -> assertEquals(100L, countOf(outcomes, OK::equals), "名额充足时全部成功：" + outcomes),
                () -> assertEquals(100, intOf("SELECT COUNT(*) FROM t_reservation")),
                () -> assertEquals(0, intOf("SELECT COUNT(*) FROM t_schedule WHERE booked_persons <> 5"), "每节排课各 5 人"));
    }

    @RepeatedTest(value = ROUNDS, name = ROUND_NAME)
    @DisplayName("混合：满员与有空位的排课上随机并发预约、排队与取消（取消触发补位），对账始终一致")
    void mixedOperations() throws Exception {
        List<Long> memberIds = insertMembers(30, 20);
        List<Long> scheduleIds = new ArrayList<Long>();
        int[] maxPersons = {3, 3, 5, 2};
        for (int i = 0; i < maxPersons.length; i++) {
            scheduleIds.add(insertSchedule(i, maxPersons[i]));
        }
        for (int m = 0; m < 3; m++) {
            create(memberIds.get(m), newRequestId(), scheduleIds.get(0), cardOf(memberIds.get(m)));
        }
        for (int m = 3; m < 5; m++) {
            create(memberIds.get(m), newRequestId(), scheduleIds.get(3), cardOf(memberIds.get(m)));
        }
        create(memberIds.get(5), newRequestId(), scheduleIds.get(1), cardOf(memberIds.get(5)));
        joinQueue(memberIds.get(6), scheduleIds.get(0));
        joinQueue(memberIds.get(7), scheduleIds.get(0));
        joinQueue(memberIds.get(8), scheduleIds.get(3));
        List<Map<String, Object>> existing = jdbcTemplate.queryForList("SELECT id, member_id FROM t_reservation");

        long seed = Long.getLong("yoga.concurrency.seed", System.nanoTime());
        System.out.println("[6 混合] 随机种子 " + seed + "（复现：-Dyoga.concurrency.seed=" + seed + "）");
        Random random = new Random(seed);
        List<Runnable> actions = new ArrayList<Runnable>();
        for (int i = 0; i < 80; i++) {
            long memberId = memberIds.get(random.nextInt(memberIds.size()));
            long scheduleId = scheduleIds.get(random.nextInt(scheduleIds.size()));
            int kind = random.nextInt(10);
            if (kind < 5) {
                actions.add(() -> create(memberId, newRequestId(), scheduleId, cardOf(memberId)));
            } else if (kind < 7) {
                actions.add(() -> joinQueue(memberId, scheduleId));
            } else {
                Map<String, Object> target = existing.get(random.nextInt(existing.size()));
                long ownerId = ((Number) target.get("member_id")).longValue();
                long reservationId = ((Number) target.get("id")).longValue();
                actions.add(() -> reservationService.cancel(ownerId, reservationId));
            }
        }

        Map<String, Long> outcomes = race("6 混合", actions);

        assertRound(outcomes, () -> assertEquals(0L, countOf(outcomes, o -> !OK.equals(o) && !o.startsWith("409:")),
                "只应出现成功或业务冲突（seed=" + seed + "）：" + outcomes));
    }

    private static Map<String, String> invariants() {
        Map<String, String> checks = new LinkedHashMap<String, String>();
        checks.put("排课占位等于有效预约人数", "SELECT s.id, s.booked_persons, (SELECT COALESCE(SUM(r.persons), 0)"
                + " FROM t_reservation r WHERE r.schedule_id = s.id AND r.status IN (1, 3)) AS seated FROM t_schedule s"
                + " HAVING s.booked_persons <> seated");
        checks.put("已预约人数不超过最大人数",
                "SELECT id, booked_persons, max_persons FROM t_schedule WHERE booked_persons > max_persons");
        checks.put("同一会员同一排课至多一条有效记录", "SELECT member_id, schedule_id, COUNT(*) AS open_count"
                + " FROM t_reservation WHERE status IN (1, 3, 4) GROUP BY member_id, schedule_id HAVING COUNT(*) > 1");
        checks.put("次数卡剩余次数非负", "SELECT id, remaining_times FROM t_member_card WHERE remaining_times < 0");
        checks.put("流水余额守恒",
                "SELECT * FROM t_member_card_transaction" + " WHERE after_times <> before_times + change_times");
        checks.put("占位的次数卡预约恰有一条等额扣次流水", "SELECT r.id, r.member_id, r.schedule_id, r.status, r.persons"
                + " FROM t_reservation r JOIN t_member_card c ON c.id = r.member_card_id"
                + " WHERE c.card_type = 1 AND r.status IN (1, 3) AND (SELECT COUNT(*) FROM t_member_card_transaction t"
                + " WHERE t.reservation_id = r.id AND t.change_type = 1 AND t.change_times = -r.persons) <> 1");
        checks.put("已取消且扣过次的预约已等额返次", "SELECT r.id, r.member_id, r.schedule_id, r.reservation_time,"
                + " r.cancel_time, d.change_times AS deduct_times, d.create_time AS deduct_time FROM t_reservation r"
                + " JOIN t_member_card_transaction d ON d.reservation_id = r.id AND d.change_type = 1"
                + " WHERE r.status = 2 AND NOT EXISTS (SELECT 1 FROM t_member_card_transaction t"
                + " WHERE t.reservation_id = r.id AND t.change_type = 2 AND t.change_times = -d.change_times)");
        checks.put("每条预约至多一条返次流水", "SELECT reservation_id, COUNT(*) AS refund_count FROM t_member_card_transaction"
                + " WHERE change_type = 2 GROUP BY reservation_id HAVING COUNT(*) > 1");
        checks.put("排队中与排队失败没有次数流水", "SELECT r.id, r.status FROM t_reservation r WHERE r.status IN (4, 5)"
                + " AND EXISTS (SELECT 1 FROM t_member_card_transaction t WHERE t.reservation_id = r.id)");
        checks.put("次数卡余额等于初始次数加流水变动", "SELECT c.id, c.purchase_times + c.gift_times AS initial_times,"
                + " c.remaining_times, (SELECT COALESCE(SUM(t.change_times), 0) FROM t_member_card_transaction t"
                + " WHERE t.member_card_id = c.id) AS ledger_change FROM t_member_card c WHERE c.card_type = 1"
                + " HAVING c.remaining_times <> initial_times + ledger_change");
        checks.put("有空位时不应仍有人排队",
                "SELECT s.id, s.booked_persons, s.max_persons FROM t_schedule s"
                        + " WHERE s.booked_persons < s.max_persons"
                        + " AND EXISTS (SELECT 1 FROM t_reservation r WHERE r.schedule_id = s.id AND r.status = 4)");
        checks.put("排队人数不超过上限", "SELECT s.id, s.queue_limit FROM t_schedule s WHERE s.queue_limit <"
                + " (SELECT COUNT(*) FROM t_reservation r WHERE r.schedule_id = s.id AND r.status = 4)");
        return checks;
    }

    /** 每轮通用断言：对账检查查不到违规记录、没有非业务异常，再加场景自己的期望 */
    private void assertRound(Map<String, Long> outcomes, Executable... expectations) {
        List<Executable> checks = new ArrayList<Executable>();
        for (Map.Entry<String, String> invariant : INVARIANTS.entrySet()) {
            checks.add(() -> {
                List<Map<String, Object>> violations = jdbcTemplate.queryForList(invariant.getValue());
                assertTrue(violations.isEmpty(), "对账失败：" + invariant.getKey() + " " + violations);
            });
        }
        checks.add(() -> assertEquals(0L, countOf(outcomes, o -> o.startsWith(UNEXPECTED)),
                "不应出现死锁、锁等待超时等非业务异常：" + outcomes));
        for (Executable expectation : expectations) {
            checks.add(expectation);
        }
        assertAll(checks);
    }

    /** 所有动作就绪后同时放行，返回结果分布并打印耗时 */
    private Map<String, Long> race(String scenario, List<Runnable> actions) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(actions.size());
        CountDownLatch ready = new CountDownLatch(actions.size());
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<String>> futures = new ArrayList<Future<String>>();
            for (Runnable action : actions) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    if (!start.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("等待统一放行超时");
                    }
                    return outcomeOf(action);
                }));
            }
            assertTrue(ready.await(TIMEOUT_SECONDS, TimeUnit.SECONDS), "所有线程应已就绪");
            long begin = System.nanoTime();
            start.countDown();
            List<String> results = new ArrayList<String>();
            for (Future<String> future : futures) {
                results.add(future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS));
            }
            long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - begin);
            Map<String, Long> outcomes = results.stream()
                    .collect(Collectors.groupingBy(Function.identity(), TreeMap::new, Collectors.counting()));
            System.out
                    .println("[" + scenario + "] 并发 " + actions.size() + "，耗时 " + elapsedMillis + " ms，结果 " + outcomes);
            synchronized (TOTALS) {
                Map<String, Long> total = TOTALS.computeIfAbsent(scenario, k -> new TreeMap<String, Long>());
                outcomes.forEach((outcome, count) -> total.merge(outcome, count, Long::sum));
            }
            if (countOf(outcomes, o -> o.startsWith(UNEXPECTED)) > 0) {
                printLatestDeadlock(scenario);
            }
            return outcomes;
        } finally {
            start.countDown();
            pool.shutdownNow();
            assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS), "并发测试线程应结束，避免影响后续数据清理");
        }
    }

    private static String outcomeOf(Runnable action) {
        try {
            action.run();
            return OK;
        } catch (ServiceException e) {
            return e.getCode() + ":" + e.getMessage();
        } catch (RuntimeException e) {
            System.out.println("[" + UNEXPECTED + "] " + e);
            Throwable cause = NestedExceptionUtils.getMostSpecificCause(e);
            String message = cause.getMessage() == null ? "" : cause.getMessage().trim().split("\n", 2)[0];
            return UNEXPECTED + ":" + e.getClass().getSimpleName() + ":" + message;
        }
    }

    /** 打印 InnoDB 最近一次死锁；查看引擎状态需要 PROCESS 权限，测试容器的 root 密码与业务账号相同 */
    private void printLatestDeadlock(String scenario) {
        DriverManagerDataSource source = (DriverManagerDataSource) dataSource;
        try (Connection connection = DriverManager.getConnection(source.getUrl(), "root", source.getPassword());
                Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery("SHOW ENGINE INNODB STATUS")) {
            String status = resultSet.next() ? resultSet.getString("Status") : "";
            int from = status.indexOf("LATEST DETECTED DEADLOCK");
            int to = status.indexOf("\nTRANSACTIONS\n", Math.max(from, 0));
            System.out.println("[" + scenario + "] InnoDB 最近一次死锁：\n"
                    + (from < 0 ? "（未记录死锁）" : status.substring(from, to < 0 ? status.length() : to)));
        } catch (SQLException e) {
            System.out.println("[" + scenario + "] 读取 InnoDB 状态失败：" + e.getMessage());
        }
    }

    private static long countOf(Map<String, Long> outcomes, Predicate<String> matcher) {
        return outcomes.entrySet().stream().filter(e -> matcher.test(e.getKey())).mapToLong(Map.Entry::getValue).sum();
    }

    private void create(long memberId, String requestId, long scheduleId, long memberCardId) {
        reservationService.create(memberId, dto(requestId, scheduleId, memberCardId));
    }

    private void joinQueue(long memberId, long scheduleId) {
        reservationService.joinQueue(memberId, dto(newRequestId(), scheduleId, cardOf(memberId)));
    }

    private static ReservationCreateDTO dto(String requestId, long scheduleId, long memberCardId) {
        ReservationCreateDTO dto = new ReservationCreateDTO();
        dto.setRequestId(requestId);
        dto.setScheduleId(scheduleId);
        dto.setMemberCardId(memberCardId);
        dto.setPersons(1);
        return dto;
    }

    private static String newRequestId() {
        return UUID.randomUUID().toString();
    }

    private static long cardOf(long memberId) {
        return memberId - MEMBER_BASE + CARD_BASE;
    }

    private int intOf(String sql, Object... args) {
        Integer value = jdbcTemplate.queryForObject(sql, Integer.class, args);
        return value == null ? 0 : value;
    }

    /** 会员各持一张团课类本店次数卡；初始次数记在 purchase_times，供余额对账 */
    private List<Long> insertMembers(int count, int times) {
        LocalDateTime now = LocalDateTime.now();
        List<Long> memberIds = new ArrayList<Long>();
        for (int i = 0; i < count; i++) {
            long memberId = MEMBER_BASE + i;
            String phone = String.format("1390000%04d", i);
            jdbcTemplate.update(INSERT_MEMBER, memberId, phone, phone.substring(phone.length() - 4), "会员" + i);
            jdbcTemplate.update(INSERT_TIMES_CARD, cardOf(memberId), memberId, STORE_ID, "S" + cardOf(memberId),
                    STORE_ID, times, times, now.minusDays(1), now.plusDays(300), now.minusDays(1));
            memberIds.add(memberId);
        }
        return memberIds;
    }

    /** 明天的已上架团课，每节错开 30 分钟，处于可预约窗口内且未过取消截止与停止排队时间 */
    private long insertSchedule(int index, int maxPersons) {
        long scheduleId = SCHEDULE_BASE + index;
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        LocalDateTime startTime = tomorrow.atTime(6, 0).plusMinutes(30L * index);
        jdbcTemplate.update("INSERT INTO t_schedule (id, store_id, course_id, course_type, coach_id, classroom_id,"
                + " schedule_date, start_time, end_time, max_persons, min_persons, queue_limit, queue_stop_minutes,"
                + " booked_persons, status, create_by, create_time, update_by, update_time)"
                + " VALUES (?, ?, ?, 1, ?, ?, ?, ?, ?, ?, 1, ?, 110, 0, 2, 1, NOW(), 1, NOW())", scheduleId, STORE_ID,
                COURSE_ID, COACH_ID, CLASSROOM_ID, tomorrow, startTime, startTime.plusHours(1), maxPersons,
                QUEUE_LIMIT);
        return scheduleId;
    }
}
