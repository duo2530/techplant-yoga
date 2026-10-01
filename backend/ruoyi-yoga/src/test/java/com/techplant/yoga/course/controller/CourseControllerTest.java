package com.techplant.yoga.course.controller;

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
import com.techplant.yoga.common.config.JacksonConfig;
import com.techplant.yoga.common.response.PageResult;
import com.techplant.yoga.course.service.CourseService;
import com.techplant.yoga.course.vo.CourseDetailVO;
import com.techplant.yoga.course.vo.CourseListItemVO;

/**
 * 课程接口层的单元测试（课程管理详细设计 §2.2）。
 *
 * <p>业务服务用模拟对象隔离；用 MockMvc 走真实的参数绑定、校验与异常转换链路。
 * 序列化用 {@link JacksonConfig} 的 ObjectMapper，保证「课程编号返回字符串」这条契约被测到。</p>
 *
 * <p><b>5 个接口：</b>列表 / 详情 / 新增（<b>不返回新 ID</b>）/ 修改（返回详情）/ 删除（物理，带门禁）。
 * <b>没有状态接口</b>，也没有 {@code PUT /{courseId}/status}。</p>
 */
@DisplayName("CourseController 单元测试")
class CourseControllerTest
{
    /** 接口示例里的课程编号 */
    private static final String COURSE_ID = "1856739201475235801";

