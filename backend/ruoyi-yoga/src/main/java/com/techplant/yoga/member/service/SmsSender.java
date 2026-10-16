package com.techplant.yoga.member.service;

/** 短信发送出口。 */
public interface SmsSender {
    /**
     * 发送一条验证码短信
     *
     * @param phone 手机号
     * @param code  6 位验证码
     * @param scene 场景 code（register／reset）
     */
    void send(String phone, String code, String scene);
}
