package com.techplant.yoga.image.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** 注册 OSS 配置绑定。 */
@Configuration
@EnableConfigurationProperties(OssProperties.class)
public class OssConfig {
}
