package com.techplant.yoga.coach.vo;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

/**
 * 管理端教练详情。
 *
 * <p>{@code gallery} 是出参里的 {@code List<String>}；DO 中承载的是 JSON 字符串，
 * 两者之间的转换在 service 层完成，本 VO 不关心存储形态。</p>
 */
@ApiModel("教练详情")
@Getter
@Setter
public class CoachDetailVO {
    @ApiModelProperty(value = "教练ID（雪花ID，字符串）", example = "1856739201475237001")
    private Long id;

    @ApiModelProperty(value = "姓名", example = "林教练")
    private String name;

    @ApiModelProperty(value = "头像URL")
    private String avatarUrl;

    @ApiModelProperty(value = "联系电话")
    private String phone;

    @ApiModelProperty(value = "简介")
    private String intro;

    @ApiModelProperty(value = "相册，最多 5 张")
    private List<String> gallery;

    @ApiModelProperty(value = "创建时间", example = "2026-09-28 10:00:00")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    @ApiModelProperty(value = "更新时间", example = "2026-09-28 10:00:00")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;
}
