package com.techplant.yoga.classroom.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
import com.techplant.yoga.classroom.dao.ClassroomDao;
import com.techplant.yoga.classroom.domain.ClassroomDO;
import com.techplant.yoga.classroom.dto.ClassroomCreateDTO;
import com.techplant.yoga.classroom.dto.ClassroomUpdateDTO;
import com.techplant.yoga.classroom.query.ClassroomQuery;
import com.techplant.yoga.classroom.service.impl.ClassroomServiceImpl;
import com.techplant.yoga.classroom.vo.ClassroomListItemVO;
import com.techplant.yoga.classroom.vo.ClassroomSummaryVO;
import com.techplant.yoga.common.response.PageResult;
import com.techplant.yoga.schedule.service.ScheduleService;
import com.techplant.yoga.store.service.StoreService;
import com.techplant.yoga.store.vo.StoreDetailVO;
import com.techplant.yoga.store.vo.StoreSummaryVO;

/**
 * 教室业务实现的单元测试（教室管理详细设计 §4）。
 *
 * <p>依赖（教室数据访问、门店服务、排课统计）全部用模拟对象隔离，不连数据库。</p>
 *
 * <p>重点覆盖：<b>改名绝不动 store_id</b>、同门店重名、<b>物理删除 + 排课门禁（fail-closed）</b>、
 * 跨模块冻结方法（countByStoreId / validateExistsAndBelongsToStore / summaries）。</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ClassroomServiceImpl 单元测试")
class ClassroomServiceImplTest
{
    private static final Long CLASSROOM_ID = 2001L;

    private static final Long STORE_ID = 1856739201475235901L;

    private static final Long OPERATOR_ID = 1L;

    @Mock
    private ClassroomDao classroomDao;

    @Mock
    private StoreService storeService;

    @Mock
    private ScheduleService scheduleService;

    @InjectMocks
    private ClassroomServiceImpl classroomService;

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
    // 列表
    // ------------------------------------------------------------------

    @Test
    @DisplayName("查询列表：分页返回，storeName 由 StoreService 批量补齐（不跨模块读表）")
    void page_shouldFillStoreNameFromStoreService()
    {
        when(classroomDao.selectPage(any(ClassroomQuery.class)))
                .thenReturn(pageOf(classroom(CLASSROOM_ID, STORE_ID, "瑜伽团课大教室")));
        StoreSummaryVO store = new StoreSummaryVO();
        store.setId(STORE_ID);
        store.setName("徐汇店");
        when(storeService.summaries(any())).thenReturn(Collections.singletonList(store));

        PageResult<ClassroomListItemVO> result = classroomService.page(new ClassroomQuery());

        assertEquals(Long.valueOf(1L), result.getTotal());
        assertEquals("瑜伽团课大教室", result.getList().get(0).getName());
        assertEquals(STORE_ID, result.getList().get(0).getStoreId());
        assertEquals("徐汇店", result.getList().get(0).getStoreName());
        // 门店编号先去重再批量查（单条时也只有一次调用）
        verify(storeService, times(1)).summaries(any());
    }

    @Test
    @DisplayName("查询列表：空结果不调门店服务")
    void page_withEmptyResult_shouldNotCallStoreService()
    {
        when(classroomDao.selectPage(any(ClassroomQuery.class))).thenReturn(pageOf());

        PageResult<ClassroomListItemVO> result = classroomService.page(new ClassroomQuery());

        assertTrue(result.getList().isEmpty());
        verify(storeService, never()).summaries(any());
    }

    // ------------------------------------------------------------------
    // 新增
    // ------------------------------------------------------------------

    @Test
    @DisplayName("新增教室：门店存在且同门店无重名 → 落库成功，不返回新 ID")
    void create_shouldInsert()
    {
        when(storeService.getById(STORE_ID)).thenReturn(new StoreDetailVO());
        when(classroomDao.selectByStoreIdAndName(STORE_ID, "普拉提器械教室")).thenReturn(null);
        when(classroomDao.insert(any(ClassroomDO.class))).thenReturn(1);

        ClassroomCreateDTO dto = new ClassroomCreateDTO();
        dto.setStoreId(STORE_ID);
        dto.setName("普拉提器械教室");
        classroomService.create(dto);

        ArgumentCaptor<ClassroomDO> captor = ArgumentCaptor.forClass(ClassroomDO.class);
        verify(classroomDao).insert(captor.capture());
        ClassroomDO saved = captor.getValue();
        assertEquals(STORE_ID, saved.getStoreId());
        assertEquals("普拉提器械教室", saved.getName());
        // 主键由框架生成
        assertEquals(null, saved.getId());
    }

    @Test
    @DisplayName("新增教室：门店不存在（StoreService 抛 404）→ 不落库")
    void create_withMissingStore_shouldPropagate404()
    {
        when(storeService.getById(STORE_ID)).thenThrow(new ServiceException("门店不存在或已被删除", 404));

        ClassroomCreateDTO dto = new ClassroomCreateDTO();
        dto.setStoreId(STORE_ID);
        dto.setName("普拉提器械教室");

        ServiceException exception = assertThrows(ServiceException.class, () -> classroomService.create(dto));

        assertEquals(404, exception.getCode().intValue());
        assertEquals("门店不存在或已被删除", exception.getMessage());
        verify(classroomDao, never()).insert(any(ClassroomDO.class));
    }

    @Test
    @DisplayName("新增教室：门店服务返回 null 也按 404 处理（防御性兜底）")
    void create_withNullStore_shouldThrow404()
    {
        when(storeService.getById(STORE_ID)).thenReturn(null);

        ClassroomCreateDTO dto = new ClassroomCreateDTO();
        dto.setStoreId(STORE_ID);
        dto.setName("普拉提器械教室");

        ServiceException exception = assertThrows(ServiceException.class, () -> classroomService.create(dto));

        assertEquals(404, exception.getCode().intValue());
        verify(classroomDao, never()).insert(any(ClassroomDO.class));
    }

    @Test
    @DisplayName("新增教室：同门店内重名 → 409「该门店下已存在同名教室」，不落库")
    void create_withDuplicateName_shouldReject()
    {
        when(storeService.getById(STORE_ID)).thenReturn(new StoreDetailVO());
        when(classroomDao.selectByStoreIdAndName(STORE_ID, "瑜伽团课大教室"))
                .thenReturn(classroom(3001L, STORE_ID, "瑜伽团课大教室"));

        ClassroomCreateDTO dto = new ClassroomCreateDTO();
        dto.setStoreId(STORE_ID);
        dto.setName("瑜伽团课大教室");

        ServiceException exception = assertThrows(ServiceException.class, () -> classroomService.create(dto));

        assertEquals(409, exception.getCode().intValue());
        assertEquals("该门店下已存在同名教室", exception.getMessage());
        verify(classroomDao, never()).insert(any(ClassroomDO.class));
    }

    // ------------------------------------------------------------------
    // 修改（只改名称）
    // ------------------------------------------------------------------

    @Test
    @DisplayName("修改教室：只更新 name，store_id 不被触碰（DTO 里没有 storeId）")
    void update_shouldOnlyUpdateName()
    {
        ClassroomDO existing = classroom(CLASSROOM_ID, STORE_ID, "瑜伽团课大教室");
        when(classroomDao.selectById(CLASSROOM_ID)).thenReturn(existing);
        when(classroomDao.selectByStoreIdAndNameExcludeId(STORE_ID, "普拉提小班课", CLASSROOM_ID)).thenReturn(null);
        when(classroomDao.updateName(CLASSROOM_ID, "普拉提小班课", OPERATOR_ID)).thenReturn(1);

        ClassroomUpdateDTO dto = new ClassroomUpdateDTO();
        dto.setName("  普拉提小班课  ");
        classroomService.update(CLASSROOM_ID, dto);

        // 显式只 set name（空格已去除）；没有任何整对象覆盖
        verify(classroomDao).updateName(CLASSROOM_ID, "普拉提小班课", OPERATOR_ID);
        verify(classroomDao, never()).insert(any(ClassroomDO.class));
        // 改名不加排课门禁：一次统计都不该发生
        verify(scheduleService, never()).countActiveByClassroomId(anyLong());
    }

    @Test
    @DisplayName("修改教室：教室不存在 → 404「教室不存在或已被删除」")
    void update_notExists_shouldThrow404()
    {
        when(classroomDao.selectById(CLASSROOM_ID)).thenReturn(null);

        ClassroomUpdateDTO dto = new ClassroomUpdateDTO();
        dto.setName("普拉提小班课");

        ServiceException exception = assertThrows(ServiceException.class, () -> classroomService.update(CLASSROOM_ID, dto));

        assertEquals(404, exception.getCode().intValue());
        assertEquals("教室不存在或已被删除", exception.getMessage());
        verify(classroomDao, never()).updateName(any(), any(), any());
    }

    @Test
    @DisplayName("修改教室：同门店内与其它教室重名 → 409，不更新")
    void update_withDuplicateName_shouldReject()
    {
        when(classroomDao.selectById(CLASSROOM_ID)).thenReturn(classroom(CLASSROOM_ID, STORE_ID, "瑜伽团课大教室"));
        when(classroomDao.selectByStoreIdAndNameExcludeId(STORE_ID, "普拉提器械教室", CLASSROOM_ID))
                .thenReturn(classroom(3002L, STORE_ID, "普拉提器械教室"));

        ClassroomUpdateDTO dto = new ClassroomUpdateDTO();
        dto.setName("普拉提器械教室");

        ServiceException exception = assertThrows(ServiceException.class, () -> classroomService.update(CLASSROOM_ID, dto));

        assertEquals(409, exception.getCode().intValue());
        assertEquals("该门店下已存在同名教室", exception.getMessage());
        verify(classroomDao, never()).updateName(any(), any(), any());
    }

    // ------------------------------------------------------------------
    // 删除（物理 + 门禁）
    // ------------------------------------------------------------------

    @Test
    @DisplayName("删除教室：无未结束排课 → 物理删除成功")
    void delete_withoutReference_shouldPhysicallyDelete()
    {
        when(classroomDao.selectById(CLASSROOM_ID)).thenReturn(classroom(CLASSROOM_ID, STORE_ID, "瑜伽团课大教室"));
        when(scheduleService.countActiveByClassroomId(CLASSROOM_ID)).thenReturn(0L);
        when(classroomDao.deleteById(CLASSROOM_ID)).thenReturn(1);

        classroomService.delete(CLASSROOM_ID);

        verify(classroomDao, times(1)).deleteById(CLASSROOM_ID);
    }

    @Test
    @DisplayName("删除教室：仍有未结束排课 → 409「该教室仍有 N 节未结束的排课，无法删除」，不删除")
    void delete_withActiveSchedule_shouldReject()
    {
        when(classroomDao.selectById(CLASSROOM_ID)).thenReturn(classroom(CLASSROOM_ID, STORE_ID, "瑜伽团课大教室"));
        when(scheduleService.countActiveByClassroomId(CLASSROOM_ID)).thenReturn(2L);

        ServiceException exception = assertThrows(ServiceException.class, () -> classroomService.delete(CLASSROOM_ID));

        assertEquals(409, exception.getCode().intValue());
        assertEquals("该教室仍有 2 节未结束的排课，无法删除", exception.getMessage());
        verify(classroomDao, never()).deleteById(anyLong());
    }

    @Test
    @DisplayName("删除教室：统计抛异常 → fail-closed，409「引用检查未完成，已拒绝本次删除」，不删除")
    void delete_whenReferenceCheckFails_shouldFailClosed()
    {
        when(classroomDao.selectById(CLASSROOM_ID)).thenReturn(classroom(CLASSROOM_ID, STORE_ID, "瑜伽团课大教室"));
        when(scheduleService.countActiveByClassroomId(CLASSROOM_ID)).thenThrow(new RuntimeException("统计服务不可用"));

        ServiceException exception = assertThrows(ServiceException.class, () -> classroomService.delete(CLASSROOM_ID));

        assertEquals(409, exception.getCode().intValue());
        assertEquals("引用检查未完成，已拒绝本次删除", exception.getMessage());
        verify(classroomDao, never()).deleteById(anyLong());
    }

    @Test
    @DisplayName("删除教室：教室不存在 → 404，不统计、不删除")
    void delete_notExists_shouldThrow404()
    {
        when(classroomDao.selectById(CLASSROOM_ID)).thenReturn(null);

        ServiceException exception = assertThrows(ServiceException.class, () -> classroomService.delete(CLASSROOM_ID));

        assertEquals(404, exception.getCode().intValue());
        verify(scheduleService, never()).countActiveByClassroomId(anyLong());
        verify(classroomDao, never()).deleteById(anyLong());
    }

    // ------------------------------------------------------------------
    // 跨模块冻结方法
    // ------------------------------------------------------------------

    @Test
    @DisplayName("countByStoreId：透传 DAO 统计结果（门店删除门禁用）")
    void countByStoreId_shouldDelegate()
    {
        when(classroomDao.countByStoreId(STORE_ID)).thenReturn(3L);

        assertEquals(3L, classroomService.countByStoreId(STORE_ID));
        assertEquals(0L, classroomService.countByStoreId(null));
        verify(classroomDao, never()).countByStoreId(null);
    }

    @Test
    @DisplayName("validateExistsAndBelongsToStore：教室不存在 → 404")
    void validate_notExists_shouldThrow404()
    {
        when(classroomDao.selectById(CLASSROOM_ID)).thenReturn(null);

        ServiceException exception = assertThrows(ServiceException.class,
                () -> classroomService.validateExistsAndBelongsToStore(CLASSROOM_ID, STORE_ID));

        assertEquals(404, exception.getCode().intValue());
        assertEquals("教室不存在或已被删除", exception.getMessage());
    }

    @Test
    @DisplayName("validateExistsAndBelongsToStore：归属不符 → 409「所选教室不属于该门店」")
    void validate_storeMismatch_shouldThrow409()
    {
        when(classroomDao.selectById(CLASSROOM_ID)).thenReturn(classroom(CLASSROOM_ID, STORE_ID, "瑜伽团课大教室"));

        ServiceException exception = assertThrows(ServiceException.class,
                () -> classroomService.validateExistsAndBelongsToStore(CLASSROOM_ID, 99999L));

        assertEquals(409, exception.getCode().intValue());
        assertEquals("所选教室不属于该门店", exception.getMessage());
    }

    @Test
    @DisplayName("validateExistsAndBelongsToStore：归属一致 → 通过")
    void validate_ok()
    {
        when(classroomDao.selectById(CLASSROOM_ID)).thenReturn(classroom(CLASSROOM_ID, STORE_ID, "瑜伽团课大教室"));

        classroomService.validateExistsAndBelongsToStore(CLASSROOM_ID, STORE_ID);
    }

    @Test
    @DisplayName("summaries：去重后批量查回摘要（排课模块补齐名称用）")
    void summaries_shouldDeduplicate()
    {
        when(classroomDao.selectByIds(any()))
                .thenReturn(Arrays.asList(classroom(1L, STORE_ID, "大教室"), classroom(2L, STORE_ID, "小教室")));

        List<ClassroomSummaryVO> list = classroomService.summaries(Arrays.asList(1L, 2L, 1L, null));

        assertEquals(2, list.size());
        assertEquals(STORE_ID, list.get(0).getStoreId());
        assertEquals("大教室", list.get(0).getName());
        ArgumentCaptor<java.util.Collection<Long>> captor = ArgumentCaptor.forClass(java.util.Collection.class);
        verify(classroomDao).selectByIds(captor.capture());
        assertEquals(2, captor.getValue().size());
    }

    @Test
    @DisplayName("summaries：入参为空返回空列表，不查库")
    void summaries_withEmptyIds_shouldReturnEmpty()
    {
        assertTrue(classroomService.summaries(null).isEmpty());
        assertTrue(classroomService.summaries(Collections.<Long>emptyList()).isEmpty());
        verify(classroomDao, never()).selectByIds(any());
    }

    // ------------------------------------------------------------------
    // 测试数据与工具
    // ------------------------------------------------------------------

    private ClassroomDO classroom(Long id, Long storeId, String name)
    {
        ClassroomDO classroom = new ClassroomDO();
        classroom.setId(id);
        classroom.setStoreId(storeId);
        classroom.setName(name);
        classroom.setCreateTime(LocalDateTime.of(2026, 10, 1, 10, 0, 0));
        classroom.setUpdateTime(LocalDateTime.of(2026, 10, 1, 10, 0, 0));
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
