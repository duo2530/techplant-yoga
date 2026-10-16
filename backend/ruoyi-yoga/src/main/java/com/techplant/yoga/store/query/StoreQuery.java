package com.techplant.yoga.store.query;

import lombok.Getter;
import lombok.Setter;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.Size;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

/**
 * 管理端门店列表查询条件。
 *
 * <p>筛选：{@code name}（模糊）、{@code regionCode}（精确，字典 store_region）、
 * {@code storeType}（1／2）；分页 {@code pageNum}／{@code pageSize}（默认 10，上限 100）。
 * 排序由 DAO 固定为 {@code store_type ASC, id DESC}。</p>
 */
@ApiModel("门店列表查询条件")
@Getter
@Setter
public class StoreQuery {
    public static final int DEFAULT_PAGE_NUM = 1;
    public static final int DEFAULT_PAGE_SIZE = 10;
    public static final int MAX_PAGE_SIZE = 100;

    @ApiModelProperty("门店名称，模糊匹配")
    @Size(max = 64, message = "门店名称长度不能超过 64")
    private String name;

    @ApiModelProperty("所在区域 code，精确匹配")
    @Size(max = 6, message = "所在区域 code 长度不能超过 6")
    private String regionCode;

    @ApiModelProperty(value = "门店类型：1主力店 2精品店", example = "1")
    @Min(value = 1, message = "门店类型取值为 1 或 2")
    @Max(value = 2, message = "门店类型取值为 1 或 2")
    private Integer storeType;

    @ApiModelProperty(value = "页码，从 1 开始，默认 1", example = "1")
    @Min(value = 1, message = "页码不能小于 1")
    private Integer pageNum;

    @ApiModelProperty(value = "每页条数，默认 10，最大 100", example = "10")
    @Min(value = 1, message = "每页条数不能小于 1")
    @Max(value = MAX_PAGE_SIZE, message = "每页条数不能超过 100")
    private Integer pageSize;

    public int pageNumOrDefault() {
        return pageNum == null ? DEFAULT_PAGE_NUM : pageNum;
    }

    public int pageSizeOrDefault() {
        return pageSize == null ? DEFAULT_PAGE_SIZE : pageSize;
    }
}
