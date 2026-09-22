package com.techplant.yoga.course.service;

import com.techplant.yoga.common.response.PageResult;
import com.techplant.yoga.course.dto.CourseCreateDTO;
import com.techplant.yoga.course.dto.CourseUpdateDTO;
import com.techplant.yoga.course.query.CourseQuery;
import com.techplant.yoga.course.vo.CourseCreatedVO;
import com.techplant.yoga.course.vo.CourseDetailVO;
import com.techplant.yoga.course.vo.CourseListItemVO;

/**
 * 课程业务接口（详细设计 §3.1.1、§3.2.2）。
 *
 * <p>出参一律 VO，不把 DO 或 MyBatis-Plus 的 IPage 暴露给 controller（§2.1.4、§3.2.3）。</p>
 */
public interface CourseService
{
    /**
     * 查询课程列表（分页）
     */
    PageResult<CourseListItemVO> page(CourseQuery query);

    /**
     * 查询课程详情，不存在或已删除抛 404
     */
    CourseDetailVO getById(Long courseId);

    /**
     * 新增课程，返回新课程编号（新课程固定启用）
     */
    CourseCreatedVO create(CourseCreateDTO dto);

    /**
     * 修改课程，返回更新后的详情
     */
    CourseDetailVO update(Long courseId, CourseUpdateDTO dto);

    /**
     * 设置课程状态：停用前做引用检查，被引用抛 409
     */
    void updateStatus(Long courseId, Integer status);
}
