package com.techplant.yoga.booking.vo;

import java.time.LocalDateTime;
import com.fasterxml.jackson.annotation.JsonFormat;

/** 预约列表项。 */
public class BookingListItemVO
{
    private Long id;
    private Long scheduleId;
    private Long userId;
    private Long storeId;
    private Long courseId;
    private Integer bookingStatus;
    private Integer bookingCount;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getScheduleId() { return scheduleId; }
    public void setScheduleId(Long scheduleId) { this.scheduleId = scheduleId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Long getStoreId() { return storeId; }
    public void setStoreId(Long storeId) { this.storeId = storeId; }
    public Long getCourseId() { return courseId; }
    public void setCourseId(Long courseId) { this.courseId = courseId; }
    public Integer getBookingStatus() { return bookingStatus; }
    public void setBookingStatus(Integer bookingStatus) { this.bookingStatus = bookingStatus; }
    public Integer getBookingCount() { return bookingCount; }
    public void setBookingCount(Integer bookingCount) { this.bookingCount = bookingCount; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
}
