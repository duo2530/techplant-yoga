package com.techplant.yoga.reservation.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
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
import com.techplant.yoga.common.config.BusinessTimeConfig;
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
import com.techplant.yoga.reservation.service.ReservationService;
import com.techplant.yoga.reservation.service.impl.ReservationServiceImpl;
import com.techplant.yoga.schedule.dao.ScheduleDaoImpl;
import com.techplant.yoga.schedule.service.impl.ScheduleBookingServiceImpl;
import com.techplant.yoga.schedule.service.impl.ScheduleServiceImpl;
import com.techplant.yoga.store.dao.StoreDaoImpl;
import com.techplant.yoga.store.dao.StoreRegionDictDaoImpl;
import com.techplant.yoga.store.service.impl.StoreServiceImpl;

/**
 * 管理端预约接口的 HTTP 层集成测试（MockMvc ＋ Testcontainers MySQL，service／DAO 都是真实实现）。
 *
 * <p>覆盖统一响应、参数校验、ID 字符串化与时间格式；管理端鉴权链不在本测试范围内。</p>
 */
@ContextConfiguration(classes = {YogaMyBatisTestConfig.class, BusinessTimeConfig.class, StoreDaoImpl.class,
    StoreRegionDictDaoImpl.class, StoreServiceImpl.class, ClassroomDaoImpl.class, ClassroomServiceImpl.class,
    CoachDaoImpl.class, CoachServiceImpl.class, CourseDaoImpl.class, CourseServiceImpl.class, MemberDaoImpl.class,
    MemberLevelDictDaoImpl.class, MemberServiceImpl.class, MemberCardDaoImpl.class, MemberCardTransactionDaoImpl.class,
    MemberCardTransactionServiceImpl.class, CardNoGenerator.class, MemberCardServiceImpl.class, ScheduleDaoImpl.class,
    ScheduleBookingServiceImpl.class, ScheduleServiceImpl.class, ReservationDaoImpl.class,
    ReservationServiceImpl.class})
@DisplayName("ReservationController 接口测试（MockMvc：管理端列表字段 / 参数校验 / ID 字符串化 / 时间格式）")
class ReservationControllerTest extends AbstractYogaIntegrationTest {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");

    private static final Long STORE_ID = 1000L;

    private static final Long COURSE_ID = 3000L;

    private static final Long COACH_ID = 4000L;

    private static final Long CLASSROOM_ID = 5000L;

    private static final Long MEMBER_ID = 6000L;

    private static final Long TIMES_CARD_ID = 7000L;

    private static final Long SCHEDULE_ID = 8000L;

    private static final Long OTHER_SCHEDULE_ID = 8001L;

    @Autowired
    private ReservationService reservationService;

    private MockMvc mockMvc;

    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        YogaSchema.reset(dataSource, "t_reservation", "t_member_card_transaction", "t_member_card", "t_schedule",
                "t_course", "t_coach", "t_classroom", "t_store", "t_member");
        jdbcTemplate = new JdbcTemplate(dataSource);
        insertFixtures();

