package com.techplant.yoga.store.dto;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

/**
 * 修改门店请求（门店管理详细设计 §2.2.4、§2.3.2）。
 *
 * <p>与新增请求字段完全一致，按<b>全量编辑</b>处理；一期门店没有状态字段，
 * 因此本 DTO 不含 {@code status}，也没有独立的状态接口。</p>
 */
@ApiModel("修改门店请求")
public class StoreUpdateDTO
{
    @ApiModelProperty(value = "门店名称（全平台唯一）", required = true, example = "徐汇店")
    @NotBlank(message = "门店名称不能为空")
    @Size(max = 64, message = "门店名称长度不能超过 64")
    private String name;

    @ApiModelProperty(value = "门店类型：1主力店 2精品店", required = true, example = "1")
    @NotNull(message = "门店类型不能为空")
    @Min(value = 1, message = "门店类型取值为 1 或 2")
    @Max(value = 2, message = "门店类型取值为 1 或 2")
    private Integer storeType;

    @ApiModelProperty(value = "所在区域：行政区划 code（字典 store_region）", required = true, example = "310104")
    @NotBlank(message = "所在区域不能为空")
    @Size(max = 6, message = "所在区域 code 长度不能超过 6")
    private String regionCode;

    @ApiModelProperty(value = "联系电话", required = true, example = "021-12345678")
    @NotBlank(message = "联系电话不能为空")
    @Size(max = 32, message = "联系电话长度不能超过 32")
    private String phone;

    @ApiModelProperty(value = "营业时间（一段展示文本）", required = true, example = "周一至周日 09:00-22:00")
    @NotBlank(message = "营业时间不能为空")
    @Size(max = 64, message = "营业时间长度不能超过 64")
    private String businessHours;

    @ApiModelProperty(value = "地址", required = true, example = "漕溪北路 88 号")
    @NotBlank(message = "地址不能为空")
    @Size(max = 255, message = "地址长度不能超过 255")
    private String address;

    @ApiModelProperty(value = "门店图片 URL（单图，可空）", example = "https://cdn.example.com/store/1.jpg")
    @Size(max = 255, message = "门店图片 URL 长度不能超过 255")
    private String imageUrl;

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
}
