package io.github.jsvro.spring;

import io.github.jsvro.core.JsvroCodec;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.http.converter.autoconfigure.ClientHttpMessageConvertersCustomizer;
import org.springframework.boot.http.converter.autoconfigure.ServerHttpMessageConvertersCustomizer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class JsvroAutoConfigurationTest {
    private final WebApplicationContextRunner webContext = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(JsvroAutoConfiguration.class))
            .withBean(JsonMapper.class, () -> JsonMapper.builder().build());

    @Test
    void registersCodecAndBothConverterCustomizersInAServletApplication() {
        webContext.run(context -> {
            assertThat(context).hasSingleBean(JsvroCodec.class);
            assertThat(context).hasSingleBean(ServerHttpMessageConvertersCustomizer.class);
            assertThat(context).hasSingleBean(ClientHttpMessageConvertersCustomizer.class);
        });
    }

    @Test
    void registersOnlyTheClientSideOutsideAServletApplication() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(JsvroAutoConfiguration.class))
                .withBean(JsonMapper.class, () -> JsonMapper.builder().build())
                .run(context -> {
                    assertThat(context).hasSingleBean(JsvroCodec.class);
                    assertThat(context).doesNotHaveBean(ServerHttpMessageConvertersCustomizer.class);
                    assertThat(context).hasSingleBean(ClientHttpMessageConvertersCustomizer.class);
                });
    }

    @Test
    void backsOffWhenDisabled() {
        webContext.withPropertyValues("jsvro.enabled=false").run(context -> {
            assertThat(context).doesNotHaveBean(JsvroCodec.class);
            assertThat(context).doesNotHaveBean(ServerHttpMessageConvertersCustomizer.class);
            assertThat(context).doesNotHaveBean(ClientHttpMessageConvertersCustomizer.class);
        });
    }

    @Test
    void keepsAUserDefinedCodec() {
        JsvroCodec custom = new JsvroCodec(JsonMapper.builder().build());

        webContext.withBean("customCodec", JsvroCodec.class, () -> custom)
                .run(context -> assertThat(context.getBean(JsvroCodec.class)).isSameAs(custom));
    }

    @Test
    void publishesConfigurationMetadataForTheEnabledProperty() throws IOException {
        try (InputStream metadata = getClass().getResourceAsStream("/META-INF/spring-configuration-metadata.json")) {
            assertThat(metadata).isNotNull();
            assertThat(new String(metadata.readAllBytes(), StandardCharsets.UTF_8)).contains("\"jsvro.enabled\"");
        }
    }
}
