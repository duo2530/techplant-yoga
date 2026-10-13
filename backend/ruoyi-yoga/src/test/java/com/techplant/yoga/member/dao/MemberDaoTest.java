package com.techplant.yoga.member.dao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.Statement;
import java.time.LocalDateTime;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.ruoyi.common.core.domain.model.LoginUser;
import com.techplant.yoga.common.mybatis.AuditMetaObjectHandler;
import com.techplant.yoga.member.domain.MemberDO;

/**
 * 会员数据访问的集成测试（H2 内存库，MySQL 兼容模式；不连开发库）。
 *
 * <p>验证的是「读代码看不出来」的那部分：{@code t_member} 的列名映射
 * （{@code phone_last4} / {@code wechat_openid} 等下划线 → 驼峰）、
 * 雪花 ID 与审计填充、以及<b>条件更新</b>的语义
 * （{@code password IS NULL} 只允许补一次密码、唯一键冲突抛
 * {@link DuplicateKeyException} 供上层转 409）。</p>
 *
 * <p>自建 {@code SqlSessionFactory} 的原因与 {@code MyBatisWiringTest} 一致：切片测试里
 * MyBatis-Plus 的自动配置不参与，必须显式挂分页插件与审计填充器。</p>
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { MemberDaoTest.TestConfig.class, AuditMetaObjectHandler.class,
        MemberDaoImpl.class })
@DisplayName("MemberDao 集成测试（H2：列映射 / 审计 / 条件更新）")
class MemberDaoTest
{
    private static final Long OPERATOR_ID = 1L;

    private static final String PHONE = "13800138000";

    private static final String HASH_1 = "$2a$10$firstHashValueForTest0000000000000000000000000000000000";

    private static final String HASH_2 = "$2a$10$secondHashValueForTest000000000000000000000000000000000";

    @Autowired
    private MemberDao memberDao;

    @Autowired
    private DataSource dataSource;

    @Configuration
    @EnableTransactionManagement
    @MapperScan("com.techplant.yoga.member.mapper")
    static class TestConfig
    {
        @Bean
        public DataSource dataSource()
        {
            return new EmbeddedDatabaseBuilder().setType(EmbeddedDatabaseType.H2)
                    .setName("yoga_member_dao;MODE=MySQL;DATABASE_TO_LOWER=TRUE").build();
        }

        @Bean
        public PlatformTransactionManager transactionManager(DataSource dataSource)
        {
            return new DataSourceTransactionManager(dataSource);
        }

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

            GlobalConfig globalConfig = new GlobalConfig();
            globalConfig.setMetaObjectHandler(auditMetaObjectHandler);
            factory.setGlobalConfig(globalConfig);

            return factory;
        }
    }

    /** 与 backend/sql/yoga.sql 的 t_member 段一一对应（列名、可空性、唯一键） */
    @BeforeEach
    void prepareTable() throws Exception
    {
        LoginUser loginUser = new LoginUser();
        loginUser.setUserId(OPERATOR_ID);
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(loginUser, null));

        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement())
        {
            statement.execute("DROP TABLE IF EXISTS t_member");
            statement.execute("CREATE TABLE t_member (" //
                    + "id bigint NOT NULL," //
                    + "wechat_openid varchar(64) DEFAULT NULL," //
                    + "wechat_unionid varchar(64) DEFAULT NULL," //
                    + "last_login_time datetime DEFAULT NULL," //
                    + "phone varchar(32) NOT NULL," //
                    + "password varchar(100) DEFAULT NULL," //
                    + "phone_last4 varchar(4) NOT NULL," //
                    + "name varchar(64) NOT NULL," //
                    + "gender tinyint NOT NULL DEFAULT 0," //
                    + "level tinyint NOT NULL DEFAULT 0," //
                    + "create_by bigint DEFAULT NULL," //
                    + "create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP," //
                    + "update_by bigint DEFAULT NULL," //
                    + "update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP," //
                    + "PRIMARY KEY (id)," //
                    + "CONSTRAINT uk_member_phone UNIQUE (phone))");
        }
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
