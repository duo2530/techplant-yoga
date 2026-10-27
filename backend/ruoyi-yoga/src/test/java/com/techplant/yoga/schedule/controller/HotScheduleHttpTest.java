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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.techplant.yoga.common.testcontainers.AbstractYogaHttpIntegrationTest;
import com.techplant.yoga.common.testcontainers.YogaSchema;

/**
 * 首页热门课程接口的 HTTP 集成测试：真实用户端安全过滤链 ＋ 真实 MySQL。
 *
 * <p>覆盖免登录访问、排序与截取、日期窗口首尾、状态与名额过滤、私教课排除、门店隔离、
 * 课种快照、关联资料缺失及参数错误。</p>
 */
@DisplayName("首页热门课程接口（真实安全链：预约量排序 / 14 天窗口 / 过滤条件 / 参数错误）")
class HotScheduleHttpTest extends AbstractYogaHttpIntegrationTest {
    private static final long STORE_ID = 100L;

    private static final long OTHER_STORE_ID = 200L;

    private static final long COURSE_ID = 300L;

    private static final long BOUTIQUE_COURSE_ID = 301L;

    private static final long PERSONAL_COURSE_ID = 302L;

    private static final long COACH_ID = 400L;

    private static final long CLASSROOM_ID = 500L;

    private static final int GROUP_COURSE = 1;

    private static final int BOUTIQUE_COURSE = 2;

    private static final int PERSONAL_COURSE = 3;

    private static final int PENDING = 1;

    private static final int PUBLISHED = 2;

    private static final int CANCELLED = 3;

    private static final ObjectMapper JSON = new ObjectMapper();

    private LocalDate today;

    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUpData() {
        today = LocalDate.now();
        YogaSchema.reset(dataSource, "t_store", "t_course", "t_classroom", "t_coach", "t_schedule");
        jdbcTemplate = new JdbcTemplate(dataSource);
        insertStore(STORE_ID, "0001", "徐汇店");
        insertStore(OTHER_STORE_ID, "0002", "静安店");
        insertCourse(COURSE_ID, "哈他瑜伽", GROUP_COURSE, "https://cdn.example.com/course/1.jpg", 2);
        insertCourse(BOUTIQUE_COURSE_ID, "迷人腰线", BOUTIQUE_COURSE, null, 3);
        insertCourse(PERSONAL_COURSE_ID, "私教体态", PERSONAL_COURSE, null, 1);
        insertClassroom(CLASSROOM_ID, STORE_ID, "瑜伽团课大教室");
        insertCoach(COACH_ID, "赵赵老师");
    }

    @Test
    @DisplayName("按已预约人数降序排列，人数相同按开始时间升序、再按 id 升序，只取前 4 条")
    void hot_shouldOrderByBookedPersonsAndLimitToFour() throws Exception {
        insertPublished(1001L, COURSE_ID, GROUP_COURSE, 1, 10, 3);
        insertPublished(1002L, COURSE_ID, GROUP_COURSE, 2, 10, 8);
        insertPublished(1003L, BOUTIQUE_COURSE_ID, BOUTIQUE_COURSE, 3, 10, 5);
        insertPublished(1005L, COURSE_ID, GROUP_COURSE, 4, 10, 5);
        insertPublished(1004L, COURSE_ID, GROUP_COURSE, 4, 10, 5);
        insertPublished(1006L, COURSE_ID, GROUP_COURSE, 5, 10, 1);

        JsonNode root = performHot(STORE_ID);

        assertEquals(200, root.get("code").asInt());
        JsonNode data = root.get("data");
        assertIds(data, 1002L, 1003L, 1004L, 1005L);
    }

    @Test
    @DisplayName("同一课程的多节排课可以同时入选")
    void hot_shouldAllowSameCourseMultipleSchedules() throws Exception {
        insertPublished(1001L, COURSE_ID, GROUP_COURSE, 1, 10, 6);
        insertPublished(1002L, COURSE_ID, GROUP_COURSE, 2, 10, 4);

        assertIds(performHot(STORE_ID).get("data"), 1001L, 1002L);
    }

