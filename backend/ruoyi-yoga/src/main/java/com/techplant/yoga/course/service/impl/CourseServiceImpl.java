package com.techplant.yoga.course.service.impl;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.ruoyi.common.exception.ServiceException;
import com.techplant.yoga.booking.service.BookingQueryService;
import com.techplant.yoga.common.response.PageResult;
import com.techplant.yoga.common.util.CurrentUserUtils;
import com.techplant.yoga.course.convert.CourseConverter;
import com.techplant.yoga.course.dao.CourseDao;
import com.techplant.yoga.course.domain.CourseDO;
import com.techplant.yoga.course.dto.CourseCreateDTO;
import com.techplant.yoga.course.dto.CourseUpdateDTO;
import com.techplant.yoga.course.query.CourseQuery;
import com.techplant.yoga.course.service.CourseService;
import com.techplant.yoga.course.vo.CourseCreatedVO;
import com.techplant.yoga.course.vo.CourseDetailVO;
import com.techplant.yoga.course.vo.CourseListItemVO;
import com.techplant.yoga.schedule.service.ScheduleQueryService;

/**
 * 课程业务实现（详细设计 §3.1.1、§3.1.4、§4.1）。
 *
 * <p>职责：存在性校验、跨模块引用检查、事务边界；对象转换交给 {@code CourseConverter}。</p>
 *
 * <p><b>异常口径：</b>业务失败统一抛若依的 {@link ServiceException}（带业务码），由框架自带的
 * {@code GlobalExceptionHandler} 转成 {@code AjaxResult}（2026-09-22 决策：全项目统一若依响应体系）：
 * 课程不存在 → 404、停用被引用 → 409。</p>
 */
@Service
public class CourseServiceImpl implements CourseService
{
    /** 课程状态：停用 */
    private static final int STATUS_DISABLED = 0;

    /** 资源不存在 */
    private static final int NOT_FOUND = 404;

    /** 状态冲突：停用被引用 */
    private static final int CONFLICT = 409;

    /** 新增/更新未生效 */
    private static final int INTERNAL_ERROR = 500;

    private final CourseDao courseDao;

    private final ScheduleQueryService scheduleQueryService;

    private final BookingQueryService bookingQueryService;

    public CourseServiceImpl(CourseDao courseDao, ScheduleQueryService scheduleQueryService,
            BookingQueryService bookingQueryService)
    {
        this.courseDao = courseDao;
        this.scheduleQueryService = scheduleQueryService;
        this.bookingQueryService = bookingQueryService;
    }

    /**
     * 查询课程列表：只读，不需要事务
     */
    @Override
    public PageResult<CourseListItemVO> page(CourseQuery query)
    {
        IPage<CourseDO> page = courseDao.selectPage(query);
        // IPage 不出 service 层（§2.1.4）
        return PageResult.of(page.getTotal(), page.getCurrent(), page.getSize(),
                CourseConverter.toListItemVOList(page.getRecords()));
    }

    /**
     * 查询课程详情：不存在或已删除 → 404
     */
    @Override
    public CourseDetailVO getById(Long courseId)
    {
        CourseDO course = courseDao.selectById(courseId);
        if (course == null)
        {
            throw notFound();
        }
        return CourseConverter.toDetailVO(course);
    }

    /**
     * 新增课程（§4.1.2）：状态固定启用、主键不赋值、审计字段由框架填充
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public CourseCreatedVO create(CourseCreateDTO dto)
    {
        long start = System.currentTimeMillis();

        CourseDO course = CourseConverter.toDO(dto);
        int rows = courseDao.insert(course);
        if (rows != 1 || course.getId() == null)
        {
            throw new ServiceException("新增课程失败", INTERNAL_ERROR);
        }
        return CourseConverter.toCreatedVO(course.getId());
    }

    /**
     * 修改课程（§4.1.3.3）：coverUrl / intro / durationMin 传 null 表示清空
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public CourseDetailVO update(Long courseId, CourseUpdateDTO dto)
    {
        long start = System.currentTimeMillis();
        CourseDO course = courseDao.selectById(courseId);
        if (course == null)
        {
            throw notFound();
        }

        CourseConverter.applyUpdate(course, dto);
        course.setUpdateBy(CurrentUserUtils.getUserIdOrNull());
        int rows = courseDao.updateById(course);

        CourseDO latest = courseDao.selectById(courseId);
        if (latest == null)
        {
            // 并发下课程被删：存在性校验通过、更新落空
            throw notFound();
        }
        // rows = 0 只说明「提交值与库中一致」（MySQL 影响行数口径），不是失败，按成功处理
        return CourseConverter.toDetailVO(latest);
    }

    /**
     * 设置课程状态（§4.1.1）：只在停用时做跨模块引用检查，被引用 → 409 + 阻塞明细
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Long courseId, Integer status)
    {
        long start = System.currentTimeMillis();
        CourseDO course = courseDao.selectById(courseId);
        if (course == null)
        {
            throw notFound();
        }

        Integer oldStatus = course.getStatus();
        // 关键规则：只有停用才做引用检查；启用不校验（§2.2.5、用例 5.1.1.5）
        if (isDisable(status))
        {
            long scheduleCount = countUnfinishedSchedules(courseId);
            long bookingCount = countUnfinishedBookings(courseId);
            if (scheduleCount > 0 || bookingCount > 0)
            {
                // 业务码 409：提示语里带上阻塞明细（若依的 AjaxResult 只能承载 code/msg，
                // 结构化明细随 2026-09-22 的响应体系决策取消，2026-09-22 详见交付说明）
                throw new ServiceException(
                        String.format("该课程下仍有 %d 个未完成排班、%d 条未结束预约，无法停用", scheduleCount, bookingCount),
                        CONFLICT);
            }
        }

        int rows = courseDao.updateStatus(courseId, status, CurrentUserUtils.getUserIdOrNull());
        if (rows == 0)
        {
            // 幂等：重复设置同一状态时 MySQL 影响行数可能是 0，按成功处理（§4.1.4）
        }
    }

    /**
     * 统计该课程下未完成的排班数量（跨模块调用）
     *
     * <p><b>fail-closed：</b>调用失败不吞异常、不降级放行 —— 否则统计服务一抖动课程就会被误停用。</p>
     */
    private long countUnfinishedSchedules(Long courseId)
    {
        long start = System.currentTimeMillis();
        try
        {
            long count = scheduleQueryService.countUnfinishedByCourseId(courseId);
            return count;
        }
        catch (RuntimeException e)
        {
            throw e;
        }
    }

    /**
     * 统计该课程下未结束的预约数量（跨模块调用）
     */
    private long countUnfinishedBookings(Long courseId)
    {
        long start = System.currentTimeMillis();
        try
        {
            long count = bookingQueryService.countUnfinishedByCourseId(courseId);
            return count;
        }
        catch (RuntimeException e)
        {
            throw e;
        }
    }

    private boolean isDisable(Integer status)
    {
        return status != null && status == STATUS_DISABLED;
    }

    private ServiceException notFound()
    {
        return new ServiceException("课程不存在或已被删除", NOT_FOUND);
    }

    private boolean hasText(String value)
    {
        return value != null && !value.trim().isEmpty();
    }
}
