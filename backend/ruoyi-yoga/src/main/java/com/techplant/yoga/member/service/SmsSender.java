package com.techplant.yoga.member.service;

/**
 * 短信发送出口（用户端接口详细设计 §3.2、§4.3）。
 *
 * <p>本阶段不接真实短信通道，实现是 {@link com.techplant.yoga.member.service.impl.LogSmsSender}
 * （打日志）；将来接通道时<b>只换实现类</b>，{@link SmsCodeService} 与接口层不用动。</p>
 */
public interface SmsSender
{
    /**
     * 发送一条验证码短信
     *
     * @param phone 手机号
     * @param code  6 位验证码
     * @param scene 场景 code（register／reset）
     */
    void send(String phone, String code, String scene);
}
