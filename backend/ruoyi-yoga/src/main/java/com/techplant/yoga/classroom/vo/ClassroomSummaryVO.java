package com.techplant.yoga.classroom.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

/**
 * 教室摘要（教室管理详细设计 §3.2 对外职责：供排课模块按 ID 批量补齐教室名称）。
 *
 * <p>字段：{@code id, storeId, name}；排课模块用 {@code storeId} 与排课门店比对，
 * 用 {@code name} 补齐卡片上的教室名。</p>
 */
@ApiModel("教室摘要")
public class ClassroomSummaryVO
{
    @ApiModelProperty(value = "教室ID（雪花ID，字符串）", example = "1856739201475236001")
    private Long id;

    @ApiModelProperty(value = "所属门店ID", example = "1856739201475235901")
    private Long storeId;

    @ApiModelProperty(value = "教室名称", example = "瑜伽团课大教室")
    private String name;

    public Long getId()
    {
        return id;
    }

    public void setId(Long id)
    {
        this.id = id;
    }

    public Long getStoreId()
    {
        return storeId;
    }

    public void setStoreId(Long storeId)
    {
        this.storeId = storeId;
    }

    public String getName()
    {
        return name;
    }

    public void setName(String name)
    {
        this.name = name;
    }
}
