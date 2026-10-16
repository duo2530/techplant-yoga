package com.techplant.yoga.member.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import com.ruoyi.common.exception.ServiceException;
import com.techplant.yoga.common.response.PageResult;
import com.techplant.yoga.common.testcontainers.AbstractYogaIntegrationTest;
import com.techplant.yoga.common.testcontainers.YogaMyBatisTestConfig;
import com.techplant.yoga.common.testcontainers.YogaSchema;
import com.techplant.yoga.member.dao.MemberDao;
import com.techplant.yoga.member.dao.MemberDaoImpl;
import com.techplant.yoga.member.dao.MemberLevelDictDaoImpl;
import com.techplant.yoga.member.domain.MemberDO;
import com.techplant.yoga.member.dto.MemberCreateDTO;
import com.techplant.yoga.member.dto.MemberUpdateDTO;
import com.techplant.yoga.member.query.MemberQuery;
import com.techplant.yoga.member.service.impl.MemberServiceImpl;
import com.techplant.yoga.member.vo.MemberDetailVO;
import com.techplant.yoga.member.vo.MemberListItemVO;

/**
 * 会员管理业务的集成测试（Testcontainers MySQL，表结构取自 backend/sql，不连开发库）。
 *
 * <p>会员等级的取值与名称来自字典 {@code member_level}，用例自行准备字典夹具。</p>
 */
@ContextConfiguration(classes = {YogaMyBatisTestConfig.class, MemberDaoImpl.class, MemberLevelDictDaoImpl.class,
    MemberServiceImpl.class})
@DisplayName("MemberService 集成测试（Testcontainers MySQL：建档 / 等级字典 / 修改字段语义 / 并发删除 / 删除）")
class MemberServiceImplTest extends AbstractYogaIntegrationTest {
    private static final String PHONE = "13800138000";

    private static final String HASH = "$2a$10$memberManageHashValueForTest000000000000000000000000000000";

    /** 字典中存在的等级：0~4 */
    private static final String[] LEVEL_LABELS = {"V0 普通会员", "V1 忻会员", "V2 银会员", "V3 金会员", "V4 黑金会员"};

    /** 字典中状态为停用的等级码 */
    private static final int DISABLED_LEVEL = 9;

    @Autowired
    private MemberService memberService;

    @Autowired
    private MemberDao memberDao;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private JdbcTemplate jdbcTemplate;

    private TransactionTemplate transactionTemplate;

    @BeforeEach
    void resetTable() {
        YogaSchema.reset(dataSource, "t_member", "sys_dict_data");
        jdbcTemplate = new JdbcTemplate(dataSource);
        transactionTemplate = new TransactionTemplate(transactionManager);
        for (int level = 0; level < LEVEL_LABELS.length; level++) {
            insertLevelDict(level + 1, String.valueOf(level), LEVEL_LABELS[level], "0");
        }
        insertLevelDict(DISABLED_LEVEL + 1, String.valueOf(DISABLED_LEVEL), "V9 已停用等级", "1");
    }

    @Test
    @DisplayName("新增：显示名与后四位由系统生成，性别与等级缺省取 0，不写登录凭证")
    void create_shouldGenerateProfileFields() {
        memberService.create(createDTO(PHONE, null, null));

        MemberDO found = memberDao.selectByPhone(PHONE);
        assertNotNull(found);
        assertNotNull(found.getId());
        assertEquals("8000", found.getPhoneLast4());
        assertEquals("忻会员8000", found.getName());
        assertEquals(Integer.valueOf(0), found.getGender());
        assertEquals(Integer.valueOf(0), found.getLevel());
        assertNull(found.getPassword());
        assertFalse(found.isRegistered());
        assertEquals(OPERATOR_ID, found.getCreateBy());

        MemberDetailVO detail = memberService.getById(found.getId());
        assertEquals("未知", detail.getGenderText());
        assertEquals("V0 普通会员", detail.getLevelText(), "等级名称取自字典 member_level");
    }

