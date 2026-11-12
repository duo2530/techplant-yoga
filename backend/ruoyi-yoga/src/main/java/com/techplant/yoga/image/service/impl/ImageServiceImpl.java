package com.techplant.yoga.image.service.impl;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import com.aliyun.oss.ClientException;
import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.OSSException;
import com.aliyun.oss.model.ObjectMetadata;
import com.ruoyi.common.constant.HttpStatus;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.uuid.IdUtils;
import com.techplant.yoga.image.config.OssProperties;
import com.techplant.yoga.image.enums.ImageType;
import com.techplant.yoga.image.service.ImageService;

/** 把业务图片存到阿里云 OSS，对象键为 {@code images/yyyy/MM/dd/{uuid}.{ext}}。 */
@Service
public class ImageServiceImpl implements ImageService, DisposableBean {
    private static final Logger log = LoggerFactory.getLogger(ImageServiceImpl.class);

    private static final long MAX_SIZE = 5L * 1024 * 1024;

    private static final DateTimeFormatter DATE_PATH = DateTimeFormatter.ofPattern("yyyy/MM/dd");

    private final OssProperties properties;

    /** 未配置访问密钥时为 null，此时上传返回 500 */
    private final OSS client;

    public ImageServiceImpl(OssProperties properties) {
        this.properties = properties;
        this.client = properties.isConfigured()
                ? new OSSClientBuilder().build(properties.getEndpoint(), properties.getAccessKeyId(),
                        properties.getAccessKeySecret())
                : null;
    }

    @Override
    public String upload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ServiceException("请选择要上传的图片", HttpStatus.BAD_REQUEST);
        }
        if (file.getSize() > MAX_SIZE) {
            throw new ServiceException("图片大小不能超过 5MB", HttpStatus.BAD_REQUEST);
        }
        ImageType declared = ImageType.fromFileName(file.getOriginalFilename())
                .orElseThrow(() -> badRequest("仅支持 png、jpg、jpeg 格式的图片"));
        byte[] content = readContent(file);
        ImageType actual = ImageType.detect(content).orElseThrow(() -> badRequest("文件内容不是有效的 png 或 jpg 图片"));
        if (actual != declared) {
            throw new ServiceException("图片扩展名与文件内容不一致", HttpStatus.BAD_REQUEST);
        }
        if (client == null) {
            throw new ServiceException("图片存储未配置，暂时无法上传", HttpStatus.ERROR);
        }
        String key = "images/" + LocalDate.now().format(DATE_PATH) + "/" + IdUtils.fastSimpleUUID() + "."
                + actual.getExtension();
        put(key, content, actual);
        return trimTrailingSlash(properties.getPublicBaseUrl()) + "/" + key;
    }

    @Override
    public void destroy() {
        if (client != null) {
            client.shutdown();
        }
    }

    private byte[] readContent(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new ServiceException("读取上传文件失败", HttpStatus.BAD_REQUEST);
        }
    }

    private void put(String key, byte[] content, ImageType type) {
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentType(type.getContentType());
        metadata.setContentLength(content.length);
        try {
            client.putObject(properties.getBucket(), key, new ByteArrayInputStream(content), metadata);
        } catch (OSSException | ClientException e) {
            log.error("上传图片到 OSS 失败 bucket={} key={}", properties.getBucket(), key, e);
            throw new ServiceException("图片上传失败，请稍后重试", HttpStatus.ERROR);
        }
    }

    private static ServiceException badRequest(String message) {
        return new ServiceException(message, HttpStatus.BAD_REQUEST);
    }

    private static String trimTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
