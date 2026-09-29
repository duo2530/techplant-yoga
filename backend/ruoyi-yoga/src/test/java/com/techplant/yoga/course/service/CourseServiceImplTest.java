package com.techplant.yoga.course.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ruoyi.common.exception.ServiceException;
import com.techplant.yoga.booking.service.BookingQueryService;
import com.techplant.yoga.common.response.PageResult;
import com.techplant.yoga.course.dao.CourseDao;
import com.techplant.yoga.course.domain.CourseDO;
import com.techplant.yoga.course.dto.CourseCreateDTO;
import com.techplant.yoga.course.dto.CourseUpdateDTO;
import com.techplant.yoga.course.query.CourseQuery;
import com.techplant.yoga.course.service.impl.CourseServiceImpl;
import com.techplant.yoga.course.vo.CourseCreatedVO;
import com.techplant.yoga.course.vo.CourseDetailVO;
import com.techplant.yoga.course.vo.CourseListItemVO;
import com.techplant.yoga.schedule.service.ScheduleQueryService;

/**
 * 课程业务实现的单元测试（详细设计 §5.1.1，用例 5.1.1.1 ~ 5.1.1.14）。
 *
 * <p>依赖（课程数据访问、排班统计、预约统计）全部用模拟对象隔离，<b>不连数据库</b>（§5.1）。</p>
 *
 * <p>用例顺序按「类 → 方法 → 分支」排：设置状态（停用 4 条 / 启用 1 条）→ 新增 → 查询列表 →
 * 查询详情 → 修改（§5.1.1 覆盖表、R05）。</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CourseServiceImpl 单元测试")
class CourseServiceImplTest
{
    /** 用例里的课程编号 C001 */
    private static final Long COURSE_ID = 1001L;

    /** 用例里的操作人编号 */
    private static final Long OPERATOR_ID = 1L;

    @Mock
    private CourseDao courseDao;

    @Mock
    private ScheduleQueryService scheduleQueryService;

    @Mock
    private BookingQueryService bookingQueryService;

    @InjectMocks
    private CourseServiceImpl courseService;

    @BeforeEach
    void setUpOperator()
    {
        // 让 CurrentUserUtils 能取到操作人（审计字段与日志都要用）
        com.ruoyi.common.core.domain.model.LoginUser loginUser = new com.ruoyi.common.core.domain.model.LoginUser();
        loginUser.setUserId(OPERATOR_ID);
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(loginUser, null));
    }

    @AfterEach
    void clearOperator()
    {
        SecurityContextHolder.clearContext();
    }

    // ------------------------------------------------------------------
    // 设置课程状态（停用）：5.1.1.1 ~ 5.1.1.4
    // ------------------------------------------------------------------

    @Test
    @DisplayName("5.1.1.1 停用课程：课程下有未完成的排班 → 拒绝停用（业务码 409）")
    void disableCourse_withUnfinishedSchedule_shouldReject()
    {
        when(courseDao.selectById(COURSE_ID)).thenReturn(enabledCourse());
        when(scheduleQueryService.countUnfinishedByCourseId(COURSE_ID)).thenReturn(3L);
        when(bookingQueryService.countUnfinishedByCourseId(COURSE_ID)).thenReturn(0L);

        ServiceException exception = assertThrows(ServiceException.class,
                () -> courseService.updateStatus(COURSE_ID, 0));

        // 响应体系统一为若依 AjaxResult 后，阻塞明细放在提示语里（不再有结构化 data）
        assertEquals(409, exception.getCode().intValue());
        assertTrue(exception.getMessage().contains("3 个未完成排班"), exception.getMessage());
        assertTrue(exception.getMessage().contains("0 条未结束预约"), exception.getMessage());
        verify(courseDao, never()).updateStatus(any(), any(), any());
    }

    @Test
    @DisplayName("5.1.1.2 停用课程：课程下有未结束的预约 → 拒绝停用（业务码 409）")
    void disableCourse_withUnfinishedBooking_shouldReject()
    {
        when(courseDao.selectById(COURSE_ID)).thenReturn(enabledCourse());
        when(scheduleQueryService.countUnfinishedByCourseId(COURSE_ID)).thenReturn(0L);
        when(bookingQueryService.countUnfinishedByCourseId(COURSE_ID)).thenReturn(5L);

        ServiceException exception = assertThrows(ServiceException.class,
                () -> courseService.updateStatus(COURSE_ID, 0));

        assertEquals(409, exception.getCode().intValue());
        assertTrue(exception.getMessage().contains("0 个未完成排班"), exception.getMessage());
        assertTrue(exception.getMessage().contains("5 条未结束预约"), exception.getMessage());
        verify(courseDao, never()).updateStatus(any(), any(), any());
    }

    @Test
    @DisplayName("5.1.1.3 停用课程：既无排班也无预约 → 停用成功")
    void disableCourse_withoutReference_shouldSucceed()
    {
        when(courseDao.selectById(COURSE_ID)).thenReturn(enabledCourse());
        when(scheduleQueryService.countUnfinishedByCourseId(COURSE_ID)).thenReturn(0L);
        when(bookingQueryService.countUnfinishedByCourseId(COURSE_ID)).thenReturn(0L);
        when(courseDao.updateStatus(eq(COURSE_ID), eq(0), any())).thenReturn(1);

        courseService.updateStatus(COURSE_ID, 0);

        verify(courseDao, times(1)).updateStatus(COURSE_ID, 0, OPERATOR_ID);
    }

    @Test
    @DisplayName("5.1.1.4 停用课程：课程不存在 → 提示课程不存在")
    void disableCourse_notExists_shouldThrow404()
    {
        when(courseDao.selectById(COURSE_ID)).thenReturn(null);

        ServiceException exception = assertThrows(ServiceException.class,
                () -> courseService.updateStatus(COURSE_ID, 0));

        assertEquals(404, exception.getCode().intValue());
        verify(scheduleQueryService, never()).countUnfinishedByCourseId(anyLong());
        verify(bookingQueryService, never()).countUnfinishedByCourseId(anyLong());
        verify(courseDao, never()).updateStatus(any(), any(), any());
    }

    @Test
    @DisplayName("5.1.1.5 启用课程：不触发引用检查 → 启用成功")
    void enableCourse_shouldSkipReferenceCheck()
    {
        CourseDO disabled = enabledCourse();
        disabled.setStatus(0);
        when(courseDao.selectById(COURSE_ID)).thenReturn(disabled);
        when(courseDao.updateStatus(eq(COURSE_ID), eq(1), any())).thenReturn(1);

        courseService.updateStatus(COURSE_ID, 1);

        // 重点用例：只有停用才做引用检查，防止实现时把检查错放到启用分支
        verify(scheduleQueryService, never()).countUnfinishedByCourseId(anyLong());
        verify(bookingQueryService, never()).countUnfinishedByCourseId(anyLong());
        verify(courseDao, times(1)).updateStatus(COURSE_ID, 1, OPERATOR_ID);
    }

    // ------------------------------------------------------------------
    // 新增课程：5.1.1.6、5.1.1.14
    // ------------------------------------------------------------------

    @Test
    @DisplayName("5.1.1.6 新增课程：参数合法 → 返回课程编号且默认启用")
    void createCourse_shouldReturnGeneratedId()
    {
        CourseCreateDTO dto = new CourseCreateDTO();
        dto.setStoreId(2001L);
        dto.setName("哈他瑜伽");
        dto.setType(1);
        dto.setDifficulty(2);
        dto.setCoverUrl("https://cdn.example.com/course/hata.jpg");
        dto.setIntro("以体式与呼吸配合为主的经典课程");
        dto.setDurationMin(60);
        dto.setSortNo(10);
        when(courseDao.insert(any(CourseDO.class))).thenAnswer(invocation -> {
            CourseDO course = invocation.getArgument(0);
            course.setId(1856739201475235840L);
            return 1;
        });

        CourseCreatedVO created = courseService.create(dto);

        ArgumentCaptor<CourseDO> captor = ArgumentCaptor.forClass(CourseDO.class);
        verify(courseDao).insert(captor.capture());
        CourseDO saved = captor.getValue();
        assertEquals("哈他瑜伽", saved.getName());
        assertEquals(Integer.valueOf(1), saved.getType());
        assertEquals(Integer.valueOf(2), saved.getDifficulty());
        assertEquals(60, saved.getDurationMin().intValue());
        assertEquals(Integer.valueOf(10), saved.getSortNo());
        // 状态启用、未删除、主键不赋值（由框架生成）
        assertEquals(Integer.valueOf(1), saved.getStatus());
        assertEquals(Integer.valueOf(0), saved.getDeleted());
        assertEquals(1856739201475235840L, created.getId());
    }

    @Test
    @DisplayName("5.1.1.14 新增课程：不传排序号 → 默认 0")
    void createCourse_withoutSortNo_shouldDefaultZero()
    {
        CourseCreateDTO dto = new CourseCreateDTO();
        dto.setStoreId(2001L);
        dto.setName("流瑜伽");
        dto.setType(2);
        dto.setDifficulty(3);
        when(courseDao.insert(any(CourseDO.class))).thenAnswer(invocation -> {
            CourseDO course = invocation.getArgument(0);
            course.setId(2L);
            return 1;
        });

        CourseCreatedVO created = courseService.create(dto);

        ArgumentCaptor<CourseDO> captor = ArgumentCaptor.forClass(CourseDO.class);
        verify(courseDao).insert(captor.capture());
        CourseDO saved = captor.getValue();
        assertEquals(Integer.valueOf(0), saved.getSortNo());
        assertNull(saved.getCoverUrl());
        assertNull(saved.getIntro());
        assertNull(saved.getDurationMin());
        assertNotNull(created.getId());
    }

    // ------------------------------------------------------------------
    // 查询课程列表：5.1.1.7、5.1.1.8
    // ------------------------------------------------------------------

    @Test
    @DisplayName("5.1.1.7 查询课程列表：无条件 → 返回分页数据")
    void pageCourses_withoutCondition()
    {
        when(courseDao.selectPage(any(CourseQuery.class))).thenReturn(pageOf(course(1L, "哈他瑜伽"), course(2L, "流瑜伽")));

        PageResult<CourseListItemVO> result = courseService.page(new CourseQuery());

        assertEquals(Long.valueOf(2L), result.getTotal());
        assertEquals(2, result.getList().size());
        // 列表项不含课程介绍与单节时长：由 CourseListItemVO 的字段设计保证（见 CourseConverterTest 5.1.1.29）
        assertEquals("哈他瑜伽", result.getList().get(0).getName());
    }

    @Test
    @DisplayName("5.1.1.8 查询课程列表：带筛选条件 → 条件正确传入")
    void pageCourses_withCondition()
    {
        when(courseDao.selectPage(any(CourseQuery.class))).thenReturn(pageOf());

        CourseQuery query = new CourseQuery();
        query.setName("瑜伽");
        query.setType(2);
        query.setStatus(1);
        query.setPageNum(2);
        query.setPageSize(20);
        courseService.page(query);

        ArgumentCaptor<CourseQuery> captor = ArgumentCaptor.forClass(CourseQuery.class);
        verify(courseDao).selectPage(captor.capture());
        CourseQuery passed = captor.getValue();
        assertEquals("瑜伽", passed.getName());
        assertEquals(Integer.valueOf(2), passed.getType());
        assertEquals(Integer.valueOf(1), passed.getStatus());
        assertEquals(Integer.valueOf(2), passed.getPageNum());
        assertEquals(Integer.valueOf(20), passed.getPageSize());
    }

    // ------------------------------------------------------------------
    // 查询课程详情：5.1.1.9、5.1.1.10
    // ------------------------------------------------------------------

    @Test
    @DisplayName("5.1.1.9 查询课程详情：课程存在 → 返回详情")
    void getCourse_exists()
    {
        CourseDO course = enabledCourse();
        course.setIntro("课程介绍");
        course.setDurationMin(60);
        course.setCreateBy(9L);
        course.setUpdateBy(9L);
        when(courseDao.selectById(COURSE_ID)).thenReturn(course);

        CourseDetailVO detail = courseService.getById(COURSE_ID);

        assertEquals("哈他瑜伽", detail.getName());
        assertEquals(Integer.valueOf(1), detail.getType());
        assertEquals(Integer.valueOf(2), detail.getDifficulty());
        assertEquals("https://cdn.example.com/course/hata.jpg", detail.getCoverUrl());
        assertEquals("课程介绍", detail.getIntro());
        assertEquals(Integer.valueOf(60), detail.getDurationMin());
        assertEquals(Integer.valueOf(10), detail.getSortNo());
        assertEquals(Integer.valueOf(1), detail.getStatus());
        assertNotNull(detail.getCreateTime());
        assertNotNull(detail.getUpdateTime());
        // 不含创建人与更新人：由 CourseDetailVO 的字段设计保证（见 CourseConverterTest 5.1.1.29 同类断言）
        assertEquals(false, hasField(CourseDetailVO.class, "createBy"));
        assertEquals(false, hasField(CourseDetailVO.class, "updateBy"));
    }

    @Test
    @DisplayName("5.1.1.10 查询课程详情：课程不存在 → 提示课程不存在")
    void getCourse_notExists()
    {
        when(courseDao.selectById(COURSE_ID)).thenReturn(null);

        ServiceException exception = assertThrows(ServiceException.class, () -> courseService.getById(COURSE_ID));

        assertEquals(404, exception.getCode().intValue());
    }

    // ------------------------------------------------------------------
    // 修改课程：5.1.1.11 ~ 5.1.1.13
    // ------------------------------------------------------------------

    @Test
    @DisplayName("5.1.1.11 修改课程：课程存在 → 字段更新成功")
    void updateCourse_shouldUpdateFields()
    {
        CourseDO course = enabledCourse();
        when(courseDao.selectById(COURSE_ID)).thenReturn(course);
        when(courseDao.updateById(any(CourseDO.class))).thenReturn(1);

        CourseUpdateDTO dto = new CourseUpdateDTO();
        dto.setStoreId(2001L);
        dto.setName("哈他瑜伽（初级）");
        dto.setType(1);
        dto.setDifficulty(3);
        dto.setCoverUrl("https://cdn.example.com/course/hata2.jpg");
        dto.setIntro("新介绍");
        dto.setDurationMin(75);
        dto.setSortNo(20);

        courseService.update(COURSE_ID, dto);

        ArgumentCaptor<CourseDO> captor = ArgumentCaptor.forClass(CourseDO.class);
        verify(courseDao).updateById(captor.capture());
        CourseDO updated = captor.getValue();
        assertEquals(COURSE_ID, updated.getId());
        assertEquals("哈他瑜伽（初级）", updated.getName());
        assertEquals(Integer.valueOf(3), updated.getDifficulty());
        assertEquals(Integer.valueOf(75), updated.getDurationMin());
        assertEquals(Integer.valueOf(20), updated.getSortNo());
        assertEquals(OPERATOR_ID, updated.getUpdateBy());
    }

    @Test
    @DisplayName("5.1.1.12 修改课程：封面图与介绍传空 → 被清空")
    void updateCourse_withNullFields_shouldClearThem()
    {
        CourseDO course = enabledCourse();
        course.setIntro("原介绍");
        when(courseDao.selectById(COURSE_ID)).thenReturn(course);
        when(courseDao.updateById(any(CourseDO.class))).thenReturn(1);

        CourseUpdateDTO dto = new CourseUpdateDTO();
        dto.setStoreId(2001L);
        dto.setName("哈他瑜伽（初级）");
        dto.setType(1);
        dto.setDifficulty(3);
        dto.setCoverUrl(null);
        dto.setIntro(null);
        dto.setDurationMin(null);
        dto.setSortNo(20);

        courseService.update(COURSE_ID, dto);

        // 重点用例：传空即清空，不能实现成「只有非空才更新」
        ArgumentCaptor<CourseDO> captor = ArgumentCaptor.forClass(CourseDO.class);
        verify(courseDao).updateById(captor.capture());
        CourseDO updated = captor.getValue();
        assertNull(updated.getCoverUrl());
        assertNull(updated.getIntro());
        assertNull(updated.getDurationMin());
        assertEquals("哈他瑜伽（初级）", updated.getName());
    }

    @Test
    @DisplayName("5.1.1.13 修改课程：课程不存在 → 提示课程不存在")
    void updateCourse_notExists()
    {
        when(courseDao.selectById(COURSE_ID)).thenReturn(null);

        CourseUpdateDTO dto = new CourseUpdateDTO();
        dto.setStoreId(2001L);
        dto.setName("哈他瑜伽");
        dto.setType(1);
        dto.setDifficulty(2);

        ServiceException exception = assertThrows(ServiceException.class, () -> courseService.update(COURSE_ID, dto));

        assertEquals(404, exception.getCode().intValue());
        verify(courseDao, never()).updateById(any(CourseDO.class));
    }

    // ------------------------------------------------------------------
    // 测试数据与工具
    // ------------------------------------------------------------------

    private CourseDO enabledCourse()
    {
        CourseDO course = new CourseDO();
        course.setId(COURSE_ID);
        course.setName("哈他瑜伽");
        course.setType(1);
        course.setDifficulty(2);
        course.setCoverUrl("https://cdn.example.com/course/hata.jpg");
        course.setSortNo(10);
        course.setStatus(1);
        course.setDeleted(0);
        course.setCreateTime(LocalDateTime.of(2026, 9, 20, 10, 12, 33));
        course.setUpdateTime(LocalDateTime.of(2026, 9, 21, 9, 3, 11));
        return course;
    }

    private CourseDO course(Long id, String name)
    {
        CourseDO course = new CourseDO();
        course.setId(id);
        course.setName(name);
        course.setType(1);
        course.setDifficulty(1);
        course.setStatus(1);
        course.setSortNo(0);
        course.setDeleted(0);
        return course;
    }

    private IPage<CourseDO> pageOf(CourseDO... courses)
    {
        List<CourseDO> records = new ArrayList<CourseDO>();
        for (CourseDO course : courses)
        {
            records.add(course);
        }
        Page<CourseDO> page = new Page<CourseDO>(1, 10);
        page.setRecords(records);
        page.setTotal(records.size());
        return page;
    }

    private boolean hasField(Class<?> type, String fieldName)
    {
        try
        {
            type.getDeclaredField(fieldName);
            return true;
        }
        catch (NoSuchFieldException e)
        {
            return false;
        }
    }
}

