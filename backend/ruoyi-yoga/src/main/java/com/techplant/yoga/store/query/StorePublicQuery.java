package com.techplant.yoga.store.query;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.Size;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

/**
 * 用户端门店列表查询条件（用户端接口详细设计 §2.1）。
 *
 * <p>筛选：{@code regionCode}（精确，字典 store_region，越界即拒绝）、{@code keyword}
 * （<b>同时模糊匹配门店名称与地址</b>）；分页 {@code pageNum}／{@code pageSize}（默认 10，上限 100）。
 * 排序由 DAO 固定为 {@code region_code ASC, name ASC, id DESC}。</p>
 */
@ApiModel("用户端门店列表查询条件")
public class StorePublicQuery
{
    public static final int DEFAULT_PAGE_NUM = 1;
    public static final int DEFAULT_PAGE_SIZE = 10;
    public static final int MAX_PAGE_SIZE = 100;

    @ApiModelProperty("所在区域 code，精确匹配")
    @Size(max = 6, message = "所在区域 code 长度不能超过 6")
    private String regionCode;

    @ApiModelProperty("关键字，同时模糊匹配门店名称与地址")
    @Size(max = 64, message = "关键字长度不能超过 64")
    private String keyword;

    @ApiModelProperty(value = "页码，从 1 开始，默认 1", example = "1")
    @Min(value = 1, message = "页码不能小于 1")
    private Integer pageNum;

    @ApiModelProperty(value = "每页条数，默认 10，最大 100", example = "10")
    @Min(value = 1, message = "每页条数不能小于 1")
    @Max(value = MAX_PAGE_SIZE, message = "每页条数不能超过 100")
    private Integer pageSize;

    public int pageNumOrDefault() { return pageNum == null ? DEFAULT_PAGE_NUM : pageNum; }

    public int pageSizeOrDefault() { return pageSize == null ? DEFAULT_PAGE_SIZE : pageSize; }

    public String getRegionCode() { return regionCode; }
    public void setRegionCode(String regionCode) { this.regionCode = regionCode; }
    public String getKeyword() { return keyword; }
    public void setKeyword(String keyword) { this.keyword = keyword; }
    public Integer getPageNum() { return pageNum; }
    public void setPageNum(Integer pageNum) { this.pageNum = pageNum; }
    public Integer getPageSize() { return pageSize; }
    public void setPageSize(Integer pageSize) { this.pageSize = pageSize; }
}
