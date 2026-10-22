package com.techplant.yoga.schedule.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.techplant.yoga.common.config.BusinessTimeConfig;
import com.techplant.yoga.common.testcontainers.AbstractYogaHttpIntegrationTest;
import com.techplant.yoga.common.testcontainers.YogaSchema;
import com.techplant.yoga.common.testcontainers.YogaSecurityTestConfig;

/**
 * 教练页排课接口的 HTTP 集成测试：真实用户端安全过滤链 ＋ 真实 MySQL ＋ 固定测试时钟。
 *
 * <p>覆盖窗口首尾日、窗口外排除、其他教练／门店排除、课种快照、待上架排除、已取消／已结束状态、
 * 满员、排序、分页，以及既有单日排课接口的回归。所有「今天」都来自
 * {@link YogaSecurityTestConfig#FIXED_TODAY}，不依赖运行时刻。</p>
 */
@DisplayName("教练页排课接口（真实安全链：14 天窗口 / 可见性 / 快照 / 排序 / 分页 / 参数错误）")
class CoachScheduleHttpTest extends AbstractYogaHttpIntegrationTest {
    private static final long STORE_ID = 100L;

    private static final long OTHER_STORE_ID = 200L;

    private static final long COURSE_ID = 300L;

    private static final long OTHER_COURSE_ID = 301L;

    private static final long COACH_ID = 400L;

    private static final long OTHER_COACH_ID = 401L;

    private static final long CLASSROOM_ID = 500L;

    private static final int GROUP_COURSE = 1;

    private static final int SPECIAL_COURSE = 4;

    /** 固定测试时钟的「今天」 */
    private static final LocalDate TODAY = YogaSecurityTestConfig.FIXED_TODAY;

    private static final ObjectMapper JSON = new ObjectMapper();

    private JdbcTemplate jdbcTemplate;

    @Autowired
    @Qualifier("businessClock")
    private Clock productionBusinessClock;

    @Autowired
    @Qualifier("fixedBusinessClock")
    private Clock fixedTestClock;

    @Test
    @DisplayName("业务时区锁定：生产 Clock、固定测试 Clock 与统一常量都使用 Asia/Shanghai")
    void businessZone_shouldBeShanghai() {
        assertEquals("Asia/Shanghai", BusinessTimeConfig.BUSINESS_ZONE.getId(), "生产业务时区常量");
        assertEquals(BusinessTimeConfig.BUSINESS_ZONE, productionBusinessClock.getZone(), "生产 Clock 使用业务时区");
        assertEquals(BusinessTimeConfig.BUSINESS_ZONE, fixedTestClock.getZone(), "固定测试 Clock 使用同一业务时区");
        assertEquals(BusinessTimeConfig.BUSINESS_ZONE, YogaSecurityTestConfig.TEST_ZONE, "测试固定时区与生产业务时区一致");
    }

