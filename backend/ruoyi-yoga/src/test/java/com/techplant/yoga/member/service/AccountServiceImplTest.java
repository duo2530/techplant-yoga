package com.techplant.yoga.member.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import com.ruoyi.common.exception.ServiceException;
import com.techplant.yoga.member.dao.MemberDao;
import com.techplant.yoga.member.domain.MemberDO;
import com.techplant.yoga.member.dto.LoginDTO;
import com.techplant.yoga.member.dto.RegisterDTO;
import com.techplant.yoga.member.dto.ResetPasswordDTO;
import com.techplant.yoga.member.dto.SmsCodeSendDTO;
import com.techplant.yoga.member.service.impl.AccountServiceImpl;
import com.techplant.yoga.member.vo.LoginVO;

/**
 * 用户端账号业务的单元测试（用户端接口详细设计 §4.3～§4.6）。
 *
 * <p>依赖全部用 Mockito 隔离（不连库、不连 Redis）：验证码机制、登录失败口径、
 * 注册的「补齐/新建」分支、忘记密码的 404／409 顺序都在这里锁死。</p>
 */
@DisplayName("AccountServiceImpl 单元测试")
class AccountServiceImplTest
{
    private static final String PHONE = "13800138000";

    private static final String RAW_PASSWORD = "yoga1234";

    private static final String SMS_CODE = "888888";

    private MemberDao memberDao;

    private MemberService memberService;

    private SmsCodeService smsCodeService;

    private MemberTokenService memberTokenService;

    private BCryptPasswordEncoder passwordEncoder;

    private AccountServiceImpl accountService;

    @BeforeEach
    void setUp()
    {
        memberDao = mock(MemberDao.class);
        memberService = mock(MemberService.class);
        smsCodeService = mock(SmsCodeService.class);
        memberTokenService = mock(MemberTokenService.class);
        passwordEncoder = new BCryptPasswordEncoder();
        accountService = new AccountServiceImpl(memberDao, memberService, smsCodeService, memberTokenService,
                passwordEncoder);
    }

    // ------------------------------------------------------------------ 发验证码

    @Test
    @DisplayName("发码：注册场景、手机号未注册 → 交给 SmsCodeService（scene=register）")
    void sendSmsCode_registerSceneOnNewPhone_shouldSend()
    {
        when(memberDao.selectByPhone(PHONE)).thenReturn(null);

        accountService.sendSmsCode(smsCodeSend(PHONE, "register"));

        verify(smsCodeService).send("register", PHONE);
    }

    @Test
    @DisplayName("发码：注册场景、手机号已注册 → 409，不发短信")
    void sendSmsCode_registerSceneOnRegisteredPhone_shouldConflict()
    {
        when(memberDao.selectByPhone(PHONE)).thenReturn(registeredMember());

        ServiceException e = assertThrows(ServiceException.class,
                () -> accountService.sendSmsCode(smsCodeSend(PHONE, "register")));

        assertEquals(409, e.getCode().intValue());
        verifyNoInteractions(smsCodeService);
    }

    @Test
    @DisplayName("发码：重置场景、手机号未注册 → 404「手机号未注册」")
    void sendSmsCode_resetSceneOnNewPhone_shouldNotFound()
    {
        when(memberDao.selectByPhone(PHONE)).thenReturn(null);

        ServiceException e = assertThrows(ServiceException.class,
                () -> accountService.sendSmsCode(smsCodeSend(PHONE, "reset")));

        assertEquals(404, e.getCode().intValue());
        assertEquals("手机号未注册", e.getMessage());
        verifyNoInteractions(smsCodeService);
    }

    @Test
    @DisplayName("发码：重置场景、手机号已注册 → 交给 SmsCodeService（scene=reset）")
    void sendSmsCode_resetSceneOnRegisteredPhone_shouldSend()
    {
        when(memberDao.selectByPhone(PHONE)).thenReturn(registeredMember());

        accountService.sendSmsCode(smsCodeSend(PHONE, "reset"));

        verify(smsCodeService).send("reset", PHONE);
    }

    @Test
    @DisplayName("发码：场景取值非法 → 500，不发短信")
    void sendSmsCode_withInvalidScene_shouldFail()
    {
        ServiceException e = assertThrows(ServiceException.class,
                () -> accountService.sendSmsCode(smsCodeSend(PHONE, "login")));

        assertEquals(500, e.getCode().intValue());
        verifyNoInteractions(smsCodeService);
    }

