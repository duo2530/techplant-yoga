package com.techplant.yoga.member.vo;

import lombok.Getter;
import lombok.Setter;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

/** 登录结果。 */
@ApiModel("登录结果")
@Getter
@Setter
public class LoginVO {
    @ApiModelProperty("用户端 JWT")
    private String token;

    @ApiModelProperty("过期时间戳（毫秒）")
    private Long expireTime;

    @ApiModelProperty("会员ID（字符串形式雪花 ID）")
    private Long memberId;

    @ApiModelProperty("会员显示名")
    private String memberName;
}
