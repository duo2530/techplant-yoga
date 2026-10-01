package com.techplant.yoga.schedule.service.impl;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.ruoyi.common.exception.ServiceException;
import com.techplant.yoga.classroom.service.ClassroomService;
import com.techplant.yoga.classroom.vo.ClassroomSummaryVO;
import com.techplant.yoga.coach.service.CoachService;
import com.techplant.yoga.coach.vo.CoachSummaryVO;
import com.techplant.yoga.common.response.PageResult;
import com.techplant.yoga.common.util.CurrentUserUtils;
import com.techplant.yoga.course.enums.CourseTypeEnum;
import com.techplant.yoga.course.service.CourseService;
import com.techplant.yoga.course.vo.CourseSummaryVO;
import com.techplant.yoga.schedule.convert.ScheduleConverter;
import com.techplant.yoga.schedule.dao.ScheduleDao;
import com.techplant.yoga.schedule.domain.ScheduleDO;
import com.techplant.yoga.schedule.dto.ScheduleCreateDTO;
import com.techplant.yoga.schedule.dto.ScheduleUpdateDTO;
import com.techplant.yoga.schedule.enums.ScheduleStatusEnum;
import com.techplant.yoga.schedule.query.PublicScheduleQuery;
import com.techplant.yoga.schedule.query.ScheduleQuery;
import com.techplant.yoga.schedule.service.ScheduleService;
import com.techplant.yoga.schedule.vo.PublicScheduleDetailVO;
import com.techplant.yoga.schedule.vo.ScheduleCardVO;
import com.techplant.yoga.schedule.vo.ScheduleDetailVO;
import com.techplant.yoga.schedule.vo.ScheduleListItemVO;
import com.techplant.yoga.store.service.StoreService;
import com.techplant.yoga.store.vo.StoreSummaryVO;

/**
 * 排课业务实现（排课管理详细设计 §3.2、§3.4、§4）。
 *
 * <p>本类同时承担三类职责：管理端 6 个接口、四个主数据的删除门禁统计、用户端 2 个免登录只读查询
 * （全项目统一不另立 QueryService）。</p>
 *
 * <p><b>三条最容易写错的口径：</b></p>
 * <ol>
 *   <li><b>「已结束」只在 service 层判定一次</b>：返回 controller 之前统一用
 *       {@link ScheduleStatusEnum} 推导 {@code finished} / {@code statusText} / {@code bookable}；
 *       controller 不判断、前端不算。唯一例外是删除门禁的 count（写在 SQL 里，见 {@code ScheduleDaoImpl}）。</li>
 *   <li><b>课种快照只在两处写入</b>：新增时从课程复制、更换课程时刷新；其它任何路径都不动快照。</li>
 *   <li><b>两套可见口径不合并</b>：用户端 {@code status <> 1}；删除门禁
 *       {@code end_time > NOW() AND status IN (1,2)}。</li>
 * </ol>
 *
 * <p><b>跨模块注入一律 {@code @Lazy}</b>：一期四个模块与本模块构成构造器注入环
 * （门店／教室／教练／课程都要调本模块做删除门禁统计），由 Spring 延迟代理打破。</p>
 */
@Service
public class ScheduleServiceImpl implements ScheduleService
{
    /** 资源不存在 */
    private static final int NOT_FOUND = 404;

    /** 状态冲突、业务范围校验不通过、删除门禁未通过 */
    private static final int CONFLICT = 409;

    /** 参数校验失败／系统异常 */
    private static final int PARAM_ERROR = 500;

    /** 排课窗口：今天起 14 天内（含今天）（BR-排课-009、BR-排课-017） */
    private static final int SCHEDULE_WINDOW_DAYS = 14;

    /** 用户端日期参数格式（严格 yyyy-MM-dd） */
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE;

    private final ScheduleDao scheduleDao;

    private final StoreService storeService;

    private final ClassroomService classroomService;

    private final CoachService coachService;

    private final CourseService courseService;

    public ScheduleServiceImpl(ScheduleDao scheduleDao,
            @Lazy StoreService storeService,
            @Lazy ClassroomService classroomService,
            @Lazy CoachService coachService,
            @Lazy CourseService courseService)
    {
        this.scheduleDao = scheduleDao;
        this.storeService = storeService;
        this.classroomService = classroomService;
        this.coachService = coachService;
        this.courseService = courseService;
    }

