package com.techplant.yoga.schedule.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

/** 新增排课的请求体。 */
@ApiModel("新增排课请求")
@Getter
@Setter
public class ScheduleCreateDTO {
    @ApiModelProperty(value = "门店ID", required = true, example = "1856739201475235901")
    @NotNull(message = "门店不能为空")
    @Min(value = 1, message = "门店ID必须大于 0")
    private Long storeId;

    @ApiModelProperty(value = "课程ID", required = true, example = "1856739201475235801")
    @NotNull(message = "课程不能为空")
    @Min(value = 1, message = "课程ID必须大于 0")
    private Long courseId;

    @ApiModelProperty(value = "教练ID", required = true, example = "1856739201475237001")
    @NotNull(message = "教练不能为空")
    @Min(value = 1, message = "教练ID必须大于 0")
    private Long coachId;

    @ApiModelProperty(value = "教室ID（归属门店必须与 storeId 一致）", required = true, example = "1856739201475236001")
    @NotNull(message = "教室不能为空")
    @Min(value = 1, message = "教室ID必须大于 0")
    private Long classroomId;

    @ApiModelProperty(value = "开始时间（含日期，与结束时间同一自然日）", required = true, example = "2026-10-03 19:00:00")
    @NotNull(message = "开始时间不能为空")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startTime;

    @ApiModelProperty(value = "结束时间（晚于开始时间且同一自然日）", required = true, example = "2026-10-03 20:00:00")
    @NotNull(message = "结束时间不能为空")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endTime;

    @ApiModelProperty(value = "最大人数（>0）", required = true, example = "20")
    @NotNull(message = "最大人数不能为空")
    private Integer maxPersons;

    @ApiModelProperty(value = "最低开课人数（1<=min<=max，默认 2）", required = true, example = "2")
    @NotNull(message = "最低开课人数不能为空")
    private Integer minPersons;
}
