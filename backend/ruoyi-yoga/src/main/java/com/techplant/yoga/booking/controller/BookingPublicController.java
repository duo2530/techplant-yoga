package com.techplant.yoga.booking.controller;

import javax.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.common.constant.HttpStatus;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.techplant.yoga.booking.dto.BookingCreateDTO;
import com.techplant.yoga.booking.query.BookingQuery;
import com.techplant.yoga.booking.service.BookingService;
import com.techplant.yoga.booking.vo.BookingListItemVO;
import com.techplant.yoga.common.response.PageResult;
import com.techplant.yoga.common.util.CurrentUserUtils;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;

/** 用户端预约接口。 */
@Api(tags = "用户端-预约")
@RestController
@RequestMapping("/api/bookings")
public class BookingPublicController extends BaseController
{
    private final BookingService bookingService;
    public BookingPublicController(BookingService bookingService) { this.bookingService = bookingService; }

    @ApiOperation("创建预约")
    @PostMapping
    public AjaxResult create(@Valid @RequestBody BookingCreateDTO dto)
    {
        return AjaxResult.success(bookingService.create(currentUserId(), dto));
    }

    @ApiOperation("查询我的预约")
    @GetMapping
    public TableDataInfo list(@Valid BookingQuery query)
    {
        query.setUserId(currentUserId());
        PageResult<BookingListItemVO> page = bookingService.page(query);
        TableDataInfo result = new TableDataInfo();
        result.setCode(HttpStatus.SUCCESS);
        result.setMsg("查询成功");
        result.setRows(page.getList());
        result.setTotal(page.getTotal() == null ? 0L : page.getTotal());
        return result;
    }

    @ApiOperation("查询我的预约详情")
    @GetMapping("/{bookingId}")
    public AjaxResult detail(@PathVariable("bookingId") Long bookingId)
    {
        return AjaxResult.success(bookingService.getMyById(bookingId, currentUserId()));
    }

    private Long currentUserId()
    {
        Long userId = CurrentUserUtils.getUserIdOrNull();
        if (userId == null)
        {
            throw new com.ruoyi.common.exception.ServiceException("请先登录", 401);
        }
        return userId;
    }
}
