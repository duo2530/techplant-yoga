package com.techplant.yoga.store.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

/** 游客可见的门店列表项，只返回启用门店的展示信息。 */
@ApiModel("游客门店列表项")
public class StorePublicItemVO
{
    @ApiModelProperty(value = "门店ID（雪花ID，字符串）", example = "1856739201475235901")
    private Long id;
    @ApiModelProperty(value = "门店名称", example = "徐汇店")
    private String name;
    @ApiModelProperty(value = "所在区域", example = "上海市徐汇区")
    private String region;
    @ApiModelProperty(value = "省行政区 code", example = "310000")
    private String provinceCode;
    @ApiModelProperty(value = "市行政区 code", example = "310100")
    private String cityCode;
    @ApiModelProperty(value = "区/县行政区 code", example = "310104")
    private String districtCode;
    @ApiModelProperty(value = "门店地址", example = "漕溪北路 88 号")
    private String address;
    @ApiModelProperty(value = "门店电话", example = "021-12345678")
    private String phone;
    @ApiModelProperty(value = "经营类型：1直营连锁 2加盟", example = "1")
    private Integer businessType;
    @ApiModelProperty(value = "门店类型：1主力店 2精品店", example = "1")
    private Integer storeType;
    @ApiModelProperty(value = "营业时间", example = "周一至周日 09:00-22:00")
    private String businessHours;

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
}
