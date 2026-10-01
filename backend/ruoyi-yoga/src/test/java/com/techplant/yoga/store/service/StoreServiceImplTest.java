package com.techplant.yoga.store.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ruoyi.common.core.domain.model.LoginUser;
import com.ruoyi.common.exception.ServiceException;
import com.techplant.yoga.classroom.service.ClassroomService;
import com.techplant.yoga.common.response.PageResult;
import com.techplant.yoga.schedule.service.ScheduleService;
import com.techplant.yoga.store.dao.StoreDao;
import com.techplant.yoga.store.dao.StoreRegionDictDao;
import com.techplant.yoga.store.domain.StoreDO;
import com.techplant.yoga.store.dto.StoreCreateDTO;
import com.techplant.yoga.store.dto.StoreUpdateDTO;
import com.techplant.yoga.store.query.StorePublicQuery;
import com.techplant.yoga.store.query.StoreQuery;
import com.techplant.yoga.store.service.impl.StoreServiceImpl;
import com.techplant.yoga.store.vo.StoreDetailVO;
import com.techplant.yoga.store.vo.StoreListItemVO;
import com.techplant.yoga.store.vo.StorePublicItemVO;
import com.techplant.yoga.store.vo.StoreSummaryVO;

/**
 * 门店业务实现的单元测试（门店管理详细设计 §2.2、§4）。
 *
 * <p>依赖（门店数据访问、区域字典、教室统计、排课统计）全部用模拟对象隔离，<b>不连数据库、不连 Redis</b>。</p>
 *
 * <p><b>覆盖口径：</b>名称唯一（新增／修改排除自身）、regionCode 走字典校验、枚举成对返回
 * （storeType + storeTypeName、regionCode + regionName）、<b>删除门禁四分支</b>
 * （404 / 教室数 / 未结束排课数 / 统计异常 fail-closed）、summaries 的去重与空集合。</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("StoreServiceImpl 单元测试")
class StoreServiceImplTest
{
    /** 用例里的门店编号 */
    private static final Long STORE_ID = 1001L;

    /** 另一个门店编号（重名冲突用） */
    private static final Long OTHER_STORE_ID = 1002L;

    /** 用例里的操作人编号 */
    private static final Long OPERATOR_ID = 1L;

    /** 字典 store_region 中的合法 code */
    private static final String REGION_XUHUI = "310104";

    /** 字典 store_region 中的另一个合法 code */
    private static final String REGION_JINGAN = "310106";

    @Mock
    private StoreDao storeDao;

    @Mock
    private StoreRegionDictDao storeRegionDictDao;

    @Mock
    private ClassroomService classroomService;

    @Mock
    private ScheduleService scheduleService;

    @InjectMocks
    private StoreServiceImpl storeService;

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
    // 管理端列表：枚举成对返回 + regionCode 走字典
    // ------------------------------------------------------------------

    @Test
    @DisplayName("列表：门店类型与所在区域都成对返回（storeTypeName / regionName 由后端补）")
    void page_shouldReturnEnumPairs()
    {
        when(storeRegionDictDao.selectRegionLabelMap()).thenReturn(regionDict());
        Page<StoreDO> page = new Page<StoreDO>(1, 10);
        page.setTotal(1L);
        page.setRecords(Collections.singletonList(store(STORE_ID, REGION_XUHUI)));
        when(storeDao.selectPage(any(StoreQuery.class))).thenReturn(page);

        PageResult<StoreListItemVO> result = storeService.page(new StoreQuery());

        assertEquals(Long.valueOf(1L), result.getTotal());
        assertEquals(1, result.getList().size());
        StoreListItemVO row = result.getList().get(0);
        assertEquals(Integer.valueOf(1), row.getStoreType());
        assertEquals("主力店", row.getStoreTypeName(), "门店类型码与名称必须成对返回");
        assertEquals(REGION_XUHUI, row.getRegionCode());
        assertEquals("徐汇区", row.getRegionName(), "区名必须由字典 store_region 翻译");
    }

    @Test
    @DisplayName("列表：regionCode 不在字典 store_region 中 → 500「所在区域取值非法」，不查库")
    void page_withUnknownRegionCode_shouldRejectWithoutQuery()
    {
        when(storeRegionDictDao.selectRegionLabelMap()).thenReturn(regionDict());
        StoreQuery query = new StoreQuery();
        query.setRegionCode("999999");

        ServiceException exception = assertThrows(ServiceException.class, () -> storeService.page(query));

        assertEquals(500, exception.getCode().intValue());
        assertEquals("所在区域取值非法", exception.getMessage());
        verifyNoInteractions(storeDao);
    }

    // ------------------------------------------------------------------
    // 用户端列表
    // ------------------------------------------------------------------

    @Test
    @DisplayName("用户端列表：只给展示字段，imageUrl 为空时兜底成空字符串")
    void publicPage_shouldReturnPublicItems()
    {
        when(storeRegionDictDao.selectRegionLabelMap()).thenReturn(regionDict());
        StoreDO store = store(STORE_ID, REGION_XUHUI);
        store.setImageUrl(null);
        Page<StoreDO> page = new Page<StoreDO>(1, 10);
        page.setTotal(1L);
        page.setRecords(Collections.singletonList(store));
        when(storeDao.selectPublicPage(any(StorePublicQuery.class))).thenReturn(page);

        StorePublicQuery query = new StorePublicQuery();
        query.setKeyword(" 徐汇 ");
        PageResult<StorePublicItemVO> result = storeService.publicPage(query);

        assertEquals(Long.valueOf(1L), result.getTotal());
        StorePublicItemVO row = result.getList().get(0);
        assertEquals("徐汇店", row.getName());
        assertEquals("主力店", row.getStoreTypeName());
        assertEquals(REGION_XUHUI, row.getRegionCode());
        assertEquals("徐汇区", row.getRegionName());
        assertEquals("", row.getImageUrl(), "空 imageUrl 必须返回空字符串而不是 null");
    }

    @Test
    @DisplayName("用户端列表：regionCode 越界 → 500「所在区域取值非法」，不查库")
    void publicPage_withUnknownRegionCode_shouldReject()
    {
        when(storeRegionDictDao.selectRegionLabelMap()).thenReturn(regionDict());
        StorePublicQuery query = new StorePublicQuery();
        query.setRegionCode("310199");

        ServiceException exception = assertThrows(ServiceException.class, () -> storeService.publicPage(query));

        assertEquals(500, exception.getCode().intValue());
        assertEquals("所在区域取值非法", exception.getMessage());
        verifyNoInteractions(storeDao);
    }

    // ------------------------------------------------------------------
    // 新增门店
    // ------------------------------------------------------------------

    @Test
    @DisplayName("新增：名称已存在 → 409「门店名称已存在」，且不调用 insert")
    void create_withDuplicateName_shouldRejectAndNotInsert()
    {
        when(storeRegionDictDao.selectRegionLabelMap()).thenReturn(regionDict());
        when(storeDao.selectByName("徐汇店")).thenReturn(store(OTHER_STORE_ID, REGION_XUHUI));

        ServiceException exception = assertThrows(ServiceException.class,
                () -> storeService.create(createDTO("徐汇店", REGION_XUHUI, 1)));

        assertEquals(409, exception.getCode().intValue());
        assertEquals("门店名称已存在", exception.getMessage());
        verify(storeDao, never()).insert(any(StoreDO.class));
    }

    @Test
    @DisplayName("新增：regionCode 不在字典 store_region 中 → 500「所在区域取值非法」，不落库")
    void create_withRegionCodeNotInDictionary_shouldReject()
    {
        when(storeRegionDictDao.selectRegionLabelMap()).thenReturn(regionDict());

        ServiceException exception = assertThrows(ServiceException.class,
                () -> storeService.create(createDTO("新店", "999999", 1)));

        assertEquals(500, exception.getCode().intValue());
        assertEquals("所在区域取值非法", exception.getMessage());
        verifyNoInteractions(storeDao);
    }

    @Test
    @DisplayName("新增：门店类型越界 → 500「门店类型取值为 1 或 2」，既不查库也不查字典")
    void create_withInvalidStoreType_shouldReject()
    {
        ServiceException exception = assertThrows(ServiceException.class,
                () -> storeService.create(createDTO("新店", REGION_XUHUI, 9)));

        assertEquals(500, exception.getCode().intValue());
        assertEquals("门店类型取值为 1 或 2", exception.getMessage());
        verifyNoInteractions(storeDao);
        verifyNoInteractions(storeRegionDictDao);
    }

    @Test
    @DisplayName("新增：参数合法 → 只落库一条、字段去空格、审计字段不由 service 赋值")
    void create_shouldInsertTrimmedStore()
    {
        when(storeRegionDictDao.selectRegionLabelMap()).thenReturn(regionDict());
        when(storeDao.selectByName(anyString())).thenReturn(null);
        // 模拟 MyBatis-Plus 的 IdType.ASSIGN_ID：insert 时回填雪花主键
        when(storeDao.insert(any(StoreDO.class))).thenAnswer(invocation -> {
            StoreDO toInsert = invocation.getArgument(0);
            toInsert.setId(STORE_ID);
            return 1;
        });
        StoreCreateDTO dto = createDTO(" 徐汇店 ", " 310104 ", 2);
        dto.setImageUrl(" https://cdn.example.com/store/xuhui.jpg ");

        storeService.create(dto);

        ArgumentCaptor<StoreDO> captor = ArgumentCaptor.forClass(StoreDO.class);
        verify(storeDao).insert(captor.capture());
        StoreDO saved = captor.getValue();
        assertEquals("徐汇店", saved.getName());
        assertEquals(REGION_XUHUI, saved.getRegionCode());
        assertEquals(Integer.valueOf(2), saved.getStoreType());
        assertEquals("021-12345678", saved.getPhone());
        assertEquals("周一至周日 09:00-22:00", saved.getBusinessHours());
        assertEquals("漕溪北路 88 号", saved.getAddress());
        assertEquals("https://cdn.example.com/store/xuhui.jpg", saved.getImageUrl());
        // 审计字段由 MetaObjectHandler 填充，service 不赋值
        assertNull(saved.getCreateBy());
        assertNull(saved.getUpdateTime());
    }

    @Test
    @DisplayName("新增：落库未生效（影响行数 0）→ 500「新增门店失败」")
    void create_whenInsertNotEffective_shouldThrow()
    {
        when(storeRegionDictDao.selectRegionLabelMap()).thenReturn(regionDict());
        when(storeDao.selectByName(anyString())).thenReturn(null);
        when(storeDao.insert(any(StoreDO.class))).thenReturn(0);

        ServiceException exception = assertThrows(ServiceException.class,
                () -> storeService.create(createDTO("徐汇店", REGION_XUHUI, 1)));

        assertEquals(500, exception.getCode().intValue());
        assertEquals("新增门店失败", exception.getMessage());
    }

    // ------------------------------------------------------------------
    // 修改门店
    // ------------------------------------------------------------------

    @Test
    @DisplayName("修改：门店不存在 → 404「门店不存在或已被删除」，不更新、不做任何统计")
    void update_notExists_shouldThrow404()
    {
        when(storeDao.selectById(STORE_ID)).thenReturn(null);

        ServiceException exception = assertThrows(ServiceException.class,
                () -> storeService.update(STORE_ID, updateDTO("徐汇店", REGION_XUHUI, 1)));

        assertEquals(404, exception.getCode().intValue());
        assertEquals("门店不存在或已被删除", exception.getMessage());
        verify(storeDao, never()).updateById(any(StoreDO.class));
        verifyNoInteractions(classroomService);
        verifyNoInteractions(scheduleService);
    }

    @Test
    @DisplayName("修改：名称与其它门店重复 → 409，且不更新")
    void update_withNameOfOtherStore_shouldReject()
    {
        when(storeDao.selectById(STORE_ID)).thenReturn(store(STORE_ID, REGION_XUHUI));
        when(storeRegionDictDao.selectRegionLabelMap()).thenReturn(regionDict());
        StoreDO other = store(OTHER_STORE_ID, REGION_JINGAN);
        other.setName("静安店");
        when(storeDao.selectByName("静安店")).thenReturn(other);

        ServiceException exception = assertThrows(ServiceException.class,
                () -> storeService.update(STORE_ID, updateDTO("静安店", REGION_JINGAN, 2)));

        assertEquals(409, exception.getCode().intValue());
        assertEquals("门店名称已存在", exception.getMessage());
        verify(storeDao, never()).updateById(any(StoreDO.class));
    }

    @Test
    @DisplayName("修改：沿用自身名称 → 放行（排除自身），返回更新后的详情（枚举成对）")
    void update_withOwnName_shouldBeAllowed()
    {
        StoreDO existing = store(STORE_ID, REGION_XUHUI);
        StoreDO latest = store(STORE_ID, REGION_XUHUI);
        latest.setStoreType(2);
        latest.setPhone("021-87654321");
        when(storeDao.selectById(STORE_ID)).thenReturn(existing, latest);
        when(storeRegionDictDao.selectRegionLabelMap()).thenReturn(regionDict());
        // 查重命中的是自己 → 必须放行（名称前后空格由转换层去掉，这里用 anyString 避免耦合查库时的入参形态）
        when(storeDao.selectByName(anyString())).thenReturn(existing);
        when(storeDao.updateById(any(StoreDO.class))).thenReturn(1);

        StoreDetailVO detail = storeService.update(STORE_ID, updateDTO(" 徐汇店 ", REGION_XUHUI, 2));

        assertEquals(STORE_ID, detail.getId());
        assertEquals("徐汇店", detail.getName());
        assertEquals(Integer.valueOf(2), detail.getStoreType());
        assertEquals("精品店", detail.getStoreTypeName());
        assertEquals(REGION_XUHUI, detail.getRegionCode());
        assertEquals("徐汇区", detail.getRegionName());

        ArgumentCaptor<StoreDO> captor = ArgumentCaptor.forClass(StoreDO.class);
        verify(storeDao).updateById(captor.capture());
        assertEquals("徐汇店", captor.getValue().getName(), "请求字段前后空格必须去掉");
        assertEquals(OPERATOR_ID, captor.getValue().getUpdateBy(), "更新人取自当前登录态");
    }

    // ------------------------------------------------------------------
    // 删除门禁四分支
    // ------------------------------------------------------------------

    @Test
    @DisplayName("删除①：门店不存在 → 404，不统计、不删除")
    void delete_notExists_shouldThrow404()
    {
        when(storeDao.selectById(STORE_ID)).thenReturn(null);

        ServiceException exception = assertThrows(ServiceException.class, () -> storeService.delete(STORE_ID));

        assertEquals(404, exception.getCode().intValue());
        assertEquals("门店不存在或已被删除", exception.getMessage());
        verify(storeDao, never()).deleteById(any(Long.class));
        verifyNoInteractions(classroomService);
        verifyNoInteractions(scheduleService);
    }

    @Test
    @DisplayName("删除②：名下有 2 间教室 → 409「该门店下仍有 2 间教室，无法删除」，不查排课、不删除")
    void delete_withClassrooms_shouldReject()
    {
        when(storeDao.selectById(STORE_ID)).thenReturn(store(STORE_ID, REGION_XUHUI));
        when(classroomService.countByStoreId(STORE_ID)).thenReturn(2L);

        ServiceException exception = assertThrows(ServiceException.class, () -> storeService.delete(STORE_ID));

        assertEquals(409, exception.getCode().intValue());
        assertEquals("该门店下仍有 2 间教室，无法删除", exception.getMessage());
        verify(storeDao, never()).deleteById(any(Long.class));
        verifyNoInteractions(scheduleService);
    }

    @Test
    @DisplayName("删除③：有 5 节未结束排课 → 409「该门店仍有 5 节未结束的排课，无法删除」，不删除")
    void delete_withActiveSchedules_shouldReject()
    {
        when(storeDao.selectById(STORE_ID)).thenReturn(store(STORE_ID, REGION_XUHUI));
        when(classroomService.countByStoreId(STORE_ID)).thenReturn(0L);
        when(scheduleService.countActiveByStoreId(STORE_ID)).thenReturn(5L);

        ServiceException exception = assertThrows(ServiceException.class, () -> storeService.delete(STORE_ID));

        assertEquals(409, exception.getCode().intValue());
        assertEquals("该门店仍有 5 节未结束的排课，无法删除", exception.getMessage());
        verify(storeDao, never()).deleteById(any(Long.class));
    }

    @Test
    @DisplayName("删除④：教室统计抛异常 → fail-closed，409「引用检查未完成，已拒绝本次删除」，不删除")
    void delete_whenClassroomCountFails_shouldFailClosed()
    {
        when(storeDao.selectById(STORE_ID)).thenReturn(store(STORE_ID, REGION_XUHUI));
        when(classroomService.countByStoreId(STORE_ID)).thenThrow(new IllegalStateException("统计服务不可用"));

        ServiceException exception = assertThrows(ServiceException.class, () -> storeService.delete(STORE_ID));

        assertEquals(409, exception.getCode().intValue());
        assertEquals("引用检查未完成，已拒绝本次删除", exception.getMessage());
        assertNotNull(exception.getCause(), "原始异常要挂上，便于排查");
        verify(storeDao, never()).deleteById(any(Long.class));
        verifyNoInteractions(scheduleService);
    }

    @Test
    @DisplayName("删除④：排课统计抛异常 → fail-closed，409「引用检查未完成，已拒绝本次删除」，不删除")
    void delete_whenScheduleCountFails_shouldFailClosed()
    {
        when(storeDao.selectById(STORE_ID)).thenReturn(store(STORE_ID, REGION_XUHUI));
        when(classroomService.countByStoreId(STORE_ID)).thenReturn(0L);
        when(scheduleService.countActiveByStoreId(STORE_ID)).thenThrow(new IllegalStateException("统计服务不可用"));

        ServiceException exception = assertThrows(ServiceException.class, () -> storeService.delete(STORE_ID));

        assertEquals(409, exception.getCode().intValue());
        assertEquals("引用检查未完成，已拒绝本次删除", exception.getMessage());
        verify(storeDao, never()).deleteById(any(Long.class));
    }

    @Test
    @DisplayName("删除：无教室且无未结束排课 → 物理删除该行")
    void delete_withoutReferences_shouldPhysicallyDelete()
    {
        when(storeDao.selectById(STORE_ID)).thenReturn(store(STORE_ID, REGION_XUHUI));
        when(classroomService.countByStoreId(STORE_ID)).thenReturn(0L);
        when(scheduleService.countActiveByStoreId(STORE_ID)).thenReturn(0L);
        when(storeDao.deleteById(STORE_ID)).thenReturn(1);

        storeService.delete(STORE_ID);

        verify(storeDao).deleteById(STORE_ID);
    }

    // ------------------------------------------------------------------
    // 跨模块摘要
    // ------------------------------------------------------------------

    @Test
    @DisplayName("摘要：入参为空／全为 null → 返回空列表，不查库")
    void summaries_withEmptyIds_shouldReturnEmptyList()
    {
        assertTrue(storeService.summaries(null).isEmpty());
        assertTrue(storeService.summaries(Collections.<Long>emptyList()).isEmpty());
        assertTrue(storeService.summaries(Arrays.asList(null, null)).isEmpty());
        verifyNoInteractions(storeDao);
    }

    @Test
    @DisplayName("摘要：按 ID 去重后批量查询，返回 id + name")
    @SuppressWarnings("unchecked")
    void summaries_shouldDeduplicateIds()
    {
        StoreDO first = store(1L, REGION_XUHUI);
        StoreDO second = store(2L, REGION_JINGAN);
        second.setName("静安店");
        when(storeDao.selectByIds(anyCollection())).thenReturn(Arrays.asList(first, second));

        List<StoreSummaryVO> summaries = storeService.summaries(Arrays.asList(1L, 1L, 2L, 2L, null));

        ArgumentCaptor<Collection<Long>> captor = ArgumentCaptor.forClass(Collection.class);
        verify(storeDao).selectByIds(captor.capture());
        assertEquals(2, captor.getValue().size(), "重复 ID 必须去重后再查库");
        assertTrue(captor.getValue().containsAll(Arrays.asList(1L, 2L)));
        assertFalse(captor.getValue().contains(null));
        assertEquals(2, summaries.size());
        assertEquals(Long.valueOf(1L), summaries.get(0).getId());
        assertEquals("徐汇店", summaries.get(0).getName());
        assertEquals("静安店", summaries.get(1).getName());
    }

    @Test
    @DisplayName("摘要：命中的门店按入参 ID 逐个返回摘要（只给 id 与 name）")
    void summaries_withExistingIds_shouldReturnFoundStores()
    {
        when(storeDao.selectByIds(anyCollection()))
                .thenReturn(Collections.singletonList(store(7L, REGION_XUHUI)));

        List<StoreSummaryVO> summaries = storeService.summaries(Collections.singletonList(7L));

        assertEquals(1, summaries.size());
        assertEquals(Long.valueOf(7L), summaries.get(0).getId());
        assertEquals("徐汇店", summaries.get(0).getName());
    }

    // ------------------------------------------------------------------
    // 夹具
    // ------------------------------------------------------------------

    /** 字典 store_region 的模拟内容（只含两个合法 code，够用即可） */
    private Map<String, String> regionDict()
    {
        Map<String, String> dict = new LinkedHashMap<String, String>();
        dict.put(REGION_XUHUI, "徐汇区");
        dict.put(REGION_JINGAN, "静安区");
        return dict;
    }

    private StoreDO store(Long id, String regionCode)
    {
        StoreDO store = new StoreDO();
        store.setId(id);
        store.setName("徐汇店");
        store.setStoreType(1);
        store.setRegionCode(regionCode);
        store.setPhone("021-12345678");
        store.setBusinessHours("周一至周日 09:00-22:00");
        store.setAddress("漕溪北路 88 号");
        return store;
    }

    private StoreCreateDTO createDTO(String name, String regionCode, Integer storeType)
    {
        StoreCreateDTO dto = new StoreCreateDTO();
        dto.setName(name);
        dto.setStoreType(storeType);
        dto.setRegionCode(regionCode);
        dto.setPhone("021-12345678");
        dto.setBusinessHours("周一至周日 09:00-22:00");
        dto.setAddress("漕溪北路 88 号");
        dto.setImageUrl("https://cdn.example.com/store/xuhui.jpg");
        return dto;
    }

    private StoreUpdateDTO updateDTO(String name, String regionCode, Integer storeType)
    {
        StoreUpdateDTO dto = new StoreUpdateDTO();
        dto.setName(name);
        dto.setStoreType(storeType);
        dto.setRegionCode(regionCode);
        dto.setPhone("021-87654321");
        dto.setBusinessHours("周一至周日 10:00-21:00");
        dto.setAddress("愚园路 168 号");
        return dto;
    }
}
