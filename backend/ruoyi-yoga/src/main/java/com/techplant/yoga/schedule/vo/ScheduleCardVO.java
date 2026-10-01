package com.techplant.yoga.schedule.vo;

import java.time.LocalDate;
import java.time.LocalDateTime;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

/**
 * 用户端排课卡片（约课页，《用户端接口详细设计》§2.2 / §3.2）。
 *
 * <p><b>快照 vs 实时</b>：{@code courseType} / {@code courseTypeName} 取排课保存的**课种快照**；
 * {@code courseCoverUrl} / {@code difficulty}（以及详情页的 {@code courseIntro}）**实时取课程当前值**。</p>
 *
 * <p><b>用户端状态文案只有三种</b>：可约／已结束／已取消（{@code BR-排课-020}）；
 * 一期恒量：{@code bookedPersons = 0}、{@code remainingPlaces = maxPersons}。</p>
 */
@ApiModel("用户端排课卡片")
public class ScheduleCardVO
{
    @ApiModelProperty(value = "排课ID（雪花ID，字符串，进详情页用）", example = "1856739201475239001")
    private Long id;

    @ApiModelProperty(value = "课程名称（课程已删除时空字符串）", example = "哈他瑜伽")
    private String courseName;

    @ApiModelProperty(value = "课程封面URL（实时取课程，可空 → 空字符串）")
    private String courseCoverUrl;

    @ApiModelProperty(value = "课种（快照）：1团课 2精品课 3私教课 4特色课", example = "1")
    private Integer courseType;

    @ApiModelProperty(value = "课种名称（按快照码翻译）", example = "团课")
    private String courseTypeName;

    @ApiModelProperty(value = "课程难度 1~5（实时取课程）", example = "2")
    private Integer difficulty;

    @ApiModelProperty(value = "上课日期 yyyy-MM-dd", example = "2026-10-03")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate scheduleDate;

    @ApiModelProperty(value = "开始时间", example = "2026-10-03 19:00:00")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startTime;

    @ApiModelProperty(value = "结束时间", example = "2026-10-03 20:00:00")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endTime;

    @ApiModelProperty(value = "教练姓名（卡片只给姓名）", example = "王老师")
    private String coachName;

    @ApiModelProperty(value = "门店ID", example = "1856739201475235901")
    private Long storeId;

    @ApiModelProperty(value = "门店名称", example = "徐汇店")
    private String storeName;

    @ApiModelProperty(value = "教室名称（教室已删除时空字符串）", example = "瑜伽团课大教室")
    private String classroomName;

    @ApiModelProperty(value = "最大人数", example = "20")
    private Integer maxPersons;

    @ApiModelProperty(value = "已预约人数（一期恒 0）", example = "0")
    private Integer bookedPersons;

    @ApiModelProperty(value = "剩余名额（一期恒等于最大人数）", example = "20")
    private Integer remainingPlaces;

    @ApiModelProperty(value = "落库状态：2 已上架、3 已取消（待上架对用户端不可见）", example = "2")
    private Integer status;

    @ApiModelProperty(value = "用户端展示文案：可约／已结束／已取消", example = "可约")
    private String statusText;

    @ApiModelProperty(value = "是否可发起预约（status=2 且 end_time>当前时间）", example = "true")
    private Boolean bookable;

    public Long getId()
    {
        return id;
    }

    public void setId(Long id)
    {
        this.id = id;
    }

    public String getCourseName()
    {
        return courseName;
    }

    public void setCourseName(String courseName)
    {
        this.courseName = courseName;
    }

    public String getCourseCoverUrl()
    {
        return courseCoverUrl;
    }

    public void setCourseCoverUrl(String courseCoverUrl)
    {
        this.courseCoverUrl = courseCoverUrl;
    }

    public Integer getCourseType()
    {
        return courseType;
    }

    public void setCourseType(Integer courseType)
    {
        this.courseType = courseType;
    }

    public String getCourseTypeName()
    {
        return courseTypeName;
    }

    public void setCourseTypeName(String courseTypeName)
    {
        this.courseTypeName = courseTypeName;
    }

    public Integer getDifficulty()
    {
        return difficulty;
    }

    public void setDifficulty(Integer difficulty)
    {
        this.difficulty = difficulty;
    }

    public LocalDate getScheduleDate()
    {
        return scheduleDate;
    }

    public void setScheduleDate(LocalDate scheduleDate)
    {
        this.scheduleDate = scheduleDate;
    }

    public LocalDateTime getStartTime()
    {
        return startTime;
    }

    public void setStartTime(LocalDateTime startTime)
    {
        this.startTime = startTime;
    }

    public LocalDateTime getEndTime()
    {
        return endTime;
    }

    public void setEndTime(LocalDateTime endTime)
    {
        this.endTime = endTime;
    }

    public String getCoachName()
    {
        return coachName;
    }

    public void setCoachName(String coachName)
    {
        this.coachName = coachName;
    }

    public Long getStoreId()
    {
        return storeId;
    }

    public void setStoreId(Long storeId)
    {
        this.storeId = storeId;
    }

    public String getStoreName()
    {
        return storeName;
    }

    public void setStoreName(String storeName)
    {
        this.storeName = storeName;
    }

    public String getClassroomName()
    {
        return classroomName;
    }

    public void setClassroomName(String classroomName)
    {
        this.classroomName = classroomName;
    }

    public Integer getMaxPersons()
    {
        return maxPersons;
    }

    public void setMaxPersons(Integer maxPersons)
    {
        this.maxPersons = maxPersons;
    }

    public Integer getBookedPersons()
    {
        return bookedPersons;
    }

    public void setBookedPersons(Integer bookedPersons)
    {
        this.bookedPersons = bookedPersons;
    }

    public Integer getRemainingPlaces()
    {
        return remainingPlaces;
    }

    public void setRemainingPlaces(Integer remainingPlaces)
    {
        this.remainingPlaces = remainingPlaces;
    }

    public Integer getStatus()
    {
        return status;
    }

    public void setStatus(Integer status)
    {
        this.status = status;
    }

    public String getStatusText()
    {
        return statusText;
    }

    public void setStatusText(String statusText)
    {
        this.statusText = statusText;
    }

    public Boolean getBookable()
    {
        return bookable;
    }

    public void setBookable(Boolean bookable)
    {
        this.bookable = bookable;
    }
}
