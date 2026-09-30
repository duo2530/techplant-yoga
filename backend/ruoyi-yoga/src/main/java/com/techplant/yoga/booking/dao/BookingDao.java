package com.techplant.yoga.booking.dao;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.techplant.yoga.booking.domain.BookingDO;
import com.techplant.yoga.booking.query.BookingQuery;

/** 预约数据访问语义。 */
public interface BookingDao
{
    IPage<BookingDO> selectPage(BookingQuery query);
    BookingDO selectById(Long id);
    BookingDO selectActiveByScheduleAndUser(Long scheduleId, Long userId);
    int insert(BookingDO booking);
    long countUnfinishedByCourseId(Long courseId);
}
