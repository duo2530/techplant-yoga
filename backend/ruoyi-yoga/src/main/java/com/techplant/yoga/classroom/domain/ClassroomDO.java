package com.techplant.yoga.classroom.domain;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/** 教室数据对象，与 {@code t_classroom} 一一对应。 */
@TableName("t_classroom")
@Getter
@Setter
public class ClassroomDO {
    /** 教室ID */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    /** 所属门店ID（只允许在新增时指定，修改不可换门店） */
    private Long storeId;

    /** 教室名称（同一门店内唯一） */
    private String name;

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
