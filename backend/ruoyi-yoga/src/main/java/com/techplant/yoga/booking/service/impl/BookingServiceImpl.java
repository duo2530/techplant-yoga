package com.techplant.yoga.booking.service.impl;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.ruoyi.common.exception.ServiceException;
import com.techplant.yoga.booking.convert.BookingConverter;
import com.techplant.yoga.booking.dao.BookingDao;
import com.techplant.yoga.booking.domain.BookingDO;
import com.techplant.yoga.booking.dto.BookingCreateDTO;
import com.techplant.yoga.booking.query.BookingQuery;
import com.techplant.yoga.booking.service.BookingService;
import com.techplant.yoga.booking.vo.BookingDetailVO;
import com.techplant.yoga.booking.vo.BookingListItemVO;
import com.techplant.yoga.common.response.PageResult;
import com.techplant.yoga.schedule.service.ScheduleBookingService;
import com.techplant.yoga.schedule.vo.ScheduleBookingSnapshot;

/** 预约业务实现：锁排班、校验重复、写预约、回写人数处于同一事务。 */
@Service
public class BookingServiceImpl implements BookingService
{
    private final BookingDao bookingDao;
    private final ScheduleBookingService scheduleBookingService;

    public BookingServiceImpl(BookingDao bookingDao, ScheduleBookingService scheduleBookingService)
    {
        this.bookingDao = bookingDao;
        this.scheduleBookingService = scheduleBookingService;
    }

    @Override
    public PageResult<BookingListItemVO> page(BookingQuery query)
    {
        IPage<BookingDO> page = bookingDao.selectPage(query);
        java.util.List<BookingListItemVO> list = new java.util.ArrayList<BookingListItemVO>();
        for (BookingDO booking : page.getRecords())
        {
            list.add(BookingConverter.toListItemVO(booking));
        }
        return PageResult.of(page.getTotal(), page.getCurrent(), page.getSize(), list);
    }

    @Override
    public BookingDetailVO getById(Long bookingId)
    {
        return requireDetail(bookingId);
    }

    @Override
    public BookingDetailVO getMyById(Long bookingId, Long userId)
    {
        BookingDO booking = require(bookingId);
        if (!userId.equals(booking.getUserId()))
        {
            throw new ServiceException("预约不存在", 404);
        }
        return BookingConverter.toDetailVO(booking);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BookingDetailVO create(Long userId, BookingCreateDTO dto)
    {
        if (userId == null)
        {
            throw new ServiceException("请先登录", 401);
        }
        ScheduleBookingSnapshot schedule = scheduleBookingService.lockForBooking(dto.getScheduleId());
        if (bookingDao.selectActiveByScheduleAndUser(dto.getScheduleId(), userId) != null)
        {
            throw new ServiceException("你已经预约过该排班", 409);
        }
        int current = schedule.getBookingCount() == null ? 0 : schedule.getBookingCount();
        int requested = dto.getBookingCount();
        if (current + requested > schedule.getCapacity())
        {
            throw new ServiceException("该排班预约人数已满", 409);
        }
        BookingDO booking = new BookingDO();
        booking.setScheduleId(schedule.getId());
        booking.setUserId(userId);
        booking.setStoreId(schedule.getStoreId());
        booking.setCourseId(schedule.getCourseId());
        booking.setBookingStatus(1);
        booking.setBookingCount(requested);
        booking.setDeleted(0);
        if (bookingDao.insert(booking) != 1 || booking.getId() == null)
        {
            throw new ServiceException("创建预约失败", 500);
        }
        scheduleBookingService.updateBookingCount(schedule.getId(), current + requested);
        return BookingConverter.toDetailVO(booking);
    }

    private BookingDetailVO requireDetail(Long bookingId)
    {
        return BookingConverter.toDetailVO(require(bookingId));
    }

    private BookingDO require(Long bookingId)
    {
        BookingDO booking = bookingDao.selectById(bookingId);
        if (booking == null)
        {
            throw new ServiceException("预约不存在或已删除", 404);
        }
        return booking;
    }
}
