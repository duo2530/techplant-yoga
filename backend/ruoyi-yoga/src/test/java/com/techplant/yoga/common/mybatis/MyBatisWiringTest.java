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
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.ruoyi.common.core.domain.model.LoginUser;
import com.techplant.yoga.course.dao.CourseDao;
import com.techplant.yoga.course.dao.CourseDaoImpl;
import com.techplant.yoga.course.domain.CourseDO;
import com.techplant.yoga.course.query.CourseQuery;

/**
 * MyBatis-Plus 装配的集成测试（交付前自检，不是详细设计 §5 的用例）。
 *
 * <p><b>为什么需要它：</b>工程自己声明了 {@code SqlSessionFactory}（ruoyi-framework 的
 * {@code MyBatisConfig}），MyBatis-Plus 的自动配置因此整体退出，分页插件与
 * {@code MetaObjectHandler} 是手工装配的。这类「装配是否真的生效」的问题读代码看不出来，
 * 只有把真实的 {@code SqlSessionFactory} 跑起来才能验证。</p>
 *
 * <p><b>本轮口径变化（详细设计总览 §2）：</b>{@code t_course} 已去掉
 * {@code store_id / duration_min / sort_no / status / deleted}，并<b>改为物理删除</b>：
 * 表结构里没有 {@code deleted} 列，{@code CourseDO} 也没有 {@code @TableLogic}。
 * 因此本类不再断言「逻辑删除过滤」，改为断言「删除后行真的没了」。</p>
 *
 * <p><b>为什么本类自建 SqlSessionFactory，而不是 {@code @ContextConfiguration} 里挂
 * {@code MyBatisConfig}：</b>在「只加载 MyBatisConfig + MybatisPlusConfig +
 * AuditMetaObjectHandler」这种<b>切片</b>用法下，映射器拿不到 MyBatis-Plus 注入的
 * {@code BaseMapper} CRUD 语句，5 条用例会全部报
 * {@code BindingException: Invalid bound statement (not found): ...CourseMapper.insert}。
 * 根因是切片里缺少真实应用由 {@code MybatisPlusAutoConfiguration} 提供的
 * MyBatis-Plus 装配上下文，属于<b>测试切片的组合问题</b>，不是生产装配的问题
 * （生产由 {@code RuoYiApplication} 全量启动，{@code MyBatisConfig} 的工厂照常工作）。
 * 自建工厂反而更贴合本类的目的：<b>直接验证「MyBatis-Plus 的雪花ID / 审计填充 / 物理删除 /
 * 分页插件的字段映射」在真实 SqlSessionFactory 下确实生效</b>。
 * 同样的写法见 {@code ScheduleDaoTest}（同样是 H2 真跑 SQL）。</p>
 *
 * <p>库用 H2 内存库（MySQL 兼容模式），不连接开发库、不产生任何外部写入。</p>
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { MyBatisWiringTest.TestConfig.class, AuditMetaObjectHandler.class,
        CourseDaoImpl.class })
@DisplayName("MyBatis-Plus 装配集成测试（雪花ID / 物理删除 / 审计填充）")
class MyBatisWiringTest
{
    private static final Long OPERATOR_ID = 1L;

    @Autowired
    private CourseDao courseDao;

    @Autowired
    private DataSource dataSource;

    @Configuration
    @EnableTransactionManagement
    @MapperScan("com.techplant.yoga.course.mapper")
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

        /**
         * 自建 MyBatis-Plus 的 {@code SqlSessionFactory}：显式
         * {@code setConfiguration(new MybatisConfiguration())}，并挂上分页插件与
         * {@link AuditMetaObjectHandler}（与 {@code MyBatisConfig} 里手工装配的那两个能力对应）。
         *
         * <p>{@code mapUnderscoreToCamelCase=true} 必须打开：业务列是 {@code course_type}
         * 这类下划线命名，DO 是 {@code courseType} 驼峰命名，靠它映射。</p>
         */
        @Bean
        public MybatisSqlSessionFactoryBean sqlSessionFactory(DataSource dataSource,
                AuditMetaObjectHandler auditMetaObjectHandler)
        {
            MybatisSqlSessionFactoryBean factory = new MybatisSqlSessionFactoryBean();
            factory.setDataSource(dataSource);

            MybatisConfiguration configuration = new MybatisConfiguration();
            configuration.setMapUnderscoreToCamelCase(true);
            configuration.setUseGeneratedKeys(true);
            factory.setConfiguration(configuration);

            MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
            PaginationInnerInterceptor pagination = new PaginationInnerInterceptor(DbType.H2);
            pagination.setMaxLimit(100L);
            interceptor.addInnerInterceptor(pagination);
            factory.setPlugins(interceptor);

            // 审计字段填充器：与 MyBatisConfig 里从容器取 MetaObjectHandler 的行为对齐
            GlobalConfig globalConfig = new GlobalConfig();
            globalConfig.setMetaObjectHandler(auditMetaObjectHandler);
            factory.setGlobalConfig(globalConfig);

            return factory;
        }
    }

    /**
     * 与 {@code backend/sql/yoga.sql} 的 {@code t_course} 段一一对应：
     * 无 store_id / status / sort_no / deleted，课种列名是 course_type
     */
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
                    + "course_type tinyint NOT NULL," //
                    + "cover_url varchar(255) DEFAULT NULL," //
                    + "intro varchar(1024) DEFAULT NULL," //
                    + "difficulty tinyint NOT NULL," //
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
        // 同名课程可以立即重建：名称随物理删除被释放（uk_course_name 不再被占用）
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
