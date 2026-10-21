package com.techplant.yoga.coach.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.techplant.yoga.common.testcontainers.AbstractYogaHttpIntegrationTest;
import com.techplant.yoga.common.testcontainers.YogaSchema;

/**
 * 用户端教练公开接口的 HTTP 集成测试：真实安全过滤链（游客身份）＋ 真实 MySQL。
 *
 * <p>覆盖全量金牌不截断、普通教练排除、字符串 ID、稳定排序、相册顺序、空列表、删除后 404、
 * 非法 ID 400 与公开字段边界。</p>
 */
@DisplayName("用户端教练公开接口（真实安全链：金牌全量 / 相册 / 隐私 / 404 / 400）")
class CoachPublicControllerHttpTest extends AbstractYogaHttpIntegrationTest {
    /** 超过管理端单页上限 100，用于验证公开接口不截断 */
    private static final int GOLD_COACH_COUNT = 101;

    private static final long BASE_COACH_ID = 1856739201475237000L;

    private static final ObjectMapper JSON = new ObjectMapper();

    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void resetTables() {
        YogaSchema.reset(dataSource, "t_coach");
        jdbcTemplate = new JdbcTemplate(dataSource);
    }

    @Test
    @DisplayName("金牌列表：101 位金牌全量返回、普通教练排除、id DESC 稳定排序、ID 为字符串")
    void featured_shouldReturnAllGoldCoaches() throws Exception {
        for (int index = 1; index <= GOLD_COACH_COUNT; index++) {
            insertCoach(BASE_COACH_ID + index, "金牌教练" + index, 2, null, "简介" + index);
        }
        insertCoach(BASE_COACH_ID + 500, "普通教练甲", 1, null, "普通简介");

        JsonNode data = getData("/api/coaches/featured");

        assertTrue(data.isArray());
        assertEquals(GOLD_COACH_COUNT, data.size(), "公开接口不按单页上限截断，金奖全部返回");
        for (int index = 0; index < data.size(); index++) {
            JsonNode item = data.get(index);
            assertEquals(BASE_COACH_ID + GOLD_COACH_COUNT - index, item.get("id").asLong(), "按 id DESC 稳定排序");
            assertTrue(item.get("id").isTextual(), "雪花 ID 以字符串返回");
            assertEquals("金牌教练" + (GOLD_COACH_COUNT - index), item.get("name").asText());
        }
        for (JsonNode item : data) {
            assertFalse("普通教练甲".equals(item.get("name").asText()), "普通教练不得出现在金牌列表");
            assertFalse(item.has("phone"), "不得返回联系电话");
            assertFalse(item.has("level"), "公开列表不返回等级字段");
            assertFalse(item.has("createTime"), "不得返回审计时间");
            assertFalse(item.has("updateTime"), "不得返回审计时间");
        }
    }

    @Test
    @DisplayName("金牌列表：没有金牌教练时返回空数组，不返回模拟数据")
    void featured_withoutGoldCoach_shouldReturnEmptyArray() throws Exception {
        insertCoach(BASE_COACH_ID + 1, "普通教练甲", 1, null, null);

        JsonNode data = getData("/api/coaches/featured");

        assertTrue(data.isArray());
        assertEquals(0, data.size());
    }

    @Test
    @DisplayName("教练详情：返回公开字段与相册原顺序；取消金牌后仍可访问；不返回 phone 与审计字段")
    void detail_shouldReturnPublicFieldsInGalleryOrder() throws Exception {
        long coachId = BASE_COACH_ID + 7;
        insertCoach(coachId, "林教练", 1, "https://cdn.example.com/coach/avatar.jpg", "专注基础体式");
        jdbcTemplate.update("UPDATE t_coach SET gallery = ? WHERE id = ?",
                "[\"https://cdn.example.com/coach/album-3.jpg\",\"https://cdn.example.com/coach/album-1.jpg\","
                        + "\"https://cdn.example.com/coach/album-2.jpg\"]",
                coachId);

        JsonNode data = getData("/api/coaches/" + coachId);

        assertTrue(data.get("id").isTextual(), "雪花 ID 以字符串返回");
        assertEquals(String.valueOf(coachId), data.get("id").asText());
        assertEquals("林教练", data.get("name").asText());
        assertEquals("https://cdn.example.com/coach/avatar.jpg", data.get("avatarUrl").asText());
        assertEquals("专注基础体式", data.get("intro").asText());
        assertTrue(data.get("gallery").isArray());
        assertEquals(3, data.get("gallery").size());
        assertEquals("https://cdn.example.com/coach/album-3.jpg", data.get("gallery").get(0).asText());
        assertEquals("https://cdn.example.com/coach/album-1.jpg", data.get("gallery").get(1).asText());
        assertEquals("https://cdn.example.com/coach/album-2.jpg", data.get("gallery").get(2).asText());
        assertFalse(data.has("phone"), "不得返回联系电话");
        assertFalse(data.has("createTime"), "不得返回审计时间");
        assertFalse(data.has("updateTime"), "不得返回审计时间");
    }

    @Test
    @DisplayName("教练详情：空相册返回空数组；教练被删除后返回 404")
    void detail_emptyGalleryAndDeletedCoach() throws Exception {
        long coachId = BASE_COACH_ID + 8;
        insertCoach(coachId, "周教练", 2, null, null);

        JsonNode data = getData("/api/coaches/" + coachId);
        assertTrue(data.get("gallery").isArray());
        assertEquals(0, data.get("gallery").size());

        jdbcTemplate.update("DELETE FROM t_coach WHERE id = ?", coachId);

        mockMvc.perform(get("/api/coaches/" + coachId)).andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(404)).andExpect(jsonPath("$.msg").value("教练不存在或已被删除"));
    }

    @Test
    @DisplayName("教练详情：ID 格式非法 → 业务码 400")
    void detail_invalidId_shouldReturnBadRequest() throws Exception {
        mockMvc.perform(get("/api/coaches/abc")).andExpect(status().isOk()).andExpect(jsonPath("$.code").value(400));
    }

    private JsonNode getData(String path) throws Exception {
        MvcResult result = mockMvc.perform(get(path)).andExpect(status().isOk()).andReturn();
        String body = new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
        JsonNode root = JSON.readTree(body);
        assertEquals(200, root.get("code").asInt(), "业务码应为 200：" + body);
        return root.get("data");
    }

    private void insertCoach(long id, String name, int level, String avatarUrl, String intro) {
        jdbcTemplate.update("INSERT INTO t_coach (id, name, avatar_url, intro, phone, gallery, level) VALUES"
                + " (?, ?, ?, ?, '13800000001', NULL, ?)", id, name, avatarUrl, intro, level);
    }
}
