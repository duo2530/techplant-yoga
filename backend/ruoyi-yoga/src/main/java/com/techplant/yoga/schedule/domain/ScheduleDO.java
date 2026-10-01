package com.techplant.yoga.schedule.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;
import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * 排课数据对象，对应 {@code t_schedule}（排课管理详细设计 §1.2.1）。
 *
 * <p><b>刻意不建的字段：</b>{@code deleted}／{@code is_hot}／{@code finished}／{@code capacity}／
 * {@code booking_count}。本表是<b>物理删除</b>（{@code BR-排课-014}），
 * 因此这里<b>绝对不要加 {@code @TableLogic}</b>；「已结束」由 {@code end_time} 推导，也不落库。</p>
 */
@TableName("t_schedule")
public class ScheduleDO
{
    /** 排课ID（雪花ID，应用侧生成） */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    /** 门店ID */
    private Long storeId;

    /** 课程ID */
    private Long courseId;

    /** 课种快照（1团课 2精品课 3私教课 4特色课，新增／更换课程时从课程复制） */
    private Integer courseType;

    /** 教练ID */
    private Long coachId;

    /** 教室ID（其 store_id 必须等于本行 store_id） */
    private Long classroomId;

    /** 上课日期（由 start_time 推导；日期筛选一律按本列） */
    private LocalDate scheduleDate;

    /** 开始时间（含日期） */
    private LocalDateTime startTime;

    /** 结束时间（须与开始时间同一自然日） */
    private LocalDateTime endTime;

    /** 最大人数（>0） */
    private Integer maxPersons;

    /** 最低开课人数（1<=min<=max，默认 2） */
    private Integer minPersons;

    /** 已预约人数（一期固定 0，只允许预约模块维护） */
    private Integer bookedPersons;

    /** 状态：1待上架 2已上架 3已取消（只允许状态流转接口维护） */
    private Integer status;

    /** 创建人（公共审计填充） */
    @TableField(fill = FieldFill.INSERT)
    private Long createBy;

    /** 创建时间（公共审计填充） */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 更新人（公共审计填充） */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long updateBy;

    /** 更新时间（公共审计填充） */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

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

    public Long getCourseId()
    {
        return courseId;
    }

    public void setCourseId(Long courseId)
    {
        this.courseId = courseId;
    }

    public Integer getCourseType()
    {
        return courseType;
    }

    public void setCourseType(Integer courseType)
    {
        this.courseType = courseType;
    }

    public Long getCoachId()
    {
        return coachId;
    }

    public void setCoachId(Long coachId)
    {
        this.coachId = coachId;
    }

    public Long getClassroomId()
    {
        return classroomId;
    }

    public void setClassroomId(Long classroomId)
    {
        this.classroomId = classroomId;
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

    public Long getCreateBy()
    {
        return createBy;
    }

    public void setCreateBy(Long createBy)
    {
        this.createBy = createBy;
    }

    public LocalDateTime getCreateTime()
    {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime)
    {
        this.createTime = createTime;
    }

    public Long getUpdateBy()
    {
        return updateBy;
    }

    public void setUpdateBy(Long updateBy)
    {
        this.updateBy = updateBy;
    }

    public LocalDateTime getUpdateTime()
    {
        return updateTime;
    }

    public void setUpdateTime(LocalDateTime updateTime)
    {
        this.updateTime = updateTime;
    }
}
