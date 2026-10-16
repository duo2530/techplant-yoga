package com.techplant.yoga.membercard.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.common.core.domain.model.LoginUser;
import com.ruoyi.framework.web.exception.GlobalExceptionHandler;
import com.techplant.yoga.common.config.JacksonConfig;
import com.techplant.yoga.common.testcontainers.AbstractYogaIntegrationTest;
import com.techplant.yoga.common.testcontainers.YogaMyBatisTestConfig;
import com.techplant.yoga.common.testcontainers.YogaSchema;
import com.techplant.yoga.member.dao.MemberDaoImpl;
import com.techplant.yoga.member.dao.MemberLevelDictDaoImpl;
import com.techplant.yoga.member.security.MemberLoginUser;
import com.techplant.yoga.member.service.impl.MemberServiceImpl;
import com.techplant.yoga.membercard.dao.MemberCardDaoImpl;
import com.techplant.yoga.membercard.service.CardNoGenerator;
import com.techplant.yoga.membercard.service.MemberCardService;
import com.techplant.yoga.membercard.service.impl.MemberCardServiceImpl;
import com.techplant.yoga.store.dao.StoreDaoImpl;
import com.techplant.yoga.store.dao.StoreRegionDictDaoImpl;
import com.techplant.yoga.store.service.impl.StoreServiceImpl;

/**
 * 用户端「我的会员卡」接口的 HTTP 层集成测试（MockMvc ＋ Testcontainers MySQL，service／DAO 都是真实实现）。
 *
 * <p>覆盖统一响应、分页参数校验、ID 字符串化、身份隔离、展示状态判定与关联对象缺失的文案兜底；
 * 安全过滤链与 Redis 不在本测试范围内，登录身份直接写入 Spring Security 上下文。</p>
 */
@ContextConfiguration(classes = {YogaMyBatisTestConfig.class, MemberCardDaoImpl.class, MemberDaoImpl.class,
    MemberLevelDictDaoImpl.class, MemberServiceImpl.class, StoreDaoImpl.class, StoreRegionDictDaoImpl.class,
    StoreServiceImpl.class, MemberCardServiceImpl.class, CardNoGenerator.class})
@DisplayName("MemberCardPublicController 接口测试（MockMvc：身份隔离 / 分页 / 状态判定 / 字段分流）")
class MemberCardPublicControllerTest extends AbstractYogaIntegrationTest {
    private static final Long MEMBER_ID = 1856739201475235902L;

    /** 另一位会员：他的卡不出现在当前会员的结果里 */
    private static final Long OTHER_MEMBER_ID = 1856739201475235909L;

    /** 没有会员卡的会员：用于空列表用例 */
    private static final Long NO_CARD_MEMBER_ID = 1856739201475235919L;

    /** 开卡门店：徐汇店 */
    private static final Long OPEN_STORE_ID = 1856739201475235901L;

    /** 适用门店：浦东店 */
    private static final Long STORE_ID = 1856739201475235903L;

    /** 不存在的适用门店（模拟门店已被删除） */
    private static final Long DELETED_STORE_ID = 999999999L;

    private static final String REGION_CODE = "310104";

    @Autowired
    private MemberCardService memberCardService;

    private MockMvc mockMvc;

    private JdbcTemplate jdbcTemplate;

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        YogaSchema.reset(dataSource, "t_member_card", "t_store");
        jdbcTemplate = new JdbcTemplate(dataSource);
        insertStore(OPEN_STORE_ID, "0001", "徐汇店");
        insertStore(STORE_ID, "0003", "浦东店");
        insertCards();
        bindMember(MEMBER_ID);

