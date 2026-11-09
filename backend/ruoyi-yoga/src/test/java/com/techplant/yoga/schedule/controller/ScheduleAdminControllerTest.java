package com.techplant.yoga.schedule.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.framework.web.exception.GlobalExceptionHandler;
import com.techplant.yoga.classroom.dao.ClassroomDaoImpl;
import com.techplant.yoga.classroom.service.impl.ClassroomServiceImpl;
import com.techplant.yoga.coach.dao.CoachDaoImpl;
import com.techplant.yoga.coach.service.impl.CoachServiceImpl;
import com.techplant.yoga.common.config.JacksonConfig;
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
import com.techplant.yoga.reservation.service.impl.ReservationServiceImpl;
import com.techplant.yoga.schedule.dao.ScheduleDaoImpl;
import com.techplant.yoga.schedule.service.ScheduleService;
import com.techplant.yoga.schedule.service.impl.ScheduleBookingServiceImpl;
import com.techplant.yoga.schedule.service.impl.ScheduleServiceImpl;
import com.techplant.yoga.store.dao.StoreDaoImpl;
import com.techplant.yoga.store.dao.StoreRegionDictDaoImpl;
import com.techplant.yoga.store.service.impl.StoreServiceImpl;

/**
 * 管理端排课新增与修改接口的 HTTP 层集成测试（MockMvc ＋ Testcontainers MySQL，service／DAO 都是真实实现）。
 *
 * <p>覆盖排队上限与停止排队时间的必填、取值范围、落库与详情返回；管理端鉴权链不在本测试范围内。</p>
 */
@ContextConfiguration(classes = {YogaMyBatisTestConfig.class, StoreDaoImpl.class, StoreRegionDictDaoImpl.class,
    StoreServiceImpl.class, ClassroomDaoImpl.class, ClassroomServiceImpl.class, CoachDaoImpl.class,
    CoachServiceImpl.class, CourseDaoImpl.class, CourseServiceImpl.class, MemberDaoImpl.class,
    MemberLevelDictDaoImpl.class, MemberServiceImpl.class, MemberCardDaoImpl.class, MemberCardTransactionDaoImpl.class,
    MemberCardTransactionServiceImpl.class, CardNoGenerator.class, MemberCardServiceImpl.class, ScheduleDaoImpl.class,
    ScheduleBookingServiceImpl.class, ScheduleServiceImpl.class, ReservationDaoImpl.class,
    ReservationServiceImpl.class})
@DisplayName("ScheduleAdminController 接口测试（MockMvc：排队上限与停止排队时间的校验、落库与返回）")
class ScheduleAdminControllerTest extends AbstractYogaIntegrationTest {
    private static final long STORE_ID = 1000L;

    private static final long COURSE_ID = 3000L;

    private static final long COACH_ID = 4000L;

    private static final long CLASSROOM_ID = 5000L;

    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired
    private ScheduleService scheduleService;

    private MockMvc mockMvc;

    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        YogaSchema.reset(dataSource, "t_reservation", "t_schedule", "t_course", "t_coach", "t_classroom", "t_store");
        jdbcTemplate = new JdbcTemplate(dataSource);
        insertFixtures();

