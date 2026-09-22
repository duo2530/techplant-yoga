package com.techplant.yoga.course.dao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.techplant.yoga.course.domain.CourseDO;
import com.techplant.yoga.course.mapper.CourseMapper;
import com.techplant.yoga.course.query.CourseQuery;

/**
 * 课程数据访问的单元测试（详细设计 §5.1.1，用例 5.1.1.22 ~ 5.1.1.26）。
 *
 * <p>课程映射器用模拟对象隔离，<b>不连数据库</b>：断言的是「交给映射器的条件与 SQL 片段」，
 * 真实 SQL 由 §5.2 的冒烟测试覆盖。</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CourseDao 单元测试")
class CourseDaoTest
{
    private static final Long COURSE_ID = 1001L;

    @Mock
    private CourseMapper courseMapper;

    @InjectMocks
    private CourseDaoImpl courseDao;

    /**
     * 初始化实体的表信息：Lambda 包装器要靠它把属性名解析成列名（coverUrl → cover_url）
     */
    @BeforeAll
    static void initTableInfo()
    {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), CourseDO.class);
    }

    @Test
    @DisplayName("5.1.1.22 分页查询：返回记录与总数（且未删除条件不由业务代码拼接）")
    void selectPage_shouldReturnRecordsAndTotal()
    {
        when(courseMapper.selectPage(any(), any())).thenReturn(pageOf(course(1L, "哈他瑜伽"), course(2L, "流瑜伽")));

        CourseQuery query = new CourseQuery();
        IPage<CourseDO> page = courseDao.selectPage(query);

        assertEquals(2L, page.getTotal());
        assertEquals(2, page.getRecords().size());

        ArgumentCaptor<IPage<CourseDO>> pageCaptor = pageCaptor();
        @SuppressWarnings("unchecked")
        ArgumentCaptor<LambdaQueryWrapper<CourseDO>> wrapperCaptor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(courseMapper).selectPage(pageCaptor.capture(), wrapperCaptor.capture());

        // 页码与每页条数来自查询条件（未传时取默认值 1 / 10）
        assertEquals(1L, pageCaptor.getValue().getCurrent());
        assertEquals(10L, pageCaptor.getValue().getSize());

        // 用例 5.1.1.8 第（3）条：未删除条件由框架的逻辑删除能力附加，业务代码不手写
        assertFalse(wrapperCaptor.getValue().getSqlSegment().contains("deleted"),
                "逻辑删除条件不应由业务代码拼接");
    }

    @Test
    @DisplayName("5.1.1.8（数据访问层落点）筛选条件与稳定排序正确落到 SQL")
    void selectPage_shouldApplyConditionsAndStableOrder()
    {
        when(courseMapper.selectPage(any(), any())).thenReturn(pageOf());

        CourseQuery query = new CourseQuery();
        query.setName("瑜伽");
        query.setType(2);
        query.setStatus(1);
        query.setPageNum(2);
        query.setPageSize(20);
        courseDao.selectPage(query);

        ArgumentCaptor<IPage<CourseDO>> pageCaptor = pageCaptor();
        @SuppressWarnings("unchecked")
        ArgumentCaptor<LambdaQueryWrapper<CourseDO>> wrapperCaptor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(courseMapper).selectPage(pageCaptor.capture(), wrapperCaptor.capture());

        String segment = wrapperCaptor.getValue().getSqlSegment();
        assertTrue(segment.contains("name"), segment);
        assertTrue(segment.contains("type"), segment);
        assertTrue(segment.contains("status"), segment);
        // 稳定排序：sort_no 升序 + id 降序兜底（§4.1.3.1、用例 5.2.1.9）
        assertTrue(segment.contains("sort_no"), segment);
        assertTrue(segment.contains("ORDER BY"), segment);
        // 列表只取列表列，不含 intro / duration_min（§4.1.3.1）
        String selectColumns = wrapperCaptor.getValue().getSqlSelect();
        assertFalse(selectColumns.contains("intro"), selectColumns);
        assertFalse(selectColumns.contains("duration_min"), selectColumns);
        assertEquals(2L, pageCaptor.getValue().getCurrent());
        assertEquals(20L, pageCaptor.getValue().getSize());
    }

    @Test
    @DisplayName("5.1.1.23 按编号查询：自动附加未删除条件")
    void selectById_shouldQueryByPrimaryKey()
    {
        CourseDO course = course(COURSE_ID, "哈他瑜伽");
        when(courseMapper.selectById(COURSE_ID)).thenReturn(course);

        CourseDO found = courseDao.selectById(COURSE_ID);

        assertNotNull(found);
        assertEquals("哈他瑜伽", found.getName());
        // 走 BaseMapper 内置方法：deleted = 0 由 @TableLogic 自动附加，业务代码不手写
        verify(courseMapper).selectById(COURSE_ID);
    }

    @Test
    @DisplayName("5.1.1.24 新增课程数据：回填生成的课程编号")
    void insert_shouldFillGeneratedId()
    {
        when(courseMapper.insert(any(CourseDO.class))).thenAnswer(invocation -> {
            CourseDO course = invocation.getArgument(0);
            course.setId(1856739201475235840L);
            return 1;
        });

        CourseDO course = new CourseDO();
        course.setName("哈他瑜伽");
        course.setStatus(1);
        course.setDeleted(0);
        course.setSortNo(0);
        int rows = courseDao.insert(course);

        assertEquals(1, rows);
        assertEquals(1856739201475235840L, course.getId());
    }

    @Test
    @DisplayName("5.1.1.25 按编号更新状态：返回影响行数并写入操作人与更新时间")
    void updateStatus_shouldWriteOperatorAndTime()
    {
        when(courseMapper.update(any(), any())).thenReturn(1);

        int rows = courseDao.updateStatus(COURSE_ID, 0, 1L);

        assertEquals(1, rows);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<LambdaUpdateWrapper<CourseDO>> captor = ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(courseMapper).update(any(), captor.capture());
        LambdaUpdateWrapper<CourseDO> wrapper = captor.getValue();
        String sqlSet = wrapper.getSqlSet();
        assertTrue(sqlSet.contains("status"), sqlSet);
        assertTrue(sqlSet.contains("update_by"), sqlSet);
        assertTrue(sqlSet.contains("update_time"), sqlSet);
        assertTrue(wrapper.getSqlSegment().contains("id"), wrapper.getSqlSegment());
        assertTrue(wrapper.getParamNameValuePairs().containsValue(0), "目标状态应写入 0（停用）");
        assertTrue(wrapper.getParamNameValuePairs().containsValue(1L), "操作人应写入 1");
        assertTrue(hasLocalDateTimeValue(wrapper), "更新时间应写入");
    }

    @Test
    @DisplayName("5.1.1.26 按编号全量更新：返回影响行数（含清空字段写 NULL）")
    void updateById_shouldSetAllColumns()
    {
        when(courseMapper.update(any(), any())).thenReturn(1);

        CourseDO course = new CourseDO();
        course.setId(COURSE_ID);
        course.setName("哈他瑜伽（初级）");
        course.setType(1);
        course.setDifficulty(3);
        course.setCoverUrl(null);
        course.setIntro(null);
        course.setDurationMin(null);
        course.setSortNo(20);
        course.setUpdateBy(1L);
        int rows = courseDao.updateById(course);

        assertEquals(1, rows);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<LambdaUpdateWrapper<CourseDO>> captor = ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(courseMapper).update(any(), captor.capture());
        LambdaUpdateWrapper<CourseDO> wrapper = captor.getValue();
        String sqlSet = wrapper.getSqlSet();
        assertTrue(sqlSet.contains("name"), sqlSet);
        assertTrue(sqlSet.contains("type"), sqlSet);
        assertTrue(sqlSet.contains("difficulty"), sqlSet);
        assertTrue(sqlSet.contains("cover_url"), sqlSet);
        assertTrue(sqlSet.contains("intro"), sqlSet);
        assertTrue(sqlSet.contains("duration_min"), sqlSet);
        assertTrue(sqlSet.contains("sort_no"), sqlSet);
        assertTrue(sqlSet.contains("update_by"), sqlSet);
        assertTrue(sqlSet.contains("update_time"), sqlSet);
        // 传 null 的字段要真的写入 NULL（清空语义），而不是被跳过
        assertTrue(wrapper.getParamNameValuePairs().containsValue(null), "可清空字段应写入 NULL");
        assertTrue(wrapper.getParamNameValuePairs().containsValue("哈他瑜伽（初级）"));
        assertTrue(wrapper.getParamNameValuePairs().containsValue(20));
    }

    @SuppressWarnings("unchecked")
    private ArgumentCaptor<IPage<CourseDO>> pageCaptor()
    {
        return (ArgumentCaptor<IPage<CourseDO>>) (ArgumentCaptor<?>) ArgumentCaptor.forClass(IPage.class);
    }

    private boolean hasLocalDateTimeValue(LambdaUpdateWrapper<CourseDO> wrapper)
    {
        for (Object value : wrapper.getParamNameValuePairs().values())
        {
            if (value instanceof LocalDateTime)
            {
                return true;
            }
        }
        return false;
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
}
