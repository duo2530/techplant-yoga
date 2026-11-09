package com.techplant.yoga.schedule.task;

import lombok.RequiredArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import com.techplant.yoga.reservation.enums.ReservationStatusEnum;
import com.techplant.yoga.reservation.service.ReservationService;
import com.techplant.yoga.schedule.service.ScheduleBookingService;

/**
 * 定时任务：开课前不超过取消截止分钟数、人数不足最低开课人数的排课自动取消。
 *
 * <p>由若依定时任务按调用目标 {@code yogaScheduleTask.cancelUnderstaffed} 调用；每节排课单独一个事务，
 * 一节失败不影响其它排课，下一轮会重新扫描。</p>
 */
@Component("yogaScheduleTask")
@RequiredArgsConstructor
public class ScheduleAutoCancelTask {
    private static final Logger log = LoggerFactory.getLogger(ScheduleAutoCancelTask.class);

    private final ScheduleBookingService scheduleBookingService;

    private final ReservationService reservationService;

    public void cancelUnderstaffed() {
        LocalDateTime now = LocalDateTime.now();
        List<Long> candidates = scheduleBookingService.understaffedIds(now,
                now.plusMinutes(ReservationStatusEnum.CANCEL_DEADLINE_MINUTES));
        if (candidates.isEmpty()) {
            log.debug("人数不足自动取消：没有待取消的排课");
            return;
        }
        List<Long> cancelled = new ArrayList<Long>();
        List<Long> failed = new ArrayList<Long>();
        for (Long scheduleId : candidates) {
            try {
                if (reservationService.cancelUnderstaffedSchedule(scheduleId)) {
                    cancelled.add(scheduleId);
                }
            } catch (RuntimeException e) {
                failed.add(scheduleId);
                log.error("人数不足自动取消失败 scheduleId={}", scheduleId, e);
            }
        }
        log.info("人数不足自动取消：候选 {} 节，取消 {} 节 {}，失败 {} 节 {}", candidates.size(), cancelled.size(), cancelled,
                failed.size(), failed);
    }
}
