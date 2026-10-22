package com.techplant.yoga.common.testcontainers;

import java.util.List;
import java.util.TimeZone;
import javax.sql.DataSource;
import javax.validation.Validator;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.PropertySource;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.common.core.redis.RedisCache;
import com.ruoyi.framework.config.ApplicationConfig;
import com.ruoyi.framework.config.MyBatisConfig;
import com.ruoyi.framework.config.RedisConfig;
import com.ruoyi.framework.config.ResourcesConfig;
import com.ruoyi.framework.config.SecurityConfig;
import com.ruoyi.framework.config.properties.PermitAllUrlProperties;
import com.ruoyi.framework.interceptor.impl.SameUrlDataInterceptor;
import com.ruoyi.framework.security.filter.JwtAuthenticationTokenFilter;
import com.ruoyi.framework.security.handle.AuthenticationEntryPointImpl;
import com.ruoyi.framework.security.handle.LogoutSuccessHandlerImpl;
import com.ruoyi.framework.web.exception.GlobalExceptionHandler;
import com.ruoyi.framework.web.service.PermissionService;
import com.ruoyi.framework.web.service.SysPasswordService;
import com.ruoyi.framework.web.service.SysPermissionService;
import com.ruoyi.framework.web.service.TokenService;
import com.ruoyi.framework.web.service.UserDetailsServiceImpl;
import com.ruoyi.system.service.impl.SysConfigServiceImpl;
import com.ruoyi.system.service.impl.SysDeptServiceImpl;
import com.ruoyi.system.service.impl.SysMenuServiceImpl;
import com.ruoyi.system.service.impl.SysPostServiceImpl;
import com.ruoyi.system.service.impl.SysRoleServiceImpl;
import com.ruoyi.system.service.impl.SysUserServiceImpl;
import com.techplant.yoga.common.config.JacksonConfig;

/**
 * 真实安全过滤链的 HTTP 集成测试装配。
 *
 * <p>与运行环境一致地装配管理端安全链（{@link SecurityConfig}）和用户端安全链
 * （{@code MemberSecurityConfig}，由业务包扫描注册），依赖真实的 MySQL／Redis 容器与真实 service、DAO，
 * 只把数据源、Redis 连接指向容器。测试用例通过真实 JWT 令牌访问接口。</p>
 */
@Configuration
@EnableWebMvc
@EnableWebSecurity
@EnableTransactionManagement
@PropertySource(value = "classpath:application.yml", factory = YamlPropertySourceFactory.class)
@ComponentScan(basePackages = "com.techplant.yoga", // 排除测试装配类，只扫业务组件
        excludeFilters = @ComponentScan.Filter(type = FilterType.REGEX, pattern = ".*testcontainers.*"))
@Import({MyBatisConfig.class, ApplicationConfig.class, RedisConfig.class, RedisCache.class, SecurityConfig.class,
    ResourcesConfig.class, SameUrlDataInterceptor.class, PermitAllUrlProperties.class,
    JwtAuthenticationTokenFilter.class, AuthenticationEntryPointImpl.class, LogoutSuccessHandlerImpl.class,
    GlobalExceptionHandler.class, TokenService.class, UserDetailsServiceImpl.class, SysPasswordService.class,
    SysPermissionService.class, PermissionService.class, SysUserServiceImpl.class, SysRoleServiceImpl.class,
    SysMenuServiceImpl.class, SysDeptServiceImpl.class, SysConfigServiceImpl.class, SysPostServiceImpl.class})
public class YogaSecurityTestConfig implements WebMvcConfigurer {
    /** 管理端安全链依赖的若依基础表：上下文创建前建好表结构，数据由各用例准备。 */
    private static final String[] SYS_TABLES = {"sys_user", "sys_role", "sys_menu", "sys_role_menu", "sys_user_role",
        "sys_post", "sys_user_post", "sys_config", "sys_dept", "sys_dict_type", "sys_dict_data", "sys_role_dept"};

    static {
        // 参数配置服务在上下文初始化时读取 sys_config，必须先把表建好
        YogaSchema.reset(YogaMySqlContainer.dataSource(), SYS_TABLES);
    }

    @Bean
    public DataSource dataSource() {
        return YogaMySqlContainer.dataSource();
    }

    @Bean
    public PlatformTransactionManager transactionManager(DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }

    @Bean(destroyMethod = "destroy")
    public RedisConnectionFactory redisConnectionFactory() {
        return new LettuceConnectionFactory(YogaRedisContainer.host(), YogaRedisContainer.port());
    }

    /** 与运行环境一致：短信验证码等服务使用字符串模板 */
    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory connectionFactory) {
        return new StringRedisTemplate(connectionFactory);
    }

    /** RedisConfig 开启了 @EnableCaching；业务代码没有缓存注解，测试用本地缓存管理器满足装配 */
    @Bean
    public CacheManager cacheManager() {
        return new ConcurrentMapCacheManager();
    }

    /** 让 @Value（token／user.password 等）能从 application.yml 解析 */
    @Bean
    public static PropertySourcesPlaceholderConfigurer propertySourcesPlaceholderConfigurer() {
        return new PropertySourcesPlaceholderConfigurer();
    }

    /** 若依用户服务需要 Bean Validation；MVC 自带的校验器仍在，这里显式标为优先 */
    @Bean
    @Primary
    public Validator validator() {
        return new LocalValidatorFactoryBean();
    }

    /** 与运行环境一致：业务对象的 64 位 ID 序列化成字符串 */
    @Override
    public void extendMessageConverters(List<HttpMessageConverter<?>> converters) {
        ObjectMapper objectMapper = Jackson2ObjectMapperBuilder.json()
                .modulesToInstall(new JacksonConfig.BusinessIdToStringModule()).timeZone(TimeZone.getDefault()).build();
        converters.removeIf(converter -> converter instanceof MappingJackson2HttpMessageConverter);
        converters.add(0, new MappingJackson2HttpMessageConverter(objectMapper));
    }
}
