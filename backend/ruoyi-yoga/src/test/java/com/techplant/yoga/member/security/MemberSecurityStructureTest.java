package com.techplant.yoga.member.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.core.annotation.Order;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import com.techplant.yoga.member.config.MemberSecurityConfig;

/**
 * 会员安全链的<b>结构性</b>回归测试（用户端接口详细设计 §2.1.2、§4.7）。
 *
 * <p>HTTP 层的越权行为已经用临时实例实跑验证过（会员 token 打 {@code /system/user/list} → 401、
 * 未登录打未交付的 {@code /api/**} → 401、退出后同 token → 401）。这些行为依赖两个
 * <b>很容易被顺手改掉</b>的结构前提，而它们一旦破坏<b>不会报错、只会静默越权</b>，
 * 因此在本类里用反射锁死：</p>
 *
 * <ol>
 *   <li>{@link MemberJwtAuthenticationTokenFilter} <b>不能是 Spring Bean</b>：
 *       Spring Boot 会把容器里的 {@code Filter} Bean 自动注册成全局 servlet 过滤器，
 *       那样会员 token 也能在 {@code /admin/**} 上建立登录上下文（§2.1.2 明确禁止）。
 *       它只能由 {@link MemberSecurityConfig} 手工 new 出来、只进 {@code /api/**} 那条链。</li>
 *   <li>会员链必须带 {@code @Order(1)}：排在框架那条链（{@code SecurityConfig}，无 {@code @Order}）
 *       前面，{@code /api/**} 才不会被管理端链先接走。</li>
 *   <li>会员认证失败要有自己的入口点（401 文案与业务码口径）。</li>
 * </ol>
 */
@DisplayName("会员安全链结构性测试（防静默越权）")
class MemberSecurityStructureTest
{
    @Test
    @DisplayName("会员 token 过滤器不能是 @Component（否则会被 Boot 注册成全局过滤器，越权 /admin/**）")
    void memberJwtFilter_shouldNotBeSpringBean()
    {
        assertNull(MemberJwtAuthenticationTokenFilter.class.getAnnotation(Component.class),
                "会员过滤器一旦成为 Bean，Spring Boot 会把 Filter Bean 自动注册到所有 URL，会员 token 将能在 /admin/** 建立登录上下文");
        assertTrue(OncePerRequestFilter.class.isAssignableFrom(MemberJwtAuthenticationTokenFilter.class),
                "会员过滤器应继承 OncePerRequestFilter，与框架的 JwtAuthenticationTokenFilter 一致");
    }

    @Test
    @DisplayName("会员过滤链：/api/** 专用链带 @Order(1)，且不把过滤器暴露成 Bean")
    void memberSecurityConfig_shouldDeclareOrderedApiChainOnly()
    {
        Method chainMethod = null;
        for (Method method : MemberSecurityConfig.class.getDeclaredMethods())
        {
            if (method.getAnnotation(Bean.class) == null)
            {
                continue;
            }
            // 任何 @Bean 方法都不允许把会员过滤器交给容器
            assertTrue(!MemberJwtAuthenticationTokenFilter.class.isAssignableFrom(method.getReturnType()),
                    "@Bean " + method.getName() + " 不能返回会员 token 过滤器（会被 Boot 全局注册）");
            if (SecurityFilterChain.class.isAssignableFrom(method.getReturnType()))
            {
                chainMethod = method;
            }
        }
        assertNotNull(chainMethod, "MemberSecurityConfig 必须声明 /api/** 的 SecurityFilterChain");
        Order order = chainMethod.getAnnotation(Order.class);
        assertNotNull(order, "会员链必须标 @Order(1)，否则可能与框架链的先后顺序不确定");
        assertEquals(1, order.value(), "会员链要排在框架链前面");
    }

    @Test
    @DisplayName("会员认证失败入口点是 Bean 且实现 AuthenticationEntryPoint（401 契约）")
    void memberEntryPoint_shouldBeAnEntryPointBean()
    {
        assertNotNull(MemberAuthenticationEntryPointImpl.class.getAnnotation(Component.class));
        assertTrue(AuthenticationEntryPoint.class.isAssignableFrom(MemberAuthenticationEntryPointImpl.class));
    }
}
