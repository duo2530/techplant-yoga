package com.techplant.yoga.reservation.convert;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import com.techplant.yoga.course.enums.CourseTypeEnum;
import com.techplant.yoga.course.vo.CourseSummaryVO;
import com.techplant.yoga.member.vo.MemberSummaryVO;
import com.techplant.yoga.membercard.enums.MemberCardTypeEnum;
import com.techplant.yoga.membercard.vo.MemberCardSummaryVO;
import com.techplant.yoga.reservation.domain.ReservationDO;
import com.techplant.yoga.reservation.dto.ReservationCreateDTO;
import com.techplant.yoga.reservation.enums.ReservationStatusEnum;
import com.techplant.yoga.reservation.vo.ReservationAdminItemVO;
import com.techplant.yoga.reservation.vo.ReservationPublicItemVO;
import com.techplant.yoga.schedule.enums.ScheduleStatusEnum;
import com.techplant.yoga.schedule.vo.ScheduleBookingSnapshot;
import com.techplant.yoga.store.vo.StoreSummaryVO;

/**
 * 预约对象转换。
 *
 * <p>转换是纯函数：课程、教练、门店、教室、会员与会员卡资料由 service 批量查好后以入参传入；
 * 关联资料已删除时传 {@code null}，对应字段按契约返回 {@code null}。</p>
 */
public final class ReservationConverter
{
    private ReservationConverter()
    {
    }

    /** 预约请求 → DO：上课时间取排课快照，状态固定为已预约 */
    public static ReservationDO toDO(ReservationCreateDTO dto, Long memberId, ScheduleBookingSnapshot schedule,
            LocalDateTime reservationTime)
    {
        ReservationDO reservation = new ReservationDO();
        reservation.setRequestId(dto.getRequestId().trim());
        reservation.setMemberId(memberId);
        reservation.setScheduleId(schedule.getId());
        reservation.setStartTime(schedule.getStartTime());
        reservation.setEndTime(schedule.getEndTime());
        reservation.setPersons(dto.getPersons());
        reservation.setMemberCardId(dto.getMemberCardId());
        reservation.setStatus(ReservationStatusEnum.RESERVED.getCode());
        reservation.setReservationTime(reservationTime);
        return reservation;
    }

    /** 单行 → 我的预约列表项；上课日期由预约的开始时间快照生成，课种取排课快照 */
    public static ReservationPublicItemVO toPublicItemVO(ReservationDO reservation, Long courseId, Integer courseType,
            String courseName, String courseCoverUrl, String coachName, String storeAddress)
    {
        ReservationPublicItemVO vo = new ReservationPublicItemVO();
        vo.setId(reservation.getId());
        vo.setScheduleId(reservation.getScheduleId());
        vo.setCourseId(courseId);
        vo.setCourseName(courseName);
        vo.setCourseCoverUrl(courseCoverUrl);
        vo.setCourseType(courseType);
        vo.setCourseTypeName(CourseTypeEnum.nameOf(courseType));
        vo.setCoachName(coachName);
        vo.setStoreAddress(storeAddress);
        vo.setScheduleDate(scheduleDateOf(reservation.getStartTime()));
        vo.setStartTime(reservation.getStartTime());
        vo.setEndTime(reservation.getEndTime());
        vo.setStatus(reservation.getStatus());
        vo.setStatusText(ReservationStatusEnum.textOf(reservation.getStatus()));
        return vo;
    }

    /**
     * 单行 → 管理端列表项
     *
     * <p>上课时间、人数与事件时间取预约自身的快照；排课与关联资料只用于展示字段，
     * 排课已被物理删除时排课派生字段留空，关联资料（课程、教练、门店、教室、会员、会员卡）缺失时对应字段为 {@code null}。</p>
     *
     * @param now 排课状态文案的判定基准时间（同一请求只用同一个 now）
     */
    public static ReservationAdminItemVO toAdminItemVO(ReservationDO reservation, ScheduleBookingSnapshot schedule,
            CourseSummaryVO course, String coachName, StoreSummaryVO store, String classroomName,
            MemberSummaryVO member, MemberCardSummaryVO card, Integer deductTimes, LocalDateTime now)
    {
        ReservationAdminItemVO vo = new ReservationAdminItemVO();
        vo.setId(reservation.getId());
        vo.setScheduleId(reservation.getScheduleId());
        vo.setStartTime(reservation.getStartTime());
        vo.setEndTime(reservation.getEndTime());
        vo.setScheduleDate(scheduleDateOf(reservation.getStartTime()));
        vo.setDurationMinutes(durationMinutes(reservation));
        vo.setPersons(reservation.getPersons());
        vo.setMemberCardId(reservation.getMemberCardId());
        vo.setStatus(reservation.getStatus());
        vo.setStatusText(ReservationStatusEnum.textOf(reservation.getStatus()));
        vo.setReservationTime(reservation.getReservationTime());
        vo.setCancelTime(reservation.getCancelTime());
        vo.setCancelReason(reservation.getCancelReason());
        vo.setCheckInTime(reservation.getCheckInTime());
        vo.setMemberId(reservation.getMemberId());
        vo.setDeductTimes(deductTimes == null ? 0 : deductTimes);

        if (schedule != null)
        {
            vo.setCourseId(schedule.getCourseId());
            vo.setCourseType(schedule.getCourseType());
            vo.setCourseTypeName(CourseTypeEnum.nameOf(schedule.getCourseType()));
            vo.setStoreId(schedule.getStoreId());
            vo.setCoachId(schedule.getCoachId());
            vo.setClassroomId(schedule.getClassroomId());
            vo.setScheduleStatus(schedule.getStatus());
            vo.setScheduleStatusText(ScheduleStatusEnum.adminTextOf(schedule.getStatus(), schedule.getEndTime(), now));
        }
        if (course != null)
        {
            vo.setCourseName(course.getName());
            vo.setCourseCoverUrl(course.getCoverUrl());
            vo.setDifficulty(course.getDifficulty());
        }
        vo.setCoachName(coachName);
        vo.setClassroomName(classroomName);
        if (store != null)
        {
            vo.setStoreName(store.getName());
            vo.setStoreAddress(store.getAddress());
        }
        if (member != null)
        {
            vo.setMemberName(member.getName());
            vo.setMemberPhone(member.getPhone());
        }
        if (card != null)
        {
            vo.setCardNo(card.getCardNo());
            vo.setCardName(card.getCardName());
            vo.setCardType(card.getCardType());
            vo.setCardTypeText(MemberCardTypeEnum.nameOf(card.getCardType()));
        }
        return vo;
    }

    /** 起止时间差的整分钟数；快照缺失时返回 {@code null} */
    private static Integer durationMinutes(ReservationDO reservation)
    {
        if (reservation.getStartTime() == null || reservation.getEndTime() == null)
        {
            return null;
        }
        return (int) ChronoUnit.MINUTES.between(reservation.getStartTime(), reservation.getEndTime());
    }

    /** 上课日期：由开始时间快照生成，缺失时返回空字符串 */
    private static String scheduleDateOf(LocalDateTime startTime)
    {
        return startTime == null ? "" : startTime.toLocalDate().toString();
    }
}
