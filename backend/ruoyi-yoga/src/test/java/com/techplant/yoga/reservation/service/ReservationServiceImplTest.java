package com.techplant.yoga.reservation.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
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
import org.springframework.dao.DataAccessException;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import com.ruoyi.common.exception.ServiceException;
import com.techplant.yoga.classroom.dao.ClassroomDaoImpl;
import com.techplant.yoga.classroom.service.impl.ClassroomServiceImpl;
import com.techplant.yoga.coach.dao.CoachDaoImpl;
import com.techplant.yoga.coach.service.impl.CoachServiceImpl;
import com.techplant.yoga.common.config.BusinessTimeConfig;
import com.techplant.yoga.common.response.PageResult;
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
import com.techplant.yoga.membercard.service.MemberCardService;
import com.techplant.yoga.membercard.dto.MemberCardStopDTO;
import com.techplant.yoga.membercard.service.impl.MemberCardServiceImpl;
import com.techplant.yoga.membercard.service.impl.MemberCardTransactionServiceImpl;
import com.techplant.yoga.reservation.dao.ReservationDao;
import com.techplant.yoga.reservation.dao.ReservationDaoImpl;
import com.techplant.yoga.reservation.dto.ReservationCreateDTO;
import com.techplant.yoga.reservation.query.ReservationAdminQuery;
import com.techplant.yoga.reservation.query.ReservationPublicQuery;
import com.techplant.yoga.reservation.service.impl.ReservationServiceImpl;
import com.techplant.yoga.reservation.vo.ReservationAdminItemVO;
import com.techplant.yoga.reservation.vo.ReservationPublicItemVO;
import com.techplant.yoga.schedule.dao.ScheduleDaoImpl;
import com.techplant.yoga.schedule.service.ScheduleBookingService;
import com.techplant.yoga.schedule.service.ScheduleService;
import com.techplant.yoga.schedule.service.impl.ScheduleBookingServiceImpl;
import com.techplant.yoga.schedule.service.impl.ScheduleServiceImpl;
import com.techplant.yoga.schedule.vo.PublicScheduleDetailVO;
import com.techplant.yoga.store.dao.StoreDaoImpl;
import com.techplant.yoga.store.dao.StoreRegionDictDaoImpl;
import com.techplant.yoga.store.service.impl.StoreServiceImpl;

/**
 * 预约业务的集成测试（Testcontainers MySQL，表结构取自 backend/sql/yoga.sql，不连开发库）。
 *
 * <p>会员、会员卡、排课、课程、教练与门店都走真实 Service 与真实 SQL，不模拟内部调用；
 * 预约、扣返次、占位与流水的联动在同一事务里验证。</p>
 */
@ContextConfiguration(classes = {YogaMyBatisTestConfig.class, BusinessTimeConfig.class, StoreDaoImpl.class,
    StoreRegionDictDaoImpl.class, StoreServiceImpl.class, ClassroomDaoImpl.class, ClassroomServiceImpl.class,
    CoachDaoImpl.class, CoachServiceImpl.class, CourseDaoImpl.class, CourseServiceImpl.class, MemberDaoImpl.class,
    MemberLevelDictDaoImpl.class, MemberServiceImpl.class, MemberCardDaoImpl.class, MemberCardTransactionDaoImpl.class,
    MemberCardTransactionServiceImpl.class, CardNoGenerator.class, MemberCardServiceImpl.class, ScheduleDaoImpl.class,
    ScheduleBookingServiceImpl.class, ScheduleServiceImpl.class, ReservationDaoImpl.class,
    ReservationServiceImpl.class})
@DisplayName("ReservationService 集成测试（Testcontainers MySQL：预约 / 取消 / 签到 / 扣返次 / 占位 / 我的预约）")
class ReservationServiceImplTest extends AbstractYogaIntegrationTest {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");

    private static final Long STORE_ID = 1000L;

    private static final Long OTHER_STORE_ID = 2000L;

    private static final Long COURSE_ID = 3000L;

    private static final Long PERSONAL_COURSE_ID = 3001L;

    private static final Long COACH_ID = 4000L;

    private static final Long CLASSROOM_ID = 5000L;

    private static final Long OTHER_CLASSROOM_ID = 5001L;

    private static final Long MEMBER_ID = 6000L;

    private static final Long OTHER_MEMBER_ID = 6001L;

    private static final Long THIRD_MEMBER_ID = 6002L;

    private static final Long FOURTH_MEMBER_ID = 6003L;

    private static final Long TIMES_CARD_ID = 7000L;

    private static final Long YEAR_CARD_ID = 7001L;

    private static final Long PENDING_CARD_ID = 7002L;

    private static final Long STOPPED_CARD_ID = 7003L;

    private static final Long EXPIRED_CARD_ID = 7004L;

    private static final Long OTHER_STORE_CARD_ID = 7005L;

    private static final Long PERSONAL_CARD_ID = 7006L;

    private static final Long OTHER_MEMBER_CARD_ID = 7007L;

    private static final Long SCHEDULE_ID = 8000L;

    private static final Long SCHEDULE_SECOND_ID = 8001L;

    private static final Long SCHEDULE_OVERLAP_ID = 8002L;

    private static final Long SCHEDULE_PERSONAL_ID = 8003L;

    private static final Long SCHEDULE_PENDING_ID = 8004L;

    private static final Long SCHEDULE_CANCELLED_ID = 8005L;

    private static final Long SCHEDULE_STARTED_ID = 8006L;

    private static final Long SCHEDULE_SOON_ID = 8007L;

    private static final Long SCHEDULE_TOO_EARLY_ID = 8008L;

    private static final Long SCHEDULE_ENDED_ID = 8009L;

    private static final Long SCHEDULE_FULL_ID = 8010L;

    private static final Long SCHEDULE_CANCEL_WINDOW_ID = 8011L;

    private static final Long SCHEDULE_OTHER_STORE_ID = 8012L;

    @Autowired
    private ReservationService reservationService;

    @Autowired
    private ScheduleService scheduleService;

    @Autowired
    private ScheduleBookingService scheduleBookingService;

    @Autowired
    private ReservationDao reservationDao;

    @Autowired
    private MemberCardService memberCardService;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void resetTables() {
        YogaSchema.reset(dataSource, "t_reservation", "t_member_card_transaction", "t_member_card", "t_schedule",
                "t_course", "t_coach", "t_classroom", "t_store", "t_member");
        jdbcTemplate = new JdbcTemplate(dataSource);
        insertStores();
        insertClassroom();
        insertCoach();
        insertCourses();
        insertMembers();
        insertCards();
        insertSchedules();
    }

    @Test
    @DisplayName("预约课程：次数卡按人数扣次、排课占位、预约与扣次流水同事务落库")
    void create_withTimesCard_shouldOccupyAndDeduct() {
        create(REQUEST_ID, SCHEDULE_ID, TIMES_CARD_ID, 2);

        assertEquals(1, countReservations(), "预约只写入一条");
        assertEquals(Integer.valueOf(2), intOf("SELECT booked_persons FROM t_schedule WHERE id = ?", SCHEDULE_ID),
                "排课占位按人数累加");
        assertEquals(Integer.valueOf(10),
                intOf("SELECT remaining_times FROM t_member_card WHERE id = ?", TIMES_CARD_ID), "次数卡扣 2 次");
        assertEquals(Integer.valueOf(10), intOf(
                "SELECT after_times FROM t_member_card_transaction" + " WHERE reservation_id = ? AND change_type = 1",
                reservationId()), "扣次流水记录扣后余额");
        assertEquals(Integer.valueOf(-2), intOf(
                "SELECT change_times FROM t_member_card_transaction" + " WHERE reservation_id = ? AND change_type = 1",
                reservationId()));
        assertEquals(Integer.valueOf(1), intOf("SELECT status FROM t_reservation WHERE id = ?", reservationId()),
                "初始状态为已预约");
    }

    @Test
    @DisplayName("预约课程：同一个 requestId 且参数一致视为成功，不重复创建、占位或扣次")
    void create_withSameRequestId_shouldBeIdempotent() {
        create(REQUEST_ID, SCHEDULE_ID, TIMES_CARD_ID, 1);
        Long firstId = reservationId();
        create(REQUEST_ID, SCHEDULE_ID, TIMES_CARD_ID, 1);

        assertEquals(1, countReservations());
        assertEquals(firstId, reservationId(), "幂等重试返回原预约");
        assertEquals(Integer.valueOf(1), intOf("SELECT booked_persons FROM t_schedule WHERE id = ?", SCHEDULE_ID));
        assertEquals(Integer.valueOf(11),
                intOf("SELECT remaining_times FROM t_member_card WHERE id = ?", TIMES_CARD_ID));
        assertEquals(1, countOf("SELECT COUNT(*) FROM t_member_card_transaction"));
    }

    @Test
    @DisplayName("预约课程：同一个 requestId 换排课或换人数按冲突返回")
    void create_withSameRequestIdButDifferentParams_shouldConflict() {
        create(REQUEST_ID, SCHEDULE_ID, TIMES_CARD_ID, 1);

        ServiceException changedPersons = assertThrows(ServiceException.class,
                () -> create(REQUEST_ID, SCHEDULE_ID, TIMES_CARD_ID, 2));
        assertEquals(Integer.valueOf(409), changedPersons.getCode());
        assertEquals("请求幂等标识已被其他预约使用", changedPersons.getMessage());

        ServiceException changedSchedule = assertThrows(ServiceException.class,
                () -> create(REQUEST_ID, SCHEDULE_SECOND_ID, TIMES_CARD_ID, 1));
        assertEquals(Integer.valueOf(409), changedSchedule.getCode());
        assertEquals("请求幂等标识已被其他预约使用", changedSchedule.getMessage());
        assertEquals(1, countReservations());
    }

