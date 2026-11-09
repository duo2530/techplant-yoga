package com.techplant.yoga.schedule.enums;

import lombok.Getter;

import java.time.LocalDateTime;

/** 排课状态枚举：状态码、管理端「已结束」的推导与管理端状态文案；用户端展示状态见 {@link PublicScheduleStateEnum}。 */
@Getter
public enum ScheduleStatusEnum {
    /** 1 待上架：用户端不可见，可编辑、可物理删除 */
    PENDING(1, "待上架"),

    /** 2 已上架：用户端可见（未开始且有名额即可约），须先下架才能编辑／删除 */
    PUBLISHED(2, "已上架"),

    /** 3 已取消：用户端可见，终态（不可编辑／不可改状态／不可删除） */
    CANCELLED(3, "已取消");

    /** 管理端「已结束」的展示文案 */
    public static final String FINISHED_TEXT = "已结束";

    /** 非法状态码的兜底文案 */
    public static final String UNKNOWN_TEXT = "";

    /** 状态码：1 待上架／2 已上架／3 已取消 */
    private final int code;

    /** 管理端展示文案 */
    private final String adminText;

    ScheduleStatusEnum(int code, String adminText) {
        this.code = code;
        this.adminText = adminText;
    }

    /**
     * 按状态码取枚举
     *
     * @param code 状态码
     * @return 命中返回枚举，{@code null} 或越界返回 {@code null}
     */
    public static ScheduleStatusEnum of(Integer code) {
        if (code == null) {
            return null;
        }
        for (ScheduleStatusEnum status : values()) {
            if (status.code == code) {
                return status;
            }
        }
        return null;
    }

    /**
     * 状态码是否合法（{@code 1}~{@code 3}）；{@code null} 视为不合法
     */
    public static boolean isValid(Integer code) {
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
    public static boolean isFinished(Integer status, LocalDateTime endTime, LocalDateTime now) {
        if (status == null || endTime == null || now == null) {
            return false;
        }
        return status == PUBLISHED.code && !endTime.isAfter(now);
    }

    /**
     * 管理端展示文案：待上架／已上架／已取消／已结束
     */
    public static String adminTextOf(Integer status, LocalDateTime endTime, LocalDateTime now) {
        ScheduleStatusEnum matched = of(status);
        if (matched == null) {
            return UNKNOWN_TEXT;
        }
        if (isFinished(status, endTime, now)) {
            return FINISHED_TEXT;
        }
        return matched.adminText;
    }
}