    @BeforeEach
    void setUpData() {
        YogaSchema.reset(dataSource, "t_store", "t_course", "t_classroom", "t_coach", "t_schedule");
        jdbcTemplate = new JdbcTemplate(dataSource);
        insertStore(STORE_ID, "0001", "徐汇店");
        insertStore(OTHER_STORE_ID, "0002", "静安店");
        insertCourse(COURSE_ID, "哈他瑜伽", GROUP_COURSE);
        insertCourse(OTHER_COURSE_ID, "空中瑜伽", SPECIAL_COURSE);
        insertClassroom(CLASSROOM_ID, STORE_ID, "瑜伽团课大教室");
        insertCoach(COACH_ID, "林教练");
        insertCoach(OTHER_COACH_ID, "周教练");

        LocalDateTime firstMinute = TODAY.atStartOfDay();
        // 窗口内：今天 00:00 的场次（固定时钟 10:00 必然已结束）、第 2 天、第 6 天、第 13 天
        insertSchedule(1101L, STORE_ID, COURSE_ID, GROUP_COURSE, COACH_ID, CLASSROOM_ID, TODAY, firstMinute,
                firstMinute.plusMinutes(1), 20, 2, 0, 2);
        insertSchedule(1102L, STORE_ID, COURSE_ID, GROUP_COURSE, COACH_ID, CLASSROOM_ID, TODAY.plusDays(1),
                TODAY.plusDays(1).atTime(10, 0), TODAY.plusDays(1).atTime(11, 0), 20, 2, 3, 2);
        insertSchedule(1103L, STORE_ID, COURSE_ID, GROUP_COURSE, COACH_ID, CLASSROOM_ID, TODAY.plusDays(13),
                TODAY.plusDays(13).atTime(9, 0), TODAY.plusDays(13).atTime(10, 0), 20, 2, 0, 2);
        // 窗口外：第 14 天、昨天
        insertSchedule(1104L, STORE_ID, COURSE_ID, GROUP_COURSE, COACH_ID, CLASSROOM_ID, TODAY.plusDays(14),
                TODAY.plusDays(14).atTime(9, 0), TODAY.plusDays(14).atTime(10, 0), 20, 2, 0, 2);
        insertSchedule(1105L, STORE_ID, COURSE_ID, GROUP_COURSE, COACH_ID, CLASSROOM_ID, TODAY.minusDays(1),
                TODAY.minusDays(1).atTime(9, 0), TODAY.minusDays(1).atTime(10, 0), 20, 2, 0, 2);
        // 窗口内：已取消、待上架
        insertSchedule(1106L, STORE_ID, COURSE_ID, GROUP_COURSE, COACH_ID, CLASSROOM_ID, TODAY.plusDays(2),
                TODAY.plusDays(2).atTime(16, 0), TODAY.plusDays(2).atTime(17, 0), 10, 2, 0, 3);
        insertSchedule(1107L, STORE_ID, COURSE_ID, GROUP_COURSE, COACH_ID, CLASSROOM_ID, TODAY.plusDays(3),
                TODAY.plusDays(3).atTime(15, 0), TODAY.plusDays(3).atTime(16, 0), 10, 2, 0, 1);
        // 课种快照：课程当前课种与排课快照方向相反，用于证明筛选与展示都取快照列
        insertSchedule(1108L, STORE_ID, COURSE_ID, SPECIAL_COURSE, COACH_ID, CLASSROOM_ID, TODAY.plusDays(4),
                TODAY.plusDays(4).atTime(15, 0), TODAY.plusDays(4).atTime(16, 0), 8, 2, 0, 2);
        insertSchedule(1114L, STORE_ID, OTHER_COURSE_ID, GROUP_COURSE, COACH_ID, CLASSROOM_ID, TODAY.plusDays(7),
                TODAY.plusDays(7).atTime(10, 0), TODAY.plusDays(7).atTime(11, 0), 20, 2, 0, 2);
        // 窗口内但不同教练 / 不同门店
        insertSchedule(1109L, STORE_ID, COURSE_ID, GROUP_COURSE, OTHER_COACH_ID, CLASSROOM_ID, TODAY.plusDays(5),
                TODAY.plusDays(5).atTime(11, 0), TODAY.plusDays(5).atTime(12, 0), 20, 2, 0, 2);
        insertSchedule(1110L, OTHER_STORE_ID, COURSE_ID, GROUP_COURSE, COACH_ID, CLASSROOM_ID, TODAY.plusDays(5),
                TODAY.plusDays(5).atTime(12, 0), TODAY.plusDays(5).atTime(13, 0), 20, 2, 0, 2);
        // 窗口内满员：剩余名额 0，不可约
        insertSchedule(1111L, STORE_ID, COURSE_ID, GROUP_COURSE, COACH_ID, CLASSROOM_ID, TODAY.plusDays(5),
                TODAY.plusDays(5).atTime(13, 0), TODAY.plusDays(5).atTime(14, 0), 5, 2, 5, 2);
        // 窗口内同一开始时间的两场：验证 id ASC 稳定排序
        insertSchedule(1113L, STORE_ID, COURSE_ID, GROUP_COURSE, COACH_ID, CLASSROOM_ID, TODAY.plusDays(6),
                TODAY.plusDays(6).atTime(10, 0), TODAY.plusDays(6).atTime(11, 0), 20, 2, 0, 2);
        insertSchedule(1112L, STORE_ID, COURSE_ID, GROUP_COURSE, COACH_ID, CLASSROOM_ID, TODAY.plusDays(6),
                TODAY.plusDays(6).atTime(10, 0), TODAY.plusDays(6).atTime(11, 0), 20, 2, 0, 2);
    }

