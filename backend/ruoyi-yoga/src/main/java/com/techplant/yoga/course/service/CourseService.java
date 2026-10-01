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

/**
 * 课程业务接口（课程管理详细设计 §3.2）。
 *
 * <p>管理端 5 个接口：列表／详情／新增／修改／<b>物理删除（带门禁）</b>；
 * <b>没有状态接口</b>（课程无状态，下架靠删除）。</p>
 *
 * <p>同时<b>对外</b>提供课程摘要读取，供排课模块做名称补齐与用户端详情页的实时字段读取
 * （全项目统一不另立 QueryService，详细设计总览 §6）。</p>
 *
 * <p>出参一律 VO，DO 与 {@code IPage} 不出 service 层；业务失败抛
 * {@code ServiceException("提示语", 业务码)}。</p>
 */
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
