package com.techplant.yoga.reservation.controller;

import javax.validation.Valid;
import org.springframework.validation.annotation.Validated;
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
import com.techplant.yoga.member.security.MemberContextUtils;
import com.techplant.yoga.reservation.dto.ReservationCreateDTO;
import com.techplant.yoga.reservation.query.ReservationPublicQuery;
import com.techplant.yoga.reservation.service.ReservationService;
import com.techplant.yoga.reservation.vo.ReservationPublicItemVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;

/** 用户端预约接口：预约课程、取消预约、预约签到与我的预约查询。 */
@Api(tags = "用户端-预约")
@Validated
@RestController
@RequestMapping("/api/reservations")
public class ReservationPublicController extends BaseController
{
    private final ReservationService reservationService;

    public ReservationPublicController(ReservationService reservationService)
    {
        this.reservationService = reservationService;
    }

    /** 预约课程：会员身份只取登录态，扣次与占位在同一事务内完成 */
    @ApiOperation("预约课程")
    @PostMapping
    public AjaxResult create(@Valid @RequestBody ReservationCreateDTO dto)
    {
        reservationService.create(MemberContextUtils.requireMemberId(), dto);
        return AjaxResult.success("预约成功");
    }

    /** 取消预约：已取消的重复请求按成功返回 */
    @ApiOperation("取消预约")
    @PutMapping("/{reservationId}/cancel")
    public AjaxResult cancel(@PathVariable Long reservationId)
    {
        reservationService.cancel(MemberContextUtils.requireMemberId(), reservationId);
        return AjaxResult.success("取消预约成功");
    }

    /** 预约签到：已有签到记录的重复请求按成功返回 */
    @ApiOperation("预约签到")
    @PutMapping("/{reservationId}/check-in")
    public AjaxResult checkIn(@PathVariable Long reservationId)
    {
        reservationService.checkIn(MemberContextUtils.requireMemberId(), reservationId);
        return AjaxResult.success("签到成功");
    }

    /** 查询我的预约：会员身份只取登录态，客户端传入值不作为访问依据 */
    @ApiOperation("查询我的预约")
    @GetMapping
    public TableDataInfo list(@Valid ReservationPublicQuery query)
    {
        PageResult<ReservationPublicItemVO> page = reservationService.pageForMember(
                MemberContextUtils.requireMemberId(), query);
        TableDataInfo dataTable = new TableDataInfo();
        dataTable.setCode(HttpStatus.SUCCESS);
        dataTable.setMsg("查询成功");
        dataTable.setRows(page.getList());
        dataTable.setTotal(page.getTotal() == null ? 0L : page.getTotal());
        return dataTable;
    }
}
