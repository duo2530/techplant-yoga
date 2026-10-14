package com.techplant.yoga.member.query;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

/**
 * 会员列表查询条件。
 *
 * <p>筛选：手机号后四位精确、显示名模糊、会员等级精确（取值见字典 {@code member_level}），
 * 结果按会员 ID 降序；{@code pageNum} 与 {@code pageSize} 必填，每页上限 100。</p>
 */
@ApiModel("会员列表查询条件")
public class MemberQuery
{
    /** 每页条数上限 */
    public static final int MAX_PAGE_SIZE = 100;

    @ApiModelProperty("手机号后 4 位，精确匹配")
    @Pattern(regexp = "^\\d{4}$", message = "手机号后4位必须是 4 位数字")
    private String phoneLast4;

    @ApiModelProperty("会员显示名，模糊匹配，最长 64")
    @Size(max = 64, message = "会员显示名长度不能超过 64")
    private String name;

    @ApiModelProperty(value = "会员等级，精确匹配；取值见字典 member_level", example = "0")
    private Integer level;

    @ApiModelProperty(value = "页码，从 1 开始", required = true, example = "1")
    @NotNull(message = "页码不能为空")
    @Min(value = 1, message = "页码不能小于 1")
    private Integer pageNum;

    @ApiModelProperty(value = "每页条数，1~100", required = true, example = "10")
    @NotNull(message = "每页条数不能为空")
    @Min(value = 1, message = "每页条数不能小于 1")
    @Max(value = MAX_PAGE_SIZE, message = "每页条数不能超过 100")
    private Integer pageSize;

    public String getPhoneLast4()
    {
        return phoneLast4;
    }

    public void setPhoneLast4(String phoneLast4)
    {
        this.phoneLast4 = phoneLast4;
    }

    /**
     * 绑定接口约定的下划线参数名 {@code phone_last4}；驼峰 {@code phoneLast4} 同样可用
     */
    public void setPhone_last4(String phoneLast4)
    {
        this.phoneLast4 = phoneLast4;
    }

    public String getName()
    {
        return name;
    }

    public void setName(String name)
    {
        this.name = name;
    }

    public Integer getLevel()
    {
        return level;
    }

    public void setLevel(Integer level)
    {
        this.level = level;
    }

    public Integer getPageNum()
    {
        return pageNum;
    }

    public void setPageNum(Integer pageNum)
    {
        this.pageNum = pageNum;
    }

    public Integer getPageSize()
    {
        return pageSize;
    }

    public void setPageSize(Integer pageSize)
    {
        this.pageSize = pageSize;
    }
}
