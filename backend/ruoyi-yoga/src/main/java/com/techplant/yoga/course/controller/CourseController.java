package com.techplant.yoga.course.controller;

import javax.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.common.constant.HttpStatus;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
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
 * <p><b>响应口径：</b>与若依既有接口/AjaxResult 体系统一（2026-09-22 决策）—— 单条操作用
 * {@link AjaxResult}（{@code {code, msg, data}}，成功 {@code code = 200}），列表用
 * {@link TableDataInfo}（{@code {total, rows, code, msg}}）；失败由框架自带的
 * {@code com.ruoyi.framework.web.exception.GlobalExceptionHandler} 统一转成 {@code AjaxResult}，
 * HTTP 状态码固定 200，业务结果看 {@code code}。</p>
 *
 * <p><b>没有删除接口</b>：管理端功能清单没有「删除课程」，表上的 {@code deleted} 只用于数据留痕（§2.2）。
 * <b>状态不在编辑表单里</b>：新增/修改都不含 {@code status}，状态变更只走接口 5。</p>
 *
 * <p>controller 只调 service、用 {@code @Valid} 做参数校验、装配响应，不写业务逻辑（§3.1.2、§3.2.3）。</p>
 */
@Api(tags = "管理端-课程管理")
@RestController
@RequestMapping("/admin/courses")
public class CourseController extends BaseController
{
    private final CourseService courseService;

    public CourseController(CourseService courseService)
    {
        this.courseService = courseService;
    }

    /**
     * 查询课程列表（§2.2.1）：支持名称模糊、类型、状态筛选，只返回未删除的课程
     *
     * <p>分页由 service 用 MyBatis-Plus 完成（页码/每页条数的默认值与上限校验见 {@link CourseQuery}），
     * 这里只把结果装配成若依标准的 {@link TableDataInfo}。</p>
     */
    @ApiOperation("查询课程列表")
    @GetMapping
    public TableDataInfo list(@Valid CourseQuery query)
    {
        PageResult<CourseListItemVO> page = courseService.page(query);
        TableDataInfo dataTable = new TableDataInfo();
        dataTable.setCode(HttpStatus.SUCCESS);
        dataTable.setMsg("查询成功");
        dataTable.setRows(page.getList());
        dataTable.setTotal(page.getTotal() == null ? 0L : page.getTotal());
        return dataTable;
    }

    /**
     * 查询课程详情（§2.2.2）：课程不存在或已删除 → 业务码 404
     */
    @ApiOperation("查询课程详情")
    @GetMapping("/{courseId}")
    public AjaxResult detail(@PathVariable("courseId") Long courseId)
    {
        return AjaxResult.success(courseService.getById(courseId));
    }

    /**
     * 新增课程（§2.2.3）：请求体不含 status，新课程默认启用；返回新课程编号
     */
    @ApiOperation("新增课程")
    @PostMapping
    public AjaxResult create(@Valid @RequestBody CourseCreateDTO dto)
    {
        CourseCreatedVO created = courseService.create(dto);
        return AjaxResult.success(created);
    }

    /**
     * 修改课程（§2.2.4）：PUT 全量提交，不含 status；返回更新后的详情
     */
    @ApiOperation("修改课程")
    @PutMapping("/{courseId}")
    public AjaxResult update(@PathVariable("courseId") Long courseId, @Valid @RequestBody CourseUpdateDTO dto)
    {
        return AjaxResult.success(courseService.update(courseId, dto));
    }

    /**
     * 设置课程状态（§2.2.5）：停用前做引用检查，被引用时业务码 409（提示语含阻塞明细）
     */
    @ApiOperation("设置课程状态")
    @PutMapping("/{courseId}/status")
    public AjaxResult updateStatus(@PathVariable("courseId") Long courseId, @Valid @RequestBody CourseStatusDTO dto)
    {
        courseService.updateStatus(courseId, dto.getStatus());
        return success();
    }
}
