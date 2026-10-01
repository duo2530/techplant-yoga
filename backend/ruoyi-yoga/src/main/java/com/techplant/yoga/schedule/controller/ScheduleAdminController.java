package com.techplant.yoga.schedule.controller;

import javax.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.common.constant.HttpStatus;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.techplant.yoga.common.response.PageResult;
import com.techplant.yoga.schedule.dto.ScheduleCreateDTO;
import com.techplant.yoga.schedule.dto.ScheduleUpdateDTO;
import com.techplant.yoga.schedule.query.ScheduleQuery;
import com.techplant.yoga.schedule.service.ScheduleService;
import com.techplant.yoga.schedule.vo.ScheduleDetailVO;
import com.techplant.yoga.schedule.vo.ScheduleListItemVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;

/**
 * 管理端排课管理接口（排课管理详细设计 §2.2，共 6 个）。
 *
 * <p><b>controller 只做参数校验与响应装配</b>：业务规则（校验顺序、状态流转、删除门禁）全在 service；
 * 「已结束」的判定也在 service 层（这里不做任何状态推导）。</p>
 *
 * <p>一期不做 RBAC，因此<b>不加 {@code @PreAuthorize}</b>（登录即可调用，{@code BR-全局-003}）。</p>
 */
@Api(tags = "管理端-排课管理")
@Validated
@RestController
@RequestMapping("/admin/schedules")
public class ScheduleAdminController extends BaseController
{
    private final ScheduleService scheduleService;

    public ScheduleAdminController(ScheduleService scheduleService)
    {
        this.scheduleService = scheduleService;
    }

    /** 查询排课列表（日期区间筛 schedule_date，排序 start_time ASC, id ASC） */
    @ApiOperation("查询排课列表")
    @GetMapping
    public TableDataInfo list(@Valid ScheduleQuery query)
    {
        PageResult<ScheduleListItemVO> page = scheduleService.page(query);
        TableDataInfo dataTable = new TableDataInfo();
        dataTable.setCode(HttpStatus.SUCCESS);
        dataTable.setMsg("查询成功");
        dataTable.setRows(page.getList());
        dataTable.setTotal(page.getTotal() == null ? 0L : page.getTotal());
        return dataTable;
    }

    /** 查询排课详情；不存在或已被删除 → 404「排课不存在或已被删除」 */
    @ApiOperation("查询排课详情")
    @GetMapping("/{scheduleId}")
    public AjaxResult detail(@PathVariable("scheduleId") Long scheduleId)
    {
        return AjaxResult.success(scheduleService.getById(scheduleId));
    }

    /** 新增排课；成功只返回 {@code {"code":200,"msg":"操作成功"}}，不返回新 ID */
    @ApiOperation("新增排课")
    @PostMapping
    public AjaxResult create(@Valid @RequestBody ScheduleCreateDTO dto)
    {
        scheduleService.create(dto);
        return success();
    }

    /** 修改排课（仅「待上架」可改）；成功返回更新后的详情 */
    @ApiOperation("修改排课")
    @PutMapping("/{scheduleId}")
    public AjaxResult update(@PathVariable("scheduleId") Long scheduleId,
            @Valid @RequestBody ScheduleUpdateDTO dto)
    {
        ScheduleDetailVO detail = scheduleService.update(scheduleId, dto);
        return AjaxResult.success(detail);
    }

    /**
     * 状态流转：{@code ?status=2} 上架／{@code 1} 下架／{@code 3} 取消
     *
     * <p>只有一个参数，直接用查询参数，**不建 DTO**（§2.3.4）；合法性校验与幂等都在
     * {@link ScheduleService#changeStatus(Long, Integer)} 里收口，这里不写 if-else。</p>
     */
    @ApiOperation("排课状态流转（上架／下架／取消）")
    @PutMapping("/{scheduleId}/status")
    public AjaxResult changeStatus(@PathVariable("scheduleId") Long scheduleId,
            @RequestParam("status") Integer status)
    {
        scheduleService.changeStatus(scheduleId, status);
        return success();
    }

    /** 删除排课（物理；仅「待上架」可删） */
    @ApiOperation("删除排课")
    @DeleteMapping("/{scheduleId}")
    public AjaxResult delete(@PathVariable("scheduleId") Long scheduleId)
    {
        scheduleService.delete(scheduleId);
        return success();
    }
}
