package com.techplant.yoga.schedule.vo;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

/** 首页今日可约团课卡片。 */
@ApiModel("首页今日可约团课卡片")
@Getter
@Setter
public class ScheduleHomeItemVO {
    @ApiModelProperty(value = "排课ID（雪花ID，字符串，进详情页用）", example = "1856739201475239001")
    private Long id;

    @ApiModelProperty(value = "课程名称（课程已删除时空字符串）", example = "扭转排毒")
    private String courseName;

    @ApiModelProperty(value = "教练姓名", example = "岳岳老师")
    private String coachName;

    @ApiModelProperty(value = "开始时间", example = "2026-10-26 11:00:00")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startTime;

    @ApiModelProperty(value = "结束时间", example = "2026-10-26 12:00:00")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endTime;

    @ApiModelProperty(value = "教室名称（教室已删除时空字符串）", example = "瑜伽团课大教室")
    private String classroomName;

    @ApiModelProperty(value = "已预约人数", example = "6")
    private Integer bookedPersons;

    @ApiModelProperty(value = "最大人数", example = "15")
    private Integer maxPersons;
}
