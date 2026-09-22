package com.techplant.yoga.common.config;

import java.util.List;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.SerializationConfig;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.BeanPropertyWriter;
import com.fasterxml.jackson.databind.ser.BeanSerializerModifier;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

/**
 * 业务模块的 JSON 序列化配置：<b>64 位整数主键序列化为字符串</b>（详细设计 §1.4、§2.1.5）。
 *
 * <p>雪花ID 是 64 位整数，超出小程序 JS {@code Number} 的 53 位安全整数范围，直接返回数字会丢精度，
 * 因此接口返回的 ID 必须带引号。</p>
 *
 * <p><b>作用范围：</b>只对业务包 {@code com.techplant.yoga} 下的返回对象生效，且只改名字为 {@code id}
 * 或以 {@code Id} 结尾的 Long 字段。这样既满足「ID 一律字符串」，又不会出现两个副作用：</p>
 * <ul>
 *   <li>不动 RuoYi 的 {@code sys_*} 接口 —— 全局把 Long 变字符串会让 {@code sys_user.userId} 变成 "1"，
 *       而管理端前端存在 {@code userId === 1} 之类的数值比较，属于回归风险；</li>
 *   <li>不动业务里的计数型 Long（例如 409 阻塞明细的 {@code scheduleCount}/{@code bookingCount}），
 *       它们按 §2.3 的结构仍是数字。</li>
 * </ul>
 */
@Configuration
public class JacksonConfig
{
    /** 业务代码包前缀 */
    public static final String BUSINESS_PACKAGE = "com.techplant.yoga";

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer businessIdToStringCustomizer()
    {
        return builder -> builder.modulesToInstall(new BusinessIdToStringModule());
    }

    /**
     * 把业务对象的 ID 字段序列化成字符串
     */
    public static class BusinessIdToStringModule extends SimpleModule
    {
        private static final long serialVersionUID = 1L;

        public BusinessIdToStringModule()
        {
            super("businessIdToString");
        }

        @Override
        public void setupModule(SetupContext context)
        {
            super.setupModule(context);
            context.addBeanSerializerModifier(new BeanSerializerModifier()
            {
                @Override
                public List<BeanPropertyWriter> changeProperties(SerializationConfig config, BeanDescription beanDesc,
                        List<BeanPropertyWriter> beanProperties)
                {
                    if (!isBusinessBean(beanDesc.getBeanClass()))
                    {
                        return beanProperties;
                    }
                    for (BeanPropertyWriter writer : beanProperties)
                    {
                        if (isIdProperty(writer.getName()) && isLongType(writer))
                        {
                            writer.assignSerializer(ToStringSerializer.instance);
                        }
                    }
                    return beanProperties;
                }
            });
        }

        private boolean isBusinessBean(Class<?> beanClass)
        {
            return beanClass != null && beanClass.getName().startsWith(BUSINESS_PACKAGE);
        }

        private boolean isIdProperty(String propertyName)
        {
            return "id".equals(propertyName) || propertyName.endsWith("Id");
        }

        private boolean isLongType(BeanPropertyWriter writer)
        {
            Class<?> rawClass = writer.getType().getRawClass();
            return rawClass == Long.class || rawClass == long.class;
        }
    }
}
