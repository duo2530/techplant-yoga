package com.techplant.yoga.reservation.vo;

import java.time.LocalDateTime;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

/**
 * 管理端预约列表项。
 *
 * <p>上课时间读取预约快照；课程、教练、门店、教室、会员与会员卡的展示字段读取当前关联资料，
 * 关联资料已物理删除时对应字段为 {@code null}。</p>
 */
@ApiModel("管理端预约列表项")
public class ReservationAdminItemVO
{
    @ApiModelProperty(value = "预约ID（雪花ID，字符串）", example = "1856739201475240001")
    private Long id;

    @ApiModelProperty(value = "排课ID（雪花ID，字符串）", example = "1856739201475239001")
    private Long scheduleId;

    @ApiModelProperty(value = "关联排课的课程ID")
    private Long courseId;

    @ApiModelProperty(value = "当前课程名称")
    private String courseName;

    @ApiModelProperty(value = "当前课程封面URL")
    private String courseCoverUrl;

    @ApiModelProperty(value = "排课课种：1团课 2精品课 3私教课 4特色课", example = "1")
    private Integer courseType;

    @ApiModelProperty(value = "课种名称", example = "团课")
    private String courseTypeName;

    @ApiModelProperty(value = "当前课程难度，1~5", example = "2")
    private Integer difficulty;

    @ApiModelProperty(value = "当前教练姓名")
    private String coachName;

    @ApiModelProperty(value = "排课门店ID")
    private Long storeId;

    @ApiModelProperty(value = "当前门店名称")
    private String storeName;

    @ApiModelProperty(value = "当前门店地址")
    private String storeAddress;

    @ApiModelProperty(value = "当前教室名称")
    private String classroomName;

    @ApiModelProperty(value = "上课日期，由预约开始时间生成", example = "2026-10-15")
    private String scheduleDate;

    @ApiModelProperty(value = "上课开始时间快照")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startTime;

    @ApiModelProperty(value = "上课结束时间快照")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endTime;

    @ApiModelProperty(value = "起止时间差的整分钟数", example = "60")
    private Integer durationMinutes;

    @ApiModelProperty(value = "原预约人数", example = "2")
    private Integer persons;

    @ApiModelProperty(value = "结算会员卡ID")
    private Long memberCardId;

    @ApiModelProperty(value = "当前会员卡名称")
    private String cardName;

    @ApiModelProperty(value = "关联会员卡类型：1次数卡 2年卡")
    private Integer cardType;

    @ApiModelProperty(value = "卡类型名称", example = "次数卡")
    private String cardTypeText;

    @ApiModelProperty(value = "原实际扣次；无扣次流水为 0", example = "2")
    private Integer deductTimes;

    @ApiModelProperty(value = "预约状态：1已预约 2已取消 3已签到", example = "1")
    private Integer status;

    @ApiModelProperty(value = "预约状态文案", example = "已预约")
    private String statusText;

    @ApiModelProperty(value = "排课状态：1待上架 2已上架 3已取消 4已结束")
    private Integer scheduleStatus;

    @ApiModelProperty(value = "排课状态文案")
    private String scheduleStatusText;

