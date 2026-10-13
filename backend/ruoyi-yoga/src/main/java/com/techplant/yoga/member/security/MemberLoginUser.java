package com.techplant.yoga.member.security;

import java.io.Serializable;

/**
 * 会员登录对象（用户端接口详细设计 §2.6.10、§4.5）。
 *
 * <p><b>只存 Redis，不对外返回</b>：JWT 只携带随机会话标识，会员身份与会话有效期放在本对象里，
 * Redis key 前缀 {@code member_tokens:}，与管理端 {@code login_tokens:} 隔离。</p>
 *
 * <p>{@code token} 存的是<b>会话标识（uuid）</b>，与框架的 {@code LoginUser} 同一写法，
 * 便于退出登录时按会话删 key；真实的 JWT 字符串只发给客户端。</p>
 *
 * <p>本对象经 Redis 的 fastjson2 值序列化器（带 {@code WriteClassName}）读写，
 * 因此类必须有无参构造与 getter/setter，且所在包要在
 * {@code Constants.JSON_WHITELIST_STR} 白名单内。</p>
 */
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
