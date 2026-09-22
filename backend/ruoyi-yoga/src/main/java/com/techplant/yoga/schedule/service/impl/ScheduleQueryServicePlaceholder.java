package com.techplant.yoga.schedule.service.impl;

import javax.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import com.techplant.yoga.schedule.service.ScheduleQueryService;

/**
 * 排班统计的<b>临时占位实现</b>（详细设计 §3.1.5「接口先行」）。
 *
 * <p>详细设计把 {@link ScheduleQueryService} 定义为排班模块对外提供的接口，实现随排班模块落地。
 * 但课程模块的「停用课程」在运行时必须注入该接口，否则应用起不来，因此本版先给一个恒返回 0 的占位实现，
 * 让课程模块可以独立编码、独立联调。</p>
 *
 * <p><b>⚠️ 排班模块落地时请删除本类</b>，改为在排班模块里实现 {@link ScheduleQueryService}
 * （两个实现同时存在时 Spring 会因 Bean 冲突启动失败，这是刻意保留的提醒）。
 * 在那之前，「停用课程」的引用检查在真实数据下不会拦下任何课程。</p>
 */
@Service
public class ScheduleQueryServicePlaceholder implements ScheduleQueryService
{
    private static final Logger log = LoggerFactory.getLogger(ScheduleQueryServicePlaceholder.class);

    @PostConstruct
    public void warnPlaceholder()
    {
        log.warn("排班模块尚未实现：ScheduleQueryService 当前为占位实现，停用课程的引用检查按 0 处理（详细设计 §3.1.5）");
    }

    @Override
    public long countUnfinishedByCourseId(Long courseId)
    {
        log.debug("占位实现：排班统计恒返回 0, courseId={}", courseId);
        return 0L;
    }
}
