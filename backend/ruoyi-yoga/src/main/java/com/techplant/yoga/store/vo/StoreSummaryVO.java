package com.techplant.yoga.store.vo;

import lombok.Getter;
import lombok.Setter;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

/**
 * 门店摘要（跨模块批量补齐名称用）。
 *
 * <p>教室模块用它批量补 {@code storeName}，排课模块用它批量补门店名称，会员卡模块用它校验开卡门店／适用门店存在并取门店编号生成卡号，
 * 预约与排课详情用它补门店地址；由 {@code StoreService#summaries(java.util.Collection)} 提供。</p>
 */
@ApiModel("门店摘要")
@Getter
@Setter
public class StoreSummaryVO {
    @ApiModelProperty(value = "门店ID（雪花ID，字符串）", example = "1856739201475235901")
    private Long id;

    @ApiModelProperty(value = "门店编号（四位数字）", example = "0001")
    private String storeNo;

    @ApiModelProperty(value = "门店名称", example = "徐汇店")
    private String name;

    @ApiModelProperty(value = "门店地址", example = "漕溪北路 88 号")
    private String address;

    public StoreSummaryVO() {
    }

    public StoreSummaryVO(Long id, String storeNo, String name) {
        this(id, storeNo, name, null);
    }

    public StoreSummaryVO(Long id, String storeNo, String name, String address) {
        this.id = id;
        this.storeNo = storeNo;
        this.name = name;
        this.address = address;
    }
}
