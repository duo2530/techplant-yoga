package com.techplant.yoga.image.controller;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import com.ruoyi.common.core.domain.AjaxResult;
import com.techplant.yoga.image.service.ImageService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;

/** 管理端业务图片上传接口：门店图片、教练头像与相册、课程封面共用。 */
@Api(tags = "管理端-图片上传")
@RestController
@RequestMapping("/admin/images")
@RequiredArgsConstructor
public class ImageController {
    private final ImageService imageService;

    /** 上传图片；{@code url} 与 {@code fileName} 都是图片的公网绝对 URL */
    @ApiOperation("上传图片")
    @PostMapping
    public AjaxResult upload(@RequestParam(value = "file", required = false) MultipartFile file) {
        String url = imageService.upload(file);
        AjaxResult result = AjaxResult.success();
        result.put("url", url);
        result.put("fileName", url);
        return result;
    }
}
