package com.techplant.yoga.reservation.vo;

import java.time.LocalDateTime;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

/** 我的预约列表项。 */
@ApiModel("我的预约列表项")
public class ReservationPublicItemVO
{
    @ApiModelProperty(value = "预约ID（雪花ID，字符串）", example = "1856739201475240001")
    private Long id;

    @ApiModelProperty(value = "排课ID（雪花ID，字符串）", example = "1856739201475239001")
    private Long scheduleId;

    @ApiModelProperty(value = "关联排课的课程ID", example = "1856739201475238001")
    private Long courseId;

    @ApiModelProperty(value = "当前课程名称")
    private String courseName;

    @ApiModelProperty(value = "当前课程封面URL")
    private String courseCoverUrl;

    @ApiModelProperty(value = "排课课种：1团课 2精品课 3私教课 4特色课", example = "1")
    private Integer courseType;

    @ApiModelProperty(value = "课种名称", example = "团课")
    private String courseTypeName;

    @ApiModelProperty(value = "当前教练姓名")
    private String coachName;

    @ApiModelProperty(value = "当前门店地址")
    private String storeAddress;

    @ApiModelProperty(value = "上课日期，由预约开始时间生成", example = "2026-10-15")
    private String scheduleDate;

    @ApiModelProperty(value = "上课开始时间快照")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startTime;

    @ApiModelProperty(value = "上课结束时间快照")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endTime;

    @ApiModelProperty(value = "预约状态：1已预约 2已取消 3已签到", example = "1")
    private Integer status;

    @ApiModelProperty(value = "预约状态文案", example = "已预约")
    private String statusText;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getScheduleId() { return scheduleId; }
    public void setScheduleId(Long scheduleId) { this.scheduleId = scheduleId; }
    public Long getCourseId() { return courseId; }
    public void setCourseId(Long courseId) { this.courseId = courseId; }
    public String getCourseName() { return courseName; }
    public void setCourseName(String courseName) { this.courseName = courseName; }
    public String getCourseCoverUrl() { return courseCoverUrl; }
    public void setCourseCoverUrl(String courseCoverUrl) { this.courseCoverUrl = courseCoverUrl; }
    public Integer getCourseType() { return courseType; }
    public void setCourseType(Integer courseType) { this.courseType = courseType; }
    public String getCourseTypeName() { return courseTypeName; }
    public void setCourseTypeName(String courseTypeName) { this.courseTypeName = courseTypeName; }
    public String getCoachName() { return coachName; }
    public void setCoachName(String coachName) { this.coachName = coachName; }
    public String getStoreAddress() { return storeAddress; }
    public void setStoreAddress(String storeAddress) { this.storeAddress = storeAddress; }
    public String getScheduleDate() { return scheduleDate; }
    public void setScheduleDate(String scheduleDate) { this.scheduleDate = scheduleDate; }
    public LocalDateTime getStartTime() { return startTime; }
    public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }
    public LocalDateTime getEndTime() { return endTime; }
    public void setEndTime(LocalDateTime endTime) { this.endTime = endTime; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public String getStatusText() { return statusText; }
    public void setStatusText(String statusText) { this.statusText = statusText; }
}
