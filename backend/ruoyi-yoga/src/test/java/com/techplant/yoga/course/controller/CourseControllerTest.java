package com.techplant.yoga.course.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
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
import com.techplant.yoga.course.vo.CourseCreatedVO;
import com.techplant.yoga.course.vo.CourseListItemVO;

/**
 * 课程接口层的单元测试（详细设计 §5.1.1，用例 5.1.1.15 ~ 5.1.1.21）。
 *
 * <p>业务服务用模拟对象隔离；用 MockMvc 走真实的参数绑定、校验与异常转换链路。
 * <b>响应口径（2026-09-22 决策）：</b>统一若依体系 —— 单条操作用 {@code AjaxResult}
 * （{@code {code, msg, data}}，成功 {@code code = 200}），列表用 {@code TableDataInfo}
 * （{@code {total, rows, code, msg}}）；失败由框架自带的 {@link GlobalExceptionHandler} 转换，
 * HTTP 状态码固定 200，业务结果看 {@code code}。</p>
 *
 * <p>序列化用 {@link JacksonConfig} 的 ObjectMapper，保证「课程编号返回字符串」这条契约被测到。</p>
 */
@DisplayName("CourseController 单元测试")
class CourseControllerTest
{
    /** 接口示例里的课程编号 */
    private static final String COURSE_ID = "1856739201475235840";

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
    @DisplayName("5.1.1.15 停用课程接口：参数合法 → 返回成功")
    void updateStatus_shouldReturnSuccess() throws Exception
    {
        mockMvc.perform(put("/admin/courses/{courseId}/status", COURSE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.msg").value("操作成功"));

        // 业务服务收到的是数值型课程编号
        verify(courseService).updateStatus(eq(1856739201475235840L), eq(0));
    }

    @Test
    @DisplayName("5.1.1.16 停用课程接口：课程编号不是数字 → 返回参数错误")
    void updateStatus_withInvalidId_shouldReturnError() throws Exception
    {
        mockMvc.perform(put("/admin/courses/{courseId}/status", "abc")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.msg").value(Matchers.containsString("courseId")));

        verifyNoInteractions(courseService);
    }

    @Test
    @DisplayName("5.1.1.17 停用课程接口：状态取值非法 → 返回参数错误")
    void updateStatus_withInvalidStatus_shouldReturnError() throws Exception
    {
        mockMvc.perform(put("/admin/courses/{courseId}/status", COURSE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.msg").value(Matchers.containsString("课程状态取值")));

        verifyNoInteractions(courseService);
    }

    @Test
    @DisplayName("5.1.1.18 新增课程接口：必填项缺失 → 返回参数错误")
    void createCourse_withoutName_shouldReturnError() throws Exception
    {
        mockMvc.perform(post("/admin/courses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":1,\"difficulty\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.msg").value("课程名称不能为空"));

        verifyNoInteractions(courseService);
    }

    @Test
    @DisplayName("5.1.1.19 新增课程接口：难度超出 1~5 星 → 返回参数错误")
    void createCourse_withInvalidDifficulty_shouldReturnError() throws Exception
    {
        mockMvc.perform(post("/admin/courses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"哈他瑜伽\",\"type\":1,\"difficulty\":6}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.msg").value("课程难度取值为 1~5"));

        verifyNoInteractions(courseService);
    }

    @Test
    @DisplayName("5.1.1.20 接口返回中的课程编号是字符串")
    void createCourse_shouldReturnIdAsString() throws Exception
    {
        when(courseService.create(any())).thenReturn(new CourseCreatedVO(1856739201475235840L));

        mockMvc.perform(post("/admin/courses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"哈他瑜伽\",\"type\":1,\"difficulty\":2,\"sortNo\":10}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                // 带引号的字符串，不是数字（雪花ID 超出 JS 53 位精度）
                .andExpect(jsonPath("$.data.id").value("1856739201475235840"));
    }

    @Test
    @DisplayName("5.1.1.21 业务异常被框架统一转换成响应（业务码 409 + 阻塞明细在提示语里）")
    void updateStatus_withReference_shouldReturnConflictCode() throws Exception
    {
        doThrow(new ServiceException("该课程下仍有 3 个未完成排班、5 条未结束预约，无法停用", 409))
                .when(courseService).updateStatus(eq(1856739201475235840L), eq(0));

        mockMvc.perform(put("/admin/courses/{courseId}/status", COURSE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(409))
                .andExpect(jsonPath("$.msg").value(Matchers.containsString("3 个未完成排班")))
                .andExpect(jsonPath("$.msg").value(Matchers.containsString("5 条未结束预约")));
    }

    @Test
    @DisplayName("补充：列表接口返回若依标准的 TableDataInfo（total / rows / code / msg）")
    void list_shouldReturnTableDataInfo() throws Exception
    {
        List<CourseListItemVO> rows = new ArrayList<CourseListItemVO>();
        CourseListItemVO vo = new CourseListItemVO();
        vo.setId(1856739201475235840L);
        vo.setName("哈他瑜伽");
        vo.setType(1);
        vo.setDifficulty(2);
        vo.setStatus(1);
        vo.setSortNo(10);
        vo.setCreateTime(LocalDateTime.of(2026, 9, 20, 10, 12, 33));
        rows.add(vo);
        when(courseService.page(any())).thenReturn(PageResult.of(2L, 1, 10, rows));

        mockMvc.perform(get("/admin/courses").param("pageNum", "1").param("pageSize", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.msg").value("查询成功"))
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.rows[0].id").value("1856739201475235840"))
                .andExpect(jsonPath("$.rows[0].name").value("哈他瑜伽"))
                .andExpect(jsonPath("$.rows[0].intro").doesNotExist());
    }

    @Test
    @DisplayName("补充：课程不存在时详情接口返回业务码 404")
    void detail_notExists_shouldReturn404Code() throws Exception
    {
        doThrow(new ServiceException("课程不存在或已被删除", 404)).when(courseService).getById(1L);

        mockMvc.perform(get("/admin/courses/{courseId}", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.msg").value("课程不存在或已被删除"));
    }
}
