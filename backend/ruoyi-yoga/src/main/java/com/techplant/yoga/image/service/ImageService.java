package com.techplant.yoga.image.service;

import org.springframework.web.multipart.MultipartFile;

/** 业务图片上传服务。 */
public interface ImageService {
    /**
     * 校验并上传一张图片：非空、不超过 5MB、扩展名为 png/jpg/jpeg 且与文件头一致；
     * 不合法 → 400，存储未配置或上传失败 → 500
     *
     * @return 图片的公网绝对 URL
     */
    String upload(MultipartFile file);
}
