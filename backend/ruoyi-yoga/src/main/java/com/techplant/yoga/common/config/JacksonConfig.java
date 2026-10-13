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

/** 业务模块的 JSON 序列化配置：<b>64 位整数主键序列化为字符串</b>。 */
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