    @Test
    @DisplayName("主查询：仅返回所选教练／门店／课种、窗口内、非待上架的场次，按 start_time ASC, id ASC 排序")
    void list_shouldApplyCoachStoreTypeVisibilityAndWindow() throws Exception {
        JsonNode root = performList(COACH_ID, STORE_ID, GROUP_COURSE, 1, 10);

        assertEquals(200, root.get("code").asInt());
        assertEquals(8, root.get("total").asInt(), "窗口内可见场次：今天、第 2、5、6、7、13 天");
        JsonNode rows = root.get("rows");
        assertIds(rows, 1101L, 1102L, 1106L, 1111L, 1112L, 1113L, 1114L, 1103L);

        assertEquals("已结束", rows.get(0).get("statusText").asText(), "固定时钟 10:00 时今天 00:00 的场次已结束");
        assertFalse(rows.get(0).get("bookable").asBoolean());
        assertEquals("可约", rows.get(1).get("statusText").asText());
        assertTrue(rows.get(1).get("bookable").asBoolean());
        assertEquals("已取消", rows.get(2).get("statusText").asText());
        assertFalse(rows.get(2).get("bookable").asBoolean());
        assertEquals(0, rows.get(3).get("remainingPlaces").asInt(), "满员：剩余名额 0");
        assertFalse(rows.get(3).get("bookable").asBoolean(), "满员不可约");
        assertEquals("可约", rows.get(3).get("statusText").asText());
        assertEquals("哈他瑜伽", rows.get(1).get("courseName").asText(), "课程名称实时取课程");
        assertEquals("林教练", rows.get(1).get("coachName").asText());
        assertEquals("徐汇店", rows.get(1).get("storeName").asText());
        assertEquals("瑜伽团课大教室", rows.get(1).get("classroomName").asText());
        assertEquals(GROUP_COURSE, rows.get(1).get("courseType").asInt(), "课种取排课快照");
        assertEquals("团课", rows.get(1).get("courseTypeName").asText());
        assertEquals(TODAY.plusDays(1).toString(), rows.get(1).get("scheduleDate").asText());
        assertTrue(rows.get(1).get("startTime").asText().startsWith(TODAY.plusDays(1).toString()));
        // 1114：课程当前课种是特色课，排课快照是团课 → 必须出现在团课查询里，且课种按快照展示
        assertEquals("空中瑜伽", rows.get(6).get("courseName").asText());
        assertEquals(GROUP_COURSE, rows.get(6).get("courseType").asInt(), "展示取快照而非课程当前课种");
        assertEquals("团课", rows.get(6).get("courseTypeName").asText());
    }

    @Test
    @DisplayName("课种快照真正区分：两个方向的课程当前课种与排课快照不同，筛选只按快照列")
    void list_shouldFilterByScheduleCourseTypeSnapshot() throws Exception {
        JsonNode groupRows = performList(COACH_ID, STORE_ID, GROUP_COURSE, 1, 100).get("rows");
        assertIds(groupRows, 1101L, 1102L, 1106L, 1111L, 1112L, 1113L, 1114L, 1103L);
        assertFalse(containsId(groupRows, 1108L), "课程当前是团课但快照是特色课，不能出现在团课查询");

        JsonNode specialRows = performList(COACH_ID, STORE_ID, SPECIAL_COURSE, 1, 100).get("rows");
        assertIds(specialRows, 1108L);
        assertEquals("哈他瑜伽", specialRows.get(0).get("courseName").asText());
        assertEquals(SPECIAL_COURSE, specialRows.get(0).get("courseType").asInt(), "课种取快照");
        assertEquals("特色课", specialRows.get(0).get("courseTypeName").asText());
        assertFalse(containsId(specialRows, 1114L), "课程当前是特色课但快照是团课，不能出现在特色课查询");
    }

