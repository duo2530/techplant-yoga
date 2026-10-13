package com.techplant.yoga.member.security;

import java.io.IOException;
import java.io.Serializable;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import com.alibaba.fastjson2.JSON;
import com.ruoyi.common.constant.HttpStatus;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.utils.ServletUtils;

/**
 * 会员认证失败处理（用户端接口详细设计 §2.1.2、§4.7）。
 *
 * <p>只在 {@code /api/**} 的过滤链上生效：未登录／登录态失效统一返回业务码 401，
 * <b>HTTP 仍是 200</b>，与框架管理端口径一致；文案不暴露内部细节。</p>
 */
@Component
public class MemberAuthenticationEntryPointImpl implements AuthenticationEntryPoint, Serializable
{
    private static final long serialVersionUID = 1L;

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException e)
            throws IOException
    {
        String msg = "登录状态已失效，请重新登录";
        ServletUtils.renderString(response, JSON.toJSONString(AjaxResult.error(HttpStatus.UNAUTHORIZED, msg)));
    }
}
