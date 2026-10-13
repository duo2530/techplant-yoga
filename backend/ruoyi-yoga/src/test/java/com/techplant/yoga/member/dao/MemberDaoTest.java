package com.techplant.yoga.member.dao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.context.ContextConfiguration;
import com.techplant.yoga.common.testcontainers.AbstractYogaIntegrationTest;
import com.techplant.yoga.common.testcontainers.YogaMyBatisTestConfig;
import com.techplant.yoga.common.testcontainers.YogaSchema;
import com.techplant.yoga.member.domain.MemberDO;

/** 会员数据访问的集成测试（Testcontainers MySQL，表结构取自 backend/sql/yoga.sql，不连开发库）。 */
@ContextConfiguration(classes = { YogaMyBatisTestConfig.class, MemberDaoImpl.class })
@DisplayName("MemberDao 集成测试（Testcontainers MySQL：列映射 / 审计 / 条件更新）")
class MemberDaoTest extends AbstractYogaIntegrationTest
{
    private static final String PHONE = "13800138000";

    private static final String HASH_1 = "$2a$10$firstHashValueForTest0000000000000000000000000000000000";

    private static final String HASH_2 = "$2a$10$secondHashValueForTest000000000000000000000000000000000";

    @Autowired
    private MemberDao memberDao;

    @BeforeEach
    void resetTable()
    {
        YogaSchema.reset(dataSource, "t_member");
    }

    @Test
    @DisplayName("新增：雪花 ID ＋ 审计填充；未注册会员 password 为空")
    void insert_shouldGenerateSnowflakeIdAndFillAuditFields()
    {
        MemberDO member = newMember(PHONE, null);

        int rows = memberDao.insert(member);

        assertEquals(1, rows);
        assertNotNull(member.getId(), "主键应由 IdType.ASSIGN_ID 生成");
        assertTrue(String.valueOf(member.getId()).length() >= 18, "雪花ID 应为 18~19 位：" + member.getId());
        assertEquals(OPERATOR_ID, member.getCreateBy(), "创建人应由 MetaObjectHandler 填充");
        assertNotNull(member.getCreateTime(), "创建时间应由 MetaObjectHandler 填充");

        MemberDO found = memberDao.selectByPhone(PHONE);
        assertNotNull(found);
        assertEquals(PHONE, found.getPhone());
        assertEquals("8000", found.getPhoneLast4(), "phone_last4 应按下划线→驼峰映射");
        assertEquals("忻会员8000", found.getName());
        assertEquals(Integer.valueOf(0), found.getGender());
        assertEquals(Integer.valueOf(0), found.getLevel());
        assertNull(found.getPassword());
        assertFalse(found.isRegistered());
        assertFalse(memberDao.existsRegisteredById(member.getId()));
    }

    @Test
    @DisplayName("新增：手机号重复 → DuplicateKeyException（上层转 409 的兜底）")
    void insert_withDuplicatePhone_shouldThrowDuplicateKey()
    {
        memberDao.insert(newMember(PHONE, null));

        assertThrows(DuplicateKeyException.class, () -> memberDao.insert(newMember(PHONE, null)));
    }

    @Test
    @DisplayName("补齐密码：仅当 password IS NULL 时生效一次，之后按已注册处理")
    void updatePasswordIfUnregistered_shouldFillOnlyOnce()
    {
        MemberDO member = newMember(PHONE, null);
        memberDao.insert(member);

        assertEquals(1, memberDao.updatePasswordIfUnregistered(member.getId(), HASH_1));
        assertTrue(memberDao.existsRegisteredById(member.getId()));
        assertEquals(HASH_1, memberDao.selectByPhone(PHONE).getPassword());

        // 第二次补：条件 password IS NULL 不成立 → 影响行数 0，密码不被覆盖
        assertEquals(0, memberDao.updatePasswordIfUnregistered(member.getId(), HASH_2));
        assertEquals(HASH_1, memberDao.selectByPhone(PHONE).getPassword());
    }

    @Test
    @DisplayName("忘记密码：覆盖哈希；刷新最后登录时间只动这两列")
    void updatePasswordAndLastLoginTime_shouldWork()
    {
        MemberDO member = newMember(PHONE, HASH_1);
        memberDao.insert(member);

        assertEquals(1, memberDao.updatePassword(member.getId(), HASH_2));
        LocalDateTime loginTime = LocalDateTime.of(2026, 10, 12, 20, 30, 0);
        assertEquals(1, memberDao.updateLastLoginTime(member.getId(), loginTime));

        MemberDO found = memberDao.selectById(member.getId());
        assertEquals(HASH_2, found.getPassword());
        assertEquals(loginTime, found.getLastLoginTime());
        // 未被误改的档案字段
        assertEquals("忻会员8000", found.getName());
        assertEquals("8000", found.getPhoneLast4());
    }

    @Test
    @DisplayName("查询：按手机号查不到返回 null；按 ID 查不到返回 null（不抛异常）")
    void select_withUnknownKey_shouldReturnNull()
    {
        assertNull(memberDao.selectByPhone("13900139000"));
        assertNull(memberDao.selectByPhone(""));
        assertNull(memberDao.selectById(999L));
        assertNull(memberDao.selectById(null));
        assertFalse(memberDao.existsRegisteredById(null));
    }

    private MemberDO newMember(String phone, String passwordHash)
    {
        MemberDO member = new MemberDO();
        member.setPhone(phone);
        member.setPassword(passwordHash);
        member.setPhoneLast4(phone.substring(phone.length() - 4));
        member.setName("忻会员" + phone.substring(phone.length() - 4));
        member.setGender(0);
        member.setLevel(0);
        return member;
    }
}
