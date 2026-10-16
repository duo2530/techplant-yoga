package com.techplant.yoga.reservation.controller;

import lombok.RequiredArgsConstructor;

import javax.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.common.constant.HttpStatus;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.page.TableDataInfo;
import com.techplant.yoga.common.response.PageResult;
import com.techplant.yoga.reservation.query.ReservationAdminQuery;
import com.techplant.yoga.reservation.service.ReservationService;
import com.techplant.yoga.reservation.vo.ReservationAdminItemVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;

/** 管理端预约接口。 */
@Api(tags = "预约管理")
@Validated
@RestController
@RequestMapping("/admin/reservations")
@RequiredArgsConstructor
public class ReservationController extends BaseController {
    private final ReservationService reservationService;

    /** 查询预约列表：按排课查询名单也使用本接口，默认包含取消记录 */
    @ApiOperation("查询预约列表")
    @GetMapping
    public TableDataInfo list(@Valid ReservationAdminQuery query) {
        PageResult<ReservationAdminItemVO> page = reservationService.pageForAdmin(query);
        TableDataInfo dataTable = new TableDataInfo();
        dataTable.setCode(HttpStatus.SUCCESS);
        dataTable.setMsg("查询成功");
        dataTable.setRows(page.getList());
        dataTable.setTotal(page.getTotal() == null ? 0L : page.getTotal());
        return dataTable;
    }
}
