package com.techplant.yoga.course.domain;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/** 课程数据对象，与 {@code t_course} 一一对应。 */
@TableName("t_course")
@Getter
@Setter
public class CourseDO {
    /** 课程ID */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    /** 课程名称（全平台唯一） */
    private String name;

    /** 课种：1团课 2精品课 3私教课 4特色课（列名 course_type） */
    private Integer courseType;

    /** 课程封面URL（单图，可空） */
    private String coverUrl;

    /** 课程介绍（纯文本，可空） */
    private String intro;

    /** 课程难度：1~5 星 */
    private Integer difficulty;

    /** 创建人ID */
    @TableField(fill = FieldFill.INSERT)
    private Long createBy;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 更新人ID */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long updateBy;

    /** 更新时间 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
