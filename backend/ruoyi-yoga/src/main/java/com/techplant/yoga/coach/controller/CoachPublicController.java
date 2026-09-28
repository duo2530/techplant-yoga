package com.techplant.yoga.coach.controller;

import javax.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.common.annotation.Anonymous;
import com.ruoyi.common.constant.HttpStatus;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.techplant.yoga.coach.query.CoachQuery;
import com.techplant.yoga.coach.service.CoachService;
import com.techplant.yoga.common.response.PageResult;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;

/** 用户端教练查询接口（详细设计 §2.2.6~§2.2.7）。 */
@Api(tags = "用户端-教练")
@Anonymous
@RestController
@RequestMapping("/api/coaches")
public class CoachPublicController extends BaseController
{
    private final CoachService coachService;

    public CoachPublicController(CoachService coachService)
    {
        this.coachService = coachService;
    }

    @ApiOperation("首页查询金牌教练")
    @GetMapping("/featured")
    public AjaxResult featured()
    {
        return AjaxResult.success(coachService.featured());
    }

    @ApiOperation("查询教练列表")
    @GetMapping
    public TableDataInfo list(@Valid CoachQuery query)
    {
        PageResult<?> page = coachService.publicPage(query);
        TableDataInfo dataTable = new TableDataInfo();
        dataTable.setCode(HttpStatus.SUCCESS);
        dataTable.setMsg("查询成功");
        dataTable.setRows(page.getList());
        dataTable.setTotal(page.getTotal() == null ? 0L : page.getTotal());
        return dataTable;
    }
}