    // ------------------------------------------------------------------
    // 管理端：6 个接口
    // ------------------------------------------------------------------

    /**
     * 查询排课列表（§2.2.1）：日期区间筛 {@code schedule_date}，名称按 ID 去重后批量补齐
     */
    @Override
    public PageResult<ScheduleListItemVO> page(ScheduleQuery query)
    {
        IPage<ScheduleDO> page = scheduleDao.selectPage(query);
        List<ScheduleDO> records = page.getRecords();
        // 同一个请求只用同一个 now，保证同一页数据的派生状态口径一致
        LocalDateTime now = LocalDateTime.now();

        Map<Long, String> storeNames = storeNameMap(distinctIds(records, ScheduleDO::getStoreId));
        Map<Long, String> courseNames = courseNameMap(distinctIds(records, ScheduleDO::getCourseId));
        Map<Long, String> coachNames = coachNameMap(distinctIds(records, ScheduleDO::getCoachId));
        Map<Long, String> classroomNames = classroomNameMap(distinctIds(records, ScheduleDO::getClassroomId));

        List<ScheduleListItemVO> list = new ArrayList<ScheduleListItemVO>(records.size());
        for (ScheduleDO schedule : records)
        {
            ScheduleListItemVO vo = ScheduleConverter.toListItemVO(schedule, now);
            applyAdminNames(vo, schedule, storeNames, courseNames, coachNames, classroomNames);
            list.add(vo);
        }
        return PageResult.of(page.getTotal(), page.getCurrent(), page.getSize(), list);
    }

    /**
     * 查询排课详情（§2.2.2）：不存在或已被删除 → 404
     */
    @Override
    public ScheduleDetailVO getById(Long scheduleId)
    {
        ScheduleDO schedule = requireSchedule(scheduleId);
        LocalDateTime now = LocalDateTime.now();
        ScheduleDetailVO vo = ScheduleConverter.toDetailVO(schedule, now);
        applyAdminNames(vo, schedule,
                storeNameMap(Collections.singletonList(schedule.getStoreId())),
                courseNameMap(Collections.singletonList(schedule.getCourseId())),
                coachNameMap(Collections.singletonList(schedule.getCoachId())),
                classroomNameMap(Collections.singletonList(schedule.getClassroomId())));
        return vo;
    }

