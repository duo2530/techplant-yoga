package com.techplant.yoga.member.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

/**
 * 登录结果（用户端接口详细设计 §2.2.6、§2.6.9）。
 *
 * <p>{@code token} 是用户端 JWT（与管理端 token 隔离）；{@code memberId} 是服务端登录主体与业务主体，
 * 客户端传入的会员 ID 一律不作为访问依据；{@code memberId} 字段名以 {@code Id} 结尾，
 * 由 {@code JacksonConfig} 序列化成字符串，避免小程序 Number 精度丢失。</p>
 */
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