        ObjectMapper objectMapper = Jackson2ObjectMapperBuilder.json()
                .modulesToInstall(new JacksonConfig.BusinessIdToStringModule()).build();
        mockMvc = MockMvcBuilders.standaloneSetup(new ScheduleAdminController(scheduleService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper)).build();
    }

    @Test
    @DisplayName("新增与修改排课：排队上限与停止排队时间落库，详情与列表原样返回，边界值 0 与上限可保存")
    void createAndUpdate_shouldPersistQueueConfig() throws Exception {
        mockMvc.perform(post("/admin/schedules").contentType(MediaType.APPLICATION_JSON)
                .content(body(10, 3, 110))).andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));
        Map<String, Object> row = jdbcTemplate
                .queryForMap("SELECT id, queue_limit, queue_stop_minutes FROM t_schedule");
        assertEquals(3, ((Number) row.get("queue_limit")).intValue());
        assertEquals(110, ((Number) row.get("queue_stop_minutes")).intValue());
        String scheduleId = String.valueOf(row.get("id"));

        mockMvc.perform(get("/admin/schedules/" + scheduleId)).andExpect(jsonPath("$.data.queueLimit").value(3))
                .andExpect(jsonPath("$.data.queueStopMinutes").value(110));
        mockMvc.perform(get("/admin/schedules").param("pageNum", "1").param("pageSize", "10"))
                .andExpect(jsonPath("$.rows[0].queueLimit").value(3))
                .andExpect(jsonPath("$.rows[0].queueStopMinutes").value(110));

        mockMvc.perform(put("/admin/schedules/" + scheduleId).contentType(MediaType.APPLICATION_JSON)
                .content(body(10, 0, 1440))).andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.queueLimit").value(0))
                .andExpect(jsonPath("$.data.queueStopMinutes").value(1440));
        mockMvc.perform(put("/admin/schedules/" + scheduleId).contentType(MediaType.APPLICATION_JSON)
                .content(body(10, 999, 0))).andExpect(jsonPath("$.code").value(200));
        assertEquals(999, intOf("SELECT queue_limit FROM t_schedule WHERE id = " + scheduleId));
        assertEquals(0, intOf("SELECT queue_stop_minutes FROM t_schedule WHERE id = " + scheduleId));
    }

    @Test
    @DisplayName("新增与修改排课：排队上限、停止排队时间缺失或越界返回业务码 400，数据不变")
    void createAndUpdate_withIllegalQueueConfig_shouldReject() throws Exception {
        assertBadRequest(post("/admin/schedules"), body(10, null, 110), "排队上限不能为空");
        assertBadRequest(post("/admin/schedules"), body(10, 3, null), "停止排队时间不能为空");
        assertBadRequest(post("/admin/schedules"), body(10, -1, 110), "排队上限取值为 0～999");
        assertBadRequest(post("/admin/schedules"), body(10, 1000, 110), "排队上限取值为 0～999");
        assertBadRequest(post("/admin/schedules"), body(10, 3, -1), "停止排队时间取值为 0～1440 分钟");
        assertBadRequest(post("/admin/schedules"), body(10, 3, 1441), "停止排队时间取值为 0～1440 分钟");
        assertEquals(0, intOf("SELECT COUNT(*) FROM t_schedule"), "非法请求不落库");

        mockMvc.perform(post("/admin/schedules").contentType(MediaType.APPLICATION_JSON).content(body(10, 3, 110)))
                .andExpect(jsonPath("$.code").value(200));
        String scheduleId = String.valueOf(jdbcTemplate.queryForObject("SELECT id FROM t_schedule", Long.class));
        assertBadRequest(put("/admin/schedules/" + scheduleId), body(10, 1000, 110), "排队上限取值为 0～999");
        assertBadRequest(put("/admin/schedules/" + scheduleId), body(10, 3, null), "停止排队时间不能为空");
        assertEquals(3, intOf("SELECT queue_limit FROM t_schedule WHERE id = " + scheduleId), "非法修改不改动配置");
    }

    private void assertBadRequest(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request,
            String content, String message) throws Exception {
        mockMvc.perform(request.contentType(MediaType.APPLICATION_JSON).content(content)).andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400)).andExpect(jsonPath("$.msg").value(message));
    }

    private String body(int maxPersons, Integer queueLimit, Integer queueStopMinutes) throws Exception {
        LocalDate day = LocalDate.now().plusDays(1);
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("storeId", String.valueOf(STORE_ID));
        body.put("courseId", String.valueOf(COURSE_ID));
        body.put("coachId", String.valueOf(COACH_ID));
        body.put("classroomId", String.valueOf(CLASSROOM_ID));
        body.put("startTime", day + " 15:00:00");
        body.put("endTime", day + " 16:00:00");
        body.put("maxPersons", maxPersons);
        body.put("minPersons", 1);
        body.put("queueLimit", queueLimit);
        body.put("queueStopMinutes", queueStopMinutes);
        return JSON.writeValueAsString(body);
    }

    private int intOf(String sql) {
        Integer value = jdbcTemplate.queryForObject(sql, Integer.class);
        return value == null ? 0 : value;
    }

    private void insertFixtures() {
        jdbcTemplate.update("INSERT INTO t_store (id, store_no, name, store_type, region_code, phone,"
                + " business_hours, address, create_by, create_time, update_by, update_time)"
                + " VALUES (?, '0001', '徐汇店', 1, '310104', '021-12345678', '周一至周日 09:00-22:00',"
                + " '漕溪北路 88 号', 1, NOW(), 1, NOW())", STORE_ID);
        jdbcTemplate.update("INSERT INTO t_classroom (id, store_id, name, create_by, create_time, update_by,"
                + " update_time) VALUES (?, ?, '瑜伽团课大教室', 1, NOW(), 1, NOW())", CLASSROOM_ID, STORE_ID);
        jdbcTemplate.update("INSERT INTO t_coach (id, name, avatar_url, intro, phone, create_by, create_time,"
                + " update_by, update_time) VALUES (?, '林教练', NULL, NULL, NULL, 1, NOW(), 1, NOW())", COACH_ID);
        jdbcTemplate.update("INSERT INTO t_course (id, name, course_type, cover_url, intro, difficulty, create_by,"
                + " create_time, update_by, update_time) VALUES (?, '哈他瑜伽', 1, NULL, NULL, 2, 1, NOW(), 1, NOW())",
                COURSE_ID);
    }
}
