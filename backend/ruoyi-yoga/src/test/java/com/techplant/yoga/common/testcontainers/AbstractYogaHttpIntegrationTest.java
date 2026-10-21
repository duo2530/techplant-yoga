package com.techplant.yoga.common.testcontainers;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.common.core.domain.model.LoginUser;
import com.ruoyi.framework.web.service.TokenService;
import com.ruoyi.framework.web.service.UserDetailsServiceImpl;
import com.ruoyi.system.service.ISysUserService;
import com.techplant.yoga.member.service.MemberTokenService;

/**
 * 真实安全过滤链的 HTTP 集成测试基类。
 *
 * <p>MockMvc 直接挂载运行环境的 {@link FilterChainProxy}（管理端链与用户端链都在其中），
 * 请求经过真实过滤器、真实 controller、真实 service／DAO／MySQL；令牌由真实登录信息生成。</p>
 */
@WebAppConfiguration
@ContextConfiguration(classes = YogaSecurityTestConfig.class)
public abstract class AbstractYogaHttpIntegrationTest extends AbstractYogaIntegrationTest {
    /** 令牌请求头 */
    protected static final String AUTHORIZATION = "Authorization";

    @Autowired
    protected WebApplicationContext webApplicationContext;

    @Autowired
    private FilterChainProxy springSecurityFilterChain;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private UserDetailsServiceImpl userDetailsService;

    @Autowired
    private ISysUserService sysUserService;

    @Autowired
    protected MemberTokenService memberTokenService;

    protected MockMvc mockMvc;

    @BeforeEach
    void buildMockMvc() {
        // 本基类一律通过真实令牌建立身份，先清掉集成测试基类绑定的操作人上下文
        SecurityContextHolder.clearContext();
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).addFilters(springSecurityFilterChain)
                .build();
    }

    /**
     * 生成管理端令牌：用户、角色与菜单权限都来自数据库，权限集合由真实权限服务计算
     *
     * @param userName 已存在的 sys_user 登录名
     * @return JWT
     */
    protected String createAdminToken(String userName) {
        SysUser sysUser = sysUserService.selectUserByUserName(userName);
        LoginUser loginUser = (LoginUser) userDetailsService.createLoginUser(sysUser);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/120.0.0.0");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        try {
            return tokenService.createToken(loginUser);
        } finally {
            RequestContextHolder.resetRequestAttributes();
        }
    }

    /** 管理端 Authorization 请求头值 */
    protected String bearer(String token) {
        return "Bearer " + token;
    }
}
