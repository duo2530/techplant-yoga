package com.techplant.yoga.classroom.dto;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

/**
 * 新增教室的请求体（教室管理详细设计 §2.3.2）：{@code storeId} 与 {@code name} 均必填。
 */
@ApiModel("新增教室请求")
public class ClassroomCreateDTO
{
    @ApiModelProperty(value = "所属门店ID", required = true, example = "1856739201475235901")
    @NotNull(message = "所属门店不能为空")
    @Min(value = 1, message = "门店ID必须大于 0")
    private Long storeId;

    @ApiModelProperty(value = "教室名称（同一门店内唯一）", required = true, example = "普拉提器械教室")
    @NotBlank(message = "教室名称不能为空")
    @Size(max = 32, message = "教室名称长度不能超过 32")
    private String name;

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