    @Test
    @DisplayName("新增：按提交的性别与等级建档；手机号已建档 → 409 且不覆盖原档案")
    void create_withSubmittedGenderAndLevel() {
        memberService.create(createDTO(PHONE, 2, 3));
        MemberDO found = memberDao.selectByPhone(PHONE);
        assertEquals(Integer.valueOf(2), found.getGender());
        assertEquals(Integer.valueOf(3), found.getLevel());
        assertEquals("V3 金会员", memberService.getById(found.getId()).getLevelText());

        ServiceException conflict = assertThrows(ServiceException.class,
                () -> memberService.create(createDTO(PHONE, 1, 0)));
        assertEquals(Integer.valueOf(409), conflict.getCode());
        assertEquals("该手机号已建立会员档案", conflict.getMessage());
        assertEquals(Integer.valueOf(3), memberDao.selectByPhone(PHONE).getLevel());
    }

    @Test
    @DisplayName("新增：性别取值非法、等级不在字典（含已停用）→ 500，且不落库")
    void create_withInvalidGenderOrLevel_shouldFail() {
        ServiceException invalidGender = assertThrows(ServiceException.class,
                () -> memberService.create(createDTO(PHONE, 3, null)));
        assertEquals(Integer.valueOf(500), invalidGender.getCode());

        ServiceException unknownLevel = assertThrows(ServiceException.class,
                () -> memberService.create(createDTO(PHONE, 0, 5)));
        assertEquals(Integer.valueOf(500), unknownLevel.getCode());
        assertEquals("会员等级取值非法", unknownLevel.getMessage());

        ServiceException disabledLevel = assertThrows(ServiceException.class,
                () -> memberService.create(createDTO(PHONE, 0, DISABLED_LEVEL)));
        assertEquals(Integer.valueOf(500), disabledLevel.getCode());

        assertNull(memberDao.selectByPhone(PHONE));
    }

    @Test
    @DisplayName("新增：等级字典没有配置时拒绝建档，提示字典未配置")
    void create_withoutLevelDict_shouldFail() {
        jdbcTemplate.update("DELETE FROM sys_dict_data WHERE dict_type = 'member_level'");

        ServiceException exception = assertThrows(ServiceException.class,
                () -> memberService.create(createDTO(PHONE, 0, null)));
        assertEquals(Integer.valueOf(500), exception.getCode());
        assertEquals("会员等级字典未配置，无法保存会员等级", exception.getMessage());
        assertNull(memberDao.selectByPhone(PHONE));
    }

    @Test
    @DisplayName("列表与详情：级别码名成对返回、registered 由密码推导；等级筛选按字典校验")
    void pageAndDetail_shouldTranslateEnumAndRegistered() {
        memberService.create(createDTO("13800138001", 1, 4));
        memberService.create(createDTO("13800138002", null, 0));
        Long registeredId = memberDao.selectByPhone("13800138001").getId();
        memberService.registerByPhone("13800138001", HASH);

        PageResult<MemberListItemVO> page = memberService.page(queryByLevel(4));
        assertEquals(Long.valueOf(1), page.getTotal());
        assertEquals("V4 黑金会员", page.getList().get(0).getLevelText());
        assertEquals("男", page.getList().get(0).getGenderText());

        ServiceException unknownLevel = assertThrows(ServiceException.class, () -> memberService.page(queryByLevel(5)));
        assertEquals(Integer.valueOf(500), unknownLevel.getCode());
        assertEquals("会员等级取值非法", unknownLevel.getMessage());

        PageResult<MemberListItemVO> all = memberService.page(queryByName("会员800"));
        assertEquals(Long.valueOf(2), all.getTotal(), "按会员 ID 降序");
        assertEquals("忻会员8002", all.getList().get(0).getName());
        assertFalse(all.getList().get(0).getRegistered());
        assertEquals("V0 普通会员", all.getList().get(0).getLevelText());
        MemberListItemVO registered = all.getList().get(1);
        assertEquals(registeredId, registered.getId());
        assertTrue(registered.getRegistered());

        MemberDetailVO detail = memberService.getById(registeredId);
        assertEquals("13800138001", detail.getPhone());
        assertEquals("V4 黑金会员", detail.getLevelText());
        assertTrue(detail.getRegistered());

        ServiceException notFound = assertThrows(ServiceException.class, () -> memberService.getById(999999L));
        assertEquals(Integer.valueOf(404), notFound.getCode());
        assertEquals("会员不存在或已被删除", notFound.getMessage());
    }