    @ApiModelProperty(value = "预约成功时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime reservationTime;

    @ApiModelProperty(value = "取消时间，未取消为空")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime cancelTime;

    @ApiModelProperty(value = "取消原因：1会员主动取消 2排课取消，未取消为空")
    private Integer cancelReason;

    @ApiModelProperty(value = "签到时间，未签到为空")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime checkInTime;

    @ApiModelProperty(value = "预约会员ID")
    private Long memberId;

    @ApiModelProperty(value = "当前会员姓名", example = "忻会员8000")
    private String memberName;

    @ApiModelProperty(value = "当前会员手机号", example = "13800138000")
    private String memberPhone;

    @ApiModelProperty(value = "当前会员卡卡号", example = "202610140001100000")
    private String cardNo;

    @ApiModelProperty(value = "排课教练ID")
    private Long coachId;

    @ApiModelProperty(value = "排课教室ID")
    private Long classroomId;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getScheduleId() { return scheduleId; }
    public void setScheduleId(Long scheduleId) { this.scheduleId = scheduleId; }
    public Long getCourseId() { return courseId; }
    public void setCourseId(Long courseId) { this.courseId = courseId; }
    public String getCourseName() { return courseName; }
    public void setCourseName(String courseName) { this.courseName = courseName; }
    public String getCourseCoverUrl() { return courseCoverUrl; }
    public void setCourseCoverUrl(String courseCoverUrl) { this.courseCoverUrl = courseCoverUrl; }
    public Integer getCourseType() { return courseType; }
    public void setCourseType(Integer courseType) { this.courseType = courseType; }
    public String getCourseTypeName() { return courseTypeName; }
    public void setCourseTypeName(String courseTypeName) { this.courseTypeName = courseTypeName; }
    public Integer getDifficulty() { return difficulty; }
    public void setDifficulty(Integer difficulty) { this.difficulty = difficulty; }
    public String getCoachName() { return coachName; }
    public void setCoachName(String coachName) { this.coachName = coachName; }
    public Long getStoreId() { return storeId; }
    public void setStoreId(Long storeId) { this.storeId = storeId; }
    public String getStoreName() { return storeName; }
    public void setStoreName(String storeName) { this.storeName = storeName; }
    public String getStoreAddress() { return storeAddress; }
    public void setStoreAddress(String storeAddress) { this.storeAddress = storeAddress; }
    public String getClassroomName() { return classroomName; }
    public void setClassroomName(String classroomName) { this.classroomName = classroomName; }
    public String getScheduleDate() { return scheduleDate; }
    public void setScheduleDate(String scheduleDate) { this.scheduleDate = scheduleDate; }
    public LocalDateTime getStartTime() { return startTime; }
    public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }
    public LocalDateTime getEndTime() { return endTime; }
    public void setEndTime(LocalDateTime endTime) { this.endTime = endTime; }
    public Integer getDurationMinutes() { return durationMinutes; }
    public void setDurationMinutes(Integer durationMinutes) { this.durationMinutes = durationMinutes; }
    public Integer getPersons() { return persons; }
    public void setPersons(Integer persons) { this.persons = persons; }
    public Long getMemberCardId() { return memberCardId; }
    public void setMemberCardId(Long memberCardId) { this.memberCardId = memberCardId; }
    public String getCardName() { return cardName; }
    public void setCardName(String cardName) { this.cardName = cardName; }
    public Integer getCardType() { return cardType; }
    public void setCardType(Integer cardType) { this.cardType = cardType; }
    public String getCardTypeText() { return cardTypeText; }
    public void setCardTypeText(String cardTypeText) { this.cardTypeText = cardTypeText; }
    public Integer getDeductTimes() { return deductTimes; }
    public void setDeductTimes(Integer deductTimes) { this.deductTimes = deductTimes; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public String getStatusText() { return statusText; }
    public void setStatusText(String statusText) { this.statusText = statusText; }
    public Integer getScheduleStatus() { return scheduleStatus; }
    public void setScheduleStatus(Integer scheduleStatus) { this.scheduleStatus = scheduleStatus; }
    public String getScheduleStatusText() { return scheduleStatusText; }
    public void setScheduleStatusText(String scheduleStatusText) { this.scheduleStatusText = scheduleStatusText; }
    public LocalDateTime getReservationTime() { return reservationTime; }
    public void setReservationTime(LocalDateTime reservationTime) { this.reservationTime = reservationTime; }
    public LocalDateTime getCancelTime() { return cancelTime; }
    public void setCancelTime(LocalDateTime cancelTime) { this.cancelTime = cancelTime; }
    public Integer getCancelReason() { return cancelReason; }
    public void setCancelReason(Integer cancelReason) { this.cancelReason = cancelReason; }
    public LocalDateTime getCheckInTime() { return checkInTime; }
    public void setCheckInTime(LocalDateTime checkInTime) { this.checkInTime = checkInTime; }
    public Long getMemberId() { return memberId; }
    public void setMemberId(Long memberId) { this.memberId = memberId; }
    public String getMemberName() { return memberName; }
    public void setMemberName(String memberName) { this.memberName = memberName; }
    public String getMemberPhone() { return memberPhone; }
    public void setMemberPhone(String memberPhone) { this.memberPhone = memberPhone; }
    public String getCardNo() { return cardNo; }
    public void setCardNo(String cardNo) { this.cardNo = cardNo; }
    public Long getCoachId() { return coachId; }
    public void setCoachId(Long coachId) { this.coachId = coachId; }
    public Long getClassroomId() { return classroomId; }
    public void setClassroomId(Long classroomId) { this.classroomId = classroomId; }
}
