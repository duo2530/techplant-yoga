package com.techplant.yoga.common.log;

import java.io.IOException;
import java.util.UUID;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import com.ruoyi.common.utils.StringUtils;

/**
 * 链路标识过滤器（详细设计第 6 章「统一格式」的「链路标识」字段）。
 *
 * <p>由它生成/透传 traceId 并写入 MDC，业务日志通过 {@link BusinessLog} 自动带上；
 * 同时回写响应头 {@code X-Trace-Id}，便于前端与运维按标识排查。</p>
 *
 * <p>说明：设计里链路标识属于「统一的请求过滤器」（公共模块职责）。本模块先自带一个最小实现，
 * 等公共模块建立后再合并。</p>
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@Component
public class TraceIdFilter extends OncePerRequestFilter
{
    /** MDC 中的链路标识键 */
    public static final String TRACE_ID_KEY = "traceId";

    /** 链路标识请求/响应头 */
    public static final String TRACE_ID_HEADER = "X-Trace-Id";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException
    {
        String traceId = request.getHeader(TRACE_ID_HEADER);
        if (StringUtils.isEmpty(traceId))
        {
            traceId = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        }
        MDC.put(TRACE_ID_KEY, traceId);
        response.setHeader(TRACE_ID_HEADER, traceId);
        try
        {
            filterChain.doFilter(request, response);
        }
        finally
        {
            MDC.remove(TRACE_ID_KEY);
        }
    }
}
