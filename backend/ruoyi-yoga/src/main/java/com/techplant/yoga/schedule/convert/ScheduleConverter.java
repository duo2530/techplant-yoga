package com.techplant.yoga.schedule.convert;

import java.time.LocalDate;
import java.time.LocalDateTime;
import com.techplant.yoga.course.enums.CourseTypeEnum;
import com.techplant.yoga.schedule.domain.ScheduleDO;
import com.techplant.yoga.schedule.dto.ScheduleCreateDTO;
import com.techplant.yoga.schedule.dto.ScheduleUpdateDTO;
import com.techplant.yoga.schedule.enums.ScheduleStatusEnum;
import com.techplant.yoga.schedule.vo.PublicScheduleDetailVO;
import com.techplant.yoga.schedule.vo.ScheduleCardVO;
import com.techplant.yoga.schedule.vo.ScheduleDetailVO;
import com.techplant.yoga.schedule.vo.ScheduleListItemVO;

/**
 * 排课对象转换（排课管理详细设计 §3.2）。
 *
 * <p><b>职责边界</b>：DTO／DO／VO 之间的字段搬运，以及两处**服务端推导**——
 * 课种名称（按快照码由 {@link CourseTypeEnum} 翻译）与派生状态
 * （{@code finished} / {@code statusText} / {@code bookable}，由 {@link ScheduleStatusEnum} 一处收口，§3.4 第 2 条）。</p>
 *
 * <p><b>跨模块名称</b>（门店／课程／教练／教室名称）不在这里查：由 service 按 ID 去重后批量调各模块
 * Service 再用 setter 补上，避免 N+1；未补上时保持**空字符串**兜底（不是 null）。</p>
 */
public final class ScheduleConverter
{
    private ScheduleConverter()
    {
    }

    /**
     * 新增：组装 DO（§4.3）
     *
     * <p>写入 {@code status = 1}（待上架）、{@code booked_persons = 0}；
     * 课种快照与上课日期由调用方（service）从课程与开始时间推导后传入，**不接受客户端提交**。</p>
     *
     * @param dto               新增请求
     * @param courseTypeSnapshot 课种快照（从课程复制）
     * @param scheduleDate      上课日期（由 start_time 推导）
     */
    public static ScheduleDO toDO(ScheduleCreateDTO dto, Integer courseTypeSnapshot, LocalDate scheduleDate)
    {
        ScheduleDO schedule = new ScheduleDO();
        schedule.setStoreId(dto.getStoreId());
        schedule.setCourseId(dto.getCourseId());
        schedule.setCourseType(courseTypeSnapshot);
        schedule.setCoachId(dto.getCoachId());
        schedule.setClassroomId(dto.getClassroomId());
        schedule.setScheduleDate(scheduleDate);
        schedule.setStartTime(dto.getStartTime());
        schedule.setEndTime(dto.getEndTime());
        schedule.setMaxPersons(dto.getMaxPersons());
        schedule.setMinPersons(dto.getMinPersons());
        // 一期固定值：状态与已预约人数不走新增接口（§2.1.4 第 4~5 条）
        schedule.setStatus(ScheduleStatusEnum.PENDING.getCode());
        schedule.setBookedPersons(0);
        return schedule;
    }

    /**
     * 修改：把请求里的业务字段应用到 DO（全量编辑，§4.4）
     *
     * <p>{@code status} 与 {@code booked_persons} **不在这里出现**；课种快照只在「更换课程」时由 service 刷新。</p>
     */
    public static void applyUpdate(ScheduleDO target, ScheduleUpdateDTO dto, Integer courseTypeSnapshot,
            LocalDate scheduleDate)
    {
        target.setStoreId(dto.getStoreId());
        target.setCourseId(dto.getCourseId());
        target.setCourseType(courseTypeSnapshot);
        target.setCoachId(dto.getCoachId());
        target.setClassroomId(dto.getClassroomId());
        target.setScheduleDate(scheduleDate);
        target.setStartTime(dto.getStartTime());
        target.setEndTime(dto.getEndTime());
        target.setMaxPersons(dto.getMaxPersons());
        target.setMinPersons(dto.getMinPersons());
    }

    /**
     * DO → 管理端列表项（派生状态在 service 层统一判定一次后写入）
     *
     * @param now 判定基准时间（同一请求只用同一个 now）
     */
    public static ScheduleListItemVO toListItemVO(ScheduleDO source, LocalDateTime now)
    {
        ScheduleListItemVO vo = new ScheduleListItemVO();
        fillAdminFields(vo, source, now);
        return vo;
    }

