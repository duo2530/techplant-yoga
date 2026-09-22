package com.techplant.yoga.course.controller;

import javax.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.techplant.yoga.common.response.ApiResponse;
import com.techplant.yoga.common.response.PageResult;
import com.techplant.yoga.course.dto.CourseCreateDTO;
import com.techplant.yoga.course.dto.CourseStatusDTO;
import com.techplant.yoga.course.dto.CourseUpdateDTO;
import com.techplant.yoga.course.query.CourseQuery;
import com.techplant.yoga.course.service.CourseService;
import com.techplant.yoga.course.vo.CourseCreatedVO;
import com.techplant.yoga.course.vo.CourseDetailVO;
import com.techplant.yoga.course.vo.CourseListItemVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;

/**
 * 管理端课程管理接口（详细设计 §2.2）。
 *
 * <table>
 * <caption>接口清单</caption>
 * <tr><td>GET /admin/courses</td><td>查询课程列表</td></tr>
 * <tr><td>GET /admin/courses/{courseId}</td><td>查询课程详情</td></tr>
 * <tr><td>POST /admin/courses</td><td>新增课程</td></tr>
 * <tr><td>PUT /admin/courses/{courseId}</td><td>修改课程</td></tr>
 * <tr><td>PUT /admin/courses/{courseId}/status</td><td>设置课程状态</td></tr>
 * </table>
 *
 * <p><b>没有删除接口</b>：管理端功能清单没有「删除课程」，表上的 {@code deleted} 只用于数据留痕（§2.2）。
 * <b>状态不在编辑表单里</b>：新增/修改都不含 {@code status}，状态变更只走接口 5。</p>
 *
 * <p>controller 只调 service、用 {@code @Valid} 做参数校验、装配统一响应，不写业务逻辑（§3.1.2、§3.2.3）。</p>
 */
@Api(tags = "管理端-课程管理")
@RestController
@RequestMapping("/admin/courses")
public class CourseController
{
    private final CourseService courseService;

    public CourseController(CourseService courseService)
    {
        this.courseService = courseService;
    }

    /**
     * 查询课程列表（§2.2.1）：支持名称模糊、类型、状态筛选，只返回未删除的课程
     */
    @ApiOperation("查询课程列表")
    @GetMapping
    public ApiResponse<PageResult<CourseListItemVO>> list(@Valid CourseQuery query)
    {
        return ApiResponse.success(courseService.page(query));
    }

    /**
     * 查询课程详情（§2.2.2）
     */
    @ApiOperation("查询课程详情")
    @GetMapping("/{courseId}")
    public ApiResponse<CourseDetailVO> detail(@PathVariable("courseId") Long courseId)
    {
        return ApiResponse.success(courseService.getById(courseId));
    }

    /**
     * 新增课程（§2.2.3）：请求体不含 status，新课程默认启用
     */
    @ApiOperation("新增课程")
    @PostMapping
    public ApiResponse<CourseCreatedVO> create(@Valid @RequestBody CourseCreateDTO dto)
    {
        return ApiResponse.success(courseService.create(dto));
    }

    /**
     * 修改课程（§2.2.4）：PUT 全量提交，不含 status
     */
    @ApiOperation("修改课程")
    @PutMapping("/{courseId}")
    public ApiResponse<CourseDetailVO> update(@PathVariable("courseId") Long courseId,
            @Valid @RequestBody CourseUpdateDTO dto)
    {
        return ApiResponse.success(courseService.update(courseId, dto));
    }

    /**
     * 设置课程状态（§2.2.5）：停用前做引用检查，被引用返回 409 + 阻塞明细
     */
    @ApiOperation("设置课程状态")
    @PutMapping("/{courseId}/status")
    public ApiResponse<Void> updateStatus(@PathVariable("courseId") Long courseId,
            @Valid @RequestBody CourseStatusDTO dto)
    {
        courseService.updateStatus(courseId, dto.getStatus());
        return ApiResponse.success();
    }
}
