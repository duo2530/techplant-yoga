package com.techplant.yoga.common.log;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import com.techplant.yoga.common.util.CurrentUserUtils;

/**
 * 业务日志（详细设计第 6 章）。
 *
 * <p>每条业务日志都带统一字段：链路标识 / 操作人 / 来源端 / 操作对象 / 动作 / 结果 / 明细 / 耗时。</p>
 *
 * <p>级别口径（§6）：INFO = 写操作成功；WARN = 可预期的业务失败（参数校验失败、课程不存在、
 * 被引用拦截、跨模块调用失败）；ERROR = 非预期异常；DEBUG = 跨模块调用细节，默认关闭。</p>
 *
 * <p>不写进日志：课程介绍（长文本）、完整封面图地址（只记有/无）、请求全量报文。</p>
 */
public final class BusinessLog
{
    /** 业务日志专用 logger，便于单独配置输出文件 */
    private static final Logger log = LoggerFactory.getLogger("com.techplant.yoga.business");

    /** 来源端：管理端 */
    public static final String SOURCE_ADMIN = "admin";

    private static final String NONE = "-";

    private BusinessLog()
    {
    }

    /**
     * 写操作入口日志（§6.1.1 埋点 1、§6.1.2 埋点 1）
     *
     * <p>结果列记为 {@code START}：设计第 6 章只列了 SUCCESS / BLOCKED / FAIL 三种结果，
     * 入口日志此时还没有结果，用 START 表示「已进入、结果未定」，便于定位「提交了但没入库」。</p>
     */
    public static void start(String action, String target, String detail)
    {
        if (log.isInfoEnabled())
        {
            log.info(line(action, target, "START", detail, null));
        }
    }

    /**
     * 写操作成功
     */
    public static void success(String action, String target, String detail, Long costMillis)
    {
        if (log.isInfoEnabled())
        {
            log.info(line(action, target, "SUCCESS", detail, costMillis));
        }
    }

    /**
     * 操作被业务规则拦截（例如停用被引用拦截，最终返回 409）
     */
    public static void blocked(String action, String target, String detail)
    {
        if (log.isWarnEnabled())
        {
            log.warn(line(action, target, "BLOCKED", detail, null));
        }
    }

    /**
     * 可预期的业务失败（例如课程不存在、参数校验失败、跨模块调用失败）
     */
    public static void warn(String action, String target, String detail)
    {
        if (log.isWarnEnabled())
        {
            log.warn(line(action, target, "FAIL", detail, null));
        }
    }

    /**
     * 非预期异常（异常摘要 + 链路标识）
     */
    public static void error(String action, String target, String detail, Throwable throwable)
    {
        if (log.isErrorEnabled())
        {
            log.error(line(action, target, "FAIL", detail, null), throwable);
        }
    }

    /**
     * 跨模块调用细节，默认关闭（§6.1.5 埋点 1）
     */
    public static void debug(String action, String target, String detail, Long costMillis)
    {
        if (log.isDebugEnabled())
        {
            log.debug(line(action, target, "SUCCESS", detail, costMillis));
        }
    }

    /**
     * 统一日志行：traceId / operator / source / target / action / result / detail / cost
     */
    private static String line(String action, String target, String result, String detail, Long costMillis)
    {
        return String.format("traceId=%s operator=%s source=%s target=%s action=%s result=%s detail=%s cost=%sms",
                traceId(), CurrentUserUtils.getUserIdText(), SOURCE_ADMIN, target == null ? NONE : target,
                action == null ? NONE : action, result, detail == null ? NONE : detail,
                costMillis == null ? NONE : String.valueOf(costMillis));
    }

    private static String traceId()
    {
        String traceId = MDC.get(TraceIdFilter.TRACE_ID_KEY);
        return traceId == null ? NONE : traceId;
    }
}
