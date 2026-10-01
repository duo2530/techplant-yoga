package com.techplant.yoga.store.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

/**
 * 用户端门店列表项（用户端接口详细设计 §2.1、§3.1）。
 *
 * <p>只给展示字段：<b>不返回审计字段、不返回任何状态字段</b>（门店本来就没有状态）。
 * {@code storeType} + {@code storeTypeName}、{@code regionCode} + {@code regionName} 成对返回；
 * {@code imageUrl} 可空时给<b>空字符串</b>（§3.1，不返回 null）。</p>
 */
@ApiModel("用户端门店列表项")
public class StorePublicItemVO
{
    @ApiModelProperty(value = "门店ID（雪花ID，字符串）", example = "1856739201475235901")
    private Long id;

    @ApiModelProperty(value = "门店名称", example = "徐汇店")
    private String name;

    @ApiModelProperty(value = "门店类型：1主力店 2精品店", example = "1")
    private Integer storeType;

    @ApiModelProperty(value = "门店类型名称", example = "主力店")
    private String storeTypeName;

    @ApiModelProperty(value = "所在区域 code（6 位行政区划代码）", example = "310104")
    private String regionCode;

    @ApiModelProperty(value = "所在区域名称（后端按字典翻译）", example = "徐汇区")
    private String regionName;

    @ApiModelProperty(value = "联系电话", example = "021-12345678")
    private String phone;

    @ApiModelProperty(value = "营业时间（一段展示文本）", example = "周一至周日 09:00-22:00")
    private String businessHours;

    @ApiModelProperty(value = "地址", example = "漕溪北路 88 号")
    private String address;

    @ApiModelProperty(value = "门店图片 URL（单图，可空 → 空字符串）", example = "https://cdn.example.com/store/1.jpg")
    private String imageUrl;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Integer getStoreType() { return storeType; }
    public void setStoreType(Integer storeType) { this.storeType = storeType; }
    public String getStoreTypeName() { return storeTypeName; }
    public void setStoreTypeName(String storeTypeName) { this.storeTypeName = storeTypeName; }
    public String getRegionCode() { return regionCode; }
    public void setRegionCode(String regionCode) { this.regionCode = regionCode; }
    public String getRegionName() { return regionName; }
    public void setRegionName(String regionName) { this.regionName = regionName; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getBusinessHours() { return businessHours; }
    public void setBusinessHours(String businessHours) { this.businessHours = businessHours; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
}
