package com.techplant.yoga.coach.vo;

import lombok.Getter;
import lombok.Setter;

import java.util.List;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

/**
 * 用户端教练公开详情。
 *
 * <p>只包含资料与相册的公开字段，<b>不含</b>联系电话、等级与审计信息。</p>
 */
@ApiModel("用户端教练公开详情")
@Getter
@Setter
public class CoachPublicDetailVO {
    @ApiModelProperty(value = "教练ID（雪花ID，字符串）", example = "1856739201475237001")
    private Long id;

    @ApiModelProperty(value = "姓名", example = "林教练")
    private String name;

    @ApiModelProperty(value = "头像URL")
    private String avatarUrl;

    @ApiModelProperty(value = "简介")
    private String intro;

    @ApiModelProperty(value = "相册，按保存顺序返回；空相册为空数组")
    private List<String> gallery;
}