    @Test
    @DisplayName("卡片字段：课种、课程名称、封面、难度、教练姓名与起止时间，不含其他字段")
    void hot_shouldReturnCardFields() throws Exception {
        insertPublished(1001L, BOUTIQUE_COURSE_ID, BOUTIQUE_COURSE, 2, 10, 4);
        insertPublished(1002L, COURSE_ID, GROUP_COURSE, 3, 10, 2);

        JsonNode data = performHot(STORE_ID).get("data");
        assertEquals(2, data.size());

        JsonNode boutique = data.get(0);
        assertEquals("1001", boutique.get("id").asText());
        assertEquals("迷人腰线", boutique.get("courseName").asText());
        assertTrue(boutique.get("courseCoverUrl").isNull(), "未设置封面返回 null");
        assertEquals(BOUTIQUE_COURSE, boutique.get("courseType").asInt());
        assertEquals("精品课", boutique.get("courseTypeName").asText());
        assertEquals(3, boutique.get("difficulty").asInt());
        assertEquals("赵赵老师", boutique.get("coachName").asText());
        assertEquals(today.plusDays(2).toString() + " 19:00:00", boutique.get("startTime").asText());
        assertEquals(today.plusDays(2).toString() + " 20:00:00", boutique.get("endTime").asText());
        assertEquals(9, boutique.size(), "只输出热门卡片模型字段");
        assertFalse(boutique.has("bookedPersons"));
        assertFalse(boutique.has("bookable"));

        JsonNode group = data.get(1);
        assertEquals("https://cdn.example.com/course/1.jpg", group.get("courseCoverUrl").asText());
        assertEquals("团课", group.get("courseTypeName").asText());
        assertEquals(2, group.get("difficulty").asInt());
    }

    @Test
    @DisplayName("日期窗口：今天起第 13 天入选，第 14 天与已开始的今日场次不入选")
    void hot_shouldApplyDateWindowAndStartedFilter() throws Exception {
        insertPublished(1001L, COURSE_ID, GROUP_COURSE, 13, 10, 3);
        insertPublished(1002L, COURSE_ID, GROUP_COURSE, 14, 10, 9);
        insertSchedule(1003L, STORE_ID, COURSE_ID, GROUP_COURSE, COACH_ID, CLASSROOM_ID, today,
                LocalDateTime.now().minusHours(1), LocalDateTime.now().plusHours(1), 10, 2, 9, PUBLISHED);
        insertSchedule(1004L, STORE_ID, COURSE_ID, GROUP_COURSE, COACH_ID, CLASSROOM_ID, today.minusDays(1),
                today.minusDays(1).atTime(10, 0), today.minusDays(1).atTime(11, 0), 10, 2, 9, PUBLISHED);

        assertIds(performHot(STORE_ID).get("data"), 1001L);
    }

    @Test
    @DisplayName("排除待上架、已取消、满员、无人预约、私教课与其他门店的排课")
    void hot_shouldExcludeIneligibleSchedules() throws Exception {
        insertPublished(1001L, COURSE_ID, GROUP_COURSE, 1, 10, 2);
        insertSchedule(1002L, STORE_ID, COURSE_ID, GROUP_COURSE, COACH_ID, CLASSROOM_ID, today.plusDays(1),
                today.plusDays(1).atTime(19, 0), today.plusDays(1).atTime(20, 0), 10, 2, 9, PENDING);
        insertSchedule(1003L, STORE_ID, COURSE_ID, GROUP_COURSE, COACH_ID, CLASSROOM_ID, today.plusDays(1),
                today.plusDays(1).atTime(19, 0), today.plusDays(1).atTime(20, 0), 10, 2, 9, CANCELLED);
        insertPublished(1004L, COURSE_ID, GROUP_COURSE, 1, 10, 10);
        insertPublished(1005L, COURSE_ID, GROUP_COURSE, 1, 10, 0);
        insertPublished(1006L, PERSONAL_COURSE_ID, PERSONAL_COURSE, 1, 3, 2);
        insertSchedule(1007L, OTHER_STORE_ID, COURSE_ID, GROUP_COURSE, COACH_ID, CLASSROOM_ID, today.plusDays(1),
                today.plusDays(1).atTime(19, 0), today.plusDays(1).atTime(20, 0), 10, 2, 9, PUBLISHED);

        assertIds(performHot(STORE_ID).get("data"), 1001L);
    }

