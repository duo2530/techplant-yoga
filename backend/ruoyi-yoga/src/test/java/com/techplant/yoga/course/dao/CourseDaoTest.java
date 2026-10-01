package com.techplant.yoga.course.dao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
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
 * 课程数据访问的单元测试。
 *
 * <p>映射器用模拟对象隔离，<b>不连数据库</b>：断言的是「交给映射器的条件与 SQL 片段」，
 * 真实 SQL 由 {@code MyBatisWiringTest}（H2）覆盖。</p>
 *
 * <p><b>本轮口径：</b>{@code t_course} 只有
 * {@code id / name / course_type / cover_url / intro / difficulty} ＋ 审计四列；
 * 没有 {@code store_id / status / sort_no / deleted}，<b>物理删除</b>（不加 {@code @TableLogic}）。</p>
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
     * 初始化实体的表信息：Lambda 包装器要靠它把属性名解析成列名（courseType → course_type）
     */
    @BeforeAll
    static void initTableInfo()
    {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), CourseDO.class);
    }

    @Test
    @DisplayName("分页查询：返回记录与总数，页码/每页条数来自 Query（默认 1 / 10）")
    void selectPage_shouldReturnRecordsAndTotal()
    {
        when(courseMapper.selectPage(any(), any())).thenReturn(pageOf(course(1L, "哈他瑜伽"), course(2L, "流瑜伽")));

        IPage<CourseDO> page = courseDao.selectPage(new CourseQuery());

        assertEquals(2L, page.getTotal());
        assertEquals(2, page.getRecords().size());

        ArgumentCaptor<IPage<CourseDO>> pageCaptor = pageCaptor();
        @SuppressWarnings("unchecked")
        ArgumentCaptor<LambdaQueryWrapper<CourseDO>> wrapperCaptor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(courseMapper).selectPage(pageCaptor.capture(), wrapperCaptor.capture());

        assertEquals(1L, pageCaptor.getValue().getCurrent());
        assertEquals(10L, pageCaptor.getValue().getSize());
        // 物理删除：没有 @TableLogic，SQL 里不应出现任何 deleted 条件
        assertFalse(wrapperCaptor.getValue().getSqlSegment().contains("deleted"),
                "本表没有逻辑删除列，业务代码也不该拼 deleted 条件");
    }

    @Test
    @DisplayName("筛选条件与稳定排序正确落到 SQL：name 模糊 + course_type + ORDER BY，列表列不含 intro")
    void selectPage_shouldApplyConditionsAndStableOrder()
    {
        when(courseMapper.selectPage(any(), any())).thenReturn(pageOf());

        CourseQuery query = new CourseQuery();
        query.setName("瑜伽");
        query.setCourseType(2);
        query.setPageNum(2);
        query.setPageSize(20);
        courseDao.selectPage(query);

        ArgumentCaptor<IPage<CourseDO>> pageCaptor = pageCaptor();
        @SuppressWarnings("unchecked")
        ArgumentCaptor<LambdaQueryWrapper<CourseDO>> wrapperCaptor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(courseMapper).selectPage(pageCaptor.capture(), wrapperCaptor.capture());

        String segment = wrapperCaptor.getValue().getSqlSegment();
        assertTrue(segment.contains("name"), segment);
        assertTrue(segment.contains("course_type"), segment);
        assertTrue(segment.contains("ORDER BY"), segment);
        // 稳定排序：course_type 升序 + id 降序兜底
        assertTrue(segment.contains("id DESC"), segment);
        // 列表只取列表列，不含大字段 intro
        String selectColumns = wrapperCaptor.getValue().getSqlSelect();
        assertNotNull(selectColumns);
        assertTrue(selectColumns.contains("course_type"), selectColumns);
        assertFalse(selectColumns.contains("intro"), selectColumns);
        assertEquals(2L, pageCaptor.getValue().getCurrent());
        assertEquals(20L, pageCaptor.getValue().getSize());
    }

    @Test
    @DisplayName("列表查询不拼店/状态条件：Query 里已没有 storeId / status 字段")
    void selectPage_shouldNotFilterByRemovedFields()
    {
        when(courseMapper.selectPage(any(), any())).thenReturn(pageOf());

        courseDao.selectPage(new CourseQuery());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<LambdaQueryWrapper<CourseDO>> wrapperCaptor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(courseMapper).selectPage(any(), wrapperCaptor.capture());
        String segment = wrapperCaptor.getValue().getSqlSegment();
        assertFalse(segment.contains("store_id"), segment);
        assertFalse(segment.contains("status"), segment);
        assertFalse(segment.contains("sort_no"), segment);
    }

    @Test
    @DisplayName("按编号查询：走 BaseMapper 内置方法，按主键查回一条")
    void selectById_shouldQueryByPrimaryKey()
    {
        CourseDO course = course(COURSE_ID, "哈他瑜伽");
        when(courseMapper.selectById(COURSE_ID)).thenReturn(course);

        CourseDO found = courseDao.selectById(COURSE_ID);

        assertNotNull(found);
        assertEquals("哈他瑜伽", found.getName());
        verify(courseMapper).selectById(COURSE_ID);
    }

    @Test
    @DisplayName("按名称查重：只按 name 精确匹配，不分课种、不带任何删除条件")
    void selectByName_shouldMatchNameOnly()
    {
        when(courseMapper.selectOne(any())).thenReturn(course(COURSE_ID, "哈他瑜伽"));

        CourseDO found = courseDao.selectByName("哈他瑜伽");

        assertNotNull(found);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<LambdaQueryWrapper<CourseDO>> captor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(courseMapper).selectOne(captor.capture());
        String segment = captor.getValue().getSqlSegment();
        assertTrue(segment.contains("name"), segment);
        assertFalse(segment.contains("course_type"), segment);
        assertFalse(segment.contains("deleted"), segment);
    }

    @Test
    @DisplayName("修改查重：按名称匹配并排除自身 id")
    void selectByNameExcludeId_shouldExcludeSelf()
    {
        when(courseMapper.selectOne(any())).thenReturn(null);

        courseDao.selectByNameExcludeId("哈他瑜伽（初级）", COURSE_ID);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<LambdaQueryWrapper<CourseDO>> captor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(courseMapper).selectOne(captor.capture());
        LambdaQueryWrapper<CourseDO> wrapper = captor.getValue();
        assertTrue(wrapper.getSqlSegment().contains("name"), wrapper.getSqlSegment());
        assertTrue(wrapper.getSqlSegment().contains("id"), wrapper.getSqlSegment());
        assertTrue(wrapper.getParamNameValuePairs().containsValue(COURSE_ID), "应把自身 id 作为排除条件");
    }

    @Test
    @DisplayName("批量查询：按编号集合一次查回（名称补齐用，避免 N+1）")
    void selectByIds_shouldQueryInBatch()
    {
        when(courseMapper.selectBatchIds(any())).thenReturn(Arrays.asList(course(1L, "哈他瑜伽"), course(2L, "流瑜伽")));

        List<CourseDO> list = courseDao.selectByIds(Arrays.asList(1L, 2L));

        assertEquals(2, list.size());
        verify(courseMapper).selectBatchIds(eq(Arrays.asList(1L, 2L)));
    }

    @Test
    @DisplayName("新增：回填生成的课程编号")
    void insert_shouldFillGeneratedId()
    {
        when(courseMapper.insert(any(CourseDO.class))).thenAnswer(invocation -> {
            CourseDO course = invocation.getArgument(0);
            course.setId(1856739201475235801L);
            return 1;
        });

        CourseDO course = new CourseDO();
        course.setName("哈他瑜伽");
        course.setCourseType(1);
        course.setDifficulty(2);
        int rows = courseDao.insert(course);

        assertEquals(1, rows);
        assertEquals(1856739201475235801L, course.getId());
    }

    @Test
    @DisplayName("全量更新：显式 SET 新字段，传 null 的字段真的写 NULL（清空语义）")
    void updateById_shouldSetAllColumnsAndClearNullFields()
    {
        when(courseMapper.update(any(), any())).thenReturn(1);

        CourseDO course = new CourseDO();
        course.setId(COURSE_ID);
        course.setName("哈他瑜伽（初级）");
        course.setCourseType(1);
        course.setCoverUrl(null);
        course.setIntro(null);
        course.setDifficulty(3);
        course.setUpdateBy(1L);
        int rows = courseDao.updateById(course);

        assertEquals(1, rows);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<LambdaUpdateWrapper<CourseDO>> captor = ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(courseMapper).update(any(), captor.capture());
        LambdaUpdateWrapper<CourseDO> wrapper = captor.getValue();
        String sqlSet = wrapper.getSqlSet();
        assertTrue(sqlSet.contains("name"), sqlSet);
        assertTrue(sqlSet.contains("course_type"), sqlSet);
        assertTrue(sqlSet.contains("cover_url"), sqlSet);
        assertTrue(sqlSet.contains("intro"), sqlSet);
        assertTrue(sqlSet.contains("difficulty"), sqlSet);
        assertTrue(sqlSet.contains("update_by"), sqlSet);
        assertTrue(sqlSet.contains("update_time"), sqlSet);
        // 已移除的列绝不能被拼进 SET
        assertFalse(sqlSet.contains("store_id"), sqlSet);
        assertFalse(sqlSet.contains("status"), sqlSet);
        assertFalse(sqlSet.contains("sort_no"), sqlSet);
        assertFalse(sqlSet.contains("duration_min"), sqlSet);
        // 传 null 的字段要真的写入 NULL（清空语义），而不是被跳过
        assertTrue(wrapper.getParamNameValuePairs().containsValue(null), "可清空字段应写入 NULL");
        assertTrue(wrapper.getParamNameValuePairs().containsValue("哈他瑜伽（初级）"));
        assertTrue(wrapper.getParamNameValuePairs().containsValue(3));
        assertTrue(hasLocalDateTimeValue(wrapper), "更新时间应写入");
        assertTrue(wrapper.getSqlSegment().contains("id"), wrapper.getSqlSegment());
    }

    @Test
    @DisplayName("删除：物理删除，走 BaseMapper.deleteById（不产生 UPDATE ... deleted = 1）")
    void deleteById_shouldCallMapperDelete()
    {
        when(courseMapper.deleteById(COURSE_ID)).thenReturn(1);

        int rows = courseDao.deleteById(COURSE_ID);

        assertEquals(1, rows);
        verify(courseMapper).deleteById(COURSE_ID);
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
