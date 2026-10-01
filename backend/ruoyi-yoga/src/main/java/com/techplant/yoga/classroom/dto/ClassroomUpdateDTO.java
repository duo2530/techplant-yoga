package com.techplant.yoga.classroom.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

/**
 * 修改教室的请求体（教室管理详细设计 §2.3.2、§3.4 第 2 条）。
 *
 * <p><b>只有 {@code name}</b>：本类<b>不定义 {@code storeId} 字段</b>，从入参层面就杜绝「换门店」
 * （换门店等价于删除后在新门店下重建，BR-教室-004）。</p>
 */
@ApiModel("修改教室请求")
public class ClassroomUpdateDTO
{
    @ApiModelProperty(value = "教室名称（同一门店内唯一）", required = true, example = "普拉提小班课")
    @NotBlank(message = "教室名称不能为空")
    @Size(max = 32, message = "教室名称长度不能超过 32")
    private String name;

    public String getName()
    {
        return name;
    }

    public void setName(String name)
    {
        this.name = name;
    }
}
