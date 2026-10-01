package com.techplant.yoga.coach.dto;

import java.util.List;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

/**
 * 新增教练请求（教练管理详细设计 §2.2.3、§2.3.2）。
 *
 * <p>姓名字段必填；<b>姓名允许重名</b>，服务端不做唯一校验（§2.2.3）。
 * 相册元素上限（≤ 5）由 service 统一校验并返回 <b>409</b>，因此此处<b>不加</b>
 * {@code @Size(max = 5)}（{@code @Valid} 失败会走框架的 500 分支，与设计口径不符）。</p>
 */
@ApiModel("新增教练请求")
public class CoachCreateDTO
{
    @ApiModelProperty(value = "姓名", required = true, example = "林教练")
    @NotBlank(message = "教练姓名不能为空")
    @Size(max = 32, message = "教练姓名长度不能超过 32")
    private String name;

    @ApiModelProperty(value = "头像URL")
    @Size(max = 255, message = "教练头像URL长度不能超过 255")
    private String avatarUrl;

    @ApiModelProperty(value = "简介")
    @Size(max = 512, message = "教练简介长度不能超过 512")
    private String intro;

    @ApiModelProperty(value = "联系电话")
    @Size(max = 32, message = "教练联系电话长度不能超过 32")
    private String phone;

    /** 相册：URL 数组，元素个数由 service 校验（> 5 → 409「相册最多 5 张」） */
    @ApiModelProperty(value = "相册，最多 5 张")
    private List<String> gallery;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getAvatarUrl() { return avatarUrl; }
    public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }
    public String getIntro() { return intro; }
    public void setIntro(String intro) { this.intro = intro; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public List<String> getGallery() { return gallery; }
    public void setGallery(List<String> gallery) { this.gallery = gallery; }
}
