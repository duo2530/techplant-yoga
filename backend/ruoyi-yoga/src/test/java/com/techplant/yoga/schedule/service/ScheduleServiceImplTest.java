package com.techplant.yoga.schedule.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ruoyi.common.exception.ServiceException;
import com.techplant.yoga.classroom.service.ClassroomService;
import com.techplant.yoga.classroom.vo.ClassroomSummaryVO;
import com.techplant.yoga.coach.service.CoachService;
import com.techplant.yoga.coach.vo.CoachSummaryVO;
import com.techplant.yoga.common.response.PageResult;
import com.techplant.yoga.course.service.CourseService;
import com.techplant.yoga.course.vo.CourseSummaryVO;
import com.techplant.yoga.schedule.dao.ScheduleDao;
import com.techplant.yoga.schedule.domain.ScheduleDO;
import com.techplant.yoga.schedule.dto.ScheduleUpdateDTO;
import com.techplant.yoga.schedule.query.PublicScheduleQuery;
import com.techplant.yoga.schedule.query.ScheduleQuery;
import com.techplant.yoga.schedule.service.impl.ScheduleServiceImpl;
import com.techplant.yoga.schedule.vo.PublicScheduleDetailVO;
import com.techplant.yoga.schedule.vo.ScheduleCardVO;
import com.techplant.yoga.schedule.vo.ScheduleDetailVO;
import com.techplant.yoga.schedule.vo.ScheduleListItemVO;
import com.techplant.yoga.store.service.StoreService;
import com.techplant.yoga.store.vo.StoreDetailVO;
import com.techplant.yoga.store.vo.StoreSummaryVO;

