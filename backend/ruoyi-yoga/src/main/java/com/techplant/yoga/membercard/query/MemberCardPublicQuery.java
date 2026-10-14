package com.techplant.yoga.membercard.query;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

/**
 * 我的会员卡查询条件。
 *
 * <p>只承载分页：会员身份由登录态决定，门店过滤由「查看全部门店」的产品口径决定，
 * 两者都不接受客户端传入。排序由 DAO 固定为 {@code id DESC}。</p>
 */
@ApiModel("我的会员卡查询条件")
public class MemberCardPublicQuery
{
    /** 每页条数上限 */
    public static final int MAX_PAGE_SIZE = 100;

    @ApiModelProperty(value = "页码，从 1 开始", required = true, example = "1")
    @NotNull(message = "页码不能为空")
    @Min(value = 1, message = "页码不能小于 1")
    private Integer pageNum;

    @ApiModelProperty(value = "每页条数，1~100", required = true, example = "10")
    @NotNull(message = "每页条数不能为空")
    @Min(value = 1, message = "每页条数不能小于 1")
    @Max(value = MAX_PAGE_SIZE, message = "每页条数不能超过 100")
    private Integer pageSize;

    public Integer getPageNum() { return pageNum; }
    public void setPageNum(Integer pageNum) { this.pageNum = pageNum; }
    public Integer getPageSize() { return pageSize; }
    public void setPageSize(Integer pageSize) { this.pageSize = pageSize; }
}
