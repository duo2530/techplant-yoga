package com.techplant.yoga.schedule.vo;

import java.time.LocalDate;
import java.time.LocalDateTime;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

/** 排课为预约模块提供的读取快照，不作为 HTTP 接口返回对象。 */
@ApiModel("排课预约快照")
public class ScheduleBookingSnapshot
{
    @ApiModelProperty("排课ID")
    private Long id;

    @ApiModelProperty("门店ID")
    private Long storeId;

    @ApiModelProperty("课程ID")
    private Long courseId;

    @ApiModelProperty("课种快照：1团课 2精品课 3私教课 4特色课")
    private Integer courseType;

    @ApiModelProperty("教练ID")
    private Long coachId;

    @ApiModelProperty("教室ID")
    private Long classroomId;

    @ApiModelProperty("上课日期")
    private LocalDate scheduleDate;

    @ApiModelProperty("开始时间")
    private LocalDateTime startTime;

    @ApiModelProperty("结束时间")
    private LocalDateTime endTime;

    @ApiModelProperty("最大人数")
    private Integer maxPersons;

    @ApiModelProperty("最低开课人数")
    private Integer minPersons;

    @ApiModelProperty("已预约人数")
    private Integer bookedPersons;

    @ApiModelProperty("排课状态：1待上架 2已上架 3已取消")
    private Integer status;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getStoreId() { return storeId; }
    public void setStoreId(Long storeId) { this.storeId = storeId; }
    public Long getCourseId() { return courseId; }
    public void setCourseId(Long courseId) { this.courseId = courseId; }
    public Integer getCourseType() { return courseType; }
    public void setCourseType(Integer courseType) { this.courseType = courseType; }
    public Long getCoachId() { return coachId; }
    public void setCoachId(Long coachId) { this.coachId = coachId; }
    public Long getClassroomId() { return classroomId; }
    public void setClassroomId(Long classroomId) { this.classroomId = classroomId; }
    public LocalDate getScheduleDate() { return scheduleDate; }
    public void setScheduleDate(LocalDate scheduleDate) { this.scheduleDate = scheduleDate; }
    public LocalDateTime getStartTime() { return startTime; }
    public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }
    public LocalDateTime getEndTime() { return endTime; }
    public void setEndTime(LocalDateTime endTime) { this.endTime = endTime; }
    public Integer getMaxPersons() { return maxPersons; }
    public void setMaxPersons(Integer maxPersons) { this.maxPersons = maxPersons; }
    public Integer getMinPersons() { return minPersons; }
    public void setMinPersons(Integer minPersons) { this.minPersons = minPersons; }
    public Integer getBookedPersons() { return bookedPersons; }
    public void setBookedPersons(Integer bookedPersons) { this.bookedPersons = bookedPersons; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
}