        ObjectMapper objectMapper = Jackson2ObjectMapperBuilder.json()
                .modulesToInstall(new JacksonConfig.BusinessIdToStringModule()).build();
        mockMvc = MockMvcBuilders.standaloneSetup(new ReservationController(reservationService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper)).build();
    }

    @Test
    @DisplayName("查询预约列表：返回 TableDataInfo，ID 字符串化、时间按 yyyy-MM-dd HH:mm:ss，关联资料逐项装配")
    void list_shouldReturnAdminFields() throws Exception {
        mockMvc.perform(get("/admin/reservations").param("pageNum", "1").param("pageSize", "10"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.msg").value("查询成功")).andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.rows[0].id").isString())
                .andExpect(jsonPath("$.rows[0].scheduleId").value(String.valueOf(SCHEDULE_ID)))
                .andExpect(jsonPath("$.rows[0].courseId").value(String.valueOf(COURSE_ID)))
                .andExpect(jsonPath("$.rows[0].courseName").value("哈他瑜伽"))
                .andExpect(jsonPath("$.rows[0].courseType").value(1))
                .andExpect(jsonPath("$.rows[0].courseTypeName").value("团课"))
                .andExpect(jsonPath("$.rows[0].difficulty").value(2))
                .andExpect(jsonPath("$.rows[0].coachName").value("林教练"))
                .andExpect(jsonPath("$.rows[0].coachId").value(String.valueOf(COACH_ID)))
                .andExpect(jsonPath("$.rows[0].classroomId").value(String.valueOf(CLASSROOM_ID)))
                .andExpect(jsonPath("$.rows[0].classroomName").value("瑜伽团课大教室"))
                .andExpect(jsonPath("$.rows[0].storeId").value(String.valueOf(STORE_ID)))
                .andExpect(jsonPath("$.rows[0].storeName").value("徐汇店"))
                .andExpect(jsonPath("$.rows[0].storeAddress").value("漕溪北路 88 号"))
                .andExpect(jsonPath("$.rows[0].scheduleDate").value(tomorrow().toString()))
                .andExpect(jsonPath("$.rows[0].startTime")
                        .value(tomorrow().atTime(10, 0).toString().replace('T', ' ') + ":00"))
                .andExpect(jsonPath("$.rows[0].endTime")
                        .value(tomorrow().atTime(11, 0).toString().replace('T', ' ') + ":00"))
                .andExpect(jsonPath("$.rows[0].durationMinutes").value(60))
                .andExpect(jsonPath("$.rows[0].persons").value(2))
                .andExpect(jsonPath("$.rows[0].memberCardId").value(String.valueOf(TIMES_CARD_ID)))
                .andExpect(jsonPath("$.rows[0].cardNo").value(cardNo()))
                .andExpect(jsonPath("$.rows[0].cardName").value("瑜伽团课次卡"))
                .andExpect(jsonPath("$.rows[0].cardType").value(1))
                .andExpect(jsonPath("$.rows[0].cardTypeText").value("次数卡"))
                .andExpect(jsonPath("$.rows[0].deductTimes").value(2)).andExpect(jsonPath("$.rows[0].status").value(1))
                .andExpect(jsonPath("$.rows[0].statusText").value("已预约"))
                .andExpect(jsonPath("$.rows[0].scheduleStatus").value(2))
                .andExpect(jsonPath("$.rows[0].scheduleStatusText").value("已上架"))
                .andExpect(jsonPath("$.rows[0].memberId").value(String.valueOf(MEMBER_ID)))
                .andExpect(jsonPath("$.rows[0].memberName").value("张小明"))
                .andExpect(jsonPath("$.rows[0].memberPhone").value("13800138000"))
                // 已取消记录默认包含，取消原因按码值返回
                .andExpect(jsonPath("$.rows[1].statusText").value("已取消"))
                .andExpect(jsonPath("$.rows[1].cancelReason").value(1))
                .andExpect(jsonPath("$.rows[1].deductTimes").value(0));
    }

    @Test
    @DisplayName("查询预约列表：按排课查询名单（含取消记录），按状态与日期筛选")
    void list_shouldFilterByScheduleStatusAndDate() throws Exception {
        mockMvc.perform(get("/admin/reservations").param("pageNum", "1").param("pageSize", "10").param("scheduleId",
                String.valueOf(SCHEDULE_ID))).andExpect(jsonPath("$.total").value(2));

        mockMvc.perform(get("/admin/reservations").param("pageNum", "1").param("pageSize", "10")
                .param("scheduleId", String.valueOf(SCHEDULE_ID)).param("status", "1"))
                .andExpect(jsonPath("$.total").value(1)).andExpect(jsonPath("$.rows[0].statusText").value("已预约"));

        mockMvc.perform(get("/admin/reservations").param("pageNum", "1").param("pageSize", "10")
                .param("scheduleId", String.valueOf(SCHEDULE_ID)).param("status", "2"))
                .andExpect(jsonPath("$.total").value(1)).andExpect(jsonPath("$.rows[0].statusText").value("已取消"));

        mockMvc.perform(get("/admin/reservations").param("pageNum", "1").param("pageSize", "10").param("scheduleId",
                String.valueOf(OTHER_SCHEDULE_ID))).andExpect(jsonPath("$.total").value(0));

        // 上课日期含当天：只查明天的场次
        mockMvc.perform(get("/admin/reservations").param("pageNum", "1").param("pageSize", "10")
                .param("startDate", tomorrow().toString()).param("endDate", tomorrow().toString()))
                .andExpect(jsonPath("$.total").value(2));

        // 超出日期范围返回空 rows，total 仍为筛选结果总数
        mockMvc.perform(get("/admin/reservations").param("pageNum", "9").param("pageSize", "10"))
                .andExpect(jsonPath("$.total").value(2)).andExpect(jsonPath("$.rows").isEmpty());
    }

    @Test
    @DisplayName("查询预约列表：分页缺失、状态非法与日期范围倒置都返回业务码 500")
    void list_withIllegalQuery_shouldReject() throws Exception {
        mockMvc.perform(get("/admin/reservations")).andExpect(status().isOk()).andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.msg", Matchers.anyOf(Matchers.is("页码不能为空"), Matchers.is("每页条数不能为空"))));

        mockMvc.perform(get("/admin/reservations").param("pageNum", "1").param("pageSize", "10").param("status", "9"))
                .andExpect(jsonPath("$.code").value(500));

        mockMvc.perform(get("/admin/reservations").param("pageNum", "1").param("pageSize", "10")
                .param("startDate", tomorrow().plusDays(1).toString()).param("endDate", tomorrow().toString()))
                .andExpect(jsonPath("$.code").value(500)).andExpect(jsonPath("$.msg").value("结束日期不能早于起始日期"));

        mockMvc.perform(get("/admin/reservations").param("pageNum", "1").param("pageSize", "101"))
                .andExpect(jsonPath("$.code").value(500)).andExpect(jsonPath("$.msg").value("每页条数不能超过 100"));
    }

    private LocalDate tomorrow() {
        return LocalDate.now(BUSINESS_ZONE).plusDays(1);
    }

    private String cardNo() {
        return jdbcTemplate.queryForObject("SELECT card_no FROM t_member_card WHERE id = ?", String.class,
                TIMES_CARD_ID);
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

        insertSchedule(SCHEDULE_ID, tomorrow().atTime(10, 0), tomorrow().atTime(11, 0));
        insertSchedule(OTHER_SCHEDULE_ID, tomorrow().plusDays(1).atTime(10, 0), tomorrow().plusDays(1).atTime(11, 0));

        LocalDateTime base = now.withNano(0);
        insertReservation(9501L, "2f1a4c1e-6a2b-4c1d-9f0e-1a2b3c4d5e6f", SCHEDULE_ID, 2, 1, base, null, null);
        insertReservation(9502L, "3f1a4c1e-6a2b-4c1d-9f0e-1a2b3c4d5e70", SCHEDULE_ID, 2, 2, base.minusMinutes(5),
                base.minusMinutes(4), 1);
        jdbcTemplate.update("INSERT INTO t_member_card_transaction (id, member_card_id, member_id, reservation_id,"
                + " change_type, persons, change_times, before_times, after_times, create_time)"
                + " VALUES (?, ?, ?, ?, 1, 2, -2, 12, 10, NOW())", 1950100L, TIMES_CARD_ID, MEMBER_ID, 9501L);
    }

    private void insertSchedule(Long id, LocalDateTime startTime, LocalDateTime endTime) {
        jdbcTemplate.update(
                "INSERT INTO t_schedule (id, store_id, course_id, course_type, coach_id, classroom_id,"
                        + " schedule_date, start_time, end_time, max_persons, min_persons, booked_persons, status,"
                        + " create_by, create_time, update_by, update_time)"
                        + " VALUES (?, ?, ?, 1, ?, ?, ?, ?, ?, 5, 1, 0, 2, 1, NOW(), 1, NOW())",
                id, STORE_ID, COURSE_ID, COACH_ID, CLASSROOM_ID, startTime.toLocalDate(), startTime, endTime);
    }

    private void insertReservation(Long id, String requestId, Long scheduleId, int persons, int status,
            LocalDateTime reservationTime, LocalDateTime cancelTime, Integer cancelReason) {
        jdbcTemplate.update("INSERT INTO t_reservation (id, request_id, member_id, schedule_id, start_time, end_time,"
                + " persons, member_card_id, status, reservation_time, cancel_time, cancel_reason, check_in_time,"
                + " create_by, create_time, update_by, update_time)"
                + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NULL, 1, NOW(), 1, NOW())", id, requestId, MEMBER_ID,
                scheduleId, scheduleStartTime(scheduleId), scheduleEndTime(scheduleId), persons, TIMES_CARD_ID, status,
                reservationTime, cancelTime, cancelReason);
    }

    private LocalDateTime scheduleStartTime(Long scheduleId) {
        return jdbcTemplate.queryForObject("SELECT start_time FROM t_schedule WHERE id = ?", LocalDateTime.class,
                scheduleId);
    }

    private LocalDateTime scheduleEndTime(Long scheduleId) {
        return jdbcTemplate.queryForObject("SELECT end_time FROM t_schedule WHERE id = ?", LocalDateTime.class,
                scheduleId);
    }

    @Test
    @DisplayName("查询预约列表：筛选参数为门店／课程／卡类型时同样只返回命中记录")
    void list_shouldFilterByStoreCourseAndCardType() throws Exception {
        mockMvc.perform(get("/admin/reservations").param("pageNum", "1").param("pageSize", "10").param("storeId",
                String.valueOf(STORE_ID))).andExpect(jsonPath("$.total").value(2));
        mockMvc.perform(get("/admin/reservations").param("pageNum", "1").param("pageSize", "10").param("courseId",
                String.valueOf(COURSE_ID))).andExpect(jsonPath("$.total").value(2));
        mockMvc.perform(get("/admin/reservations").param("pageNum", "1").param("pageSize", "10").param("cardType", "1"))
                .andExpect(jsonPath("$.total").value(2));
        mockMvc.perform(get("/admin/reservations").param("pageNum", "1").param("pageSize", "10").param("cardType", "2"))
                .andExpect(jsonPath("$.total").value(0));
        mockMvc.perform(get("/admin/reservations").param("pageNum", "1").param("pageSize", "10").param("memberId",
                String.valueOf(MEMBER_ID))).andExpect(jsonPath("$.total").value(2));
        mockMvc.perform(
                get("/admin/reservations").param("pageNum", "1").param("pageSize", "10").param("memberId", "999999"))
                .andExpect(jsonPath("$.total").value(0));
    }
}
