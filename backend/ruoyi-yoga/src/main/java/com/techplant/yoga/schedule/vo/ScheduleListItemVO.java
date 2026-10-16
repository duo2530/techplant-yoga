package com.techplant.yoga.schedule.vo;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

/** 管理端排课列表项。 */
@ApiModel("管理端排课列表项")
@Getter
@Setter
public class ScheduleListItemVO {
    @ApiModelProperty(value = "排课ID（雪花ID，字符串）", example = "1856739201475239001")
    private Long id;

    @ApiModelProperty(value = "门店ID", example = "1856739201475235901")
    private Long storeId;

    @ApiModelProperty(value = "门店名称（对象已删除时为空字符串）", example = "徐汇店")
    private String storeName;

    @ApiModelProperty(value = "课程ID", example = "1856739201475235801")
    private Long courseId;

    @ApiModelProperty(value = "课程名称（对象已删除时为空字符串）", example = "哈他瑜伽")
    private String courseName;

    @ApiModelProperty(value = "课种快照：1团课 2精品课 3私教课 4特色课", example = "1")
    private Integer courseType;

    @ApiModelProperty(value = "课种名称（按快照码翻译）", example = "团课")
    private String courseTypeName;

    @ApiModelProperty(value = "教练ID", example = "1856739201475237001")
    private Long coachId;

    @ApiModelProperty(value = "教练姓名（对象已删除时为空字符串）", example = "王老师")
    private String coachName;

    @ApiModelProperty(value = "教室ID", example = "1856739201475236001")
    private Long classroomId;

    @ApiModelProperty(value = "教室名称（对象已删除时为空字符串）", example = "瑜伽团课大教室")
    private String classroomName;

    @ApiModelProperty(value = "上课日期（由开始时间推导，筛选按本列）", example = "2026-10-03")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate scheduleDate;

    @ApiModelProperty(value = "开始时间", example = "2026-10-03 19:00:00")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startTime;

    @ApiModelProperty(value = "结束时间", example = "2026-10-03 20:00:00")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endTime;

    @ApiModelProperty(value = "最大人数", example = "20")
    private Integer maxPersons;

    @ApiModelProperty(value = "最低开课人数", example = "2")
    private Integer minPersons;

    @ApiModelProperty(value = "已预约人数（一期恒 0）", example = "0")
    private Integer bookedPersons;

    @ApiModelProperty(value = "落库状态：1待上架 2已上架 3已取消", example = "2")
    private Integer status;

    @ApiModelProperty(value = "展示文案：待上架／已上架／已取消／已结束", example = "已上架")
    private String statusText;

    @ApiModelProperty(value = "是否已结束（status=2 且 end_time<=当前时间）", example = "false")
    private Boolean finished;
}
