package com.techplant.yoga.reservation.service.impl;

import lombok.RequiredArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.ruoyi.common.exception.ServiceException;
import com.techplant.yoga.classroom.service.ClassroomService;
import com.techplant.yoga.classroom.vo.ClassroomSummaryVO;
import com.techplant.yoga.coach.service.CoachService;
import com.techplant.yoga.coach.vo.CoachSummaryVO;
import com.techplant.yoga.common.response.PageResult;
import com.techplant.yoga.course.service.CourseService;
import com.techplant.yoga.course.vo.CourseSummaryVO;
import com.techplant.yoga.member.service.MemberService;
import com.techplant.yoga.member.vo.MemberSummaryVO;
import com.techplant.yoga.membercard.enums.MemberCardTypeEnum;
import com.techplant.yoga.membercard.service.MemberCardService;
import com.techplant.yoga.membercard.service.MemberCardTransactionService;
import com.techplant.yoga.membercard.vo.MemberCardBookingSnapshot;
import com.techplant.yoga.membercard.vo.MemberCardSummaryVO;
import com.techplant.yoga.reservation.convert.ReservationConverter;
import com.techplant.yoga.reservation.dao.ReservationDao;
import com.techplant.yoga.reservation.domain.ReservationDO;
import com.techplant.yoga.reservation.dto.ReservationCreateDTO;
import com.techplant.yoga.reservation.enums.ReservationCancelReasonEnum;
import com.techplant.yoga.reservation.enums.ReservationStatusEnum;
import com.techplant.yoga.reservation.query.ReservationAdminQuery;
import com.techplant.yoga.reservation.query.ReservationPublicQuery;
import com.techplant.yoga.reservation.service.ReservationService;
import com.techplant.yoga.reservation.vo.ReservationAdminItemVO;
import com.techplant.yoga.reservation.vo.ReservationPublicItemVO;
import com.techplant.yoga.schedule.enums.ScheduleStatusEnum;
import com.techplant.yoga.schedule.service.ScheduleBookingService;
import com.techplant.yoga.schedule.vo.ScheduleBookingSnapshot;
import com.techplant.yoga.store.service.StoreService;
import com.techplant.yoga.store.vo.StoreSummaryVO;

/** 用户端预约业务实现。 */
@Service
@RequiredArgsConstructor
public class ReservationServiceImpl implements ReservationService {
    /** 资源不存在：排课、会员卡、会员或关联资料已删除 */
    private static final int NOT_FOUND = 404;

    /** 业务冲突：名额、次数、时间、重复预约与状态流转 */
    private static final int CONFLICT = 409;

    /** 参数校验失败／系统异常 */
    private static final int PARAM_ERROR = 500;

    /** 预约业务时区 */
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");

    /** 可预约窗口：今天起 14 天内（含今天） */
    private static final int SCHEDULE_WINDOW_DAYS = 14;

    /** 取消截止：课程开始前 2 小时 */
    private static final long CANCEL_DEADLINE_MINUTES = 120L;

    /** 签到窗口：课程开始前 30 分钟 */
    private static final long CHECK_IN_OPEN_MINUTES = 30L;

    /** 排课详情最多展示的预约人数 */
    private static final int MAX_BOOKERS = 10;

    private final ReservationDao reservationDao;

    private final ScheduleBookingService scheduleBookingService;

    private final MemberCardService memberCardService;

    private final MemberCardTransactionService memberCardTransactionService;

    private final MemberService memberService;

    private final CourseService courseService;

    private final CoachService coachService;

    private final StoreService storeService;

