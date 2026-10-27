package com.techplant.yoga.schedule.vo;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

/** 首页热门课程卡片。 */
@ApiModel("首页热门课程卡片")
@Getter
@Setter
public class ScheduleHotItemVO {
    @ApiModelProperty(value = "排课ID（雪花ID，字符串，进详情页用）", example = "1856739201475239005")
    private Long id;

    @ApiModelProperty(value = "课程名称（课程已删除时空字符串）", example = "迷人腰线")
    private String courseName;

    @ApiModelProperty(value = "课程封面URL（未设置或课程已删除时为 null）", example = "https://cdn.example.com/course/5.jpg")
    private String courseCoverUrl;

    @ApiModelProperty(value = "课种：1团课／2精品课／4特色课", example = "2")
    private Integer courseType;

    @ApiModelProperty(value = "课种名称", example = "精品课")
    private String courseTypeName;

    @ApiModelProperty(value = "课程难度 1~5（课程已删除时为 null）", example = "3")
    private Integer difficulty;

    @ApiModelProperty(value = "教练姓名", example = "赵赵老师")
    private String coachName;

    @ApiModelProperty(value = "开始时间", example = "2026-10-28 19:00:00")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startTime;

    @ApiModelProperty(value = "结束时间", example = "2026-10-28 20:00:00")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endTime;
}
