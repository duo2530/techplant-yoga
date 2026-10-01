package com.techplant.yoga.course.controller;

import javax.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
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
import com.techplant.yoga.course.dto.CourseUpdateDTO;
import com.techplant.yoga.course.query.CourseQuery;
import com.techplant.yoga.course.service.CourseService;
import com.techplant.yoga.course.vo.CourseDetailVO;
import com.techplant.yoga.course.vo.CourseListItemVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;

/**
 * 管理端课程管理接口（课程管理详细设计 §2.2）。
 *
 * <table>
 * <caption>接口清单（5 个）</caption>
 * <tr><td>GET /admin/courses</td><td>查询课程列表</td></tr>
 * <tr><td>GET /admin/courses/{courseId}</td><td>查询课程详情</td></tr>
 * <tr><td>POST /admin/courses</td><td>新增课程（不返回新 ID）</td></tr>
 * <tr><td>PUT /admin/courses/{courseId}</td><td>修改课程（全量编辑，返回详情）</td></tr>
 * <tr><td>DELETE /admin/courses/{courseId}</td><td>删除课程（物理，带排课门禁）</td></tr>
 * </table>
 *
 * <p><b>没有状态接口</b>：课程无状态，下架通过删除实现（BR-课程-007）；
 * 也没有 {@code PUT /{courseId}/status}。</p>
 *
 * <p>controller 只调 service、用 {@code @Valid} 做参数校验、装配响应，不写业务逻辑；
 * 失败由框架的 {@code GlobalExceptionHandler} 统一转成 {@code AjaxResult}，HTTP 固定 200，业务结果看 {@code code}。</p>
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
     * 查询课程列表（§2.2.1）：支持名称模糊、课种筛选；不返回 intro；课种成对返回
     *
     * <p>分页由 service 用 MyBatis-Plus 完成，这里只装配成若依标准的 {@link TableDataInfo}（§8.8：不用 startPage）。</p>
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
     * 查询课程详情（§2.2.2）：课程不存在 → 业务码 404「课程不存在或已被删除」
     */
    @ApiOperation("查询课程详情")
    @GetMapping("/{courseId}")
    public AjaxResult detail(@PathVariable("courseId") Long courseId)
    {
        return AjaxResult.success(courseService.getById(courseId));
    }

    /**
     * 新增课程（§2.2.3）：成功只返回 {@code {"code":200,"msg":"操作成功"}}，<b>不返回新 ID</b>
     */
    @ApiOperation("新增课程")
    @PostMapping
    public AjaxResult create(@Valid @RequestBody CourseCreateDTO dto)
    {
        courseService.create(dto);
        return success();
    }

    /**
     * 修改课程（§2.2.4）：PUT 全量提交；成功返回更新后的 {@link CourseDetailVO}
     */
    @ApiOperation("修改课程")
    @PutMapping("/{courseId}")
    public AjaxResult update(@PathVariable("courseId") Long courseId, @Valid @RequestBody CourseUpdateDTO dto)
    {
        CourseDetailVO detail = courseService.update(courseId, dto);
        return AjaxResult.success(detail);
    }

    /**
     * 删除课程（§2.2.5）：<b>物理删除</b>，被「未结束且待上架或已上架」的排课引用时业务码 409
     */
    @ApiOperation("删除课程")
    @DeleteMapping("/{courseId}")
    public AjaxResult delete(@PathVariable("courseId") Long courseId)
    {
        courseService.delete(courseId);
        return success();
    }
}