    /**
     * 新增排课（§2.2.3、§4.3）：校验 1~8 条与 INSERT 在**同一个事务**内
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void create(ScheduleCreateDTO dto)
    {
        // ① 四个对象存在（门店／课程／教练）＋ ② 教室存在且归属门店一致
        CourseSummaryVO course = validateRelations(dto.getStoreId(), dto.getCourseId(), dto.getCoachId(),
                dto.getClassroomId());
        // ③ 开始早于结束且同一自然日
        validateTimeRange(dto.getStartTime(), dto.getEndTime());
        // ④ 最大人数 > 0 ／ ⑤ 1 <= 最低开课人数 <= 最大人数
        validatePersons(dto.getMaxPersons(), dto.getMinPersons());
        // ⑥ 上课日期落在今天起 14 天内（含今天）
        LocalDate scheduleDate = dto.getStartTime().toLocalDate();
        validateScheduleWindow(scheduleDate);
        // ⑦ 同门店＋同课程＋完全相同的起止时间不重复（排除已取消）
        validateSameSlot(dto.getStoreId(), dto.getCourseId(), dto.getStartTime(), dto.getEndTime(), null);
        // ⑧ 同教练时间区间不相交（排除已取消）
        validateCoachOverlap(dto.getCoachId(), dto.getStartTime(), dto.getEndTime(), null);

        // 课种快照从课程复制、上课日期由开始时间推导、状态固定 1、已预约人数固定 0
        ScheduleDO schedule = ScheduleConverter.toDO(dto, course.getCourseType(), scheduleDate);
        if (scheduleDao.insert(schedule) != 1)
        {
            throw new ServiceException("新增排课失败", PARAM_ERROR);
        }
    }

    /**
     * 修改排课（§2.2.4、§4.4）：仅「待上架」可改；校验 1~8 条（⑦⑧ 排除自身）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public ScheduleDetailVO update(Long scheduleId, ScheduleUpdateDTO dto)
    {
        ScheduleDO existing = requireSchedule(scheduleId);
        // 编辑门禁：已上架须先下架；已取消与已结束是终态
        if (!isPending(existing.getStatus()))
        {
            throw new ServiceException("已上架／已取消／已结束的排课不可修改，请先下架", CONFLICT);
        }

        CourseSummaryVO course = validateRelations(dto.getStoreId(), dto.getCourseId(), dto.getCoachId(),
                dto.getClassroomId());
        validateTimeRange(dto.getStartTime(), dto.getEndTime());
        validatePersons(dto.getMaxPersons(), dto.getMinPersons());
        LocalDate scheduleDate = dto.getStartTime().toLocalDate();
        validateScheduleWindow(scheduleDate);
        validateSameSlot(dto.getStoreId(), dto.getCourseId(), dto.getStartTime(), dto.getEndTime(), scheduleId);
        validateCoachOverlap(dto.getCoachId(), dto.getStartTime(), dto.getEndTime(), scheduleId);

        // 课种快照：只有「更换了课程」才刷新（快照的唯一刷新点）；否则保持原快照不动
        Integer snapshot = Objects.equals(dto.getCourseId(), existing.getCourseId())
                ? existing.getCourseType()
                : course.getCourseType();
        ScheduleConverter.applyUpdate(existing, dto, snapshot, scheduleDate);
        existing.setUpdateBy(CurrentUserUtils.getUserIdOrNull());
        // 显式指定更新列，不含 status 与 booked_persons
        scheduleDao.updateById(existing);
        return getById(scheduleId);
    }

    /**
     * 状态流转（§2.2.5、§4.5）：唯一入口，controller 里不写 if-else
     *
     * <p>合法流转只有 {@code 1→2} 上架、{@code 2→1} 下架、{@code 2→3} 取消；
     * 目标 = 当前幂等成功；已取消与「已上架且已结束」是终态，一律 409。</p>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void changeStatus(Long scheduleId, Integer targetStatus)
    {
        if (!ScheduleStatusEnum.isValid(targetStatus))
        {
            throw new ServiceException("排课状态取值为 1~3", PARAM_ERROR);
        }

        ScheduleDO schedule = requireSchedule(scheduleId);
        Integer current = schedule.getStatus();
        LocalDateTime now = LocalDateTime.now();

        // 终态 1：已取消不可再变更状态
        if (ScheduleStatusEnum.of(current) == ScheduleStatusEnum.CANCELLED)
        {
            throw new ServiceException("已取消的排课不可再变更状态", CONFLICT);
        }
        // 终态 2：已上架且已结束（「已结束」不落库，这里用 service 层推导）
        if (ScheduleStatusEnum.isFinished(current, schedule.getEndTime(), now))
        {
            throw new ServiceException("已结束的排课不可再变更状态", CONFLICT);
        }
        // 幂等：目标 = 当前，按成功处理（BR-全局-008）
        if (targetStatus.equals(current))
        {
            return;
        }
        // 非法流转（如 1→3 待上架直接取消）
        if (!isAllowedTransition(current, targetStatus))
        {
            throw new ServiceException("当前状态不允许该操作", CONFLICT);
        }

        scheduleDao.updateStatus(scheduleId, targetStatus, CurrentUserUtils.getUserIdOrNull());
    }

    /**
     * 删除排课（§2.2.6、§4.6）：物理删除，**只允许删 {@code status = 1}**；已取消与已结束是终态留痕
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long scheduleId)
    {
        ScheduleDO schedule = requireSchedule(scheduleId);
        Integer status = schedule.getStatus();
        LocalDateTime now = LocalDateTime.now();

        if (ScheduleStatusEnum.of(status) == ScheduleStatusEnum.PENDING)
        {
            // 物理删除：本表没有 deleted 列、没有 @TableLogic
            scheduleDao.deleteById(scheduleId);
            return;
        }
        if (ScheduleStatusEnum.isFinished(status, schedule.getEndTime(), now))
        {
            throw new ServiceException("已结束的排课不可删除", CONFLICT);
        }
        if (ScheduleStatusEnum.of(status) == ScheduleStatusEnum.PUBLISHED)
        {
            throw new ServiceException("已上架的排课须先下架才能删除", CONFLICT);
        }
        if (ScheduleStatusEnum.of(status) == ScheduleStatusEnum.CANCELLED)
        {
            throw new ServiceException("已取消的排课不可删除", CONFLICT);
        }
        // 防御：状态码异常（理论不可达）
        throw new ServiceException("当前状态不允许该操作", CONFLICT);
    }

    // ------------------------------------------------------------------
    // 跨模块：四个主数据的删除门禁统计（口径完全一致，且都在 SQL 里）
    // ------------------------------------------------------------------

    @Override
    public long countActiveByStoreId(Long storeId)
    {
        return scheduleDao.countActiveByStoreId(storeId);
    }

    @Override
    public long countActiveByClassroomId(Long classroomId)
    {
        return scheduleDao.countActiveByClassroomId(classroomId);
    }

    @Override
    public long countActiveByCoachId(Long coachId)
    {
        return scheduleDao.countActiveByCoachId(coachId);
    }

    @Override
    public long countActiveByCourseId(Long courseId)
    {
        return scheduleDao.countActiveByCourseId(courseId);
    }

    // ------------------------------------------------------------------
    // 用户端：2 个免登录只读查询
    // ------------------------------------------------------------------

    /**
     * 用户端排课列表（《用户端接口详细设计》§2.2）：固定过滤 {@code status <> 1}、
     * 门店、课种<b>快照</b>、上课日期<b>派生列</b>
     */
    @Override
    public PageResult<ScheduleCardVO> publicPage(PublicScheduleQuery query)
    {
        // 课种取值校验（码 → 名与取值校验都收在 CourseTypeEnum 一处）
        CourseTypeEnum.validate(query.getCourseType());
        // date 格式非法 / 超出今天起 14 天 → 拒绝
        LocalDate date = parseDate(query.getDate());
        validatePublicDateWindow(date);
        // 门店不存在或已删除 → 404「门店不存在或已被删除」
        storeService.getById(query.getStoreId());

        IPage<ScheduleDO> page = scheduleDao.selectPublicPage(query, date);
        List<ScheduleDO> records = page.getRecords();
        LocalDateTime now = LocalDateTime.now();

        Set<Long> storeIds = distinctIds(records, ScheduleDO::getStoreId);
        storeIds.add(query.getStoreId());
        Map<Long, CourseSummaryVO> courses = courseSummaryMap(distinctIds(records, ScheduleDO::getCourseId));
        Map<Long, String> coachNames = coachNameMap(distinctIds(records, ScheduleDO::getCoachId));
        Map<Long, String> classroomNames = classroomNameMap(distinctIds(records, ScheduleDO::getClassroomId));
        Map<Long, String> storeNames = storeNameMap(storeIds);

        List<ScheduleCardVO> list = new ArrayList<ScheduleCardVO>(records.size());
        for (ScheduleDO schedule : records)
        {
            ScheduleCardVO vo = ScheduleConverter.toCardVO(schedule, now);
            CourseSummaryVO course = courses.get(schedule.getCourseId());
            if (course != null)
            {
                // 名称／封面／难度实时取课程（不做快照）
                vo.setCourseName(text(course.getName()));
                vo.setCourseCoverUrl(text(course.getCoverUrl()));
                vo.setDifficulty(course.getDifficulty());
            }
            vo.setCoachName(nameOf(coachNames, schedule.getCoachId()));
            vo.setClassroomName(nameOf(classroomNames, schedule.getClassroomId()));
            vo.setStoreName(nameOf(storeNames, schedule.getStoreId()));
            list.add(vo);
        }
        return PageResult.of(page.getTotal(), page.getCurrent(), page.getSize(), list);
    }

