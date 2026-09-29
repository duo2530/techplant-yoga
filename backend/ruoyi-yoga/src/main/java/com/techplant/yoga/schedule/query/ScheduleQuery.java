package com.techplant.yoga.schedule.query;

import java.time.LocalDateTime;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import org.springframework.format.annotation.DateTimeFormat;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

/** 排班分页查询条件。 */
@ApiModel("排班查询条件")
public class ScheduleQuery
{
    @ApiModelProperty("门店ID")
    @Min(value = 1, message = "门店ID必须大于 0")
    private Long storeId;
    @ApiModelProperty("课程ID")
    @Min(value = 1, message = "课程ID必须大于 0")
    private Long courseId;
    @ApiModelProperty("排班状态：1可预约 2已取消 3已结束")
    @Min(value = 1, message = "排班状态取值为 1~3")
    @Max(value = 3, message = "排班状态取值为 1~3")
    private Integer status;
    @ApiModelProperty("开始时间起点")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startTimeFrom;
    @ApiModelProperty("开始时间终点")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startTimeTo;
    @Min(value = 1, message = "页码不能小于 1")
    private Integer pageNum;
    @Min(value = 1, message = "每页条数不能小于 1")
    @Max(value = 100, message = "每页条数不能超过 100")
    private Integer pageSize;

    public int pageNumOrDefault() { return pageNum == null ? 1 : pageNum; }
    public int pageSizeOrDefault() { return pageSize == null ? 10 : pageSize; }
    public Long getStoreId() { return storeId; }
    public void setStoreId(Long storeId) { this.storeId = storeId; }
    public Long getCourseId() { return courseId; }
    public void setCourseId(Long courseId) { this.courseId = courseId; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public LocalDateTime getStartTimeFrom() { return startTimeFrom; }
    public void setStartTimeFrom(LocalDateTime startTimeFrom) { this.startTimeFrom = startTimeFrom; }
    public LocalDateTime getStartTimeTo() { return startTimeTo; }
    public void setStartTimeTo(LocalDateTime startTimeTo) { this.startTimeTo = startTimeTo; }
    public Integer getPageNum() { return pageNum; }
    public void setPageNum(Integer pageNum) { this.pageNum = pageNum; }
    public Integer getPageSize() { return pageSize; }
    public void setPageSize(Integer pageSize) { this.pageSize = pageSize; }
}
