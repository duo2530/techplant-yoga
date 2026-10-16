package com.techplant.yoga.schedule.vo;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

/** 用户端排课详情。 */
@ApiModel("用户端排课详情")
@Getter
public class PublicScheduleDetailVO extends ScheduleCardVO {
    @ApiModelProperty(value = "课程ID", example = "1856739201475235801")
    @Setter
    private Long courseId;

    @ApiModelProperty(value = "课程介绍（实时取课程）")
    @Setter
    private String courseIntro;

    @ApiModelProperty(value = "教练ID", example = "1856739201475237001")
    @Setter
    private Long coachId;

    @ApiModelProperty(value = "教练头像URL")
    @Setter
    private String coachAvatarUrl;

    @ApiModelProperty(value = "教练简介")
    @Setter
    private String coachIntro;

    @ApiModelProperty(value = "门店地址（关联门店已删除时为空）")
    @Setter
    private String storeAddress;

    @ApiModelProperty(value = "最低开课人数（需求里的「开课人数」）", example = "2")
    @Setter
    private Integer minPersons;

    @ApiModelProperty(value = "预约会员显示名（脱敏，最多 10 位）", example = "[\"张*\"]")
    private List<String> bookers = new ArrayList<String>();

    public void setBookers(List<String> bookers) {
        this.bookers = bookers == null ? new ArrayList<String>() : bookers;
    }
}
