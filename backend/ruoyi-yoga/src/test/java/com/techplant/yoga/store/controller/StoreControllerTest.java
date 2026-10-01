package com.techplant.yoga.store.controller;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.framework.web.exception.GlobalExceptionHandler;
import com.techplant.yoga.common.config.JacksonConfig;
import com.techplant.yoga.common.response.PageResult;
import com.techplant.yoga.store.service.StoreService;
import com.techplant.yoga.store.vo.StoreDetailVO;
import com.techplant.yoga.store.vo.StoreListItemVO;

/**
 * 门店管理端接口层的单元测试（门店管理详细设计 §2.2）。
 *
 * <p>业务服务用模拟对象隔离；用 MockMvc 走真实的参数绑定、{@code @Valid} 校验与异常转换链路，
 * 并挂上框架真实的 {@link GlobalExceptionHandler} 与 {@link JacksonConfig} 的 ID 字符串化模块，
 * 保证「主键返回带引号的字符串」「新增不返回新 ID」这些契约被测到。</p>
 *
 * <p><b>5 个接口：</b>列表 / 详情 / 新增（不返回新 ID）/ 修改（返回详情）/ 删除（物理，带门禁）。
 * <b>没有状态接口</b>，也没有 {@code PUT /{storeId}/status}。</p>
 */
@DisplayName("StoreController 单元测试")
class StoreControllerTest
{
    /** 接口示例里的门店编号（字符串形态，19 位雪花 ID） */
    private static final String STORE_ID = "1856739201475235901";

    /** 同一个编号的 Long 形态（用于打桩） */
    private static final Long STORE_ID_LONG = 1856739201475235901L;

