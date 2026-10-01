package com.techplant.yoga.course.domain;

import java.time.LocalDateTime;
import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * 课程数据对象，与 {@code t_course} 一一对应（课程管理详细设计 §1.2.1）。
 *
 * <p>只在 mapper 与 service 之间传递，不出现在接口契约里（§3.2）。</p>
 *
 * <p><b>三点约定：</b></p>
 * <ul>
 *   <li>主键：雪花ID，由 MyBatis-Plus 的 {@link IdType#ASSIGN_ID} 在应用侧生成，插入时不赋值；</li>
 *   <li><b>物理删除</b>：本表没有 {@code deleted} 字段，<b>不加</b> {@code @TableLogic}
 *       （详细设计总览 §2、BR-课程-008）；</li>
 *   <li>审计字段：由 {@code AuditMetaObjectHandler} 自动填充（§1.1.1）。</li>
 * </ul>
 *
 * <p><b>本表刻意不建的字段</b>（§1.2.1）：{@code store_id}（课程是平台级，不归属门店）、
 * {@code status}（课程无状态，下架靠删除）、{@code sort_no}（一期不设展示排序）、{@code deleted}。</p>
 */
@TableName("t_course")
public class CourseDO
{
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

    public Long getId()
    {
        return id;
    }

    public void setId(Long id)
    {
        this.id = id;
    }

    public String getName()
    {
        return name;
    }

    public void setName(String name)
    {
        this.name = name;
    }

    public Integer getCourseType()
    {
        return courseType;
    }

    public void setCourseType(Integer courseType)
    {
        this.courseType = courseType;
    }

    public String getCoverUrl()
    {
        return coverUrl;
    }

    public void setCoverUrl(String coverUrl)
    {
        this.coverUrl = coverUrl;
    }

    public String getIntro()
    {
        return intro;
    }

    public void setIntro(String intro)
    {
        this.intro = intro;
    }

    public Integer getDifficulty()
    {
        return difficulty;
    }

    public void setDifficulty(Integer difficulty)
    {
        this.difficulty = difficulty;
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
