package com.techplant.yoga.coach.controller;

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
import com.techplant.yoga.coach.dto.CoachCreateDTO;
import com.techplant.yoga.coach.dto.CoachUpdateDTO;
import com.techplant.yoga.coach.query.CoachQuery;
import com.techplant.yoga.coach.service.CoachService;
import com.techplant.yoga.coach.vo.CoachListItemVO;
import com.techplant.yoga.common.response.PageResult;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;

/**
 * 管理端教练管理接口（教练管理详细设计 §2.2.1~§2.2.5，共 5 个）。
 *
 * <p>教练<b>没有状态接口</b>；一期用户端<b>不提供</b>教练列表与详情接口（BR-用户端-010）。
 * 管理端接口只要求登录，不发明权限串（详细设计总览 §2）。</p>
 */
@Api(tags = "管理端-教练管理")
@RestController
@RequestMapping("/admin/coaches")
public class CoachController extends BaseController
{
    private final CoachService coachService;

    public CoachController(CoachService coachService)
    {
        this.coachService = coachService;
    }

    /** §2.2.1 查询教练列表 */
    @ApiOperation("查询教练列表")
    @GetMapping
    public TableDataInfo list(@Valid CoachQuery query)
    {
        PageResult<CoachListItemVO> page = coachService.page(query);
        TableDataInfo dataTable = new TableDataInfo();
        dataTable.setCode(HttpStatus.SUCCESS);
        dataTable.setMsg("查询成功");
        dataTable.setRows(page.getList());
        dataTable.setTotal(page.getTotal() == null ? 0L : page.getTotal());
        return dataTable;
    }

    /** §2.2.2 查询教练详情；不存在 → 404 */
    @ApiOperation("查询教练详情")
    @GetMapping("/{coachId}")
    public AjaxResult detail(@PathVariable("coachId") Long coachId)
    {
        return AjaxResult.success(coachService.getById(coachId));
    }

    /** §2.2.3 新增教练；成功只返回 {@code {code,msg}}，不返回新 ID */
    @ApiOperation("新增教练")
    @PostMapping
    public AjaxResult create(@Valid @RequestBody CoachCreateDTO dto)
    {
        coachService.create(dto);
        return success();
    }

    /** §2.2.4 修改教练（全量编辑）；成功返回最新详情 */
    @ApiOperation("修改教练")
    @PutMapping("/{coachId}")
    public AjaxResult update(@PathVariable("coachId") Long coachId, @Valid @RequestBody CoachUpdateDTO dto)
    {
        return AjaxResult.success(coachService.update(coachId, dto));
    }

    /** §2.2.5 删除教练（物理删除，带排课删除门禁） */
    @ApiOperation("删除教练")
    @DeleteMapping("/{coachId}")
    public AjaxResult delete(@PathVariable("coachId") Long coachId)
    {
        coachService.delete(coachId);
        return success();
    }
}