    @Lazy
    private final ClassroomService classroomService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void create(Long memberId, ReservationCreateDTO dto) {
        String requestId = requireRequestId(dto.getRequestId());
        Long scheduleId = requirePositiveId(dto.getScheduleId(), "排课ID");
        Long memberCardId = requirePositiveId(dto.getMemberCardId(), "会员卡ID");
        Integer persons = requirePersons(dto.getPersons());

        // 幂等：同一个 requestId 且参数一致直接按成功返回
        ReservationDO submitted = reservationDao.selectByMemberAndRequestId(memberId, requestId);
        if (submitted != null) {
            requireSameRequest(submitted, scheduleId, memberCardId, persons);
            return;
        }

        // 锁顺序：排课 → 会员 → 会员卡
        ScheduleBookingSnapshot schedule = scheduleBookingService.lockForBooking(scheduleId);
        if (schedule == null) {
            throw new ServiceException("排课不存在或已被删除", NOT_FOUND);
        }
        if (!memberService.lockForBooking(memberId)) {
            throw new ServiceException("会员不存在或已被删除", NOT_FOUND);
        }

        // 取锁后复查幂等键与冲突：都用当前读，避免事务快照看不到并发提交的预约
        ReservationDO current = reservationDao.selectByMemberAndRequestIdForUpdate(memberId, requestId);
        if (current != null) {
            requireSameRequest(current, scheduleId, memberCardId, persons);
            return;
        }

        validateSchedule(schedule, persons);
        MemberCardBookingSnapshot card = memberCardService.lockBookableCard(memberCardId, memberId,
                schedule.getCourseType(), schedule.getStoreId(), schedule.getStartTime(), schedule.getEndTime());
        validateCardLimit(card, persons);
        if (courseService.getSummary(schedule.getCourseId()) == null) {
            throw new ServiceException("课程不存在或已被删除", NOT_FOUND);
        }
        if (reservationDao.existsActiveByMemberAndSchedule(memberId, scheduleId)) {
            throw new ServiceException("您已预约该课程", CONFLICT);
        }

        LocalDateTime reservationTime = LocalDateTime.now(BUSINESS_ZONE);
        ReservationDO reservation = ReservationConverter.toDO(dto, memberId, schedule, reservationTime);
        try {
            if (reservationDao.insert(reservation) != 1 || reservation.getId() == null) {
                throw new ServiceException("预约失败，请稍后重试", PARAM_ERROR);
            }
        } catch (DuplicateKeyException e) {
            ReservationDO duplicated = reservationDao.selectByMemberAndRequestIdForUpdate(memberId, requestId);
            if (duplicated == null) {
                throw new ServiceException("请勿重复提交，请稍后重试", CONFLICT);
            }
            requireSameRequest(duplicated, scheduleId, memberCardId, persons);
            return;
        }

        int bookedPersons = bookedPersons(schedule) + persons;
        if (scheduleBookingService.updateBookingCount(scheduleId, bookedPersons) != 1) {
            throw new ServiceException("剩余名额不足，预约失败", CONFLICT);
        }
        deductTimesIfNeeded(reservation, card);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long memberId, Long reservationId) {
        Long id = requirePositiveId(reservationId, "预约ID");
        ReservationDO preview = requireOwnedReservation(id, memberId);

        // 锁顺序：排课 → 预约 → 会员卡
        ScheduleBookingSnapshot schedule = scheduleBookingService.lockForBooking(preview.getScheduleId());
        ReservationDO reservation = requireOwnedReservationForUpdate(id, memberId);
        if (ReservationStatusEnum.CANCELLED.getCode().equals(reservation.getStatus())) {
            return;
        }
        if (ReservationStatusEnum.CHECKED_IN.getCode().equals(reservation.getStatus())) {
            throw new ServiceException("已签到的预约不能取消", CONFLICT);
        }
        if (schedule == null || ScheduleStatusEnum.of(schedule.getStatus()) != ScheduleStatusEnum.PUBLISHED) {
            throw new ServiceException("排课已取消或已结束，不能取消预约", CONFLICT);
        }
        LocalDateTime now = LocalDateTime.now(BUSINESS_ZONE);
        if (reservation.getStartTime() == null
                || now.isAfter(reservation.getStartTime().minusMinutes(CANCEL_DEADLINE_MINUTES))) {
            throw new ServiceException("已超过取消截止时间，不能取消预约", CONFLICT);
        }

        if (reservationDao.cancel(id, now, ReservationCancelReasonEnum.MEMBER.getCode()) != 1) {
            throw new ServiceException("预约状态已变更，请刷新后重试", CONFLICT);
        }
        int bookedPersons = Math.max(bookedPersons(schedule) - reservation.getPersons(), 0);
        if (scheduleBookingService.updateBookingCount(schedule.getId(), bookedPersons) != 1) {
            throw new ServiceException("名额释放失败，请稍后重试", CONFLICT);
        }
        refundTimesIfNeeded(reservation);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void checkIn(Long memberId, Long reservationId) {
        Long id = requirePositiveId(reservationId, "预约ID");
        ReservationDO preview = requireOwnedReservation(id, memberId);

        // 锁顺序：排课 → 预约
        ScheduleBookingSnapshot schedule = scheduleBookingService.lockForBooking(preview.getScheduleId());
        ReservationDO reservation = requireOwnedReservationForUpdate(id, memberId);
        if (ReservationStatusEnum.CHECKED_IN.getCode().equals(reservation.getStatus())
                || reservation.getCheckInTime() != null) {
            return;
        }
        if (ReservationStatusEnum.CANCELLED.getCode().equals(reservation.getStatus())) {
            throw new ServiceException("已取消的预约不能签到", CONFLICT);
        }
        if (schedule == null || ScheduleStatusEnum.of(schedule.getStatus()) != ScheduleStatusEnum.PUBLISHED) {
            throw new ServiceException("排课已取消，不能签到", CONFLICT);
        }
        if (reservation.getStartTime() == null || reservation.getEndTime() == null) {
            throw new ServiceException("排课数据异常，不能签到", CONFLICT);
        }
        LocalDateTime now = LocalDateTime.now(BUSINESS_ZONE);
        if (now.isBefore(reservation.getStartTime().minusMinutes(CHECK_IN_OPEN_MINUTES))) {
            throw new ServiceException("未到签到时间", CONFLICT);
        }
        if (!now.isBefore(reservation.getEndTime())) {
            throw new ServiceException("课程已结束，不能签到", CONFLICT);
        }
        if (reservationDao.checkIn(id, now) != 1) {
            throw new ServiceException("预约状态已变更，请刷新后重试", CONFLICT);
        }
    }

    @Override
    public PageResult<ReservationPublicItemVO> pageForMember(Long memberId, ReservationPublicQuery query) {
        if (query.getStatus() != null && ReservationStatusEnum.of(query.getStatus()) == null) {
            throw new ServiceException("预约状态取值为 1、2 或 3", PARAM_ERROR);
        }
        if (query.getCardType() != null && MemberCardTypeEnum.of(query.getCardType()) == null) {
            throw new ServiceException("卡类型取值为 1 或 2", PARAM_ERROR);
        }
        List<Long> cardIds = null;
        if (query.getCardType() != null) {
            cardIds = memberCardService.cardIdsOfMember(memberId, query.getCardType());
            if (cardIds.isEmpty()) {
                return PageResult.of(0L, query.getPageNum(), query.getPageSize(),
                        new ArrayList<ReservationPublicItemVO>());
            }
        }

        IPage<ReservationDO> page = reservationDao.selectPageForMember(memberId, query, cardIds);
        List<ReservationDO> records = page.getRecords();
        Map<Long, ScheduleBookingSnapshot> schedules = indexSchedules(distinct(records, ReservationDO::getScheduleId));
        Map<Long, CourseSummaryVO> courses = indexCourses(scheduleCourseIds(schedules.values()));
        Map<Long, String> coachNames = indexCoachNames(scheduleCoachIds(schedules.values()));
        Map<Long, String> storeAddresses = indexStoreAddresses(scheduleStoreIds(schedules.values()));

        List<ReservationPublicItemVO> list = new ArrayList<ReservationPublicItemVO>(records.size());
        for (ReservationDO reservation : records) {
            ScheduleBookingSnapshot schedule = schedules.get(reservation.getScheduleId());
            CourseSummaryVO course = schedule == null ? null : courses.get(schedule.getCourseId());
            list.add(ReservationConverter.toPublicItemVO(reservation, schedule == null ? null : schedule.getCourseId(),
                    schedule == null ? null : schedule.getCourseType(), course == null ? null : course.getName(),
                    course == null ? null : course.getCoverUrl(),
                    schedule == null ? null : coachNames.get(schedule.getCoachId()),
                    schedule == null ? null : storeAddresses.get(schedule.getStoreId())));
        }
        return PageResult.of(page.getTotal(), page.getCurrent(), page.getSize(), list);
    }

    @Override
    public PageResult<ReservationAdminItemVO> pageForAdmin(ReservationAdminQuery query) {
        validateAdminQuery(query);
        IPage<ReservationDO> page = reservationDao.selectAdminPage(query);
        List<ReservationDO> records = page.getRecords();
        if (records.isEmpty()) {
            return PageResult.of(page.getTotal(), page.getCurrent(), page.getSize(),
                    new ArrayList<ReservationAdminItemVO>());
        }

        Map<Long, ScheduleBookingSnapshot> schedules = indexSchedules(distinct(records, ReservationDO::getScheduleId));
        Map<Long, CourseSummaryVO> courses = indexCourses(scheduleCourseIds(schedules.values()));
        Map<Long, String> coachNames = indexCoachNames(scheduleCoachIds(schedules.values()));
        Map<Long, String> classroomNames = indexClassroomNames(scheduleClassroomIds(schedules.values()));
        Map<Long, StoreSummaryVO> stores = indexStoreSummaries(scheduleStoreIds(schedules.values()));
        Map<Long, MemberSummaryVO> members = indexMemberSummaries(reservationMemberIds(records));
        Map<Long, MemberCardSummaryVO> cards = indexCardSummaries(reservationCardIds(records));
        Map<Long, Integer> deductTimes = memberCardTransactionService.deductedTimesOf(reservationIds(records));

        LocalDateTime now = LocalDateTime.now(BUSINESS_ZONE);
        List<ReservationAdminItemVO> list = new ArrayList<ReservationAdminItemVO>(records.size());
        for (ReservationDO reservation : records) {
            ScheduleBookingSnapshot schedule = schedules.get(reservation.getScheduleId());
            list.add(ReservationConverter.toAdminItemVO(reservation, schedule,
                    schedule == null ? null : courses.get(schedule.getCourseId()),
                    schedule == null ? null : coachNames.get(schedule.getCoachId()),
                    schedule == null ? null : stores.get(schedule.getStoreId()),
                    schedule == null ? null : classroomNames.get(schedule.getClassroomId()),
                    members.get(reservation.getMemberId()), cards.get(reservation.getMemberCardId()),
                    deductTimes.get(reservation.getId()), now));
        }
        return PageResult.of(page.getTotal(), page.getCurrent(), page.getSize(), list);
    }

    /** 管理端筛选校验：状态与卡类型取值、日期范围与 ID 格式 */
    private void validateAdminQuery(ReservationAdminQuery query) {
        if (query.getStatus() != null && ReservationStatusEnum.of(query.getStatus()) == null) {
            throw new ServiceException("预约状态取值为 1、2 或 3", PARAM_ERROR);
        }
        if (query.getCardType() != null && MemberCardTypeEnum.of(query.getCardType()) == null) {
            throw new ServiceException("卡类型取值为 1 或 2", PARAM_ERROR);
        }
        if (query.getStartDate() != null && query.getEndDate() != null
                && query.getStartDate().isAfter(query.getEndDate())) {
            throw new ServiceException("结束日期不能早于起始日期", PARAM_ERROR);
        }
        requirePositiveIdOrNull(query.getStoreId(), "门店ID");
        requirePositiveIdOrNull(query.getCourseId(), "课程ID");
        requirePositiveIdOrNull(query.getScheduleId(), "排课ID");
        requirePositiveIdOrNull(query.getMemberId(), "会员ID");
    }

    /** 可选 ID：传了就必须是正整数 */
    private void requirePositiveIdOrNull(Long id, String fieldName) {
        if (id != null) {
            requirePositiveId(id, fieldName);
        }
    }

    @Override
    public List<String> bookerDisplayNames(Long scheduleId) {
        List<String> bookers = new ArrayList<String>();
        if (scheduleId == null) {
            return bookers;
        }
        Set<Long> memberIds = new LinkedHashSet<Long>();
        for (ReservationDO reservation : reservationDao.selectActiveBySchedule(scheduleId)) {
            if (reservation.getMemberId() != null) {
                memberIds.add(reservation.getMemberId());
            }
            if (memberIds.size() >= MAX_BOOKERS) {
                break;
            }
        }
        if (memberIds.isEmpty()) {
            return bookers;
        }
        Map<Long, String> names = new HashMap<Long, String>();
        for (MemberSummaryVO member : memberService.summaries(memberIds)) {
            names.put(member.getId(), member.getName());
        }
        for (Long memberId : memberIds) {
            bookers.add(maskName(names.get(memberId)));
        }
        return bookers;
    }

    /** 姓名脱敏：保留首字符，其余替换为一个「*」；单字符姓名返回「*」，姓名缺失返回空字符串 */
    private String maskName(String name) {
        if (name == null || name.isEmpty()) {
            return "";
        }
        return name.length() == 1 ? "*" : name.charAt(0) + "*";
    }

    /** 排课校验：待上架按不存在处理，已取消与已开始、超出窗口、名额不足都拒绝预约 */
    private void validateSchedule(ScheduleBookingSnapshot schedule, Integer persons) {
        ScheduleStatusEnum status = ScheduleStatusEnum.of(schedule.getStatus());
        if (status == null || status == ScheduleStatusEnum.PENDING) {
            throw new ServiceException("排课不存在或已被删除", NOT_FOUND);
        }
        if (status == ScheduleStatusEnum.CANCELLED) {
            throw new ServiceException("排课已取消，不能预约", CONFLICT);
        }
        LocalDateTime now = LocalDateTime.now(BUSINESS_ZONE);
        if (schedule.getStartTime() == null || !schedule.getStartTime().isAfter(now)) {
            throw new ServiceException("排课已开始或已结束，不能预约", CONFLICT);
        }
        LocalDate today = now.toLocalDate();
        if (schedule.getStartTime().toLocalDate().isAfter(today.plusDays(SCHEDULE_WINDOW_DAYS - 1L))) {
            throw new ServiceException("只能预约今天起 14 天内的课程", CONFLICT);
        }
        if (schedule.getMaxPersons() == null) {
            throw new ServiceException("排课最大人数缺失，不能预约", PARAM_ERROR);
        }
        int remainingPlaces = Math.max(schedule.getMaxPersons() - bookedPersons(schedule), 0);
        if (persons > remainingPlaces) {
            throw new ServiceException("剩余名额不足，不能预约", CONFLICT);
        }
    }

    /** 会员卡与人数校验：年卡固定 1 人，次数卡人数不得超过剩余次数 */
    private void validateCardLimit(MemberCardBookingSnapshot card, Integer persons) {
        MemberCardTypeEnum cardType = MemberCardTypeEnum.of(card.getCardType());
        if (cardType == null) {
            throw new ServiceException("卡类型取值非法，无法预约", PARAM_ERROR);
        }
        if (!cardType.isTimesCard()) {
            if (persons != 1) {
                throw new ServiceException("年卡预约人数只能为 1", CONFLICT);
            }
            return;
        }
        int remainingTimes = card.getRemainingTimes() == null ? 0 : card.getRemainingTimes();
        if (persons > remainingTimes) {
            throw new ServiceException("会员卡剩余次数不足", CONFLICT);
        }
    }

    /** 次数卡扣次并追加扣次流水；年卡不扣次 */
    private void deductTimesIfNeeded(ReservationDO reservation, MemberCardBookingSnapshot card) {
        if (!MemberCardTypeEnum.TIMES.getCode().equals(card.getCardType())) {
            return;
        }
        int beforeTimes = card.getRemainingTimes() == null ? 0 : card.getRemainingTimes();
        if (!memberCardService.deductTimes(card.getId(), reservation.getPersons())) {
            throw new ServiceException("会员卡剩余次数不足", CONFLICT);
        }
        memberCardTransactionService.recordDeduct(reservation.getId(), card.getId(), reservation.getMemberId(),
                reservation.getPersons(), beforeTimes, beforeTimes - reservation.getPersons());
    }

    /** 取消预约返次：按原扣次流水记录的次数返还原卡，年卡没有扣次流水则不返次 */
    private void refundTimesIfNeeded(ReservationDO reservation) {
        Integer deductedTimes = memberCardTransactionService.deductedTimesOf(reservation.getId());
        if (deductedTimes == null || deductedTimes <= 0) {
            return;
        }
        MemberCardBookingSnapshot card = memberCardService.lockCard(reservation.getMemberCardId(),
                reservation.getMemberId());
        int beforeTimes = card.getRemainingTimes() == null ? 0 : card.getRemainingTimes();
        if (!memberCardService.refundTimes(card.getId(), deductedTimes)) {
            throw new ServiceException("返次失败，请稍后重试", CONFLICT);
        }
        memberCardTransactionService.recordRefund(reservation.getId(), card.getId(), reservation.getMemberId(),
                reservation.getPersons(), deductedTimes, beforeTimes, beforeTimes + deductedTimes);
    }

    /** 幂等参数一致性：同一个 requestId 只能对应同一组排课、会员卡与人数 */
    private void requireSameRequest(ReservationDO reservation, Long scheduleId, Long memberCardId, Integer persons) {
        if (!scheduleId.equals(reservation.getScheduleId()) || !memberCardId.equals(reservation.getMemberCardId())
                || !persons.equals(reservation.getPersons())) {
            throw new ServiceException("请求幂等标识已被其他预约使用", CONFLICT);
        }
    }

    private ReservationDO requireOwnedReservation(Long reservationId, Long memberId) {
        ReservationDO reservation = reservationDao.selectById(reservationId);
        if (reservation == null || !memberId.equals(reservation.getMemberId())) {
            throw notFoundReservation();
        }
        return reservation;
    }

    private ReservationDO requireOwnedReservationForUpdate(Long reservationId, Long memberId) {
        ReservationDO reservation = reservationDao.selectByIdForUpdate(reservationId);
        if (reservation == null || !memberId.equals(reservation.getMemberId())) {
            throw notFoundReservation();
        }
        return reservation;
    }

    private ServiceException notFoundReservation() {
        return new ServiceException("预约不存在或已被删除", NOT_FOUND);
    }

    private String requireRequestId(String requestId) {
        String value = requestId == null ? null : requestId.trim();
        if (value == null || value.isEmpty()) {
            throw new ServiceException("请求幂等标识不能为空", PARAM_ERROR);
        }
        try {
            UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            throw new ServiceException("请求幂等标识格式非法，应为 UUID", PARAM_ERROR);
        }
        return value;
    }

    private Long requirePositiveId(Long id, String fieldName) {
        if (id == null || id <= 0) {
            throw new ServiceException(fieldName + "格式非法", PARAM_ERROR);
        }
        return id;
    }

    private Integer requirePersons(Integer persons) {
        if (persons == null || persons <= 0) {
            throw new ServiceException("预约人数必须大于 0", PARAM_ERROR);
        }
        return persons;
    }

    private int bookedPersons(ScheduleBookingSnapshot schedule) {
        return schedule.getBookedPersons() == null ? 0 : schedule.getBookedPersons();
    }

    private Set<Long> distinct(List<ReservationDO> reservations, Function<ReservationDO, Long> extractor) {
        Set<Long> ids = new HashSet<Long>();
        for (ReservationDO reservation : reservations) {
            Long id = extractor.apply(reservation);
            if (id != null) {
                ids.add(id);
            }
        }
        return ids;
    }

    private Map<Long, ScheduleBookingSnapshot> indexSchedules(Collection<Long> scheduleIds) {
        Map<Long, ScheduleBookingSnapshot> schedules = new HashMap<Long, ScheduleBookingSnapshot>();
        for (ScheduleBookingSnapshot snapshot : scheduleBookingService.snapshots(scheduleIds)) {
            schedules.put(snapshot.getId(), snapshot);
        }
        return schedules;
    }

    private Collection<Long> scheduleCourseIds(Collection<ScheduleBookingSnapshot> schedules) {
        Set<Long> ids = new HashSet<Long>();
        for (ScheduleBookingSnapshot snapshot : schedules) {
            if (snapshot.getCourseId() != null) {
                ids.add(snapshot.getCourseId());
            }
        }
        return ids;
    }

    private Collection<Long> scheduleCoachIds(Collection<ScheduleBookingSnapshot> schedules) {
        Set<Long> ids = new HashSet<Long>();
        for (ScheduleBookingSnapshot snapshot : schedules) {
            if (snapshot.getCoachId() != null) {
                ids.add(snapshot.getCoachId());
            }
        }
        return ids;
    }

    private Collection<Long> scheduleStoreIds(Collection<ScheduleBookingSnapshot> schedules) {
        Set<Long> ids = new HashSet<Long>();
        for (ScheduleBookingSnapshot snapshot : schedules) {
            if (snapshot.getStoreId() != null) {
                ids.add(snapshot.getStoreId());
            }
        }
        return ids;
    }

    private Map<Long, CourseSummaryVO> indexCourses(Collection<Long> courseIds) {
        Map<Long, CourseSummaryVO> courses = new HashMap<Long, CourseSummaryVO>();
        if (courseIds.isEmpty()) {
            return courses;
        }
        for (CourseSummaryVO course : courseService.summaries(courseIds)) {
            courses.put(course.getId(), course);
        }
        return courses;
    }

    private Map<Long, String> indexCoachNames(Collection<Long> coachIds) {
        Map<Long, String> names = new HashMap<Long, String>();
        if (coachIds.isEmpty()) {
            return names;
        }
        for (CoachSummaryVO coach : coachService.summaries(coachIds)) {
            names.put(coach.getId(), coach.getName());
        }
        return names;
    }

    private Map<Long, String> indexStoreAddresses(Collection<Long> storeIds) {
        Map<Long, String> addresses = new HashMap<Long, String>();
        if (storeIds.isEmpty()) {
            return addresses;
        }
        for (StoreSummaryVO store : storeService.summaries(storeIds)) {
            addresses.put(store.getId(), store.getAddress());
        }
        return addresses;
    }

    private Collection<Long> scheduleClassroomIds(Collection<ScheduleBookingSnapshot> schedules) {
        Set<Long> ids = new HashSet<Long>();
        for (ScheduleBookingSnapshot snapshot : schedules) {
            if (snapshot.getClassroomId() != null) {
                ids.add(snapshot.getClassroomId());
            }
        }
        return ids;
    }

    private Map<Long, String> indexClassroomNames(Collection<Long> classroomIds) {
        Map<Long, String> names = new HashMap<Long, String>();
        if (classroomIds.isEmpty()) {
            return names;
        }
        for (ClassroomSummaryVO classroom : classroomService.summaries(classroomIds)) {
            names.put(classroom.getId(), classroom.getName());
        }
        return names;
    }

    private Map<Long, StoreSummaryVO> indexStoreSummaries(Collection<Long> storeIds) {
        Map<Long, StoreSummaryVO> stores = new HashMap<Long, StoreSummaryVO>();
        if (storeIds.isEmpty()) {
            return stores;
        }
        for (StoreSummaryVO store : storeService.summaries(storeIds)) {
            stores.put(store.getId(), store);
        }
        return stores;
    }

    private Map<Long, MemberSummaryVO> indexMemberSummaries(Collection<Long> memberIds) {
        Map<Long, MemberSummaryVO> members = new HashMap<Long, MemberSummaryVO>();
        if (memberIds.isEmpty()) {
            return members;
        }
        for (MemberSummaryVO member : memberService.summaries(memberIds)) {
            members.put(member.getId(), member);
        }
        return members;
    }

    private Map<Long, MemberCardSummaryVO> indexCardSummaries(Collection<Long> cardIds) {
        Map<Long, MemberCardSummaryVO> cards = new HashMap<Long, MemberCardSummaryVO>();
        if (cardIds.isEmpty()) {
            return cards;
        }
        for (MemberCardSummaryVO card : memberCardService.summaries(cardIds)) {
            cards.put(card.getId(), card);
        }
        return cards;
    }

    private Collection<Long> reservationIds(List<ReservationDO> reservations) {
        Set<Long> ids = new HashSet<Long>();
        for (ReservationDO reservation : reservations) {
            if (reservation.getId() != null) {
                ids.add(reservation.getId());
            }
        }
        return ids;
    }

    private Collection<Long> reservationMemberIds(List<ReservationDO> reservations) {
        Set<Long> ids = new HashSet<Long>();
        for (ReservationDO reservation : reservations) {
            if (reservation.getMemberId() != null) {
                ids.add(reservation.getMemberId());
            }
        }
        return ids;
    }

    private Collection<Long> reservationCardIds(List<ReservationDO> reservations) {
        Set<Long> ids = new HashSet<Long>();
        for (ReservationDO reservation : reservations) {
            if (reservation.getMemberCardId() != null) {
                ids.add(reservation.getMemberCardId());
            }
        }
        return ids;
    }
}
