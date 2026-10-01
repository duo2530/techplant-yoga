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

/**
 * 管理端教室管理接口（教室管理详细设计 §2.2）。
 *
 * <table>
 * <caption>接口清单（4 个，<b>没有详情接口</b>）</caption>
 * <tr><td>GET /admin/classrooms</td><td>查询教室列表</td></tr>
 * <tr><td>POST /admin/classrooms</td><td>新增教室（不返回新 ID）</td></tr>
 * <tr><td>PUT /admin/classrooms/{classroomId}</td><td>修改教室（只改名称）</td></tr>
 * <tr><td>DELETE /admin/classrooms/{classroomId}</td><td>删除教室（物理，带排课门禁）</td></tr>
 * </table>
 *
 * <p>教室不提供详情接口：字段只有「所属门店 ＋ 名称」，列表项已含全部字段，编辑弹窗直接用列表行数据回填。</p>
 *
 * <p>controller 只调 service、用 {@code @Valid} 做参数校验、装配响应，不写业务逻辑。</p>
 */
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
     * 查询教室列表（§2.2.1）：按 {@code store_id ASC, name ASC, id DESC} 稳定排序；storeName 批量补齐
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
     * 新增教室（§2.2.2）：成功只返回 {@code {"code":200,"msg":"操作成功"}}，<b>不返回新 ID</b>
     */
    @ApiOperation("新增教室")
    @PostMapping
    public AjaxResult create(@Valid @RequestBody ClassroomCreateDTO dto)
    {
        classroomService.create(dto);
        return success();
    }

    /**
     * 修改教室（§2.2.3）：请求体只有 {@code name}（{@code storeId} 不在模型里）；成功只返回统一成功响应
     */
    @ApiOperation("修改教室（只改名称）")
    @PutMapping("/{classroomId}")
    public AjaxResult update(@PathVariable("classroomId") Long classroomId, @Valid @RequestBody ClassroomUpdateDTO dto)
    {
        classroomService.update(classroomId, dto);
        return success();
    }

    /**
     * 删除教室（§2.2.4）：<b>物理删除</b>；被「未结束且待上架或已上架」的排课引用时业务码 409
     */
    @ApiOperation("删除教室")
    @DeleteMapping("/{classroomId}")
    public AjaxResult delete(@PathVariable("classroomId") Long classroomId)
    {
        classroomService.delete(classroomId);
        return success();
    }
}
