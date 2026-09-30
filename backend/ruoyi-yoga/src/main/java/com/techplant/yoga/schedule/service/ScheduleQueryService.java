package com.techplant.yoga.schedule.service;

/**
 * 排班模块对外提供的查询服务（详细设计 §3.1.5「接口先行」）。
 *
 * <p><b>课程模块不直接读 {@code t_schedule} 表</b>：排班表归排班模块所有，直接读表会把两个模块的
 * 数据库结构绑死。因此这里只依赖接口，实现随排班模块落地。</p>
 */
public interface ScheduleQueryService
{
    /**
     * 统计某课程下「未完成」的排班数量
     *
     * <p>未完成 = 未开始 + 进行中；已完成、已取消不算（§2.2.5 阻塞口径）。</p>
     *
     * @param courseId 课程编号
     * @return 未完成排班数量
     */
    long countUnfinishedByCourseId(Long courseId);

    /** 统计课程关联的排班数量，用于修改课程门店归属时的引用保护。 */
    long countByCourseId(Long courseId);
}
