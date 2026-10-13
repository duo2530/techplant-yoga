package com.techplant.yoga.member.security;

import java.io.Serializable;

/** 会员登录对象。 */
public class MemberLoginUser implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 会话标识（JWT 里的随机会话 ID，也是 Redis key 的后缀） */
    private String token;

    /** 会员ID（服务端登录主体与业务主体） */
    private Long memberId;

    /** 手机号 */
    private String phone;

    /** 登录时间戳（毫秒） */
    private Long loginTime;

    /** 过期时间戳（毫秒），有效期内不自动续期 */
    private Long expireTime;

    /** 登录 IP */
    private String ipaddr;

    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }
    public Long getMemberId() { return memberId; }
    public void setMemberId(Long memberId) { this.memberId = memberId; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public Long getLoginTime() { return loginTime; }
    public void setLoginTime(Long loginTime) { this.loginTime = loginTime; }
    public Long getExpireTime() { return expireTime; }
    public void setExpireTime(Long expireTime) { this.expireTime = expireTime; }
    public String getIpaddr() { return ipaddr; }
    public void setIpaddr(String ipaddr) { this.ipaddr = ipaddr; }
}
