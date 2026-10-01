package com.techplant.yoga.course.convert;

import java.util.ArrayList;
import java.util.List;
import com.techplant.yoga.course.domain.CourseDO;
import com.techplant.yoga.course.dto.CourseCreateDTO;
import com.techplant.yoga.course.dto.CourseUpdateDTO;
import com.techplant.yoga.course.enums.CourseTypeEnum;
import com.techplant.yoga.course.vo.CourseDetailVO;
import com.techplant.yoga.course.vo.CourseListItemVO;
import com.techplant.yoga.course.vo.CourseSummaryVO;

/**
 * 课程对象转换（课程管理详细设计 §3.2）。
 *
 * <p>字段少，本版手写转换方法；<b>课种名称统一走 {@link CourseTypeEnum#nameOf(Integer)}</b>，
 * 本类不再自己写「1 = 团课」的映射（§3.4 第 5 条）。</p>
 */
public final class CourseConverter
{
    private CourseConverter()
    {
    }

    /**
     * 新增请求 → 数据对象；<b>主键不赋值</b>（由 MyBatis-Plus 生成雪花ID），
     * 本表没有 status / deleted / store_id / sort_no，一个都不设。
     */
    public static CourseDO toDO(CourseCreateDTO dto)
    {
        CourseDO course = new CourseDO();
        course.setName(trim(dto.getName()));
        course.setCourseType(dto.getCourseType());
        course.setCoverUrl(trim(dto.getCoverUrl()));
        course.setIntro(trim(dto.getIntro()));
        course.setDifficulty(dto.getDifficulty());
        return course;
    }

    /**
     * 更新请求写入数据对象（全量编辑）
     *
     * <p><b>清空语义：</b>{@code coverUrl / intro} 一律直接覆盖，传 {@code null} 就是清空 ——
     * 这里不能写成「非空才更新」，否则运营清不掉封面图或课程介绍。</p>
     */
    public static void applyUpdate(CourseDO course, CourseUpdateDTO dto)
    {
        course.setName(trim(dto.getName()));
        course.setCourseType(dto.getCourseType());
        course.setCoverUrl(trim(dto.getCoverUrl()));
        course.setIntro(trim(dto.getIntro()));
        course.setDifficulty(dto.getDifficulty());
    }

    /**
     * 数据对象 → 列表项（不含 intro）；课种成对返回
     */
    public static CourseListItemVO toListItemVO(CourseDO course)
    {
        CourseListItemVO vo = new CourseListItemVO();
        vo.setId(course.getId());
        vo.setName(course.getName());
        vo.setCourseType(course.getCourseType());
        vo.setCourseTypeName(CourseTypeEnum.nameOf(course.getCourseType()));
        vo.setCoverUrl(course.getCoverUrl());
        vo.setDifficulty(course.getDifficulty());
        vo.setCreateTime(course.getCreateTime());
        vo.setUpdateTime(course.getUpdateTime());
        return vo;
    }

    /**
     * 数据对象 → 列表项集合
     */
    public static List<CourseListItemVO> toListItemVOList(List<CourseDO> courses)
    {
        List<CourseListItemVO> list = new ArrayList<CourseListItemVO>();
        if (courses != null)
        {
            for (CourseDO course : courses)
            {
                list.add(toListItemVO(course));
            }
        }
        return list;
    }

    /**
     * 数据对象 → 详情（多一个 intro）；课种成对返回
     */
    public static CourseDetailVO toDetailVO(CourseDO course)
    {
        CourseDetailVO vo = new CourseDetailVO();
        vo.setId(course.getId());
        vo.setName(course.getName());
        vo.setCourseType(course.getCourseType());
        vo.setCourseTypeName(CourseTypeEnum.nameOf(course.getCourseType()));
        vo.setCoverUrl(course.getCoverUrl());
        vo.setDifficulty(course.getDifficulty());
        vo.setIntro(course.getIntro());
        vo.setCreateTime(course.getCreateTime());
        vo.setUpdateTime(course.getUpdateTime());
        return vo;
    }

    /**
     * 数据对象 → 摘要（跨模块出参）
     */
    public static CourseSummaryVO toSummaryVO(CourseDO course)
    {
        CourseSummaryVO vo = new CourseSummaryVO();
        vo.setId(course.getId());
        vo.setName(course.getName());
        vo.setCourseType(course.getCourseType());
        vo.setCourseTypeName(CourseTypeEnum.nameOf(course.getCourseType()));
        vo.setCoverUrl(course.getCoverUrl());
        vo.setIntro(course.getIntro());
        vo.setDifficulty(course.getDifficulty());
        return vo;
    }

    /**
     * 数据对象 → 摘要集合
     */
    public static List<CourseSummaryVO> toSummaryVOList(List<CourseDO> courses)
    {
        List<CourseSummaryVO> list = new ArrayList<CourseSummaryVO>();
        if (courses != null)
        {
            for (CourseDO course : courses)
            {
                list.add(toSummaryVO(course));
            }
        }
        return list;
    }

    /**
     * 去除字符串前后空格；空串归一为 {@code null}（字段规则：请求字段前后空格由转换层去除）
     */
    private static String trim(String value)
    {
        if (value == null)
        {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
