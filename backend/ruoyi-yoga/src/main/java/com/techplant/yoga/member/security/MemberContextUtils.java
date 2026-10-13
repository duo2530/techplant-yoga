package com.techplant.yoga.member.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import com.ruoyi.common.exception.ServiceException;

/**
 * 当前会员上下文（用户端接口详细设计 §2.6.10、§4.7）。
 *
 * <p>只有 {@link MemberJwtAuthenticationTokenFilter} 校验通过后才会把
 * {@link MemberLoginUser} 写进登录上下文；本类统一从上下文取会员 ID，
 * <b>未登录一律抛 401</b>，不返回 null、不降级放行。</p>
 */
public final class MemberContextUtils
{
    /** 未登录 / 登录态失效 */
    private static final int UNAUTHORIZED = 401;

    private MemberContextUtils()
    {
    }

    /**
     * 当前登录会员 ID
     *
     * @throws ServiceException 未登录或登录态失效（业务码 401）
     */
    public static Long requireMemberId()
    {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof MemberLoginUser)
        {
            return ((MemberLoginUser) authentication.getPrincipal()).getMemberId();
        }
        throw new ServiceException("登录状态已失效，请重新登录", UNAUTHORIZED);
    }
}
