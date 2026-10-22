package com.techplant.yoga.member.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import com.techplant.yoga.member.domain.MemberDO;

/**
 * 用户端安全链（{@code /api/**}）的白名单 HTTP 集成测试。
 *
 * <p>验证三个新增教练只读路径对游客与会员开放、白名单只放行 GET（写操作仍要求身份）、
 * 合法参数之外返回 400、缺失教练或门店返回 404。</p>
 */
@DisplayName("用户端安全链白名单（真实过滤链：游客／会员可读、写操作拦截、400／404）")
class MemberSecurityChainHttpTest extends AbstractYogaHttpIntegrationTest {
    private static final long COACH_ID = 400L;

    private static final long STORE_ID = 100L;

    private static final long COURSE_ID = 300L;

    private static final long CLASSROOM_ID = 500L;

    private static final long MEMBER_ID = 1856739201475235902L;

    private static final ObjectMapper JSON = new ObjectMapper();

    private JdbcTemplate jdbcTemplate;

    private String memberToken;

    @BeforeEach
    void setUpData() {
        YogaSchema.reset(dataSource, "t_coach", "t_store", "t_course", "t_classroom", "t_schedule", "t_member");
        jdbcTemplate = new JdbcTemplate(dataSource);
        LocalDate today = LocalDate.now();
        jdbcTemplate.update(
                "INSERT INTO t_coach (id, name, avatar_url, intro, level) VALUES (?, '林教练', NULL," + " '专注基础体式', 2)",
                COACH_ID);
        jdbcTemplate.update("INSERT INTO t_store (id, store_no, name, store_type, region_code, phone,"
                + " business_hours, address) VALUES (?, '0001', '徐汇店', 1, '310104', '021-12345678',"
                + " '周一至周日 09:00-22:00', '漕溪北路 88 号')", STORE_ID);
        jdbcTemplate.update(
                "INSERT INTO t_course (id, name, course_type, intro, difficulty) VALUES (?, '哈他瑜伽'," + " 1, '课程介绍', 2)",
                COURSE_ID);
        jdbcTemplate.update("INSERT INTO t_classroom (id, store_id, name) VALUES (?, ?, '瑜伽团课大教室')", CLASSROOM_ID,
                STORE_ID);
        LocalDateTime start = today.plusDays(1).atTime(10, 0);
        jdbcTemplate.update(
                "INSERT INTO t_schedule (id, store_id, course_id, course_type, coach_id, classroom_id,"
                        + " schedule_date, start_time, end_time, max_persons, min_persons, booked_persons, status)"
                        + " VALUES (9001, ?, ?, 1, ?, ?, ?, ?, ?, 20, 2, 0, 2)",
                STORE_ID, COURSE_ID, COACH_ID, CLASSROOM_ID, Date.valueOf(today.plusDays(1)), Timestamp.valueOf(start),
                Timestamp.valueOf(start.plusHours(1)));
        jdbcTemplate.update("INSERT INTO t_member (id, phone, password, phone_last4, name, gender, level) VALUES"
                + " (?, '13800138000', 'hash', '8000', '忻会员8000', 0, 0)", MEMBER_ID);

        MemberDO member = new MemberDO();
        member.setId(MEMBER_ID);
        member.setPhone("13800138000");
        memberToken = memberTokenService.createToken(member).getToken();
    }

    @Test
    @DisplayName("游客可读三个只读路径：金牌列表、教练详情、教练排课")
    void guest_shouldReadCoachPublicEndpoints() throws Exception {
        assertSuccess(get("/api/coaches/featured"));
        assertSuccess(get("/api/coaches/" + COACH_ID));
        assertSuccess(coachSchedules());
    }

    @Test
    @DisplayName("会员 token 也可读三个只读路径")
    void member_shouldReadCoachPublicEndpoints() throws Exception {
        assertSuccess(get("/api/coaches/featured").header(AUTHORIZATION, bearer(memberToken)));
        assertSuccess(get("/api/coaches/" + COACH_ID).header(AUTHORIZATION, bearer(memberToken)));
        assertSuccess(coachSchedules().header(AUTHORIZATION, bearer(memberToken)));
    }

