package com.techplant.yoga.common.mybatis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.Statement;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import com.ruoyi.common.core.domain.model.LoginUser;
import com.ruoyi.framework.config.MyBatisConfig;
import com.techplant.yoga.common.config.MybatisPlusConfig;
import com.techplant.yoga.course.dao.CourseDao;
import com.techplant.yoga.course.dao.CourseDaoImpl;
import com.techplant.yoga.course.domain.CourseDO;
import com.techplant.yoga.course.query.CourseQuery;

/**
 * MyBatis-Plus 装配的集成测试（不是详细设计 §5.1 的用例，属于交付前的自检）。
 *
 * <p><b>为什么需要它：</b>工程自己声明了 {@code SqlSessionFactory}（ruoyi-framework 的
 * {@code MyBatisConfig}），MyBatis-Plus 的自动配置因此整体退出，分页插件与
 * {@code MetaObjectHandler} 是手工装配的。这类「装配是否真的生效」的问题读代码看不出来，
 * 只有把真实的 {@code MyBatisConfig} + {@code MybatisPlusConfig} + {@code AuditMetaObjectHandler}
 * 跑起来才能验证。</p>
 *
 * <p>库用 H2 内存库（MySQL 兼容模式），表结构与 {@code backend/sql/yoga_course.sql} 对应，
 * 不连接开发库、不产生任何外部写入（详细设计 §5.1「单元测试不连数据库」的口径
 * 针对的是 5.1 的用例，本类只做装配自检）。</p>
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { MyBatisWiringTest.TestConfig.class, MyBatisConfig.class, MybatisPlusConfig.class,
        AuditMetaObjectHandler.class, CourseDaoImpl.class })
@TestPropertySource(properties = {
        "mybatis.typeAliasesPackage=com.ruoyi.**.domain,com.techplant.yoga.**.domain",
        "mybatis.mapperLocations=classpath*:mapper/**/*Mapper.xml",
        "mybatis.configLocation=classpath:mybatis/mybatis-config.xml" })
@DisplayName("MyBatis-Plus 装配集成测试（雪花ID / 逻辑删除 / 审计填充）")
class MyBatisWiringTest
{
    private static final Long OPERATOR_ID = 1L;

    @Autowired
    private CourseDao courseDao;

    @Autowired
    private DataSource dataSource;

    @Configuration
    @EnableTransactionManagement
    static class TestConfig
    {
        @Bean
        public DataSource dataSource()
        {
            return new EmbeddedDatabaseBuilder().setType(EmbeddedDatabaseType.H2)
                    .setName("yoga_mybatis_wiring;MODE=MySQL;DATABASE_TO_LOWER=TRUE").build();
        }

        @Bean
        public PlatformTransactionManager transactionManager(DataSource dataSource)
        {
            return new DataSourceTransactionManager(dataSource);
        }
    }

