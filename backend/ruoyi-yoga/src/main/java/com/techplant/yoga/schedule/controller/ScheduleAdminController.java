package com.techplant.yoga.schedule.controller;

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
import com.techplant.yoga.schedule.dto.ScheduleCreateDTO;
import com.techplant.yoga.schedule.dto.ScheduleStatusDTO;
import com.techplant.yoga.schedule.dto.ScheduleUpdateDTO;
import com.techplant.yoga.schedule.query.ScheduleQuery;
import com.techplant.yoga.schedule.service.ScheduleService;
import com.techplant.yoga.schedule.vo.ScheduleDetailVO;
import com.techplant.yoga.schedule.vo.ScheduleListItemVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;

/** 管理端排班管理接口。 */
@Api(tags = "管理端-排班管理")
@RestController
@RequestMapping("/admin/schedules")
public class ScheduleAdminController extends BaseController
{
    private final ScheduleService scheduleService;
    public ScheduleAdminController(ScheduleService scheduleService) { this.scheduleService = scheduleService; }

    @ApiOperation("查询排班列表")
    @GetMapping
    public TableDataInfo list(@Valid ScheduleQuery query)
    {
        PageResult<ScheduleListItemVO> page = scheduleService.page(query);
        TableDataInfo result = new TableDataInfo();
        result.setCode(HttpStatus.SUCCESS);
        result.setMsg("查询成功");
        result.setRows(page.getList());
        result.setTotal(page.getTotal() == null ? 0L : page.getTotal());
        return result;
    }

    @ApiOperation("查询排班详情")
    @GetMapping("/{scheduleId}")
    public AjaxResult detail(@PathVariable("scheduleId") Long scheduleId)
    {
        return AjaxResult.success(scheduleService.getById(scheduleId));
    }

    @ApiOperation("新增排班")
    @PostMapping
    public AjaxResult create(@Valid @RequestBody ScheduleCreateDTO dto)
    {
        return AjaxResult.success(scheduleService.create(dto));
    }

    @ApiOperation("修改排班")
    @PutMapping("/{scheduleId}")
    public AjaxResult update(@PathVariable("scheduleId") Long scheduleId,
            @Valid @RequestBody ScheduleUpdateDTO dto)
    {
        ScheduleDetailVO detail = scheduleService.update(scheduleId, dto);
        return AjaxResult.success(detail);
    }

    @ApiOperation("设置排班状态")
    @PutMapping("/{scheduleId}/status")
    public AjaxResult updateStatus(@PathVariable("scheduleId") Long scheduleId,
            @Valid @RequestBody ScheduleStatusDTO dto)
    {
        scheduleService.updateStatus(scheduleId, dto);
        return success();
    }
}
