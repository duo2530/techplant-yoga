package com.techplant.yoga.reservation.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
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
import com.techplant.yoga.member.security.MemberLoginUser;
import com.techplant.yoga.member.service.impl.MemberServiceImpl;
import com.techplant.yoga.membercard.dao.MemberCardDaoImpl;
import com.techplant.yoga.membercard.dao.MemberCardTransactionDaoImpl;
import com.techplant.yoga.membercard.service.CardNoGenerator;
import com.techplant.yoga.membercard.service.impl.MemberCardServiceImpl;
import com.techplant.yoga.membercard.service.impl.MemberCardTransactionServiceImpl;
import com.techplant.yoga.reservation.dao.ReservationDaoImpl;
import com.techplant.yoga.reservation.dto.ReservationCreateDTO;
import com.techplant.yoga.reservation.service.ReservationService;
import com.techplant.yoga.reservation.service.impl.ReservationServiceImpl;
import com.techplant.yoga.schedule.dao.ScheduleDaoImpl;
import com.techplant.yoga.schedule.service.impl.ScheduleBookingServiceImpl;
import com.techplant.yoga.schedule.service.impl.ScheduleServiceImpl;
import com.techplant.yoga.store.dao.StoreDaoImpl;
import com.techplant.yoga.store.dao.StoreRegionDictDaoImpl;
import com.techplant.yoga.store.service.impl.StoreServiceImpl;

/**
 * 用户端预约接口的 HTTP 层集成测试（MockMvc ＋ Testcontainers MySQL，service／DAO 都是真实实现）。
 *
 * <p>覆盖统一响应、参数校验、ID 字符串化与时间格式；会员鉴权链与 Redis 不在本测试范围内，
 * 测试直接往登录上下文写会员主体。</p>
 */
@ContextConfiguration(classes = {YogaMyBatisTestConfig.class, StoreDaoImpl.class, StoreRegionDictDaoImpl.class,
    StoreServiceImpl.class, ClassroomDaoImpl.class, ClassroomServiceImpl.class, CoachDaoImpl.class,
    CoachServiceImpl.class, CourseDaoImpl.class, CourseServiceImpl.class, MemberDaoImpl.class,
    MemberLevelDictDaoImpl.class, MemberServiceImpl.class, MemberCardDaoImpl.class, MemberCardTransactionDaoImpl.class,
    MemberCardTransactionServiceImpl.class, CardNoGenerator.class, MemberCardServiceImpl.class, ScheduleDaoImpl.class,
    ScheduleBookingServiceImpl.class, ScheduleServiceImpl.class, ReservationDaoImpl.class,
    ReservationServiceImpl.class})
@DisplayName("ReservationPublicController 接口测试（MockMvc：统一响应 / 参数校验 / ID 字符串化 / 时间格式）")
class ReservationPublicControllerTest extends AbstractYogaIntegrationTest {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");

    private static final Long STORE_ID = 1000L;

    private static final Long COURSE_ID = 3000L;

    private static final Long COACH_ID = 4000L;

    private static final Long CLASSROOM_ID = 5000L;

    private static final Long MEMBER_ID = 6000L;

    private static final Long TIMES_CARD_ID = 7000L;

    private static final Long SCHEDULE_ID = 8000L;

    private static final Long SCHEDULE_TOO_EARLY_ID = 8001L;

    private static final String REQUEST_ID = "2f1a4c1e-6a2b-4c1d-9f0e-1a2b3c4d5e6f";

    @Autowired
    private ReservationService reservationService;

    private MockMvc mockMvc;

    private JdbcTemplate jdbcTemplate;

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        YogaSchema.reset(dataSource, "t_reservation", "t_member_card_transaction", "t_member_card", "t_schedule",
                "t_course", "t_coach", "t_classroom", "t_store", "t_member");
        jdbcTemplate = new JdbcTemplate(dataSource);
        insertFixtures();