    @Test
    @DisplayName("翻页：分页返回不同页且总数不变，末页不足一页时按实际条数返回")
    void list_shouldPaginateStably() throws Exception {
        JsonNode firstPage = performList(COACH_ID, STORE_ID, GROUP_COURSE, 1, 2);
        assertEquals(8, firstPage.get("total").asInt());
        assertIds(firstPage.get("rows"), 1101L, 1102L);

        JsonNode secondPage = performList(COACH_ID, STORE_ID, GROUP_COURSE, 2, 2);
        assertEquals(8, secondPage.get("total").asInt());
        assertIds(secondPage.get("rows"), 1106L, 1111L);

        JsonNode lastPage = performList(COACH_ID, STORE_ID, GROUP_COURSE, 4, 2);
        assertEquals(8, lastPage.get("total").asInt());
        assertIds(lastPage.get("rows"), 1114L, 1103L);
    }

    @Test
    @DisplayName("默认每页 10 条：不传分页参数时返回全部 8 条")
    void list_shouldDefaultToTenPerPage() throws Exception {
        JsonNode root = performList(COACH_ID, STORE_ID, GROUP_COURSE, null, null);
        assertEquals(8, root.get("rows").size());
    }

    @Test
    @DisplayName("非法参数：门店／课种缺失、课种越界、分页越界、ID 格式非法都返回业务码 400")
    void list_invalidParams_shouldReturnBadRequest() throws Exception {
        assertBadRequest(getList(COACH_ID).param("courseType", "1"));
        assertBadRequest(getList(COACH_ID).param("storeId", String.valueOf(STORE_ID)));
        assertBadRequest(getList(COACH_ID).param("storeId", String.valueOf(STORE_ID)).param("courseType", "9"));
        assertBadRequest(getList(COACH_ID).param("storeId", String.valueOf(STORE_ID)).param("courseType", "abc"));
        assertBadRequest(getList(COACH_ID).param("storeId", "abc").param("courseType", "1"));
        assertBadRequest(getList(COACH_ID).param("storeId", String.valueOf(STORE_ID)).param("courseType", "1")
                .param("pageNum", "0"));
        assertBadRequest(getList(COACH_ID).param("storeId", String.valueOf(STORE_ID)).param("courseType", "1")
                .param("pageSize", "101"));
        assertBadRequest(getList(COACH_ID).param("storeId", String.valueOf(STORE_ID)).param("courseType", "1")
                .param("pageSize", "0"));
        assertBadRequest(
                get("/api/coaches/abc/schedules").param("storeId", String.valueOf(STORE_ID)).param("courseType", "1"));
    }

    @Test
    @DisplayName("教练不存在 → 404；门店不存在 → 404")
    void list_missingCoachOrStore_shouldReturnNotFound() throws Exception {
        JsonNode missingCoach = performList(999999999L, STORE_ID, GROUP_COURSE, 1, 10);
        assertEquals(404, missingCoach.get("code").asInt());
        assertEquals("教练不存在或已被删除", missingCoach.get("msg").asText());

        JsonNode missingStore = performList(COACH_ID, 999999999L, GROUP_COURSE, 1, 10);
        assertEquals(404, missingStore.get("code").asInt());
        assertEquals("门店不存在或已被删除", missingStore.get("msg").asText());
    }

