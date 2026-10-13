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

/** 管理端课程管理接口。 */
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
     * 查询课程列表：支持名称模糊、课种筛选；不返回 intro；课种成对返回
     *
     * <p>分页由 service 用 MyBatis-Plus 完成，这里只装配成若依标准的 {@link TableDataInfo}。</p>
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
     * 查询课程详情：课程不存在 → 业务码 404「课程不存在或已被删除」
     */
    @ApiOperation("查询课程详情")
    @GetMapping("/{courseId}")
    public AjaxResult detail(@PathVariable("courseId") Long courseId)
    {
        return AjaxResult.success(courseService.getById(courseId));
    }

    /**
     * 新增课程：成功只返回 {@code {"code":200,"msg":"操作成功"}}，<b>不返回新 ID</b>
     */
    @ApiOperation("新增课程")
    @PostMapping
    public AjaxResult create(@Valid @RequestBody CourseCreateDTO dto)
    {
        courseService.create(dto);
        return success();
    }

    /**
     * 修改课程：PUT 全量提交；成功返回更新后的 {@link CourseDetailVO}
     */
    @ApiOperation("修改课程")
    @PutMapping("/{courseId}")
    public AjaxResult update(@PathVariable("courseId") Long courseId, @Valid @RequestBody CourseUpdateDTO dto)
    {
        CourseDetailVO detail = courseService.update(courseId, dto);
        return AjaxResult.success(detail);
    }

    /**
     * 删除课程：<b>物理删除</b>，被「未结束且待上架或已上架」的排课引用时业务码 409
     */
    @ApiOperation("删除课程")
    @DeleteMapping("/{courseId}")
    public AjaxResult delete(@PathVariable("courseId") Long courseId)
    {
        courseService.delete(courseId);
        return success();
    }
}
