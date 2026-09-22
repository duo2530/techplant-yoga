package com.techplant.yoga.course.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

/**
 * 新增课程的返回数据（详细设计 §2.2.3 的 200 响应结构：{@code data = { id }}）。
 *
 * <p>{@code id} 在响应里是字符串（§2.1.5），由 {@code JacksonConfig} 把业务对象的 ID 字段统一转换。</p>
 */
@ApiModel("新增课程返回")
public class CourseCreatedVO
{
    @ApiModelProperty(value = "课程ID（雪花ID，字符串）", example = "1856739201475235840")
    private Long id;

    public CourseCreatedVO()
    {
    }

    public CourseCreatedVO(Long id)
    {
        this.id = id;
    }

    public Long getId()
    {
        return id;
    }

    public void setId(Long id)
    {
        this.id = id;
    }
}
