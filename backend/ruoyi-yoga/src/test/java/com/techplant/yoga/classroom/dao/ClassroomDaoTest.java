package com.techplant.yoga.classroom.dao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
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
import com.techplant.yoga.classroom.domain.ClassroomDO;
import com.techplant.yoga.classroom.mapper.ClassroomMapper;
import com.techplant.yoga.classroom.query.ClassroomQuery;

/**
 * 教室数据访问的单元测试。
 *
 * <p>映射器用模拟对象隔离，不连数据库。重点：稳定排序
 * {@code store_id ASC, name ASC, id DESC}、<b>改名只 SET name</b>（绝不碰 store_id）、
 * <b>物理删除</b>（没有 deleted 条件）。</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ClassroomDao 单元测试")
class ClassroomDaoTest
{
    private static final Long CLASSROOM_ID = 2001L;

    private static final Long STORE_ID = 1856739201475235901L;

    @Mock
    private ClassroomMapper classroomMapper;

    @InjectMocks
    private ClassroomDaoImpl classroomDao;

    @BeforeAll
    static void initTableInfo()
    {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), ClassroomDO.class);
    }

    @Test
    @DisplayName("分页查询：页码/每页条数取默认值，稳定排序与筛选条件落到 SQL，且没有 deleted 条件")
    void selectPage_shouldApplyStableOrder()
    {
        when(classroomMapper.selectPage(any(), any())).thenReturn(pageOf(classroom(1L, STORE_ID, "大教室")));

        ClassroomQuery query = new ClassroomQuery();
        query.setStoreId(STORE_ID);
        query.setName("教室");
        IPage<ClassroomDO> page = classroomDao.selectPage(query);

        assertEquals(1, page.getRecords().size());
        ArgumentCaptor<IPage<ClassroomDO>> pageCaptor = pageCaptor();
        @SuppressWarnings("unchecked")
        ArgumentCaptor<LambdaQueryWrapper<ClassroomDO>> wrapperCaptor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(classroomMapper).selectPage(pageCaptor.capture(), wrapperCaptor.capture());

        String segment = wrapperCaptor.getValue().getSqlSegment();
        assertTrue(segment.contains("store_id"), segment);
        assertTrue(segment.contains("name"), segment);
        assertTrue(segment.contains("ORDER BY"), segment);
        // store_id ASC, name ASC, id DESC
        assertTrue(segment.contains("store_id ASC"), segment);
        assertTrue(segment.contains("name ASC"), segment);
        assertTrue(segment.contains("id DESC"), segment);
        assertFalse(segment.contains("deleted"), "本表没有逻辑删除列");
        assertEquals(1L, pageCaptor.getValue().getCurrent());
        assertEquals(10L, pageCaptor.getValue().getSize());
    }

    @Test
    @DisplayName("新增：回填雪花ID")
    void insert_shouldFillGeneratedId()
    {
        when(classroomMapper.insert(any(ClassroomDO.class))).thenAnswer(invocation -> {
            ClassroomDO classroom = invocation.getArgument(0);
            classroom.setId(1856739201475236001L);
            return 1;
        });

        ClassroomDO classroom = new ClassroomDO();
        classroom.setStoreId(STORE_ID);
        classroom.setName("普拉提器械教室");
        int rows = classroomDao.insert(classroom);

        assertEquals(1, rows);
        assertEquals(1856739201475236001L, classroom.getId());
    }

    @Test
    @DisplayName("改名：SET 列表只有 name（+ 审计），store_id 绝不出现")
    void updateName_shouldOnlySetName()
    {
        when(classroomMapper.update(any(), any())).thenReturn(1);

        int rows = classroomDao.updateName(CLASSROOM_ID, "普拉提小班课", 1L);

        assertEquals(1, rows);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<LambdaUpdateWrapper<ClassroomDO>> captor = ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(classroomMapper).update(any(), captor.capture());
        LambdaUpdateWrapper<ClassroomDO> wrapper = captor.getValue();
        String sqlSet = wrapper.getSqlSet();
        assertTrue(sqlSet.contains("name"), sqlSet);
        assertTrue(sqlSet.contains("update_by"), sqlSet);
        assertTrue(sqlSet.contains("update_time"), sqlSet);
        assertFalse(sqlSet.contains("store_id"), "改名绝不能把 store_id 写空：" + sqlSet);
        assertTrue(wrapper.getSqlSegment().contains("id"), wrapper.getSqlSegment());
        assertTrue(wrapper.getParamNameValuePairs().containsValue("普拉提小班课"));
        assertTrue(hasLocalDateTimeValue(wrapper), "更新时间应写入");
    }

    @Test
    @DisplayName("删除：物理删除，走 BaseMapper.deleteById")
    void deleteById_shouldCallMapperDelete()
    {
        when(classroomMapper.deleteById(CLASSROOM_ID)).thenReturn(1);

        assertEquals(1, classroomDao.deleteById(CLASSROOM_ID));
        verify(classroomMapper).deleteById(CLASSROOM_ID);
    }

    @Test
    @DisplayName("统计门店名下教室数：selectCount 带 store_id 条件")
    void countByStoreId_shouldCount()
    {
        when(classroomMapper.selectCount(any())).thenReturn(4L);

        assertEquals(4L, classroomDao.countByStoreId(STORE_ID));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<LambdaQueryWrapper<ClassroomDO>> captor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(classroomMapper).selectCount(captor.capture());
        assertTrue(captor.getValue().getSqlSegment().contains("store_id"), captor.getValue().getSqlSegment());
    }

    @Test
    @DisplayName("查重：按 store_id + name，修改时排除自身")
    void selectByStoreIdAndNameExcludeId_shouldExcludeSelf()
    {
        when(classroomMapper.selectOne(any())).thenReturn(null);

        classroomDao.selectByStoreIdAndNameExcludeId(STORE_ID, "普拉提小班课", CLASSROOM_ID);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<LambdaQueryWrapper<ClassroomDO>> captor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(classroomMapper).selectOne(captor.capture());
        LambdaQueryWrapper<ClassroomDO> wrapper = captor.getValue();
        assertTrue(wrapper.getSqlSegment().contains("store_id"), wrapper.getSqlSegment());
        assertTrue(wrapper.getSqlSegment().contains("name"), wrapper.getSqlSegment());
        assertTrue(wrapper.getParamNameValuePairs().containsValue(STORE_ID));
        assertTrue(wrapper.getParamNameValuePairs().containsValue(CLASSROOM_ID));
    }

    @SuppressWarnings("unchecked")
    private ArgumentCaptor<IPage<ClassroomDO>> pageCaptor()
    {
        return (ArgumentCaptor<IPage<ClassroomDO>>) (ArgumentCaptor<?>) ArgumentCaptor.forClass(IPage.class);
    }

    private boolean hasLocalDateTimeValue(LambdaUpdateWrapper<ClassroomDO> wrapper)
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

    private ClassroomDO classroom(Long id, Long storeId, String name)
    {
        ClassroomDO classroom = new ClassroomDO();
        classroom.setId(id);
        classroom.setStoreId(storeId);
        classroom.setName(name);
        return classroom;
    }

    private IPage<ClassroomDO> pageOf(ClassroomDO... classrooms)
    {
        List<ClassroomDO> records = new ArrayList<ClassroomDO>();
        for (ClassroomDO classroom : classrooms)
        {
            records.add(classroom);
        }
        Page<ClassroomDO> page = new Page<ClassroomDO>(1, 10);
        page.setRecords(records);
        page.setTotal(records.size());
        return page;
    }
}