    /**
     * 用户端排课详情（《用户端接口详细设计》§2.3）：未删除且 {@code status <> 1} 才可见，
     * 否则**一律按不存在返回 404**（不区分「不存在」与「不可见」）
     */
    @Override
    public PublicScheduleDetailVO publicDetail(Long scheduleId)
    {
        ScheduleDO schedule = scheduleDao.selectVisibleById(scheduleId);
        if (schedule == null)
        {
            // 与「不存在」同一个响应，避免暴露某节课处于待上架
            throw notFoundSchedule();
        }

        LocalDateTime now = LocalDateTime.now();
        PublicScheduleDetailVO vo = ScheduleConverter.toPublicDetailVO(schedule, now);

        // 封面／介绍／难度实时取课程；课程已删除时全部按空处理
        CourseSummaryVO course = courseService.getSummary(schedule.getCourseId());
        if (course != null)
        {
            vo.setCourseName(text(course.getName()));
            vo.setCourseCoverUrl(text(course.getCoverUrl()));
            vo.setCourseIntro(text(course.getIntro()));
            vo.setDifficulty(course.getDifficulty());
        }

        // 教练头像与简介只在详情页给；教练已删除时名称空字符串
        if (schedule.getCoachId() != null)
        {
            List<CoachSummaryVO> coaches = coachService.summaries(Collections.singletonList(schedule.getCoachId()));
            if (coaches != null && !coaches.isEmpty() && coaches.get(0) != null)
            {
                CoachSummaryVO coach = coaches.get(0);
                vo.setCoachName(text(coach.getName()));
                vo.setCoachAvatarUrl(text(coach.getAvatarUrl()));
                vo.setCoachIntro(text(coach.getIntro()));
            }
        }

        vo.setStoreName(nameOf(storeNameMap(Collections.singletonList(schedule.getStoreId())),
                schedule.getStoreId()));
        vo.setClassroomName(nameOf(classroomNameMap(Collections.singletonList(schedule.getClassroomId())),
                schedule.getClassroomId()));
        // 一期没有预约：固定空数组，不是 null
        vo.setBookers(new ArrayList<String>());
        return vo;
    }

