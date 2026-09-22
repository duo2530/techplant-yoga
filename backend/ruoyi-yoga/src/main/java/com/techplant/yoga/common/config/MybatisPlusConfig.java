package com.techplant.yoga.common.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;

/**
 * MyBatis-Plus 配置（详细设计 §1.1.1、§3.2）。
 *
 * <p>工程自己声明了 SqlSessionFactory（见 ruoyi-framework 的 {@code MyBatisConfig}），
 * MyBatis-Plus 的自动配置会整体退出，因此分页插件与全局配置由 {@code MyBatisConfig}
 * 从这里取 Bean 手动装配 —— 本类只负责「提供 Bean」，不重复造 SqlSessionFactory，
 * 这样 RuoYi 原有的原生 MyBatis XML Mapper 与业务模块的 BaseMapper 共用同一个工厂。</p>
 */
@Configuration
@MapperScan("com.techplant.yoga.**.mapper")
public class MybatisPlusConfig
{
    /** 每页条数上限，与详细设计 §2.1.4「pageSize 最大 100」一致（校验在接口层，这里是兜底） */
    private static final long MAX_PAGE_SIZE = 100L;

    /**
     * MyBatis-Plus 插件：分页
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor()
    {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        PaginationInnerInterceptor pagination = new PaginationInnerInterceptor(DbType.MYSQL);
        pagination.setMaxLimit(MAX_PAGE_SIZE);
        interceptor.addInnerInterceptor(pagination);
        return interceptor;
    }
}