    private CourseService courseService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp()
    {
        courseService = mock(CourseService.class);
        CourseController controller = new CourseController(courseService);
        ObjectMapper objectMapper = new Jackson2ObjectMapperBuilder()
                .modulesToInstall(new JacksonConfig.BusinessIdToStringModule())
                .build();
        // 用框架自带的全局异常处理器，与线上一致
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    @Test
    @DisplayName("列表：返回若依标准 TableDataInfo，课种成对返回且不含 intro")
    void list_shouldReturnTableDataInfoWithTypePair()
    {
        List<CourseListItemVO> rows = new ArrayList<CourseListItemVO>();
        CourseListItemVO vo = new CourseListItemVO();
        vo.setId(1856739201475235801L);
        vo.setName("哈他瑜伽");
        vo.setCourseType(1);
        vo.setCourseTypeName("团课");
        vo.setDifficulty(2);
        vo.setCreateTime(LocalDateTime.of(2026, 9, 20, 10, 12, 33));
        rows.add(vo);
        when(courseService.page(any())).thenReturn(PageResult.of(2L, 1, 10, rows));

        try
        {
            mockMvc.perform(get("/admin/courses").param("pageNum", "1").param("pageSize", "10"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.msg").value("查询成功"))
                    .andExpect(jsonPath("$.total").value(2))
                    .andExpect(jsonPath("$.rows[0].id").value(COURSE_ID))
                    .andExpect(jsonPath("$.rows[0].name").value("哈他瑜伽"))
                    .andExpect(jsonPath("$.rows[0].courseType").value(1))
                    .andExpect(jsonPath("$.rows[0].courseTypeName").value("团课"))
                    .andExpect(jsonPath("$.rows[0].intro").doesNotExist());
        }
        catch (Exception e)
        {
            throw new AssertionError(e);
        }
    }

    @Test
    @DisplayName("列表：课种越界 → 500「课程类型取值为 1~4」（枚举校验经 @Valid 生效）")
    void list_withInvalidCourseType_shouldReturnError()
    {
        try
        {
            mockMvc.perform(get("/admin/courses").param("courseType", "9"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(500))
                    .andExpect(jsonPath("$.msg").value("课程类型取值为 1~4"));
        }
        catch (Exception e)
        {
            throw new AssertionError(e);
        }
        verifyNoInteractions(courseService);
    }

    @Test
    @DisplayName("详情：返回 CourseDetailVO（含 intro，课种成对）")
    void detail_shouldReturnDetail()
    {
        CourseDetailVO detail = new CourseDetailVO();
        detail.setId(1856739201475235801L);
        detail.setName("哈他瑜伽");
        detail.setCourseType(1);
        detail.setCourseTypeName("团课");
        detail.setDifficulty(2);
        detail.setIntro("以体式与呼吸配合为主的经典课程");
        when(courseService.getById(1856739201475235801L)).thenReturn(detail);

        try
        {
            mockMvc.perform(get("/admin/courses/{courseId}", COURSE_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.id").value(COURSE_ID))
                    .andExpect(jsonPath("$.data.courseType").value(1))
                    .andExpect(jsonPath("$.data.courseTypeName").value("团课"))
                    .andExpect(jsonPath("$.data.intro").value("以体式与呼吸配合为主的经典课程"));
        }
        catch (Exception e)
        {
            throw new AssertionError(e);
        }
    }

    @Test
    @DisplayName("详情：课程不存在 → 业务码 404「课程不存在或已被删除」")
    void detail_notExists_shouldReturn404Code()
    {
        doThrow(new ServiceException("课程不存在或已被删除", 404)).when(courseService).getById(1L);

        try
        {
            mockMvc.perform(get("/admin/courses/{courseId}", "1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(404))
                    .andExpect(jsonPath("$.msg").value("课程不存在或已被删除"));
        }
        catch (Exception e)
        {
            throw new AssertionError(e);
        }
    }

    @Test
    @DisplayName("新增：成功只返回 code/msg，不返回新 ID")
    void create_shouldReturnSuccessWithoutId()
    {
        try
        {
            mockMvc.perform(post("/admin/courses")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"哈他瑜伽\",\"courseType\":1,\"difficulty\":2,"
                                    + "\"coverUrl\":\"https://cdn.example.com/course/hata.jpg\",\"intro\":\"经典课程\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.msg").value("操作成功"))
                    .andExpect(jsonPath("$.data").doesNotExist());
        }
        catch (Exception e)
        {
            throw new AssertionError(e);
        }
        verify(courseService).create(any());
    }

    @Test
    @DisplayName("新增：名称缺失 → 500「课程名称不能为空」")
    void create_withoutName_shouldReturnError()
    {
        try
        {
            mockMvc.perform(post("/admin/courses")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"courseType\":1,\"difficulty\":2}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(500))
                    .andExpect(jsonPath("$.msg").value("课程名称不能为空"));
        }
        catch (Exception e)
        {
            throw new AssertionError(e);
        }
        verifyNoInteractions(courseService);
    }

    @Test
    @DisplayName("新增：课种不在 1~4 → 500「课程类型取值为 1~4」")
    void create_withInvalidCourseType_shouldReturnError()
    {
        try
        {
            mockMvc.perform(post("/admin/courses")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"哈他瑜伽\",\"courseType\":5,\"difficulty\":2}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(500))
                    .andExpect(jsonPath("$.msg").value("课程类型取值为 1~4"));
        }
        catch (Exception e)
        {
            throw new AssertionError(e);
        }
        verifyNoInteractions(courseService);
    }

    @Test
    @DisplayName("新增：难度超出 1~5 星 → 500「课程难度取值为 1~5」")
    void create_withInvalidDifficulty_shouldReturnError()
    {
        try
        {
            mockMvc.perform(post("/admin/courses")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"哈他瑜伽\",\"courseType\":1,\"difficulty\":6}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(500))
                    .andExpect(jsonPath("$.msg").value("课程难度取值为 1~5"));
        }
        catch (Exception e)
        {
            throw new AssertionError(e);
        }
        verifyNoInteractions(courseService);
    }

    @Test
    @DisplayName("新增：课程名称已存在 → 409「课程名称已存在」")
    void create_withDuplicateName_shouldReturn409()
    {
        doThrow(new ServiceException("课程名称已存在", 409)).when(courseService).create(any());

        try
        {
            mockMvc.perform(post("/admin/courses")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"哈他瑜伽\",\"courseType\":1,\"difficulty\":2}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(409))
                    .andExpect(jsonPath("$.msg").value("课程名称已存在"));
        }
        catch (Exception e)
        {
            throw new AssertionError(e);
        }
    }

    @Test
    @DisplayName("修改：返回更新后的 CourseDetailVO（课种成对）")
    void update_shouldReturnUpdatedDetail()
    {
        CourseDetailVO detail = new CourseDetailVO();
        detail.setId(1856739201475235801L);
        detail.setName("哈他瑜伽（初级）");
        detail.setCourseType(2);
        detail.setCourseTypeName("精品课");
        detail.setDifficulty(3);
        when(courseService.update(eq(1856739201475235801L), any())).thenReturn(detail);

        try
        {
            mockMvc.perform(put("/admin/courses/{courseId}", COURSE_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"哈他瑜伽（初级）\",\"courseType\":2,\"difficulty\":3}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.name").value("哈他瑜伽（初级）"))
                    .andExpect(jsonPath("$.data.courseTypeName").value("精品课"));
        }
        catch (Exception e)
        {
            throw new AssertionError(e);
        }
    }

    @Test
    @DisplayName("修改：课程不存在 → 404（业务异常经全局处理器转换）")
    void update_notExists_shouldReturn404()
    {
        doThrow(new ServiceException("课程不存在或已被删除", 404))
                .when(courseService).update(eq(1L), any());

        try
        {
            mockMvc.perform(put("/admin/courses/{courseId}", "1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"哈他瑜伽\",\"courseType\":1,\"difficulty\":2}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(404));
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
            mockMvc.perform(delete("/admin/courses/{courseId}", COURSE_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.msg").value("操作成功"));
        }
        catch (Exception e)
        {
            throw new AssertionError(e);
        }
        verify(courseService).delete(1856739201475235801L);
    }

    @Test
    @DisplayName("删除：仍有未结束排课 → 409，提示语带数量")
    void delete_withActiveSchedule_shouldReturn409()
    {
        doThrow(new ServiceException("该课程仍有 3 节未结束的排课，无法删除", 409))
                .when(courseService).delete(1856739201475235801L);

        try
        {
            mockMvc.perform(delete("/admin/courses/{courseId}", COURSE_ID))
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
                .when(courseService).delete(1856739201475235801L);

        try
        {
            mockMvc.perform(delete("/admin/courses/{courseId}", COURSE_ID))
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
            mockMvc.perform(get("/admin/courses/{courseId}", "abc"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(500))
                    .andExpect(jsonPath("$.msg").value(Matchers.containsString("courseId")));
        }
        catch (Exception e)
        {
            throw new AssertionError(e);
        }
        verifyNoInteractions(courseService);
    }

    @Test
    @DisplayName("状态接口已下线：PUT /admin/courses/{courseId}/status 不再存在")
    void statusEndpoint_shouldNotExist()
    {
        try
        {
            mockMvc.perform(put("/admin/courses/{courseId}/status", COURSE_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"status\":0}"));
        }
        catch (Exception e)
        {
            // standalone MockMvc 对未映射路径直接抛异常，这里只要求它确实没有落到 controller 上
            verifyNoInteractions(courseService);
            return;
        }
        verifyNoInteractions(courseService);
    }
}
