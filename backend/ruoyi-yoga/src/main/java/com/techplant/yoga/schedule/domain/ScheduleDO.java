package com.techplant.yoga.schedule.domain;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/** 排课数据对象，对应 {@code t_schedule}。 */
@TableName("t_schedule")
@Getter
@Setter
public class ScheduleDO {
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

    /** 已预约人数 */
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
}
