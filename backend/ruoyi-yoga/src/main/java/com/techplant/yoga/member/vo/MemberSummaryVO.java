package com.techplant.yoga.member.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

/**
 * 会员摘要（跨模块批量补齐显示名与手机号用）。
 *
 * <p>会员卡模块用它批量补 {@code memberName}，预约管理端列表用它批量补 {@code memberName}／{@code memberPhone}：
 * 会员已删除时该预约仍要返回，因此这里只给命中的会员，由调用方按缺失兜底；
 * 由 {@code MemberService#summaries(java.util.Collection)} 提供。</p>
 */
@ApiModel("会员摘要")
public class MemberSummaryVO
{
    @ApiModelProperty(value = "会员ID（雪花ID，字符串）", example = "1856739201475235902")
    private Long id;

    @ApiModelProperty(value = "会员显示名", example = "忻会员8000")
    private String name;

    @ApiModelProperty(value = "手机号", example = "13800138000")
    private String phone;

    public MemberSummaryVO()
    {
    }

    public MemberSummaryVO(Long id, String name)
    {
        this(id, name, null);
    }

    public MemberSummaryVO(Long id, String name, String phone)
    {
        this.id = id;
        this.name = name;
        this.phone = phone;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
}
