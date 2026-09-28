package com.techplant.yoga.coach.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

/** 新增教练返回值（详细设计 §2.3.4）。 */
@ApiModel("教练ID")
public class CoachIdVO
{
    @ApiModelProperty(value = "教练ID（雪花ID，字符串）", example = "1856739201475235901")
    private Long id;

    public CoachIdVO() { }
    public CoachIdVO(Long id) { this.id = id; }
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
}
