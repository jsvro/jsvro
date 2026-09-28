package io.github.jsvro.spring;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("jsvro")
public record JsvroProperties(@DefaultValue("true") boolean enabled) {
}