        MemberLoginUser member = new MemberLoginUser();
        member.setMemberId(MEMBER_ID);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(member, null));

        objectMapper = Jackson2ObjectMapperBuilder.json().modulesToInstall(new JacksonConfig.BusinessIdToStringModule())
                .build();
        mockMvc = MockMvcBuilders.standaloneSetup(new ReservationPublicController(reservationService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper)).build();
    }

    @Test
    @DisplayName("预约课程：成功返回统一响应（code/msg 且无 data），扣次与占位真实落库")
    void create_shouldReturnSuccessAndWriteReservation() throws Exception {
        mockMvc.perform(post("/api/reservations").contentType(MediaType.APPLICATION_JSON)
                .content(body(REQUEST_ID, SCHEDULE_ID, TIMES_CARD_ID, 2))).andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200)).andExpect(jsonPath("$.msg").value("预约成功"))
                .andExpect(jsonPath("$.data").doesNotExist());

        assertEquals(Integer.valueOf(1), countOf("SELECT COUNT(*) FROM t_reservation"));
        assertEquals(Integer.valueOf(2), intOf("SELECT booked_persons FROM t_schedule WHERE id = ?", SCHEDULE_ID));
        assertEquals(Integer.valueOf(10),
                intOf("SELECT remaining_times FROM t_member_card WHERE id = ?", TIMES_CARD_ID));
    }

    @Test
    @DisplayName("预约课程：必填缺失与 UUID 非法都返回业务码 500，提示语来自校验规则")
    void create_withIllegalBody_shouldReject() throws Exception {
        mockMvc.perform(post("/api/reservations").contentType(MediaType.APPLICATION_JSON).content(
                "{\"scheduleId\":\"" + SCHEDULE_ID + "\",\"memberCardId\":\"" + TIMES_CARD_ID + "\",\"persons\":1}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.msg").value("请求幂等标识不能为空"));

        mockMvc.perform(post("/api/reservations").contentType(MediaType.APPLICATION_JSON)
                .content(body("not-a-uuid", SCHEDULE_ID, TIMES_CARD_ID, 1))).andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500)).andExpect(jsonPath("$.msg").value("请求幂等标识格式非法，应为 UUID"));

        assertEquals(Integer.valueOf(0), countOf("SELECT COUNT(*) FROM t_reservation"));
    }

    @Test
    @DisplayName("预约课程：排课不存在返回 404，会员卡不属于本人也返回 404")
    void create_withMissingRelation_shouldReportNotFound() throws Exception {
        mockMvc.perform(post("/api/reservations").contentType(MediaType.APPLICATION_JSON)
                .content(body(REQUEST_ID, 999999L, TIMES_CARD_ID, 1))).andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(404)).andExpect(jsonPath("$.msg").value("排课不存在或已被删除"));

        mockMvc.perform(post("/api/reservations").contentType(MediaType.APPLICATION_JSON)
                .content(body(REQUEST_ID, SCHEDULE_ID, 999999L, 1))).andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(404)).andExpect(jsonPath("$.msg").value("会员卡不存在或不属于本人"));
    }

    @Test
    @DisplayName("我的预约：返回 TableDataInfo（total/rows/code/msg），ID 是字符串、时间是 yyyy-MM-dd HH:mm:ss")
    void list_shouldReturnTableDataInfo() throws Exception {
        createReservation();

        mockMvc.perform(get("/api/reservations").param("pageNum", "1").param("pageSize", "10"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.msg").value("查询成功")).andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.rows[0].id").isString())
                .andExpect(jsonPath("$.rows[0].scheduleId").value(String.valueOf(SCHEDULE_ID)))
                .andExpect(jsonPath("$.rows[0].courseId").value(String.valueOf(COURSE_ID)))
                .andExpect(jsonPath("$.rows[0].courseName").value("哈他瑜伽"))
                .andExpect(jsonPath("$.rows[0].courseType").value(1))
                .andExpect(jsonPath("$.rows[0].courseTypeName").value("团课"))
                .andExpect(jsonPath("$.rows[0].coachName").value("林教练"))
                .andExpect(jsonPath("$.rows[0].storeAddress").value("漕溪北路 88 号"))
                .andExpect(
                        jsonPath("$.rows[0].scheduleDate").value(LocalDate.now(BUSINESS_ZONE).plusDays(1).toString()))
                .andExpect(jsonPath("$.rows[0].startTime").value(scheduleStartTime()
                        .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))))
                .andExpect(jsonPath("$.rows[0].status").value(1))
                .andExpect(jsonPath("$.rows[0].statusText").value("已预约"));
    }

    @Test
    @DisplayName("我的预约：分页参数缺失与状态取值非法都返回 500")
    void list_withIllegalQuery_shouldReject() throws Exception {
        mockMvc.perform(get("/api/reservations")).andExpect(status().isOk()).andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.msg", Matchers.anyOf(Matchers.is("页码不能为空"), Matchers.is("每页条数不能为空"))));

        mockMvc.perform(get("/api/reservations").param("pageNum", "1").param("pageSize", "10").param("status", "9"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.msg").value("预约状态取值为 1、2 或 3"));
    }

    @Test
    @DisplayName("取消预约与签到：成功返回各自文案，签到窗口外返回 409")
    void cancelAndCheckIn_shouldReturnBusinessMessages() throws Exception {
        createReservation();
        Long reservationId = jdbcTemplate.queryForObject("SELECT MAX(id) FROM t_reservation", Long.class);

        mockMvc.perform(put("/api/reservations/" + reservationId + "/check-in")).andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(409)).andExpect(jsonPath("$.msg").value("未到签到时间"));

        mockMvc.perform(put("/api/reservations/" + reservationId + "/cancel")).andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200)).andExpect(jsonPath("$.msg").value("取消预约成功"));

        mockMvc.perform(put("/api/reservations/" + reservationId + "/cancel")).andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200)).andExpect(jsonPath("$.msg").value("取消预约成功"));

        // 已取消的重复请求按成功返回，别人的预约按不存在返回 404
        mockMvc.perform(put("/api/reservations/" + reservationId + "/cancel")).andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200)).andExpect(jsonPath("$.msg").value("取消预约成功"));

        mockMvc.perform(put("/api/reservations/999999/cancel")).andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(404)).andExpect(jsonPath("$.msg").value("预约不存在或已被删除"));
    }

    private void createReservation() throws Exception {
        mockMvc.perform(post("/api/reservations").contentType(MediaType.APPLICATION_JSON)
                .content(body(REQUEST_ID, SCHEDULE_ID, TIMES_CARD_ID, 1))).andExpect(jsonPath("$.code").value(200));
    }

    private String body(String requestId, Long scheduleId, Long memberCardId, int persons) throws Exception {
        ReservationCreateDTO dto = new ReservationCreateDTO();
        dto.setRequestId(requestId);
        dto.setScheduleId(scheduleId);
        dto.setMemberCardId(memberCardId);
        dto.setPersons(persons);
        return objectMapper.writeValueAsString(dto);
    }

    private LocalDateTime scheduleStartTime() {
        return jdbcTemplate.queryForObject("SELECT start_time FROM t_schedule WHERE id = ?", LocalDateTime.class,
                SCHEDULE_ID);
    }

    private int countOf(String sql, Object... args) {
        Integer value = jdbcTemplate.queryForObject(sql, Integer.class, args);
        return value == null ? 0 : value;
    }

    private Integer intOf(String sql, Object... args) {
        return jdbcTemplate.queryForObject(sql, Integer.class, args);
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
        jdbcTemplate.update("INSERT INTO t_member (id, phone, password, phone_last4, name, gender, level, create_by,"
                + " create_time, update_by, update_time) VALUES (?, '13800138000', NULL, '8000', '张小明', 0, 0, 1,"
                + " NOW(), 1, NOW())", MEMBER_ID);

        LocalDateTime now = LocalDateTime.now(BUSINESS_ZONE);
        jdbcTemplate.update("INSERT INTO t_member_card (id, member_id, open_store_id, card_no, card_name, card_type,"
                + " course_scope, store_id, purchase_times, gift_times, remaining_times, valid_period_unit,"
                + " valid_period_count, valid_start_time, valid_end_time, activate_time, stop_time, stop_reason,"
                + " create_by, create_time, update_by, update_time) VALUES (?, ?, ?, '202610150007000', '瑜伽团课次卡',"
                + " 1, 1, ?, 12, 0, 12, 1, 12, ?, ?, ?, NULL, NULL, 1, NOW(), 1, NOW())", TIMES_CARD_ID, MEMBER_ID,
                STORE_ID, STORE_ID, now.minusDays(1), now.plusDays(300), now.minusDays(1));

        LocalDate tomorrow = LocalDate.now(BUSINESS_ZONE).plusDays(1);
        insertSchedule(SCHEDULE_ID, tomorrow.atTime(10, 0), tomorrow.atTime(11, 0));
        insertSchedule(SCHEDULE_TOO_EARLY_ID, now.plusMinutes(40), now.plusMinutes(100));
    }

    private void insertSchedule(Long id, LocalDateTime startTime, LocalDateTime endTime) {
        jdbcTemplate.update(
                "INSERT INTO t_schedule (id, store_id, course_id, course_type, coach_id, classroom_id,"
                        + " schedule_date, start_time, end_time, max_persons, min_persons, booked_persons, status,"
                        + " create_by, create_time, update_by, update_time)"
                        + " VALUES (?, ?, ?, 1, ?, ?, ?, ?, ?, 5, 1, 0, 2, 1, NOW(), 1, NOW())",
                id, STORE_ID, COURSE_ID, COACH_ID, CLASSROOM_ID, startTime.toLocalDate(), startTime, endTime);
    }
}
