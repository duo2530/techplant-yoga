package com.techplant.yoga.booking.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.techplant.yoga.booking.domain.BookingDO;

/** 预约 Mapper。 */
public interface BookingMapper extends BaseMapper<BookingDO>
{
    long countUnfinishedByCourseId(Long courseId);
}
