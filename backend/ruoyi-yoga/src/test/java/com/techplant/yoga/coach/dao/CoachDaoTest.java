package com.techplant.yoga.coach.dao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.techplant.yoga.coach.domain.CoachDO;
import com.techplant.yoga.coach.query.CoachQuery;
import com.techplant.yoga.common.testcontainers.AbstractYogaIntegrationTest;
import com.techplant.yoga.common.testcontainers.YogaMyBatisTestConfig;
import com.techplant.yoga.common.testcontainers.YogaSchema;

/** 教练数据访问的**真实 SQL 口径**集成测试（Testcontainers MySQL，表结构取自 backend/sql/yoga.sql，不连开发库）。 */
@ContextConfiguration(classes = {YogaMyBatisTestConfig.class, CoachDaoImpl.class})
@DisplayName("CoachDao 集成测试（Testcontainers MySQL：列表 / 详情 / 物理删除 / 全量更新）")
class CoachDaoTest extends AbstractYogaIntegrationTest {
    private static final Long LIN_COACH_ID = 9001L;

    private static final Long ZHOU_COACH_ID = 9002L;

    private static final Long SECOND_LIN_COACH_ID = 9003L;

    /** 允许重名的证明数据：两个同名「林教练」 */
    private static final String LIN_NAME = "林教练";

    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired
    private CoachDao coachDao;

    @BeforeEach
    void resetTable() {
        YogaSchema.reset(dataSource, "t_coach");
    }

    // ------------------------------------------------------------------
    // 列表：姓名模糊 + id DESC + 不查大字段
    // ------------------------------------------------------------------

    @Test
    @DisplayName("列表：姓名模糊匹配，固定按 id DESC；不传姓名时返回全部")
    void selectAdminPage_shouldFilterByNameAndOrderByIdDesc() throws Exception {
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
    void selectAdminPage_shouldNotSelectIntroAndGallery() throws Exception {
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
    void selectById_shouldReturnGalleryJsonAsIs() throws Exception {
        String gallery = "[\"https://cdn.example.com/coach/a.jpg\",\"https://cdn.example.com/coach/b.jpg\"]";
        insertCoach(LIN_COACH_ID, LIN_NAME, "十年哈他瑜伽经验", gallery);

        CoachDO coach = coachDao.selectById(LIN_COACH_ID);

        assertNotNull(coach);
        assertEquals(LIN_NAME, coach.getName());
        assertEquals("十年哈他瑜伽经验", coach.getIntro());
        assertEquals(JSON.readTree(gallery), JSON.readTree(coach.getGallery()),
                "gallery 列按 json 原样承载，MySQL 会按自己的格式返回内容");
        assertNull(coachDao.selectById(9999L), "不存在返回 null");
    }

    // ------------------------------------------------------------------
    // 物理删除
    // ------------------------------------------------------------------

    @Test
    @DisplayName("删除是物理删除：DELETE 之后行真的没了（不是逻辑删除）")
    void deleteById_shouldPhysicallyRemoveRow() throws Exception {
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
    void updateById_shouldWriteBusinessColumnsOnly() throws Exception {
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
        assertEquals(JSON.readTree("[\"https://cdn.example.com/coach/new.jpg\"]"), JSON.readTree(latest.getGallery()));
        assertEquals(Long.valueOf(9L), latest.getUpdateBy());
        assertEquals(Long.valueOf(7L), latest.getCreateBy(), "create_by 属于创建审计列，更新不得触碰");
    }

    @Test
    @DisplayName("更新：相册清空（传 null）→ gallery 列写成 NULL")
    void updateById_withNullGallery_shouldClearColumn() throws Exception {
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
    void selectSummaryByIds_shouldReturnIntroWithoutGallery() throws Exception {
        insertCoach(LIN_COACH_ID, LIN_NAME, "十年哈他瑜伽经验", "[\"https://cdn.example.com/coach/a.jpg\"]");
        insertCoach(ZHOU_COACH_ID, "周教练", "擅长普拉提", null);

        List<CoachDO> summaries = coachDao.selectSummaryByIds(Arrays.asList(LIN_COACH_ID, ZHOU_COACH_ID, 9999L));

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
    private void insertCoach(Long id, String name, String intro, String gallery) throws Exception {
        String sql = "INSERT INTO t_coach (id, name, avatar_url, intro, phone, gallery,"
                + " create_by, create_time, update_by, update_time) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
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

    private long rawCountById(Long id) throws Exception {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection
                        .prepareStatement("SELECT COUNT(*) FROM t_coach WHERE id = ?")) {
            statement.setLong(1, id);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getLong(1);
            }
        }
    }
}
