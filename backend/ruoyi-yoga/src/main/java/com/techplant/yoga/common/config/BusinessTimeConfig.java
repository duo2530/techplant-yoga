package com.techplant.yoga.common.config;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 业务时区与时钟：用户端排课窗口、「今天」与可约判定统一按华东时间计算。 */
@Configuration
public class BusinessTimeConfig {
    /** 业务时区 */
    public static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");

    /** 业务时钟；排课服务通过构造器注入，测试可替换为固定时钟 */
    @Bean
    public Clock businessClock() {
        return Clock.system(BUSINESS_ZONE);
    }
}
