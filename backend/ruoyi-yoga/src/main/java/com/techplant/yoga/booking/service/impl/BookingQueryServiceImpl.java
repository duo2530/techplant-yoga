package com.techplant.yoga.booking.service.impl;

import org.springframework.stereotype.Service;
import com.techplant.yoga.booking.dao.BookingDao;
import com.techplant.yoga.booking.service.BookingQueryService;

/** 预约模块对外查询实现。 */
@Service
public class BookingQueryServiceImpl implements BookingQueryService
{
    private final BookingDao bookingDao;
    public BookingQueryServiceImpl(BookingDao bookingDao) { this.bookingDao = bookingDao; }
    @Override
    public long countUnfinishedByCourseId(Long courseId) { return bookingDao.countUnfinishedByCourseId(courseId); }
}