        // 与运行环境一致：业务 ID 序列化成字符串，异常统一由全局处理器装配
        objectMapper = Jackson2ObjectMapperBuilder.json().modulesToInstall(new JacksonConfig.BusinessIdToStringModule())
                .build();
        mockMvc = MockMvcBuilders.standaloneSetup(new MemberCardPublicController(memberCardService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper)).build();
    }

    @Test
    @DisplayName("身份隔离：管理端身份（非会员）请求 → 业务码 401")
    void list_withAdminPrincipal_shouldReturnUnauthorized() throws Exception {
        LoginUser admin = new LoginUser();
        admin.setUserId(OPERATOR_ID);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(admin, null));

        mockMvc.perform(get("/api/member-cards").param("pageNum", "1").param("pageSize", "10"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(401))
                .andExpect(jsonPath("$.msg").value("登录状态已失效，请重新登录"));
    }

    @Test
    @DisplayName("分页：缺页码／缺每页条数 → 500；页码 0、每页 101 → 500")
    void list_withInvalidPaging_shouldReject() throws Exception {
        String missingPageNum = performMessage(get("/api/member-cards").param("pageSize", "10"));
        assertEquals("页码不能为空", missingPageNum);

        String missingPageSize = performMessage(get("/api/member-cards").param("pageNum", "1"));
        assertEquals("每页条数不能为空", missingPageSize);

        String zeroPageNum = performMessage(get("/api/member-cards").param("pageNum", "0").param("pageSize", "10"));
        assertEquals("页码不能小于 1", zeroPageNum);

        String tooMany = performMessage(get("/api/member-cards").param("pageNum", "1").param("pageSize", "101"));
        assertEquals("每页条数不能超过 100", tooMany);
    }

    @Test
    @DisplayName("列表：只返回本人卡（按 id 倒序），分页响应为 {total,rows,code,msg}，ID 序列化为字符串")
    void list_shouldReturnOwnCardsInPagingShape() throws Exception {
        JsonNode body = list(1, 10);

        assertEquals(200, body.get("code").asInt());
        assertEquals("查询成功", body.get("msg").asText());
        assertEquals(9, body.get("total").asInt(), "另一位会员的卡不计入 total");
        JsonNode rows = body.get("rows");
        assertEquals(9, rows.size());
        assertEquals("1109", rows.get(0).get("id").asText(), "按 id 倒序：最新发卡在前");
        assertEquals("1101", rows.get(8).get("id").asText());
        assertTrue(rows.get(0).get("id").isTextual(), "雪花 ID 必须序列化为字符串");
        assertTrue(rows.get(0).get("storeId").isTextual());

        // 管理端字段与状态字段都不出现在用户端出参里
        assertJsonNull(rows.get(0).get("memberId"));
        assertJsonNull(rows.get(0).get("stopReason"));
        assertJsonNull(rows.get(0).get("createTime"));
    }

    @Test
    @DisplayName("展示状态：停卡 → 未开卡 → 过期 → 已用尽 → 正常，且状态文案成对返回")
    void list_shouldDerivePublicStatus() throws Exception {
        JsonNode rows = list(1, 10).get("rows");

        assertStatus(rows, "1109", 4, "停卡", "停卡优先于未开卡");
        assertStatus(rows, "1107", 1, "未开卡", "未开卡的次数卡");
        assertStatus(rows, "1106", 4, "停卡", "已开卡后停卡");
        assertStatus(rows, "1105", 2, "正常", "有效期内的年卡");
        assertStatus(rows, "1104", 3, "过期", "有效期止早于当前时间");
        assertStatus(rows, "1103", 5, "已用尽", "剩余次数为 0 的次数卡");
        assertStatus(rows, "1102", 2, "正常", "有效期内的次数卡");
        assertStatus(rows, "1101", 1, "未开卡", "未开卡的年卡");
    }

    @Test
    @DisplayName("字段分流：次数卡给次数与总次数，年卡给剩余天数并全部门店通用")
    void list_shouldSplitTimesCardAndAnnualCardFields() throws Exception {
        JsonNode rows = list(1, 10).get("rows");

        JsonNode pendingTimes = row(rows, "1107");
        assertEquals("次数卡", pendingTimes.get("cardTypeText").asText());
        assertEquals("次数卡", pendingTimes.get("cardCategoryText").asText());
        assertEquals("团课类", pendingTimes.get("courseScopeName").asText());
        assertEquals(1, pendingTimes.get("purchaseTimes").asInt());
        assertEquals(14, pendingTimes.get("giftTimes").asInt());
        assertEquals(15, pendingTimes.get("totalTimes").asInt());
        assertEquals(15, pendingTimes.get("remainingTimes").asInt());
        assertJsonNull(pendingTimes.get("remainingDays"));
        assertJsonNull(pendingTimes.get("activateTime"));
        assertJsonNull(pendingTimes.get("validStartTime"));
        assertJsonNull(pendingTimes.get("validEndTime"));
        assertEquals("徐汇店", pendingTimes.get("storeName").asText());
        assertEquals("徐汇店", pendingTimes.get("storeScopeText").asText());

        JsonNode annual = row(rows, "1105");
        assertEquals("年卡", annual.get("cardTypeText").asText());
        assertEquals("期限卡", annual.get("cardCategoryText").asText());
        assertJsonNull(annual.get("purchaseTimes"));
        assertJsonNull(annual.get("giftTimes"));
        assertJsonNull(annual.get("totalTimes"));
        assertJsonNull(annual.get("remainingTimes"));
        assertJsonNull(annual.get("storeId"));
        assertJsonNull(annual.get("storeName"));
        assertEquals("全部门店通用", annual.get("storeScopeText").asText());
        assertEquals(10, annual.get("remainingDays").asInt(), "剩余天数含今天");
        assertTrue(annual.get("validStartTime").isTextual());

        JsonNode stopped = row(rows, "1106");
        assertEquals(101, stopped.get("remainingDays").asInt(), "停卡不改变剩余天数的计算口径");
    }

    @Test
    @DisplayName("适用门店已删除：门店名与门店 ID 保留判定，文案给「适用门店已删除」")
    void list_withDeletedStore_shouldFallbackText() throws Exception {
        JsonNode row = row(list(1, 10).get("rows"), "1108");

        assertEquals(String.valueOf(DELETED_STORE_ID), row.get("storeId").asText());
        assertJsonNull(row.get("storeName"));
        assertEquals("适用门店已删除", row.get("storeScopeText").asText());
    }

    @Test
    @DisplayName("分页边界：页码超出范围 → 空 rows 且 total 不变；每页 1 条时按 id 倒序翻页")
    void list_withPagingBoundary() throws Exception {
        JsonNode beyond = list(9, 10);
        assertEquals(200, beyond.get("code").asInt());
        assertEquals(9, beyond.get("total").asInt());
        assertEquals(0, beyond.get("rows").size());

        JsonNode firstPage = list(1, 1);
        assertEquals("1109", firstPage.get("rows").get(0).get("id").asText());
        JsonNode secondPage = list(2, 1);
        assertEquals("1108", secondPage.get("rows").get(0).get("id").asText());
    }

    @Test
    @DisplayName("无卡会员：成功返回空列表，total 为 0")
    void list_withoutCards_shouldReturnEmptyRows() throws Exception {
        bindMember(NO_CARD_MEMBER_ID);

        JsonNode body = list(1, 10);

        assertEquals(200, body.get("code").asInt());
        assertEquals(0, body.get("total").asInt());
        assertEquals(0, body.get("rows").size());
    }

    /**
     * 夹具卡片：id 1101~1109（倒序返回 1109→1102 属当前会员，1108 属他人）。
     *
     * <p>用 id 编码用例语义：1101 未开卡年卡、1102 正常次数卡、1103 已用尽、1104 过期、
     * 1105 正常年卡、1106 停卡年卡、1107 未开卡次数卡、1108 适用门店已删除、1109 未开卡即停卡。</p>
     */
    private void insertCards() {
        LocalDateTime now = LocalDateTime.now(CardNoGenerator.CARD_ZONE);
        LocalDate today = now.toLocalDate();

        // 1101 年卡：未开卡
        insertCard(1101L, MEMBER_ID, 2, null, null, null, null, null, null, null, "未开卡年卡");
        // 1102 次数卡：已开卡且在有效期内
        insertCard(1102L, MEMBER_ID, 1, STORE_ID, 10, 2, 5, today.minusDays(1).atTime(10, 0), null,
                endOfDay(today.plusDays(30)), "正常次数卡");
        // 1103 次数卡：剩余次数为 0
        insertCard(1103L, MEMBER_ID, 1, OPEN_STORE_ID, 10, 0, 0, today.minusDays(1).atTime(10, 0), null,
                endOfDay(today.plusDays(30)), "已用尽次数卡");
        // 1104 次数卡：有效期止已过，剩余次数不为 0
        insertCard(1104L, MEMBER_ID, 1, OPEN_STORE_ID, 10, 0, 3, today.minusDays(40).atTime(10, 0), null,
                endOfDay(today.minusDays(1)), "过期次数卡");
        // 1105 年卡：有效期内，剩余天数含今天
        insertCard(1105L, MEMBER_ID, 2, null, null, null, null, today.minusDays(1).atTime(10, 0), null,
                endOfDay(today.plusDays(9)), "正常年卡");
        // 1106 年卡：已停卡，剩余天数照常计算
        insertCard(1106L, MEMBER_ID, 2, null, null, null, null, today.minusDays(2).atTime(10, 0), now.minusHours(1),
                endOfDay(today.plusDays(100)), "停卡年卡");
        // 1107 次数卡：未开卡，赠送 14 次
        insertCard(1107L, MEMBER_ID, 1, OPEN_STORE_ID, 1, 14, 15, null, null, null, "未开卡次数卡");
        // 1108 次数卡：适用门店已被删除
        insertCard(1108L, MEMBER_ID, 1, DELETED_STORE_ID, 10, 0, 4, today.minusDays(1).atTime(10, 0), null,
                endOfDay(today.plusDays(30)), "门店缺失次数卡");
        // 1109 次数卡：未开卡即停卡，停卡优先
        insertCard(1109L, MEMBER_ID, 1, OPEN_STORE_ID, 10, 0, 10, null, now.minusHours(2), null, "未开卡即停卡");
        // 1100 别的会员的卡：不出现在当前会员结果里
        insertCard(1100L, OTHER_MEMBER_ID, 1, OPEN_STORE_ID, 10, 0, 10, null, null, null, "他人次数卡");
    }

    private JsonNode list(int pageNum, int pageSize) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/member-cards").param("pageNum", String.valueOf(pageNum))
                .param("pageSize", String.valueOf(pageSize))).andExpect(status().isOk()).andReturn();
        String body = new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
        return objectMapper.readTree(body);
    }

    private JsonNode row(JsonNode rows, String id) {
        for (JsonNode node : rows) {
            if (id.equals(node.get("id").asText())) {
                return node;
            }
        }
        throw new AssertionError("响应里没有 id=" + id + " 的会员卡");
    }

    /** JSON 字段允许两种空：字段缺失（Java null）与显式 null（NullNode） */
    private void assertJsonNull(JsonNode node) {
        assertTrue(node == null || node.isNull(), "期望 null，实际：" + node);
    }

    private void assertStatus(JsonNode rows, String id, int status, String statusText, String message) {
        JsonNode row = row(rows, id);
        assertEquals(status, row.get("status").asInt(), message);
        assertEquals(statusText, row.get("statusText").asText(), message);
    }

    /** 取统一响应里的 msg，供缺失字段顺序不确定的用例断言（MockMvc 响应未声明字符集，按 UTF-8 解码） */
    private String performMessage(MockHttpServletRequestBuilder request) throws Exception {
        MvcResult result = mockMvc.perform(request).andExpect(status().isOk()).andExpect(jsonPath("$.code").value(500))
                .andReturn();
        String body = new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
        return objectMapper.readTree(body).get("msg").asText();
    }

    private void bindMember(Long memberId) {
        MemberLoginUser loginUser = new MemberLoginUser();
        loginUser.setMemberId(memberId);
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(loginUser, null, Collections.emptyList()));
    }

    private void insertStore(Long id, String storeNo, String name) {
        jdbcTemplate.update(
                "INSERT INTO t_store (id, store_no, name, store_type, region_code, phone, business_hours,"
                        + " address, image_url, create_by, create_time, update_by, update_time) VALUES (?, ?, ?, 1, ?,"
                        + " '021-12345678', '周一至周日 09:00-22:00', '漕溪北路 88 号', NULL, 1, NOW(), 1, NOW())",
                id, storeNo, name, REGION_CODE);
    }

    private void insertCard(Long id, Long memberId, int cardType, Long storeId, Integer purchaseTimes,
            Integer giftTimes, Integer remainingTimes, LocalDateTime activateTime, LocalDateTime stopTime,
            LocalDateTime validEndTime, String cardName) {
        jdbcTemplate.update("INSERT INTO t_member_card (id, member_id, open_store_id, card_no, card_name, card_type,"
                + " course_scope, store_id, purchase_times, gift_times, remaining_times, valid_period_count,"
                + " valid_start_time, valid_end_time, activate_time, stop_time, create_by, create_time, update_by,"
                + " update_time) VALUES (?, ?, ?, ?, ?, ?, 1, ?, ?, ?, ?, 12, ?, ?, ?, ?, 1, NOW(), 1, NOW())", id,
                memberId, OPEN_STORE_ID, cardNo(id), cardName, cardType, storeId, purchaseTimes, giftTimes,
                remainingTimes, timestamp(activateTime == null ? null : activateTime.toLocalDate().atStartOfDay()),
                timestamp(validEndTime), timestamp(activateTime), timestamp(stopTime));
    }

    /** 18 位卡号：测试用固定前缀 + 递增序号，与生成规则无关，只用于断言卡号是字符串 */
    private String cardNo(Long id) {
        return "20261014" + String.format("%010d", id);
    }

    private LocalDateTime endOfDay(LocalDate date) {
        return date.atTime(23, 59, 59);
    }

    private Timestamp timestamp(LocalDateTime value) {
        return value == null ? null : Timestamp.valueOf(value);
    }
}
