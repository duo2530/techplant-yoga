package com.techplant.yoga.member.security;

import java.io.IOException;
import java.util.Collections;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;
import com.ruoyi.common.utils.SecurityUtils;
import com.techplant.yoga.member.service.MemberService;
import com.techplant.yoga.member.service.MemberTokenService;

/**
 * 会员 token 过滤器（用户端接口详细设计 §4.7）。
 *
 * <p><b>只挂在 {@code /api/**} 的过滤链上</b>（见 {@code MemberSecurityConfig}）：
 * 会员 token 只对用户端接口生效，{@code /admin/**} 与 {@code sys_*} 一律仍走
 * {@code sys_user} JWT，两边互不认账（§2.1.2）。</p>
 *
 * <p><b>刻意不做成 {@code @Component}</b>：Spring Boot 会把容器里的 {@link javax.servlet.Filter}
 * Bean 自动注册成全局 servlet 过滤器，那样会员 token 就能在 {@code /admin/**} 上也建立登录上下文，
 * 正是 §2.1.2 要避免的越权。因此本类由 {@code MemberSecurityConfig} 手工 new 出来，
 * 只进安全过滤链。</p>
 *
 * <p><b>校验顺序</b>（§4.7）：解析会员 JWT 取 Redis 会话（存在性 + 过期时间）→
 * 会员仍存在且已注册 → 才写入登录上下文。数据库查询失败<b>不降级放行</b>，异常直接向上抛。</p>
 */
public class MemberJwtAuthenticationTokenFilter extends OncePerRequestFilter
{
    private static final Logger log = LoggerFactory.getLogger(MemberJwtAuthenticationTokenFilter.class);

    private final MemberTokenService memberTokenService;

    private final MemberService memberService;

    public MemberJwtAuthenticationTokenFilter(MemberTokenService memberTokenService, MemberService memberService)
    {
        this.memberTokenService = memberTokenService;
        this.memberService = memberService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException
    {
        MemberLoginUser loginUser = memberTokenService.getLoginUser(request);
        if (loginUser != null && SecurityUtils.getAuthentication() == null)
        {
            if (memberService.isRegistered(loginUser.getMemberId()))
            {
                UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(
                        loginUser, null, Collections.emptyList());
                authenticationToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authenticationToken);
            }
            else
            {
                // 会员已不存在或未注册：不写登录上下文，并尽力清掉本次请求的失效会话
                log.info("会员[{}]不存在或未注册，本次请求按未登录处理", loginUser.getMemberId());
                try
                {
                    memberTokenService.revokeSession(request);
                }
                catch (RuntimeException e)
                {
                    log.warn("清理失效会员会话失败：{}", e.getMessage());
                }
            }
        }
        chain.doFilter(request, response);
    }
}
