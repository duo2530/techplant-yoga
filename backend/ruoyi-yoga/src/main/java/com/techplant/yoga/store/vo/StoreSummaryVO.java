package com.techplant.yoga.store.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

/**
 * 门店摘要（跨模块批量补齐名称用）。
 *
 * <p>教室模块用它批量补 {@code storeName}，排课模块用它批量补门店名称；
 * 由 {@code StoreService#summaries(java.util.Collection)} 提供（门店管理详细设计 §3.2、
 * 详细设计总览 §6「跨模块调用一律走对方的 Service」）。</p>
 */
@ApiModel("门店摘要")
public class StoreSummaryVO
{
    @ApiModelProperty(value = "门店ID（雪花ID，字符串）", example = "1856739201475235901")
    private Long id;

    @ApiModelProperty(value = "门店名称", example = "徐汇店")
    private String name;

    public StoreSummaryVO()
    {
    }

    public StoreSummaryVO(Long id, String name)
    {
        this.id = id;
        this.name = name;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
}
