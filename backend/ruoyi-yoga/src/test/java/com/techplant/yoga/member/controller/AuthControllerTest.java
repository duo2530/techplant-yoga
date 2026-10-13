package com.techplant.yoga.member.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.hamcrest.Matchers;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.framework.web.exception.GlobalExceptionHandler;
import com.techplant.yoga.common.config.JacksonConfig;
import com.techplant.yoga.member.service.AccountService;
import com.techplant.yoga.member.service.MemberTokenService;
import com.techplant.yoga.member.vo.LoginVO;

/**
 * 用户端账号接口的单元测试（用户端接口详细设计 §2.2.4～§2.2.8）。
 *
 * <p>MockMvc 走真实的参数绑定、{@code @Valid} 校验与统一异常转换（挂框架
 * {@link GlobalExceptionHandler}），序列化用 {@link JacksonConfig} 的 ObjectMapper，
 * 保证「会员 ID 返回字符串」这条契约被测到。</p>
 */
@DisplayName("AuthController 单元测试")
class AuthControllerTest
{
    private static final String MEMBER_ID = "1856739201475235902";

    private AccountService accountService;

    private MemberTokenService memberTokenService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp()
    {
        accountService = mock(AccountService.class);
        memberTokenService = mock(MemberTokenService.class);
        AuthController controller = new AuthController(accountService, memberTokenService);
        ObjectMapper objectMapper = new Jackson2ObjectMapperBuilder()
                .modulesToInstall(new JacksonConfig.BusinessIdToStringModule())
                .build();
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    @Test
    @DisplayName("登录：成功返回 token/expireTime/memberId(字符串)/memberName")
    void login_shouldReturnLoginVO()
    {
        LoginVO loginVO = new LoginVO();
        loginVO.setToken("member-jwt");
        loginVO.setExpireTime(1792368000000L);
        loginVO.setMemberId(1856739201475235902L);
        loginVO.setMemberName("忻会员8000");
        when(accountService.login(any())).thenReturn(loginVO);

        try
        {
            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"phone\":\"13800138000\",\"password\":\"yoga1234\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.msg").value("登录成功"))
                    .andExpect(jsonPath("$.data.token").value("member-jwt"))
                    .andExpect(jsonPath("$.data.expireTime").value(1792368000000L))
                    .andExpect(jsonPath("$.data.memberId").value(MEMBER_ID))
                    .andExpect(jsonPath("$.data.memberName").value("忻会员8000"));
        }
        catch (Exception e)
        {
            throw new AssertionError(e);
        }
        verify(accountService).login(any());
    }

    @Test
    @DisplayName("登录：手机号或密码错误 → 409「手机号或密码错误」")
    void login_withWrongCredential_shouldReturn409()
    {
        doThrow(new ServiceException("手机号或密码错误", 409)).when(accountService).login(any());

        try
        {
            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"phone\":\"13800138000\",\"password\":\"badpass1\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(409))
                    .andExpect(jsonPath("$.msg").value("手机号或密码错误"));
        }
        catch (Exception e)
        {
            throw new AssertionError(e);
        }
    }

    @Test
    @DisplayName("登录：密码缺失 → 500「密码不能为空」（@Valid 生效，不落 service）")
    void login_withoutPassword_shouldReturnParamError()
    {
        try
        {
            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"phone\":\"13800138000\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(500))
                    .andExpect(jsonPath("$.msg").value("密码不能为空"));
        }
        catch (Exception e)
        {
            throw new AssertionError(e);
        }
        verifyNoInteractions(accountService);
    }

    @Test
    @DisplayName("登录：手机号格式非法 → 500「手机号格式不正确」")
    void login_withInvalidPhone_shouldReturnParamError()
    {
        try
        {
            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"phone\":\"12345\",\"password\":\"yoga1234\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(500))
                    .andExpect(jsonPath("$.msg").value("手机号格式不正确"));
        }
        catch (Exception e)
        {
            throw new AssertionError(e);
        }
        verifyNoInteractions(accountService);
    }

    @Test
    @DisplayName("注册：成功只返回 code/msg，不发 token（前端跳回登录页）")
    void register_shouldReturnSuccessWithoutToken()
    {
        try
        {
            mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"phone\":\"13800138000\",\"smsCode\":\"888888\",\"password\":\"yoga1234\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.msg").value("注册成功，请登录"))
                    .andExpect(jsonPath("$.data").doesNotExist());
        }
        catch (Exception e)
        {
            throw new AssertionError(e);
        }
        verify(accountService).register(any());
        verifyNoInteractions(memberTokenService);
    }

    @Test
    @DisplayName("注册：密码强度不合规 → 500，提示语指向密码规则")
    void register_withWeakPassword_shouldReturnParamError()
    {
        try
        {
            mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"phone\":\"13800138000\",\"smsCode\":\"888888\",\"password\":\"1234567\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(500))
                    // @Size 与 @Pattern 都会命中，具体落到哪一条取决于校验器，这里只锁「提示语指向密码规则」
                    .andExpect(jsonPath("$.msg").value(Matchers.containsString("密码")));
        }
        catch (Exception e)
        {
            throw new AssertionError(e);
        }
        verifyNoInteractions(accountService);
    }

    @Test
    @DisplayName("注册：验证码不是 6 位数字 → 500「验证码为 6 位数字」")
    void register_withInvalidSmsCode_shouldReturnParamError()
    {
        try
        {
            mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"phone\":\"13800138000\",\"smsCode\":\"88\",\"password\":\"yoga1234\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(500))
                    .andExpect(jsonPath("$.msg").value("验证码为 6 位数字"));
        }
        catch (Exception e)
        {
            throw new AssertionError(e);
        }
        verifyNoInteractions(accountService);
    }

    @Test
    @DisplayName("发码：成功返回「验证码已发送」，不返回验证码")
    void sendSmsCode_shouldReturnSuccess()
    {
        try
        {
            mockMvc.perform(post("/api/auth/sms-code")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"phone\":\"13800138000\",\"scene\":\"register\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.msg").value("验证码已发送"))
                    .andExpect(jsonPath("$.data").doesNotExist());
        }
        catch (Exception e)
        {
            throw new AssertionError(e);
        }
        verify(accountService).sendSmsCode(any());
    }

    @Test
    @DisplayName("发码：场景取值非法 → 500「验证码场景取值为 register 或 reset」")
    void sendSmsCode_withInvalidScene_shouldReturnParamError()
    {
        try
        {
            mockMvc.perform(post("/api/auth/sms-code")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"phone\":\"13800138000\",\"scene\":\"login\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(500))
                    .andExpect(jsonPath("$.msg").value("验证码场景取值为 register 或 reset"));
        }
        catch (Exception e)
        {
            throw new AssertionError(e);
        }
        verifyNoInteractions(accountService);
    }

    @Test
    @DisplayName("忘记密码：成功返回「密码已重置」")
    void resetPassword_shouldReturnSuccess()
    {
        try
        {
            mockMvc.perform(post("/api/auth/reset-password")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"phone\":\"13800138000\",\"smsCode\":\"888888\",\"password\":\"new12345\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.msg").value("密码已重置"));
        }
        catch (Exception e)
        {
            throw new AssertionError(e);
        }
        verify(accountService).resetPassword(any());
    }

    @Test
    @DisplayName("忘记密码：手机号未注册 → 404「手机号未注册」")
    void resetPassword_withUnregisteredPhone_shouldReturn404()
    {
        doThrow(new ServiceException("手机号未注册", 404)).when(accountService).resetPassword(any());

        try
        {
            mockMvc.perform(post("/api/auth/reset-password")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"phone\":\"13900139000\",\"smsCode\":\"888888\",\"password\":\"new12345\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(404))
                    .andExpect(jsonPath("$.msg").value("手机号未注册"));
        }
        catch (Exception e)
        {
            throw new AssertionError(e);
        }
    }

    @Test
    @DisplayName("退出登录：无 token 也返回成功（幂等），并尝试清理会话")
    void logout_shouldBeIdempotent()
    {
        try
        {
            mockMvc.perform(post("/api/auth/logout"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.msg").value("退出成功"));
        }
        catch (Exception e)
        {
            throw new AssertionError(e);
        }
        verify(memberTokenService).revokeSession(any());
        verifyNoInteractions(accountService);
    }
}
