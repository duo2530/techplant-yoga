package com.techplant.yoga.membercard.vo;

import java.time.LocalDateTime;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

/**
 * 管理端会员卡列表项。
 *
 * <p>{@code cardType}/{@code cardTypeText}、{@code courseScope}/{@code courseScopeName} 成对返回；
 * 次数类字段只有次数卡有值（年卡为 {@code null}），年卡的 {@code storeId} 与 {@code storeName} 同为 {@code null}。</p>
 */
@ApiModel("会员卡列表项")
public class MemberCardListItemVO
{
    @ApiModelProperty(value = "会员卡ID（雪花ID，字符串）", example = "1856739201475235911")
    private Long id;

    @ApiModelProperty(value = "卡号（18 位：发卡日期 + 门店编号 + 发卡时间）", example = "202610030001100000")
    private String cardNo;

    @ApiModelProperty(value = "开卡门店ID（门店已删除时仍保留）", example = "1856739201475235901")
    private Long openStoreId;

    @ApiModelProperty(value = "开卡门店编号（门店已删除时为空字符串）", example = "0001")
    private String openStoreNo;

    @ApiModelProperty(value = "开卡门店名称（门店已删除时为空字符串）", example = "徐汇店")
    private String openStoreName;

    @ApiModelProperty(value = "卡名称", example = "普拉提次卡")
    private String cardName;

    @ApiModelProperty(value = "卡类型：1次数卡 2年卡", example = "1")
    private Integer cardType;

    @ApiModelProperty(value = "卡类型名称", example = "次数卡")
    private String cardTypeText;

    @ApiModelProperty(value = "适用课种范围：1团课类 2私教课", example = "1")
    private Integer courseScope;

    @ApiModelProperty(value = "适用课种范围名称", example = "团课类")
    private String courseScopeName;

    @ApiModelProperty(value = "适用门店ID（年卡为 null；门店已删除时仍保留）", example = "1856739201475235901")
    private Long storeId;

    @ApiModelProperty(value = "适用门店名称（年卡为 null；门店已删除时为空字符串）", example = "徐汇店")
    private String storeName;

    @ApiModelProperty(value = "所属会员ID", example = "1856739201475235902")
    private Long memberId;

    @ApiModelProperty(value = "所属会员显示名（会员已删除时为空字符串）", example = "忻会员8000")
    private String memberName;

    @ApiModelProperty(value = "购买次数（仅次数卡）", example = "10")
    private Integer purchaseTimes;

    @ApiModelProperty(value = "赠送次数（仅次数卡）", example = "2")
    private Integer giftTimes;

    @ApiModelProperty(value = "剩余次数（仅次数卡）", example = "12")
    private Integer remainingTimes;

    @ApiModelProperty(value = "总次数 = 购买 + 赠送（仅次数卡）", example = "12")
    private Integer totalTimes;

    @ApiModelProperty("有效期起（未开卡为 null）")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime validStartTime;

    @ApiModelProperty("有效期止（未开卡为 null）")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime validEndTime;

    @ApiModelProperty(value = "剩余天数（含今天，仅年卡未过期时有值）", example = "30")
    private Integer remainingDays;

    @ApiModelProperty("开卡时间（未开卡为 null）")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime activateTime;

    @ApiModelProperty("停卡时间（未停卡为 null）")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime stopTime;

    @ApiModelProperty("停卡原因（未停卡为 null）")
    private String stopReason;

    @ApiModelProperty("发卡时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getCardNo() { return cardNo; }
    public void setCardNo(String cardNo) { this.cardNo = cardNo; }
    public Long getOpenStoreId() { return openStoreId; }
    public void setOpenStoreId(Long openStoreId) { this.openStoreId = openStoreId; }
    public String getOpenStoreNo() { return openStoreNo; }
    public void setOpenStoreNo(String openStoreNo) { this.openStoreNo = openStoreNo; }
    public String getOpenStoreName() { return openStoreName; }
    public void setOpenStoreName(String openStoreName) { this.openStoreName = openStoreName; }
    public String getCardName() { return cardName; }
    public void setCardName(String cardName) { this.cardName = cardName; }
    public Integer getCardType() { return cardType; }
    public void setCardType(Integer cardType) { this.cardType = cardType; }
    public String getCardTypeText() { return cardTypeText; }
    public void setCardTypeText(String cardTypeText) { this.cardTypeText = cardTypeText; }
    public Integer getCourseScope() { return courseScope; }
    public void setCourseScope(Integer courseScope) { this.courseScope = courseScope; }
    public String getCourseScopeName() { return courseScopeName; }
    public void setCourseScopeName(String courseScopeName) { this.courseScopeName = courseScopeName; }
    public Long getStoreId() { return storeId; }
    public void setStoreId(Long storeId) { this.storeId = storeId; }
    public String getStoreName() { return storeName; }
    public void setStoreName(String storeName) { this.storeName = storeName; }
    public Long getMemberId() { return memberId; }
    public void setMemberId(Long memberId) { this.memberId = memberId; }
    public String getMemberName() { return memberName; }
    public void setMemberName(String memberName) { this.memberName = memberName; }
    public Integer getPurchaseTimes() { return purchaseTimes; }
    public void setPurchaseTimes(Integer purchaseTimes) { this.purchaseTimes = purchaseTimes; }
    public Integer getGiftTimes() { return giftTimes; }
    public void setGiftTimes(Integer giftTimes) { this.giftTimes = giftTimes; }
    public Integer getRemainingTimes() { return remainingTimes; }
    public void setRemainingTimes(Integer remainingTimes) { this.remainingTimes = remainingTimes; }
    public Integer getTotalTimes() { return totalTimes; }
    public void setTotalTimes(Integer totalTimes) { this.totalTimes = totalTimes; }
    public LocalDateTime getValidStartTime() { return validStartTime; }
    public void setValidStartTime(LocalDateTime validStartTime) { this.validStartTime = validStartTime; }
    public LocalDateTime getValidEndTime() { return validEndTime; }
    public void setValidEndTime(LocalDateTime validEndTime) { this.validEndTime = validEndTime; }
    public Integer getRemainingDays() { return remainingDays; }
    public void setRemainingDays(Integer remainingDays) { this.remainingDays = remainingDays; }
    public LocalDateTime getActivateTime() { return activateTime; }
    public void setActivateTime(LocalDateTime activateTime) { this.activateTime = activateTime; }
    public LocalDateTime getStopTime() { return stopTime; }
    public void setStopTime(LocalDateTime stopTime) { this.stopTime = stopTime; }
    public String getStopReason() { return stopReason; }
    public void setStopReason(String stopReason) { this.stopReason = stopReason; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
}
