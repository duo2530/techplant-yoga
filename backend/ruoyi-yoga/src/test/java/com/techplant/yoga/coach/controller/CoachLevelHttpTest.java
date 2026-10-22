package com.techplant.yoga.coach.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.techplant.yoga.common.testcontainers.AbstractYogaHttpIntegrationTest;
import com.techplant.yoga.common.testcontainers.YogaSchema;

/**
 * 管理端教练等级接口的 HTTP 集成测试：真实管理端安全过滤链 ＋ 真实 MySQL。
 *
 * <p>覆盖等级 1／2 设置与取消、非法等级与非法 ID 返回 400、不存在返回 404、重复设置幂等、
 * 审计字段、新增默认普通、资料编辑不覆盖等级，以及游客／会员不可操作，普通管理员可操作。</p>
 */
@DisplayName("管理端教练等级接口（真实安全链：认证 / 400 / 404 / 幂等 / 审计 / 等级隔离）")
class CoachLevelHttpTest extends AbstractYogaHttpIntegrationTest {
    private static final long COACH_ID = 9001L;

    private static final long ADMIN_USER_ID = 100L;

    private static final long OTHER_ADMIN_USER_ID = 101L;

    private static final ObjectMapper JSON = new ObjectMapper();

    private JdbcTemplate jdbcTemplate;

    private String adminToken;

    private String otherAdminToken;

    private String memberToken;

    @BeforeEach
    void setUpData() throws Exception {
        YogaSchema.reset(dataSource, "t_coach", "t_member", "sys_user", "sys_role", "sys_menu", "sys_role_menu",
                "sys_user_role");
        jdbcTemplate = new JdbcTemplate(dataSource);
        prepareAdminFixtures();
        insertCoach(COACH_ID, "林教练", 1, LocalDateTime.of(2026, 1, 1, 0, 0, 0));
        prepareMember();

        adminToken = createAdminToken("gold_admin");
        otherAdminToken = createAdminToken("plain_admin");
        com.techplant.yoga.member.domain.MemberDO member = new com.techplant.yoga.member.domain.MemberDO();
        member.setId(1856739201475235902L);
        member.setPhone("13800138000");
        memberToken = memberTokenService.createToken(member).getToken();
    }

    // ------------------------------------------------------------------
    // 等级设置
    // ------------------------------------------------------------------

    @Test
    @DisplayName("管理员：设为金牌与取消金牌成功，成功返回 {code,msg} 不返回 data")
    void updateLevel_asAdmin_shouldSucceed() throws Exception {
        mockMvc.perform(put("/admin/coaches/" + COACH_ID + "/level").param("level", "2").header(AUTHORIZATION,
                bearer(adminToken))).andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").doesNotExist());
        assertEquals(2, levelOf(COACH_ID));

        mockMvc.perform(put("/admin/coaches/" + COACH_ID + "/level").param("level", "1").header(AUTHORIZATION,
                bearer(adminToken))).andExpect(jsonPath("$.code").value(200));
        assertEquals(1, levelOf(COACH_ID));
    }

    @Test
    @DisplayName("重复设置同一等级幂等成功，等级保持一致")
    void updateLevel_repeated_shouldBeIdempotent() throws Exception {
        for (int round = 0; round < 2; round++) {
            mockMvc.perform(put("/admin/coaches/" + COACH_ID + "/level").param("level", "2").header(AUTHORIZATION,
                    bearer(adminToken))).andExpect(jsonPath("$.code").value(200));
        }
        assertEquals(2, levelOf(COACH_ID));
    }

