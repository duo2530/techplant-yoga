package com.techplant.yoga.course.dao;

import java.time.LocalDateTime;
import org.springframework.stereotype.Repository;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.techplant.yoga.course.domain.CourseDO;
import com.techplant.yoga.course.mapper.CourseMapper;
import com.techplant.yoga.course.query.CourseQuery;

/**
 * 课程数据访问实现（详细设计 §3.1.1、§4.1.3）。
 */
@Repository
public class CourseDaoImpl implements CourseDao
{
    private final CourseMapper courseMapper;

    public CourseDaoImpl(CourseMapper courseMapper)
    {
        this.courseMapper = courseMapper;
    }

    @Override
    public IPage<CourseDO> selectPage(CourseQuery query)
    {
        // 只查列表列，避免把 intro 这类长文本带进列表查询（§4.1.3.1）
        LambdaQueryWrapper<CourseDO> wrapper = Wrappers.<CourseDO>lambdaQuery();
        wrapper.select(CourseDO::getId, CourseDO::getStoreId, CourseDO::getName, CourseDO::getType, CourseDO::getDifficulty,
                CourseDO::getCoverUrl, CourseDO::getStatus, CourseDO::getSortNo, CourseDO::getCreateTime,
                CourseDO::getUpdateTime);
        boolean hasName = query.getName() != null && !query.getName().trim().isEmpty();
        wrapper.like(hasName, CourseDO::getName, query.getName());
        wrapper.eq(query.getStoreId() != null, CourseDO::getStoreId, query.getStoreId());
        wrapper.eq(query.getType() != null, CourseDO::getType, query.getType());
        wrapper.eq(query.getStatus() != null, CourseDO::getStatus, query.getStatus());
        // 排序必须稳定：sort_no 升序 + id 降序兜底，否则翻页会重复/漏项（§4.1.3.1、用例 5.2.1.9）
        wrapper.orderByAsc(CourseDO::getSortNo);
        wrapper.orderByDesc(CourseDO::getId);

        // deleted = 0 由 @TableLogic 自动附加，业务代码不拼这个条件（用例 5.1.1.8）
        Page<CourseDO> page = new Page<CourseDO>(query.pageNumOrDefault(), query.pageSizeOrDefault());
        return courseMapper.selectPage(page, wrapper);
    }

    @Override
    public CourseDO selectById(Long id)
    {
        return courseMapper.selectById(id);
    }

    @Override
    public int insert(CourseDO course)
    {
        return courseMapper.insert(course);
    }

    @Override
    public int updateStatus(Long id, Integer status, Long updateBy)
    {
        return courseMapper.update(null, Wrappers.<CourseDO>lambdaUpdate()
                .eq(CourseDO::getId, id)
                .set(CourseDO::getStatus, status)
                .set(CourseDO::getUpdateBy, updateBy)
                .set(CourseDO::getUpdateTime, LocalDateTime.now()));
    }

    @Override
    public int updateById(CourseDO course)
    {
        // 用显式 SET 列表实现「全量更新」：cover_url / intro / duration_min 传 null 就是要写 NULL（§4.1.3.3、§2.3.7）。
        // 若改用 MyBatis-Plus 的 updateById(entity)，null 字段会被默认的 NOT_NULL 策略跳过，运营就清不掉封面图和介绍。
        return courseMapper.update(null, Wrappers.<CourseDO>lambdaUpdate()
                .eq(CourseDO::getId, course.getId())
                .set(CourseDO::getStoreId, course.getStoreId())
                .set(CourseDO::getName, course.getName())
                .set(CourseDO::getType, course.getType())
                .set(CourseDO::getDifficulty, course.getDifficulty())
                .set(CourseDO::getCoverUrl, course.getCoverUrl())
                .set(CourseDO::getIntro, course.getIntro())
                .set(CourseDO::getDurationMin, course.getDurationMin())
                .set(CourseDO::getSortNo, course.getSortNo())
                .set(CourseDO::getUpdateBy, course.getUpdateBy())
                .set(CourseDO::getUpdateTime, LocalDateTime.now()));
    }
}
