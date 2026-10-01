package com.techplant.yoga.coach.dao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
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
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.techplant.yoga.coach.domain.CoachDO;
import com.techplant.yoga.coach.query.CoachQuery;

/**
 * 教练数据访问的**真实 SQL 口径**集成测试（H2 内存库，MySQL 兼容模式，不连开发库）。
 *
 * <p><b>为什么需要它：</b>教练模块有三条「读代码看不出来」的口径，只有把真实
 * {@code SqlSessionFactory} 与分页插件跑起来、配真表真数据才能证明：</p>
 * <ol>
 *   <li><b>物理删除</b>：本表没有 {@code deleted} 列、DO 没有 {@code @TableLogic}，
 *       {@code deleteById} 必须真的把行 DELETE 掉（不是 UPDATE 打标记）；</li>
 *   <li><b>列表只 select 必要列</b>：{@code selectAdminPage} 不查 {@code intro}/{@code gallery}
 *       两个大字段；</li>
 *   <li><b>相册 JSON 原样承载</b>：DO 的 {@code gallery} 与 {@code json} 列内容一比一
 *       （转换只在 service 层发生，DAO 不做任何加工）。</li>
 * </ol>
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { CoachDaoTest.TestConfig.class, CoachDaoImpl.class })
@DisplayName("CoachDao 真实 SQL 口径集成测试（H2）")
class CoachDaoTest
{
    private static final Long LIN_COACH_ID = 9001L;

    private static final Long ZHOU_COACH_ID = 9002L;

    private static final Long SECOND_LIN_COACH_ID = 9003L;

    /** 允许重名的证明数据：两个同名「林教练」 */
    private static final String LIN_NAME = "林教练";

    @Autowired
    private CoachDao coachDao;

    @Autowired
    private DataSource dataSource;

    @Configuration
    @EnableTransactionManagement
    @MapperScan("com.techplant.yoga.coach.mapper")
    static class TestConfig
    {
        @Bean
        public DataSource dataSource()
        {
            return new EmbeddedDatabaseBuilder().setType(EmbeddedDatabaseType.H2)
                    .setName("yoga_coach_dao;MODE=MySQL;DATABASE_TO_LOWER=TRUE").build();
        }

        @Bean
        public PlatformTransactionManager transactionManager(DataSource dataSource)
        {
            return new DataSourceTransactionManager(dataSource);
        }

        /**
         * 本测试自己装配一个 MyBatis-Plus 的 {@code SqlSessionFactory}（显式
         * {@code setConfiguration(new MybatisConfiguration())}），避免「切片上下文里映射器拿不到
         * BaseMapper CRUD 语句」的坑（{@code backend/AGENTS.md} §8.9 的注意事项，范例
         * {@code ScheduleDaoTest} / {@code MyBatisWiringTest}）。
         */
        @Bean
        public MybatisSqlSessionFactoryBean sqlSessionFactory(DataSource dataSource)
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
            return factory;
        }
    }

    /**
     * 表结构与 {@code backend/sql/yoga.sql} 的 {@code t_coach} 段一一对应：没有 {@code deleted}、
     * 没有 {@code status}/{@code title}，相册列名是 {@code gallery}。
     * 这里把 {@code gallery} 建成 varchar 只是 H2 1.4 没有 json 类型，映射层承载的是同一个字符串。
     */
    @BeforeEach
    void prepareTable() throws Exception
    {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement())
        {
            statement.execute("DROP TABLE IF EXISTS t_coach");
            statement.execute("CREATE TABLE t_coach (" //
                    + "id bigint NOT NULL," //
                    + "name varchar(32) NOT NULL," //
                    + "avatar_url varchar(255) DEFAULT NULL," //
                    + "intro varchar(512) DEFAULT NULL," //
                    + "phone varchar(32) DEFAULT NULL," //
                    + "gallery varchar(2000) DEFAULT NULL," //
                    + "create_by bigint DEFAULT NULL," //
                    + "create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP," //
                    + "update_by bigint DEFAULT NULL," //
                    + "update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP," //
                    + "PRIMARY KEY (id))");
        }
    }

    // ------------------------------------------------------------------
    // 列表：姓名模糊 + id DESC + 不查大字段
    // ------------------------------------------------------------------

    @Test
    @DisplayName("列表：姓名模糊匹配，固定按 id DESC；不传姓名时返回全部")
    void selectAdminPage_shouldFilterByNameAndOrderByIdDesc() throws Exception
    {
        insertCoach(LIN_COACH_ID, LIN_NAME, null, null);
        insertCoach(ZHOU_COACH_ID, "周教练", null, null);
        insertCoach(SECOND_LIN_COACH_ID, LIN_NAME, null, null);

        CoachQuery query = new CoachQuery();
        query.setName("林");
        query.setPageNum(1);
        query.setPageSize(10);

        IPage<CoachDO> page = coachDao.selectAdminPage(query);

        assertEquals(Long.valueOf(2L), page.getTotal(), "两个同名的「林教练」都要命中（允许重名）");
        assertEquals(2, page.getRecords().size());
        assertEquals(Long.valueOf(SECOND_LIN_COACH_ID), page.getRecords().get(0).getId(), "id DESC 稳定排序");
        assertEquals(Long.valueOf(LIN_COACH_ID), page.getRecords().get(1).getId());

        CoachQuery all = new CoachQuery();
        IPage<CoachDO> allPage = coachDao.selectAdminPage(all);
        assertEquals(Long.valueOf(3L), allPage.getTotal(), "不传姓名时不加条件");
    }

    @Test
    @DisplayName("列表 SQL 只 select 必要列：intro 与 gallery 不会被查出来")
    void selectAdminPage_shouldNotSelectIntroAndGallery() throws Exception
    {
        insertCoach(LIN_COACH_ID, LIN_NAME, "十年哈他瑜伽经验", "[\"https://cdn.example.com/coach/a.jpg\"]");

        CoachQuery query = new CoachQuery();
        query.setName(LIN_NAME);

        CoachDO row = coachDao.selectAdminPage(query).getRecords().get(0);

        assertEquals(LIN_NAME, row.getName());
        assertEquals("13800000001", row.getPhone());
        assertNotNull(row.getCreateTime(), "审计时间要在列表里（列表列包含 createTime/updateTime）");
        assertNull(row.getIntro(), "列表不查 intro 大字段");
        assertNull(row.getGallery(), "列表不查 gallery 大字段");
    }

    // ------------------------------------------------------------------
    // 详情：JSON 字符串原样承载
    // ------------------------------------------------------------------

    @Test
    @DisplayName("按主键查询：gallery 的 JSON 内容原样读出（DAO 不做 JSON 加工）")
    void selectById_shouldReturnGalleryJsonAsIs() throws Exception
    {
        String gallery = "[\"https://cdn.example.com/coach/a.jpg\",\"https://cdn.example.com/coach/b.jpg\"]";
        insertCoach(LIN_COACH_ID, LIN_NAME, "十年哈他瑜伽经验", gallery);

        CoachDO coach = coachDao.selectById(LIN_COACH_ID);

        assertNotNull(coach);
        assertEquals(LIN_NAME, coach.getName());
        assertEquals("十年哈他瑜伽经验", coach.getIntro());
        assertEquals(gallery, coach.getGallery(), "DO 里的 gallery 是与列内容一致的 JSON 字符串");
        assertNull(coachDao.selectById(9999L), "不存在返回 null");
    }

    // ------------------------------------------------------------------
    // 物理删除
    // ------------------------------------------------------------------

    @Test
    @DisplayName("删除是物理删除：DELETE 之后行真的没了（不是逻辑删除）")
    void deleteById_shouldPhysicallyRemoveRow() throws Exception
    {
        insertCoach(LIN_COACH_ID, LIN_NAME, null, null);
        assertNotNull(coachDao.selectById(LIN_COACH_ID));

        assertEquals(1, coachDao.deleteById(LIN_COACH_ID));

        assertNull(coachDao.selectById(LIN_COACH_ID), "物理删除后按主键查不到");
        assertEquals(0L, rawCountById(LIN_COACH_ID), "数据库里该行确实不存在（本表没有 deleted 列）");
    }

    // ------------------------------------------------------------------
    // 全量更新：只写业务列
    // ------------------------------------------------------------------

    @Test
    @DisplayName("更新是全量覆盖业务列：name/avatar_url/intro/phone/gallery 都写，create_by 不动")
    void updateById_shouldWriteBusinessColumnsOnly() throws Exception
    {
        insertCoach(LIN_COACH_ID, LIN_NAME, "旧简介", null);

        CoachDO update = new CoachDO();
        update.setId(LIN_COACH_ID);
        update.setName("周教练");
        update.setAvatarUrl("https://cdn.example.com/coach/new-avatar.jpg");
        update.setIntro("新简介");
        update.setPhone("13800000009");
        update.setGallery("[\"https://cdn.example.com/coach/new.jpg\"]");
        update.setUpdateBy(9L);

        assertEquals(1, coachDao.updateById(update));

        CoachDO latest = coachDao.selectById(LIN_COACH_ID);
        assertEquals("周教练", latest.getName());
        assertEquals("https://cdn.example.com/coach/new-avatar.jpg", latest.getAvatarUrl());
        assertEquals("新简介", latest.getIntro());
        assertEquals("13800000009", latest.getPhone());
        assertEquals("[\"https://cdn.example.com/coach/new.jpg\"]", latest.getGallery());
        assertEquals(Long.valueOf(9L), latest.getUpdateBy());
        assertEquals(Long.valueOf(7L), latest.getCreateBy(), "create_by 属于创建审计列，更新不得触碰");
    }

    @Test
    @DisplayName("更新：相册清空（传 null）→ gallery 列写成 NULL")
    void updateById_withNullGallery_shouldClearColumn() throws Exception
    {
        insertCoach(LIN_COACH_ID, LIN_NAME, null, "[\"https://cdn.example.com/coach/a.jpg\"]");

        CoachDO update = new CoachDO();
        update.setId(LIN_COACH_ID);
        update.setName(LIN_NAME);
        update.setGallery(null);
        update.setUpdateBy(9L);

        coachDao.updateById(update);

        assertNull(coachDao.selectById(LIN_COACH_ID).getGallery(), "清空相册要真的把列写成 NULL");
    }

    // ------------------------------------------------------------------
    // 跨模块摘要
    // ------------------------------------------------------------------

    @Test
    @DisplayName("摘要查询：按 ID 集合命中现有行，带 intro、不带 gallery（排课模块补名称头像用）")
    void selectSummaryByIds_shouldReturnIntroWithoutGallery() throws Exception
    {
        insertCoach(LIN_COACH_ID, LIN_NAME, "十年哈他瑜伽经验", "[\"https://cdn.example.com/coach/a.jpg\"]");
        insertCoach(ZHOU_COACH_ID, "周教练", "擅长普拉提", null);

        List<CoachDO> summaries = coachDao.selectSummaryByIds(
                Arrays.asList(LIN_COACH_ID, ZHOU_COACH_ID, 9999L));

        assertEquals(2, summaries.size(), "已被物理删除/不存在的 ID 不会出现在结果里");
        CoachDO first = summaries.get(0);
        assertEquals(Long.valueOf(ZHOU_COACH_ID), first.getId(), "摘要查询同样按 id DESC");
        assertEquals("擅长普拉提", first.getIntro());
        assertNull(first.getGallery(), "摘要不查 gallery");

        assertTrue(coachDao.selectSummaryByIds(Collections.<Long>emptyList()).isEmpty(), "空集合不去查库");
    }

    // ------------------------------------------------------------------
    // 夹具
    // ------------------------------------------------------------------

    /** 直插测试数据（避开审计填充与雪花 ID，断言才稳定） */
    private void insertCoach(Long id, String name, String intro, String gallery) throws Exception
    {
        String sql = "INSERT INTO t_coach (id, name, avatar_url, intro, phone, gallery,"
                + " create_by, create_time, update_by, update_time) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql))
        {
            Timestamp now = Timestamp.valueOf(LocalDateTime.of(2026, 9, 28, 10, 0, 0));
            statement.setLong(1, id);
            statement.setString(2, name);
            statement.setString(3, "https://cdn.example.com/coach/avatar.jpg");
            statement.setString(4, intro);
            statement.setString(5, "13800000001");
            statement.setString(6, gallery);
            statement.setLong(7, 7L);
            statement.setTimestamp(8, now);
            statement.setLong(9, 7L);
            statement.setTimestamp(10, now);
            statement.executeUpdate();
        }
    }

    private long rawCountById(Long id) throws Exception
    {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT COUNT(*) FROM t_coach WHERE id = ?"))
        {
            statement.setLong(1, id);
            try (ResultSet resultSet = statement.executeQuery())
            {
                resultSet.next();
                return resultSet.getLong(1);
            }
        }
    }
}
