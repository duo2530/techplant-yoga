package com.techplant.yoga.store.domain;

import java.time.LocalDateTime;
import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * 门店数据对象，与 {@code t_store} 一一对应（门店管理详细设计 §1.2.1）。
 *
 * <p><b>本表没有状态字段、没有 {@code deleted} 字段</b>：门店一期不设状态（BR-门店-008），
 * 删除一律<b>物理删除</b>（BR-门店-009、详细设计总览 §5 决策 1），因此这里
 * <b>刻意不标 {@code @TableLogic}</b>。</p>
 *
 * <p>仅在 mapper、dao 与 service 内部使用，接口层通过 VO 输出（§3.4 第 5 条）。</p>
 */
@TableName("t_store")
public class StoreDO
{
    /** 门店ID（雪花ID，应用侧生成） */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    /** 门店名称（全平台唯一，uk_store_name） */
    private String name;

    /** 门店类型：1主力店 2精品店 */
    private Integer storeType;

    /** 所在区域：上海市市辖区行政区划代码（字典 store_region） */
    private String regionCode;

    /** 联系电话（文本，允许座机／手机号／分机） */
    private String phone;

    /** 营业时间（一段展示文本，不拆分时段） */
    private String businessHours;

    /** 地址 */
    private String address;

    /** 门店图片 URL（单图，可空） */
    private String imageUrl;

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
    public Integer getStoreType() { return storeType; }
    public void setStoreType(Integer storeType) { this.storeType = storeType; }
    public String getRegionCode() { return regionCode; }
    public void setRegionCode(String regionCode) { this.regionCode = regionCode; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getBusinessHours() { return businessHours; }
    public void setBusinessHours(String businessHours) { this.businessHours = businessHours; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    public Long getCreateBy() { return createBy; }
    public void setCreateBy(Long createBy) { this.createBy = createBy; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
    public Long getUpdateBy() { return updateBy; }
    public void setUpdateBy(Long updateBy) { this.updateBy = updateBy; }
    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }
}
