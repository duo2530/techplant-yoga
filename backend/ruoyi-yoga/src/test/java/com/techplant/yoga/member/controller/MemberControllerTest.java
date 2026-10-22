package com.techplant.yoga.member.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.framework.web.exception.GlobalExceptionHandler;
import com.techplant.yoga.common.config.JacksonConfig;
import com.techplant.yoga.common.testcontainers.AbstractYogaIntegrationTest;
import com.techplant.yoga.common.testcontainers.YogaMyBatisTestConfig;
import com.techplant.yoga.common.testcontainers.YogaSchema;
import com.techplant.yoga.member.dao.MemberDao;
import com.techplant.yoga.member.dao.MemberDaoImpl;
import com.techplant.yoga.member.dao.MemberLevelDictDaoImpl;
import com.techplant.yoga.member.domain.MemberDO;
import com.techplant.yoga.member.dto.MemberCreateDTO;
import com.techplant.yoga.member.service.MemberService;
import com.techplant.yoga.member.service.impl.MemberServiceImpl;

/**
 * 管理端会员接口的 HTTP 层集成测试（MockMvc ＋ Testcontainers MySQL，service／DAO 都是真实实现）。
 *
 * <p>覆盖统一响应、参数校验、ID 字符串化与失败分支；安全过滤链与 Redis 不在本测试范围内。</p>
 */
@ContextConfiguration(classes = {YogaMyBatisTestConfig.class, MemberDaoImpl.class, MemberLevelDictDaoImpl.class,
    MemberServiceImpl.class})
@DisplayName("MemberController 接口测试（MockMvc：分页参数必填 / 统一响应 / ID 字符串化 / null 与缺省）")
class MemberControllerTest extends AbstractYogaIntegrationTest {
    private static final String PHONE = "13800138000";

    private static final String[] LEVEL_LABELS = {"V0 普通会员", "V1 忻会员", "V2 银会员", "V3 金会员", "V4 黑金会员"};

    @Autowired
    private MemberService memberService;

    @Autowired
    private MemberDao memberDao;

    private MockMvc mockMvc;

    private JdbcTemplate jdbcTemplate;

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        YogaSchema.reset(dataSource, "t_member", "sys_dict_data");
        jdbcTemplate = new JdbcTemplate(dataSource);
        for (int level = 0; level < LEVEL_LABELS.length; level++) {
            jdbcTemplate.update("INSERT INTO sys_dict_data (dict_sort, dict_label, dict_value, dict_type, css_class,"
                    + " list_class, is_default, status, create_by, create_time) VALUES (?, ?, ?, 'member_level', '',"
                    + " 'default', 'N', '0', 'admin', NOW())", level + 1, LEVEL_LABELS[level], String.valueOf(level));
        }