    @Test
    @DisplayName("预约课程：年卡不扣次，人数只能为 1")
    void create_withYearCard_shouldSkipDeductAndRejectMultiplePersons() {
        create(REQUEST_ID, SCHEDULE_ID, YEAR_CARD_ID, 1);

        assertEquals(0, countOf("SELECT COUNT(*) FROM t_member_card_transaction"), "年卡不产生次数流水");
        assertEquals(Integer.valueOf(1), intOf("SELECT booked_persons FROM t_schedule WHERE id = ?", SCHEDULE_ID));

        assertMessage(409, "年卡预约人数只能为 1", () -> create(OTHER_REQUEST_ID, SCHEDULE_SECOND_ID, YEAR_CARD_ID, 2));
    }

    @Test
    @DisplayName("预约课程：待上架排课按不存在处理，已取消与已开始返回冲突")
    void create_withUnusableSchedule_shouldReject() {
        assertMessage(404, "排课不存在或已被删除", () -> create(REQUEST_ID, SCHEDULE_PENDING_ID, TIMES_CARD_ID, 1));
        assertMessage(409, "排课已取消，不能预约", () -> create(REQUEST_ID, SCHEDULE_CANCELLED_ID, TIMES_CARD_ID, 1));
        assertMessage(409, "排课已开始或已结束，不能预约", () -> create(REQUEST_ID, SCHEDULE_STARTED_ID, TIMES_CARD_ID, 1));
        assertMessage(404, "排课不存在或已被删除", () -> create(REQUEST_ID, 999999L, TIMES_CARD_ID, 1));
        assertEquals(0, countReservations());
    }

    @Test
    @DisplayName("预约课程：未开卡、已停卡、已过期、门店或课种不适用与不属于本人的卡都拒绝")
    void create_withUnusableCard_shouldReject() {
        assertMessage(409, "会员卡未开卡，不能预约", () -> create(REQUEST_ID, SCHEDULE_ID, PENDING_CARD_ID, 1));
        assertMessage(409, "会员卡已停卡，不能预约", () -> create(REQUEST_ID, SCHEDULE_ID, STOPPED_CARD_ID, 1));
        assertMessage(409, "会员卡已过期，不能预约", () -> create(REQUEST_ID, SCHEDULE_ID, EXPIRED_CARD_ID, 1));
        assertMessage(409, "会员卡不适用于该门店", () -> create(REQUEST_ID, SCHEDULE_ID, OTHER_STORE_CARD_ID, 1));
        assertMessage(409, "会员卡不适用于该课程", () -> create(REQUEST_ID, SCHEDULE_PERSONAL_ID, TIMES_CARD_ID, 1));
        assertMessage(409, "会员卡不适用于该课程", () -> create(REQUEST_ID, SCHEDULE_ID, PERSONAL_CARD_ID, 1));
        assertMessage(404, "会员卡不存在或不属于本人", () -> create(REQUEST_ID, SCHEDULE_ID, OTHER_MEMBER_CARD_ID, 1));
        assertMessage(404, "会员卡不存在或不属于本人", () -> create(REQUEST_ID, SCHEDULE_ID, 999999L, 1));
        assertEquals(0, countReservations());
        assertEquals(Integer.valueOf(0), intOf("SELECT booked_persons FROM t_schedule WHERE id = ?", SCHEDULE_ID));
    }

    @Test
    @DisplayName("预约课程：同排课重复预约拒绝，取消后允许重新预约")
    void create_withDuplicateSchedule_shouldConflict() {
        create(REQUEST_ID, SCHEDULE_ID, TIMES_CARD_ID, 1);

        assertMessage(409, "您已预约该课程", () -> create(OTHER_REQUEST_ID, SCHEDULE_ID, TIMES_CARD_ID, 1));
        assertEquals(1, countReservations());

        // 已取消后可以重新预约同一排课
        reservationService.cancel(MEMBER_ID, reservationId());
        create(OTHER_REQUEST_ID, SCHEDULE_ID, TIMES_CARD_ID, 1);
        assertEquals(2, countReservations());
    }

    @Test
    @DisplayName("预约课程：人数超过剩余名额返回冲突，且不写名额与余额")
    void create_withInsufficientPlaces_shouldConflict() {
        assertMessage(409, "剩余名额不足，不能预约", () -> create(REQUEST_ID, SCHEDULE_FULL_ID, TIMES_CARD_ID, 2));

        assertEquals(0, countReservations());
        assertEquals(Integer.valueOf(0), intOf("SELECT booked_persons FROM t_schedule WHERE id = ?", SCHEDULE_FULL_ID));
        assertEquals(Integer.valueOf(12),
                intOf("SELECT remaining_times FROM t_member_card WHERE id = ?", TIMES_CARD_ID));
    }

    @Test
    @DisplayName("预约课程：参数格式非法返回参数错误")
    void create_withIllegalParams_shouldFail() {
        assertMessage(400, "请求幂等标识格式非法，应为 UUID", () -> create("not-a-uuid", SCHEDULE_ID, TIMES_CARD_ID, 1));
        assertMessage(400, "预约人数必须大于 0", () -> create(REQUEST_ID, SCHEDULE_ID, TIMES_CARD_ID, 0));
        assertMessage(400, "排课ID格式非法", () -> create(REQUEST_ID, 0L, TIMES_CARD_ID, 1));
        assertEquals(0, countReservations());
    }

    @Test
    @DisplayName("取消预约：释放名额、按原扣次返次并写返次流水；重复取消幂等")
    void cancel_shouldReleaseAndRefund() {
        create(REQUEST_ID, SCHEDULE_ID, TIMES_CARD_ID, 2);
        Long id = reservationId();

        reservationService.cancel(MEMBER_ID, id);

        assertEquals(Integer.valueOf(2), intOf("SELECT status FROM t_reservation WHERE id = ?", id));
        assertEquals(Integer.valueOf(1), intOf("SELECT cancel_reason FROM t_reservation WHERE id = ?", id));
        assertNotNull(jdbcTemplate.queryForObject("SELECT cancel_time FROM t_reservation WHERE id = ?",
                LocalDateTime.class, id));
        assertEquals(Integer.valueOf(0), intOf("SELECT booked_persons FROM t_schedule WHERE id = ?", SCHEDULE_ID));
        assertEquals(Integer.valueOf(12),
                intOf("SELECT remaining_times FROM t_member_card WHERE id = ?", TIMES_CARD_ID), "返次按原扣次流水次数返还");
        assertEquals(Integer.valueOf(2), intOf(
                "SELECT change_times FROM t_member_card_transaction" + " WHERE reservation_id = ? AND change_type = 2",
                id));
        assertEquals(Integer.valueOf(12), intOf(
                "SELECT after_times FROM t_member_card_transaction" + " WHERE reservation_id = ? AND change_type = 2",
                id));

        reservationService.cancel(MEMBER_ID, id);
        assertEquals(Integer.valueOf(12),
                intOf("SELECT remaining_times FROM t_member_card WHERE id = ?", TIMES_CARD_ID), "重复取消不重复返次");
        assertEquals(1, countOf("SELECT COUNT(*) FROM t_member_card_transaction WHERE change_type = 2"));
    }

    @Test
    @DisplayName("取消预约：已签到、超过截止时间与不属于本人都拒绝")
    void cancel_shouldRejectIllegalState() {
        create(REQUEST_ID, SCHEDULE_SOON_ID, TIMES_CARD_ID, 1);
        Long id = reservationId();
        reservationService.checkIn(MEMBER_ID, id);
        assertMessage(409, "已签到的预约不能取消", () -> reservationService.cancel(MEMBER_ID, id));

        create(SECOND_REQUEST_ID, SCHEDULE_CANCEL_WINDOW_ID, TIMES_CARD_ID, 1);
        assertMessage(409, "已超过取消截止时间，不能取消预约", () -> reservationService.cancel(MEMBER_ID, reservationId()));

        assertMessage(404, "预约不存在或已被删除", () -> reservationService.cancel(OTHER_MEMBER_ID, id));
        assertMessage(404, "预约不存在或已被删除", () -> reservationService.cancel(MEMBER_ID, 999999L));
    }

