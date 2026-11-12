package com.techplant.yoga.image.enums;

import lombok.Getter;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** 允许上传的图片格式：扩展名、文件头签名与对象存储的 Content-Type。 */
public enum ImageType {
    PNG("image/png", "png", new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A}, "png"),

    JPEG("image/jpeg", "jpg", new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF}, "jpg", "jpeg");

    /** 对象存储返回给浏览器的 Content-Type */
    @Getter
    private final String contentType;

    /** 对象键使用的扩展名 */
    @Getter
    private final String extension;

    private final byte[] signature;

    private final List<String> fileExtensions;

    ImageType(String contentType, String extension, byte[] signature, String... fileExtensions) {
        this.contentType = contentType;
        this.extension = extension;
        this.signature = signature;
        this.fileExtensions = Arrays.asList(fileExtensions);
    }

    /** 按文件名扩展名（不区分大小写）匹配格式 */
    public static Optional<ImageType> fromFileName(String fileName) {
        if (fileName == null || fileName.lastIndexOf('.') < 0) {
            return Optional.empty();
        }
        String ext = fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        return Arrays.stream(values()).filter(type -> type.fileExtensions.contains(ext)).findFirst();
    }

    /** 按文件头签名识别格式 */
    public static Optional<ImageType> detect(byte[] content) {
        return Arrays.stream(values()).filter(type -> type.matches(content)).findFirst();
    }

    private boolean matches(byte[] content) {
        if (content.length < signature.length) {
            return false;
        }
        for (int i = 0; i < signature.length; i++) {
            if (content[i] != signature[i]) {
                return false;
            }
        }
        return true;
    }
}
