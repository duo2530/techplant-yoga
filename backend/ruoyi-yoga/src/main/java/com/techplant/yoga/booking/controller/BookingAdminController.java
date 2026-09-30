package com.techplant.yoga.booking.controller;

import javax.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.common.constant.HttpStatus;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.techplant.yoga.booking.query.BookingQuery;
import com.techplant.yoga.booking.service.BookingService;
import com.techplant.yoga.booking.vo.BookingListItemVO;
import com.techplant.yoga.common.response.PageResult;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;

/** 管理端预约查询接口。 */
@Api(tags = "管理端-预约管理")
@RestController
@RequestMapping("/admin/bookings")
public class BookingAdminController extends BaseController
{
    private final BookingService bookingService;
    public BookingAdminController(BookingService bookingService) { this.bookingService = bookingService; }

    @ApiOperation("查询预约列表")
    @GetMapping
    public TableDataInfo list(@Valid BookingQuery query) { return table(bookingService.page(query)); }

    @ApiOperation("查询预约详情")
    @GetMapping("/{bookingId}")
    public AjaxResult detail(@PathVariable("bookingId") Long bookingId)
    {
        return AjaxResult.success(bookingService.getById(bookingId));
    }

    @ApiOperation("查询排班预约列表")
    @GetMapping("/schedule/{scheduleId}")
    public TableDataInfo bySchedule(@PathVariable("scheduleId") Long scheduleId, @Valid BookingQuery query)
    {
        query.setScheduleId(scheduleId);
        return table(bookingService.page(query));
    }

    private TableDataInfo table(PageResult<BookingListItemVO> page)
    {
        TableDataInfo result = new TableDataInfo();
        result.setCode(HttpStatus.SUCCESS);
        result.setMsg("查询成功");
        result.setRows(page.getList());
        result.setTotal(page.getTotal() == null ? 0L : page.getTotal());
        return result;
    }
}