    // ------------------------------------------------------------------
    // 内部：业务校验
    // ------------------------------------------------------------------

    /**
     * 校验 ① 四个对象存在 ＋ ② 教室归属门店一致
     *
     * @return 课程摘要（调用方要用它的 {@code courseType} 写课种快照）
     */
    private CourseSummaryVO validateRelations(Long storeId, Long courseId, Long coachId, Long classroomId)
    {
        // 门店不存在 → 404「门店不存在或已被删除」（由 StoreService 抛）
        storeService.getById(storeId);

        // 课程不存在（含物理删除）→ getSummary 返回 null，这里补 404
        CourseSummaryVO course = courseService.getSummary(courseId);
        if (course == null)
        {
            throw new ServiceException("课程不存在或已被删除", NOT_FOUND);
        }

        // 教练不存在或已被删除 → 404（由 CoachService 抛）
        coachService.getSummary(coachId);

        // 教室不存在 → 404「教室不存在或已被删除」；归属不符 → 409「所选教室不属于该门店」
        classroomService.validateExistsAndBelongsToStore(classroomId, storeId);
        return course;
    }

    /** ③ 开始时间必须早于结束时间，且两者在同一自然日 */
    private void validateTimeRange(LocalDateTime startTime, LocalDateTime endTime)
    {
        if (startTime == null || endTime == null || !startTime.isBefore(endTime)
                || !startTime.toLocalDate().equals(endTime.toLocalDate()))
        {
            throw new ServiceException("开始时间必须早于结束时间且不能跨天", CONFLICT);
        }
    }

    /** ④ 最大人数 > 0；⑤ 1 <= 最低开课人数 <= 最大人数 */
    private void validatePersons(Integer maxPersons, Integer minPersons)
    {
        if (maxPersons == null || maxPersons <= 0)
        {
            throw new ServiceException("最大人数必须大于 0", CONFLICT);
        }
        if (minPersons == null || minPersons < 1 || minPersons > maxPersons)
        {
            throw new ServiceException("最低开课人数必须在 1 与最大人数之间", CONFLICT);
        }
    }

