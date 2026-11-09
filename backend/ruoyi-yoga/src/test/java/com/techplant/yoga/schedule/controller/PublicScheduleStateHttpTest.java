package com.techplant.yoga.schedule.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.techplant.yoga.common.testcontainers.AbstractYogaHttpIntegrationTest;
import com.techplant.yoga.common.testcontainers.YogaSchema;
import com.techplant.yoga.reservation.service.ReservationService;
import com.techplant.yoga.schedule.service.ScheduleService;

/**
 * 用户端排课展示状态的 HTTP 集成测试：真实用户端安全过滤链 ＋ 真实 MySQL。
 *
 * <p>覆盖列表与详情的可约／排队／已约满／已结束／已取消／未开放、排队人数批量统计，以及管理端文案不受影响。</p>
 */
@DisplayName("用户端排课展示状态（真实安全链：列表与详情同一口径 / 排队人数批量统计 / 管理端文案不变）")
class PublicScheduleStateHttpTest extends AbstractYogaHttpIntegrationTest {
    private static final long STORE_ID = 100L;

    private static final long COURSE_ID = 300L;

    private static final long COACH_ID = 400L;

    private static final long CLASSROOM_ID = 500L;

    private static final int GROUP_COURSE = 1;

    private static final int PUBLISHED = 2;

    private static final int CANCELLED = 3;

    private static final int QUEUED = 4;

    private static final int RESERVATION_CANCELLED = 2;

    private static final long STARTED_ID = 2001L;

    private static final long BOOKABLE_ID = 2101L;

    private static final long QUEUE_OPEN_ID = 2102L;

    private static final long QUEUE_FULL_ID = 2103L;

    private static final long QUEUE_CLOSED_ID = 2104L;

    private static final long QUEUE_STOPPED_ID = 2105L;

    private static final long CANCELLED_ID = 2106L;

    private static final long QUEUE_ONE_ID = 2107L;

    private static final long OUT_OF_WINDOW_ID = 2108L;

    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired
    private ReservationService reservationService;

    @Autowired
    private ScheduleService scheduleService;

    private JdbcTemplate jdbcTemplate;

    private LocalDateTime startedAt;

    private LocalDateTime futureAt;

    private long reservationSeq;

    @BeforeEach
    void setUpData() {
        YogaSchema.reset(dataSource, "t_store", "t_course", "t_classroom", "t_coach", "t_schedule", "t_reservation");
        jdbcTemplate = new JdbcTemplate(dataSource);
        reservationSeq = 9000L;
        jdbcTemplate.update("INSERT INTO t_store (id, store_no, name, store_type, region_code, phone, business_hours,"
                + " address) VALUES (?, '0001', '徐汇店', 1, '310104', '02100000000', '09:00-22:00', '上海')", STORE_ID);
        jdbcTemplate.update("INSERT INTO t_course (id, name, course_type, intro, difficulty)"
                + " VALUES (?, '哈他瑜伽', ?, '课程介绍', 2)", COURSE_ID, GROUP_COURSE);
        jdbcTemplate.update("INSERT INTO t_classroom (id, store_id, name) VALUES (?, ?, '大教室')", CLASSROOM_ID,
                STORE_ID);
        jdbcTemplate.update("INSERT INTO t_coach (id, name, level) VALUES (?, '林教练', 1)", COACH_ID);

        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES);
        startedAt = now.minusMinutes(30).isBefore(now.toLocalDate().atStartOfDay()) ? now.toLocalDate().atStartOfDay()
                : now.minusMinutes(30);
        futureAt = now.plusHours(3);
        insertSchedule(STARTED_ID, startedAt, 0, 10, 3, 110, PUBLISHED);
        insertSchedule(BOOKABLE_ID, futureAt, 0, 10, 3, 0, PUBLISHED);
        insertSchedule(QUEUE_OPEN_ID, futureAt.plusMinutes(1), 2, 2, 3, 0, PUBLISHED);
        insertSchedule(QUEUE_FULL_ID, futureAt.plusMinutes(2), 1, 1, 2, 0, PUBLISHED);
        insertSchedule(QUEUE_CLOSED_ID, futureAt.plusMinutes(3), 1, 1, 0, 0, PUBLISHED);
        // 停止排队时间 1440 分钟远大于距开课的 3 小时：已过停止排队时间
        insertSchedule(QUEUE_STOPPED_ID, futureAt.plusMinutes(4), 1, 1, 3, 1440, PUBLISHED);
        insertSchedule(CANCELLED_ID, futureAt.plusMinutes(5), 0, 10, 3, 0, CANCELLED);
        insertSchedule(QUEUE_ONE_ID, futureAt.plusMinutes(6), 1, 1, 2, 0, PUBLISHED);
        insertSchedule(OUT_OF_WINDOW_ID, futureAt.plusDays(14), 0, 10, 3, 0, PUBLISHED);

