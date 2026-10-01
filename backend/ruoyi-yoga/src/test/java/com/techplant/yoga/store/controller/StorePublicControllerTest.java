package com.techplant.yoga.store.controller;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.common.annotation.Anonymous;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.framework.web.exception.GlobalExceptionHandler;
import com.techplant.yoga.common.config.JacksonConfig;
import com.techplant.yoga.common.response.PageResult;
import com.techplant.yoga.store.service.StoreService;
import com.techplant.yoga.store.vo.StorePublicItemVO;

/**
 * 用户端门店列表接口层的单元测试（用户端接口详细设计 §2.1、§3.1）。
 *
 * <p>要点：类上标 {@link Anonymous}（免登录）、返回若依标准 {@code TableDataInfo}、
 * 主键为字符串、门店类型与区域成对返回、<b>不返回审计字段与任何状态字段</b>、
 * 空 imageUrl 兜底成空字符串、分页上限 100、区域 code 越界即拒绝。</p>
 */
@DisplayName("StorePublicController 单元测试")
class StorePublicControllerTest
{
    /** 接口示例里的门店编号（字符串形态，19 位雪花 ID） */
    private static final String STORE_ID = "1856739201475235901";

    /** 同一个编号的 Long 形态（用于造数据） */
    private static final Long STORE_ID_LONG = 1856739201475235901L;

    private StoreService storeService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp()
    {
        storeService = mock(StoreService.class);
        ObjectMapper objectMapper = new Jackson2ObjectMapperBuilder()
                .modulesToInstall(new JacksonConfig.BusinessIdToStringModule())
                .build();
        mockMvc = MockMvcBuilders.standaloneSetup(new StorePublicController(storeService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    @Test
    @DisplayName("免登录：StorePublicController 类上标注 @Anonymous")
    void controller_shouldBeAnonymous()
    {
        assertTrue(StorePublicController.class.isAnnotationPresent(Anonymous.class),
                "用户端门店接口必须标 @Anonymous，进入 permitAll 白名单");
    }

    @Test
    @DisplayName("用户端列表：返回 TableDataInfo{total,rows,code,msg}，枚举成对且不含审计/状态字段")
    void list_shouldReturnTableDataInfo()
    {
        List<StorePublicItemVO> rows = new ArrayList<StorePublicItemVO>();
        StorePublicItemVO vo = new StorePublicItemVO();
        vo.setId(STORE_ID_LONG);
        vo.setName("徐汇店");
        vo.setStoreType(1);
        vo.setStoreTypeName("主力店");
        vo.setRegionCode("310104");
        vo.setRegionName("徐汇区");
        vo.setPhone("021-12345678");
        vo.setBusinessHours("周一至周日 09:00-22:00");
        vo.setAddress("漕溪北路 88 号");
        vo.setImageUrl("");
        rows.add(vo);
        when(storeService.publicPage(any())).thenReturn(PageResult.of(1L, 1, 10, rows));

        try
        {
            mockMvc.perform(get("/api/stores").param("pageNum", "1").param("pageSize", "10"))
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
                    .andExpect(jsonPath("$.rows[0].imageUrl").value(""))
                    // 用户端不返回审计字段与任何状态字段
                    .andExpect(jsonPath("$.rows[0].createTime").doesNotExist())
                    .andExpect(jsonPath("$.rows[0].updateTime").doesNotExist())
                    .andExpect(jsonPath("$.rows[0].createBy").doesNotExist())
                    .andExpect(jsonPath("$.rows[0].status").doesNotExist())
                    .andExpect(jsonPath("$.rows[0].deleted").doesNotExist());
        }
        catch (Exception e)
        {
            throw new AssertionError(e);
        }
    }

    @Test
    @DisplayName("用户端列表：无门店 → total 0、rows 空数组（不是 null）")
    void list_withoutStores_shouldReturnEmptyRows()
    {
        when(storeService.publicPage(any())).thenReturn(PageResult.of(0L, 1, 10, new ArrayList<StorePublicItemVO>()));

        try
        {
            mockMvc.perform(get("/api/stores"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.total").value(0))
                    .andExpect(jsonPath("$.rows").isArray())
                    .andExpect(jsonPath("$.rows").isEmpty());
        }
        catch (Exception e)
        {
            throw new AssertionError(e);
        }
    }

    @Test
    @DisplayName("用户端列表：regionCode 越界 → 500「所在区域取值非法」")
    void list_withUnknownRegionCode_shouldReturn500()
    {
        doThrow(new ServiceException("所在区域取值非法", 500)).when(storeService).publicPage(any());

        try
        {
            mockMvc.perform(get("/api/stores").param("regionCode", "999999"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(500))
                    .andExpect(jsonPath("$.msg").value("所在区域取值非法"));
        }
        catch (Exception e)
        {
            throw new AssertionError(e);
        }
    }

    @Test
    @DisplayName("用户端列表：pageSize 超过 100 → 500「每页条数不能超过 100」，不落到 service")
    void list_withTooLargePageSize_shouldReturnError()
    {
        try
        {
            mockMvc.perform(get("/api/stores").param("pageSize", "1000"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(500))
                    .andExpect(jsonPath("$.msg").value("每页条数不能超过 100"));
        }
        catch (Exception e)
        {
            throw new AssertionError(e);
        }
        verifyNoInteractions(storeService);
    }
}
