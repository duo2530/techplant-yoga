package com.techplant.yoga.membercard.query;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

/**
 * 会员卡列表查询条件。
 *
 * <p>筛选：{@code memberId}（精确）、{@code cardNo}（模糊）、{@code cardType}（1／2）、
 * {@code storeId}（适用门店，精确）；分页 {@code pageNum}／{@code pageSize} 必填，每页上限 100。
 * 排序由 DAO 固定为 {@code id DESC}，最新发卡在前。</p>
 */
@ApiModel("会员卡列表查询条件")
public class MemberCardQuery
{
    /** 每页条数上限 */
    public static final int MAX_PAGE_SIZE = 100;

    @ApiModelProperty("所属会员 ID")
    private Long memberId;

    @ApiModelProperty("卡号，模糊匹配，最长 64")
    @Size(max = 64, message = "卡号长度不能超过 64")
    private String cardNo;

    @ApiModelProperty(value = "卡类型：1次数卡 2年卡", example = "1")
    @Min(value = 1, message = "卡类型取值为 1 或 2")
    @Max(value = 2, message = "卡类型取值为 1 或 2")
    private Integer cardType;

    @ApiModelProperty("适用门店 ID")
    private Long storeId;

    @ApiModelProperty(value = "页码，从 1 开始", required = true, example = "1")
    @NotNull(message = "页码不能为空")
    @Min(value = 1, message = "页码不能小于 1")
    private Integer pageNum;

    @ApiModelProperty(value = "每页条数，1~100", required = true, example = "10")
    @NotNull(message = "每页条数不能为空")
    @Min(value = 1, message = "每页条数不能小于 1")
    @Max(value = MAX_PAGE_SIZE, message = "每页条数不能超过 100")
    private Integer pageSize;

    public Long getMemberId() { return memberId; }
    public void setMemberId(Long memberId) { this.memberId = memberId; }
    public String getCardNo() { return cardNo; }
    public void setCardNo(String cardNo) { this.cardNo = cardNo; }
    public Integer getCardType() { return cardType; }
    public void setCardType(Integer cardType) { this.cardType = cardType; }
    public Long getStoreId() { return storeId; }
    public void setStoreId(Long storeId) { this.storeId = storeId; }
    public Integer getPageNum() { return pageNum; }
    public void setPageNum(Integer pageNum) { this.pageNum = pageNum; }
    public Integer getPageSize() { return pageSize; }
    public void setPageSize(Integer pageSize) { this.pageSize = pageSize; }
}
