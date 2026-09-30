package com.techplant.yoga.course.convert;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import com.techplant.yoga.course.domain.CourseDO;
import com.techplant.yoga.course.dto.CourseCreateDTO;
import com.techplant.yoga.course.dto.CourseUpdateDTO;
import com.techplant.yoga.course.vo.CourseCreatedVO;
import com.techplant.yoga.course.vo.CourseDetailVO;
import com.techplant.yoga.course.vo.CourseListItemVO;

/**
 * 课程对象转换（详细设计 §3.1.4）。
 *
 * <p>字段少，本版手写转换方法，可读性优先；字段变多后再考虑引入 MapStruct。</p>
 */
public final class CourseConverter
{
    /** 新增课程的默认状态：启用（§2.2.3） */
    private static final int STATUS_ENABLED = 1;

    /** 逻辑删除标记：正常（§1.1.1） */
    private static final int NOT_DELETED = 0;

    /** 默认展示排序（§1.2.1 sort_no 默认 0） */
    private static final int DEFAULT_SORT_NO = 0;

    private CourseConverter()
    {
    }

    /**
     * 新增请求 → 数据对象（§3.1.4）
     *
     * <p>状态固定启用、未删除标记固定正常、排序号不传按 0 处理，**主键不赋值**（由 MyBatis-Plus 生成雪花ID）。</p>
     */
    public static CourseDO toDO(CourseCreateDTO dto)
    {
        CourseDO course = new CourseDO();
        course.setStoreId(dto.getStoreId());
        course.setName(dto.getName());
        course.setType(dto.getType());
        course.setDifficulty(dto.getDifficulty());
        course.setCoverUrl(dto.getCoverUrl());
        course.setIntro(dto.getIntro());
        course.setDurationMin(dto.getDurationMin());
        course.setSortNo(dto.getSortNo() == null ? DEFAULT_SORT_NO : dto.getSortNo());
        course.setStatus(STATUS_ENABLED);
        course.setDeleted(NOT_DELETED);
        return course;
    }

    /**
     * 更新请求写入数据对象（§4.1.3.3）
     *
     * <p><b>清空语义：</b>{@code coverUrl / intro / durationMin} 一律直接覆盖，传 {@code null} 就是清空 ——
     * 这里不能写成「非空才更新」，否则运营清不掉封面图或课程介绍（§2.3.7、用例 5.1.1.12）。</p>
     */
    public static void applyUpdate(CourseDO course, CourseUpdateDTO dto)
    {
        course.setStoreId(dto.getStoreId());
        course.setName(dto.getName());
        course.setType(dto.getType());
        course.setDifficulty(dto.getDifficulty());
        course.setCoverUrl(dto.getCoverUrl());
        course.setIntro(dto.getIntro());
        course.setDurationMin(dto.getDurationMin());
        course.setSortNo(dto.getSortNo() == null ? DEFAULT_SORT_NO : dto.getSortNo());
    }

    /**
     * 数据对象 → 列表项（不含 intro / durationMin）
     */
    public static CourseListItemVO toListItemVO(CourseDO course)
    {
        CourseListItemVO vo = new CourseListItemVO();
        vo.setId(course.getId());
        vo.setStoreId(course.getStoreId());
        vo.setName(course.getName());
        vo.setType(course.getType());
        vo.setDifficulty(course.getDifficulty());
        vo.setCoverUrl(course.getCoverUrl());
        vo.setStatus(course.getStatus());
        vo.setSortNo(course.getSortNo());
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
     * 数据对象 → 详情
     */
    public static CourseDetailVO toDetailVO(CourseDO course)
    {
        CourseDetailVO vo = new CourseDetailVO();
        vo.setId(course.getId());
        vo.setStoreId(course.getStoreId());
        vo.setName(course.getName());
        vo.setType(course.getType());
        vo.setDifficulty(course.getDifficulty());
        vo.setCoverUrl(course.getCoverUrl());
        vo.setIntro(course.getIntro());
        vo.setDurationMin(course.getDurationMin());
        vo.setSortNo(course.getSortNo());
        vo.setStatus(course.getStatus());
        vo.setCreateTime(course.getCreateTime());
        vo.setUpdateTime(course.getUpdateTime());
        return vo;
    }

    /**
     * 主键 → 新增返回
     */
    public static CourseCreatedVO toCreatedVO(Long id)
    {
        return new CourseCreatedVO(id);
    }
}
