package com.techplant.yoga.course.dao;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import org.springframework.stereotype.Repository;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.techplant.yoga.course.domain.CourseDO;
import com.techplant.yoga.course.mapper.CourseMapper;
import com.techplant.yoga.course.query.CourseQuery;

/**
 * 课程数据访问实现（课程管理详细设计 §3.2、§4）。
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
        // 只查列表列，避免把 intro 这类长文本带进列表查询（§3.4 第 4 条）
        LambdaQueryWrapper<CourseDO> wrapper = Wrappers.<CourseDO>lambdaQuery();
        wrapper.select(CourseDO::getId, CourseDO::getName, CourseDO::getCourseType, CourseDO::getCoverUrl,
                CourseDO::getDifficulty, CourseDO::getCreateTime, CourseDO::getUpdateTime);
        boolean hasName = query.getName() != null && !query.getName().trim().isEmpty();
        wrapper.like(hasName, CourseDO::getName, query.getName());
        wrapper.eq(query.getCourseType() != null, CourseDO::getCourseType, query.getCourseType());
        // 稳定排序：course_type 升序 + id 降序兜底，否则翻页会重复/漏项（§3.4 第 4 条）
        wrapper.orderByAsc(CourseDO::getCourseType);
        wrapper.orderByDesc(CourseDO::getId);

        Page<CourseDO> page = new Page<CourseDO>(query.pageNumOrDefault(), query.pageSizeOrDefault());
        return courseMapper.selectPage(page, wrapper);
    }

    @Override
    public CourseDO selectById(Long id)
    {
        return courseMapper.selectById(id);
    }

    @Override
    public CourseDO selectByName(String name)
    {
        return selectByNameExcludeId(name, null);
    }

    @Override
    public CourseDO selectByNameExcludeId(String name, Long excludeId)
    {
        LambdaQueryWrapper<CourseDO> wrapper = Wrappers.<CourseDO>lambdaQuery()
                .eq(CourseDO::getName, name)
                .ne(excludeId != null, CourseDO::getId, excludeId)
                .last("LIMIT 1");
        return courseMapper.selectOne(wrapper);
    }

    @Override
    public List<CourseDO> selectByIds(Collection<Long> ids)
    {
        return courseMapper.selectBatchIds(ids);
    }

    @Override
    public int insert(CourseDO course)
    {
        return courseMapper.insert(course);
    }

    @Override
    public int updateById(CourseDO course)
    {
        // 用显式 SET 列表实现「全量更新」：cover_url / intro 传 null 就是要写 NULL（§2.2.4 全量编辑）。
        // 若改用 MyBatis-Plus 的 updateById(entity)，null 字段会被默认的 NOT_NULL 策略跳过，运营就清不掉封面图和介绍。
        // 显式列举列名同时保证不会误改 t_course 上不存在的列（本表没有 store_id / status / sort_no）。
        return courseMapper.update(null, Wrappers.<CourseDO>lambdaUpdate()
                .eq(CourseDO::getId, course.getId())
                .set(CourseDO::getName, course.getName())
                .set(CourseDO::getCourseType, course.getCourseType())
                .set(CourseDO::getCoverUrl, course.getCoverUrl())
                .set(CourseDO::getIntro, course.getIntro())
                .set(CourseDO::getDifficulty, course.getDifficulty())
                .set(CourseDO::getUpdateBy, course.getUpdateBy())
                .set(CourseDO::getUpdateTime, LocalDateTime.now()));
    }

    @Override
    public int deleteById(Long id)
    {
        // 物理删除：CourseDO 没有 @TableLogic，deleteById 生成 DELETE FROM t_course WHERE id = ?
        return courseMapper.deleteById(id);
    }
}
