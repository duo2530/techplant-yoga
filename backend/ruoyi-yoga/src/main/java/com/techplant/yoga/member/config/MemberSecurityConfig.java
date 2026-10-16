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

/** 用户端 {@code /api/**} 的安全过滤链。 */
@Configuration
public class MemberSecurityConfig {
    /** 用户端安全链；会员过滤器仅注册到该链。 */
    @Bean
    @Order(1)
    public SecurityFilterChain memberSecurityFilterChain(HttpSecurity http, MemberTokenService memberTokenService,
            MemberService memberService, MemberAuthenticationEntryPointImpl memberAuthenticationEntryPoint,
            CorsFilter corsFilter) throws Exception {
        MemberJwtAuthenticationTokenFilter memberJwtFilter = new MemberJwtAuthenticationTokenFilter(memberTokenService,
                memberService);

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
                                "/api/auth/reset-password", "/api/auth/logout")
                        .permitAll().antMatchers(HttpMethod.GET, "/api/stores", "/api/schedules", "/api/schedules/*")
                        .permitAll()
                        // 其余 /api/** 一律要求会员身份
                        .anyRequest().authenticated())
                // 会员 token 过滤器
                .addFilterBefore(memberJwtFilter, UsernamePasswordAuthenticationFilter.class)
                // 跨域（小程序 H5 调试需要；与框架 CorsFilter 同一个 Bean）
                .addFilterBefore(corsFilter, MemberJwtAuthenticationTokenFilter.class);

        return http.build();
    }
}
