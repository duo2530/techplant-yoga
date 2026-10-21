package com.techplant.yoga.coach.vo;

import lombok.Getter;
import lombok.Setter;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

/**
 * 用户端金牌教练卡片。
 *
 * <p>只包含首页卡片需要的公开字段，<b>不含</b>联系电话、等级与审计信息。</p>
 */
@ApiModel("用户端金牌教练卡片")
@Getter
@Setter
public class CoachFeaturedVO {
    @ApiModelProperty(value = "教练ID（雪花ID，字符串）", example = "1856739201475237001")
    private Long id;

    @ApiModelProperty(value = "姓名", example = "林教练")
    private String name;

    @ApiModelProperty(value = "头像URL")
    private String avatarUrl;

    @ApiModelProperty(value = "简介")
    private String intro;
}
