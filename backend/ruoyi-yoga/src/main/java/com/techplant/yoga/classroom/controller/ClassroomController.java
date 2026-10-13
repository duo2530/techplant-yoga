package com.techplant.yoga.classroom.controller;

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
import com.techplant.yoga.classroom.dto.ClassroomCreateDTO;
import com.techplant.yoga.classroom.dto.ClassroomUpdateDTO;
import com.techplant.yoga.classroom.query.ClassroomQuery;
import com.techplant.yoga.classroom.service.ClassroomService;
import com.techplant.yoga.classroom.vo.ClassroomListItemVO;
import com.techplant.yoga.common.response.PageResult;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;

/** 管理端教室管理接口。 */
@Api(tags = "管理端-教室管理")
@RestController
@RequestMapping("/admin/classrooms")
public class ClassroomController extends BaseController
{
    private final ClassroomService classroomService;

    public ClassroomController(ClassroomService classroomService)
    {
        this.classroomService = classroomService;
    }

    /**
     * 查询教室列表：按 {@code store_id ASC, name ASC, id DESC} 稳定排序；storeName 批量补齐
     */
    @ApiOperation("查询教室列表")
    @GetMapping
    public TableDataInfo list(@Valid ClassroomQuery query)
    {
        PageResult<ClassroomListItemVO> page = classroomService.page(query);
        TableDataInfo dataTable = new TableDataInfo();
        dataTable.setCode(HttpStatus.SUCCESS);
        dataTable.setMsg("查询成功");
        dataTable.setRows(page.getList());
        dataTable.setTotal(page.getTotal() == null ? 0L : page.getTotal());
        return dataTable;
    }

    /**
     * 新增教室：成功只返回 {@code {"code":200,"msg":"操作成功"}}，<b>不返回新 ID</b>
     */
    @ApiOperation("新增教室")
    @PostMapping
    public AjaxResult create(@Valid @RequestBody ClassroomCreateDTO dto)
    {
        classroomService.create(dto);
        return success();
    }

    /**
     * 修改教室：请求体只有 {@code name}（{@code storeId} 不在模型里）；成功只返回统一成功响应
     */
    @ApiOperation("修改教室（只改名称）")
    @PutMapping("/{classroomId}")
    public AjaxResult update(@PathVariable("classroomId") Long classroomId, @Valid @RequestBody ClassroomUpdateDTO dto)
    {
        classroomService.update(classroomId, dto);
        return success();
    }

    /**
     * 删除教室：<b>物理删除</b>；被「未结束且待上架或已上架」的排课引用时业务码 409
     */
    @ApiOperation("删除教室")
    @DeleteMapping("/{classroomId}")
    public AjaxResult delete(@PathVariable("classroomId") Long classroomId)
    {
        classroomService.delete(classroomId);
        return success();
    }
}
