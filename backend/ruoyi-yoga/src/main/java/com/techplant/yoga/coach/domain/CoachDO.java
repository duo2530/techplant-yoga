package com.techplant.yoga.coach.domain;

import java.time.LocalDateTime;
import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * 教练数据对象，与 {@code t_coach} 一一对应（教练管理详细设计 §1.2.1）。
 *
 * <p><b>四点约定：</b></p>
 * <ul>
 *   <li>主键：雪花ID，由 MyBatis-Plus 的 {@link IdType#ASSIGN_ID} 在应用侧生成，插入时不赋值（§1.1.1）；</li>
 *   <li><b>物理删除</b>：本表<b>没有</b> {@code deleted} 字段，<b>不加</b> {@code @TableLogic}
 *       （详细设计 §1.1.1、详细设计总览 §2）；</li>
 *   <li><b>没有状态字段</b>：教练不设启用／停用；</li>
 *   <li>审计字段：由 {@code AuditMetaObjectHandler} 自动填充（{@code backend/AGENTS.md} §8.4）。</li>
 * </ul>
 *
 * <p>{@code gallery} 在 DO 里以 <b>JSON 字符串</b> 承载（列类型是 MySQL {@code json}），
 * JSON ↔ {@code List<String>} 的转换<b>只在 service 层发生</b>（详细设计 §3.4 第 2 条）。</p>
 */
@TableName("t_coach")
public class CoachDO
{
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

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getAvatarUrl() { return avatarUrl; }
    public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }
    public String getIntro() { return intro; }
    public void setIntro(String intro) { this.intro = intro; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getGallery() { return gallery; }
    public void setGallery(String gallery) { this.gallery = gallery; }
    public Long getCreateBy() { return createBy; }
    public void setCreateBy(Long createBy) { this.createBy = createBy; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
    public Long getUpdateBy() { return updateBy; }
    public void setUpdateBy(Long updateBy) { this.updateBy = updateBy; }
    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }
}