    @Test
    @DisplayName("单日排课接口：参数合法时正常返回，缺失参数统一返回 400")
    void existingPublicScheduleEndpoint_shouldKeepBehavior() throws Exception {
        MvcResult result = mockMvc
                .perform(get("/api/schedules").param("storeId", String.valueOf(STORE_ID))
                        .param("courseType", String.valueOf(GROUP_COURSE)).param("date", TODAY.toString()))
                .andExpect(status().isOk()).andReturn();
        JsonNode root = JSON.readTree(new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8));
        assertEquals(200, root.get("code").asInt());
        assertEquals(1, root.get("total").asInt(), "单日接口只返回该日期的 1 条可见排课");

        mockMvc.perform(get("/api/schedules")).andExpect(status().isOk()).andExpect(jsonPath("$.code").value(400));
    }

    private JsonNode performList(Long coachId, Long storeId, Integer courseType, Integer pageNum, Integer pageSize)
            throws Exception {
        MockHttpServletRequestBuilder request = getList(coachId).param("storeId", String.valueOf(storeId))
                .param("courseType", String.valueOf(courseType));
        if (pageNum != null) {
            request.param("pageNum", String.valueOf(pageNum));
        }
        if (pageSize != null) {
            request.param("pageSize", String.valueOf(pageSize));
        }
        MvcResult result = mockMvc.perform(request).andExpect(status().isOk()).andReturn();
        return JSON.readTree(new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8));
    }

    private MockHttpServletRequestBuilder getList(Long coachId) {
        return get("/api/coaches/" + coachId + "/schedules");
    }

    private void assertBadRequest(MockHttpServletRequestBuilder request) throws Exception {
        MvcResult result = mockMvc.perform(request).andExpect(status().isOk()).andReturn();
        String body = new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
        assertEquals(400, JSON.readTree(body).get("code").asInt(), "应返回业务码 400：" + body);
    }

    private void assertIds(JsonNode rows, Long... expectedIds) {
        assertEquals(expectedIds.length, rows.size());
        for (int index = 0; index < expectedIds.length; index++) {
            assertEquals(String.valueOf(expectedIds[index]), rows.get(index).get("id").asText(),
                    "第 " + index + " 条排课ID");
        }
    }

    private boolean containsId(JsonNode rows, long id) {
        for (JsonNode row : rows) {
            if (row.get("id").asText().equals(String.valueOf(id))) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------
    // 夹具
    // ------------------------------------------------------------------

    private void insertStore(long id, String storeNo, String name) {
        jdbcTemplate.update(
                "INSERT INTO t_store (id, store_no, name, store_type, region_code, phone, business_hours,"
                        + " address) VALUES (?, ?, ?, 1, '310104', '021-12345678', '周一至周日 09:00-22:00', '漕溪北路 88 号')",
                id, storeNo, name);
    }

    private void insertCourse(long id, String name, int courseType) {
        jdbcTemplate.update("INSERT INTO t_course (id, name, course_type, cover_url, intro, difficulty) VALUES"
                + " (?, ?, ?, 'https://cdn.example.com/course/cover.jpg', '课程介绍', 2)", id, name, courseType);
    }

    private void insertClassroom(long id, long storeId, String name) {
        jdbcTemplate.update("INSERT INTO t_classroom (id, store_id, name) VALUES (?, ?, ?)", id, storeId, name);
    }

    private void insertCoach(long id, String name) {
        jdbcTemplate.update("INSERT INTO t_coach (id, name, level) VALUES (?, ?, 1)", id, name);
    }

    private void insertSchedule(long id, long storeId, long courseId, int courseType, long coachId, long classroomId,
            LocalDate scheduleDate, LocalDateTime startTime, LocalDateTime endTime, int maxPersons, int minPersons,
            int bookedPersons, int status) {
        jdbcTemplate.update(
                "INSERT INTO t_schedule (id, store_id, course_id, course_type, coach_id, classroom_id,"
                        + " schedule_date, start_time, end_time, max_persons, min_persons, booked_persons, status)"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id, storeId, courseId, courseType, coachId, classroomId, Date.valueOf(scheduleDate),
                Timestamp.valueOf(startTime), Timestamp.valueOf(endTime), maxPersons, minPersons, bookedPersons,
                status);
    }
}
