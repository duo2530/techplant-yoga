package com.techplant.yoga.membercard.vo;

import java.time.LocalDateTime;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

/** 预约事务内锁定的会员卡快照，不作为 HTTP 接口返回对象。 */
@ApiModel("会员卡预约快照")
public class MemberCardBookingSnapshot
{
    @ApiModelProperty("会员卡ID")
    private Long id;

    @ApiModelProperty("所属会员ID")
    private Long memberId;

    @ApiModelProperty("卡名称")
    private String cardName;

    @ApiModelProperty("卡类型：1次数卡 2年卡")
    private Integer cardType;

    @ApiModelProperty("适用课种范围：1团课类 2私教课")
    private Integer courseScope;

    @ApiModelProperty("适用门店ID（年卡为空）")
    private Long storeId;

    @ApiModelProperty("有效期起")
    private LocalDateTime validStartTime;

    @ApiModelProperty("有效期止")
    private LocalDateTime validEndTime;

    @ApiModelProperty("开卡时间")
    private LocalDateTime activateTime;

    @ApiModelProperty("停卡时间")
    private LocalDateTime stopTime;

    @ApiModelProperty("剩余次数（仅次数卡）")
    private Integer remainingTimes;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getMemberId() { return memberId; }
    public void setMemberId(Long memberId) { this.memberId = memberId; }
    public String getCardName() { return cardName; }
    public void setCardName(String cardName) { this.cardName = cardName; }
    public Integer getCardType() { return cardType; }
    public void setCardType(Integer cardType) { this.cardType = cardType; }
    public Integer getCourseScope() { return courseScope; }
    public void setCourseScope(Integer courseScope) { this.courseScope = courseScope; }
    public Long getStoreId() { return storeId; }
    public void setStoreId(Long storeId) { this.storeId = storeId; }
    public LocalDateTime getValidStartTime() { return validStartTime; }
    public void setValidStartTime(LocalDateTime validStartTime) { this.validStartTime = validStartTime; }
    public LocalDateTime getValidEndTime() { return validEndTime; }
    public void setValidEndTime(LocalDateTime validEndTime) { this.validEndTime = validEndTime; }
    public LocalDateTime getActivateTime() { return activateTime; }
    public void setActivateTime(LocalDateTime activateTime) { this.activateTime = activateTime; }
    public LocalDateTime getStopTime() { return stopTime; }
    public void setStopTime(LocalDateTime stopTime) { this.stopTime = stopTime; }
    public Integer getRemainingTimes() { return remainingTimes; }
    public void setRemainingTimes(Integer remainingTimes) { this.remainingTimes = remainingTimes; }
}