    @Test
    @DisplayName("预约签到：窗口内写状态与签到时间，重复签到幂等；未到窗口与课程已结束都拒绝")
    void checkIn_shouldWriteStatusAndRejectOutOfWindow() {
        create(REQUEST_ID, SCHEDULE_SOON_ID, TIMES_CARD_ID, 1);
        Long id = reservationId();

        reservationService.checkIn(MEMBER_ID, id);
        assertEquals(Integer.valueOf(3), intOf("SELECT status FROM t_reservation WHERE id = ?", id));
        assertNotNull(jdbcTemplate.queryForObject("SELECT check_in_time FROM t_reservation WHERE id = ?",
                LocalDateTime.class, id));
        assertEquals(1, countOf(
                "SELECT COUNT(*) FROM t_member_card_transaction WHERE change_type = 1" + " AND reservation_id = " + id),
                "签到不再产生扣次流水");
        assertEquals(Integer.valueOf(11),
                intOf("SELECT remaining_times FROM t_member_card WHERE id = ?", TIMES_CARD_ID), "签到不重复扣次");

        reservationService.checkIn(MEMBER_ID, id);
        assertEquals(Integer.valueOf(3), intOf("SELECT status FROM t_reservation WHERE id = ?", id), "重复签到幂等");

        create(SECOND_REQUEST_ID, SCHEDULE_TOO_EARLY_ID, TIMES_CARD_ID, 1);
        assertMessage(409, "未到签到时间", () -> reservationService.checkIn(MEMBER_ID, reservationId()));

        insertReservation(9001L, OTHER_REQUEST_ID, MEMBER_ID, SCHEDULE_ENDED_ID, TIMES_CARD_ID, 1, 1);
        assertMessage(409, "课程已结束，不能签到", () -> reservationService.checkIn(MEMBER_ID, 9001L));

        assertMessage(404, "预约不存在或已被删除", () -> reservationService.checkIn(OTHER_MEMBER_ID, id));
    }

    @Test
    @DisplayName("我的预约：按状态与卡类型筛选，倒序稳定排序，关联资料缺失时字段为空")
    void pageForMember_shouldFilterAndAssemble() {
        LocalDateTime base = LocalDateTime.now(BUSINESS_ZONE).withNano(0);
        insertReservation(9101L, REQUEST_ID, MEMBER_ID, SCHEDULE_ID, TIMES_CARD_ID, 1, 1, base.minusDays(2));
        insertReservation(9102L, OTHER_REQUEST_ID, MEMBER_ID, SCHEDULE_SECOND_ID, YEAR_CARD_ID, 1, 3,
                base.minusDays(1));
        insertReservation(9103L, SECOND_REQUEST_ID, MEMBER_ID, SCHEDULE_OVERLAP_ID, TIMES_CARD_ID, 1, 2, base);
        insertReservation(9104L, THIRD_REQUEST_ID, OTHER_MEMBER_ID, SCHEDULE_ID, OTHER_MEMBER_CARD_ID, 1, 1, base);

        PageResult<ReservationPublicItemVO> all = reservationService.pageForMember(MEMBER_ID, query(null, null));
        assertEquals(Long.valueOf(3), all.getTotal(), "只返回本人预约");
        assertEquals(9103L, all.getList().get(0).getId(), "按预约时间倒序，最新的在前");
        assertEquals(9101L, all.getList().get(2).getId());

        ReservationPublicItemVO first = all.getList().get(0);
        assertEquals("哈他瑜伽", first.getCourseName());
        assertEquals("团课", first.getCourseTypeName());
        assertEquals("林教练", first.getCoachName());
        assertEquals("漕溪北路 88 号", first.getStoreAddress());
        assertEquals("已取消", first.getStatusText());
        assertEquals(1, first.getCourseType().intValue(), "课种取排课快照");
        assertEquals(expectedScheduleDate(SCHEDULE_OVERLAP_ID), first.getScheduleDate());
        assertEquals(SCHEDULE_OVERLAP_ID, first.getScheduleId());

        assertEquals(Long.valueOf(1), reservationService.pageForMember(MEMBER_ID, query(1, null)).getTotal(),
                "状态筛选：已预约 1 条");
        assertEquals(9101L, reservationService.pageForMember(MEMBER_ID, query(1, null)).getList().get(0).getId());
        assertEquals(Long.valueOf(1), reservationService.pageForMember(MEMBER_ID, query(null, 2)).getTotal(),
                "卡类型筛选：年卡 1 条");
        assertEquals(9102L, reservationService.pageForMember(MEMBER_ID, query(null, 2)).getList().get(0).getId());
        assertEquals(Long.valueOf(0), reservationService.pageForMember(OTHER_MEMBER_ID, query(null, 2)).getTotal(),
                "本人没有该类型会员卡时直接返回空页");

        // 页码超出范围返回空 rows，total 仍是筛选结果总数
        ReservationPublicQuery outOfRange = query(null, null);
        outOfRange.setPageNum(9);
        PageResult<ReservationPublicItemVO> empty = reservationService.pageForMember(MEMBER_ID, outOfRange);
        assertTrue(empty.getList().isEmpty());
        assertEquals(Long.valueOf(3), empty.getTotal());

        assertMessage(400, "预约状态取值为 1、2 或 3", () -> reservationService.pageForMember(MEMBER_ID, query(9, null)));
        assertMessage(400, "卡类型取值为 1 或 2", () -> reservationService.pageForMember(MEMBER_ID, query(null, 9)));
    }

    @Test
    @DisplayName("我的预约：课程已删除时名称与封面为空，关联门店已删除时地址为空")
    void pageForMember_withDeletedRelations_shouldReturnNullFields() {
        insertReservation(9201L, REQUEST_ID, MEMBER_ID, SCHEDULE_ID, TIMES_CARD_ID, 1, 1);
        jdbcTemplate.update("DELETE FROM t_course WHERE id = ?", COURSE_ID);
        jdbcTemplate.update("DELETE FROM t_coach WHERE id = ?", COACH_ID);
        jdbcTemplate.update("DELETE FROM t_store WHERE id = ?", STORE_ID);

        ReservationPublicItemVO item = reservationService.pageForMember(MEMBER_ID, query(null, null)).getList().get(0);
        assertNull(item.getCourseName());
        assertNull(item.getCourseCoverUrl());
        assertNull(item.getCoachName());
        assertNull(item.getStoreAddress());
        assertEquals(SCHEDULE_ID, item.getScheduleId(), "排课 ID 照常返回");
    }

    @Test
    @DisplayName("排课详情：预约人员按会员去重、姓名脱敏、排除已取消")
    void bookerDisplayNames_shouldDedupAndMask() {
        LocalDateTime base = LocalDateTime.now(BUSINESS_ZONE).withNano(0);
        insertReservation(9301L, REQUEST_ID, OTHER_MEMBER_ID, SCHEDULE_ID, OTHER_MEMBER_CARD_ID, 1, 1,
                base.minusMinutes(4));
        insertReservation(9302L, OTHER_REQUEST_ID, MEMBER_ID, SCHEDULE_ID, TIMES_CARD_ID, 1, 1, base.minusMinutes(3));
        insertReservation(9303L, SECOND_REQUEST_ID, OTHER_MEMBER_ID, SCHEDULE_ID, OTHER_MEMBER_CARD_ID, 1, 1,
                base.minusMinutes(2));
        insertReservation(9304L, THIRD_REQUEST_ID, THIRD_MEMBER_ID, SCHEDULE_ID, OTHER_MEMBER_CARD_ID, 1, 1,
                base.minusMinutes(1));
        insertReservation(9305L, FOURTH_REQUEST_ID, FOURTH_MEMBER_ID, SCHEDULE_ID, OTHER_MEMBER_CARD_ID, 1, 2, base);

        assertEquals(Arrays.asList("李*", "张*", "*"), reservationService.bookerDisplayNames(SCHEDULE_ID));

        PublicScheduleDetailVO detail = scheduleService.publicDetail(SCHEDULE_ID);
        assertEquals(Arrays.asList("李*", "张*", "*"), detail.getBookers(), "排课详情返回同样的脱敏名单");
        assertEquals("漕溪北路 88 号", detail.getStoreAddress());

        assertEquals(0, reservationService.bookerDisplayNames(SCHEDULE_SECOND_ID).size(), "无预约返回空名单");
    }

    @Test
    @DisplayName("排课可约口径：占用名额后剩余名额与 bookable 同步，占满后不可约")
    void scheduleDetail_shouldReflectBookingCount() {
        PublicScheduleDetailVO before = scheduleService.publicDetail(SCHEDULE_ID);
        assertTrue(before.getBookable(), "已上架、未开始、14 天内且有名额 → 可约");
        assertEquals(Integer.valueOf(2), before.getRemainingPlaces());

        create(REQUEST_ID, SCHEDULE_ID, TIMES_CARD_ID, 2);

        PublicScheduleDetailVO after = scheduleService.publicDetail(SCHEDULE_ID);
        assertEquals(Integer.valueOf(2), after.getBookedPersons());
        assertEquals(Integer.valueOf(0), after.getRemainingPlaces());
        assertTrue(!after.getBookable(), "名额占满后不可约");
    }

    @Test
    @DisplayName("占位条件更新：写入值超过最大人数时不更新")
    void updateBookingCount_shouldRejectOverCapacity() {
        assertEquals(0, scheduleBookingService.updateBookingCount(SCHEDULE_FULL_ID, 2));
        assertEquals(Integer.valueOf(0), intOf("SELECT booked_persons FROM t_schedule WHERE id = ?", SCHEDULE_FULL_ID));
        assertEquals(1, scheduleBookingService.updateBookingCount(SCHEDULE_FULL_ID, 1));
        assertEquals(Integer.valueOf(1), intOf("SELECT booked_persons FROM t_schedule WHERE id = ?", SCHEDULE_FULL_ID));
    }

    // ------------------------------------------------------------------
    // 并发与事务快照
    // ------------------------------------------------------------------