    /** ⑥ 上课日期必须落在今天起 14 天内（含今天） */
    private void validateScheduleWindow(LocalDate scheduleDate)
    {
        LocalDate today = LocalDate.now();
        LocalDate lastDay = today.plusDays(SCHEDULE_WINDOW_DAYS - 1L);
        if (scheduleDate == null || scheduleDate.isBefore(today) || scheduleDate.isAfter(lastDay))
        {
            throw new ServiceException("只能排今天起 14 天内的课", CONFLICT);
        }
    }

    /** ⑦ 同一门店＋同一课程＋完全相同的起止时间（status <> 3）不重复 */
    private void validateSameSlot(Long storeId, Long courseId, LocalDateTime startTime, LocalDateTime endTime,
            Long excludeId)
    {
        if (scheduleDao.existsSameSlot(storeId, courseId, startTime, endTime, excludeId))
        {
            throw new ServiceException("该门店该课程在此时间已有排课", CONFLICT);
        }
    }

    /** ⑧ 同一教练时间区间不相交（status <> 3） */
    private void validateCoachOverlap(Long coachId, LocalDateTime startTime, LocalDateTime endTime, Long excludeId)
    {
        if (scheduleDao.existsCoachOverlap(coachId, startTime, endTime, excludeId))
        {
            throw new ServiceException("该教练在此时间段已有排课", CONFLICT);
        }
    }

    /** 用户端查询窗口：date 必须落在今天起 14 天内（含今天） */
    private void validatePublicDateWindow(LocalDate date)
    {
        LocalDate today = LocalDate.now();
        LocalDate lastDay = today.plusDays(SCHEDULE_WINDOW_DAYS - 1L);
        if (date.isBefore(today) || date.isAfter(lastDay))
        {
            throw new ServiceException("只能查今天起 14 天内的课", PARAM_ERROR);
        }
    }

    /** 用户端 date 解析：不是 yyyy-MM-dd → 500（提示格式非法） */
    private LocalDate parseDate(String date)
    {
        if (date == null || date.trim().isEmpty())
        {
            throw new ServiceException("日期不能为空", PARAM_ERROR);
        }
        try
        {
            return LocalDate.parse(date.trim(), DATE_FORMAT);
        }
        catch (DateTimeParseException e)
        {
            throw new ServiceException("日期格式非法，应为 yyyy-MM-dd", PARAM_ERROR);
        }
    }

    /** 状态流转白名单：1→2 上架、2→1 下架、2→3 取消 */
    private boolean isAllowedTransition(Integer current, Integer target)
    {
        ScheduleStatusEnum from = ScheduleStatusEnum.of(current);
        ScheduleStatusEnum to = ScheduleStatusEnum.of(target);
        if (from == null || to == null)
        {
            return false;
        }
        return (from == ScheduleStatusEnum.PENDING && to == ScheduleStatusEnum.PUBLISHED)
                || (from == ScheduleStatusEnum.PUBLISHED && to == ScheduleStatusEnum.PENDING)
                || (from == ScheduleStatusEnum.PUBLISHED && to == ScheduleStatusEnum.CANCELLED);
    }

    private boolean isPending(Integer status)
    {
        return ScheduleStatusEnum.of(status) == ScheduleStatusEnum.PENDING;
    }

    private ScheduleDO requireSchedule(Long scheduleId)
    {
        ScheduleDO schedule = scheduleDao.selectById(scheduleId);
        if (schedule == null)
        {
            throw notFoundSchedule();
        }
        return schedule;
    }

    private ServiceException notFoundSchedule()
    {
        return new ServiceException("排课不存在或已被删除", NOT_FOUND);
    }

    // ------------------------------------------------------------------
    // 内部：跨模块名称批量补齐（按 ID 去重，避免 N+1；查不到一律空字符串）
    // ------------------------------------------------------------------

    private void applyAdminNames(ScheduleListItemVO vo, ScheduleDO schedule, Map<Long, String> storeNames,
            Map<Long, String> courseNames, Map<Long, String> coachNames, Map<Long, String> classroomNames)
    {
        vo.setStoreName(nameOf(storeNames, schedule.getStoreId()));
        vo.setCourseName(nameOf(courseNames, schedule.getCourseId()));
        vo.setCoachName(nameOf(coachNames, schedule.getCoachId()));
        vo.setClassroomName(nameOf(classroomNames, schedule.getClassroomId()));
    }

