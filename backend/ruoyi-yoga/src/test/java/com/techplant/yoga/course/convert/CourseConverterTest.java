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
import com.techplant.yoga.course.vo.CourseDetailVO;
import com.techplant.yoga.course.vo.CourseListItemVO;

/**
 * 课程对象转换的单元测试（详细设计 §5.1.1，用例 5.1.1.27 ~ 5.1.1.29）。
 *
 * <p>纯转换，无依赖，不连数据库。</p>
 */
@DisplayName("CourseConverter 单元测试")
class CourseConverterTest
{
    @Test
    @DisplayName("5.1.1.27 新增请求转数据对象：字段一一对应且默认启用")
    void toDO_shouldMapFieldsAndSetDefaults()
    {
        CourseCreateDTO dto = new CourseCreateDTO();
        dto.setName("哈他瑜伽");
        dto.setType(1);
        dto.setDifficulty(2);
        dto.setCoverUrl("https://cdn.example.com/course/hata.jpg");
        dto.setIntro("以体式与呼吸配合为主的经典课程");
        dto.setDurationMin(60);
        dto.setSortNo(10);

        CourseDO course = CourseConverter.toDO(dto);

        assertEquals("哈他瑜伽", course.getName());
        assertEquals(Integer.valueOf(1), course.getType());
        assertEquals(Integer.valueOf(2), course.getDifficulty());
        assertEquals("https://cdn.example.com/course/hata.jpg", course.getCoverUrl());
        assertEquals("以体式与呼吸配合为主的经典课程", course.getIntro());
        assertEquals(Integer.valueOf(60), course.getDurationMin());
        assertEquals(Integer.valueOf(10), course.getSortNo());
        assertEquals(Integer.valueOf(1), course.getStatus());
        assertEquals(Integer.valueOf(0), course.getDeleted());
        assertNull(course.getId());
    }

    @Test
    @DisplayName("5.1.1.28 更新请求写入数据对象：可选字段为空时写入空值")
    void applyUpdate_shouldWriteNullForOptionalFields()
    {
        CourseDO course = new CourseDO();
        course.setId(1001L);
        course.setName("哈他瑜伽");
        course.setCoverUrl("https://cdn.example.com/course/hata.jpg");
        course.setIntro("原介绍");

        CourseUpdateDTO dto = new CourseUpdateDTO();
        dto.setName("哈他瑜伽（初级）");
        dto.setType(1);
        dto.setDifficulty(3);
        dto.setCoverUrl(null);
        dto.setIntro(null);
        dto.setDurationMin(null);
        dto.setSortNo(20);

        CourseConverter.applyUpdate(course, dto);

        assertEquals("哈他瑜伽（初级）", course.getName());
        assertEquals(Integer.valueOf(3), course.getDifficulty());
        assertEquals(Integer.valueOf(20), course.getSortNo());
        // 清空语义：传空即清空
        assertNull(course.getCoverUrl());
        assertNull(course.getIntro());
        assertNull(course.getDurationMin());
    }

    @Test
    @DisplayName("5.1.1.29 数据对象转列表项：不含长字段")
    void toListItemVO_shouldExcludeLongFields()
    {
        CourseDO course = fullCourse();

        CourseListItemVO vo = CourseConverter.toListItemVO(course);

        assertEquals(1001L, vo.getId());
        assertEquals("哈他瑜伽", vo.getName());
        assertEquals(Integer.valueOf(1), vo.getType());
        assertEquals(Integer.valueOf(2), vo.getDifficulty());
        assertEquals("https://cdn.example.com/course/hata.jpg", vo.getCoverUrl());
        assertEquals(Integer.valueOf(1), vo.getStatus());
        assertEquals(Integer.valueOf(10), vo.getSortNo());
        assertNotNull(vo.getCreateTime());
        assertNotNull(vo.getUpdateTime());
        // 列表项不含介绍与单节时长
        assertFalse(hasField(CourseListItemVO.class, "intro"));
        assertFalse(hasField(CourseListItemVO.class, "durationMin"));
    }

    @Test
    @DisplayName("补充：数据对象转详情包含介绍与单节时长，且不含创建人/更新人")
    void toDetailVO_shouldIncludeLongFieldsButNotOperator()
    {
        CourseDO course = fullCourse();

        CourseDetailVO vo = CourseConverter.toDetailVO(course);

        assertEquals("课程介绍", vo.getIntro());
        assertEquals(Integer.valueOf(60), vo.getDurationMin());
        assertEquals(1001L, vo.getId());
        assertFalse(hasField(CourseDetailVO.class, "createBy"));
        assertFalse(hasField(CourseDetailVO.class, "updateBy"));
    }


    private CourseDO fullCourse()
    {
        CourseDO course = new CourseDO();
        course.setId(1001L);
        course.setName("哈他瑜伽");
        course.setType(1);
        course.setDifficulty(2);
        course.setCoverUrl("https://cdn.example.com/course/hata.jpg");
        course.setIntro("课程介绍");
        course.setDurationMin(60);
        course.setSortNo(10);
        course.setStatus(1);
        course.setDeleted(0);
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