    @Test
    @DisplayName("课种取排课快照：课程当前课种与快照不同，展示快照课种，私教快照仍被排除")
    void hot_shouldUseCourseTypeSnapshot() throws Exception {
        insertPublished(1001L, COURSE_ID, BOUTIQUE_COURSE, 1, 10, 4);
        insertPublished(1002L, BOUTIQUE_COURSE_ID, PERSONAL_COURSE, 1, 10, 5);

        JsonNode data = performHot(STORE_ID).get("data");
        assertIds(data, 1001L);
        assertEquals(BOUTIQUE_COURSE, data.get(0).get("courseType").asInt());
        assertEquals("精品课", data.get(0).get("courseTypeName").asText());
        assertEquals("哈他瑜伽", data.get(0).get("courseName").asText());
    }

    @Test
    @DisplayName("课程与教练已被删除时仍返回排课，名称为空字符串，封面与难度为 null")
    void hot_shouldKeepScheduleWhenRelationsDeleted() throws Exception {
        insertSchedule(1001L, STORE_ID, 999999L, GROUP_COURSE, 888888L, CLASSROOM_ID, today.plusDays(1),
                today.plusDays(1).atTime(19, 0), today.plusDays(1).atTime(20, 0), 10, 2, 3, PUBLISHED);

        JsonNode data = performHot(STORE_ID).get("data");
        assertIds(data, 1001L);
        assertEquals("", data.get(0).get("courseName").asText());
        assertTrue(data.get(0).get("courseCoverUrl").isNull());
        assertTrue(data.get(0).get("difficulty").isNull());
        assertEquals("", data.get(0).get("coachName").asText());
    }

    @Test
    @DisplayName("没有符合条件的排课时返回空数组")
    void hot_noCandidates_shouldReturnEmptyArray() throws Exception {
        JsonNode root = performHot(STORE_ID);
        assertEquals(200, root.get("code").asInt());
        assertTrue(root.get("data").isArray());
        assertEquals(0, root.get("data").size());
    }

    @Test
    @DisplayName("门店不存在返回 404；storeId 缺失或格式非法返回 400；既有今日可约接口不受影响")
    void hot_invalidStore_shouldReturnErrors() throws Exception {
        JsonNode missingStore = performHot(999999999L);
        assertEquals(404, missingStore.get("code").asInt());
        assertEquals("门店不存在或已被删除", missingStore.get("msg").asText());

        assertCode(get("/api/schedules/hot"), 400);
        assertCode(get("/api/schedules/hot").param("storeId", "abc"), 400);
        assertCode(get("/api/schedules/today-bookable").param("storeId", String.valueOf(STORE_ID)), 200);
    }

    private JsonNode performHot(long storeId) throws Exception {
        return perform(get("/api/schedules/hot").param("storeId", String.valueOf(storeId)));
    }

    private JsonNode perform(MockHttpServletRequestBuilder request) throws Exception {
        MvcResult result = mockMvc.perform(request).andExpect(status().isOk()).andReturn();
        return JSON.readTree(new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8));
    }

    private void assertCode(MockHttpServletRequestBuilder request, int expected) throws Exception {
        JsonNode root = perform(request);
        assertEquals(expected, root.get("code").asInt(), "业务码：" + root);
    }

    private void assertIds(JsonNode rows, Long... expectedIds) {
        assertEquals(expectedIds.length, rows.size(), "返回条数：" + rows);
        for (int index = 0; index < expectedIds.length; index++) {
            assertEquals(String.valueOf(expectedIds[index]), rows.get(index).get("id").asText(),
                    "第 " + index + " 条排课ID");
        }
    }

    private void insertPublished(long id, long courseId, int courseType, int plusDays, int maxPersons,
            int bookedPersons) {
        insertSchedule(id, STORE_ID, courseId, courseType, COACH_ID, CLASSROOM_ID, today.plusDays(plusDays),
                today.plusDays(plusDays).atTime(19, 0), today.plusDays(plusDays).atTime(20, 0), maxPersons, 2,
                bookedPersons, PUBLISHED);
    }

    private void insertStore(long id, String storeNo, String name) {
        jdbcTemplate.update(
                "INSERT INTO t_store (id, store_no, name, store_type, region_code, phone, business_hours,"
                        + " address) VALUES (?, ?, ?, 1, '310104', '021-12345678', '周一至周日 09:00-22:00', '漕溪北路 88 号')",
                id, storeNo, name);
    }

    private void insertCourse(long id, String name, int courseType, String coverUrl, int difficulty) {
        jdbcTemplate.update("INSERT INTO t_course (id, name, course_type, cover_url, intro, difficulty)"
                + " VALUES (?, ?, ?, ?, '课程介绍', ?)", id, name, courseType, coverUrl, difficulty);
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