    @Test
    @DisplayName("并发防护：重复预约与幂等复查能看到本事务快照之后提交的预约")
    void conflictChecks_shouldReadLatestCommittedAfterSnapshot() {
        new TransactionTemplate(transactionManager).execute(status -> {
            // 普通读建立事务快照：此后并发提交的预约不会出现在快照里
            assertNull(reservationDao.selectByMemberAndRequestId(MEMBER_ID, REQUEST_ID));

            // 另一个连接提交一条与该场次重叠的预约（模拟并发事务，不受本事务快照影响）
            try {
                insertReservationWithNewConnection(9401L, OTHER_REQUEST_ID, MEMBER_ID, SCHEDULE_OVERLAP_ID,
                        TIMES_CARD_ID, 1, 1, LocalDateTime.now(BUSINESS_ZONE).withNano(0));
            } catch (SQLException e) {
                throw new IllegalStateException("独立连接写入预约失败", e);
            }

            assertTrue(reservationDao.existsActiveByMemberAndSchedule(MEMBER_ID, SCHEDULE_OVERLAP_ID),
                    "重复预约检查必须看到并发提交的预约");
            assertNotNull(reservationDao.selectByMemberAndRequestIdForUpdate(MEMBER_ID, OTHER_REQUEST_ID),
                    "幂等复查必须看到并发提交的同键预约");
            return null;
        });
    }

    @Test
    @DisplayName("并发预约：同会员允许预约两节重叠课程，各自占位扣次")
    void create_concurrentlyOverlapping_shouldAllowBoth() throws Exception {
        List<String> results = runConcurrently(() -> create(REQUEST_ID, SCHEDULE_ID, TIMES_CARD_ID, 1),
                () -> create(OTHER_REQUEST_ID, SCHEDULE_OVERLAP_ID, TIMES_CARD_ID, 1));

        assertEquals(Arrays.asList(RESULT_OK, RESULT_OK), results);
        assertEquals(2, countReservations());
        assertEquals(2, countOf("SELECT COUNT(*) FROM t_member_card_transaction WHERE change_type = 1"));
        assertEquals(Integer.valueOf(10),
                intOf("SELECT remaining_times FROM t_member_card WHERE id = ?", TIMES_CARD_ID));
        assertEquals(Integer.valueOf(1), intOf("SELECT booked_persons FROM t_schedule WHERE id = ?", SCHEDULE_ID));
        assertEquals(Integer.valueOf(1),
                intOf("SELECT booked_persons FROM t_schedule WHERE id = ?", SCHEDULE_OVERLAP_ID));
    }

    @Test
    @DisplayName("并发预约：同 requestId 同时提交，两次都成功且只创建一条预约、只扣一次次数")
    void create_concurrentlySameRequestId_shouldBeIdempotent() throws Exception {
        List<String> results = runConcurrently(() -> create(REQUEST_ID, SCHEDULE_ID, TIMES_CARD_ID, 2),
                () -> create(REQUEST_ID, SCHEDULE_ID, TIMES_CARD_ID, 2));

        assertEquals(2, results.stream().filter(RESULT_OK::equals).count(), "幂等重试都按成功返回：" + results);
        assertEquals(1, countReservations(), "同一 requestId 只创建一条预约");
        assertEquals(Integer.valueOf(2), intOf("SELECT booked_persons FROM t_schedule WHERE id = ?", SCHEDULE_ID));
        assertEquals(Integer.valueOf(10),
                intOf("SELECT remaining_times FROM t_member_card WHERE id = ?", TIMES_CARD_ID), "只扣一次");
    }

    @Test
    @DisplayName("并发预约：不同会员抢最后一个名额，仅成功者占位、扣次、写流水")
    void create_concurrentlyLastSeat_shouldNotOversell() throws Exception {
        List<String> results = runConcurrently(() -> create(REQUEST_ID, SCHEDULE_FULL_ID, TIMES_CARD_ID, 1),
                () -> createForMember(OTHER_MEMBER_ID, OTHER_REQUEST_ID, SCHEDULE_FULL_ID, OTHER_MEMBER_CARD_ID, 1));

        assertEquals(1, results.stream().filter(RESULT_OK::equals).count(), results.toString());
        assertTrue(results.contains("409:剩余名额不足，不能预约"), results.toString());
        assertEquals(1, countReservations());
        assertEquals(Integer.valueOf(1), intOf("SELECT booked_persons FROM t_schedule WHERE id = ?", SCHEDULE_FULL_ID));
        assertEquals(Integer.valueOf(23), intOf("SELECT SUM(remaining_times) FROM t_member_card WHERE id IN (?, ?)",
                TIMES_CARD_ID, OTHER_MEMBER_CARD_ID));
        assertEquals(1, countOf("SELECT COUNT(*) FROM t_member_card_transaction"));
        assertAccountingConsistent();
    }

    @Test
    @DisplayName("并发预约：不同请求标识预约同一排课，拒绝重复预约")
    void create_concurrentlyDuplicateSchedule_shouldAllowOnlyOne() throws Exception {
        List<String> results = runConcurrently(() -> create(REQUEST_ID, SCHEDULE_ID, TIMES_CARD_ID, 1),
                () -> create(OTHER_REQUEST_ID, SCHEDULE_ID, TIMES_CARD_ID, 1));

        assertEquals(1, results.stream().filter(RESULT_OK::equals).count(), results.toString());
        assertTrue(results.contains("409:您已预约该课程"), results.toString());
        assertEquals(1, countReservations());
        assertEquals(Integer.valueOf(11),
                intOf("SELECT remaining_times FROM t_member_card WHERE id = ?", TIMES_CARD_ID));
        assertAccountingConsistent();
    }

    @Test
    @DisplayName("并发预约：同一请求标识提交不同排课，只有一笔生效，另一笔参数冲突")
    void create_concurrentlySameRequestDifferentSchedule_shouldConflict() throws Exception {
        List<String> results = runConcurrently(() -> create(REQUEST_ID, SCHEDULE_ID, TIMES_CARD_ID, 1),
                () -> create(REQUEST_ID, SCHEDULE_SECOND_ID, TIMES_CARD_ID, 1));

        assertEquals(1, results.stream().filter(RESULT_OK::equals).count(), results.toString());
        assertTrue(results.contains("409:请求幂等标识已被其他预约使用"), results.toString());
        assertEquals(1, countReservations());
        assertEquals(Integer.valueOf(11),
                intOf("SELECT remaining_times FROM t_member_card WHERE id = ?", TIMES_CARD_ID));
        assertAccountingConsistent();
    }

    @Test
    @DisplayName("并发预约：卡剩两次，两笔预约各扣两次，只允许一笔成功")
    void create_concurrentlyInsufficientBalance_shouldNotOverdraw() throws Exception {
        jdbcTemplate.update("UPDATE t_member_card SET remaining_times = 2 WHERE id = ?", TIMES_CARD_ID);
        List<String> results = runConcurrently(() -> create(REQUEST_ID, SCHEDULE_ID, TIMES_CARD_ID, 2),
                () -> create(OTHER_REQUEST_ID, SCHEDULE_SECOND_ID, TIMES_CARD_ID, 2));

        assertEquals(1, results.stream().filter(RESULT_OK::equals).count(), results.toString());
        assertTrue(results.contains("409:会员卡剩余次数不足"), results.toString());
        assertEquals(1, countReservations());
        assertEquals(Integer.valueOf(0),
                intOf("SELECT remaining_times FROM t_member_card WHERE id = ?", TIMES_CARD_ID));
        assertEquals(Integer.valueOf(2), intOf("SELECT before_times FROM t_member_card_transaction"));
        assertEquals(Integer.valueOf(0), intOf("SELECT after_times FROM t_member_card_transaction"));
        assertAccountingConsistent();
    }

    @Test
    @DisplayName("幂等复查：首笔预约未提交时重试建立旧快照，首笔提交后仍按成功返回")
    void create_retryWithOldSnapshot_shouldReturnSuccessEvenWhenFull() throws Exception {
        List<String> results = runWithFirstTransactionHeld(() -> create(REQUEST_ID, SCHEDULE_ID, TIMES_CARD_ID, 2),
                () -> create(REQUEST_ID, SCHEDULE_ID, TIMES_CARD_ID, 2));

        assertEquals(Arrays.asList(RESULT_OK, RESULT_OK), results);
        assertEquals(1, countReservations());
        assertEquals(Integer.valueOf(10),
                intOf("SELECT remaining_times FROM t_member_card WHERE id = ?", TIMES_CARD_ID));
        assertEquals(1, countOf("SELECT COUNT(*) FROM t_member_card_transaction"));
        assertAccountingConsistent();
    }

    @Test
    @DisplayName("预约回滚：扣次流水写入失败，预约、占位与扣次全部回滚")
    void create_whenLedgerInsertFails_shouldRollbackEverything() {
        rejectLedgerInserts(1);
        try {
            assertThrows(DataAccessException.class, () -> create(REQUEST_ID, SCHEDULE_ID, TIMES_CARD_ID, 2));
        } finally {
            jdbcTemplate.execute("ALTER TABLE t_member_card_transaction DROP CHECK reject_reservation_ledger");
        }
        assertEquals(0, countReservations());
        assertEquals(Integer.valueOf(0), intOf("SELECT booked_persons FROM t_schedule WHERE id = ?", SCHEDULE_ID));
        assertEquals(Integer.valueOf(12),
                intOf("SELECT remaining_times FROM t_member_card WHERE id = ?", TIMES_CARD_ID));
        assertEquals(0, countOf("SELECT COUNT(*) FROM t_member_card_transaction"));
        create(REQUEST_ID, SCHEDULE_ID, TIMES_CARD_ID, 2);
        assertEquals(1, countReservations(), "失败后相同请求可以重试");
        assertAccountingConsistent();
    }

