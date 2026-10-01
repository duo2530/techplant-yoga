package com.techplant.yoga.schedule.enums;

import java.time.LocalDateTime;

/**
 * 排课状态枚举：状态码、「已结束」的推导、两套状态文案与 {@code bookable} 都收在这一处
 * （排课管理详细设计 §1.2.2、§3.2、§3.4 第 2 条）。
 *
 * <p><b>落库只有三个值</b>：{@code 1} 待上架、{@code 2} 已上架、{@code 3} 已取消。
 * <b>「已结束」不落库</b>，是派生状态：{@code status == 2 && end_time <= 当前时间}（{@code BR-排课-010}）。</p>
 *
 * <p><b>两套文案</b>（{@code BR-排课-020}）：</p>
 * <ul>
 *   <li>管理端：待上架／已上架／已取消／<b>已结束</b>；</li>
 *   <li>用户端：<b>可约</b>／已结束／已取消 —— 用户端不出现「待上架」「已上架」措辞。</li>
 * </ul>
 *
 * <p><b>判定落点在 service 层</b>：返回 controller 之前由 service 调用本枚举判定一次，
 * 装配 {@code finished} / {@code statusText} / {@code bookable}；controller 不判断、前端不推导。</p>
 *
 * <p><b>唯一例外</b>：删除门禁的 count 查询把 {@code end_time > NOW()} 写在 SQL 条件里
 * （见 {@code ScheduleDaoImpl#buildActiveCondition()}），不走本枚举。</p>
 */
public enum ScheduleStatusEnum
{
    /** 1 待上架：用户端不可见，可编辑、可物理删除 */
    PENDING(1, "待上架"),

    /** 2 已上架：用户端可见（未结束即可约），须先下架才能编辑／删除 */
    PUBLISHED(2, "已上架"),

    /** 3 已取消：用户端可见，终态（不可编辑／不可改状态／不可删除） */
    CANCELLED(3, "已取消");

    /** 「已结束」的展示文案（管理端与用户端同字，仅用户端不会出现「已上架」） */
    public static final String FINISHED_TEXT = "已结束";

    /** 用户端「已上架且未结束」的文案 */
    public static final String BOOKABLE_TEXT = "可约";

    /**
     * 待上架在用户端的兜底文案：用户端口径是 {@code status <> 1}，理论不可达；
     * 万一被调用也**不能**把「待上架」这个管理端措辞透出去。
     */
    public static final String INVISIBLE_TEXT = "";

    /** 非法状态码的兜底文案 */
    public static final String UNKNOWN_TEXT = "";

    /** 状态码：1 待上架／2 已上架／3 已取消 */
    private final int code;

    /** 管理端展示文案 */
    private final String adminText;

    ScheduleStatusEnum(int code, String adminText)
    {
        this.code = code;
        this.adminText = adminText;
    }

    public int getCode()
    {
        return code;
    }

    public String getAdminText()
    {
        return adminText;
    }

    /**
     * 按状态码取枚举
     *
     * @param code 状态码
     * @return 命中返回枚举，{@code null} 或越界返回 {@code null}
     */
    public static ScheduleStatusEnum of(Integer code)
    {
        if (code == null)
        {
            return null;
        }
        for (ScheduleStatusEnum status : values())
        {
            if (status.code == code)
            {
                return status;
            }
        }
        return null;
    }

    /**
     * 状态码是否合法（{@code 1}~{@code 3}）；{@code null} 视为不合法
     */
    public static boolean isValid(Integer code)
    {
        return of(code) != null;
    }

    /**
     * 推导「已结束」：{@code status == 2 && end_time <= now}
     *
     * @param status  落库状态码
     * @param endTime 结束时间，{@code null} 视为未结束
     * @param now     判定基准时间（同一个请求内只用同一个 {@code now}，保证同一页数据口径一致）
     * @return 是否已结束
     */
    public static boolean isFinished(Integer status, LocalDateTime endTime, LocalDateTime now)
    {
        if (status == null || endTime == null || now == null)
        {
            return false;
        }
        return status == PUBLISHED.code && !endTime.isAfter(now);
    }

    /**
     * 管理端展示文案：待上架／已上架／已取消／已结束
     */
    public static String adminTextOf(Integer status, LocalDateTime endTime, LocalDateTime now)
    {
        ScheduleStatusEnum matched = of(status);
        if (matched == null)
        {
            return UNKNOWN_TEXT;
        }
        if (isFinished(status, endTime, now))
        {
            return FINISHED_TEXT;
        }
        return matched.adminText;
    }

    /**
     * 用户端展示文案：可约／已结束／已取消
     *
     * <p>只可能是这三种之一；待上架（1）理论上查不到，兜底为空字符串。</p>
     */
    public static String publicTextOf(Integer status, LocalDateTime endTime, LocalDateTime now)
    {
        ScheduleStatusEnum matched = of(status);
        if (matched == null)
        {
            return UNKNOWN_TEXT;
        }
        if (matched == PENDING)
        {
            return INVISIBLE_TEXT;
        }
        if (matched == CANCELLED)
        {
            return CANCELLED.adminText;
        }
        return isFinished(status, endTime, now) ? FINISHED_TEXT : BOOKABLE_TEXT;
    }

    /**
     * 用户端是否可发起预约：{@code status == 2} 且 {@code end_time > now}
     *
     * <p>一期「立即预约」点击只提示「敬请期待」，本字段仅用于前端按钮置灰（{@code BR-排课-018}）。</p>
     */
    public static boolean isBookable(Integer status, LocalDateTime endTime, LocalDateTime now)
    {
        return status != null && status == PUBLISHED.code && endTime != null && now != null
                && endTime.isAfter(now);
    }
}
