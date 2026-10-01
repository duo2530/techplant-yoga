package com.techplant.yoga.classroom.controller;

import static org.junit.jupiter.api.Assertions.assertFalse;
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
import org.hamcrest.Matchers;
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
import com.techplant.yoga.classroom.dto.ClassroomUpdateDTO;
import com.techplant.yoga.classroom.service.ClassroomService;
import com.techplant.yoga.classroom.vo.ClassroomListItemVO;
import com.techplant.yoga.common.config.JacksonConfig;
import com.techplant.yoga.common.response.PageResult;

/**
 * 教室接口层的单元测试（教室管理详细设计 §2.2）。
 *
 * <p><b>4 个接口，没有详情接口</b>：列表 / 新增（不返回新 ID）/ 修改（只改名称）/ 删除（物理，带门禁）。</p>
 */
@DisplayName("ClassroomController 单元测试")
class ClassroomControllerTest
{
    private static final String CLASSROOM_ID = "1856739201475236001";

    private ClassroomService classroomService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp()
    {
        classroomService = mock(ClassroomService.class);
        ClassroomController controller = new ClassroomController(classroomService);
        ObjectMapper objectMapper = new Jackson2ObjectMapperBuilder()
                .modulesToInstall(new JacksonConfig.BusinessIdToStringModule())
                .build();
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    @Test
    @DisplayName("列表：返回 TableDataInfo（id / storeId / storeName / name / 时间）")
    void list_shouldReturnTableDataInfo()
    {
        List<ClassroomListItemVO> rows = new ArrayList<ClassroomListItemVO>();
        ClassroomListItemVO vo = new ClassroomListItemVO();
        vo.setId(1856739201475236001L);
        vo.setStoreId(1856739201475235901L);
        vo.setStoreName("徐汇店");
        vo.setName("瑜伽团课大教室");
        vo.setCreateTime(LocalDateTime.of(2026, 10, 1, 10, 0, 0));
        vo.setUpdateTime(LocalDateTime.of(2026, 10, 1, 10, 0, 0));
        rows.add(vo);
        when(classroomService.page(any())).thenReturn(PageResult.of(1L, 1, 10, rows));

        try
        {
            mockMvc.perform(get("/admin/classrooms").param("storeId", "1856739201475235901").param("pageNum", "1")
                            .param("pageSize", "10"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.msg").value("查询成功"))
                    .andExpect(jsonPath("$.total").value(1))
                    .andExpect(jsonPath("$.rows[0].id").value(CLASSROOM_ID))
                    .andExpect(jsonPath("$.rows[0].storeId").value("1856739201475235901"))
                    .andExpect(jsonPath("$.rows[0].storeName").value("徐汇店"))
                    .andExpect(jsonPath("$.rows[0].name").value("瑜伽团课大教室"));
        }
        catch (Exception e)
        {
            throw new AssertionError(e);
        }
    }

    @Test
    @DisplayName("列表：pageSize 超过 100 → 500（参数校验）")
    void list_withTooLargePageSize_shouldReturnError()
    {
        try
        {
            mockMvc.perform(get("/admin/classrooms").param("pageSize", "101"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(500))
                    .andExpect(jsonPath("$.msg").value("每页条数不能超过 100"));
        }
        catch (Exception e)
        {
            throw new AssertionError(e);
        }
        verifyNoInteractions(classroomService);
    }

    @Test
    @DisplayName("新增：成功只返回 code/msg，不返回新 ID")
    void create_shouldReturnSuccessWithoutId()
    {
        try
        {
            mockMvc.perform(post("/admin/classrooms")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"storeId\":\"1856739201475235901\",\"name\":\"普拉提器械教室\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.msg").value("操作成功"))
                    .andExpect(jsonPath("$.data").doesNotExist());
        }
        catch (Exception e)
        {
            throw new AssertionError(e);
        }
        verify(classroomService).create(any());
    }

    @Test
    @DisplayName("新增：所属门店缺失 → 500「所属门店不能为空」")
    void create_withoutStoreId_shouldReturnError()
    {
        try
        {
            mockMvc.perform(post("/admin/classrooms")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"普拉提器械教室\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(500))
                    .andExpect(jsonPath("$.msg").value("所属门店不能为空"));
        }
        catch (Exception e)
        {
            throw new AssertionError(e);
        }
        verifyNoInteractions(classroomService);
    }

    @Test
    @DisplayName("新增：教室名称缺失 → 500「教室名称不能为空」")
    void create_withoutName_shouldReturnError()
    {
        try
        {
            mockMvc.perform(post("/admin/classrooms")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"storeId\":1}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(500))
                    .andExpect(jsonPath("$.msg").value("教室名称不能为空"));
        }
        catch (Exception e)
        {
            throw new AssertionError(e);
        }
        verifyNoInteractions(classroomService);
    }

    @Test
    @DisplayName("新增：门店不存在 → 404「门店不存在或已被删除」")
    void create_withMissingStore_shouldReturn404()
    {
        doThrow(new ServiceException("门店不存在或已被删除", 404)).when(classroomService).create(any());

        try
        {
            mockMvc.perform(post("/admin/classrooms")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"storeId\":1,\"name\":\"普拉提器械教室\"}"))
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
    @DisplayName("新增：同门店重名 → 409「该门店下已存在同名教室」")
    void create_withDuplicateName_shouldReturn409()
    {
        doThrow(new ServiceException("该门店下已存在同名教室", 409)).when(classroomService).create(any());

        try
        {
            mockMvc.perform(post("/admin/classrooms")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"storeId\":1,\"name\":\"瑜伽团课大教室\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(409))
                    .andExpect(jsonPath("$.msg").value("该门店下已存在同名教室"));
        }
        catch (Exception e)
        {
            throw new AssertionError(e);
        }
    }

    @Test
    @DisplayName("修改：请求体只有 name，携带 storeId 也被忽略（模型里没有该字段）")
    void update_shouldIgnoreStoreIdInBody()
    {
        // 模型层杜绝换门店：ClassroomUpdateDTO 不定义 storeId
        assertFalse(hasField(ClassroomUpdateDTO.class, "storeId"), "ClassroomUpdateDTO 不得定义 storeId");

        try
        {
            mockMvc.perform(put("/admin/classrooms/{classroomId}", CLASSROOM_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"普拉提小班课\",\"storeId\":\"999\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.msg").value("操作成功"));
        }
        catch (Exception e)
        {
            throw new AssertionError(e);
        }
        verify(classroomService).update(eq(1856739201475236001L), any(ClassroomUpdateDTO.class));
    }

    @Test
    @DisplayName("修改：名称缺失 → 500「教室名称不能为空」")
    void update_withoutName_shouldReturnError()
    {
        try
        {
            mockMvc.perform(put("/admin/classrooms/{classroomId}", CLASSROOM_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(500))
                    .andExpect(jsonPath("$.msg").value("教室名称不能为空"));
        }
        catch (Exception e)
        {
            throw new AssertionError(e);
        }
        verifyNoInteractions(classroomService);
    }

    @Test
    @DisplayName("修改：教室不存在 → 404「教室不存在或已被删除」")
    void update_notExists_shouldReturn404()
    {
        doThrow(new ServiceException("教室不存在或已被删除", 404))
                .when(classroomService).update(eq(1L), any());

        try
        {
            mockMvc.perform(put("/admin/classrooms/{classroomId}", "1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"普拉提小班课\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(404))
                    .andExpect(jsonPath("$.msg").value("教室不存在或已被删除"));
        }
        catch (Exception e)
        {
            throw new AssertionError(e);
        }
    }

    @Test
    @DisplayName("删除：成功只返回 code/msg")
    void delete_shouldReturnSuccess()
    {
        try
        {
            mockMvc.perform(delete("/admin/classrooms/{classroomId}", CLASSROOM_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.msg").value("操作成功"));
        }
        catch (Exception e)
        {
            throw new AssertionError(e);
        }
        verify(classroomService).delete(1856739201475236001L);
    }

    @Test
    @DisplayName("删除：仍有未结束排课 → 409，提示语带数量")
    void delete_withActiveSchedule_shouldReturn409()
    {
        doThrow(new ServiceException("该教室仍有 2 节未结束的排课，无法删除", 409))
                .when(classroomService).delete(1856739201475236001L);

        try
        {
            mockMvc.perform(delete("/admin/classrooms/{classroomId}", CLASSROOM_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(409))
                    .andExpect(jsonPath("$.msg").value(Matchers.containsString("2 节未结束的排课")));
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
                .when(classroomService).delete(1856739201475236001L);

        try
        {
            mockMvc.perform(delete("/admin/classrooms/{classroomId}", CLASSROOM_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(409))
                    .andExpect(jsonPath("$.msg").value("引用检查未完成，已拒绝本次删除"));
        }
        catch (Exception e)
        {
            throw new AssertionError(e);
        }
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
