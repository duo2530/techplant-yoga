package com.techplant.yoga.member.security;

import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;

/** 会员登录对象。 */
@Getter
@Setter
public class MemberLoginUser implements Serializable {
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
}