    @Test
    @DisplayName("取消回滚：返次流水写入失败，预约状态、名额与余额全部还原")
    void cancel_whenLedgerInsertFails_shouldRollbackEverything() {
        create(REQUEST_ID, SCHEDULE_ID, TIMES_CARD_ID, 2);
        Long id = reservationId();
        rejectLedgerInserts(2);
        try {
            assertThrows(DataAccessException.class, () -> reservationService.cancel(MEMBER_ID, id));
        } finally {
            jdbcTemplate.execute("ALTER TABLE t_member_card_transaction DROP CHECK reject_reservation_ledger");
        }
        assertEquals(Integer.valueOf(1), intOf("SELECT status FROM t_reservation WHERE id = ?", id));
        assertNull(jdbcTemplate.queryForObject("SELECT cancel_time FROM t_reservation WHERE id = ?",
                LocalDateTime.class, id));
        assertEquals(Integer.valueOf(2), intOf("SELECT booked_persons FROM t_schedule WHERE id = ?", SCHEDULE_ID));
        assertEquals(Integer.valueOf(10),
                intOf("SELECT remaining_times FROM t_member_card WHERE id = ?", TIMES_CARD_ID));
        assertEquals(1, countOf("SELECT COUNT(*) FROM t_member_card_transaction"));
        reservationService.cancel(MEMBER_ID, id);
        assertEquals(Integer.valueOf(12),
                intOf("SELECT remaining_times FROM t_member_card WHERE id = ?", TIMES_CARD_ID));
        assertAccountingConsistent();
    }

    @Test
    @DisplayName("并发取消：同一预约取消两次，只释放和返还一次")
    void cancel_concurrently_shouldRefundOnlyOnce() throws Exception {
        create(REQUEST_ID, SCHEDULE_ID, TIMES_CARD_ID, 2);
        Long id = reservationId();
        List<String> results = runConcurrently(() -> reservationService.cancel(MEMBER_ID, id),
                () -> reservationService.cancel(MEMBER_ID, id));
        assertEquals(Arrays.asList(RESULT_OK, RESULT_OK), results);
        assertEquals(Integer.valueOf(12),
                intOf("SELECT remaining_times FROM t_member_card WHERE id = ?", TIMES_CARD_ID));
        assertEquals(1, countOf("SELECT COUNT(*) FROM t_member_card_transaction WHERE change_type = 2"));
        assertAccountingConsistent();
    }

    @Test
    @DisplayName("停卡先持锁：预约使用旧快照等待，停卡提交后必须拒绝")
    void create_whenStopCommitsFirst_shouldReject() throws Exception {
        List<String> results = runWithFirstTransactionHeld(this::stopTimesCard,
                () -> create(REQUEST_ID, SCHEDULE_ID, TIMES_CARD_ID, 1));
        assertEquals(Arrays.asList(RESULT_OK, "409:会员卡已停卡，不能预约"), results);
        assertEquals(0, countReservations());
        assertEquals(Integer.valueOf(12),
                intOf("SELECT remaining_times FROM t_member_card WHERE id = ?", TIMES_CARD_ID));
        assertEquals(0, countOf("SELECT COUNT(*) FROM t_member_card_transaction"));
        assertAccountingConsistent();
    }

    @Test
    @DisplayName("预约先持锁：预约提交后才停卡，已成功预约与扣次保留")
    void stop_whenBookingCommitsFirst_shouldPreserveBooking() throws Exception {
        List<String> results = runWithFirstTransactionHeld(() -> create(REQUEST_ID, SCHEDULE_ID, TIMES_CARD_ID, 1),
                this::stopTimesCard);
        assertEquals(Arrays.asList(RESULT_OK, RESULT_OK), results);
        assertEquals(1, countReservations());
        assertNotNull(jdbcTemplate.queryForObject("SELECT stop_time FROM t_member_card WHERE id = ?",
                LocalDateTime.class, TIMES_CARD_ID));
        assertEquals(Integer.valueOf(11),
                intOf("SELECT remaining_times FROM t_member_card WHERE id = ?", TIMES_CARD_ID));
        assertAccountingConsistent();
    }

    @Test
    @DisplayName("取消排课先持锁：预约使用旧快照等待，取消提交后必须拒绝")
    void create_whenScheduleCancellationCommitsFirst_shouldReject() throws Exception {
        List<String> results = runWithFirstTransactionHeld(() -> scheduleService.changeStatus(SCHEDULE_ID, 3),
                () -> create(REQUEST_ID, SCHEDULE_ID, TIMES_CARD_ID, 1));
        assertEquals(Arrays.asList(RESULT_OK, "409:排课已取消，不能预约"), results);
        assertEquals(0, countReservations());
        assertEquals(Integer.valueOf(12),
                intOf("SELECT remaining_times FROM t_member_card WHERE id = ?", TIMES_CARD_ID));
        assertEquals(0, countOf("SELECT COUNT(*) FROM t_member_card_transaction"));
        assertAccountingConsistent();
    }

    private void stopTimesCard() {
        MemberCardStopDTO dto = new MemberCardStopDTO();
        dto.setStopReason("测试停卡");
        memberCardService.stop(TIMES_CARD_ID, dto);
    }

    private void rejectLedgerInserts(int changeType) {
        jdbcTemplate.execute("ALTER TABLE t_member_card_transaction ADD CONSTRAINT reject_reservation_ledger"
                + " CHECK (change_type <> " + changeType + ")");
    }

    private void assertAccountingConsistent() {
        assertEquals(0,
                countOf("SELECT COUNT(*) FROM t_schedule s WHERE s.booked_persons <>"
                        + " (SELECT COALESCE(SUM(r.persons), 0) FROM t_reservation r"
                        + " WHERE r.schedule_id = s.id AND r.status <> 2)"),
                "排课占位应等于有效预约人数");
        assertEquals(0, countOf("SELECT COUNT(*) FROM t_member_card WHERE remaining_times < 0"));
        assertEquals(0, countOf(
                "SELECT COUNT(*) FROM t_member_card_transaction" + " WHERE after_times <> before_times + change_times"),
                "流水余额应守恒");
        assertEquals(0,
                countOf("SELECT COUNT(*) FROM t_reservation r WHERE NOT EXISTS"
                        + " (SELECT 1 FROM t_member_card_transaction t WHERE t.reservation_id = r.id"
                        + " AND t.change_type = 1 AND t.change_times = -r.persons)"),
                "次数卡预约应有对应扣次流水");
    }

