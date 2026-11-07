package com.techplant.yoga.reservation.enums;

import lombok.Getter;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import com.techplant.yoga.schedule.enums.ScheduleStatusEnum;

/**
 * 预约状态枚举：1 已预约／2 已取消／3 已签到／4 排队中／5 排队失败。
 *
 * <p>状态码、允许的流转、占位口径与「排队失败」的展示推导都收在这一处。</p>
 */
@Getter
public enum ReservationStatusEnum {
    /** 已预约：占用名额并已扣次 */
    RESERVED(1, "已预约"),

    /** 已取消：已预约的取消释放名额并返次；排队中的取消不涉及名额与次数 */
    CANCELLED(2, "已取消"),

    /** 已签到：不重复扣次 */
    CHECKED_IN(3, "已签到"),

    /** 排队中：不占名额、不扣次，有空位时按排队顺序转为已预约 */
    QUEUED(4, "排队中"),

    /** 排队失败：轮到补位时会员卡已不可用，跳过该会员 */
    QUEUE_FAILED(5, "排队失败");

    /** 取消预约、加入排队与补位的截止：课程开始前 120 分钟 */
    public static final long CHANGE_DEADLINE_MINUTES = 120L;

    /** 占用排课名额的状态码 */
    public static final List<Integer> SEAT_CODES = Collections
            .unmodifiableList(Arrays.asList(RESERVED.code, CHECKED_IN.code));

    /** 同一会员在同一排课上至多一条的状态码：占位状态加排队中 */
    public static final List<Integer> OPEN_CODES = Collections
            .unmodifiableList(Arrays.asList(RESERVED.code, CHECKED_IN.code, QUEUED.code));

    /** 预约状态码 */
    private final Integer code;

    /** 预约状态文案 */
    private final String text;

    ReservationStatusEnum(Integer code, String text) {
        this.code = code;
        this.text = text;
    }

    /** 当前状态允许流转到的目标状态 */
    private Set<ReservationStatusEnum> targets() {
        switch (this) {
            case RESERVED:
                return EnumSet.of(CANCELLED, CHECKED_IN);
            case QUEUED:
                return EnumSet.of(RESERVED, CANCELLED, QUEUE_FAILED);
            default:
                return EnumSet.noneOf(ReservationStatusEnum.class);
        }
    }

    public boolean canTransitionTo(ReservationStatusEnum target) {
        return target != null && targets().contains(target);
    }

    /** 按码取枚举，非法或空返回 {@code null} */
    public static ReservationStatusEnum of(Integer code) {
        if (code == null) {
            return null;
        }
        for (ReservationStatusEnum item : values()) {
            if (item.code.equals(code)) {
                return item;
            }
        }
        return null;
    }

    /** 是否已过取消、排队与补位截止：开始时间缺失视为已截止 */
    public static boolean isPastChangeDeadline(LocalDateTime startTime, LocalDateTime now) {
        return startTime == null || now.isAfter(startTime.minusMinutes(CHANGE_DEADLINE_MINUTES));
    }

    /**
     * 排队是否仍可能补位：排课已上架且未过截止时间
     *
     * @param scheduleStatus 排课落库状态；排课已删除传 {@code null}
     */
    public static boolean isQueueOpen(Integer scheduleStatus, LocalDateTime startTime, LocalDateTime now) {
        return ScheduleStatusEnum.of(scheduleStatus) == ScheduleStatusEnum.PUBLISHED
                && !isPastChangeDeadline(startTime, now);
    }

    /**
     * 展示状态：排队中的记录在排课取消、删除或过了补位截止后显示为排队失败，不落库；非法码返回 {@code null}
     *
     * @param scheduleStatus 排课落库状态；排课已删除传 {@code null}
     */
    public static ReservationStatusEnum displayOf(Integer code, Integer scheduleStatus, LocalDateTime startTime,
            LocalDateTime now) {
        ReservationStatusEnum status = of(code);
        if (status == QUEUED && !isQueueOpen(scheduleStatus, startTime, now)) {
            return QUEUE_FAILED;
        }
        return status;
    }
}
