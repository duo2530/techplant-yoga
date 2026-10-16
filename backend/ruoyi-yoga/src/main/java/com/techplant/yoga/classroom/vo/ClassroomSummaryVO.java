package com.techplant.yoga.classroom.vo;

import lombok.Getter;
import lombok.Setter;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

/**
 * 教室摘要。
 *
 * <p>字段：{@code id, storeId, name}；排课模块用 {@code storeId} 与排课门店比对，
 * 用 {@code name} 补齐卡片上的教室名。</p>
 */
@ApiModel("教室摘要")
@Getter
@Setter
public class ClassroomSummaryVO {
    @ApiModelProperty(value = "教室ID（雪花ID，字符串）", example = "1856739201475236001")
    private Long id;

    @ApiModelProperty(value = "所属门店ID", example = "1856739201475235901")
    private Long storeId;

    @ApiModelProperty(value = "教室名称", example = "瑜伽团课大教室")
    private String name;
}
