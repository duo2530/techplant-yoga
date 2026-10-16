package com.techplant.yoga.classroom.dto;

import lombok.Getter;
import lombok.Setter;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

/**
 * 新增教室的请求体：{@code storeId} 与 {@code name} 均必填。
 */
@ApiModel("新增教室请求")
@Getter
@Setter
public class ClassroomCreateDTO {
    @ApiModelProperty(value = "所属门店ID", required = true, example = "1856739201475235901")
    @NotNull(message = "所属门店不能为空")
    @Min(value = 1, message = "门店ID必须大于 0")
    private Long storeId;

    @ApiModelProperty(value = "教室名称（同一门店内唯一）", required = true, example = "普拉提器械教室")
    @NotBlank(message = "教室名称不能为空")
    @Size(max = 32, message = "教室名称长度不能超过 32")
    private String name;
}
