package com.techplant.yoga.course.domain;

import java.time.LocalDateTime;
import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * 课程数据对象，与 {@code t_course} 一一对应（详细设计 §1.2.1）。
 *
 * <p>只在 mapper 与 service 之间传递，不出现在接口契约里（§2.1.6）。</p>
 *
 * <p><b>三点约定：</b></p>
 * <ul>
 *   <li>主键：雪花ID，由 MyBatis-Plus 的 {@link IdType#ASSIGN_ID} 在应用侧生成，插入时不赋值（§1.4）；</li>
 *   <li>逻辑删除：{@link TableLogic} 标注 {@code deleted}，框架自动给查询追加 {@code deleted = 0}；</li>
 *   <li>审计字段：由 {@code MetaObjectHandler} 自动填充（§3.1.4）。</li>
 * </ul>
 */
@TableName("t_course")
public class CourseDO
{
    /** 课程ID */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    /** 课程名称 */
    private String name;

    /** 课程类型：1团课 2精品课 3私教课 4特色课 */
    private Integer type;

    /** 课程难度：1~5 星 */
    private Integer difficulty;

    /** 课程封面图URL */
    private String coverUrl;

    /** 课程介绍 */
    private String intro;

    /** 单节时长（分钟） */
    private Integer durationMin;

    /** 展示排序，值越小越靠前 */
    private Integer sortNo;

    /** 课程状态：1启用 0停用 */
    private Integer status;

    /** 逻辑删除：0正常 1已删除 */
    @TableLogic
    private Integer deleted;

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

    public Integer getType()
    {
        return type;
    }

    public void setType(Integer type)
    {
        this.type = type;
    }

    public Integer getDifficulty()
    {
        return difficulty;
    }

    public void setDifficulty(Integer difficulty)
    {
        this.difficulty = difficulty;
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

    public Integer getDurationMin()
    {
        return durationMin;
    }

    public void setDurationMin(Integer durationMin)
    {
        this.durationMin = durationMin;
    }

    public Integer getSortNo()
    {
        return sortNo;
    }

    public void setSortNo(Integer sortNo)
    {
        this.sortNo = sortNo;
    }

    public Integer getStatus()
    {
        return status;
    }

    public void setStatus(Integer status)
    {
        this.status = status;
    }

    public Integer getDeleted()
    {
        return deleted;
    }

    public void setDeleted(Integer deleted)
    {
        this.deleted = deleted;
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
