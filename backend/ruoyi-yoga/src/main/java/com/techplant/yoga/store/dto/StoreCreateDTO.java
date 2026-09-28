package com.techplant.yoga.store.dto;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

/** 新增门店请求（门店管理详细设计 §2.2.3）。 */
@ApiModel("新增门店请求")
public class StoreCreateDTO
{
    @ApiModelProperty(value = "门店名称", required = true, example = "徐汇店")
    @NotBlank(message = "门店名称不能为空")
    @Size(max = 64, message = "门店名称长度不能超过 64")
    private String name;

    @ApiModelProperty(value = "所在区域", required = true, example = "上海市徐汇区")
    @NotBlank(message = "所在区域不能为空")
    @Size(max = 64, message = "所在区域长度不能超过 64")
    private String region;

    @ApiModelProperty(value = "省行政区 code", required = true, example = "310000")
    @NotBlank(message = "省行政区 code 不能为空")
    @Size(max = 16, message = "省行政区 code 长度不能超过 16")
    private String provinceCode;

    @ApiModelProperty(value = "市行政区 code", required = true, example = "310100")
    @NotBlank(message = "市行政区 code 不能为空")
    @Size(max = 16, message = "市行政区 code 长度不能超过 16")
    private String cityCode;

    @ApiModelProperty(value = "区/县行政区 code", required = true, example = "310104")
    @NotBlank(message = "区/县行政区 code 不能为空")
    @Size(max = 16, message = "区/县行政区 code 长度不能超过 16")
    private String districtCode;

    @ApiModelProperty(value = "门店地址；用户端展示为地址", required = true, example = "漕溪北路 88 号")
    @NotBlank(message = "门店地址不能为空")
    @Size(max = 255, message = "门店地址长度不能超过 255")
    private String address;

    @ApiModelProperty(value = "门店电话", required = true, example = "021-12345678")
    @NotBlank(message = "门店电话不能为空")
    @Size(max = 32, message = "门店电话长度不能超过 32")
    private String phone;

    @ApiModelProperty(value = "经营类型：1直营连锁 2加盟", required = true, example = "1")
    @NotNull(message = "经营类型不能为空")
    @Min(value = 1, message = "经营类型取值为 1 或 2")
    @Max(value = 2, message = "经营类型取值为 1 或 2")
    private Integer businessType;

    @ApiModelProperty(value = "门店类型：1主力店 2精品店", required = true, example = "1")
    @NotNull(message = "门店类型不能为空")
    @Min(value = 1, message = "门店类型取值为 1 或 2")
    @Max(value = 2, message = "门店类型取值为 1 或 2")
    private Integer storeType;

    @ApiModelProperty(value = "营业时间", required = true, example = "周一至周日 09:00-22:00")
    @NotBlank(message = "营业时间不能为空")
    @Size(max = 64, message = "营业时间长度不能超过 64")
    private String businessHours;

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
