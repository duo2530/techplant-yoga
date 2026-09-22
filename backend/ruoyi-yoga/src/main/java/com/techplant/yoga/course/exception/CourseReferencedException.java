package com.techplant.yoga.course.exception;

import org.springframework.http.HttpStatus;
import com.techplant.yoga.common.exception.BusinessException;

/**
 * 停用课程被引用时抛出（详细设计 §2.2.5、§3.1.1）。
 *
 * <p>携带阻塞明细：未完成排班数与未结束预约数，由全局异常处理器组装成
 * HTTP 409 + {@code ApiResponse{code:409, data:{scheduleCount, bookingCount}}}。</p>
 */
public class CourseReferencedException extends BusinessException
{
    private static final long serialVersionUID = 1L;

    /** 未完成的排班数量 */
    private final long scheduleCount;

    /** 未结束的预约数量 */
    private final long bookingCount;

    public CourseReferencedException(long scheduleCount, long bookingCount)
    {
        super(HttpStatus.CONFLICT.value(),
                String.format("该课程下仍有 %d 个未完成排班、%d 条未结束预约，无法停用", scheduleCount, bookingCount),
                new BlockDetail(scheduleCount, bookingCount));
        this.scheduleCount = scheduleCount;
        this.bookingCount = bookingCount;
    }

    public long getScheduleCount()
    {
        return scheduleCount;
    }

    public long getBookingCount()
    {
        return bookingCount;
    }

    /**
     * 阻塞明细（§2.2.5 的 409 响应体）。
     *
     * <p>计数保持数字类型：{@code JacksonConfig} 只把业务对象的 ID 字段转成字符串，
     * 这两个计数按 §2.3 的结构仍是整数。</p>
     */
    public static class BlockDetail
    {
        private final long scheduleCount;

        private final long bookingCount;

        public BlockDetail(long scheduleCount, long bookingCount)
        {
            this.scheduleCount = scheduleCount;
            this.bookingCount = bookingCount;
        }

        public long getScheduleCount()
        {
            return scheduleCount;
        }

        public long getBookingCount()
        {
            return bookingCount;
        }
    }
}
