package com.techplant.yoga.booking.dao;

import org.springframework.stereotype.Repository;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.techplant.yoga.booking.domain.BookingDO;
import com.techplant.yoga.booking.mapper.BookingMapper;
import com.techplant.yoga.booking.query.BookingQuery;

/** 预约数据访问实现。 */
@Repository
public class BookingDaoImpl implements BookingDao
{
    private final BookingMapper bookingMapper;
    public BookingDaoImpl(BookingMapper bookingMapper) { this.bookingMapper = bookingMapper; }

    @Override
    public IPage<BookingDO> selectPage(BookingQuery query)
    {
        LambdaQueryWrapper<BookingDO> wrapper = Wrappers.<BookingDO>lambdaQuery();
        wrapper.select(BookingDO::getId, BookingDO::getScheduleId, BookingDO::getUserId,
                BookingDO::getStoreId, BookingDO::getCourseId, BookingDO::getBookingStatus,
                BookingDO::getBookingCount, BookingDO::getCreateTime);
        wrapper.eq(query.getUserId() != null, BookingDO::getUserId, query.getUserId());
        wrapper.eq(query.getScheduleId() != null, BookingDO::getScheduleId, query.getScheduleId());
        wrapper.eq(query.getStoreId() != null, BookingDO::getStoreId, query.getStoreId());
        wrapper.eq(query.getCourseId() != null, BookingDO::getCourseId, query.getCourseId());
        wrapper.eq(query.getBookingStatus() != null, BookingDO::getBookingStatus, query.getBookingStatus());
        wrapper.orderByDesc(BookingDO::getCreateTime).orderByDesc(BookingDO::getId);
        return bookingMapper.selectPage(new Page<BookingDO>(query.pageNumOrDefault(), query.pageSizeOrDefault()), wrapper);
    }

    @Override
    public BookingDO selectById(Long id) { return bookingMapper.selectById(id); }

    @Override
    public BookingDO selectActiveByScheduleAndUser(Long scheduleId, Long userId)
    {
        return bookingMapper.selectOne(Wrappers.<BookingDO>lambdaQuery().eq(BookingDO::getScheduleId, scheduleId)
                .eq(BookingDO::getUserId, userId).eq(BookingDO::getBookingStatus, 1));
    }

    @Override
    public int insert(BookingDO booking) { return bookingMapper.insert(booking); }

    @Override
    public long countUnfinishedByCourseId(Long courseId) { return bookingMapper.countUnfinishedByCourseId(courseId); }
}
