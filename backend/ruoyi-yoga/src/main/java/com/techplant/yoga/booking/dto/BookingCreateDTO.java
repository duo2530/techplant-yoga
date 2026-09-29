package com.techplant.yoga.booking.dto;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import io.swagger.annotations.ApiModel;

/** 用户预约请求。当前 MVP 一次预约一个名额。 */
@ApiModel("创建预约请求")
public class BookingCreateDTO
{
    @NotNull(message = "排班不能为空")
    @Min(value = 1, message = "排班ID必须大于 0")
    private Long scheduleId;
    @NotNull(message = "预约人数不能为空")
    @Min(value = 1, message = "预约人数必须大于 0")
    @Max(value = 1, message = "当前每个用户每个排班只能预约 1 人")
    private Integer bookingCount;
    public Long getScheduleId() { return scheduleId; }
    public void setScheduleId(Long scheduleId) { this.scheduleId = scheduleId; }
    public Integer getBookingCount() { return bookingCount; }
    public void setBookingCount(Integer bookingCount) { this.bookingCount = bookingCount; }
}