    private Map<Long, String> storeNameMap(Collection<Long> ids)
    {
        Map<Long, String> names = new HashMap<Long, String>();
        Collection<Long> effectiveIds = effectiveIds(ids);
        if (effectiveIds.isEmpty())
        {
            return names;
        }
        List<StoreSummaryVO> summaries = storeService.summaries(effectiveIds);
        if (summaries == null)
        {
            return names;
        }
        for (StoreSummaryVO item : summaries)
        {
            if (item != null && item.getId() != null)
            {
                names.put(item.getId(), text(item.getName()));
            }
        }
        return names;
    }

    private Map<Long, String> courseNameMap(Collection<Long> ids)
    {
        Map<Long, String> names = new HashMap<Long, String>();
        Map<Long, CourseSummaryVO> courses = courseSummaryMap(ids);
        for (Map.Entry<Long, CourseSummaryVO> entry : courses.entrySet())
        {
            names.put(entry.getKey(), text(entry.getValue().getName()));
        }
        return names;
    }

    private Map<Long, CourseSummaryVO> courseSummaryMap(Collection<Long> ids)
    {
        Map<Long, CourseSummaryVO> courses = new HashMap<Long, CourseSummaryVO>();
        Collection<Long> effectiveIds = effectiveIds(ids);
        if (effectiveIds.isEmpty())
        {
            return courses;
        }
        List<CourseSummaryVO> summaries = courseService.summaries(effectiveIds);
        if (summaries == null)
        {
            return courses;
        }
        for (CourseSummaryVO item : summaries)
        {
            if (item != null && item.getId() != null)
            {
                courses.put(item.getId(), item);
            }
        }
        return courses;
    }

    private Map<Long, String> coachNameMap(Collection<Long> ids)
    {
        Map<Long, String> names = new HashMap<Long, String>();
        Collection<Long> effectiveIds = effectiveIds(ids);
        if (effectiveIds.isEmpty())
        {
            return names;
        }
        List<CoachSummaryVO> summaries = coachService.summaries(effectiveIds);
        if (summaries == null)
        {
            return names;
        }
        for (CoachSummaryVO item : summaries)
        {
            if (item != null && item.getId() != null)
            {
                names.put(item.getId(), text(item.getName()));
            }
        }
        return names;
    }

    private Map<Long, String> classroomNameMap(Collection<Long> ids)
    {
        Map<Long, String> names = new HashMap<Long, String>();
        Collection<Long> effectiveIds = effectiveIds(ids);
        if (effectiveIds.isEmpty())
        {
            return names;
        }
        List<ClassroomSummaryVO> summaries = classroomService.summaries(effectiveIds);
        if (summaries == null)
        {
            return names;
        }
        for (ClassroomSummaryVO item : summaries)
        {
            if (item != null && item.getId() != null)
            {
                names.put(item.getId(), text(item.getName()));
            }
        }
        return names;
    }

    /** 按 ID 去重（批量补齐时先集合去重，避免同一对象查多次） */
    private Set<Long> distinctIds(List<ScheduleDO> records, Function<ScheduleDO, Long> extractor)
    {
        Set<Long> ids = new HashSet<Long>();
        for (ScheduleDO record : records)
        {
            Long id = extractor.apply(record);
            if (id != null)
            {
                ids.add(id);
            }
        }
        return ids;
    }

    /** 去掉 null 并把唯一元素集合（含 null 的集合）过滤掉，避免用 null 当查询参数 */
    private Collection<Long> effectiveIds(Collection<Long> ids)
    {
        if (ids == null || ids.isEmpty())
        {
            return Collections.emptyList();
        }
        Set<Long> effective = new HashSet<Long>();
        for (Long id : ids)
        {
            if (id != null)
            {
                effective.add(id);
            }
        }
        return effective;
    }

    /** 名称兜底：查不到（对象已被物理删除）返回空字符串，不是 null */
    private String nameOf(Map<Long, String> names, Long id)
    {
        if (id == null)
        {
            return "";
        }
        String name = names.get(id);
        return name == null ? "" : name;
    }

    private String text(String value)
    {
        return value == null ? "" : value;
    }
}
