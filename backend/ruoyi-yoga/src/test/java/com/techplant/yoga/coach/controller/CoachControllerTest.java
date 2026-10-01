package com.techplant.yoga.coach.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import java.util.Arrays;
import java.util.List;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.framework.web.exception.GlobalExceptionHandler;
import com.techplant.yoga.coach.dto.CoachCreateDTO;
import com.techplant.yoga.coach.service.CoachService;
import com.techplant.yoga.coach.vo.CoachDetailVO;
import com.techplant.yoga.coach.vo.CoachListItemVO;
import com.techplant.yoga.common.config.JacksonConfig;
import com.techplant.yoga.common.response.PageResult;

/**
 * 教练接口层的单元测试（教练管理详细设计 §2.2）。
 *
 * <p>业务服务用模拟对象隔离；用 MockMvc 走真实的参数绑定、{@code @Valid} 校验与异常转换链路。
 * 序列化用 {@link JacksonConfig} 的 ObjectMapper，保证「教练编号返回字符串」这条契约被测到。</p>
 *
 * <p><b>5 个接口：</b>列表 / 详情 / 新增（<b>不返回新 ID</b>）/ 修改（返回详情）/ 删除（物理，带门禁）。
 * <b>没有状态接口</b>，{@code PUT /admin/coaches/{coachId}/status} 必须不存在。</p>
 */
@DisplayName("CoachController 单元测试")
class CoachControllerTest
{
    /** 详情示例里的教练编号（雪花 ID，19 位） */
    private static final String COACH_ID = "1856739201475237001";