    @Test
    @DisplayName("修改：省略字段保留原值；等级按字典校验；提交 null → 500；不存在 → 404")
    void update_shouldOnlyApplySubmittedFields() {
        memberService.create(createDTO(PHONE, 1, 0));
        Long memberId = memberDao.selectByPhone(PHONE).getId();

        MemberUpdateDTO onlyLevel = new MemberUpdateDTO();
        onlyLevel.setLevel(2);
        MemberDetailVO leveled = memberService.update(memberId, onlyLevel);
        assertEquals(Integer.valueOf(2), leveled.getLevel());
        assertEquals("V2 银会员", leveled.getLevelText());
        assertEquals(Integer.valueOf(1), leveled.getGender(), "省略的性别应保留原值");

        MemberUpdateDTO onlyGender = new MemberUpdateDTO();
        onlyGender.setGender(2);
        MemberDetailVO changed = memberService.update(memberId, onlyGender);
        assertEquals(Integer.valueOf(2), changed.getGender());
        assertEquals("女", changed.getGenderText());
        assertEquals(Integer.valueOf(2), changed.getLevel(), "省略的等级应保留原值");
        assertEquals("忻会员8000", changed.getName(), "显示名不随修改变化");
        assertEquals(PHONE, changed.getPhone(), "手机号不随修改变化");

        assertEquals(OPERATOR_ID, memberDao.selectById(memberId).getUpdateBy());

        MemberUpdateDTO nullGender = new MemberUpdateDTO();
        nullGender.setGender(null);
        assertEquals(Integer.valueOf(500),
                assertThrows(ServiceException.class, () -> memberService.update(memberId, nullGender)).getCode());

        MemberUpdateDTO unknownLevel = new MemberUpdateDTO();
        unknownLevel.setLevel(5);
        assertEquals(Integer.valueOf(500),
                assertThrows(ServiceException.class, () -> memberService.update(memberId, unknownLevel)).getCode());

        MemberUpdateDTO disabledLevel = new MemberUpdateDTO();
        disabledLevel.setLevel(DISABLED_LEVEL);
        assertEquals(Integer.valueOf(500),
                assertThrows(ServiceException.class, () -> memberService.update(memberId, disabledLevel)).getCode());

        MemberUpdateDTO missingMember = new MemberUpdateDTO();
        missingMember.setGender(1);
        assertEquals(Integer.valueOf(404),
                assertThrows(ServiceException.class, () -> memberService.update(999999L, missingMember)).getCode());

        MemberDO afterFailedUpdates = memberDao.selectById(memberId);
        assertEquals(Integer.valueOf(2), afterFailedUpdates.getGender());
        assertEquals(Integer.valueOf(2), afterFailedUpdates.getLevel());
    }

