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
import java.util.Arrays;
import java.util.Collections;
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
import com.ruoyi.common.core.domain.model.LoginUser;
import com.ruoyi.common.exception.ServiceException;
import com.techplant.yoga.common.response.PageResult;
import com.techplant.yoga.course.dao.CourseDao;
import com.techplant.yoga.course.domain.CourseDO;
import com.techplant.yoga.course.dto.CourseCreateDTO;
import com.techplant.yoga.course.dto.CourseUpdateDTO;
import com.techplant.yoga.course.query.CourseQuery;
import com.techplant.yoga.course.service.impl.CourseServiceImpl;
import com.techplant.yoga.course.vo.CourseDetailVO;
import com.techplant.yoga.course.vo.CourseListItemVO;
import com.techplant.yoga.course.vo.CourseSummaryVO;
import com.techplant.yoga.schedule.service.ScheduleService;

/**
 * 课程业务实现的单元测试。
 *
 * <p>依赖（课程数据访问、排课统计）全部用模拟对象隔离，<b>不连数据库</b>。</p>
 *
 * <p><b>本轮口径：</b>课程<b>没有状态接口</b>（{@code updateStatus} 已删除）、名称全平台唯一、
 * 修改<b>不做引用检查</b>、删除是<b>物理删除</b>且带排课门禁（统计失败 fail-closed）。</p>
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
    private ScheduleService scheduleService;

    @InjectMocks
    private CourseServiceImpl courseService;

    @BeforeEach
    void setUpOperator()
    {
        LoginUser loginUser = new LoginUser();
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
    // 新增课程
    // ------------------------------------------------------------------

    @Test
    @DisplayName("新增课程：参数合法 → 不返回 ID，只落库一条（课种/难度/封面/介绍都写入）")
    void createCourse_shouldInsertWithoutReturningId()
    {
        CourseCreateDTO dto = new CourseCreateDTO();
        dto.setName("哈他瑜伽");
        dto.setCourseType(1);
        dto.setDifficulty(2);
        dto.setCoverUrl("https://cdn.example.com/course/hata.jpg");
        dto.setIntro("以体式与呼吸配合为主的经典课程");
        when(courseDao.selectByName("哈他瑜伽")).thenReturn(null);
        when(courseDao.insert(any(CourseDO.class))).thenReturn(1);

        courseService.create(dto);

        ArgumentCaptor<CourseDO> captor = ArgumentCaptor.forClass(CourseDO.class);
        verify(courseDao).insert(captor.capture());
        CourseDO saved = captor.getValue();
        assertEquals("哈他瑜伽", saved.getName());
        assertEquals(Integer.valueOf(1), saved.getCourseType());
        assertEquals(Integer.valueOf(2), saved.getDifficulty());
        assertEquals("https://cdn.example.com/course/hata.jpg", saved.getCoverUrl());
        assertEquals("以体式与呼吸配合为主的经典课程", saved.getIntro());
        // 主键由框架生成，service 不赋值；也没有 status / deleted / storeId / sortNo
        assertNull(saved.getId());
    }

    @Test
    @DisplayName("新增课程：名称已存在 → 拒绝（409「课程名称已存在」），不落库")
    void createCourse_withDuplicateName_shouldReject()
    {
        CourseCreateDTO dto = new CourseCreateDTO();
        dto.setName("哈他瑜伽");
        dto.setCourseType(1);
        dto.setDifficulty(2);
        when(courseDao.selectByName("哈他瑜伽")).thenReturn(course(2001L, "哈他瑜伽"));

        ServiceException exception = assertThrows(ServiceException.class, () -> courseService.create(dto));

        assertEquals(409, exception.getCode().intValue());
        assertEquals("课程名称已存在", exception.getMessage());
        verify(courseDao, never()).insert(any(CourseDO.class));
    }

    @Test
    @DisplayName("新增课程：课种越界 → 500「课程类型取值为 1~4」（枚举兜底校验）")
    void createCourse_withInvalidCourseType_shouldReject()
    {
        CourseCreateDTO dto = new CourseCreateDTO();
        dto.setName("哈他瑜伽");
        dto.setCourseType(9);
        dto.setDifficulty(2);

        ServiceException exception = assertThrows(ServiceException.class, () -> courseService.create(dto));

        assertEquals(500, exception.getCode().intValue());
        assertEquals("课程类型取值为 1~4", exception.getMessage());
        verify(courseDao, never()).insert(any(CourseDO.class));
    }

    @Test
    @DisplayName("新增课程：落库未生效（影响行数 0）→ 500")
    void createCourse_whenInsertFails_shouldThrow()
    {
        CourseCreateDTO dto = new CourseCreateDTO();
        dto.setName("哈他瑜伽");
        dto.setCourseType(1);
        dto.setDifficulty(2);
        when(courseDao.selectByName("哈他瑜伽")).thenReturn(null);
        when(courseDao.insert(any(CourseDO.class))).thenReturn(0);

        ServiceException exception = assertThrows(ServiceException.class, () -> courseService.create(dto));

        assertEquals(500, exception.getCode().intValue());
    }

    // ------------------------------------------------------------------
    // 查询课程列表
    // ------------------------------------------------------------------

    @Test
    @DisplayName("查询课程列表：无条件 → 返回分页数据，列表项含课种名称")
    void pageCourses_withoutCondition()
    {
        when(courseDao.selectPage(any(CourseQuery.class)))
                .thenReturn(pageOf(course(1L, "哈他瑜伽"), course(2L, "流瑜伽")));

        PageResult<CourseListItemVO> result = courseService.page(new CourseQuery());

        assertEquals(Long.valueOf(2L), result.getTotal());
        assertEquals(2, result.getList().size());
        assertEquals("哈他瑜伽", result.getList().get(0).getName());
        assertEquals(Integer.valueOf(1), result.getList().get(0).getCourseType());
        assertEquals("团课", result.getList().get(0).getCourseTypeName(), "课种码与名称成对返回");
    }

    @Test
    @DisplayName("查询课程列表：带筛选条件 → 条件正确传入（只有名称与课种）")
    void pageCourses_withCondition()
    {
        when(courseDao.selectPage(any(CourseQuery.class))).thenReturn(pageOf());

        CourseQuery query = new CourseQuery();
        query.setName("瑜伽");
        query.setCourseType(2);
        query.setPageNum(2);
        query.setPageSize(20);
        courseService.page(query);

        ArgumentCaptor<CourseQuery> captor = ArgumentCaptor.forClass(CourseQuery.class);
        verify(courseDao).selectPage(captor.capture());
        CourseQuery passed = captor.getValue();
        assertEquals("瑜伽", passed.getName());
        assertEquals(Integer.valueOf(2), passed.getCourseType());
        assertEquals(Integer.valueOf(2), passed.getPageNum());
        assertEquals(Integer.valueOf(20), passed.getPageSize());
    }

    // ------------------------------------------------------------------
    // 查询课程详情
    // ------------------------------------------------------------------

    @Test
    @DisplayName("查询课程详情：课程存在 → 返回详情（含 intro，课种成对）")
    void getCourse_exists()
    {
        CourseDO course = fullCourse();
        when(courseDao.selectById(COURSE_ID)).thenReturn(course);

        CourseDetailVO detail = courseService.getById(COURSE_ID);

        assertEquals("哈他瑜伽", detail.getName());
        assertEquals(Integer.valueOf(1), detail.getCourseType());
        assertEquals("团课", detail.getCourseTypeName());
        assertEquals(Integer.valueOf(2), detail.getDifficulty());
        assertEquals("https://cdn.example.com/course/hata.jpg", detail.getCoverUrl());
        assertEquals("课程介绍", detail.getIntro());
        assertNotNull(detail.getCreateTime());
        assertNotNull(detail.getUpdateTime());
    }

    @Test
    @DisplayName("查询课程详情：课程不存在（或已物理删除）→ 404")
    void getCourse_notExists()
    {
        when(courseDao.selectById(COURSE_ID)).thenReturn(null);

        ServiceException exception = assertThrows(ServiceException.class, () -> courseService.getById(COURSE_ID));

        assertEquals(404, exception.getCode().intValue());
        assertEquals("课程不存在或已被删除", exception.getMessage());
    }

    // ------------------------------------------------------------------
    // 修改课程
    // ------------------------------------------------------------------

    @Test
    @DisplayName("修改课程：课程存在 → 字段全量覆盖，返回最新详情，不做引用检查")
    void updateCourse_shouldUpdateFieldsWithoutReferenceCheck()
    {
        CourseDO course = fullCourse();
        when(courseDao.selectById(COURSE_ID)).thenReturn(course);
        when(courseDao.selectByNameExcludeId(eq("哈他瑜伽（初级）"), eq(COURSE_ID))).thenReturn(null);
        when(courseDao.updateById(any(CourseDO.class))).thenReturn(1);

        CourseUpdateDTO dto = new CourseUpdateDTO();
        dto.setName("哈他瑜伽（初级）");
        dto.setCourseType(2);
        dto.setDifficulty(3);
        dto.setCoverUrl("https://cdn.example.com/course/hata2.jpg");
        dto.setIntro("新介绍");

        CourseDetailVO detail = courseService.update(COURSE_ID, dto);

        ArgumentCaptor<CourseDO> captor = ArgumentCaptor.forClass(CourseDO.class);
        verify(courseDao).updateById(captor.capture());
        CourseDO updated = captor.getValue();
        assertEquals(COURSE_ID, updated.getId());
        assertEquals("哈他瑜伽（初级）", updated.getName());
        assertEquals(Integer.valueOf(2), updated.getCourseType());
        assertEquals(Integer.valueOf(3), updated.getDifficulty());
        assertEquals(OPERATOR_ID, updated.getUpdateBy());
        assertEquals("精品课", detail.getCourseTypeName());
        // 修改不做引用检查：一次排课统计都不该发生
        verify(scheduleService, never()).countActiveByCourseId(anyLong());
    }

    @Test
    @DisplayName("修改课程：封面图与介绍传空 → 被清空（传空即清空）")
    void updateCourse_withNullFields_shouldClearThem()
    {
        CourseDO course = fullCourse();
        when(courseDao.selectById(COURSE_ID)).thenReturn(course);
        when(courseDao.selectByNameExcludeId(any(), eq(COURSE_ID))).thenReturn(null);
        when(courseDao.updateById(any(CourseDO.class))).thenReturn(1);

        CourseUpdateDTO dto = new CourseUpdateDTO();
        dto.setName("哈他瑜伽（初级）");
        dto.setCourseType(1);
        dto.setDifficulty(3);
        dto.setCoverUrl(null);
        dto.setIntro(null);

        courseService.update(COURSE_ID, dto);

        ArgumentCaptor<CourseDO> captor = ArgumentCaptor.forClass(CourseDO.class);
        verify(courseDao).updateById(captor.capture());
        CourseDO updated = captor.getValue();
        assertNull(updated.getCoverUrl());
        assertNull(updated.getIntro());
    }

    @Test
    @DisplayName("修改课程：名称与其它课程重复 → 409，不更新")
    void updateCourse_withDuplicateName_shouldReject()
    {
        when(courseDao.selectById(COURSE_ID)).thenReturn(fullCourse());
        when(courseDao.selectByNameExcludeId(eq("流瑜伽"), eq(COURSE_ID))).thenReturn(course(2002L, "流瑜伽"));

        CourseUpdateDTO dto = new CourseUpdateDTO();
        dto.setName("流瑜伽");
        dto.setCourseType(1);
        dto.setDifficulty(2);

        ServiceException exception = assertThrows(ServiceException.class, () -> courseService.update(COURSE_ID, dto));

        assertEquals(409, exception.getCode().intValue());
        assertEquals("课程名称已存在", exception.getMessage());
        verify(courseDao, never()).updateById(any(CourseDO.class));
    }

    @Test
    @DisplayName("修改课程：课程不存在 → 404，不更新")
    void updateCourse_notExists()
    {
        when(courseDao.selectById(COURSE_ID)).thenReturn(null);

        CourseUpdateDTO dto = new CourseUpdateDTO();
        dto.setName("哈他瑜伽");
        dto.setCourseType(1);
        dto.setDifficulty(2);

        ServiceException exception = assertThrows(ServiceException.class, () -> courseService.update(COURSE_ID, dto));

        assertEquals(404, exception.getCode().intValue());
        verify(courseDao, never()).updateById(any(CourseDO.class));
    }

    // ------------------------------------------------------------------
    // 删除课程（物理删除 + 门禁）
    // ------------------------------------------------------------------

    @Test
    @DisplayName("删除课程：无未结束排课 → 物理删除成功（走 deleteById，不是 UPDATE deleted）")
    void deleteCourse_withoutReference_shouldPhysicallyDelete()
    {
        when(courseDao.selectById(COURSE_ID)).thenReturn(fullCourse());
        when(scheduleService.countActiveByCourseId(COURSE_ID)).thenReturn(0L);
        when(courseDao.deleteById(COURSE_ID)).thenReturn(1);

        courseService.delete(COURSE_ID);

        verify(courseDao, times(1)).deleteById(COURSE_ID);
    }

    @Test
    @DisplayName("删除课程：仍有未结束排课 → 409「该课程仍有 N 节未结束的排课，无法删除」，不删除")
    void deleteCourse_withActiveSchedule_shouldReject()
    {
        when(courseDao.selectById(COURSE_ID)).thenReturn(fullCourse());
        when(scheduleService.countActiveByCourseId(COURSE_ID)).thenReturn(3L);

        ServiceException exception = assertThrows(ServiceException.class, () -> courseService.delete(COURSE_ID));

        assertEquals(409, exception.getCode().intValue());
        assertEquals("该课程仍有 3 节未结束的排课，无法删除", exception.getMessage());
        verify(courseDao, never()).deleteById(anyLong());
    }

    @Test
    @DisplayName("删除课程：引用统计抛异常 → fail-closed，409「引用检查未完成，已拒绝本次删除」，不删除")
    void deleteCourse_whenReferenceCheckFails_shouldFailClosed()
    {
        when(courseDao.selectById(COURSE_ID)).thenReturn(fullCourse());
        when(scheduleService.countActiveByCourseId(COURSE_ID)).thenThrow(new RuntimeException("统计服务不可用"));

        ServiceException exception = assertThrows(ServiceException.class, () -> courseService.delete(COURSE_ID));

        assertEquals(409, exception.getCode().intValue());
        assertEquals("引用检查未完成，已拒绝本次删除", exception.getMessage());
        verify(courseDao, never()).deleteById(anyLong());
    }

    @Test
    @DisplayName("删除课程：课程不存在 → 404，不统计、不删除")
    void deleteCourse_notExists_shouldThrow404()
    {
        when(courseDao.selectById(COURSE_ID)).thenReturn(null);

        ServiceException exception = assertThrows(ServiceException.class, () -> courseService.delete(COURSE_ID));

        assertEquals(404, exception.getCode().intValue());
        verify(scheduleService, never()).countActiveByCourseId(anyLong());
        verify(courseDao, never()).deleteById(anyLong());
    }

    // ------------------------------------------------------------------
    // 跨模块出参：summaries / getSummary
    // ------------------------------------------------------------------

    @Test
    @DisplayName("批量摘要：按去重后的编号一次查回，课种名一并翻译")
    void summaries_shouldDeduplicateAndTranslate()
    {
        when(courseDao.selectByIds(any())).thenReturn(Arrays.asList(course(1L, "哈他瑜伽"), course(2L, "流瑜伽")));

        List<CourseSummaryVO> list = courseService.summaries(Arrays.asList(1L, 2L, 1L, null));

        assertEquals(2, list.size());
        assertEquals("团课", list.get(0).getCourseTypeName());
        ArgumentCaptor<java.util.Collection<Long>> captor = ArgumentCaptor.forClass(java.util.Collection.class);
        verify(courseDao).selectByIds(captor.capture());
        assertEquals(2, captor.getValue().size(), "重复 id 与 null 应先去掉");
    }

    @Test
    @DisplayName("批量摘要：入参为空返回空列表，不查库")
    void summaries_withEmptyIds_shouldReturnEmpty()
    {
        assertTrue(courseService.summaries(null).isEmpty());
        assertTrue(courseService.summaries(Collections.<Long>emptyList()).isEmpty());
        verify(courseDao, never()).selectByIds(any());
    }

    @Test
    @DisplayName("单条摘要：课程存在返回摘要；不存在返回 null（调用方按 404 处理）")
    void getSummary_shouldReturnNullWhenAbsent()
    {
        when(courseDao.selectById(COURSE_ID)).thenReturn(fullCourse());
        CourseSummaryVO summary = courseService.getSummary(COURSE_ID);
        assertNotNull(summary);
        assertEquals("团课", summary.getCourseTypeName());
        assertEquals("课程介绍", summary.getIntro());

        when(courseDao.selectById(9999L)).thenReturn(null);
        assertNull(courseService.getSummary(9999L));
        assertNull(courseService.getSummary(null));
    }

    // ------------------------------------------------------------------
    // 测试数据与工具
    // ------------------------------------------------------------------

    private CourseDO fullCourse()
    {
        CourseDO course = new CourseDO();
        course.setId(COURSE_ID);
        course.setName("哈他瑜伽");
        course.setCourseType(1);
        course.setDifficulty(2);
        course.setCoverUrl("https://cdn.example.com/course/hata.jpg");
        course.setIntro("课程介绍");
        course.setCreateTime(LocalDateTime.of(2026, 9, 20, 10, 12, 33));
        course.setUpdateTime(LocalDateTime.of(2026, 9, 21, 9, 3, 11));
        return course;
    }

    private CourseDO course(Long id, String name)
    {
        CourseDO course = new CourseDO();
        course.setId(id);
        course.setName(name);
        course.setCourseType(1);
        course.setDifficulty(1);
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
}
