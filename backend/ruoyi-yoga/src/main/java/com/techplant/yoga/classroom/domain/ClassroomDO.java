package com.techplant.yoga.classroom.domain;

import java.time.LocalDateTime;
import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * 教室数据对象，与 {@code t_classroom} 一一对应（教室管理详细设计 §1.2.1）。
 *
 * <p><b>字段只有</b> {@code id / store_id / name} ＋ 审计四列：没有状态、没有容量／设备／图片／楼层。
 * 同一门店内名称唯一（{@code uk_classroom_store_name}）。</p>
 *
 * <p><b>物理删除：</b>本表没有 {@code deleted} 字段，<b>不加</b> {@code @TableLogic}
 * （详细设计总览 §2、BR-教室-006）。</p>
 */
@TableName("t_classroom")
public class ClassroomDO
{
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

    public String getName()
    {
        return name;
    }

    public void setName(String name)
    {
        this.name = name;
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