    @Test
    @DisplayName("发码／登录：手机号为空 → 500「手机号不能为空」（service 直调兜底）")
    void sendSmsCodeAndLogin_withoutPhone_shouldFail()
    {
        ServiceException send = assertThrows(ServiceException.class,
                () -> accountService.sendSmsCode(smsCodeSend(null, "register")));
        assertEquals(500, send.getCode().intValue());
        assertEquals("手机号不能为空", send.getMessage());

        ServiceException login = assertThrows(ServiceException.class,
                () -> accountService.login(login("  ", RAW_PASSWORD)));
        assertEquals(500, login.getCode().intValue());
        assertEquals("手机号不能为空", login.getMessage());

        verifyNoInteractions(smsCodeService, memberTokenService);
    }

    // ------------------------------------------------------------------ 注册

    @Test
    @DisplayName("注册：验证码校验成功 → 先消费验证码，再用 BCrypt 哈希建档，不签发 token")
    void register_shouldConsumeCodeAndStoreBcryptHash()
    {
        accountService.register(register(PHONE, SMS_CODE, RAW_PASSWORD));

        // 注册场景（register）校验并消费验证码
        verify(smsCodeService).verifyAndConsume("register", PHONE, SMS_CODE);
        // 交给 MemberService 的是哈希，不是明文
        verify(memberService).registerByPhone(eq(PHONE), org.mockito.ArgumentMatchers.argThat(
                hash -> hash != null && !RAW_PASSWORD.equals(hash) && passwordEncoder.matches(RAW_PASSWORD, hash)));
        // 注册成功不签发 token（§4.4 第 4 步）
        verifyNoInteractions(memberTokenService);
    }

    @Test
    @DisplayName("注册：验证码错误 → 409 且不建档（保留验证码供重试由 SmsCodeService 负责）")
    void register_whenCodeInvalid_shouldNotCreateMember()
    {
        org.mockito.Mockito.doThrow(new ServiceException("验证码错误", 409))
                .when(smsCodeService).verifyAndConsume("register", PHONE, SMS_CODE);

        ServiceException e = assertThrows(ServiceException.class,
                () -> accountService.register(register(PHONE, SMS_CODE, RAW_PASSWORD)));

        assertEquals(409, e.getCode().intValue());
        verifyNoInteractions(memberService);
    }

    @Test
    @DisplayName("注册：密码强度不合规 → 500，连验证码都不校验")
    void register_withWeakPassword_shouldFail()
    {
        ServiceException e = assertThrows(ServiceException.class,
                () -> accountService.register(register(PHONE, SMS_CODE, "12345678")));

        assertEquals(500, e.getCode().intValue());
        verifyNoInteractions(smsCodeService, memberService);
    }

    // ------------------------------------------------------------------ 登录

    @Test
    @DisplayName("登录：手机号 + 密码正确 → 刷新最后登录时间并签发登录态")
    void login_shouldRefreshLastLoginAndIssueToken()
    {
        MemberDO member = registeredMember();
        when(memberDao.selectByPhone(PHONE)).thenReturn(member);
        LoginVO expected = new LoginVO();
        expected.setToken("member-jwt");
        expected.setExpireTime(1792368000000L);
        expected.setMemberId(member.getId());
        expected.setMemberName(member.getName());
        when(memberTokenService.createToken(member)).thenReturn(expected);

        LoginVO actual = accountService.login(login(PHONE, RAW_PASSWORD));

        assertEquals("member-jwt", actual.getToken());
        assertEquals(member.getId(), actual.getMemberId());
        verify(memberDao).updateLastLoginTime(eq(member.getId()), any());
        verify(memberTokenService).createToken(member);
    }

    @Test
    @DisplayName("登录：密码错误 → 409「手机号或密码错误」，不签发 token、不刷新登录时间")
    void login_withWrongPassword_shouldConflict()
    {
        when(memberDao.selectByPhone(PHONE)).thenReturn(registeredMember());

        ServiceException e = assertThrows(ServiceException.class,
                () -> accountService.login(login(PHONE, "wrong1234")));

        assertEquals(409, e.getCode().intValue());
        assertEquals("手机号或密码错误", e.getMessage());
        verify(memberDao, never()).updateLastLoginTime(any(), any());
        verifyNoInteractions(memberTokenService);
    }