    private StoreService storeService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp()
    {
        storeService = mock(StoreService.class);
        StoreController controller = new StoreController(storeService);
        ObjectMapper objectMapper = new Jackson2ObjectMapperBuilder()
                .modulesToInstall(new JacksonConfig.BusinessIdToStringModule())
                .build();
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    @Test
    @DisplayName("列表：返回若依标准 TableDataInfo{total,rows,code,msg}，枚举成对、主键为字符串")
    void list_shouldReturnTableDataInfoWithEnumPairs()
    {
        List<StoreListItemVO> rows = new ArrayList<StoreListItemVO>();
        StoreListItemVO vo = new StoreListItemVO();
        vo.setId(STORE_ID_LONG);
        vo.setName("徐汇店");
        vo.setStoreType(1);
        vo.setStoreTypeName("主力店");
        vo.setRegionCode("310104");
        vo.setRegionName("徐汇区");
        vo.setPhone("021-12345678");
        vo.setBusinessHours("周一至周日 09:00-22:00");
        vo.setAddress("漕溪北路 88 号");
        vo.setCreateTime(LocalDateTime.of(2026, 9, 20, 10, 0, 0));
        rows.add(vo);
        when(storeService.page(any())).thenReturn(PageResult.of(1L, 1, 10, rows));

        try
        {
            mockMvc.perform(get("/admin/stores").param("pageNum", "1").param("pageSize", "10"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.msg").value("查询成功"))
                    .andExpect(jsonPath("$.total").value(1))
                    .andExpect(jsonPath("$.rows[0].id").isString())
                    .andExpect(jsonPath("$.rows[0].id").value(STORE_ID))
                    .andExpect(jsonPath("$.rows[0].name").value("徐汇店"))
                    .andExpect(jsonPath("$.rows[0].storeType").value(1))
                    .andExpect(jsonPath("$.rows[0].storeTypeName").value("主力店"))
                    .andExpect(jsonPath("$.rows[0].regionCode").value("310104"))
                    .andExpect(jsonPath("$.rows[0].regionName").value("徐汇区"))
                    .andExpect(jsonPath("$.rows[0].createTime").value("2026-09-20 10:00:00"))
                    // 门店没有状态字段，列表更不返回审计字段
                    .andExpect(jsonPath("$.rows[0].status").doesNotExist())
                    .andExpect(jsonPath("$.rows[0].createBy").doesNotExist());
        }
        catch (Exception e)
        {
            throw new AssertionError(e);
        }
    }

    @Test
    @DisplayName("列表：门店类型越界 → 500「门店类型取值为 1 或 2」（@Valid 生效，不落到 service）")
    void list_withInvalidStoreType_shouldReturnError()
    {
        try
        {
            mockMvc.perform(get("/admin/stores").param("storeType", "9"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(500))
                    .andExpect(jsonPath("$.msg").value("门店类型取值为 1 或 2"));
        }
        catch (Exception e)
        {
            throw new AssertionError(e);
        }
        verifyNoInteractions(storeService);
    }

    @Test
    @DisplayName("详情：返回 AjaxResult(data=StoreDetailVO)，主键是带引号的字符串")
    void detail_shouldReturnDetailWithStringId()
    {
        StoreDetailVO detail = new StoreDetailVO();
        detail.setId(STORE_ID_LONG);
        detail.setName("徐汇店");
        detail.setStoreType(2);
        detail.setStoreTypeName("精品店");
        detail.setRegionCode("310104");
        detail.setRegionName("徐汇区");
        detail.setImageUrl("https://cdn.example.com/store/xuhui.jpg");
        when(storeService.getById(STORE_ID_LONG)).thenReturn(detail);

        try
        {
            mockMvc.perform(get("/admin/stores/{storeId}", STORE_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.id").isString())
                    .andExpect(jsonPath("$.data.id").value(STORE_ID))
                    .andExpect(jsonPath("$.data.storeType").value(2))
                    .andExpect(jsonPath("$.data.storeTypeName").value("精品店"))
                    .andExpect(jsonPath("$.data.regionCode").value("310104"))
                    .andExpect(jsonPath("$.data.regionName").value("徐汇区"))
                    .andExpect(jsonPath("$.data.imageUrl").value("https://cdn.example.com/store/xuhui.jpg"));
        }
        catch (Exception e)
        {
            throw new AssertionError(e);
        }
    }

    @Test
    @DisplayName("详情：门店不存在 → 业务码 404「门店不存在或已被删除」")
    void detail_notExists_shouldReturn404Code()
    {
        doThrow(new ServiceException("门店不存在或已被删除", 404)).when(storeService).getById(1L);

        try
        {
            mockMvc.perform(get("/admin/stores/{storeId}", "1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(404))
                    .andExpect(jsonPath("$.msg").value("门店不存在或已被删除"));
        }
        catch (Exception e)
        {
            throw new AssertionError(e);
        }
    }

    @Test
    @DisplayName("新增：成功只返回 {code:200,msg:操作成功}，不返回新 ID（data / data.id 都不存在）")
    void create_shouldReturnSuccessWithoutNewId()
    {
        try
        {
            mockMvc.perform(post("/admin/stores")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"徐汇店\",\"storeType\":1,\"regionCode\":\"310104\","
                                    + "\"phone\":\"021-12345678\",\"businessHours\":\"周一至周日 09:00-22:00\","
                                    + "\"address\":\"漕溪北路 88 号\",\"imageUrl\":\"https://cdn.example.com/store/xuhui.jpg\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.msg").value("操作成功"))
                    .andExpect(jsonPath("$.data").doesNotExist())
                    .andExpect(jsonPath("$.data.id").doesNotExist());
        }
        catch (Exception e)
        {
            throw new AssertionError(e);
        }
        verify(storeService).create(any());
    }

    @Test
    @DisplayName("新增：门店名称缺失 → 500「门店名称不能为空」，不落到 service")
    void create_withoutName_shouldReturnError()
    {
        try
        {
            mockMvc.perform(post("/admin/stores")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"storeType\":1,\"regionCode\":\"310104\",\"phone\":\"021-12345678\","
                                    + "\"businessHours\":\"周一至周日 09:00-22:00\",\"address\":\"漕溪北路 88 号\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(500))
                    .andExpect(jsonPath("$.msg").value("门店名称不能为空"));
        }
        catch (Exception e)
        {
            throw new AssertionError(e);
        }
        verifyNoInteractions(storeService);
    }

    @Test
    @DisplayName("新增：regionCode 缺失 → 500「所在区域不能为空」，不落到 service")
    void create_withoutRegionCode_shouldReturnError()
    {
        try
        {
            mockMvc.perform(post("/admin/stores")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"徐汇店\",\"storeType\":1,\"phone\":\"021-12345678\","
                                    + "\"businessHours\":\"周一至周日 09:00-22:00\",\"address\":\"漕溪北路 88 号\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(500))
                    .andExpect(jsonPath("$.msg").value("所在区域不能为空"));
        }
        catch (Exception e)
        {
            throw new AssertionError(e);
        }
        verifyNoInteractions(storeService);
    }

    @Test
    @DisplayName("新增：名称已存在 → 409「门店名称已存在」")
    void create_withDuplicateName_shouldReturn409()
    {
        doThrow(new ServiceException("门店名称已存在", 409)).when(storeService).create(any());

        try
        {
            mockMvc.perform(post("/admin/stores")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"徐汇店\",\"storeType\":1,\"regionCode\":\"310104\","
                                    + "\"phone\":\"021-12345678\",\"businessHours\":\"周一至周日 09:00-22:00\","
                                    + "\"address\":\"漕溪北路 88 号\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(409))
                    .andExpect(jsonPath("$.msg").value("门店名称已存在"));
        }
        catch (Exception e)
        {
            throw new AssertionError(e);
        }
    }

    @Test
    @DisplayName("修改：返回更新后的 StoreDetailVO（枚举成对，主键为字符串）")
    void update_shouldReturnUpdatedDetail()
    {
        StoreDetailVO detail = new StoreDetailVO();
        detail.setId(STORE_ID_LONG);
        detail.setName("徐汇店（新）");
        detail.setStoreType(2);
        detail.setStoreTypeName("精品店");
        detail.setRegionCode("310106");
        detail.setRegionName("静安区");
        when(storeService.update(eq(STORE_ID_LONG), any())).thenReturn(detail);

        try
        {
            mockMvc.perform(put("/admin/stores/{storeId}", STORE_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"徐汇店（新）\",\"storeType\":2,\"regionCode\":\"310106\","
                                    + "\"phone\":\"021-12345678\",\"businessHours\":\"周一至周日 09:00-22:00\","
                                    + "\"address\":\"愚园路 168 号\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.id").isString())
                    .andExpect(jsonPath("$.data.id").value(STORE_ID))
                    .andExpect(jsonPath("$.data.name").value("徐汇店（新）"))
                    .andExpect(jsonPath("$.data.storeTypeName").value("精品店"))
                    .andExpect(jsonPath("$.data.regionName").value("静安区"));
        }
        catch (Exception e)
        {
            throw new AssertionError(e);
        }
    }

    @Test
    @DisplayName("删除：成功只返回 code/msg")
    void remove_shouldReturnSuccess()
    {
        try
        {
            mockMvc.perform(delete("/admin/stores/{storeId}", STORE_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.msg").value("操作成功"));
        }
        catch (Exception e)
        {
            throw new AssertionError(e);
        }
        verify(storeService).delete(STORE_ID_LONG);
    }

    @Test
    @DisplayName("删除：名下仍有教室 → 409，提示语带数量")
    void remove_blockedByClassrooms_shouldReturn409WithCount()
    {
        doThrow(new ServiceException("该门店下仍有 2 间教室，无法删除", 409)).when(storeService).delete(STORE_ID_LONG);

        try
        {
            mockMvc.perform(delete("/admin/stores/{storeId}", STORE_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(409))
                    .andExpect(jsonPath("$.msg").value(containsString("2 间教室")));
        }
        catch (Exception e)
        {
            throw new AssertionError(e);
        }
    }

    @Test
    @DisplayName("删除：仍有未结束排课 → 409，提示语带数量")
    void remove_blockedBySchedules_shouldReturn409WithCount()
    {
        doThrow(new ServiceException("该门店仍有 5 节未结束的排课，无法删除", 409))
                .when(storeService).delete(STORE_ID_LONG);

        try
        {
            mockMvc.perform(delete("/admin/stores/{storeId}", STORE_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(409))
                    .andExpect(jsonPath("$.msg").value(containsString("5 节未结束的排课")));
        }
        catch (Exception e)
        {
            throw new AssertionError(e);
        }
    }

    @Test
    @DisplayName("删除：引用统计失败 → fail-closed，409「引用检查未完成，已拒绝本次删除」")
    void remove_whenReferenceCheckFails_shouldReturn409()
    {
        doThrow(new ServiceException("引用检查未完成，已拒绝本次删除", 409))
                .when(storeService).delete(STORE_ID_LONG);

        try
        {
            mockMvc.perform(delete("/admin/stores/{storeId}", STORE_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(409))
                    .andExpect(jsonPath("$.msg").value("引用检查未完成，已拒绝本次删除"));
        }
        catch (Exception e)
        {
            throw new AssertionError(e);
        }
    }

    @Test
    @DisplayName("路径参数不是数字 → 500（框架参数类型不匹配处理）")
    void detail_withNonNumericId_shouldReturnError()
    {
        try
        {
            mockMvc.perform(get("/admin/stores/{storeId}", "abc"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(500))
                    .andExpect(jsonPath("$.msg").value(containsString("storeId")));
        }
        catch (Exception e)
        {
            throw new AssertionError(e);
        }
        verifyNoInteractions(storeService);
    }

    @Test
    @DisplayName("状态接口已下线：PUT /admin/stores/{storeId}/status 不再存在")
    void statusEndpoint_shouldNotExist()
    {
        try
        {
            mockMvc.perform(put("/admin/stores/{storeId}/status", STORE_ID).param("status", "1"));
        }
        catch (Exception e)
        {
            // standalone MockMvc 对未映射路径直接抛异常，这里只要求它确实没有落到 controller 上
            verifyNoInteractions(storeService);
            return;
        }
        verifyNoInteractions(storeService);
    }
}
