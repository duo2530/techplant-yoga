package com.techplant.yoga.store.vo;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

/**
 * 管理端门店列表项。
 *
 * <p>{@code storeType} + {@code storeTypeName}、{@code regionCode} + {@code regionName}
 * <b>成对返回</b>，前端不自己维护「码 → 名」映射。门店字段不多，
 * 列表不裁剪，字段与 {@link StoreDetailVO} 一致。</p>
 */
@ApiModel("门店列表项")
@Getter
@Setter
public class StoreListItemVO {
    @ApiModelProperty(value = "门店ID（雪花ID，字符串）", example = "1856739201475235901")
    private Long id;

    @ApiModelProperty(value = "门店编号（四位数字，服务端分配、创建后不可修改）", example = "0001")
    private String storeNo;

    @ApiModelProperty(value = "门店名称", example = "徐汇店")
    private String name;

    @ApiModelProperty(value = "门店类型：1主力店 2精品店", example = "1")
    private Integer storeType;

    @ApiModelProperty(value = "门店类型名称", example = "主力店")
    private String storeTypeName;

    @ApiModelProperty(value = "所在区域 code（字典 store_region）", example = "310104")
    private String regionCode;

    @ApiModelProperty(value = "所在区域名称（后端按字典翻译）", example = "徐汇区")
    private String regionName;

    @ApiModelProperty(value = "联系电话", example = "021-12345678")
    private String phone;

    @ApiModelProperty(value = "营业时间", example = "周一至周日 09:00-22:00")
    private String businessHours;

    @ApiModelProperty(value = "地址", example = "漕溪北路 88 号")
    private String address;

    @ApiModelProperty(value = "门店图片 URL（单图，可空）", example = "https://cdn.example.com/store/1.jpg")
    private String imageUrl;

    @ApiModelProperty("创建时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    @ApiModelProperty("更新时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;
}
