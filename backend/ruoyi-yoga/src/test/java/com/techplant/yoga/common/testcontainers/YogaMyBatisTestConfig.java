package com.techplant.yoga.common.testcontainers;

import javax.sql.DataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.PropertySource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import com.ruoyi.framework.config.MyBatisConfig;
import com.techplant.yoga.common.config.MybatisPlusConfig;
import com.techplant.yoga.common.mybatis.AuditMetaObjectHandler;

/** 集成测试的持久化装配：数据源指向测试容器，其余装配复用运行环境的配置类。 */
@Configuration
@EnableTransactionManagement
@PropertySource(value = "classpath:application.yml", factory = YamlPropertySourceFactory.class)
@Import({ MyBatisConfig.class, MybatisPlusConfig.class, AuditMetaObjectHandler.class })
public class YogaMyBatisTestConfig
{
    @Bean
    public DataSource dataSource()
    {
        return YogaMySqlContainer.dataSource();
    }

    @Bean
    public PlatformTransactionManager transactionManager(DataSource dataSource)
    {
        return new DataSourceTransactionManager(dataSource);
    }
}
