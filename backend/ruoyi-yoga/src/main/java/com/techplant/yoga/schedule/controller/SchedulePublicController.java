package com.techplant.yoga.schedule.controller;

import lombok.RequiredArgsConstructor;

import javax.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.common.annotation.Anonymous;
import com.ruoyi.common.constant.HttpStatus;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.techplant.yoga.common.response.PageResult;
import com.techplant.yoga.schedule.query.PublicScheduleQuery;
import com.techplant.yoga.schedule.service.ScheduleService;
import com.techplant.yoga.schedule.vo.ScheduleCardVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;

/** 用户端排课接口。 */
@Api(tags = "用户端-排课")
@Anonymous
@Validated
@RestController
@RequestMapping("/api/schedules")
@RequiredArgsConstructor
public class SchedulePublicController extends BaseController {
    private final ScheduleService scheduleService;

    /** 排课列表（约课页）：按当前门店 ＋ 课种快照 ＋ 上课日期分页查询 */
    @ApiOperation("查询排课列表（约课页）")
    @GetMapping
    public TableDataInfo list(@Valid PublicScheduleQuery query) {
        PageResult<ScheduleCardVO> page = scheduleService.publicPage(query);
        TableDataInfo dataTable = new TableDataInfo();
        dataTable.setCode(HttpStatus.SUCCESS);
        dataTable.setMsg("查询成功");
        // 空结果返回 []，不是 null
        dataTable.setRows(page.getList());
        dataTable.setTotal(page.getTotal() == null ? 0L : page.getTotal());
        return dataTable;
    }

    /** 首页今日可约团课：当前门店今天仍可约的团课，最多 2 条 */
    @ApiOperation("查询首页今日可约团课")
    @GetMapping("/today-bookable")
    public AjaxResult todayBookable(@RequestParam("storeId") Long storeId) {
        return AjaxResult.success(scheduleService.publicTodayBookable(storeId));
    }

    /** 排课详情（课程详情页）：未删除且 status<>1 才可见，否则一律 404 */
    @ApiOperation("查询排课详情（课程详情页）")
    @GetMapping("/{scheduleId}")
    public AjaxResult detail(@PathVariable("scheduleId") Long scheduleId) {
        return AjaxResult.success(scheduleService.publicDetail(scheduleId));
    }
}
