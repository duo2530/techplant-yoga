package com.techplant.yoga.image.config;

import lombok.Getter;
import lombok.Setter;

import org.springframework.boot.context.properties.ConfigurationProperties;
import com.ruoyi.common.utils.StringUtils;

/** 业务图片所在的阿里云 OSS Bucket。 */
@Getter
@Setter
@ConfigurationProperties(prefix = "yoga.oss")
public class OssProperties {
    /** 后端访问 OSS 的地域节点；部署在同地域 VPC 内时用内网节点 */
    private String endpoint;

    private String bucket;

    /** 浏览器与小程序读取图片的公网地址前缀，与对象键拼成库里保存的图片 URL */
    private String publicBaseUrl;

    private String accessKeyId;

    private String accessKeySecret;

    /** 访问密钥齐全才能上传 */
    public boolean isConfigured() {
        return StringUtils.isNotBlank(accessKeyId) && StringUtils.isNotBlank(accessKeySecret);
    }
}
