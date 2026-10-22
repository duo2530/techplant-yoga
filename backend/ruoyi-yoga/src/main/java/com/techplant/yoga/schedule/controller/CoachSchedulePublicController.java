package com.techplant.yoga.schedule.controller;

import lombok.RequiredArgsConstructor;

import javax.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.common.annotation.Anonymous;
import com.ruoyi.common.constant.HttpStatus;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.page.TableDataInfo;
import com.techplant.yoga.common.response.PageResult;
import com.techplant.yoga.schedule.query.CoachScheduleQuery;
import com.techplant.yoga.schedule.service.ScheduleService;
import com.techplant.yoga.schedule.vo.ScheduleCardVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;

/** 用户端教练排课接口（免登录只读）。 */
@Api(tags = "用户端-教练排课")
@Anonymous
@RestController
@RequestMapping("/api/coaches/{coachId}/schedules")
@RequiredArgsConstructor
public class CoachSchedulePublicController extends BaseController {
    private final ScheduleService scheduleService;

    /** 当前门店、所选教练与课种的近期排课（今天起 14 天，含今天） */
    @ApiOperation("查询教练排课列表")
    @GetMapping
    public TableDataInfo list(@PathVariable("coachId") Long coachId, @Valid CoachScheduleQuery query) {
        PageResult<ScheduleCardVO> page = scheduleService.publicPageByCoach(coachId, query);
        TableDataInfo dataTable = new TableDataInfo();
        dataTable.setCode(HttpStatus.SUCCESS);
        dataTable.setMsg("查询成功");
        dataTable.setRows(page.getList());
        dataTable.setTotal(page.getTotal() == null ? 0L : page.getTotal());
        return dataTable;
    }
}
