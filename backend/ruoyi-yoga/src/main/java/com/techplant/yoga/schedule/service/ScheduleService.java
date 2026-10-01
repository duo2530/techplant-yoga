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

/**
 * 排课业务服务（排课管理详细设计 §3.2）。
 *
 * <p>本接口同时承担两类对外职责（全项目统一不另立 QueryService，详细设计总览 §6）：</p>
 * <ol>
 *   <li><b>管理端</b>：列表／详情／新增／修改／状态流转／删除；</li>
 *   <li><b>跨模块</b>：四个主数据（门店／教室／教练／课程）的<b>删除门禁统计</b>，
 *       以及用户端两个免登录只读查询。</li>
 * </ol>
 *
 * <p><b>删除门禁统一口径</b>（门店管理详细设计 §2.2.5、BR-全局-007／009）：</p>
 * <pre>未结束（end_time &gt; 当前时间）AND status IN (1 待上架, 2 已上架)</pre>
 * <p>已取消（3）与已结束的历史排课<b>不阻塞</b>删除；统计失败一律 <b>fail-closed</b>，
 * 由调用方以 {@code ServiceException} 终止本次删除，<b>不降级放行</b>。</p>
 *
 * <p><b>与用户端可见口径的区别</b>（排课管理详细设计 §3.4 第 9 条）：用户端可见口径是
 * {@code status &lt;&gt; 1}，与上面的门禁口径是<b>两套不同条件</b>，不要合并成一个方法。</p>
 */
public interface ScheduleService
{
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
    // 用户端：2 个免登录只读查询（契约见《用户端接口详细设计》§2.2、§2.3）
    // ------------------------------------------------------------------

    /** 用户端排课列表（约课页）：固定过滤 status &lt;&gt; 1，按 course_type 快照与 schedule_date 派生列筛选 */
    PageResult<ScheduleCardVO> publicPage(PublicScheduleQuery query);

    /** 用户端排课详情（课程详情页）：未删除且 status &lt;&gt; 1，否则一律按不存在返回 404 */
    PublicScheduleDetailVO publicDetail(Long scheduleId);
}
