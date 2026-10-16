package com.techplant.yoga.coach.domain;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/** 教练数据对象，与 {@code t_coach} 一一对应。 */
@TableName("t_coach")
@Getter
@Setter
public class CoachDO {
    /** 教练ID（雪花ID） */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    /** 姓名（允许重名） */
    private String name;

    /** 头像URL */
    private String avatarUrl;

    /** 简介 */
    private String intro;

    /** 联系电话（管理端专用） */
    private String phone;

    /** 相册：JSON 数组字符串，最多 5 个图片 URL；空相册为 {@code null} */
    private String gallery;

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
