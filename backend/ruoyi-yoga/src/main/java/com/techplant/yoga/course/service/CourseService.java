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
 *
 * <p>业务失败统一抛若依的 {@code ServiceException}（带业务码），由框架自带的异常处理器转成
 * {@code AjaxResult}（2026-09-22 决策：全项目统一若依响应体系）。</p>
 */
public interface CourseService
{
    /**
     * 查询课程列表（分页）
     *
     * @return 总记录数 + 当前页数据（service 层内部结构，controller 装配成 TableDataInfo）
     */
    PageResult<CourseListItemVO> page(CourseQuery query);

    /**
     * 查询课程详情
     *
     * @throws com.ruoyi.common.exception.ServiceException 课程不存在或已删除（业务码 404）
     */
    CourseDetailVO getById(Long courseId);

    /**
     * 新增课程，返回新课程编号（新课程固定启用）
     */
    CourseCreatedVO create(CourseCreateDTO dto);

    /**
     * 修改课程，返回更新后的详情
     *
     * @throws com.ruoyi.common.exception.ServiceException 课程不存在或已删除（业务码 404）
     */
    CourseDetailVO update(Long courseId, CourseUpdateDTO dto);

    /**
     * 设置课程状态：停用前做引用检查，被引用抛 409
     *
     * @throws com.ruoyi.common.exception.ServiceException 课程不存在（404）或仍被排班/预约引用（409）
     */
    void updateStatus(Long courseId, Integer status);
}