    /**
     * DO → 管理端详情（字段与列表项完全相同）
     */
    public static ScheduleDetailVO toDetailVO(ScheduleDO source, LocalDateTime now)
    {
        ScheduleDetailVO vo = new ScheduleDetailVO();
        fillAdminFields(vo, source, now);
        return vo;
    }

    /**
     * DO → 用户端卡片（用户端文案只有 可约／已结束／已取消）
     *
     * <p>一期恒量：{@code bookedPersons = 0}、{@code remainingPlaces = maxPersons}。
     * 课程名称／封面／难度与教练、门店、教室名称由 service 批量补齐（默认空字符串）。</p>
     */
    public static ScheduleCardVO toCardVO(ScheduleDO source, LocalDateTime now)
    {
        ScheduleCardVO vo = new ScheduleCardVO();
        fillCardFields(vo, source, now);
        return vo;
    }

    /**
     * DO → 用户端详情（在卡片字段上补课程／教练／人数信息）
     *
     * <p>课种取**快照**；封面／介绍／难度由 service 实时从课程取；{@code bookers} 固定空数组。</p>
     */
    public static PublicScheduleDetailVO toPublicDetailVO(ScheduleDO source, LocalDateTime now)
    {
        PublicScheduleDetailVO vo = new PublicScheduleDetailVO();
        fillCardFields(vo, source, now);
        vo.setCourseId(source.getCourseId());
        vo.setCoachId(source.getCoachId());
        vo.setMinPersons(source.getMinPersons());
        vo.setBookers(new java.util.ArrayList<String>());
        return vo;
    }

    private static void fillAdminFields(ScheduleListItemVO vo, ScheduleDO source, LocalDateTime now)
    {
        vo.setId(source.getId());
        vo.setStoreId(source.getStoreId());
        vo.setCourseId(source.getCourseId());
        vo.setCourseType(source.getCourseType());
        vo.setCourseTypeName(emptyIfNull(CourseTypeEnum.nameOf(source.getCourseType())));
        vo.setCoachId(source.getCoachId());
        vo.setClassroomId(source.getClassroomId());
        vo.setScheduleDate(source.getScheduleDate());
        vo.setStartTime(source.getStartTime());
        vo.setEndTime(source.getEndTime());
        vo.setMaxPersons(source.getMaxPersons());
        vo.setMinPersons(source.getMinPersons());
        vo.setBookedPersons(source.getBookedPersons() == null ? 0 : source.getBookedPersons());
        vo.setStatus(source.getStatus());
        vo.setStatusText(ScheduleStatusEnum.adminTextOf(source.getStatus(), source.getEndTime(), now));
        vo.setFinished(ScheduleStatusEnum.isFinished(source.getStatus(), source.getEndTime(), now));
        // 名称兜底为空字符串，随后由 service 批量补齐（对象已被物理删除时保持空字符串）
        vo.setStoreName("");
        vo.setCourseName("");
        vo.setCoachName("");
        vo.setClassroomName("");
    }

    private static void fillCardFields(ScheduleCardVO vo, ScheduleDO source, LocalDateTime now)
    {
        vo.setId(source.getId());
        vo.setCourseType(source.getCourseType());
        vo.setCourseTypeName(emptyIfNull(CourseTypeEnum.nameOf(source.getCourseType())));
        vo.setScheduleDate(source.getScheduleDate());
        vo.setStartTime(source.getStartTime());
        vo.setEndTime(source.getEndTime());
        vo.setStoreId(source.getStoreId());
        vo.setMaxPersons(source.getMaxPersons());
        // 一期没有预约（BR-排课-018）：已预约人数恒 0、剩余名额恒等于最大人数
        vo.setBookedPersons(0);
        vo.setRemainingPlaces(source.getMaxPersons());
        vo.setStatus(source.getStatus());
        vo.setStatusText(ScheduleStatusEnum.publicTextOf(source.getStatus(), source.getEndTime(), now));
        vo.setBookable(ScheduleStatusEnum.isBookable(source.getStatus(), source.getEndTime(), now));
        // 空值兜底：名称与课程资料由 service 批量补齐，未补齐时是空字符串而不是 null
        vo.setCourseName("");
        vo.setCourseCoverUrl("");
        vo.setCoachName("");
        vo.setStoreName("");
        vo.setClassroomName("");
        if (vo instanceof PublicScheduleDetailVO)
        {
            ((PublicScheduleDetailVO) vo).setCourseIntro("");
            ((PublicScheduleDetailVO) vo).setCoachAvatarUrl("");
            ((PublicScheduleDetailVO) vo).setCoachIntro("");
        }
    }

    /** 空值兜底：契约要求名称字段返回空字符串，不能是 null */
    private static String emptyIfNull(String value)
    {
        return value == null ? "" : value;
    }
}
