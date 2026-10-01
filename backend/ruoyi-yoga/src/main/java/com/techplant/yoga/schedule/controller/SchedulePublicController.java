package com.techplant.yoga.schedule.controller;

import javax.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
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

/**
 * 用户端排课接口（《用户端接口详细设计》§2.2、§2.3，共 2 个）。
 *
 * <p><b>免登录、只读、无身份</b>：类上标 {@link Anonymous}，由 {@code PermitAllUrlProperties}
 * 收进 permitAll 白名单；两个接口都是 {@code GET}，没有任何写操作（{@code BR-全局-002}）。</p>
 *
 * <p>可见口径是 {@code status <> 1}（待上架不可见），与管理端的删除门禁口径是两套条件；
 * 详情对「待上架」返回 404，与「不存在」同一个响应，不区分。</p>
 */
@Api(tags = "用户端-排课")
@Anonymous
@Validated
@RestController
@RequestMapping("/api/schedules")
public class SchedulePublicController extends BaseController
{
    private final ScheduleService scheduleService;

    public SchedulePublicController(ScheduleService scheduleService)
    {
        this.scheduleService = scheduleService;
    }

    /** 排课列表（约课页）：按当前门店 ＋ 课种快照 ＋ 上课日期分页查询 */
    @ApiOperation("查询排课列表（约课页）")
    @GetMapping
    public TableDataInfo list(@Valid PublicScheduleQuery query)
    {
        PageResult<ScheduleCardVO> page = scheduleService.publicPage(query);
        TableDataInfo dataTable = new TableDataInfo();
        dataTable.setCode(HttpStatus.SUCCESS);
        dataTable.setMsg("查询成功");
        // 空结果返回 []，不是 null
        dataTable.setRows(page.getList());
        dataTable.setTotal(page.getTotal() == null ? 0L : page.getTotal());
        return dataTable;
    }

    /** 排课详情（课程详情页）：未删除且 status<>1 才可见，否则一律 404 */
    @ApiOperation("查询排课详情（课程详情页）")
    @GetMapping("/{scheduleId}")
    public AjaxResult detail(@PathVariable("scheduleId") Long scheduleId)
    {
        return AjaxResult.success(scheduleService.publicDetail(scheduleId));
    }
}
