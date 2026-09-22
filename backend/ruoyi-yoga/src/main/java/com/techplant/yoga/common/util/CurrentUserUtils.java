package com.techplant.yoga.common.util;

import com.ruoyi.common.utils.SecurityUtils;

/**
 * 当前登录人工具：审计字段与业务日志都要写「操作人」（详细设计 §1.2.1、第 6 章）。
 *
 * <p>{@link SecurityUtils#getUserId()} 在未登录时会抛异常；审计填充与日志不应因此打断主流程，
 * 因此这里统一降级为 null / “-”。</p>
 */
public final class CurrentUserUtils
{
    private CurrentUserUtils()
    {
    }

    /**
     * 当前登录用户ID，未登录返回 null
     */
    public static Long getUserIdOrNull()
    {
        try
        {
            return SecurityUtils.getUserId();
        }
        catch (Exception e)
        {
            return null;
        }
    }

    /**
     * 当前登录用户ID的字符串形式，未登录返回 "-"（用于日志字段）
     */
    public static String getUserIdText()
    {
        Long userId = getUserIdOrNull();
        return userId == null ? "-" : String.valueOf(userId);
    }
}
