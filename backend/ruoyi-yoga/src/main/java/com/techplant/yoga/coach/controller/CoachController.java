package com.techplant.yoga.coach.controller;

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
import com.techplant.yoga.coach.dto.CoachCreateDTO;
import com.techplant.yoga.coach.dto.CoachStatusDTO;
import com.techplant.yoga.coach.dto.CoachUpdateDTO;
import com.techplant.yoga.coach.query.CoachQuery;
import com.techplant.yoga.coach.service.CoachService;
import com.techplant.yoga.common.response.PageResult;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;

/** 管理端教练管理接口（详细设计 §2.2.1~§2.2.5）。 */
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

    @ApiOperation("查询教练列表")
    @GetMapping
    public TableDataInfo list(@Valid CoachQuery query)
    {
        PageResult<?> page = coachService.page(query);
        TableDataInfo dataTable = new TableDataInfo();
        dataTable.setCode(HttpStatus.SUCCESS);
        dataTable.setMsg("查询成功");
        dataTable.setRows(page.getList());
        dataTable.setTotal(page.getTotal() == null ? 0L : page.getTotal());
        return dataTable;
    }

    @ApiOperation("查询教练详情")
    @GetMapping("/{coachId}")
    public AjaxResult detail(@PathVariable("coachId") Long coachId)
    {
        return AjaxResult.success(coachService.getById(coachId));
    }

    @ApiOperation("新增教练")
    @PostMapping
    public AjaxResult create(@Valid @RequestBody CoachCreateDTO dto)
    {
        return AjaxResult.success(coachService.create(dto));
    }

    @ApiOperation("修改教练")
    @PutMapping("/{coachId}")
    public AjaxResult update(@PathVariable("coachId") Long coachId, @Valid @RequestBody CoachUpdateDTO dto)
    {
        return AjaxResult.success(coachService.update(coachId, dto));
    }

    @ApiOperation("设置教练状态")
    @PutMapping("/{coachId}/status")
    public AjaxResult updateStatus(@PathVariable("coachId") Long coachId,
            @Valid @RequestBody CoachStatusDTO dto)
    {
        coachService.updateStatus(coachId, dto.getStatus());
        return success();
    }
}
