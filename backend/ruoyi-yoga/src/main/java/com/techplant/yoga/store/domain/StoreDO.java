package com.techplant.yoga.store.domain;

import java.time.LocalDateTime;
import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * 门店数据对象，与 {@code t_store} 一一对应（门店管理详细设计 §1.2.1）。
 *
 * <p>仅在 mapper、dao 与 service 内部使用，接口层通过 VO 输出。</p>
 */
@TableName("t_store")
public class StoreDO
{
    /** 门店ID（雪花ID） */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    /** 门店名称 */
    private String name;

    /** 所在区域 */
    private String region;

    /** 省级行政区编码，用于地图定位 */
    private String provinceCode;

    /** 市级行政区编码，用于地图定位 */
    private String cityCode;

    /** 区/县级行政区编码，用于地图定位 */
    private String districtCode;

    /** 门店地址；用户端“地址”和“门店地址”共用此字段 */
    private String address;

    /** 门店电话 */
    private String phone;

    /** 经营类型：1直营连锁 2加盟 */
    private Integer businessType;

    /** 门店类型：1主力店 2精品店 */
    private Integer storeType;

    /** 营业时间 */
    private String businessHours;

    /** 门店状态：1启用 0停用 */
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
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getRegion() { return region; }
    public void setRegion(String region) { this.region = region; }
    public String getProvinceCode() { return provinceCode; }
    public void setProvinceCode(String provinceCode) { this.provinceCode = provinceCode; }
    public String getCityCode() { return cityCode; }
    public void setCityCode(String cityCode) { this.cityCode = cityCode; }
    public String getDistrictCode() { return districtCode; }
    public void setDistrictCode(String districtCode) { this.districtCode = districtCode; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public Integer getBusinessType() { return businessType; }
    public void setBusinessType(Integer businessType) { this.businessType = businessType; }
    public Integer getStoreType() { return storeType; }
    public void setStoreType(Integer storeType) { this.storeType = storeType; }
    public String getBusinessHours() { return businessHours; }
    public void setBusinessHours(String businessHours) { this.businessHours = businessHours; }
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
