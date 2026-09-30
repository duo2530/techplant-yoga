package com.techplant.yoga.booking.convert;

import com.techplant.yoga.booking.domain.BookingDO;
import com.techplant.yoga.booking.vo.BookingDetailVO;
import com.techplant.yoga.booking.vo.BookingListItemVO;

/** 预约对象转换。 */
public final class BookingConverter
{
    private BookingConverter() { }
    public static BookingListItemVO toListItemVO(BookingDO source)
    {
        BookingListItemVO vo = new BookingListItemVO();
        vo.setId(source.getId());
        vo.setScheduleId(source.getScheduleId());
        vo.setUserId(source.getUserId());
        vo.setStoreId(source.getStoreId());
        vo.setCourseId(source.getCourseId());
        vo.setBookingStatus(source.getBookingStatus());
        vo.setBookingCount(source.getBookingCount());
        vo.setCreateTime(source.getCreateTime());
        return vo;
    }
    public static BookingDetailVO toDetailVO(BookingDO source)
    {
        BookingDetailVO vo = new BookingDetailVO();
        BookingListItemVO item = toListItemVO(source);
        vo.setId(item.getId());
        vo.setScheduleId(item.getScheduleId());
        vo.setUserId(item.getUserId());
        vo.setStoreId(item.getStoreId());
        vo.setCourseId(item.getCourseId());
        vo.setBookingStatus(item.getBookingStatus());
        vo.setBookingCount(item.getBookingCount());
        vo.setCreateTime(item.getCreateTime());
        return vo;
    }
}
