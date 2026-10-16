package com.techplant.yoga.member.enums;

import lombok.Getter;

/**
 * 短信验证码场景。
 *
 * <p>{@code register} 只允许<b>未注册</b>手机号，{@code reset} 只允许<b>已注册</b>手机号；
 * 两者的验证码在 Redis 里分场景存放，互不串用。</p>
 */
@Getter
public enum SmsSceneEnum {
    /** 注册 */
    REGISTER("register", "注册"),

    /** 忘记密码 */
    RESET("reset", "忘记密码");

    private final String code;

    private final String label;

    SmsSceneEnum(String code, String label) {
        this.code = code;
        this.label = label;
    }

    /** 场景取值是否合法 */
    public static boolean isValid(String code) {
        return fromCode(code) != null;
    }

    /** 按 code 取枚举，非法取值返回 null */
    public static SmsSceneEnum fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (SmsSceneEnum scene : values()) {
            if (scene.code.equals(code.trim())) {
                return scene;
            }
        }
        return null;
    }

    /** 场景取值清单（校验提示语用） */
    public static String codes() {
        StringBuilder builder = new StringBuilder();
        for (SmsSceneEnum scene : values()) {
            if (builder.length() > 0) {
                builder.append("／");
            }
            builder.append(scene.code);
        }
        return builder.toString();
    }
}
