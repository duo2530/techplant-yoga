package com.techplant.yoga.course.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.techplant.yoga.course.domain.CourseDO;

/**
 * 课程 Mapper（详细设计 §3.1.1、§3.2.2）。
 *
 * <p>继承 MyBatis-Plus 的 {@link BaseMapper}：单表 CRUD、逻辑删除、雪花ID、分页都由框架提供；
 * 复杂 SQL 才写 XML（本模块的列表/详情/更新都能用 Wrapper 表达，见交付说明）。</p>
 *
 * <p>扫描路径见 {@code com.techplant.yoga.common.config.MybatisPlusConfig} 的 {@code @MapperScan}。</p>
 */
public interface CourseMapper extends BaseMapper<CourseDO>
{
}
