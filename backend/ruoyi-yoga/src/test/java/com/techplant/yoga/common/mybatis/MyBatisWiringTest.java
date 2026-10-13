package com.techplant.yoga.common.mybatis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import com.techplant.yoga.common.testcontainers.AbstractYogaIntegrationTest;
import com.techplant.yoga.common.testcontainers.YogaMyBatisTestConfig;
import com.techplant.yoga.common.testcontainers.YogaSchema;
import com.techplant.yoga.course.dao.CourseDao;
import com.techplant.yoga.course.dao.CourseDaoImpl;
import com.techplant.yoga.course.domain.CourseDO;
import com.techplant.yoga.course.query.CourseQuery;

/** MyBatis-Plus 装配的集成测试：SqlSessionFactory、分页插件与审计填充都用运行环境的配置类。 */
@ContextConfiguration(classes = { YogaMyBatisTestConfig.class, CourseDaoImpl.class })
@DisplayName("MyBatis-Plus 装配集成测试（Testcontainers MySQL：雪花ID / 物理删除 / 分页 / 审计填充）")
class MyBatisWiringTest extends AbstractYogaIntegrationTest
{
    @Autowired
    private CourseDao courseDao;

    @BeforeEach
    void resetTable()
    {
        YogaSchema.reset(dataSource, "t_course");
    }

    @Test
    @DisplayName("新增：主键由 MyBatis-Plus 生成（雪花ID），审计字段由 MetaObjectHandler 填充")
    void insert_shouldGenerateSnowflakeIdAndFillAuditFields()
    {
        CourseDO course = new CourseDO();
        course.setName("哈他瑜伽");
        course.setCourseType(1);
        course.setDifficulty(2);

        int rows = courseDao.insert(course);

        assertEquals(1, rows);
        assertNotNull(course.getId(), "主键应由 IdType.ASSIGN_ID 生成");
        assertTrue(String.valueOf(course.getId()).length() >= 18, "雪花ID 应为 18~19 位：" + course.getId());
        assertEquals(OPERATOR_ID, course.getCreateBy(), "创建人应由 MetaObjectHandler 填充");
        assertNotNull(course.getCreateTime(), "创建时间应由 MetaObjectHandler 填充");
        assertEquals(OPERATOR_ID, course.getUpdateBy(), "更新人应由 MetaObjectHandler 填充");
        assertNotNull(course.getUpdateTime(), "更新时间应由 MetaObjectHandler 填充");
    }

    @Test
    @DisplayName("物理删除：deleteById 后该行真的查不到（没有 deleted 列、没有 @TableLogic）")
    void deleteById_shouldPhysicallyRemoveRow()
    {
        CourseDO course = insert("待删除课程", 1, 3);

        int rows = courseDao.deleteById(course.getId());

        assertEquals(1, rows);
        assertNull(courseDao.selectById(course.getId()), "物理删除后不应再查到该课程");
        CourseDO recreated = insert("待删除课程", 1, 3);
        assertNotNull(recreated.getId());
    }

    @Test
    @DisplayName("详情：selectById 按主键查回全部业务字段")
    void selectById_shouldReturnBusinessFields()
    {
        CourseDO course = new CourseDO();
        course.setName("流瑜伽");
        course.setCourseType(2);
        course.setDifficulty(3);
        course.setCoverUrl("https://cdn.example.com/course/vinyasa.jpg");
        course.setIntro("体式之间以呼吸串联");
        courseDao.insert(course);

        CourseDO found = courseDao.selectById(course.getId());

        assertNotNull(found);
        assertEquals("流瑜伽", found.getName());
        assertEquals(Integer.valueOf(2), found.getCourseType());
        assertEquals(Integer.valueOf(3), found.getDifficulty());
        assertEquals("https://cdn.example.com/course/vinyasa.jpg", found.getCoverUrl());
        assertEquals("体式之间以呼吸串联", found.getIntro());
    }

    @Test
    @DisplayName("列表：按 course_type 升序、同课种内 id 降序稳定排序，且列表查询不含 intro")
    void selectPage_shouldReturnStableOrder()
    {
        insert("特色课A", 4, 1);
        insert("团课B", 1, 1);
        insert("团课C", 1, 1);
        insert("精品课D", 2, 1);

        CourseQuery query = new CourseQuery();
        List<CourseDO> records = courseDao.selectPage(query).getRecords();

        assertEquals(4, records.size());
        // 同课种（course_type = 1）内按 id 降序：后插入的「团课C」id 更大，排在前面
        assertEquals("团课C", records.get(0).getName());
        assertEquals("团课B", records.get(1).getName());
        assertTrue(records.get(0).getId() > records.get(1).getId());
        assertEquals("精品课D", records.get(2).getName());
        assertEquals("特色课A", records.get(3).getName());
        // 列表 SQL 只 select 列表列：intro 没被查出来
        assertNull(records.get(0).getIntro(), "列表查询不应带出 intro");
    }

    @Test
    @DisplayName("全量更新：显式 SET 让传 null 的字段被真正清空")
    void updateById_shouldClearNullFields()
    {
        CourseDO course = new CourseDO();
        course.setName("哈他瑜伽");
        course.setCourseType(1);
        course.setDifficulty(2);
        course.setCoverUrl("https://cdn.example.com/course/hata.jpg");
        course.setIntro("课程介绍");
        courseDao.insert(course);

        CourseDO update = new CourseDO();
        update.setId(course.getId());
        update.setName("哈他瑜伽（初级）");
        update.setCourseType(1);
        update.setDifficulty(3);
        update.setCoverUrl(null);
        update.setIntro(null);
        update.setUpdateBy(OPERATOR_ID);
        int rows = courseDao.updateById(update);

        assertEquals(1, rows);
        CourseDO updated = courseDao.selectById(course.getId());
        assertEquals("哈他瑜伽（初级）", updated.getName());
        assertEquals(Integer.valueOf(3), updated.getDifficulty());
        assertNull(updated.getCoverUrl(), "封面图应被清空");
        assertNull(updated.getIntro(), "课程介绍应被清空");
        assertEquals(OPERATOR_ID, updated.getUpdateBy());
    }

    private CourseDO insert(String name, int courseType, int difficulty)
    {
        CourseDO course = new CourseDO();
        course.setName(name);
        course.setCourseType(courseType);
        course.setDifficulty(difficulty);
        courseDao.insert(course);
        return course;
    }
}
