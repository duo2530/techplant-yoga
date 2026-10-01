package com.techplant.yoga.course.service.impl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.ruoyi.common.exception.ServiceException;
import com.techplant.yoga.common.response.PageResult;
import com.techplant.yoga.common.util.CurrentUserUtils;
import com.techplant.yoga.course.convert.CourseConverter;
import com.techplant.yoga.course.dao.CourseDao;
import com.techplant.yoga.course.domain.CourseDO;
import com.techplant.yoga.course.dto.CourseCreateDTO;
import com.techplant.yoga.course.dto.CourseUpdateDTO;
import com.techplant.yoga.course.enums.CourseTypeEnum;
import com.techplant.yoga.course.query.CourseQuery;
import com.techplant.yoga.course.service.CourseService;
import com.techplant.yoga.course.vo.CourseDetailVO;
import com.techplant.yoga.course.vo.CourseListItemVO;
import com.techplant.yoga.course.vo.CourseSummaryVO;
import com.techplant.yoga.schedule.service.ScheduleService;

/**
 * 课程业务实现（课程管理详细设计 §3.2、§4）。
 *
 * <p><b>物理删除：</b>本表没有 {@code deleted} 字段，删除走 {@code DELETE}；课种取值范围与
 * 「码 → 名」翻译都只在 {@link CourseTypeEnum} 一处（§3.4 第 5 条）。</p>
 *
 * <p><b>删除门禁：</b>调 {@link ScheduleService#countActiveByCourseId(Long)} 统计「未结束且待上架或已上架」的排课数；
 * 统计调用失败一律 <b>fail-closed</b>（抛 409「引用检查未完成，已拒绝本次删除」），不降级放行。</p>
 *
 * <p><b>跨模块注入用 {@code @Lazy}：</b>课程依赖排课统计、排课又依赖课程读取，构造器注入会成环，
 * 本工程统一在构造器参数上加 {@code @Lazy} 打破。</p>
 */
@Service
public class CourseServiceImpl implements CourseService
{
    /** 资源不存在 */
    private static final int NOT_FOUND = 404;

    /** 状态冲突：名称重复／删除门禁未通过 */
    private static final int CONFLICT = 409;

    /** 新增未生效 */
    private static final int INTERNAL_ERROR = 500;

    private final CourseDao courseDao;

    private final ScheduleService scheduleService;

    public CourseServiceImpl(CourseDao courseDao, @Lazy ScheduleService scheduleService)
    {
        this.courseDao = courseDao;
        this.scheduleService = scheduleService;
    }

    /**
     * 查询课程列表：只读，不需要事务
     */
    @Override
    public PageResult<CourseListItemVO> page(CourseQuery query)
    {
        IPage<CourseDO> page = courseDao.selectPage(query);
        return PageResult.of(page.getTotal(), page.getCurrent(), page.getSize(),
                CourseConverter.toListItemVOList(page.getRecords()));
    }

    /**
     * 查询课程详情：不存在（或已被物理删除）→ 404
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
     * 新增课程：名称全平台唯一（不分课种）、课种取值 1~4；<b>不返回新 ID</b>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void create(CourseCreateDTO dto)
    {
        CourseDO course = CourseConverter.toDO(dto);
        // 课种取值范围校验只由 CourseTypeEnum 提供（DTO 上的 @CourseTypeValid 已先拦一道，
        // 这里再兜一道，两者都委托同一个枚举，不存在第二份取值表）
        CourseTypeEnum.validate(course.getCourseType());
        if (courseDao.selectByName(course.getName()) != null)
        {
            throw new ServiceException("课程名称已存在", CONFLICT);
        }
        int rows = courseDao.insert(course);
        if (rows != 1)
        {
            throw new ServiceException("新增课程失败", INTERNAL_ERROR);
        }
    }

    /**
     * 修改课程（§4.4）：名称查重排除自身；<b>不做引用检查</b>、<b>不刷新既有排课的课种快照</b>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public CourseDetailVO update(Long courseId, CourseUpdateDTO dto)
    {
        CourseDO course = courseDao.selectById(courseId);
        if (course == null)
        {
            throw notFound();
        }

        CourseTypeEnum.validate(dto.getCourseType());
        CourseConverter.applyUpdate(course, dto);
        if (courseDao.selectByNameExcludeId(course.getName(), courseId) != null)
        {
            throw new ServiceException("课程名称已存在", CONFLICT);
        }

        course.setUpdateBy(CurrentUserUtils.getUserIdOrNull());
        courseDao.updateById(course);

        CourseDO latest = courseDao.selectById(courseId);
        if (latest == null)
        {
            // 并发下课程被删：存在性校验通过、更新落空
            throw notFound();
        }
        return CourseConverter.toDetailVO(latest);
    }

    /**
     * 删除课程（§4.5）：物理删除，删除前做排课引用门禁（fail-closed）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long courseId)
    {
        CourseDO course = courseDao.selectById(courseId);
        if (course == null)
        {
            throw notFound();
        }

        long activeSchedules = countActiveSchedules(courseId);
        if (activeSchedules > 0)
        {
            throw new ServiceException(
                    String.format("该课程仍有 %d 节未结束的排课，无法删除", activeSchedules), CONFLICT);
        }

        courseDao.deleteById(courseId);
    }

    /**
     * 批量取课程摘要（跨模块出参）：按编号去重后一次查回，避免 N+1
     */
    @Override
    public List<CourseSummaryVO> summaries(Collection<Long> ids)
    {
        if (ids == null || ids.isEmpty())
        {
            return new ArrayList<CourseSummaryVO>();
        }
        LinkedHashSet<Long> distinctIds = new LinkedHashSet<Long>();
        for (Long id : ids)
        {
            if (id != null)
            {
                distinctIds.add(id);
            }
        }
        if (distinctIds.isEmpty())
        {
            return new ArrayList<CourseSummaryVO>();
        }
        return CourseConverter.toSummaryVOList(courseDao.selectByIds(distinctIds));
    }

    /**
     * 取单个课程摘要（跨模块出参）；课程不存在返回 {@code null}
     */
    @Override
    public CourseSummaryVO getSummary(Long courseId)
    {
        if (courseId == null)
        {
            return null;
        }
        CourseDO course = courseDao.selectById(courseId);
        return course == null ? null : CourseConverter.toSummaryVO(course);
    }

    /**
     * 统计该课程下「未结束且状态为待上架或已上架」的排课数量（跨模块调用）
     *
     * <p><b>fail-closed：</b>统计调用失败不吞异常、不降级放行 —— 统计服务一抖动课程就会被误删。</p>
     */
    private long countActiveSchedules(Long courseId)
    {
        try
        {
            return scheduleService.countActiveByCourseId(courseId);
        }
        catch (RuntimeException e)
        {
            throw new ServiceException("引用检查未完成，已拒绝本次删除", CONFLICT);
        }
    }

    private ServiceException notFound()
    {
        return new ServiceException("课程不存在或已被删除", NOT_FOUND);
    }
}
