package com.techplant.yoga.booking.service;

import com.techplant.yoga.booking.dto.BookingCreateDTO;
import com.techplant.yoga.booking.query.BookingQuery;
import com.techplant.yoga.booking.vo.BookingDetailVO;
import com.techplant.yoga.booking.vo.BookingListItemVO;
import com.techplant.yoga.common.response.PageResult;

/** 预约业务服务。 */
public interface BookingService
{
    PageResult<BookingListItemVO> page(BookingQuery query);
    BookingDetailVO getById(Long bookingId);
    BookingDetailVO getMyById(Long bookingId, Long userId);
    BookingDetailVO create(Long userId, BookingCreateDTO dto);
}
