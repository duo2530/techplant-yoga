package com.techplant.yoga.member.service.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import com.techplant.yoga.member.service.SmsSender;

/**
 * 短信发送的日志实现（用户端接口详细设计 §3.2、§4.3 第 4 步）。
 *
 * <p><b>本阶段不接真实短信通道</b>：验证码只写日志，便于本机联调与验收；
 * 真机/生产前必须换成真实通道实现（微信小程序订阅消息或第三方短信服务），
 * 否则用户永远收不到码。</p>
 *
 * <p>日志级别用 info，且只打手机号与场景，不打任何用户资料；验证码进日志是<b>开发期行为</b>，
 * 上线前应连同本实现一起替换。</p>
 */
@Service
public class LogSmsSender implements SmsSender
{
    private static final Logger log = LoggerFactory.getLogger(LogSmsSender.class);

    @Override
    public void send(String phone, String code, String scene)
    {
        log.info("【开发短信通道】场景={} 手机号={} 验证码={}（未接入真实通道，验证码仅写日志）",
                scene, phone, code);
    }
}
