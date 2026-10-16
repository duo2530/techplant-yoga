package com.techplant.yoga.course.vo;

import lombok.Getter;
import lombok.Setter;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

/** 课程摘要。 */
@ApiModel("课程摘要")
@Getter
@Setter
public class CourseSummaryVO {
    @ApiModelProperty(value = "课程ID（雪花ID，字符串）", example = "1856739201475235801")
    private Long id;

    @ApiModelProperty(value = "课程名称", example = "哈他瑜伽")
    private String name;

    @ApiModelProperty(value = "课种：1团课 2精品课 3私教课 4特色课", example = "1")
    private Integer courseType;

    @ApiModelProperty(value = "课种名称（由 CourseTypeEnum 翻译）", example = "团课")
    private String courseTypeName;

    @ApiModelProperty(value = "课程封面URL（单图，可空）")
    private String coverUrl;

    @ApiModelProperty(value = "课程介绍")
    private String intro;

    @ApiModelProperty(value = "课程难度：1~5 星", example = "2")
    private Integer difficulty;
}
