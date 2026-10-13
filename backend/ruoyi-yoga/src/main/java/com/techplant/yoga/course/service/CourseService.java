package com.techplant.yoga.course.service;

import java.util.Collection;
import java.util.List;
import com.techplant.yoga.common.response.PageResult;
import com.techplant.yoga.course.dto.CourseCreateDTO;
import com.techplant.yoga.course.dto.CourseUpdateDTO;
import com.techplant.yoga.course.query.CourseQuery;
import com.techplant.yoga.course.vo.CourseDetailVO;
import com.techplant.yoga.course.vo.CourseListItemVO;
import com.techplant.yoga.course.vo.CourseSummaryVO;

/** 课程业务接口。 */
public interface CourseService
{
    /**
     * 查询课程列表（分页，按 {@code course_type ASC, id DESC} 稳定排序）
     */
    PageResult<CourseListItemVO> page(CourseQuery query);

    /**
     * 查询课程详情
     *
     * @throws com.ruoyi.common.exception.ServiceException 课程不存在或已被删除（业务码 404）
     */
    CourseDetailVO getById(Long courseId);

    /**
     * 新增课程（<b>不返回新 ID</b>）
     *
     * @throws com.ruoyi.common.exception.ServiceException 课程名称已存在（409）
     */
    void create(CourseCreateDTO dto);

    /**
     * 修改课程（全量编辑，<b>不做引用检查</b>，也不刷新既有排课的课种快照），返回更新后的详情
     *
     * @throws com.ruoyi.common.exception.ServiceException 课程不存在（404）、名称重复（409）
     */
    CourseDetailVO update(Long courseId, CourseUpdateDTO dto);

    /**
     * 删除课程（<b>物理删除</b>，带排课引用门禁；统计失败 fail-closed）
     *
     * @throws com.ruoyi.common.exception.ServiceException 课程不存在（404）、仍有未结束排课（409）
     */
    void delete(Long courseId);

    /**
     * 批量取课程摘要（跨模块出参；只返回存在的课程，入参 null／空集合返回空列表）
     */
    List<CourseSummaryVO> summaries(Collection<Long> ids);

    /**
     * 取单个课程摘要（跨模块出参）
     *
     * @return 课程摘要；课程不存在（含已被物理删除）时返回 {@code null}，由调用方按 404 处理
     */
    CourseSummaryVO getSummary(Long courseId);
}
