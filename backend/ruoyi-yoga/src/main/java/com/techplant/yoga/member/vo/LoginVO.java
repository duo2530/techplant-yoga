package com.techplant.yoga.member.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

/** 登录结果。 */
@ApiModel("登录结果")
public class LoginVO
{
    @ApiModelProperty("用户端 JWT")
    private String token;

    @ApiModelProperty("过期时间戳（毫秒）")
    private Long expireTime;

    @ApiModelProperty("会员ID（字符串形式雪花 ID）")
    private Long memberId;

    @ApiModelProperty("会员显示名")
    private String memberName;

    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }
    public Long getExpireTime() { return expireTime; }
    public void setExpireTime(Long expireTime) { this.expireTime = expireTime; }
    public Long getMemberId() { return memberId; }
    public void setMemberId(Long memberId) { this.memberId = memberId; }
    public String getMemberName() { return memberName; }
    public void setMemberName(String memberName) { this.memberName = memberName; }
}
