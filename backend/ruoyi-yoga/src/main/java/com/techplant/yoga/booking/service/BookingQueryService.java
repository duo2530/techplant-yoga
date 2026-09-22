package com.techplant.yoga.booking.service;

/**
 * 预约模块对外提供的查询服务（详细设计 §3.1.5「接口先行」）。
 *
 * <p><b>课程模块不直接读 {@code t_booking} 表</b>，理由同 {@code ScheduleQueryService}。</p>
 */
public interface BookingQueryService
{
    /**
     * 统计某课程下「未结束」的用户预约数量
     *
     * <p>未结束 = 已预约 + 待上课；已签到、已取消不算（§2.2.5 阻塞口径）。</p>
     *
     * @param courseId 课程编号
     * @return 未结束预约数量
     */
    long countUnfinishedByCourseId(Long courseId);
}
