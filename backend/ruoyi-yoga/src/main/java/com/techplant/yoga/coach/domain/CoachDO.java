package com.techplant.yoga.coach.domain;

import java.time.LocalDateTime;
import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * 教练数据对象，与 {@code t_coach} 一一对应（详细设计 §1.2.1）。
 */
@TableName("t_coach")
public class CoachDO
{
    /** 教练ID（雪花ID） */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    /** 教练简介 */
    private String intro;

    /** 教练名称 */
    private String name;

    /** 教练头衔 */
    private String title;

    /** 教练头像URL */
    private String avatarUrl;

    /** 教练相册JSON字符串 */
    @TableField("album_urls")
    private String albumUrlsJson;

    /** 教练状态：1启用 0停用 */
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

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getIntro() { return intro; }
    public void setIntro(String intro) { this.intro = intro; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getAvatarUrl() { return avatarUrl; }
    public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }
    public String getAlbumUrlsJson() { return albumUrlsJson; }
    public void setAlbumUrlsJson(String albumUrlsJson) { this.albumUrlsJson = albumUrlsJson; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public Integer getDeleted() { return deleted; }
    public void setDeleted(Integer deleted) { this.deleted = deleted; }
    public Long getCreateBy() { return createBy; }
    public void setCreateBy(Long createBy) { this.createBy = createBy; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
    public Long getUpdateBy() { return updateBy; }
    public void setUpdateBy(Long updateBy) { this.updateBy = updateBy; }
    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }
}
