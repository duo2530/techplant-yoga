package com.techplant.yoga.schedule.vo;

import java.time.LocalDate;
import java.time.LocalDateTime;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

/**
 * 管理端排课列表项（排课管理详细设计 §2.3.1）。
 *
 * <p>枚举**成对返回**：{@code courseType} ＋ {@code courseTypeName}（课种名称按快照码翻译，
 * 前端不许硬编码 {@code 1 = 团课}）、{@code status} ＋ {@code statusText}。</p>
 *
 * <p>{@code statusText}（待上架／已上架／已取消／已结束）与 {@code finished} 由 <b>service 层统一判定一次</b>
 * 后写入；controller 不判断、前端不推导（§3.4 第 2 条）。</p>
 *
 * <p>门店／课程／教练／教室的名称由 service 批量补齐；对象已被物理删除时返回**空字符串**（不是 null）。</p>
 */
@ApiModel("管理端排课列表项")
public class ScheduleListItemVO
{
    @ApiModelProperty(value = "排课ID（雪花ID，字符串）", example = "1856739201475239001")
    private Long id;

    @ApiModelProperty(value = "门店ID", example = "1856739201475235901")
    private Long storeId;

    @ApiModelProperty(value = "门店名称（对象已删除时为空字符串）", example = "徐汇店")
    private String storeName;

    @ApiModelProperty(value = "课程ID", example = "1856739201475235801")
    private Long courseId;

    @ApiModelProperty(value = "课程名称（对象已删除时为空字符串）", example = "哈他瑜伽")
    private String courseName;

    @ApiModelProperty(value = "课种快照：1团课 2精品课 3私教课 4特色课", example = "1")
    private Integer courseType;

    @ApiModelProperty(value = "课种名称（按快照码翻译）", example = "团课")
    private String courseTypeName;

    @ApiModelProperty(value = "教练ID", example = "1856739201475237001")
    private Long coachId;

    @ApiModelProperty(value = "教练姓名（对象已删除时为空字符串）", example = "王老师")
    private String coachName;

    @ApiModelProperty(value = "教室ID", example = "1856739201475236001")
    private Long classroomId;

    @ApiModelProperty(value = "教室名称（对象已删除时为空字符串）", example = "瑜伽团课大教室")
    private String classroomName;

    @ApiModelProperty(value = "上课日期（由开始时间推导，筛选按本列）", example = "2026-10-03")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate scheduleDate;

    @ApiModelProperty(value = "开始时间", example = "2026-10-03 19:00:00")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startTime;

    @ApiModelProperty(value = "结束时间", example = "2026-10-03 20:00:00")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endTime;

    @ApiModelProperty(value = "最大人数", example = "20")
    private Integer maxPersons;

    @ApiModelProperty(value = "最低开课人数", example = "2")
    private Integer minPersons;

    @ApiModelProperty(value = "已预约人数（一期恒 0）", example = "0")
    private Integer bookedPersons;

    @ApiModelProperty(value = "落库状态：1待上架 2已上架 3已取消", example = "2")
    private Integer status;

    @ApiModelProperty(value = "展示文案：待上架／已上架／已取消／已结束", example = "已上架")
    private String statusText;

    @ApiModelProperty(value = "是否已结束（status=2 且 end_time<=当前时间）", example = "false")
    private Boolean finished;

    public Long getId()
    {
        return id;
    }

    public void setId(Long id)
    {
        this.id = id;
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

    public Long getCourseId()
    {
        return courseId;
    }

    public void setCourseId(Long courseId)
    {
        this.courseId = courseId;
    }

    public String getCourseName()
    {
        return courseName;
    }

    public void setCourseName(String courseName)
    {
        this.courseName = courseName;
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

    public Long getCoachId()
    {
        return coachId;
    }

    public void setCoachId(Long coachId)
    {
        this.coachId = coachId;
    }

    public String getCoachName()
    {
        return coachName;
    }

    public void setCoachName(String coachName)
    {
        this.coachName = coachName;
    }

    public Long getClassroomId()
    {
        return classroomId;
    }

    public void setClassroomId(Long classroomId)
    {
        this.classroomId = classroomId;
    }

    public String getClassroomName()
    {
        return classroomName;
    }

    public void setClassroomName(String classroomName)
    {
        this.classroomName = classroomName;
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

    public Integer getMaxPersons()
    {
        return maxPersons;
    }

    public void setMaxPersons(Integer maxPersons)
    {
        this.maxPersons = maxPersons;
    }

    public Integer getMinPersons()
    {
        return minPersons;
    }

    public void setMinPersons(Integer minPersons)
    {
        this.minPersons = minPersons;
    }

    public Integer getBookedPersons()
    {
        return bookedPersons;
    }

    public void setBookedPersons(Integer bookedPersons)
    {
        this.bookedPersons = bookedPersons;
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

    public Boolean getFinished()
    {
        return finished;
    }

    public void setFinished(Boolean finished)
    {
        this.finished = finished;
    }
}