    @Test
    @DisplayName("白名单只放行 GET：游客的写操作仍返回业务码 401，数据不变")
    void guest_writeOperations_shouldBeRejected() throws Exception {
        assertUnauthorized(post("/api/coaches/featured"));
        assertUnauthorized(put("/api/coaches/" + COACH_ID));
        assertUnauthorized(delete("/api/coaches/" + COACH_ID));
        assertUnauthorized(post("/api/coaches/" + COACH_ID + "/schedules").param("storeId", String.valueOf(STORE_ID))
                .param("courseType", "1"));

        assertEquals(1, jdbcTemplate
                .queryForObject("SELECT COUNT(*) FROM t_coach WHERE id = ?", Integer.class, COACH_ID).intValue(),
                "教练不得被删除");
        assertEquals(2, jdbcTemplate.queryForObject("SELECT level FROM t_coach WHERE id = ?", Integer.class, COACH_ID)
                .intValue(), "等级不得被修改");
    }

    @Test
    @DisplayName("非法参数：非法教练 ID／非法门店参数／越界课种返回业务码 400")
    void invalidParams_shouldReturnBadRequest() throws Exception {
        assertBadRequest(get("/api/coaches/abc"));
        assertBadRequest(
                get("/api/coaches/" + COACH_ID + "/schedules").param("storeId", "abc").param("courseType", "1"));
        assertBadRequest(get("/api/coaches/" + COACH_ID + "/schedules").param("storeId", String.valueOf(STORE_ID))
                .param("courseType", "9"));
    }

    @Test
    @DisplayName("类型转换失败：返回中文提示「参数[字段]格式非法」，不暴露框架英文原文")
    void invalidParamType_shouldReturnFriendlyMessage() throws Exception {
        JsonNode storeIdError = perform(
                get("/api/coaches/" + COACH_ID + "/schedules").param("storeId", "abc").param("courseType", "1"));
        assertEquals(400, storeIdError.get("code").asInt());
        assertEquals("参数[storeId]格式非法", storeIdError.get("msg").asText());

        JsonNode pageSizeError = perform(get("/api/coaches/" + COACH_ID + "/schedules")
                .param("storeId", String.valueOf(STORE_ID)).param("courseType", "1").param("pageSize", "abc"));
        assertEquals(400, pageSizeError.get("code").asInt());
        assertEquals("参数[pageSize]格式非法", pageSizeError.get("msg").asText());

        JsonNode coachIdError = perform(get("/api/coaches/abc"));
        assertEquals(400, coachIdError.get("code").asInt());
        assertEquals("参数[coachId]格式非法", coachIdError.get("msg").asText());
    }

    @Test
    @DisplayName("缺失教练或门店返回业务码 404")
    void missingCoachOrStore_shouldReturnNotFound() throws Exception {
        assertNotFound(get("/api/coaches/999999999"));
        assertNotFound(
                get("/api/coaches/" + COACH_ID + "/schedules").param("storeId", "999999999").param("courseType", "1"));
    }

    private MockHttpServletRequestBuilder coachSchedules() {
        return get("/api/coaches/" + COACH_ID + "/schedules").param("storeId", String.valueOf(STORE_ID))
                .param("courseType", "1");
    }

    private void assertSuccess(MockHttpServletRequestBuilder request) throws Exception {
        JsonNode root = perform(request);
        assertEquals(200, root.get("code").asInt(), "应可匿名访问：" + root);
    }

    private void assertUnauthorized(MockHttpServletRequestBuilder request) throws Exception {
        JsonNode root = perform(request);
        assertEquals(401, root.get("code").asInt(), "写操作应被安全链拦截：" + root);
    }

    private void assertBadRequest(MockHttpServletRequestBuilder request) throws Exception {
        JsonNode root = perform(request);
        assertEquals(400, root.get("code").asInt(), "非法参数应返回 400：" + root);
    }

    private void assertNotFound(MockHttpServletRequestBuilder request) throws Exception {
        JsonNode root = perform(request);
        assertEquals(404, root.get("code").asInt(), "缺失资源应返回 404：" + root);
    }

    private JsonNode perform(MockHttpServletRequestBuilder request) throws Exception {
        MvcResult result = mockMvc.perform(request).andExpect(status().isOk()).andReturn();
        return JSON.readTree(new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8));
    }
}
