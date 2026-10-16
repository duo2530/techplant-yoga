package com.techplant.yoga.membercard.enums;

import lombok.Getter;

/**
 * 会员卡在用户端的展示状态。
 *
 * <p>不落库：由开卡时间、停卡时间、有效期止与剩余次数实时判定，判定优先级为
 * 停卡 → 未开卡 → 过期 → 已用尽 → 正常，与管理端派生状态的口径一致。</p>
 */
@Getter
public enum MemberCardPublicStatusEnum {
    /** 尚未开卡：有效期起止与开卡时间都为空 */
    PENDING(1, "未开卡"),

    /** 正常：已开卡、未过期且次数卡仍有剩余次数 */
    ACTIVE(2, "正常"),

    /** 过期：有效期止早于当前时间 */
    EXPIRED(3, "过期"),

    /** 停卡：已写入停卡时间，剩余次数与有效期保留 */
    STOPPED(4, "停卡"),

    /** 已用尽：次数卡剩余次数为 0 */
    EXHAUSTED(5, "已用尽");

    /** 状态码 */
    private final Integer code;

    /** 状态文案 */
    private final String text;

    MemberCardPublicStatusEnum(Integer code, String text) {
        this.code = code;
        this.text = text;
    }
}
