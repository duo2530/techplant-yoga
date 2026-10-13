package com.techplant.yoga.common.testcontainers;

import java.io.IOException;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.core.io.support.PropertySourceFactory;

/** 让 {@code @PropertySource} 能加载 YAML 配置。 */
public class YamlPropertySourceFactory implements PropertySourceFactory
{
    @Override
    public PropertySource<?> createPropertySource(String name, EncodedResource resource) throws IOException
    {
        String sourceName = name != null ? name : resource.getResource().getFilename();
        return new YamlPropertySourceLoader().load(sourceName, resource.getResource()).get(0);
    }
}