    @Test
    @DisplayName("登录：手机号不存在 → 与密码错误同一句提示（不泄露是否注册）")
    void login_withUnknownPhone_shouldConflictWithSameMessage()
    {
        when(memberDao.selectByPhone(PHONE)).thenReturn(null);

        ServiceException e = assertThrows(ServiceException.class,
                () -> accountService.login(login(PHONE, RAW_PASSWORD)));

        assertEquals(409, e.getCode().intValue());
        assertEquals("手机号或密码错误", e.getMessage());
        verifyNoInteractions(memberTokenService);
    }

    @Test
    @DisplayName("登录：有档案但未注册（password 为空）→ 同样 409")
    void login_withUnregisteredProfile_shouldConflict()
    {
        MemberDO member = registeredMember();
        member.setPassword(null);
        when(memberDao.selectByPhone(PHONE)).thenReturn(member);

        ServiceException e = assertThrows(ServiceException.class,
                () -> accountService.login(login(PHONE, RAW_PASSWORD)));

        assertEquals(409, e.getCode().intValue());
        assertEquals("手机号或密码错误", e.getMessage());
        verifyNoInteractions(memberTokenService);
    }

    // ------------------------------------------------------------------ 忘记密码

    @Test
    @DisplayName("忘记密码：手机号未注册 → 404，不消费验证码")
    void resetPassword_withUnregisteredPhone_shouldNotFound()
    {
        when(memberDao.selectByPhone(PHONE)).thenReturn(null);

        ServiceException e = assertThrows(ServiceException.class,
                () -> accountService.resetPassword(reset(PHONE, SMS_CODE, "new12345")));

        assertEquals(404, e.getCode().intValue());
        verifyNoInteractions(smsCodeService);
    }

    @Test
    @DisplayName("忘记密码：验证码错误 → 409，且不覆盖密码")
    void resetPassword_whenCodeInvalid_shouldNotUpdatePassword()
    {
        when(memberDao.selectByPhone(PHONE)).thenReturn(registeredMember());
        org.mockito.Mockito.doThrow(new ServiceException("验证码已过期，请重新获取", 409))
                .when(smsCodeService).verifyAndConsume(eq("reset"), eq(PHONE), anyString());

        ServiceException e = assertThrows(ServiceException.class,
                () -> accountService.resetPassword(reset(PHONE, SMS_CODE, "new12345")));

        assertEquals(409, e.getCode().intValue());
        verify(memberDao, never()).updatePassword(any(), anyString());
    }

    @Test
    @DisplayName("忘记密码：验证码正确 → 消费验证码并覆盖 BCrypt 哈希，不需要旧密码")
    void resetPassword_shouldOverwriteHash()
    {
        MemberDO member = registeredMember();
        when(memberDao.selectByPhone(PHONE)).thenReturn(member);

        accountService.resetPassword(reset(PHONE, SMS_CODE, "new12345"));

        verify(smsCodeService).verifyAndConsume("reset", PHONE, SMS_CODE);
        verify(memberDao).updatePassword(eq(member.getId()), org.mockito.ArgumentMatchers.argThat(
                hash -> hash != null && passwordEncoder.matches("new12345", hash)));
    }

    // ------------------------------------------------------------------ 夹具

    private SmsCodeSendDTO smsCodeSend(String phone, String scene)
    {
        SmsCodeSendDTO dto = new SmsCodeSendDTO();
        dto.setPhone(phone);
        dto.setScene(scene);
        return dto;
    }

    private RegisterDTO register(String phone, String smsCode, String password)
    {
        RegisterDTO dto = new RegisterDTO();
        dto.setPhone(phone);
        dto.setSmsCode(smsCode);
        dto.setPassword(password);
        return dto;
    }

    private ResetPasswordDTO reset(String phone, String smsCode, String password)
    {
        ResetPasswordDTO dto = new ResetPasswordDTO();
        dto.setPhone(phone);
        dto.setSmsCode(smsCode);
        dto.setPassword(password);
        return dto;
    }

    private LoginDTO login(String phone, String password)
    {
        LoginDTO dto = new LoginDTO();
        dto.setPhone(phone);
        dto.setPassword(password);
        return dto;
    }

    /** 已注册会员：密码是 yoga1234 的 BCrypt 哈希（与 yoga.sql 种子同口径） */
    private MemberDO registeredMember()
    {
        MemberDO member = new MemberDO();
        member.setId(1856739201475235902L);
        member.setPhone(PHONE);
        member.setPassword(passwordEncoder.encode(RAW_PASSWORD));
        member.setPhoneLast4("8000");
        member.setName("忻会员8000");
        member.setGender(0);
        member.setLevel(0);
        assertNotNull(member.getPassword());
        assertTrue(member.isRegistered());
        return member;
    }
}
