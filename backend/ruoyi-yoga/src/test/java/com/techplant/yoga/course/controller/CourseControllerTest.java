package com.techplant.yoga.course.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.techplant.yoga.common.config.JacksonConfig;
import com.techplant.yoga.common.exception.BusinessExceptionHandler;
import com.techplant.yoga.course.exception.CourseReferencedException;
import com.techplant.yoga.course.service.CourseService;
import com.techplant.yoga.course.vo.CourseCreatedVO;

/**
 * 课程接口层的单元测试（详细设计 §5.1.1，用例 5.1.1.15 ~ 5.1.1.21）。
 *
 * <p>业务服务用模拟对象隔离；用 MockMvc 走真实的参数绑定、校验与异常转换链路，
 * 其中序列化用 {@link JacksonConfig} 的 ObjectMapper，保证「课程编号返回字符串」这条契约被测到。</p>
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
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new BusinessExceptionHandler())
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
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.message").value("success"))
                .andExpect(jsonPath("$.data").doesNotExist());

        // 业务服务收到的是数值型课程编号
        verify(courseService).updateStatus(eq(1856739201475235840L), eq(0));
    }

    @Test
    @DisplayName("5.1.1.16 停用课程接口：课程编号不是数字 → 返回参数错误")
    void updateStatus_withInvalidId_shouldReturn400() throws Exception
    {
        mockMvc.perform(put("/admin/courses/{courseId}/status", "abc")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));

        verifyNoInteractions(courseService);
    }

    @Test
    @DisplayName("5.1.1.17 停用课程接口：状态取值非法 → 返回参数错误")
    void updateStatus_withInvalidStatus_shouldReturn400() throws Exception
    {
        mockMvc.perform(put("/admin/courses/{courseId}/status", COURSE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":2}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));

        verifyNoInteractions(courseService);
    }

    @Test
    @DisplayName("5.1.1.18 新增课程接口：必填项缺失 → 返回参数错误")
    void createCourse_withoutName_shouldReturn400() throws Exception
    {
        mockMvc.perform(post("/admin/courses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":1,\"difficulty\":2}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("name")));

        verifyNoInteractions(courseService);
    }

    @Test
    @DisplayName("5.1.1.19 新增课程接口：难度超出 1~5 星 → 返回参数错误")
    void createCourse_withInvalidDifficulty_shouldReturn400() throws Exception
    {
        mockMvc.perform(post("/admin/courses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"哈他瑜伽\",\"type\":1,\"difficulty\":6}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));

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
                .andExpect(jsonPath("$.code").value(0))
                // 带引号的字符串，不是数字（雪花ID 超出 JS 53 位精度）
                .andExpect(jsonPath("$.data.id").value("1856739201475235840"));
    }

    @Test
    @DisplayName("5.1.1.21 业务异常被统一转换成响应（409 + 阻塞明细）")
    void updateStatus_withReference_shouldReturn409() throws Exception
    {
        org.mockito.Mockito.doThrow(new CourseReferencedException(3L, 5L))
                .when(courseService).updateStatus(eq(1856739201475235840L), eq(0));

        mockMvc.perform(put("/admin/courses/{courseId}/status", COURSE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":0}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(409))
                .andExpect(jsonPath("$.message")
                        .value(org.hamcrest.Matchers.containsString("3 个未完成排班")))
                .andExpect(jsonPath("$.message")
                        .value(org.hamcrest.Matchers.containsString("5 条未结束预约")))
                .andExpect(jsonPath("$.data.scheduleCount").value(3))
                .andExpect(jsonPath("$.data.bookingCount").value(5));
    }
}
