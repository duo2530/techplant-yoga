package com.techplant.yoga.store.query;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.Size;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

/** 门店管理列表查询条件。 */
@ApiModel("门店列表查询条件")
public class StoreQuery
{
    public static final int DEFAULT_PAGE_NUM = 1;
    public static final int DEFAULT_PAGE_SIZE = 10;
    public static final int MAX_PAGE_SIZE = 100;

    @ApiModelProperty("门店名称，模糊匹配")
    @Size(max = 64, message = "门店名称长度不能超过 64")
    private String name;

    @ApiModelProperty("所在区域，模糊匹配")
    @Size(max = 64, message = "所在区域长度不能超过 64")
    private String region;

    @ApiModelProperty(value = "经营类型：1直营连锁 2加盟", example = "1")
    @Min(value = 1, message = "经营类型取值为 1 或 2")
    @Max(value = 2, message = "经营类型取值为 1 或 2")
    private Integer businessType;

    @ApiModelProperty(value = "门店类型：1主力店 2精品店", example = "1")
    @Min(value = 1, message = "门店类型取值为 1 或 2")
    @Max(value = 2, message = "门店类型取值为 1 或 2")
    private Integer storeType;

    @ApiModelProperty(value = "门店状态：1启用 0停用；不传不限", example = "1")
    @Min(value = 0, message = "门店状态取值为 0 或 1")
    @Max(value = 1, message = "门店状态取值为 0 或 1")
    private Integer status;

    @ApiModelProperty(value = "页码，从 1 开始，默认 1", example = "1")
    @Min(value = 1, message = "页码不能小于 1")
    private Integer pageNum;

    @ApiModelProperty(value = "每页条数，默认 10，最大 100", example = "10")
    @Min(value = 1, message = "每页条数不能小于 1")
    @Max(value = MAX_PAGE_SIZE, message = "每页条数不能超过 100")
    private Integer pageSize;

    public int pageNumOrDefault() { return pageNum == null ? DEFAULT_PAGE_NUM : pageNum; }
    public int pageSizeOrDefault() { return pageSize == null ? DEFAULT_PAGE_SIZE : pageSize; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getRegion() { return region; }
    public void setRegion(String region) { this.region = region; }
    public Integer getBusinessType() { return businessType; }
    public void setBusinessType(Integer businessType) { this.businessType = businessType; }
    public Integer getStoreType() { return storeType; }
    public void setStoreType(Integer storeType) { this.storeType = storeType; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public Integer getPageNum() { return pageNum; }
    public void setPageNum(Integer pageNum) { this.pageNum = pageNum; }
    public Integer getPageSize() { return pageSize; }
    public void setPageSize(Integer pageSize) { this.pageSize = pageSize; }
}
