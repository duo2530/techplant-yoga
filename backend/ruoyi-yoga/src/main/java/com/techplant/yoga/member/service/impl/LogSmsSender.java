package com.techplant.yoga.member.service.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import com.techplant.yoga.member.service.SmsSender;

/** 将短信验证码写入日志的发送实现。 */
@Service
public class LogSmsSender implements SmsSender {
    private static final Logger log = LoggerFactory.getLogger(LogSmsSender.class);

    @Override
    public void send(String phone, String code, String scene) {
        log.info("【开发短信通道】场景={} 手机号={} 验证码={}（未接入真实通道，验证码仅写日志）", scene, phone, code);
    }
}