        insertReservation(QUEUE_OPEN_ID, QUEUED);
        insertReservation(QUEUE_OPEN_ID, QUEUED);
        insertReservation(QUEUE_FULL_ID, QUEUED);
        insertReservation(QUEUE_FULL_ID, QUEUED);
        insertReservation(QUEUE_ONE_ID, QUEUED);
        insertReservation(QUEUE_ONE_ID, RESERVATION_CANCELLED);
    }

    @Test
    @DisplayName("约课列表：可约／排队／已约满（排队已满、不开放排队、已过停止排队时间）／已取消，按钮标识同口径")
    void list_shouldReturnPublicStatePerSchedule() throws Exception {
        Map<String, JsonNode> rows = listByDate(futureAt.toLocalDate());

        assertState(rows.get(String.valueOf(BOOKABLE_ID)), "可约", true, false);
        assertState(rows.get(String.valueOf(QUEUE_OPEN_ID)), "排队", false, true);
        assertState(rows.get(String.valueOf(QUEUE_FULL_ID)), "已约满", false, false);
        assertState(rows.get(String.valueOf(QUEUE_CLOSED_ID)), "已约满", false, false);
        assertState(rows.get(String.valueOf(QUEUE_STOPPED_ID)), "已约满", false, false);
        assertState(rows.get(String.valueOf(CANCELLED_ID)), "已取消", false, false);
        assertState(rows.get(String.valueOf(QUEUE_ONE_ID)), "排队", false, true);
    }

    @Test
    @DisplayName("开课后未下课：列表与详情显示已结束，管理端仍显示已上架")
    void started_shouldBeFinishedForMembersOnly() throws Exception {
        Map<String, JsonNode> rows = listByDate(startedAt.toLocalDate());
        assertState(rows.get(String.valueOf(STARTED_ID)), "已结束", false, false);
        assertState(detail(STARTED_ID), "已结束", false, false);

        assertEquals("已上架", scheduleService.getById(STARTED_ID).getStatusText(), "管理端按结束时间判定已结束");
        assertFalse(scheduleService.getById(STARTED_ID).getFinished());
    }

    @Test
    @DisplayName("详情：状态与列表一致，排队人数只计排队中记录；不在 14 天窗口内显示未开放")
    void detail_shouldMatchListState() throws Exception {
        JsonNode queueOpen = detail(QUEUE_OPEN_ID);
        assertState(queueOpen, "排队", false, true);
        assertEquals(2, queueOpen.get("queueCount").asInt());
        assertEquals(3, queueOpen.get("queueLimit").asInt());

        JsonNode queueFull = detail(QUEUE_FULL_ID);
        assertState(queueFull, "已约满", false, false);
        assertEquals(2, queueFull.get("queueCount").asInt());

        assertState(detail(QUEUE_STOPPED_ID), "已约满", false, false);
        assertState(detail(CANCELLED_ID), "已取消", false, false);
        assertState(detail(BOOKABLE_ID), "可约", true, false);
        assertEquals(0, detail(BOOKABLE_ID).get("queueCount").asInt());
        assertState(detail(OUT_OF_WINDOW_ID), "未开放", false, false);
    }

    @Test
    @DisplayName("排队人数批量统计：多节排课一次返回，只计排队中，没有排队的排课不在结果中")
    void queueCounts_shouldGroupBySchedule() {
        Map<Long, Integer> counts = reservationService
                .queueCounts(Arrays.asList(QUEUE_OPEN_ID, QUEUE_FULL_ID, QUEUE_ONE_ID, BOOKABLE_ID));
        Map<Long, Integer> expected = new HashMap<Long, Integer>();
        expected.put(QUEUE_OPEN_ID, 2);
        expected.put(QUEUE_FULL_ID, 2);
        expected.put(QUEUE_ONE_ID, 1);
        assertEquals(expected, counts);
        assertTrue(reservationService.queueCounts(Arrays.<Long>asList()).isEmpty(), "空 ID 列表返回空结果");
    }

    private void assertState(JsonNode row, String statusText, boolean bookable, boolean queueable) {
        assertEquals(statusText, row.get("statusText").asText(), "排课 " + row.get("id").asText() + " 的状态文案");
        assertEquals(bookable, row.get("bookable").asBoolean(), "排课 " + row.get("id").asText() + " 的 bookable");
        assertEquals(queueable, row.get("queueable").asBoolean(), "排课 " + row.get("id").asText() + " 的 queueable");
    }

    private Map<String, JsonNode> listByDate(LocalDate date) throws Exception {
        JsonNode root = read(mockMvc.perform(get("/api/schedules").param("storeId", String.valueOf(STORE_ID))
                .param("courseType", String.valueOf(GROUP_COURSE)).param("date", date.toString())
                .param("pageNum", "1").param("pageSize", "20")).andExpect(status().isOk()).andReturn());
        assertEquals(200, root.get("code").asInt());
        Map<String, JsonNode> rows = new HashMap<String, JsonNode>();
        for (JsonNode row : root.get("rows")) {
            rows.put(row.get("id").asText(), row);
        }
        return rows;
    }

    private JsonNode detail(long scheduleId) throws Exception {
        JsonNode root = read(mockMvc.perform(get("/api/schedules/" + scheduleId)).andExpect(status().isOk())
                .andReturn());
        assertEquals(200, root.get("code").asInt());
        return root.get("data");
    }

    private JsonNode read(MvcResult result) throws Exception {
        return JSON.readTree(new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8));
    }

    private void insertSchedule(long id, LocalDateTime startTime, int bookedPersons, int maxPersons, int queueLimit,
            int queueStopMinutes, int status) {
        jdbcTemplate.update("INSERT INTO t_schedule (id, store_id, course_id, course_type, coach_id, classroom_id,"
                + " schedule_date, start_time, end_time, max_persons, min_persons, queue_limit, queue_stop_minutes,"
                + " booked_persons, status) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1, ?, ?, ?, ?)", id, STORE_ID,
                COURSE_ID, GROUP_COURSE, COACH_ID, CLASSROOM_ID, Date.valueOf(startTime.toLocalDate()),
                Timestamp.valueOf(startTime), Timestamp.valueOf(startTime.plusMinutes(60)), maxPersons, queueLimit,
                queueStopMinutes, bookedPersons, status);
    }

    private void insertReservation(long scheduleId, int status) {
        long id = ++reservationSeq;
        LocalDateTime startTime = jdbcTemplate.queryForObject("SELECT start_time FROM t_schedule WHERE id = ?",
                Timestamp.class, scheduleId).toLocalDateTime();
        jdbcTemplate.update("INSERT INTO t_reservation (id, request_id, member_id, schedule_id, start_time, end_time,"
                + " persons, member_card_id, status, reservation_time) VALUES (?, ?, ?, ?, ?, ?, 1, ?, ?, NOW())", id,
                "req-" + id, id, scheduleId, Timestamp.valueOf(startTime), Timestamp.valueOf(startTime.plusMinutes(60)),
                id, status);
    }
}