    /** 首个操作完成但尚未提交时，让第二个事务建立快照，再释放首个事务。 */
    private List<String> runWithFirstTransactionHeld(Runnable first, Runnable second) throws Exception {
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
                    return new TransactionTemplate(transactionManager).execute(s -> {
                        assertNull(reservationDao.selectByMemberAndRequestId(MEMBER_ID, REQUEST_ID));
                        secondSnapshot.countDown();
                        second.run();
                        return RESULT_OK;
                    });
                } catch (ServiceException e) {
                    return e.getCode() + ":" + e.getMessage();
                }
            });
            assertTrue(secondSnapshot.await(10, TimeUnit.SECONDS), "第二个事务应在首个提交之前建立快照");
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

    private List<String> runConcurrently(Runnable first, Runnable second) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<String> firstFuture = pool.submit(concurrentTask(first, ready, start));
            Future<String> secondFuture = pool.submit(concurrentTask(second, ready, start));
            assertTrue(ready.await(10, TimeUnit.SECONDS), "两个线程应都已就绪");
            start.countDown();
            return Arrays.asList(firstFuture.get(30, TimeUnit.SECONDS), secondFuture.get(30, TimeUnit.SECONDS));
        } finally {
            start.countDown();
            pool.shutdownNow();
            assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS), "测试线程应结束，避免影响后续数据清理");
        }
    }

    private Callable<String> concurrentTask(Runnable action, CountDownLatch ready, CountDownLatch start) {
        return () -> {
            ready.countDown();
            awaitLatch(start);
            try {
                action.run();
                return RESULT_OK;
            } catch (ServiceException e) {
                return e.getCode() + ":" + e.getMessage();
            }
        };
    }

    // ------------------------------------------------------------------
    // 管理端列表
    // ------------------------------------------------------------------

    @Test
    @DisplayName("管理端预约列表：门店／课程／排课／会员／状态／卡类型／上课日期筛选，倒序分页并装配关联资料")
    void pageForAdmin_shouldFilterAndAssemble() {
        LocalDateTime base = LocalDateTime.now(BUSINESS_ZONE).withNano(0);
        insertAdminReservation(9501L, REQUEST_ID, MEMBER_ID, SCHEDULE_ID, TIMES_CARD_ID, 2, 1, base.minusMinutes(3),
                null, null, null);
        insertDeductTransaction(9501L, TIMES_CARD_ID, MEMBER_ID, 2);
        insertAdminReservation(9502L, OTHER_REQUEST_ID, OTHER_MEMBER_ID, SCHEDULE_PERSONAL_ID, YEAR_CARD_ID, 1, 2,
                base.minusMinutes(2), base.minusMinutes(1), 1, null);
        insertAdminReservation(9503L, SECOND_REQUEST_ID, MEMBER_ID, SCHEDULE_SECOND_ID, TIMES_CARD_ID, 1, 3,
                base.minusMinutes(1), null, null, base.minusMinutes(1));
        insertAdminReservation(9504L, THIRD_REQUEST_ID, MEMBER_ID, SCHEDULE_OTHER_STORE_ID, TIMES_CARD_ID, 1, 1, base,
                null, null, null);

        PageResult<ReservationAdminItemVO> all = reservationService.pageForAdmin(adminQuery());
        assertEquals(Long.valueOf(4), all.getTotal());
        assertEquals(9504L, all.getList().get(0).getId(), "按预约时间倒序");

        ReservationAdminItemVO item = all.getList().get(3);
        assertEquals(9501L, item.getId());
        assertEquals(SCHEDULE_ID, item.getScheduleId());
        assertEquals(COURSE_ID, item.getCourseId());
        assertEquals("哈他瑜伽", item.getCourseName());
        assertEquals(Integer.valueOf(1), item.getCourseType());
        assertEquals("团课", item.getCourseTypeName());
        assertEquals(Integer.valueOf(2), item.getDifficulty());
        assertEquals("林教练", item.getCoachName());
        assertEquals(STORE_ID, item.getStoreId());
        assertEquals("徐汇店", item.getStoreName());
        assertEquals("漕溪北路 88 号", item.getStoreAddress());
        assertEquals("瑜伽团课大教室", item.getClassroomName());
        assertEquals(LocalDate.now(BUSINESS_ZONE).plusDays(1).toString(), item.getScheduleDate());
        assertEquals(Integer.valueOf(60), item.getDurationMinutes(), "起止时间差为整分钟数");
        assertEquals(Integer.valueOf(2), item.getPersons());
        assertEquals(TIMES_CARD_ID, item.getMemberCardId());
        assertEquals("测试会员卡", item.getCardName());
        assertEquals("20261015000" + TIMES_CARD_ID, item.getCardNo());
        assertEquals(Integer.valueOf(1), item.getCardType());
        assertEquals("次数卡", item.getCardTypeText());
        assertEquals(Integer.valueOf(2), item.getDeductTimes(), "扣次取扣次流水绝对值");
        assertEquals(Integer.valueOf(1), item.getStatus());
        assertEquals("已预约", item.getStatusText());
        assertEquals(Integer.valueOf(2), item.getScheduleStatus());
        assertEquals("已上架", item.getScheduleStatusText());
        assertEquals(MEMBER_ID, item.getMemberId());
        assertEquals("张小明", item.getMemberName());
        assertEquals("13800138000", item.getMemberPhone());
        assertNull(item.getCancelTime());
        assertNull(item.getCheckInTime());

        ReservationAdminItemVO cancelled = all.getList().get(2);
        assertEquals(9502L, cancelled.getId());
        assertEquals("已取消", cancelled.getStatusText());
        assertEquals("普拉提器械私教", cancelled.getCourseName());
        assertEquals("年卡", cancelled.getCardTypeText());
        assertEquals(Integer.valueOf(0), cancelled.getDeductTimes(), "无扣次流水按 0 返回");
        assertNotNull(cancelled.getCancelTime());
        assertEquals(Integer.valueOf(1), cancelled.getCancelReason());
        assertEquals("李四", cancelled.getMemberName());
        assertEquals("13800138001", cancelled.getMemberPhone());

        ReservationAdminItemVO checkedIn = all.getList().get(1);
        assertEquals(9503L, checkedIn.getId());
        assertEquals("已签到", checkedIn.getStatusText());
        assertNotNull(checkedIn.getCheckInTime());

        ReservationAdminQuery byStore = adminQuery();
        byStore.setStoreId(STORE_ID);
        assertEquals(Long.valueOf(3), reservationService.pageForAdmin(byStore).getTotal());
        ReservationAdminQuery byOtherStore = adminQuery();
        byOtherStore.setStoreId(OTHER_STORE_ID);
        PageResult<ReservationAdminItemVO> otherStore = reservationService.pageForAdmin(byOtherStore);
        assertEquals(Long.valueOf(1), otherStore.getTotal());
        assertEquals("团课大教室", otherStore.getList().get(0).getClassroomName());
        assertEquals("浦东店", otherStore.getList().get(0).getStoreName());

        ReservationAdminQuery byCourse = adminQuery();
        byCourse.setCourseId(COURSE_ID);
        assertEquals(Long.valueOf(3), reservationService.pageForAdmin(byCourse).getTotal());
        ReservationAdminQuery bySchedule = adminQuery();
        bySchedule.setScheduleId(SCHEDULE_ID);
        assertEquals(Long.valueOf(1), reservationService.pageForAdmin(bySchedule).getTotal());
        ReservationAdminQuery byMember = adminQuery();
        byMember.setMemberId(MEMBER_ID);
        assertEquals(Long.valueOf(3), reservationService.pageForAdmin(byMember).getTotal());
        ReservationAdminQuery byStatus = adminQuery();
        byStatus.setStatus(2);
        assertEquals(Long.valueOf(1), reservationService.pageForAdmin(byStatus).getTotal());
        ReservationAdminQuery byTimesCard = adminQuery();
        byTimesCard.setCardType(1);
        assertEquals(Long.valueOf(3), reservationService.pageForAdmin(byTimesCard).getTotal());
        ReservationAdminQuery byYearCard = adminQuery();
        byYearCard.setCardType(2);
        assertEquals(Long.valueOf(1), reservationService.pageForAdmin(byYearCard).getTotal());

        // 上课日期区间含首尾当天
        LocalDate tomorrow = LocalDate.now(BUSINESS_ZONE).plusDays(1);
        ReservationAdminQuery byDate = adminQuery();
        byDate.setStartDate(tomorrow);
        byDate.setEndDate(tomorrow);
        assertEquals(Long.valueOf(3), reservationService.pageForAdmin(byDate).getTotal());
        ReservationAdminQuery byOtherDate = adminQuery();
        byOtherDate.setStartDate(tomorrow.plusDays(1));
        byOtherDate.setEndDate(tomorrow.plusDays(1));
        assertEquals(Long.valueOf(1), reservationService.pageForAdmin(byOtherDate).getTotal());

        // 页码超出范围：空 rows，total 仍为筛选结果总数
        ReservationAdminQuery outOfRange = adminQuery();
        outOfRange.setPageNum(9);
        PageResult<ReservationAdminItemVO> empty = reservationService.pageForAdmin(outOfRange);
        assertTrue(empty.getList().isEmpty());
        assertEquals(Long.valueOf(4), empty.getTotal());
    }

    @Test
    @DisplayName("管理端预约列表：排课与关联资料已删除时对应展示字段为空，预约自身字段照常返回")
    void pageForAdmin_withDeletedRelations_shouldReturnNullFields() {
        insertAdminReservation(9601L, REQUEST_ID, MEMBER_ID, SCHEDULE_ID, TIMES_CARD_ID, 1, 1,
                LocalDateTime.now(BUSINESS_ZONE).withNano(0), null, null, null);
        jdbcTemplate.update("DELETE FROM t_course WHERE id = ?", COURSE_ID);
        jdbcTemplate.update("DELETE FROM t_coach WHERE id = ?", COACH_ID);
        jdbcTemplate.update("DELETE FROM t_classroom WHERE id = ?", CLASSROOM_ID);
        jdbcTemplate.update("DELETE FROM t_store WHERE id = ?", STORE_ID);
        jdbcTemplate.update("DELETE FROM t_member WHERE id = ?", MEMBER_ID);
        jdbcTemplate.update("DELETE FROM t_member_card WHERE id = ?", TIMES_CARD_ID);

        ReservationAdminItemVO item = reservationService.pageForAdmin(adminQuery()).getList().get(0);
        assertNull(item.getCourseName());
        assertNull(item.getCourseCoverUrl());
        assertNull(item.getCoachName());
        assertNull(item.getClassroomName());
        assertNull(item.getStoreName());
        assertNull(item.getStoreAddress());
        assertNull(item.getMemberName());
        assertNull(item.getMemberPhone());
        assertNull(item.getCardNo());
        assertNull(item.getCardName());
        assertEquals(SCHEDULE_ID, item.getScheduleId(), "排课 ID 仍保留");
        assertEquals(MEMBER_ID, item.getMemberId());
        assertEquals(TIMES_CARD_ID, item.getMemberCardId());
        assertEquals("已预约", item.getStatusText(), "预约状态文案不受关联删除影响");
        assertEquals(Integer.valueOf(0), item.getDeductTimes());
    }

    @Test
    @DisplayName("管理端预约列表：状态、卡类型、日期范围与 ID 非法都返回参数错误")
    void pageForAdmin_withIllegalQuery_shouldFail() {
        ReservationAdminQuery badStatus = adminQuery();
        badStatus.setStatus(9);
        assertMessage(400, "预约状态取值为 1、2 或 3", () -> reservationService.pageForAdmin(badStatus));

        ReservationAdminQuery badCardType = adminQuery();
        badCardType.setCardType(9);
        assertMessage(400, "卡类型取值为 1 或 2", () -> reservationService.pageForAdmin(badCardType));

        ReservationAdminQuery inverted = adminQuery();
        inverted.setStartDate(LocalDate.now(BUSINESS_ZONE).plusDays(2));
        inverted.setEndDate(LocalDate.now(BUSINESS_ZONE).plusDays(1));
        assertMessage(400, "结束日期不能早于起始日期", () -> reservationService.pageForAdmin(inverted));

        ReservationAdminQuery badId = adminQuery();
        badId.setScheduleId(0L);
        assertMessage(400, "排课ID格式非法", () -> reservationService.pageForAdmin(badId));

        ReservationAdminQuery badMember = adminQuery();
        badMember.setMemberId(-1L);
        assertMessage(400, "会员ID格式非法", () -> reservationService.pageForAdmin(badMember));
    }

    private ReservationAdminQuery adminQuery() {
        ReservationAdminQuery query = new ReservationAdminQuery();
        query.setPageNum(1);
        query.setPageSize(10);
        return query;
    }

    // ------------------------------------------------------------------
    // 夹具
    // ------------------------------------------------------------------

    private static final String RESULT_OK = "ok";

    private static final String REQUEST_ID = "2f1a4c1e-6a2b-4c1d-9f0e-1a2b3c4d5e6f";

    private static final String OTHER_REQUEST_ID = "3f1a4c1e-6a2b-4c1d-9f0e-1a2b3c4d5e70";

    private static final String SECOND_REQUEST_ID = "4f1a4c1e-6a2b-4c1d-9f0e-1a2b3c4d5e71";

    private static final String THIRD_REQUEST_ID = "5f1a4c1e-6a2b-4c1d-9f0e-1a2b3c4d5e72";

    private static final String FOURTH_REQUEST_ID = "6f1a4c1e-6a2b-4c1d-9f0e-1a2b3c4d5e73";

    private void create(String requestId, Long scheduleId, Long memberCardId, Integer persons) {
        createForMember(MEMBER_ID, requestId, scheduleId, memberCardId, persons);
    }

    private void createForMember(Long memberId, String requestId, Long scheduleId, Long memberCardId, Integer persons) {
        ReservationCreateDTO dto = new ReservationCreateDTO();
        dto.setRequestId(requestId);
        dto.setScheduleId(scheduleId);
        dto.setMemberCardId(memberCardId);
        dto.setPersons(persons);
        reservationService.create(memberId, dto);
    }

    private ReservationPublicQuery query(Integer status, Integer cardType) {
        ReservationPublicQuery query = new ReservationPublicQuery();
        query.setStatus(status);
        query.setCardType(cardType);
        query.setPageNum(1);
        query.setPageSize(10);
        return query;
    }

    private void assertMessage(int code, String message, Runnable action) {
        ServiceException exception = assertThrows(ServiceException.class, action::run);
        assertEquals(Integer.valueOf(code), exception.getCode());
        assertEquals(message, exception.getMessage());
    }

    private Long reservationId() {
        return jdbcTemplate.queryForObject("SELECT MAX(id) FROM t_reservation", Long.class);
    }

    private int countReservations() {
        return countOf("SELECT COUNT(*) FROM t_reservation");
    }

    private int countOf(String sql, Object... args) {
        Integer value = jdbcTemplate.queryForObject(sql, Integer.class, args);
        return value == null ? 0 : value;
    }

    private Integer intOf(String sql, Object... args) {
        return jdbcTemplate.queryForObject(sql, Integer.class, args);
    }

    private String expectedScheduleDate(Long scheduleId) {
        LocalDate date = jdbcTemplate.queryForObject("SELECT schedule_date FROM t_schedule WHERE id = ?",
                LocalDate.class, scheduleId);
        return date == null ? null : date.toString();
    }

    private LocalDateTime scheduleStartTime(Long scheduleId) {
        return jdbcTemplate.queryForObject("SELECT start_time FROM t_schedule WHERE id = ?", LocalDateTime.class,
                scheduleId);
    }

    private LocalDateTime scheduleEndTime(Long scheduleId) {
        return jdbcTemplate.queryForObject("SELECT end_time FROM t_schedule WHERE id = ?", LocalDateTime.class,
                scheduleId);
    }

    private void insertStores() {
        insertStore(STORE_ID, "0001", "徐汇店", "310104", "漕溪北路 88 号");
        insertStore(OTHER_STORE_ID, "0002", "浦东店", "310115", "世纪大道 1000 号");
    }

    private void insertStore(Long id, String storeNo, String name, String regionCode, String address) {
        jdbcTemplate.update(
                "INSERT INTO t_store (id, store_no, name, store_type, region_code, phone,"
                        + " business_hours, address, create_by, create_time, update_by, update_time)"
                        + " VALUES (?, ?, ?, 1, ?, '021-12345678', '周一至周日 09:00-22:00', ?, 1, NOW(), 1, NOW())",
                id, storeNo, name, regionCode, address);
    }

    private void insertClassroom() {
        jdbcTemplate.update("INSERT INTO t_classroom (id, store_id, name, create_by, create_time, update_by,"
                + " update_time) VALUES (?, ?, '瑜伽团课大教室', 1, NOW(), 1, NOW())", CLASSROOM_ID, STORE_ID);
        jdbcTemplate.update(
                "INSERT INTO t_classroom (id, store_id, name, create_by, create_time, update_by,"
                        + " update_time) VALUES (?, ?, '团课大教室', 1, NOW(), 1, NOW())",
                OTHER_CLASSROOM_ID, OTHER_STORE_ID);
    }

    private void insertCoach() {
        jdbcTemplate.update("INSERT INTO t_coach (id, name, avatar_url, intro, phone, create_by, create_time,"
                + " update_by, update_time) VALUES (?, '林教练', NULL, NULL, NULL, 1, NOW(), 1, NOW())", COACH_ID);
    }

    private void insertCourses() {
        insertCourse(COURSE_ID, "哈他瑜伽", 1);
        insertCourse(PERSONAL_COURSE_ID, "普拉提器械私教", 3);
    }

    private void insertCourse(Long id, String name, int courseType) {
        jdbcTemplate.update(
                "INSERT INTO t_course (id, name, course_type, cover_url, intro, difficulty, create_by,"
                        + " create_time, update_by, update_time) VALUES (?, ?, ?, NULL, NULL, 2, 1, NOW(), 1, NOW())",
                id, name, courseType);
    }

    private void insertMembers() {
        insertMember(MEMBER_ID, "13800138000", "张小明");
        insertMember(OTHER_MEMBER_ID, "13800138001", "李四");
        insertMember(THIRD_MEMBER_ID, "13800138002", "王");
        insertMember(FOURTH_MEMBER_ID, "13800138003", "赵六");
    }

    private void insertMember(Long id, String phone, String name) {
        jdbcTemplate.update(
                "INSERT INTO t_member (id, phone, password, phone_last4, name, gender, level, create_by,"
                        + " create_time, update_by, update_time) VALUES (?, ?, NULL, ?, ?, 0, 0, 1, NOW(), 1, NOW())",
                id, phone, phone.substring(phone.length() - 4), name);
    }

    private void insertCards() {
        LocalDateTime now = LocalDateTime.now(BUSINESS_ZONE);
        // 次数卡：团课类、本店、剩余 12 次、有效期未过
        insertCard(TIMES_CARD_ID, MEMBER_ID, 1, 1, STORE_ID, 12, now.minusDays(1), now.plusDays(300), now.minusDays(1),
                null);
        // 年卡：团课类、全门店通用、无剩余次数
        insertCard(YEAR_CARD_ID, MEMBER_ID, 2, 1, null, null, now.minusDays(1), now.plusDays(365), now.minusDays(1),
                null);
        // 未开卡
        insertCard(PENDING_CARD_ID, MEMBER_ID, 1, 1, STORE_ID, 12, null, null, null, null);
        // 已停卡
        insertCard(STOPPED_CARD_ID, MEMBER_ID, 1, 1, STORE_ID, 12, now.minusDays(1), now.plusDays(300),
                now.minusDays(2), now.minusDays(1));
        // 已过期
        insertCard(EXPIRED_CARD_ID, MEMBER_ID, 1, 1, STORE_ID, 12, now.minusDays(10), now.minusDays(1),
                now.minusDays(10), null);
        // 门店不适用：适用门店是另一家
        insertCard(OTHER_STORE_CARD_ID, MEMBER_ID, 1, 1, OTHER_STORE_ID, 12, now.minusDays(1), now.plusDays(300),
                now.minusDays(1), null);
        // 课种不适用：私教课卡用不了团课
        insertCard(PERSONAL_CARD_ID, MEMBER_ID, 1, 2, STORE_ID, 12, now.minusDays(1), now.plusDays(300),
                now.minusDays(1), null);
        // 属于其他会员
        insertCard(OTHER_MEMBER_CARD_ID, OTHER_MEMBER_ID, 1, 1, STORE_ID, 12, now.minusDays(1), now.plusDays(300),
                now.minusDays(1), null);
    }

    private void insertCard(Long id, Long memberId, int cardType, int courseScope, Long storeId, Integer remainingTimes,
            LocalDateTime validStartTime, LocalDateTime validEndTime, LocalDateTime activateTime,
            LocalDateTime stopTime) {
        jdbcTemplate.update("INSERT INTO t_member_card (id, member_id, open_store_id, card_no, card_name, card_type,"
                + " course_scope, store_id, purchase_times, gift_times, remaining_times, valid_period_unit,"
                + " valid_period_count, valid_start_time, valid_end_time, activate_time, stop_time, stop_reason,"
                + " create_by, create_time, update_by, update_time)"
                + " VALUES (?, ?, ?, ?, '测试会员卡', ?, ?, ?, ?, 0, ?, 1, 12, ?, ?, ?, ?, NULL, 1, NOW(), 1, NOW())", id,
                memberId, STORE_ID, "20261015000" + id, cardType, courseScope, storeId,
                cardType == 1 ? remainingTimes : null, remainingTimes, validStartTime, validEndTime, activateTime,
                stopTime);
    }

    private void insertSchedules() {
        LocalDate today = LocalDate.now(BUSINESS_ZONE);
        LocalDate tomorrow = today.plusDays(1);
        LocalDateTime now = LocalDateTime.now(BUSINESS_ZONE).withNano(0);
        insertSchedule(SCHEDULE_ID, COURSE_ID, 1, tomorrow.atTime(10, 0), tomorrow.atTime(11, 0), 2, 0, 2);
        insertSchedule(SCHEDULE_SECOND_ID, COURSE_ID, 1, tomorrow.atTime(14, 0), tomorrow.atTime(15, 0), 5, 0, 2);
        insertSchedule(SCHEDULE_OVERLAP_ID, COURSE_ID, 1, tomorrow.atTime(10, 30), tomorrow.atTime(11, 30), 5, 0, 2);
        insertSchedule(SCHEDULE_PERSONAL_ID, PERSONAL_COURSE_ID, 3, tomorrow.atTime(16, 0), tomorrow.atTime(17, 0), 5,
                0, 2);
        insertSchedule(SCHEDULE_PENDING_ID, COURSE_ID, 1, tomorrow.atTime(18, 0), tomorrow.atTime(19, 0), 5, 0, 1);
        insertSchedule(SCHEDULE_CANCELLED_ID, COURSE_ID, 1, tomorrow.atTime(19, 0), tomorrow.atTime(20, 0), 5, 0, 3);
        insertSchedule(SCHEDULE_STARTED_ID, COURSE_ID, 1, now.minusHours(1), now.plusMinutes(30), 5, 0, 2);
        insertSchedule(SCHEDULE_SOON_ID, COURSE_ID, 1, now.plusMinutes(20), now.plusMinutes(80), 5, 0, 2);
        insertSchedule(SCHEDULE_TOO_EARLY_ID, COURSE_ID, 1, now.plusMinutes(90), now.plusMinutes(150), 5, 0, 2);
        insertSchedule(SCHEDULE_ENDED_ID, COURSE_ID, 1, now.minusHours(2), now.minusHours(1), 5, 0, 2);
        insertSchedule(SCHEDULE_FULL_ID, COURSE_ID, 1, tomorrow.atTime(12, 0), tomorrow.atTime(13, 0), 1, 0, 2);
        insertSchedule(SCHEDULE_CANCEL_WINDOW_ID, COURSE_ID, 1, now.plusMinutes(90), now.plusMinutes(150), 5, 0, 2);
        // 另一家门店的场次：管理端按门店与上课日期筛选用
        insertSchedule(SCHEDULE_OTHER_STORE_ID, OTHER_STORE_ID, OTHER_CLASSROOM_ID, COURSE_ID, 1,
                today.plusDays(2).atTime(10, 0), today.plusDays(2).atTime(11, 0), 5, 0, 2);
    }

    private void insertSchedule(Long id, Long courseId, int courseType, LocalDateTime startTime, LocalDateTime endTime,
            int maxPersons, int bookedPersons, int status) {
        insertSchedule(id, STORE_ID, CLASSROOM_ID, courseId, courseType, startTime, endTime, maxPersons, bookedPersons,
                status);
    }

    private void insertSchedule(Long id, Long storeId, Long classroomId, Long courseId, int courseType,
            LocalDateTime startTime, LocalDateTime endTime, int maxPersons, int bookedPersons, int status) {
        jdbcTemplate.update(
                "INSERT INTO t_schedule (id, store_id, course_id, course_type, coach_id, classroom_id,"
                        + " schedule_date, start_time, end_time, max_persons, min_persons, booked_persons, status,"
                        + " create_by, create_time, update_by, update_time)"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1, ?, ?, 1, NOW(), 1, NOW())",
                id, storeId, courseId, courseType, COACH_ID, classroomId, startTime.toLocalDate(), startTime, endTime,
                maxPersons, bookedPersons, status);
    }

    private void insertReservation(Long id, String requestId, Long memberId, Long scheduleId, Long memberCardId,
            int persons, int status) {
        LocalDateTime startTime = jdbcTemplate.queryForObject("SELECT start_time FROM t_schedule WHERE id = ?",
                LocalDateTime.class, scheduleId);
        LocalDateTime endTime = jdbcTemplate.queryForObject("SELECT end_time FROM t_schedule WHERE id = ?",
                LocalDateTime.class, scheduleId);
        insertReservation(id, requestId, memberId, scheduleId, memberCardId, persons, status,
                LocalDateTime.now(BUSINESS_ZONE).withNano(0).minusMinutes(1), startTime, endTime);
    }

    private void insertReservation(Long id, String requestId, Long memberId, Long scheduleId, Long memberCardId,
            int persons, int status, LocalDateTime reservationTime) {
        LocalDateTime startTime = jdbcTemplate.queryForObject("SELECT start_time FROM t_schedule WHERE id = ?",
                LocalDateTime.class, scheduleId);
        LocalDateTime endTime = jdbcTemplate.queryForObject("SELECT end_time FROM t_schedule WHERE id = ?",
                LocalDateTime.class, scheduleId);
        insertReservation(id, requestId, memberId, scheduleId, memberCardId, persons, status, reservationTime,
                startTime, endTime);
    }

    private void insertReservation(Long id, String requestId, Long memberId, Long scheduleId, Long memberCardId,
            int persons, int status, LocalDateTime reservationTime, LocalDateTime startTime, LocalDateTime endTime) {
        jdbcTemplate.update("INSERT INTO t_reservation (id, request_id, member_id, schedule_id, start_time, end_time,"
                + " persons, member_card_id, status, reservation_time, cancel_time, cancel_reason, check_in_time,"
                + " create_by, create_time, update_by, update_time)"
                + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NULL, NULL, NULL, 1, NOW(), 1, NOW())", id, requestId,
                memberId, scheduleId, startTime, endTime, persons, memberCardId, status, reservationTime);
    }

    /**
     * 用独立连接插入并提交一条预约：JdbcTemplate 会复用事务绑定连接，
     * 验证「快照之后提交的数据」时必须另开连接。
     */
    private void insertReservationWithNewConnection(Long id, String requestId, Long memberId, Long scheduleId,
            Long memberCardId, int persons, int status, LocalDateTime reservationTime) throws SQLException {
        LocalDateTime startTime = scheduleStartTime(scheduleId);
        LocalDateTime endTime = scheduleEndTime(scheduleId);
        String sql = "INSERT INTO t_reservation (id, request_id, member_id, schedule_id, start_time, end_time,"
                + " persons, member_card_id, status, reservation_time, cancel_time, cancel_reason, check_in_time,"
                + " create_by, create_time, update_by, update_time)"
                + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NULL, NULL, NULL, 1, NOW(), 1, NOW())";
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);
            statement.setString(2, requestId);
            statement.setLong(3, memberId);
            statement.setLong(4, scheduleId);
            statement.setTimestamp(5, Timestamp.valueOf(startTime));
            statement.setTimestamp(6, Timestamp.valueOf(endTime));
            statement.setInt(7, persons);
            statement.setLong(8, memberCardId);
            statement.setInt(9, status);
            statement.setTimestamp(10, Timestamp.valueOf(reservationTime));
            statement.executeUpdate();
        }
    }

    /** 管理端列表夹具：可显式指定取消与签到事件时间 */
    private void insertAdminReservation(Long id, String requestId, Long memberId, Long scheduleId, Long memberCardId,
            int persons, int status, LocalDateTime reservationTime, LocalDateTime cancelTime, Integer cancelReason,
            LocalDateTime checkInTime) {
        jdbcTemplate.update("INSERT INTO t_reservation (id, request_id, member_id, schedule_id, start_time, end_time,"
                + " persons, member_card_id, status, reservation_time, cancel_time, cancel_reason, check_in_time,"
                + " create_by, create_time, update_by, update_time)"
                + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1, NOW(), 1, NOW())", id, requestId, memberId,
                scheduleId, scheduleStartTime(scheduleId), scheduleEndTime(scheduleId), persons, memberCardId, status,
                reservationTime, cancelTime, cancelReason, checkInTime);
    }

    /** 扣次流水夹具：change_times 为负、余额按 12 → 12 - persons */
    private void insertDeductTransaction(Long reservationId, Long memberCardId, Long memberId, int persons) {
        jdbcTemplate.update(
                "INSERT INTO t_member_card_transaction (id, member_card_id, member_id, reservation_id,"
                        + " change_type, persons, change_times, before_times, after_times, create_time)"
                        + " VALUES (?, ?, ?, ?, 1, ?, ?, 12, ?, NOW())",
                reservationId + 1000000L, memberCardId, memberId, reservationId, persons, -persons, 12 - persons);
    }
}
