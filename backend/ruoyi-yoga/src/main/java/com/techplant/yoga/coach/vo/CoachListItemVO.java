package com.techplant.yoga.coach.vo;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

/**
 * 管理端教练列表项。
 *
 * <p>列表<b>不返回</b> {@code intro} 与 {@code gallery} 两个大字段。</p>
 */
@ApiModel("教练列表项")
@Getter
@Setter
public class CoachListItemVO {
    @ApiModelProperty(value = "教练ID（雪花ID，字符串）", example = "1856739201475237001")
    private Long id;

    @ApiModelProperty(value = "姓名", example = "林教练")
    private String name;

    @ApiModelProperty(value = "头像URL")
    private String avatarUrl;

    @ApiModelProperty(value = "联系电话")
    private String phone;

    @ApiModelProperty(value = "教练等级：1 普通、2 金牌", example = "1")
    private Integer level;

    @ApiModelProperty(value = "创建时间", example = "2026-09-28 10:00:00")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    @ApiModelProperty(value = "更新时间", example = "2026-09-28 10:00:00")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;
}
