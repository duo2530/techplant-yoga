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

/** 会员 token 过滤器。 */
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