    private CoachService coachService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp()
    {
        coachService = mock(CoachService.class);
        CoachController controller = new CoachController(coachService);
        ObjectMapper objectMapper = new Jackson2ObjectMapperBuilder()
                .modulesToInstall(new JacksonConfig.BusinessIdToStringModule())
                .build();
        // 用框架自带的全局异常处理器，与线上一致
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    // ------------------------------------------------------------------
    // 列表
    // ------------------------------------------------------------------

    @Test
    @DisplayName("列表：返回若依标准 TableDataInfo，id 是字符串，且不含 intro/gallery")
    void list_shouldReturnTableDataInfoWithoutBigFields()
    {
        List<CoachListItemVO> rows = new ArrayList<CoachListItemVO>();
        CoachListItemVO vo = new CoachListItemVO();
        vo.setId(1856739201475237001L);
        vo.setName("林教练");
        vo.setAvatarUrl("https://cdn.example.com/coach/avatar.jpg");
        vo.setPhone("13800000001");
        vo.setCreateTime(LocalDateTime.of(2026, 9, 28, 10, 0, 0));
        vo.setUpdateTime(LocalDateTime.of(2026, 9, 28, 10, 5, 0));
        rows.add(vo);
        when(coachService.page(any())).thenReturn(PageResult.of(1L, 1, 10, rows));

        try
        {
            mockMvc.perform(get("/admin/coaches").param("name", "林").param("pageNum", "1").param("pageSize", "10"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.msg").value("查询成功"))
                    .andExpect(jsonPath("$.total").value(1))
                    .andExpect(jsonPath("$.rows[0].id").value(COACH_ID))
                    .andExpect(jsonPath("$.rows[0].name").value("林教练"))
                    .andExpect(jsonPath("$.rows[0].phone").value("13800000001"))
                    .andExpect(jsonPath("$.rows[0].intro").doesNotExist())
                    .andExpect(jsonPath("$.rows[0].gallery").doesNotExist());
        }
        catch (Exception e)
        {
            throw new AssertionError(e);
        }
    }

    @Test
    @DisplayName("列表：pageSize 超过 100 → 500「每页条数不能超过 100」，不调 service")
    void list_withTooLargePageSize_shouldReturnError()
    {
        try
        {
            mockMvc.perform(get("/admin/coaches").param("pageSize", "101"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(500))
                    .andExpect(jsonPath("$.msg").value("每页条数不能超过 100"));
        }
        catch (Exception e)
        {
            throw new AssertionError(e);
        }
        verifyNoInteractions(coachService);
    }

    // ------------------------------------------------------------------
    // 详情
    // ------------------------------------------------------------------

    @Test
    @DisplayName("详情：id 序列化为带引号的字符串，gallery 是数组")
    void detail_shouldReturnStringIdAndGalleryArray()
    {
        CoachDetailVO detail = new CoachDetailVO();
        detail.setId(1856739201475237001L);
        detail.setName("林教练");
        detail.setAvatarUrl("https://cdn.example.com/coach/avatar.jpg");
        detail.setPhone("13800000001");
        detail.setIntro("十年哈他瑜伽经验");
        detail.setGallery(Arrays.asList("https://cdn.example.com/coach/a.jpg", "https://cdn.example.com/coach/b.jpg"));
        when(coachService.getById(1856739201475237001L)).thenReturn(detail);

        try
        {
            mockMvc.perform(get("/admin/coaches/{coachId}", COACH_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.id").value(COACH_ID))
                    .andExpect(jsonPath("$.data.name").value("林教练"))
                    .andExpect(jsonPath("$.data.intro").value("十年哈他瑜伽经验"))
                    .andExpect(jsonPath("$.data.gallery.length()").value(2))
                    .andExpect(jsonPath("$.data.gallery[0]").value("https://cdn.example.com/coach/a.jpg"));
        }
        catch (Exception e)
        {
            throw new AssertionError(e);
        }
    }

    @Test
    @DisplayName("详情：教练不存在 → 业务码 404「教练不存在或已被删除」")
    void detail_notExists_shouldReturn404Code()
    {
        doThrow(new ServiceException("教练不存在或已被删除", 404)).when(coachService).getById(1L);

        try
        {
            mockMvc.perform(get("/admin/coaches/{coachId}", "1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(404))
                    .andExpect(jsonPath("$.msg").value("教练不存在或已被删除"));
        }
        catch (Exception e)
        {
            throw new AssertionError(e);
        }
    }

    @Test
    @DisplayName("详情：路径参数不是数字 → 500（框架参数类型不匹配处理），不调 service")
    void detail_withNonNumericId_shouldReturnError()
    {
        try
        {
            mockMvc.perform(get("/admin/coaches/{coachId}", "abc"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(500))
                    .andExpect(jsonPath("$.msg").value(Matchers.containsString("coachId")));
        }
        catch (Exception e)
        {
            throw new AssertionError(e);
        }
        verifyNoInteractions(coachService);
    }

    // ------------------------------------------------------------------
    // 新增：不返回新 ID；gallery 上限由 service 判定
    // ------------------------------------------------------------------

    @Test
    @DisplayName("新增：成功只返回 code/msg，不返回新 ID")
    void create_shouldReturnSuccessWithoutId()
    {
        try
        {
            mockMvc.perform(post("/admin/coaches")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"林教练\",\"avatarUrl\":\"https://cdn.example.com/coach/avatar.jpg\","
                                    + "\"intro\":\"十年哈他瑜伽经验\",\"phone\":\"13800000001\","
                                    + "\"gallery\":[\"https://cdn.example.com/coach/a.jpg\"]}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.msg").value("操作成功"))
                    .andExpect(jsonPath("$.data").doesNotExist());
        }
        catch (Exception e)
        {
            throw new AssertionError(e);
        }
        verify(coachService).create(any(CoachCreateDTO.class));
    }

    @Test
    @DisplayName("新增：姓名缺失 → 500「教练姓名不能为空」，不调 service")
    void create_withoutName_shouldReturnError()
    {
        try
        {
            mockMvc.perform(post("/admin/coaches")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"phone\":\"13800000001\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(500))
                    .andExpect(jsonPath("$.msg").value("教练姓名不能为空"));
        }
        catch (Exception e)
        {
            throw new AssertionError(e);
        }
        verifyNoInteractions(coachService);
    }

    @Test
    @DisplayName("新增：姓名超过 32 字 → 500「教练姓名长度不能超过 32」，不调 service")
    void create_withTooLongName_shouldReturnError()
    {
        StringBuilder name = new StringBuilder();
        for (int i = 0; i < 33; i++)
        {
            name.append("林");
        }

        try
        {
            mockMvc.perform(post("/admin/coaches")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"" + name + "\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(500))
                    .andExpect(jsonPath("$.msg").value("教练姓名长度不能超过 32"));
        }
        catch (Exception e)
        {
            throw new AssertionError(e);
        }
        verifyNoInteractions(coachService);
    }

    @Test
    @DisplayName("新增：相册 6 张 → 409「相册最多 5 张」（service 判定，controller 不截断入参）")
    void create_withSixGalleryUrls_shouldReturn409AndPassAllToService()
    {
        List<String> six = new ArrayList<String>();
        for (int i = 1; i <= 6; i++)
        {
            six.add("https://cdn.example.com/coach/album-" + i + ".jpg");
        }
        StringBuilder galleryJson = new StringBuilder("[");
        for (int i = 0; i < six.size(); i++)
        {
            galleryJson.append(i == 0 ? "" : ",").append('"').append(six.get(i)).append('"');
        }
        galleryJson.append(']');

        ArgumentCaptor<CoachCreateDTO> captor = ArgumentCaptor.forClass(CoachCreateDTO.class);
        doThrow(new ServiceException("相册最多 5 张", 409)).when(coachService).create(captor.capture());

        try
        {
            mockMvc.perform(post("/admin/coaches")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"林教练\",\"gallery\":" + galleryJson + "}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(409))
                    .andExpect(jsonPath("$.msg").value("相册最多 5 张"));
        }
        catch (Exception e)
        {
            throw new AssertionError(e);
        }
        assertEquals(6, captor.getValue().getGallery().size(), "controller 不得截断相册入参，超限判定在 service");
    }

    // ------------------------------------------------------------------
    // 修改
    // ------------------------------------------------------------------

    @Test
    @DisplayName("修改：返回更新后的 CoachDetailVO")
    void update_shouldReturnUpdatedDetail()
    {
        CoachDetailVO detail = new CoachDetailVO();
        detail.setId(1856739201475237001L);
        detail.setName("周教练");
        detail.setPhone("13800000009");
        when(coachService.update(eq(1856739201475237001L), any())).thenReturn(detail);

        try
        {
            mockMvc.perform(put("/admin/coaches/{coachId}", COACH_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"周教练\",\"phone\":\"13800000009\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.id").value(COACH_ID))
                    .andExpect(jsonPath("$.data.name").value("周教练"));
        }
        catch (Exception e)
        {
            throw new AssertionError(e);
        }
        verify(coachService).update(eq(1856739201475237001L), any());
    }

    @Test
    @DisplayName("修改：教练不存在 → 404")
    void update_notExists_shouldReturn404()
    {
        doThrow(new ServiceException("教练不存在或已被删除", 404))
                .when(coachService).update(eq(1L), any());

        try
        {
            mockMvc.perform(put("/admin/coaches/{coachId}", "1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"林教练\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(404))
                    .andExpect(jsonPath("$.msg").value("教练不存在或已被删除"));
        }
        catch (Exception e)
        {
            throw new AssertionError(e);
        }
    }

    // ------------------------------------------------------------------
    // 删除
    // ------------------------------------------------------------------

    @Test
    @DisplayName("删除：成功只返回 code/msg")
    void delete_shouldReturnSuccess()
    {
        try
        {
            mockMvc.perform(delete("/admin/coaches/{coachId}", COACH_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.msg").value("操作成功"));
        }
        catch (Exception e)
        {
            throw new AssertionError(e);
        }
        verify(coachService).delete(1856739201475237001L);
    }

    @Test
    @DisplayName("删除：仍有未结束排课 → 409，提示语带数量")
    void delete_withActiveSchedule_shouldReturn409()
    {
        doThrow(new ServiceException("该教练仍有 3 节未结束的排课，无法删除", 409))
                .when(coachService).delete(1856739201475237001L);

        try
        {
            mockMvc.perform(delete("/admin/coaches/{coachId}", COACH_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(409))
                    .andExpect(jsonPath("$.msg").value(Matchers.containsString("3 节未结束的排课")));
        }
        catch (Exception e)
        {
            throw new AssertionError(e);
        }
    }

    @Test
    @DisplayName("删除：引用统计失败 → fail-closed，409「引用检查未完成，已拒绝本次删除」")
    void delete_whenReferenceCheckFails_shouldReturn409()
    {
        doThrow(new ServiceException("引用检查未完成，已拒绝本次删除", 409))
                .when(coachService).delete(1856739201475237001L);

        try
        {
            mockMvc.perform(delete("/admin/coaches/{coachId}", COACH_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(409))
                    .andExpect(jsonPath("$.msg").value("引用检查未完成，已拒绝本次删除"));
        }
        catch (Exception e)
        {
            throw new AssertionError(e);
        }
    }

    // ------------------------------------------------------------------
    // 状态接口已下线（教练无状态）
    // ------------------------------------------------------------------

    @Test
    @DisplayName("状态接口已下线：PUT /admin/coaches/{coachId}/status 不再存在")
    void statusEndpoint_shouldNotExist()
    {
        try
        {
            mockMvc.perform(put("/admin/coaches/{coachId}/status", COACH_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"status\":0}"));
        }
        catch (Exception e)
        {
            // standalone MockMvc 对未映射路径直接抛异常，这里只要求它确实没有落到 controller 上
            verifyNoInteractions(coachService);
            return;
        }
        verifyNoInteractions(coachService);
    }
}