/**
 * 排课业务实现的单元测试（排课管理详细设计 §2.2.3~§2.2.6、§3.4）。
 *
 * <p>依赖全部用模拟对象隔离，不连数据库。重点覆盖：</p>
 * <ul>
 *   <li>新增／修改的 <b>8 条校验与拒绝文案</b>（含校验顺序：前面的校验不过就不查后面的、不落库）；</li>
 *   <li><b>课种快照只在两处写入</b>：新增复制、更换课程刷新，未换课程即使课程改了课种也不动快照；</li>
 *   <li><b>schedule_date 由 start_time 推导</b>、修改开始时间时同步刷新；</li>
 *   <li><b>状态流转全部分支</b>（1→2、2→1、2→3、幂等、终态、非法流转、参数越界）；</li>
 *   <li><b>删除门禁四分支</b>（待上架可删、已上架须先下架、已结束、已取消）；</li>
 *   <li><b>四个 countActiveByXxx 转发到 DAO</b>（真实 SQL 口径在 ScheduleDaoImpl）；</li>
 *   <li>用户端两条接口：{@code status<>1} 的 404 口径、快照／实时字段、{@code bookers} 固定空数组。</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ScheduleServiceImpl 单元测试")
class ScheduleServiceImplTest
{
    private static final Long SCHEDULE_ID = 1856739201475239001L;

    private static final Long STORE_ID = 1856739201475235901L;

    private static final Long COURSE_ID = 1856739201475235801L;

    private static final Long OTHER_COURSE_ID = 1856739201475235802L;

    private static final Long COACH_ID = 1856739201475237001L;

    private static final Long CLASSROOM_ID = 1856739201475236001L;

    /** 课种：团课 */
    private static final int COURSE_TYPE_GROUP = 1;

    /** 课种：特色课（用作“课程事后改了课种”的场景） */
    private static final int COURSE_TYPE_FEATURE = 4;

    @Mock
    private ScheduleDao scheduleDao;

    @Mock
    private StoreService storeService;

    @Mock
    private ClassroomService classroomService;

    @Mock
    private CoachService coachService;

    @Mock
    private CourseService courseService;

    @InjectMocks
    private ScheduleServiceImpl scheduleService;

    // ------------------------------------------------------------------
    // 新增：8 条校验
    // ------------------------------------------------------------------

    @Test
    @DisplayName("新增：全部校验通过 → 写课种快照、派生上课日期、status=1、booked_persons=0")
    void create_shouldWriteSnapshotDerivedDateAndFixedDefaults()
    {
        stubRelationsOk(COURSE_TYPE_GROUP);
        when(scheduleDao.existsSameSlot(eq(STORE_ID), eq(COURSE_ID), any(), any(), eq(null))).thenReturn(false);
        when(scheduleDao.existsCoachOverlap(eq(COACH_ID), any(), any(), eq(null))).thenReturn(false);
        when(scheduleDao.insert(any(ScheduleDO.class))).thenReturn(1);

        LocalDate day = tomorrow();
        ScheduleUpdateDTO dto = dto(day, 19, 20, 20, 2);
        scheduleService.create(dto);

        ArgumentCaptor<ScheduleDO> captor = ArgumentCaptor.forClass(ScheduleDO.class);
        verify(scheduleDao).insert(captor.capture());
        ScheduleDO inserted = captor.getValue();
        assertEquals(COURSE_TYPE_GROUP, inserted.getCourseType(), "新增时课种快照必须从课程复制");
        assertEquals(day, inserted.getScheduleDate(), "schedule_date 必须由 start_time 推导");
        assertEquals(Integer.valueOf(1), inserted.getStatus(), "新增固定待上架");
        assertEquals(Integer.valueOf(0), inserted.getBookedPersons(), "新增固定已预约人数 0");
        assertEquals(COACH_ID, inserted.getCoachId());
        assertEquals(CLASSROOM_ID, inserted.getClassroomId());
        assertEquals(Integer.valueOf(20), inserted.getMaxPersons());
        assertEquals(Integer.valueOf(2), inserted.getMinPersons());
    }

    @Test
    @DisplayName("新增校验①：课程不存在 → 404「课程不存在或已被删除」，不落库")
    void create_whenCourseMissing_should404()
    {
        when(storeService.getById(STORE_ID)).thenReturn(new StoreDetailVO());
        when(courseService.getSummary(COURSE_ID)).thenReturn(null);

        ServiceException ex = assertThrows(ServiceException.class,
                () -> scheduleService.create(dto(tomorrow(), 19, 20, 20, 2)));
        assertEquals(404, ex.getCode());
        assertEquals("课程不存在或已被删除", ex.getMessage());
        verify(scheduleDao, never()).insert(any(ScheduleDO.class));
    }

    @Test
    @DisplayName("新增校验②：教室不属于该门店 → 409「所选教室不属于该门店」，不落库")
    void create_whenClassroomNotBelongToStore_should409()
    {
        when(storeService.getById(STORE_ID)).thenReturn(new StoreDetailVO());
        when(courseService.getSummary(COURSE_ID)).thenReturn(course(COURSE_TYPE_GROUP));
        when(coachService.getSummary(COACH_ID)).thenReturn(new CoachSummaryVO());
        doThrow(new ServiceException("所选教室不属于该门店", 409))
                .when(classroomService).validateExistsAndBelongsToStore(CLASSROOM_ID, STORE_ID);

        ServiceException ex = assertThrows(ServiceException.class,
                () -> scheduleService.create(dto(tomorrow(), 19, 20, 20, 2)));
        assertEquals(409, ex.getCode());
        assertEquals("所选教室不属于该门店", ex.getMessage());
        verify(scheduleDao, never()).insert(any(ScheduleDO.class));
    }

    @Test
    @DisplayName("新增校验③：跨天 → 409「开始时间必须早于结束时间且不能跨天」")
    void create_whenCrossDay_should409()
    {
        stubRelationsOk(COURSE_TYPE_GROUP);
        LocalDateTime start = tomorrow().atTime(23, 0);
        LocalDateTime end = tomorrow().plusDays(1).atTime(1, 0);

        ServiceException ex = assertThrows(ServiceException.class,
                () -> scheduleService.create(dtoOf(start, end, 20, 2)));
        assertEquals(409, ex.getCode());
        assertEquals("开始时间必须早于结束时间且不能跨天", ex.getMessage());
        verify(scheduleDao, never()).insert(any(ScheduleDO.class));
    }

    @Test
    @DisplayName("新增校验④：最大人数 0 → 409「最大人数必须大于 0」")
    void create_whenMaxPersonsNotPositive_should409()
    {
        stubRelationsOk(COURSE_TYPE_GROUP);

        ServiceException ex = assertThrows(ServiceException.class,
                () -> scheduleService.create(dto(tomorrow(), 19, 20, 0, 0)));
        assertEquals(409, ex.getCode());
        assertEquals("最大人数必须大于 0", ex.getMessage());
        verify(scheduleDao, never()).insert(any(ScheduleDO.class));
    }

    @Test
    @DisplayName("新增校验⑤：最低开课人数 0 或大于最大人数 → 409「最低开课人数必须在 1 与最大人数之间」")
    void create_whenMinPersonsOutOfRange_should409()
    {
        stubRelationsOk(COURSE_TYPE_GROUP);

        ServiceException zero = assertThrows(ServiceException.class,
                () -> scheduleService.create(dto(tomorrow(), 19, 20, 20, 0)));
        assertEquals(409, zero.getCode());
        assertEquals("最低开课人数必须在 1 与最大人数之间", zero.getMessage());

        ServiceException tooBig = assertThrows(ServiceException.class,
                () -> scheduleService.create(dto(tomorrow(), 19, 20, 5, 6)));
        assertEquals(409, tooBig.getCode());
        assertEquals("最低开课人数必须在 1 与最大人数之间", tooBig.getMessage());
        verify(scheduleDao, never()).insert(any(ScheduleDO.class));
    }

    @Test
    @DisplayName("新增校验⑥：超出今天起 14 天 → 409「只能排今天起 14 天内的课」")
    void create_whenOutOfWindow_should409()
    {
        stubRelationsOk(COURSE_TYPE_GROUP);

        ServiceException ex = assertThrows(ServiceException.class,
                () -> scheduleService.create(dto(LocalDate.now().plusDays(20), 19, 20, 20, 2)));
        assertEquals(409, ex.getCode());
        assertEquals("只能排今天起 14 天内的课", ex.getMessage());
        verify(scheduleDao, never()).insert(any(ScheduleDO.class));
    }

    @Test
    @DisplayName("新增校验⑦：同门店同课程完全相同的起止时间 → 409「该门店该课程在此时间已有排课」")
    void create_whenSameSlotExists_should409()
    {
        stubRelationsOk(COURSE_TYPE_GROUP);
        when(scheduleDao.existsSameSlot(eq(STORE_ID), eq(COURSE_ID), any(), any(), eq(null))).thenReturn(true);

        ServiceException ex = assertThrows(ServiceException.class,
                () -> scheduleService.create(dto(tomorrow(), 19, 20, 20, 2)));
        assertEquals(409, ex.getCode());
        assertEquals("该门店该课程在此时间已有排课", ex.getMessage());
        // ⑦ 不通过时不再查 ⑧，也不落库
        verify(scheduleDao, never()).existsCoachOverlap(any(), any(), any(), any());
        verify(scheduleDao, never()).insert(any(ScheduleDO.class));
    }

    @Test
    @DisplayName("新增校验⑧：教练时间相交 → 409「该教练在此时间段已有排课」")
    void create_whenCoachOverlap_should409()
    {
        stubRelationsOk(COURSE_TYPE_GROUP);
        when(scheduleDao.existsSameSlot(eq(STORE_ID), eq(COURSE_ID), any(), any(), eq(null))).thenReturn(false);
        when(scheduleDao.existsCoachOverlap(eq(COACH_ID), any(), any(), eq(null))).thenReturn(true);

        ServiceException ex = assertThrows(ServiceException.class,
                () -> scheduleService.create(dto(tomorrow(), 19, 20, 20, 2)));
        assertEquals(409, ex.getCode());
        assertEquals("该教练在此时间段已有排课", ex.getMessage());
        verify(scheduleDao, never()).insert(any(ScheduleDO.class));
    }

    // ------------------------------------------------------------------
    // 修改：编辑门禁 + 快照刷新 + 日期刷新
    // ------------------------------------------------------------------

    @Test
    @DisplayName("修改：非「待上架」→ 409「已上架／已取消／已结束的排课不可修改，请先下架」，不更新")
    void update_whenNotPending_should409()
    {
        when(scheduleDao.selectById(SCHEDULE_ID)).thenReturn(existing(2, LocalDateTime.now().plusHours(2)));

        ServiceException ex = assertThrows(ServiceException.class,
                () -> scheduleService.update(SCHEDULE_ID, dto(tomorrow(), 19, 20, 20, 2)));
        assertEquals(409, ex.getCode());
        assertEquals("已上架／已取消／已结束的排课不可修改，请先下架", ex.getMessage());
        verify(scheduleDao, never()).updateById(any(ScheduleDO.class));
    }

    @Test
    @DisplayName("修改：未更换课程 → 即使课程当前课种变了，也保持原快照不动")
    void update_whenCourseUnchanged_shouldKeepSnapshot()
    {
        ScheduleDO existing = existing(1, null);
        existing.setCourseType(COURSE_TYPE_FEATURE);
        when(scheduleDao.selectById(SCHEDULE_ID)).thenReturn(existing);
        // 课程侧事后把课种改成了团课，但排课没有换课程 → 快照不追溯
        stubRelationsOk(COURSE_TYPE_GROUP);
        when(scheduleDao.existsSameSlot(eq(STORE_ID), eq(COURSE_ID), any(), any(), eq(SCHEDULE_ID)))
                .thenReturn(false);
        when(scheduleDao.existsCoachOverlap(eq(COACH_ID), any(), any(), eq(SCHEDULE_ID))).thenReturn(false);
        when(scheduleDao.updateById(any(ScheduleDO.class))).thenReturn(1);

        LocalDate day = tomorrow();
        ScheduleUpdateDTO dto = dto(day, 19, 20, 20, 2);
        dto.setCourseId(COURSE_ID);
        scheduleService.update(SCHEDULE_ID, dto);

        ArgumentCaptor<ScheduleDO> captor = ArgumentCaptor.forClass(ScheduleDO.class);
        verify(scheduleDao).updateById(captor.capture());
        assertEquals(COURSE_TYPE_FEATURE, captor.getValue().getCourseType(), "未换课程时快照必须保持不变");
        assertEquals(day, captor.getValue().getScheduleDate(), "开始时间对应的上课日期要同步刷新");
    }

    @Test
    @DisplayName("修改：更换课程 → 刷新课种快照（快照的唯一刷新点）")
    void update_whenCourseChanged_shouldRefreshSnapshot()
    {
        ScheduleDO existing = existing(1, null);
        existing.setCourseId(OTHER_COURSE_ID);
        existing.setCourseType(COURSE_TYPE_FEATURE);
        when(scheduleDao.selectById(SCHEDULE_ID)).thenReturn(existing);
        stubRelationsOk(COURSE_TYPE_GROUP);
        when(scheduleDao.existsSameSlot(eq(STORE_ID), eq(COURSE_ID), any(), any(), eq(SCHEDULE_ID)))
                .thenReturn(false);
        when(scheduleDao.existsCoachOverlap(eq(COACH_ID), any(), any(), eq(SCHEDULE_ID))).thenReturn(false);
        when(scheduleDao.updateById(any(ScheduleDO.class))).thenReturn(1);

        scheduleService.update(SCHEDULE_ID, dto(tomorrow(), 19, 20, 20, 2));

        ArgumentCaptor<ScheduleDO> captor = ArgumentCaptor.forClass(ScheduleDO.class);
        verify(scheduleDao).updateById(captor.capture());
        assertEquals(COURSE_TYPE_GROUP, captor.getValue().getCourseType(), "更换课程时要刷新课种快照");
    }

    @Test
    @DisplayName("修改：开始时间变了 → schedule_date 同步刷新（不出现 start_time 拼日期）")
    void update_shouldRefreshScheduleDateFromStartTime()
    {
        ScheduleDO existing = existing(1, null);
        when(scheduleDao.selectById(SCHEDULE_ID)).thenReturn(existing);
        stubRelationsOk(COURSE_TYPE_GROUP);
        when(scheduleDao.existsSameSlot(eq(STORE_ID), eq(COURSE_ID), any(), any(), eq(SCHEDULE_ID)))
                .thenReturn(false);
        when(scheduleDao.existsCoachOverlap(eq(COACH_ID), any(), any(), eq(SCHEDULE_ID))).thenReturn(false);
        when(scheduleDao.updateById(any(ScheduleDO.class))).thenReturn(1);

        LocalDate newDay = LocalDate.now().plusDays(3);
        scheduleService.update(SCHEDULE_ID, dto(newDay, 10, 11, 20, 2));

        ArgumentCaptor<ScheduleDO> captor = ArgumentCaptor.forClass(ScheduleDO.class);
        verify(scheduleDao).updateById(captor.capture());
        assertEquals(newDay, captor.getValue().getScheduleDate());
        assertEquals(newDay.atTime(10, 0), captor.getValue().getStartTime());
    }

    // ------------------------------------------------------------------
    // 状态流转：全部分支
    // ------------------------------------------------------------------

    @Test
    @DisplayName("状态流转：1→2 上架、2→1 下架、2→3 取消 三条合法流转")
    void changeStatus_shouldAllowThreeLegalTransitions()
    {
        when(scheduleDao.selectById(SCHEDULE_ID))
                .thenReturn(existing(1, LocalDateTime.now().plusHours(2)))
                .thenReturn(existing(2, LocalDateTime.now().plusHours(2)))
                .thenReturn(existing(2, LocalDateTime.now().plusHours(2)));

        scheduleService.changeStatus(SCHEDULE_ID, 2);
        scheduleService.changeStatus(SCHEDULE_ID, 1);
        scheduleService.changeStatus(SCHEDULE_ID, 3);

        verify(scheduleDao).updateStatus(eq(SCHEDULE_ID), eq(2), any());
        verify(scheduleDao).updateStatus(eq(SCHEDULE_ID), eq(1), any());
        verify(scheduleDao).updateStatus(eq(SCHEDULE_ID), eq(3), any());
    }

    @Test
    @DisplayName("状态流转：目标 = 当前 → 幂等成功，不写库")
    void changeStatus_whenTargetEqualsCurrent_shouldBeIdempotent()
    {
        when(scheduleDao.selectById(SCHEDULE_ID)).thenReturn(existing(1, LocalDateTime.now().plusHours(2)));

        scheduleService.changeStatus(SCHEDULE_ID, 1);

        verify(scheduleDao, never()).updateStatus(any(), any(), any());
    }

    @Test
    @DisplayName("状态流转：当前已取消 → 409「已取消的排课不可再变更状态」（即使目标是 3 也不幂等）")
    void changeStatus_whenCancelled_should409()
    {
        when(scheduleDao.selectById(SCHEDULE_ID)).thenReturn(existing(3, null));

        ServiceException ex = assertThrows(ServiceException.class,
                () -> scheduleService.changeStatus(SCHEDULE_ID, 3));
        assertEquals(409, ex.getCode());
        assertEquals("已取消的排课不可再变更状态", ex.getMessage());
        verify(scheduleDao, never()).updateStatus(any(), any(), any());
    }

    @Test
    @DisplayName("状态流转：已上架但已结束 → 409「已结束的排课不可再变更状态」")
    void changeStatus_whenFinished_should409()
    {
        when(scheduleDao.selectById(SCHEDULE_ID))
                .thenReturn(existing(2, LocalDateTime.now().minusHours(1)));

        ServiceException ex = assertThrows(ServiceException.class,
                () -> scheduleService.changeStatus(SCHEDULE_ID, 1));
        assertEquals(409, ex.getCode());
        assertEquals("已结束的排课不可再变更状态", ex.getMessage());
        verify(scheduleDao, never()).updateStatus(any(), any(), any());
    }

    @Test
    @DisplayName("状态流转：非法流转 1→3 → 409「当前状态不允许该操作」")
    void changeStatus_whenIllegalTransition_should409()
    {
        when(scheduleDao.selectById(SCHEDULE_ID)).thenReturn(existing(1, LocalDateTime.now().plusHours(2)));

        ServiceException ex = assertThrows(ServiceException.class,
                () -> scheduleService.changeStatus(SCHEDULE_ID, 3));
        assertEquals(409, ex.getCode());
        assertEquals("当前状态不允许该操作", ex.getMessage());
        verify(scheduleDao, never()).updateStatus(any(), any(), any());
    }

    @Test
    @DisplayName("状态流转：status 不在 {1,2,3} → 500 参数校验，且不查库")
    void changeStatus_whenStatusInvalid_should500()
    {
        ServiceException ex = assertThrows(ServiceException.class,
                () -> scheduleService.changeStatus(SCHEDULE_ID, 9));
        assertEquals(500, ex.getCode());
        assertEquals("排课状态取值为 1~3", ex.getMessage());
        verify(scheduleDao, never()).selectById(any());
    }

    @Test
    @DisplayName("状态流转：排课不存在 → 404")
    void changeStatus_whenMissing_should404()
    {
        when(scheduleDao.selectById(SCHEDULE_ID)).thenReturn(null);

        ServiceException ex = assertThrows(ServiceException.class,
                () -> scheduleService.changeStatus(SCHEDULE_ID, 2));
        assertEquals(404, ex.getCode());
        assertEquals("排课不存在或已被删除", ex.getMessage());
    }

    // ------------------------------------------------------------------
    // 删除门禁：四个分支
    // ------------------------------------------------------------------

    @Test
    @DisplayName("删除：待上架 → 物理删除")
    void delete_whenPending_shouldPhysicallyDelete()
    {
        when(scheduleDao.selectById(SCHEDULE_ID)).thenReturn(existing(1, null));

        scheduleService.delete(SCHEDULE_ID);

        verify(scheduleDao).deleteById(SCHEDULE_ID);
    }

    @Test
    @DisplayName("删除：已上架未结束 → 409「已上架的排课须先下架才能删除」")
    void delete_whenPublished_should409()
    {
        when(scheduleDao.selectById(SCHEDULE_ID)).thenReturn(existing(2, LocalDateTime.now().plusHours(1)));

        ServiceException ex = assertThrows(ServiceException.class, () -> scheduleService.delete(SCHEDULE_ID));
        assertEquals(409, ex.getCode());
        assertEquals("已上架的排课须先下架才能删除", ex.getMessage());
        verify(scheduleDao, never()).deleteById(any());
    }

    @Test
    @DisplayName("删除：已结束（status=2 且 end_time<=now）→ 409「已结束的排课不可删除」")
    void delete_whenFinished_should409()
    {
        when(scheduleDao.selectById(SCHEDULE_ID)).thenReturn(existing(2, LocalDateTime.now().minusMinutes(1)));

        ServiceException ex = assertThrows(ServiceException.class, () -> scheduleService.delete(SCHEDULE_ID));
        assertEquals(409, ex.getCode());
        assertEquals("已结束的排课不可删除", ex.getMessage());
        verify(scheduleDao, never()).deleteById(any());
    }

    @Test
    @DisplayName("删除：已取消 → 409「已取消的排课不可删除」")
    void delete_whenCancelled_should409()
    {
        when(scheduleDao.selectById(SCHEDULE_ID)).thenReturn(existing(3, null));

        ServiceException ex = assertThrows(ServiceException.class, () -> scheduleService.delete(SCHEDULE_ID));
        assertEquals(409, ex.getCode());
        assertEquals("已取消的排课不可删除", ex.getMessage());
        verify(scheduleDao, never()).deleteById(any());
    }

    @Test
    @DisplayName("删除：不存在 → 404，不执行 DELETE")
    void delete_whenMissing_should404()
    {
        when(scheduleDao.selectById(SCHEDULE_ID)).thenReturn(null);

        ServiceException ex = assertThrows(ServiceException.class, () -> scheduleService.delete(SCHEDULE_ID));
        assertEquals(404, ex.getCode());
        verify(scheduleDao, never()).deleteById(any());
    }

    // ------------------------------------------------------------------
    // 四个门禁统计（占位必须换成真实统计）
    // ------------------------------------------------------------------

    @Test
    @DisplayName("四个 countActiveByXxx 都转发到 DAO 的真实统计（不在内存筛）")
    void countActive_shouldDelegateToDao()
    {
        when(scheduleDao.countActiveByStoreId(STORE_ID)).thenReturn(3L);
        when(scheduleDao.countActiveByClassroomId(CLASSROOM_ID)).thenReturn(2L);
        when(scheduleDao.countActiveByCoachId(COACH_ID)).thenReturn(1L);
        when(scheduleDao.countActiveByCourseId(COURSE_ID)).thenReturn(4L);

        assertEquals(3L, scheduleService.countActiveByStoreId(STORE_ID));
        assertEquals(2L, scheduleService.countActiveByClassroomId(CLASSROOM_ID));
        assertEquals(1L, scheduleService.countActiveByCoachId(COACH_ID));
        assertEquals(4L, scheduleService.countActiveByCourseId(COURSE_ID));

        verify(scheduleDao).countActiveByStoreId(STORE_ID);
        verify(scheduleDao).countActiveByClassroomId(CLASSROOM_ID);
        verify(scheduleDao).countActiveByCoachId(COACH_ID);
        verify(scheduleDao).countActiveByCourseId(COURSE_ID);
    }

    // ------------------------------------------------------------------
    // 用户端：列表与详情
    // ------------------------------------------------------------------

    @Test
    @DisplayName("用户端列表：card 的一期恒量与用户端文案（可约／已结束／已取消）")
    void publicPage_shouldFillCardContract()
    {
        when(storeService.getById(STORE_ID)).thenReturn(new StoreDetailVO());
        when(scheduleDao.selectPublicPage(any(PublicScheduleQuery.class), eq(tomorrow())))
                .thenReturn(publicPageOf(existing(2, LocalDateTime.now().plusHours(1))));
        when(storeService.summaries(anyCollection())).thenReturn(Collections.singletonList(store()));
        when(courseService.summaries(anyCollection())).thenReturn(Collections.singletonList(course(COURSE_TYPE_GROUP)));
        when(coachService.summaries(anyCollection())).thenReturn(Collections.singletonList(coach()));
        when(classroomService.summaries(anyCollection()))
                .thenReturn(Collections.singletonList(classroom()));

        PageResult<ScheduleCardVO> result = scheduleService.publicPage(publicQuery(tomorrow()));

        assertEquals(1, result.getList().size());
        ScheduleCardVO card = result.getList().get(0);
        assertEquals(Integer.valueOf(0), card.getBookedPersons(), "一期已预约人数恒 0");
        assertEquals(card.getMaxPersons(), card.getRemainingPlaces(), "剩余名额恒等于最大人数");
        assertEquals("可约", card.getStatusText(), "用户端文案不能出现「已上架」");
        assertTrue(card.getBookable());
        assertEquals("团课", card.getCourseTypeName(), "课种名按快照码翻译");
        assertEquals("哈他瑜伽", card.getCourseName());
        assertEquals("徐汇店", card.getStoreName());
        assertEquals("王老师", card.getCoachName());
        assertEquals("瑜伽团课大教室", card.getClassroomName());
        assertEquals(Integer.valueOf(2), card.getDifficulty(), "难度实时取课程");
    }

    @Test
    @DisplayName("用户端列表：date 格式非法 → 500，且不查门店、不查库")
    void publicPage_whenDateFormatInvalid_should500()
    {
        PublicScheduleQuery query = publicQuery(tomorrow());
        query.setDate("2026/10/03");

        ServiceException ex = assertThrows(ServiceException.class, () -> scheduleService.publicPage(query));
        assertEquals(500, ex.getCode());
        assertEquals("日期格式非法，应为 yyyy-MM-dd", ex.getMessage());
        verify(storeService, never()).getById(any());
        verify(scheduleDao, never()).selectPublicPage(any(), any());
    }

    @Test
    @DisplayName("用户端列表：date 超出今天起 14 天 → 500")
    void publicPage_whenDateOutOfWindow_should500()
    {
        PublicScheduleQuery query = publicQuery(LocalDate.now().plusDays(20));

        ServiceException ex = assertThrows(ServiceException.class, () -> scheduleService.publicPage(query));
        assertEquals(500, ex.getCode());
        assertEquals("只能查今天起 14 天内的课", ex.getMessage());
        verify(scheduleDao, never()).selectPublicPage(any(), any());
    }

    @Test
    @DisplayName("用户端列表：门店不存在 → 404「门店不存在或已被删除」")
    void publicPage_whenStoreMissing_should404()
    {
        when(storeService.getById(STORE_ID)).thenThrow(new ServiceException("门店不存在或已被删除", 404));

        ServiceException ex = assertThrows(ServiceException.class,
                () -> scheduleService.publicPage(publicQuery(tomorrow())));
        assertEquals(404, ex.getCode());
        assertEquals("门店不存在或已被删除", ex.getMessage());
        verify(scheduleDao, never()).selectPublicPage(any(), any());
    }

    @Test
    @DisplayName("用户端列表：课种越界 → 500（取值校验收在 CourseTypeEnum）")
    void publicPage_whenCourseTypeInvalid_should500()
    {
        PublicScheduleQuery query = publicQuery(tomorrow());
        query.setCourseType(9);

        ServiceException ex = assertThrows(ServiceException.class, () -> scheduleService.publicPage(query));
        assertEquals(500, ex.getCode());
        assertEquals("课程类型取值为 1~4", ex.getMessage());
    }

    @Test
    @DisplayName("用户端详情：「待上架」与「不存在」同一个响应 → 404「排课不存在或已被删除」")
    void publicDetail_whenInvisibleOrMissing_should404()
    {
        when(scheduleDao.selectVisibleById(SCHEDULE_ID)).thenReturn(null);

        ServiceException ex = assertThrows(ServiceException.class,
                () -> scheduleService.publicDetail(SCHEDULE_ID));
        assertEquals(404, ex.getCode());
        assertEquals("排课不存在或已被删除", ex.getMessage());
    }

    @Test
    @DisplayName("用户端详情：课种取快照、封面／介绍／难度实时取课程、bookers 固定空数组")
    void publicDetail_shouldUseSnapshotForTypeAndLiveCourseForMaterials()
    {
        ScheduleDO schedule = existing(2, LocalDateTime.now().plusHours(1));
        schedule.setCourseType(COURSE_TYPE_GROUP);
        schedule.setMinPersons(2);
        when(scheduleDao.selectVisibleById(SCHEDULE_ID)).thenReturn(schedule);
        // 课程侧已把课种改成特色课、难度改成 5：课种要取快照（团课），难度要实时取课程
        CourseSummaryVO liveCourse = course(COURSE_TYPE_FEATURE);
        liveCourse.setDifficulty(5);
        when(courseService.getSummary(COURSE_ID)).thenReturn(liveCourse);
        when(coachService.summaries(anyCollection())).thenReturn(Collections.singletonList(coach()));
        when(storeService.summaries(anyCollection())).thenReturn(Collections.singletonList(store()));
        when(classroomService.summaries(anyCollection()))
                .thenReturn(Collections.singletonList(classroom()));

        PublicScheduleDetailVO detail = scheduleService.publicDetail(SCHEDULE_ID);

        assertEquals(Integer.valueOf(COURSE_TYPE_GROUP), detail.getCourseType(), "课种取排课快照");
        assertEquals("团课", detail.getCourseTypeName(), "课种名由快照码翻译，不回读课程");
        assertEquals(Integer.valueOf(5), detail.getDifficulty(), "难度实时取课程");
        assertEquals("以体式与呼吸配合为主的经典课程", detail.getCourseIntro(), "介绍实时取课程");
        assertEquals("https://cdn.example.com/course/hata.jpg", detail.getCourseCoverUrl());
        assertNotNull(detail.getBookers(), "bookers 不能是 null");
        assertTrue(detail.getBookers().isEmpty(), "一期 bookers 固定空数组");
        assertEquals("王老师", detail.getCoachName());
        assertEquals("十年哈他瑜伽经验", detail.getCoachIntro());
        assertEquals(Integer.valueOf(2), detail.getMinPersons());
    }

    @Test
    @DisplayName("用户端详情：课程／教练／教室已被物理删除 → 对应名称空字符串，不是 null")
    void publicDetail_whenObjectsDeleted_shouldFallbackToEmptyString()
    {
        ScheduleDO schedule = existing(3, null);
        when(scheduleDao.selectVisibleById(SCHEDULE_ID)).thenReturn(schedule);
        when(courseService.getSummary(COURSE_ID)).thenReturn(null);
        when(coachService.summaries(anyCollection())).thenReturn(Collections.emptyList());
        when(storeService.summaries(anyCollection())).thenReturn(Collections.emptyList());
        when(classroomService.summaries(anyCollection())).thenReturn(Collections.emptyList());

        PublicScheduleDetailVO detail = scheduleService.publicDetail(SCHEDULE_ID);

        assertEquals("", detail.getCourseName());
        assertEquals("", detail.getCourseCoverUrl());
        assertEquals("", detail.getCourseIntro());
        assertEquals("", detail.getCoachName());
        assertEquals("", detail.getCoachAvatarUrl());
        assertEquals("", detail.getCoachIntro());
        assertEquals("", detail.getStoreName());
        assertEquals("", detail.getClassroomName());
        assertEquals("已取消", detail.getStatusText(), "用户端已取消文案");
        assertFalse(detail.getBookable());
        assertNull(detail.getDifficulty());
    }

    @Test
    @DisplayName("管理端详情：名称按 ID 批量补齐，对象已删除时名称空字符串")
    void getById_shouldFillNamesWithEmptyFallback()
    {
        when(scheduleDao.selectById(SCHEDULE_ID)).thenReturn(existing(1, null));
        when(storeService.summaries(anyCollection())).thenReturn(Collections.singletonList(store()));
        when(courseService.summaries(anyCollection())).thenReturn(Collections.emptyList());
        when(coachService.summaries(anyCollection())).thenReturn(Collections.singletonList(coach()));
        when(classroomService.summaries(anyCollection()))
                .thenReturn(Collections.singletonList(classroom()));

        ScheduleDetailVO detail = scheduleService.getById(SCHEDULE_ID);

        assertEquals("徐汇店", detail.getStoreName());
        assertEquals("", detail.getCourseName(), "课程已物理删除 → 空字符串");
        assertEquals("王老师", detail.getCoachName());
        assertEquals("待上架", detail.getStatusText(), "管理端文案");
        assertFalse(detail.getFinished());
        assertEquals("团课", detail.getCourseTypeName());
    }

    @Test
    @DisplayName("管理端列表：status=2 且已结束 → statusText「已结束」、finished=true（service 层判定一次）")
    void page_shouldDeriveFinishedStatus()
    {
        ScheduleDO finished = existing(2, LocalDateTime.now().minusHours(2));
        Page<ScheduleDO> daoPage = new Page<ScheduleDO>(1, 10);
        daoPage.setRecords(Collections.singletonList(finished));
        daoPage.setTotal(1);
        when(scheduleDao.selectPage(any(ScheduleQuery.class))).thenReturn(daoPage);
        when(storeService.summaries(anyCollection())).thenReturn(Collections.emptyList());
        when(courseService.summaries(anyCollection())).thenReturn(Collections.emptyList());
        when(coachService.summaries(anyCollection())).thenReturn(Collections.emptyList());
        when(classroomService.summaries(anyCollection())).thenReturn(Collections.emptyList());

        PageResult<ScheduleListItemVO> result = scheduleService.page(new ScheduleQuery());

        assertEquals("已结束", result.getList().get(0).getStatusText());
        assertTrue(result.getList().get(0).getFinished());
    }

    // ------------------------------------------------------------------
    // 辅助
    // ------------------------------------------------------------------

    private void stubRelationsOk(int courseType)
    {
        when(storeService.getById(STORE_ID)).thenReturn(new StoreDetailVO());
        when(courseService.getSummary(COURSE_ID)).thenReturn(course(courseType));
        when(coachService.getSummary(COACH_ID)).thenReturn(new CoachSummaryVO());
    }

    private LocalDate tomorrow()
    {
        return LocalDate.now().plusDays(1);
    }

    private ScheduleUpdateDTO dto(LocalDate day, int startHour, int endHour, int maxPersons, int minPersons)
    {
        return dtoOf(day.atTime(startHour, 0), day.atTime(endHour, 0), maxPersons, minPersons);
    }

    private ScheduleUpdateDTO dtoOf(LocalDateTime start, LocalDateTime end, int maxPersons, int minPersons)
    {
        ScheduleUpdateDTO dto = new ScheduleUpdateDTO();
        dto.setStoreId(STORE_ID);
        dto.setCourseId(COURSE_ID);
        dto.setCoachId(COACH_ID);
        dto.setClassroomId(CLASSROOM_ID);
        dto.setStartTime(start);
        dto.setEndTime(end);
        dto.setMaxPersons(maxPersons);
        dto.setMinPersons(minPersons);
        return dto;
    }

    private ScheduleDO existing(int status, LocalDateTime endTime)
    {
        ScheduleDO schedule = new ScheduleDO();
        schedule.setId(SCHEDULE_ID);
        schedule.setStoreId(STORE_ID);
        schedule.setCourseId(COURSE_ID);
        schedule.setCourseType(COURSE_TYPE_GROUP);
        schedule.setCoachId(COACH_ID);
        schedule.setClassroomId(CLASSROOM_ID);
        schedule.setScheduleDate(LocalDate.now().plusDays(1));
        schedule.setStartTime(LocalDate.now().plusDays(1).atTime(19, 0));
        schedule.setEndTime(endTime == null ? LocalDate.now().plusDays(1).atTime(20, 0) : endTime);
        schedule.setMaxPersons(20);
        schedule.setMinPersons(2);
        schedule.setBookedPersons(0);
        schedule.setStatus(status);
        return schedule;
    }

    private CourseSummaryVO course(int courseType)
    {
        CourseSummaryVO course = new CourseSummaryVO();
        course.setId(COURSE_ID);
        course.setName("哈他瑜伽");
        course.setCourseType(courseType);
        course.setCourseTypeName("团课");
        course.setCoverUrl("https://cdn.example.com/course/hata.jpg");
        course.setIntro("以体式与呼吸配合为主的经典课程");
        course.setDifficulty(2);
        return course;
    }

    private CoachSummaryVO coach()
    {
        CoachSummaryVO coach = new CoachSummaryVO();
        coach.setId(COACH_ID);
        coach.setName("王老师");
        coach.setAvatarUrl("https://cdn.example.com/coach/1.jpg");
        coach.setIntro("十年哈他瑜伽经验");
        return coach;
    }

    private StoreSummaryVO store()
    {
        StoreSummaryVO store = new StoreSummaryVO();
        store.setId(STORE_ID);
        store.setName("徐汇店");
        return store;
    }

    private ClassroomSummaryVO classroom()
    {
        ClassroomSummaryVO classroom = new ClassroomSummaryVO();
        classroom.setId(CLASSROOM_ID);
        classroom.setStoreId(STORE_ID);
        classroom.setName("瑜伽团课大教室");
        return classroom;
    }

    private PublicScheduleQuery publicQuery(LocalDate date)
    {
        PublicScheduleQuery query = new PublicScheduleQuery();
        query.setStoreId(STORE_ID);
        query.setCourseType(COURSE_TYPE_GROUP);
        query.setDate(date.toString());
        return query;
    }

    private Page<ScheduleDO> publicPageOf(ScheduleDO record)
    {
        Page<ScheduleDO> page = new Page<ScheduleDO>(1, 10);
        page.setRecords(Collections.singletonList(record));
        page.setTotal(1);
        return page;
    }
}
