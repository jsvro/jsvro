package io.github.jsvro.spring;

import org.springframework.http.MediaType;

public final class JsvroMediaType {
    public static final String APPLICATION_JSVRO_VALUE = "application/vnd.jsvro";
    public static final MediaType APPLICATION_JSVRO = MediaType.parseMediaType(APPLICATION_JSVRO_VALUE);

    private JsvroMediaType() {
    }
}
