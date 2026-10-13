package com.techplant.yoga.member.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.web.filter.CorsFilter;
import com.techplant.yoga.member.security.MemberAuthenticationEntryPointImpl;
import com.techplant.yoga.member.security.MemberJwtAuthenticationTokenFilter;
import com.techplant.yoga.member.service.MemberService;
import com.techplant.yoga.member.service.MemberTokenService;

/**
 * 用户端 {@code /api/**} 的安全过滤链（用户端接口详细设计 §2.1.2、§4.7）。
 *
 * <p><b>为什么单独一条链</b>：会员 token 只作用于 {@code /api/**}，管理端
 * {@code /admin/**} 与 {@code sys_*} 仍走 {@code sys_user} JWT。用链级匹配器把
 * {@code /api/**} 摘出来（{@code @Order(1)} 先匹配），管理端那条链（框架 {@code SecurityConfig}，
 * 无 {@code @Order}）就不会被会员 token 影响，反之亦然。</p>
 *
 * <p><b>放行清单与 §2.1.2 表一一对应</b>：发码／注册／登录／忘记密码免登录，
 * 退出登录允许清理；一期三个免登录接口
 * （{@code GET /api/stores}、{@code GET /api/schedules}、{@code GET /api/schedules/{id}}）
 * <b>行为不变</b>。其余 {@code /api/**} 一律要求会员身份（fail-closed：将来新加的接口默认受保护）。</p>
 *
 * <p>本阶段这条链里没有任何需要会员身份的接口，但鉴权链路已经就位：
 * {@link MemberJwtAuthenticationTokenFilter} 建立登录上下文，
 * {@link MemberAuthenticationEntryPointImpl} 负责 401。</p>
 */
@Configuration
public class MemberSecurityConfig
{
    /**
     * 用户端过滤链（{@code /api/**}）
     *
     * <p>{@code MemberJwtAuthenticationTokenFilter} 在这里<b>手工 new</b>，不注册成 Bean：
     * 否则 Spring Boot 会把它自动注册为全局 servlet 过滤器，会员 token 将能在
     * {@code /admin/**} 上建立登录上下文（§2.1.2 明确禁止）。</p>
     */
    @Bean
    @Order(1)
    public SecurityFilterChain memberSecurityFilterChain(HttpSecurity http,
            MemberTokenService memberTokenService, MemberService memberService,
            MemberAuthenticationEntryPointImpl memberAuthenticationEntryPoint, CorsFilter corsFilter) throws Exception
    {
        MemberJwtAuthenticationTokenFilter memberJwtFilter =
                new MemberJwtAuthenticationTokenFilter(memberTokenService, memberService);

        http
            // 链级匹配：只有 /api/** 走这一条
            .requestMatcher(new AntPathRequestMatcher("/api/**"))
            // 无状态 + 不使用 session，token 存 Redis
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            // 会员认证失败：业务码 401（HTTP 仍 200）
            .exceptionHandling(exception -> exception.authenticationEntryPoint(memberAuthenticationEntryPoint))
            .authorizeHttpRequests(requests -> requests
                    // 免登录（发码／注册／登录／忘记密码）与允许清理的退出
                    .antMatchers("/api/auth/sms-code", "/api/auth/register", "/api/auth/login",
                            "/api/auth/reset-password", "/api/auth/logout").permitAll()
                    // 一期三个免登录接口：仍然匿名可读
                    .antMatchers(HttpMethod.GET, "/api/stores", "/api/schedules", "/api/schedules/*").permitAll()
                    // 其余 /api/** 一律要求会员身份
                    .anyRequest().authenticated())
            // 会员 token 过滤器
            .addFilterBefore(memberJwtFilter, UsernamePasswordAuthenticationFilter.class)
            // 跨域（小程序 H5 调试需要；与框架 CorsFilter 同一个 Bean）
            .addFilterBefore(corsFilter, MemberJwtAuthenticationTokenFilter.class);

        return http.build();
    }
}