        // 与运行环境一致：业务 ID 序列化成字符串，异常统一由全局处理器装配
        objectMapper = Jackson2ObjectMapperBuilder.json().modulesToInstall(new JacksonConfig.BusinessIdToStringModule())
                .build();
        mockMvc = MockMvcBuilders.standaloneSetup(new MemberController(memberService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper)).build();
    }

    @Test
    @DisplayName("列表：未传分页参数 → 业务码 400（pageNum 与 pageSize 都必填）")
    void list_withoutPagingParams_shouldReject() throws Exception {
        String message = performMessage(get("/admin/members"));

        assertTrue("页码不能为空".equals(message) || "每页条数不能为空".equals(message), "缺失分页提示语应为页码或每页条数：" + message);
    }

    @Test
    @DisplayName("列表：只传 pageNum 或只传 pageSize → 业务码 400")
    void list_withPartialPagingParams_shouldReject() throws Exception {
        mockMvc.perform(get("/admin/members").param("pageNum", "1")).andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400)).andExpect(jsonPath("$.msg").value("每页条数不能为空"));

        mockMvc.perform(get("/admin/members").param("pageSize", "10")).andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400)).andExpect(jsonPath("$.msg").value("页码不能为空"));
    }

    @Test
    @DisplayName("列表：分页合法时返回 TableDataInfo（rows/total/code/msg），ID 序列化为字符串")
    void list_withPagingParams_shouldReturnRows() throws Exception {
        memberService.create(createDTO(PHONE));

        mockMvc.perform(get("/admin/members").param("pageNum", "1").param("pageSize", "10")).andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200)).andExpect(jsonPath("$.msg").value("查询成功"))
                .andExpect(jsonPath("$.total").value(1)).andExpect(jsonPath("$.rows[0].id").isString())
                .andExpect(jsonPath("$.rows[0].phone").value(PHONE))
                .andExpect(jsonPath("$.rows[0].name").value("忻会员8000"))
                .andExpect(jsonPath("$.rows[0].genderText").value("未知"))
                .andExpect(jsonPath("$.rows[0].levelText").value("V0 普通会员"))
                .andExpect(jsonPath("$.rows[0].registered").value(false))
                .andExpect(jsonPath("$.rows[0].password").doesNotExist());
    }

    @Test
    @DisplayName("列表：phone_last4 下划线参数精确筛选；非法后四位 → 400")
    void list_withPhoneLast4Param() throws Exception {
        memberService.create(createDTO(PHONE));

        mockMvc.perform(
                get("/admin/members").param("pageNum", "1").param("pageSize", "10").param("phone_last4", "8000"))
                .andExpect(jsonPath("$.total").value(1));

        mockMvc.perform(get("/admin/members").param("pageNum", "1").param("pageSize", "10").param("phone_last4", "12"))
                .andExpect(jsonPath("$.code").value(400)).andExpect(jsonPath("$.msg").value("手机号后4位必须是 4 位数字"));
    }

    @Test
    @DisplayName("详情：不存在 → 404；memberId 格式非法 → 400")
    void detail_shouldReportNotFoundAndBadRequest() throws Exception {
        mockMvc.perform(get("/admin/members/999999999")).andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.msg").value("会员不存在或已被删除"));

        mockMvc.perform(get("/admin/members/abc")).andExpect(jsonPath("$.code").value(400));
    }

    @Test
    @DisplayName("新增：手机号已建档 → 409；等级不在字典 → 400；缺省等级取 0")
    void create_shouldReportConflictAndDefaultLevel() throws Exception {
        memberService.create(createDTO(PHONE));

        mockMvc.perform(
                post("/admin/members").contentType(MediaType.APPLICATION_JSON).content("{\"phone\":\"" + PHONE + "\"}"))
                .andExpect(jsonPath("$.code").value(409)).andExpect(jsonPath("$.msg").value("该手机号已建立会员档案"));

        mockMvc.perform(post("/admin/members").contentType(MediaType.APPLICATION_JSON)
                .content("{\"phone\":\"13900139000\",\"level\":5}")).andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.msg").value("会员等级取值非法"));

        mockMvc.perform(post("/admin/members").contentType(MediaType.APPLICATION_JSON)
                .content("{\"phone\":\"13900139001\",\"level\":3}")).andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.msg").value("操作成功")).andExpect(jsonPath("$.data").doesNotExist());

        MemberDO created = memberDao.selectByPhone("13900139001");
        assertEquals(Integer.valueOf(3), created.getLevel(), "新档等级应为提交的 3");
    }

    @Test
    @DisplayName("修改：省略字段保留原值，显式 null → 400，忽略 phone/name/registered")
    void update_shouldDistinguishMissingAndNull() throws Exception {
        memberService.create(createDTO(PHONE));
        Long memberId = memberDao.selectByPhone(PHONE).getId();

        // 省略 level 与 phone/name/registered 无关字段：只改性别
        mockMvc.perform(put("/admin/members/" + memberId).contentType(MediaType.APPLICATION_JSON)
                .content("{\"gender\":2,\"phone\":\"13911111111\",\"name\":\"篡改名\",\"registered\":true}"))
                .andExpect(jsonPath("$.code").value(200)).andExpect(jsonPath("$.data.id").isString())
                .andExpect(jsonPath("$.data.gender").value(2)).andExpect(jsonPath("$.data.level").value(0))
                .andExpect(jsonPath("$.data.phone").value(PHONE)).andExpect(jsonPath("$.data.name").value("忻会员8000"));

        mockMvc.perform(
                put("/admin/members/" + memberId).contentType(MediaType.APPLICATION_JSON).content("{\"gender\":null}"))
                .andExpect(jsonPath("$.code").value(400)).andExpect(jsonPath("$.msg").value("性别不能为 null，若要保留原值请省略该字段"));

        // 空对象：两个字段都省略，返回当前详情且不报错
        mockMvc.perform(put("/admin/members/" + memberId).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(jsonPath("$.code").value(200)).andExpect(jsonPath("$.data.gender").value(2))
                .andExpect(jsonPath("$.data.level").value(0));
    }

    @Test
    @DisplayName("请求体缺失、JSON 损坏或字段类型错误返回 400，且不建档")
    void create_withUnreadableBody_shouldReturnBadRequest() throws Exception {
        for (String body : new String[]{"", "{", "{\"phone\":\"13900139000\",\"gender\":\"abc\"}"}) {
            mockMvc.perform(post("/admin/members").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(400))
                    .andExpect(jsonPath("$.msg").value("请求体不能为空或格式非法"));
        }
        assertEquals(0, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM t_member", Integer.class).intValue());
    }

    @Test
    @DisplayName("会员等级字典缺失属于服务端配置错误，返回 500，且不建档")
    void create_withoutLevelDictionary_shouldReturnInternalError() throws Exception {
        jdbcTemplate.update("DELETE FROM sys_dict_data WHERE dict_type = 'member_level'");
        mockMvc.perform(
                post("/admin/members").contentType(MediaType.APPLICATION_JSON).content("{\"phone\":\"13900139000\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.msg").value("会员等级字典未配置，无法保存会员等级"));
        assertEquals(0, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM t_member", Integer.class).intValue());
    }

    private MemberCreateDTO createDTO(String phone) {
        MemberCreateDTO dto = new MemberCreateDTO();
        dto.setPhone(phone);
        return dto;
    }

    /**
     * 取统一响应里的 msg，供缺失字段顺序不确定的用例断言（MockMvc 响应未声明字符集，按 UTF-8 解码）
     */
    private String performMessage(MockHttpServletRequestBuilder request) throws Exception {
        MvcResult result = mockMvc.perform(request).andExpect(status().isOk()).andExpect(jsonPath("$.code").value(400))
                .andReturn();
        String body = new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
        return objectMapper.readTree(body).get("msg").asText();
    }
}
