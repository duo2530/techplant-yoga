package com.techplant.yoga.booking.query;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

/** 预约分页查询条件。 */
@ApiModel("预约查询条件")
public class BookingQuery
{
    @ApiModelProperty("用户ID，仅管理端使用")
    @Min(value = 1, message = "用户ID必须大于 0")
    private Long userId;
    @ApiModelProperty("排班ID")
    @Min(value = 1, message = "排班ID必须大于 0")
    private Long scheduleId;
    @ApiModelProperty("门店ID")
    @Min(value = 1, message = "门店ID必须大于 0")
    private Long storeId;
    @ApiModelProperty("课程ID")
    @Min(value = 1, message = "课程ID必须大于 0")
    private Long courseId;
    @ApiModelProperty("预约状态：1已预约 2已取消 3已签到")
    @Min(value = 1, message = "预约状态取值为 1~3")
    @Max(value = 3, message = "预约状态取值为 1~3")
    private Integer bookingStatus;
    @Min(value = 1, message = "页码不能小于 1")
    private Integer pageNum;
    @Min(value = 1, message = "每页条数不能小于 1")
    @Max(value = 100, message = "每页条数不能超过 100")
    private Integer pageSize;
    public int pageNumOrDefault() { return pageNum == null ? 1 : pageNum; }
    public int pageSizeOrDefault() { return pageSize == null ? 10 : pageSize; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Long getScheduleId() { return scheduleId; }
    public void setScheduleId(Long scheduleId) { this.scheduleId = scheduleId; }
    public Long getStoreId() { return storeId; }
    public void setStoreId(Long storeId) { this.storeId = storeId; }
    public Long getCourseId() { return courseId; }
    public void setCourseId(Long courseId) { this.courseId = courseId; }
    public Integer getBookingStatus() { return bookingStatus; }
    public void setBookingStatus(Integer bookingStatus) { this.bookingStatus = bookingStatus; }
    public Integer getPageNum() { return pageNum; }
    public void setPageNum(Integer pageNum) { this.pageNum = pageNum; }
    public Integer getPageSize() { return pageSize; }
    public void setPageSize(Integer pageSize) { this.pageSize = pageSize; }
}
