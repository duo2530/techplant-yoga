package com.techplant.yoga.course.dao;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.techplant.yoga.course.domain.CourseDO;
import com.techplant.yoga.course.query.CourseQuery;

/**
 * 课程数据访问（详细设计 §3.1.1、§3.2.2）。
 *
 * <p>面向业务语义、屏蔽 mapper 细节：返回 {@code DO}，由 service 负责转 VO（§3.2.3）。</p>
 *
 * <p>逻辑删除说明：{@code selectById} 走 MyBatis-Plus 内置方法，{@code deleted = 0} 由
 * {@code @TableLogic} 自动附加，业务代码不手写（§4.1.3.1、用例 5.1.1.23）。</p>
 */
public interface CourseDao
{
    /**
     * 分页查询课程列表（只取列表列，不含 intro / duration_min）
     *
     * @param query 查询条件（名称模糊、类型、状态、页码、每页条数）
     * @return MyBatis-Plus 分页结果，由 service 转成 PageResult
     */
    IPage<CourseDO> selectPage(CourseQuery query);

    /**
     * 按编号查询课程（已删除视为不存在）
     */
    CourseDO selectById(Long id);

    /**
     * 新增课程（主键不传，由 MyBatis-Plus 生成雪花ID）
     *
     * @return 影响行数
     */
    int insert(CourseDO course);

    /**
     * 按编号更新课程状态（同时写入更新人与更新时间）
     *
     * @return 影响行数
     */
    int updateStatus(Long id, Integer status, Long updateBy);

    /**
     * 按编号全量更新课程（含可清空字段，传 null 即写入 NULL）
     *
     * @return 影响行数
     */
    int updateById(CourseDO course);
}