    @Test
    @DisplayName("修改：会员在事务快照建立后被并发删除 → 404，不返回旧值")
    void update_afterConcurrentDelete_shouldReportNotFound() {
        memberService.create(createDTO(PHONE, 1, 0));
        final Long memberId = memberDao.selectByPhone(PHONE).getId();

        MemberUpdateDTO dto = new MemberUpdateDTO();
        dto.setGender(2);

        final ServiceException[] failure = new ServiceException[1];
        transactionTemplate.execute(status -> {
            // 事务内第一次读建立快照；随后另一连接删除并提交，模拟并发删除
            assertNotNull(memberDao.selectById(memberId));
            deleteOnSeparateConnection(memberId);

            try {
                memberService.update(memberId, dto);
            } catch (ServiceException e) {
                failure[0] = e;
            }
            status.setRollbackOnly();
            return null;
        });

        assertNotNull(failure[0], "并发删除后修改不应返回成功");
        assertEquals(Integer.valueOf(404), failure[0].getCode());
        assertEquals("会员不存在或已被删除", failure[0].getMessage());
        assertNull(memberDao.selectById(memberId));
        assertNull(memberDao.selectByPhone(PHONE));
    }

    @Test
    @DisplayName("删除：会员在事务快照建立后被并发删除 → 404")
    void delete_afterConcurrentDelete_shouldReportNotFound() {
        memberService.create(createDTO(PHONE, 1, 0));
        final Long memberId = memberDao.selectByPhone(PHONE).getId();

        final ServiceException[] failure = new ServiceException[1];
        transactionTemplate.execute(status -> {
            assertNotNull(memberDao.selectById(memberId));
            deleteOnSeparateConnection(memberId);

            try {
                memberService.delete(memberId);
            } catch (ServiceException e) {
                failure[0] = e;
            }
            status.setRollbackOnly();
            return null;
        });

        assertNotNull(failure[0], "并发删除后再次删除不应返回成功");
        assertEquals(Integer.valueOf(404), failure[0].getCode());
    }

    @Test
    @DisplayName("删除：会员行与登录凭证一并删除，再删 → 404")
    void delete_shouldRemoveRowAndCredentials() {
        memberService.create(createDTO(PHONE, 1, 0));
        Long memberId = memberDao.selectByPhone(PHONE).getId();
        memberService.registerByPhone(PHONE, HASH);
        assertTrue(memberService.existsById(memberId));

        memberService.delete(memberId);

        assertNull(memberDao.selectById(memberId));
        assertNull(memberDao.selectByPhone(PHONE));
        assertFalse(memberService.existsById(memberId));
        assertFalse(memberService.isRegistered(memberId));

        ServiceException notFound = assertThrows(ServiceException.class, () -> memberService.delete(memberId));
        assertEquals(Integer.valueOf(404), notFound.getCode());
    }

    private MemberCreateDTO createDTO(String phone, Integer gender, Integer level) {
        MemberCreateDTO dto = new MemberCreateDTO();
        dto.setPhone(phone);
        dto.setGender(gender);
        dto.setLevel(level);
        return dto;
    }

    private MemberQuery queryByName(String name) {
        MemberQuery query = query(10);
        query.setName(name);
        return query;
    }

    private MemberQuery queryByLevel(Integer level) {
        MemberQuery query = query(10);
        query.setLevel(level);
        return query;
    }

    private MemberQuery query(int pageSize) {
        MemberQuery query = new MemberQuery();
        query.setPageNum(1);
        query.setPageSize(pageSize);
        return query;
    }

    private void insertLevelDict(int dictSort, String value, String label, String status) {
        jdbcTemplate.update("INSERT INTO sys_dict_data (dict_sort, dict_label, dict_value, dict_type, css_class,"
                + " list_class, is_default, status, create_by, create_time) VALUES (?, ?, ?, 'member_level', '',"
                + " 'default', 'N', ?, 'admin', NOW())", dictSort, label, value, status);
    }

    /**
     * 用独立连接删除会员并提交：不走当前事务，模拟并发删除（快照读仍能看到旧行）
     */
    private void deleteOnSeparateConnection(Long memberId) {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement("DELETE FROM t_member WHERE id = ?")) {
            statement.setLong(1, memberId);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("并发删除夹具执行失败", e);
        }
    }
}