    @Test
    @DisplayName("等级更新写入 update_by 与 update_time")
    void updateLevel_shouldWriteAuditFields() throws Exception {
        mockMvc.perform(put("/admin/coaches/" + COACH_ID + "/level").param("level", "2").header(AUTHORIZATION,
                bearer(adminToken))).andExpect(jsonPath("$.code").value(200));

        Map<String, Object> row = jdbcTemplate
                .queryForMap("SELECT level, update_by, update_time FROM t_coach WHERE id = ?", COACH_ID);
        assertEquals(2, ((Number) row.get("level")).intValue());
        assertEquals(ADMIN_USER_ID, ((Number) row.get("update_by")).longValue(), "写当前管理员");
        assertNotNull(row.get("update_time"));
        assertEquals(1,
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM t_coach WHERE id = ?" + " AND update_time > '2026-01-01 00:00:00'",
                        Integer.class, COACH_ID).intValue(),
                "更新时间要刷新");
    }

    @Test
    @DisplayName("非法等级：3／0／缺失／非数字都返回业务码 400，等级不变")
    void updateLevel_invalidLevel_shouldReturnBadRequest() throws Exception {
        assertBadRequestWithMessage(put("/admin/coaches/" + COACH_ID + "/level").param("level", "3")
                .header(AUTHORIZATION, bearer(adminToken)), "教练等级取值为 1 或 2");
        assertBadRequestWithMessage(put("/admin/coaches/" + COACH_ID + "/level").param("level", "0")
                .header(AUTHORIZATION, bearer(adminToken)), "教练等级取值为 1 或 2");
        assertBadRequestWithMessage(
                put("/admin/coaches/" + COACH_ID + "/level").header(AUTHORIZATION, bearer(adminToken)),
                "参数[level]不能为空");
        assertBadRequestWithMessage(put("/admin/coaches/" + COACH_ID + "/level").param("level", "abc")
                .header(AUTHORIZATION, bearer(adminToken)), "参数[level]格式非法");
        assertBadRequestWithMessage(put("/admin/coaches/" + COACH_ID + "/level").param("level", "2.5")
                .header(AUTHORIZATION, bearer(adminToken)), "参数[level]格式非法");

        assertEquals(1, levelOf(COACH_ID), "非法请求不得改动等级");
    }

    @Test
    @DisplayName("非法 coachId 返回 400；教练不存在返回 404")
    void updateLevel_invalidOrMissingCoach() throws Exception {
        assertBadRequestWithMessage(
                put("/admin/coaches/abc/level").param("level", "2").header(AUTHORIZATION, bearer(adminToken)),
                "参数[coachId]格式非法");

        mockMvc.perform(
                put("/admin/coaches/999999999/level").param("level", "2").header(AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.msg").value("教练不存在或已被删除"));
    }

    // ------------------------------------------------------------------
    // 权限隔离
    // ------------------------------------------------------------------

    @Test
    @DisplayName("游客：不能修改等级，返回业务码 401")
    void updateLevel_asGuest_shouldBeRejected() throws Exception {
        mockMvc.perform(put("/admin/coaches/" + COACH_ID + "/level").param("level", "2")).andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(401));
        assertEquals(1, levelOf(COACH_ID));
    }

    @Test
    @DisplayName("会员：会员 token 不能修改管理端等级，返回业务码 401")
    void updateLevel_asMember_shouldBeRejected() throws Exception {
        mockMvc.perform(put("/admin/coaches/" + COACH_ID + "/level").param("level", "2").header(AUTHORIZATION,
                bearer(memberToken))).andExpect(status().isOk()).andExpect(jsonPath("$.code").value(401));
        assertEquals(1, levelOf(COACH_ID));
    }

    @Test
    @DisplayName("普通管理员无需等级按钮权限即可设置和取消金牌")
    void updateLevel_asOrdinaryAdmin_shouldSucceed() throws Exception {
        mockMvc.perform(put("/admin/coaches/" + COACH_ID + "/level").param("level", "2").header(AUTHORIZATION,
                bearer(otherAdminToken))).andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));
        assertEquals(2, levelOf(COACH_ID));
        mockMvc.perform(put("/admin/coaches/" + COACH_ID + "/level").param("level", "1").header(AUTHORIZATION,
                bearer(otherAdminToken))).andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));
        assertEquals(1, levelOf(COACH_ID));
    }

    // ------------------------------------------------------------------
    // 等级与资料编辑隔离
    // ------------------------------------------------------------------

    @Test
    @DisplayName("新增教练固定普通等级，审计字段写当前管理员")
    void create_shouldDefaultToNormalLevel() throws Exception {
        mockMvc.perform(post("/admin/coaches").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"新教练\",\"intro\":\"新简介\"}").header(AUTHORIZATION, bearer(adminToken)))
                .andExpect(jsonPath("$.code").value(200));

        Map<String, Object> row = jdbcTemplate.queryForMap("SELECT level, create_by FROM t_coach WHERE name = '新教练'");
        assertEquals(1, ((Number) row.get("level")).intValue(), "新增教练固定普通等级");
        assertEquals(ADMIN_USER_ID, ((Number) row.get("create_by")).longValue());
    }

    @Test
    @DisplayName("资料编辑不覆盖等级：金牌教练改简介后等级仍为 2")
    void updateProfile_shouldNotChangeLevel() throws Exception {
        jdbcTemplate.update("UPDATE t_coach SET level = 2 WHERE id = ?", COACH_ID);

        mockMvc.perform(put("/admin/coaches/" + COACH_ID).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"林教练\",\"intro\":\"更新后的简介\",\"gallery\":[]}")
                .header(AUTHORIZATION, bearer(adminToken))).andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.level").value(2));

        Map<String, Object> row = jdbcTemplate.queryForMap("SELECT level, intro FROM t_coach WHERE id = ?", COACH_ID);
        assertEquals(2, ((Number) row.get("level")).intValue(), "资料编辑不得覆盖等级");
        assertEquals("更新后的简介", row.get("intro"));
    }

    @Test
    @DisplayName("资料编辑请求体夹带 level 时被忽略：等级仍为 2")
    void updateProfile_withLevelInBody_shouldIgnoreLevel() throws Exception {
        jdbcTemplate.update("UPDATE t_coach SET level = 2 WHERE id = ?", COACH_ID);

        mockMvc.perform(put("/admin/coaches/" + COACH_ID).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"林教练\",\"intro\":\"夹带等级的编辑\",\"level\":1,\"gallery\":[]}")
                .header(AUTHORIZATION, bearer(adminToken))).andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.level").value(2));

        assertEquals(2, levelOf(COACH_ID), "请求体里的 level 不生效，等级仍是 2");
    }

    @Test
    @DisplayName("管理列表与详情都返回等级")
    void listAndDetail_shouldReturnLevel() throws Exception {
        jdbcTemplate.update("UPDATE t_coach SET level = 2 WHERE id = ?", COACH_ID);

        mockMvc.perform(get("/admin/coaches").param("pageNum", "1").param("pageSize", "10").header(AUTHORIZATION,
                bearer(adminToken))).andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.rows[0].level").value(2));

        mockMvc.perform(get("/admin/coaches/" + COACH_ID).header(AUTHORIZATION, bearer(adminToken)))
                .andExpect(jsonPath("$.code").value(200)).andExpect(jsonPath("$.data.level").value(2));
    }

    @Test
    @DisplayName("教练管理参数错误统一返回 400，非法资料请求不写入数据库")
    void coachManagement_invalidParameters_shouldReturnBadRequest() throws Exception {
        assertBadRequestWithMessage(get("/admin/coaches/abc").header(AUTHORIZATION, bearer(adminToken)),
                "参数[coachId]格式非法");
        assertBadRequestWithMessage(
                get("/admin/coaches").param("pageNum", "abc").header(AUTHORIZATION, bearer(adminToken)),
                "参数[pageNum]格式非法");
        mockMvc.perform(get("/admin/coaches").param("pageSize", "101").header(AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(400));
        mockMvc.perform(post("/admin/coaches").contentType(MediaType.APPLICATION_JSON).content("{}")
                .header(AUTHORIZATION, bearer(adminToken))).andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
        mockMvc.perform(put("/admin/coaches/" + COACH_ID).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"\",\"intro\":\"非法编辑\"}").header(AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(400));

        assertEquals(1, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM t_coach", Integer.class).intValue());
        assertEquals("旧简介",
                jdbcTemplate.queryForObject("SELECT intro FROM t_coach WHERE id = ?", String.class, COACH_ID));
        assertEquals(1, levelOf(COACH_ID));
    }

    private void createUser(long userId, String userName, long roleId) {
        jdbcTemplate.update("INSERT INTO sys_user (user_id, user_name, nick_name, user_type, email, phonenumber, sex,"
                + " avatar, password, status, del_flag, create_by, create_time) VALUES (?, ?, ?, '00', '', '', '0',"
                + " '', '', '0', '0', 'admin', NOW())", userId, userName, userName);
        jdbcTemplate.update("INSERT INTO sys_user_role (user_id, role_id) VALUES (?, ?)", userId, roleId);
    }

    private void insertCoach(long id, String name, int level, LocalDateTime updateTime) {
        jdbcTemplate.update("INSERT INTO t_coach (id, name, avatar_url, intro, phone, gallery, level, create_by,"
                + " create_time, update_by, update_time) VALUES (?, ?, NULL, '旧简介', '13800000001', NULL, ?, 1,"
                + " '2026-01-01 00:00:00', 1, ?)", id, name, level, Timestamp.valueOf(updateTime));
    }

    private void prepareAdminFixtures() {
        jdbcTemplate.update("INSERT INTO sys_role (role_id, role_name, role_key, role_sort, data_scope,"
                + " menu_check_strictly, dept_check_strictly, status, del_flag, create_by, create_time) VALUES"
                + " (2, '运营管理员', 'operation', 2, '1', 1, 1, '0', '0', 'admin', NOW())");
        jdbcTemplate.update("INSERT INTO sys_role (role_id, role_name, role_key, role_sort, data_scope,"
                + " menu_check_strictly, dept_check_strictly, status, del_flag, create_by, create_time) VALUES"
                + " (3, '普通管理员', 'ordinary', 3, '1', 1, 1, '0', '0', 'admin', NOW())");
        createUser(ADMIN_USER_ID, "gold_admin", 2L);
        createUser(OTHER_ADMIN_USER_ID, "plain_admin", 3L);

        insertMenu(2003L, "教练管理", 2000L, "coach", "C", "");
        jdbcTemplate.update("INSERT INTO sys_role_menu (role_id, menu_id) VALUES (2, 2003), (3, 2003)");
    }

    private void insertMenu(long menuId, String name, long parentId, String path, String menuType, String perms) {
        jdbcTemplate.update("INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query,"
                + " route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time,"
                + " update_by, update_time, remark) VALUES (?, ?, ?, 1, ?, '', '', '', 1, 0, ?, '0', '0', ?, '#',"
                + " 'admin', NOW(), '', NULL, NULL)", menuId, name, parentId, path, menuType, perms);
    }

    private void prepareMember() {
        jdbcTemplate.update("INSERT INTO t_member (id, phone, password, phone_last4, name, gender, level) VALUES"
                + " (1856739201475235902, '13800138000', 'hash', '8000', '忻会员8000', 0, 0)");
    }

    private int levelOf(long coachId) {
        return jdbcTemplate.queryForObject("SELECT level FROM t_coach WHERE id = ?", Integer.class, coachId);
    }

    private void assertBadRequestWithMessage(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request, String expectedMessage)
            throws Exception {
        MvcResult result = mockMvc.perform(request).andExpect(status().isOk()).andReturn();
        String body = new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
        JsonNode response = JSON.readTree(body);
        assertEquals(400, response.get("code").asInt(), "应返回业务码 400：" + body);
        assertEquals(expectedMessage, response.get("msg").asText(), "参数错误提示语：" + body);
    }
}
