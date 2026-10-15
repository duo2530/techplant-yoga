package com.techplant.yoga.membercard.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

/**
 * 会员卡摘要（跨模块批量补齐卡号、卡名与卡类型用）。
 *
 * <p>预约管理端列表用它把预约的结算卡翻译成展示字段：会员卡已被删除时该预约仍要返回，
 * 因此这里只给命中的卡，由调用方按缺失兜底；由 {@code MemberCardService#summaries(java.util.Collection)} 提供。</p>
 */
@ApiModel("会员卡摘要")
public class MemberCardSummaryVO
{
    @ApiModelProperty(value = "会员卡ID（雪花ID，字符串）", example = "1856739201475235912")
    private Long id;

    @ApiModelProperty(value = "卡号", example = "202610140001100000")
    private String cardNo;

    @ApiModelProperty(value = "卡名称", example = "普拉提次卡")
    private String cardName;

    @ApiModelProperty(value = "卡类型：1次数卡 2年卡", example = "1")
    private Integer cardType;

    public MemberCardSummaryVO()
    {
    }

    public MemberCardSummaryVO(Long id, String cardNo, String cardName, Integer cardType)
    {
        this.id = id;
        this.cardNo = cardNo;
        this.cardName = cardName;
        this.cardType = cardType;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getCardNo() { return cardNo; }
    public void setCardNo(String cardNo) { this.cardNo = cardNo; }
    public String getCardName() { return cardName; }
    public void setCardName(String cardName) { this.cardName = cardName; }
    public Integer getCardType() { return cardType; }
    public void setCardType(Integer cardType) { this.cardType = cardType; }
}
