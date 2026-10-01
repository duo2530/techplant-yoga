package com.techplant.yoga.course.dao;

import java.util.Collection;
import java.util.List;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.techplant.yoga.course.domain.CourseDO;
import com.techplant.yoga.course.query.CourseQuery;

/**
 * 课程数据访问（课程管理详细设计 §3.2）。
 *
 * <p>面向业务语义、屏蔽 mapper 细节：返回 {@code DO}，由 service 负责转 VO。</p>
 *
 * <p><b>物理删除：</b>{@code CourseDO} 没有 {@code deleted} 字段、没有 {@code @TableLogic}，
 * 业务代码也<b>不要</b>拼 {@code deleted = 0} 条件（详细设计总览 §2、BR-课程-008）。</p>
 */
public interface CourseDao
{
    /**
     * 分页查询课程列表（只取列表列，<b>不含 intro</b>），按 {@code course_type ASC, id DESC} 稳定排序
     */
    IPage<CourseDO> selectPage(CourseQuery query);

    /**
     * 按编号查询课程；不存在返回 {@code null}
     */
    CourseDO selectById(Long id);

    /**
     * 按名称精确查询课程（名称全平台唯一）
     */
    CourseDO selectByName(String name);

    /**
     * 按名称查询课程并排除自身（修改时的查重）
     *
     * @param excludeId 需要排除的课程编号，可为空（为空时不排除任何行）
     */
    CourseDO selectByNameExcludeId(String name, Long excludeId);

    /**
     * 按编号批量查询（名称补齐用；调用方保证非空集合）
     */
    List<CourseDO> selectByIds(Collection<Long> ids);

    /**
     * 新增课程（主键不传，由 MyBatis-Plus 生成雪花ID）
     *
     * @return 影响行数
     */
    int insert(CourseDO course);

    /**
     * 按编号全量更新业务字段（显式 SET，含可清空字段：传 null 即写入 NULL）
     *
     * @return 影响行数
     */
    int updateById(CourseDO course);

    /**
     * 按编号<b>物理删除</b>课程
     *
     * @return 影响行数
     */
    int deleteById(Long id);
}
