package com.techplant.yoga.schedule.service;

import com.techplant.yoga.common.response.PageResult;
import com.techplant.yoga.schedule.dto.ScheduleCreateDTO;
import com.techplant.yoga.schedule.dto.ScheduleUpdateDTO;
import com.techplant.yoga.schedule.query.PublicScheduleQuery;
import com.techplant.yoga.schedule.query.ScheduleQuery;
import com.techplant.yoga.schedule.vo.PublicScheduleDetailVO;
import com.techplant.yoga.schedule.vo.ScheduleCardVO;
import com.techplant.yoga.schedule.vo.ScheduleDetailVO;
import com.techplant.yoga.schedule.vo.ScheduleListItemVO;

/** 排课业务服务。 */
public interface ScheduleService {
    // ------------------------------------------------------------------
    // 管理端：6 个接口
    // ------------------------------------------------------------------

    /** 查询排课列表（分页，管理端口径：statusText 为 待上架／已上架／已取消／已结束） */
    PageResult<ScheduleListItemVO> page(ScheduleQuery query);

    /** 查询排课详情；不存在 → 404 */
    ScheduleDetailVO getById(Long scheduleId);

    /** 新增排课：固定写入 status=1、booked_persons=0，课种快照与上课日期由服务端推导 */
    void create(ScheduleCreateDTO dto);

    /** 修改排课：仅「待上架」可改；更换课程时刷新课种快照、开始时间变化时刷新上课日期 */
    ScheduleDetailVO update(Long scheduleId, ScheduleUpdateDTO dto);

    /** 状态流转：只有 1→2（上架）、2→1（下架）、2→3（取消）；重复设同一状态幂等成功 */
    void changeStatus(Long scheduleId, Integer targetStatus);

    /** 删除排课（物理）：仅「待上架」可删，已上架须先下架，已取消与已结束是终态 */
    void delete(Long scheduleId);

    // ------------------------------------------------------------------
    // 跨模块：四个主数据的删除门禁统计
    // ------------------------------------------------------------------

    /**
     * 统计某门店下「未结束且状态为待上架或已上架」的排课数量
     *
     * @param storeId 门店编号
     * @return 阻塞删除的排课数量
     */
    long countActiveByStoreId(Long storeId);

    /**
     * 统计某教室下「未结束且状态为待上架或已上架」的排课数量
     *
     * @param classroomId 教室编号
     * @return 阻塞删除的排课数量
     */
    long countActiveByClassroomId(Long classroomId);

    /**
     * 统计某教练下「未结束且状态为待上架或已上架」的排课数量
     *
     * @param coachId 教练编号
     * @return 阻塞删除的排课数量
     */
    long countActiveByCoachId(Long coachId);

    /**
     * 统计某课程下「未结束且状态为待上架或已上架」的排课数量
     *
     * @param courseId 课程编号
     * @return 阻塞删除的排课数量
     */
    long countActiveByCourseId(Long courseId);

    // ------------------------------------------------------------------
    // 用户端：2 个免登录只读查询
    // ------------------------------------------------------------------

    /** 用户端排课列表（约课页）：固定过滤 status &lt;&gt; 1，按 course_type 快照与 schedule_date 派生列筛选 */
    PageResult<ScheduleCardVO> publicPage(PublicScheduleQuery query);

    /** 用户端排课详情（课程详情页）：未删除且 status &lt;&gt; 1，否则一律按不存在返回 404 */
    PublicScheduleDetailVO publicDetail(Long scheduleId);
}
