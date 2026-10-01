package com.techplant.yoga.course.convert;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.techplant.yoga.course.domain.CourseDO;
import com.techplant.yoga.course.dto.CourseCreateDTO;
import com.techplant.yoga.course.dto.CourseUpdateDTO;
import com.techplant.yoga.course.enums.CourseTypeEnum;
import com.techplant.yoga.course.vo.CourseDetailVO;
import com.techplant.yoga.course.vo.CourseListItemVO;
import com.techplant.yoga.course.vo.CourseSummaryVO;

/**
 * 课程对象转换的单元测试。
 *
 * <p>纯转换，无依赖，不连数据库。重点：<b>课种成对返回</b>（{@code courseType} + {@code courseTypeName}
 * 由 {@link CourseTypeEnum} 翻译）、列表项不含 {@code intro}、<b>不含已删除的旧字段</b>。</p>
 */
@DisplayName("CourseConverter 单元测试")
class CourseConverterTest
{
    @Test
    @DisplayName("新增请求转数据对象：字段一一对应，且不产生已删除的旧字段")
    void toDO_shouldMapFieldsOnly()
    {
        CourseCreateDTO dto = new CourseCreateDTO();
        dto.setName("  哈他瑜伽  ");
        dto.setCourseType(1);
        dto.setDifficulty(2);
        dto.setCoverUrl("https://cdn.example.com/course/hata.jpg");
        dto.setIntro("以体式与呼吸配合为主的经典课程");

        CourseDO course = CourseConverter.toDO(dto);

        assertEquals("哈他瑜伽", course.getName(), "名称前后空格应由转换层去除");
        assertEquals(Integer.valueOf(1), course.getCourseType());
        assertEquals(Integer.valueOf(2), course.getDifficulty());
        assertEquals("https://cdn.example.com/course/hata.jpg", course.getCoverUrl());
        assertEquals("以体式与呼吸配合为主的经典课程", course.getIntro());
        // 主键由框架生成，转换层不赋值
        assertNull(course.getId());
        // 已移除的旧字段在 DO 上根本不存在（表结构与设计均已去掉）
        assertFalse(hasField(CourseDO.class, "storeId"), "t_course 不再有 store_id");
        assertFalse(hasField(CourseDO.class, "status"), "课程没有状态字段");
        assertFalse(hasField(CourseDO.class, "sortNo"));
        assertFalse(hasField(CourseDO.class, "durationMin"));
        assertFalse(hasField(CourseDO.class, "deleted"), "物理删除：没有 deleted 字段");
        assertFalse(hasField(CourseDO.class, "type"), "旧列名 type 已改名为 courseType");
    }

    @Test
    @DisplayName("更新请求写入数据对象：可选字段传空即清空；空串归一为 null")
    void applyUpdate_shouldWriteNullForOptionalFields()
    {
        CourseDO course = new CourseDO();
        course.setId(1001L);
        course.setName("哈他瑜伽");
        course.setCourseType(1);
        course.setCoverUrl("https://cdn.example.com/course/hata.jpg");
        course.setIntro("原介绍");

        CourseUpdateDTO dto = new CourseUpdateDTO();
        dto.setName("哈他瑜伽（初级）");
        dto.setCourseType(2);
        dto.setDifficulty(3);
        dto.setCoverUrl(null);
        dto.setIntro("   ");

        CourseConverter.applyUpdate(course, dto);

        assertEquals("哈他瑜伽（初级）", course.getName());
        assertEquals(Integer.valueOf(2), course.getCourseType());
        assertEquals(Integer.valueOf(3), course.getDifficulty());
        // 清空语义：传 null 即清空；纯空格也归一为清空（不是「空字符串」）
        assertNull(course.getCoverUrl());
        assertNull(course.getIntro());
    }

    @Test
    @DisplayName("数据对象转列表项：课种成对返回，且不含 intro")
    void toListItemVO_shouldExcludeIntroAndReturnTypeName()
    {
        CourseDO course = fullCourse();

        CourseListItemVO vo = CourseConverter.toListItemVO(course);

        assertEquals(1001L, vo.getId());
        assertEquals("哈他瑜伽", vo.getName());
        assertEquals(Integer.valueOf(1), vo.getCourseType());
        assertEquals("团课", vo.getCourseTypeName(), "课种名称必须由后端成对返回");
        assertEquals("https://cdn.example.com/course/hata.jpg", vo.getCoverUrl());
        assertEquals(Integer.valueOf(2), vo.getDifficulty());
        assertNotNull(vo.getCreateTime());
        assertNotNull(vo.getUpdateTime());
        // 列表项不含课程介绍
        assertFalse(hasField(CourseListItemVO.class, "intro"));
        // 旧字段已下线
        assertFalse(hasField(CourseListItemVO.class, "status"));
        assertFalse(hasField(CourseListItemVO.class, "sortNo"));
        assertFalse(hasField(CourseListItemVO.class, "storeId"));
    }

    @Test
    @DisplayName("数据对象转详情：含 intro、课种成对返回、不含审计人")
    void toDetailVO_shouldIncludeIntroAndTypeName()
    {
        CourseDO course = fullCourse();

        CourseDetailVO vo = CourseConverter.toDetailVO(course);

        assertEquals(1001L, vo.getId());
        assertEquals("课程介绍", vo.getIntro());
        assertEquals(Integer.valueOf(1), vo.getCourseType());
        assertEquals("团课", vo.getCourseTypeName());
        assertFalse(hasField(CourseDetailVO.class, "createBy"));
        assertFalse(hasField(CourseDetailVO.class, "updateBy"));
        assertFalse(hasField(CourseDetailVO.class, "status"));
    }

    @Test
    @DisplayName("数据对象转摘要（跨模块出参）：含 intro / difficulty 与课种名")
    void toSummaryVO_shouldCarryRealtimeFields()
    {
        CourseDO course = fullCourse();

        CourseSummaryVO vo = CourseConverter.toSummaryVO(course);

        assertEquals(1001L, vo.getId());
        assertEquals("哈他瑜伽", vo.getName());
        assertEquals(Integer.valueOf(1), vo.getCourseType());
        assertEquals("团课", vo.getCourseTypeName());
        assertEquals("https://cdn.example.com/course/hata.jpg", vo.getCoverUrl());
        assertEquals("课程介绍", vo.getIntro());
        assertEquals(Integer.valueOf(2), vo.getDifficulty());
    }

    @Test
    @DisplayName("课种命名为空/越界时返回 null，不抛异常（供用户端空字符串兜底）")
    void nameOf_shouldReturnNullForUnknownCode()
    {
        assertNull(CourseTypeEnum.nameOf(null));
        assertNull(CourseTypeEnum.nameOf(0));
        assertNull(CourseTypeEnum.nameOf(9));
    }

    private CourseDO fullCourse()
    {
        CourseDO course = new CourseDO();
        course.setId(1001L);
        course.setName("哈他瑜伽");
        course.setCourseType(1);
        course.setDifficulty(2);
        course.setCoverUrl("https://cdn.example.com/course/hata.jpg");
        course.setIntro("课程介绍");
        course.setCreateBy(9L);
        course.setUpdateBy(9L);
        course.setCreateTime(LocalDateTime.of(2026, 9, 20, 10, 12, 33));
        course.setUpdateTime(LocalDateTime.of(2026, 9, 21, 9, 3, 11));
        return course;
    }

    private boolean hasField(Class<?> type, String fieldName)
    {
        try
        {
            type.getDeclaredField(fieldName);
            return true;
        }
        catch (NoSuchFieldException e)
        {
            return false;
        }
    }
}
