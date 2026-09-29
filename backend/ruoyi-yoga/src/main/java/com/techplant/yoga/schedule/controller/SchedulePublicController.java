package com.techplant.yoga.schedule.controller;

import java.time.LocalDateTime;
import javax.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.common.annotation.Anonymous;
import com.ruoyi.common.constant.HttpStatus;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.page.TableDataInfo;
import com.techplant.yoga.common.response.PageResult;
import com.techplant.yoga.schedule.query.ScheduleQuery;
import com.techplant.yoga.schedule.service.ScheduleService;
import com.techplant.yoga.schedule.vo.ScheduleListItemVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;

/** 用户端排班查询接口。 */
@Api(tags = "用户端-排班")
@Anonymous
@RestController
@RequestMapping("/api/schedules")
public class SchedulePublicController extends BaseController
{
    private final ScheduleService scheduleService;
    public SchedulePublicController(ScheduleService scheduleService) { this.scheduleService = scheduleService; }

    @ApiOperation("查询可预约排班")
    @GetMapping
    public TableDataInfo list(@Valid ScheduleQuery query)
    {
        if (query.getStatus() == null)
        {
            query.setStatus(1);
        }
        if (query.getStartTimeFrom() == null)
        {
            query.setStartTimeFrom(LocalDateTime.now());
        }
        PageResult<ScheduleListItemVO> page = scheduleService.page(query);
        TableDataInfo result = new TableDataInfo();
        result.setCode(HttpStatus.SUCCESS);
        result.setMsg("查询成功");
        result.setRows(page.getList());
        result.setTotal(page.getTotal() == null ? 0L : page.getTotal());
        return result;
    }
}
