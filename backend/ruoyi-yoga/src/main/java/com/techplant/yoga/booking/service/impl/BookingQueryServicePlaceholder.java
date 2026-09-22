package com.techplant.yoga.booking.service.impl;

import javax.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import com.techplant.yoga.booking.service.BookingQueryService;

/**
 * 预约统计的<b>临时占位实现</b>（详细设计 §3.1.5「接口先行」）。
 *
 * <p>同 {@code ScheduleQueryServicePlaceholder}：为了让课程模块能独立运行而提供的占位实现，
 * 恒返回 0。<b>⚠️ 预约模块落地时请删除本类。</b></p>
 */
@Service
public class BookingQueryServicePlaceholder implements BookingQueryService
{
    private static final Logger log = LoggerFactory.getLogger(BookingQueryServicePlaceholder.class);

    @PostConstruct
    public void warnPlaceholder()
    {
        log.warn("预约模块尚未实现：BookingQueryService 当前为占位实现，停用课程的引用检查按 0 处理（详细设计 §3.1.5）");
    }

    @Override
    public long countUnfinishedByCourseId(Long courseId)
    {
        log.debug("占位实现：预约统计恒返回 0, courseId={}", courseId);
        return 0L;
    }
}
