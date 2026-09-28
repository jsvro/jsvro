package io.github.jsvro.spring;

import io.github.jsvro.core.JsvroCodec;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.http.converter.autoconfigure.ClientHttpMessageConvertersCustomizer;
import org.springframework.boot.http.converter.autoconfigure.ServerHttpMessageConvertersCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.HttpMessageConverter;
import tools.jackson.databind.json.JsonMapper;

@AutoConfiguration
@ConditionalOnClass(JsonMapper.class)
@ConditionalOnProperty(prefix = "jsvro", name = "enabled", matchIfMissing = true)
@EnableConfigurationProperties(JsvroProperties.class)
public class JsvroAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    JsvroCodec jsvroCodec(JsonMapper jsonMapper) {
        return new JsvroCodec(jsonMapper);
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnClass({HttpMessageConverter.class, ServerHttpMessageConvertersCustomizer.class})
    static class ServerConverterConfiguration {

        @Bean
        ServerHttpMessageConvertersCustomizer jsvroServerHttpMessageConvertersCustomizer(
                JsonMapper jsonMapper,
                JsvroCodec codec) {
            // Append rather than register as a converter bean. Spring Boot automatically adds
            // HttpMessageConverter beans to MVC; doing both would register JSVRO twice.
            // Appending also means Accept: */* keeps Spring's normal JSON representation.
            return builder -> builder.configureMessageConvertersList(
                    converters -> converters.add(new JsvroHttpMessageConverter(jsonMapper, codec)));
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass({HttpMessageConverter.class, ClientHttpMessageConvertersCustomizer.class})
    static class ClientConverterConfiguration {

        @Bean
        ClientHttpMessageConvertersCustomizer jsvroClientHttpMessageConvertersCustomizer(
                JsonMapper jsonMapper,
                JsvroCodec codec) {
            return builder -> builder.configureMessageConvertersList(
                    converters -> converters.add(new JsvroHttpMessageConverter(jsonMapper, codec)));
        }
    }
}