    @BeforeEach
    void prepareTable() throws Exception
    {
        LoginUser loginUser = new LoginUser();
        loginUser.setUserId(OPERATOR_ID);
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(loginUser, null));

        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement())
        {
            statement.execute("DROP TABLE IF EXISTS t_course");
            statement.execute("CREATE TABLE t_course (" //
                    + "id bigint NOT NULL," //
                    + "name varchar(64) NOT NULL," //
                    + "type tinyint NOT NULL," //
                    + "difficulty tinyint NOT NULL DEFAULT 1," //
                    + "cover_url varchar(255) DEFAULT NULL," //
                    + "intro text," //
                    + "duration_min smallint DEFAULT NULL," //
                    + "sort_no int NOT NULL DEFAULT 0," //
                    + "status tinyint NOT NULL DEFAULT 1," //
                    + "deleted tinyint NOT NULL DEFAULT 0," //
                    + "create_by bigint DEFAULT NULL," //
                    + "create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP," //
                    + "update_by bigint DEFAULT NULL," //
                    + "update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP," //
                    + "PRIMARY KEY (id))");
        }
    }

    @Test
    @DisplayName("新增：主键由 MyBatis-Plus 生成（雪花ID），审计字段由 MetaObjectHandler 填充")
    void insert_shouldGenerateSnowflakeIdAndFillAuditFields()
    {
        CourseDO course = new CourseDO();
        course.setName("哈他瑜伽");
        course.setType(1);
        course.setDifficulty(2);
        course.setSortNo(10);
        course.setStatus(1);
        course.setDeleted(0);

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
    @DisplayName("详情：deleted = 1 的记录查不到（@TableLogic 生效）")
    void selectById_shouldIgnoreDeletedRows()
    {
        CourseDO course = new CourseDO();
        course.setName("已删除课程");
        course.setType(1);
        course.setDifficulty(1);
        course.setSortNo(0);
        course.setStatus(1);
        course.setDeleted(1);
        courseDao.insert(course);

        assertNull(courseDao.selectById(course.getId()), "逻辑删除的记录不应被查出");
    }

    @Test
    @DisplayName("列表：未删除记录参与分页，且排序稳定（sort_no 升序、id 降序）")
    void selectPage_shouldReturnNotDeletedRowsInStableOrder()
    {
        insert("流瑜伽", 2, 20);
        insert("哈他瑜伽", 1, 10);
        CourseDO deleted = new CourseDO();
        deleted.setName("已删除课程");
        deleted.setType(1);
        deleted.setDifficulty(1);
        deleted.setSortNo(1);
        deleted.setStatus(1);
        deleted.setDeleted(1);
        courseDao.insert(deleted);

        CourseQuery query = new CourseQuery();
        List<CourseDO> records = courseDao.selectPage(query).getRecords();

        assertEquals(2, records.size(), "逻辑删除的记录不应出现在列表里");
        assertEquals("哈他瑜伽", records.get(0).getName(), "sort_no 小的排前面");
        assertEquals("流瑜伽", records.get(1).getName());
    }

    @Test
    @DisplayName("状态更新：写入更新人与更新时间，未删除记录命中 1 行")
    void updateStatus_shouldWriteOperatorAndTime()
    {
        CourseDO course = insert("哈他瑜伽", 1, 10);

        int rows = courseDao.updateStatus(course.getId(), 0, OPERATOR_ID);

        assertEquals(1, rows);
        CourseDO updated = courseDao.selectById(course.getId());
        assertEquals(Integer.valueOf(0), updated.getStatus());
        assertEquals(OPERATOR_ID, updated.getUpdateBy());
        assertNotNull(updated.getUpdateTime());
    }

    @Test
    @DisplayName("全量更新：传 null 的字段被真正清空")
    void updateById_shouldClearNullFields()
    {
        CourseDO course = new CourseDO();
        course.setName("哈他瑜伽");
        course.setType(1);
        course.setDifficulty(2);
        course.setCoverUrl("https://cdn.example.com/course/hata.jpg");
        course.setIntro("课程介绍");
        course.setDurationMin(60);
        course.setSortNo(10);
        course.setStatus(1);
        course.setDeleted(0);
        courseDao.insert(course);

        CourseDO update = new CourseDO();
        update.setId(course.getId());
        update.setName("哈他瑜伽（初级）");
        update.setType(1);
        update.setDifficulty(3);
        update.setCoverUrl(null);
        update.setIntro(null);
        update.setDurationMin(null);
        update.setSortNo(20);
        update.setUpdateBy(OPERATOR_ID);
        int rows = courseDao.updateById(update);

        assertEquals(1, rows);
        CourseDO updated = courseDao.selectById(course.getId());
        assertEquals("哈他瑜伽（初级）", updated.getName());
        assertEquals(Integer.valueOf(3), updated.getDifficulty());
        assertEquals(Integer.valueOf(20), updated.getSortNo());
        assertNull(updated.getCoverUrl(), "封面图应被清空");
        assertNull(updated.getIntro(), "课程介绍应被清空");
        assertNull(updated.getDurationMin(), "单节时长应被清空");
        assertEquals(OPERATOR_ID, updated.getUpdateBy());
    }

    private CourseDO insert(String name, int type, int sortNo)
    {
        CourseDO course = new CourseDO();
        course.setName(name);
        course.setType(type);
        course.setDifficulty(1);
        course.setSortNo(sortNo);
        course.setStatus(1);
        course.setDeleted(0);
        courseDao.insert(course);
        return course;
    }
}
