package com.culturalevents.shared.web;

import java.util.Arrays;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Coloca sob {@code /api/v1} (o {@code servers} de api/openapi.yaml) todo controller que
 * implementa uma interface gerada a partir do contrato. Como o prefixo é aplicado por
 * controller, e não por context-path, {@code /actuator} e {@code /swagger-ui} não são afetados.
 */
@Configuration
class ApiPathPrefixConfig implements WebMvcConfigurer {

    static final String API_PREFIX = "/api/v1";
    private static final String GENERATED_API_PACKAGE = "com.culturalevents.shared.api";

    @Override
    public void configurePathMatch(PathMatchConfigurer configurer) {
        configurer.addPathPrefix(API_PREFIX, ApiPathPrefixConfig::implementsGeneratedApi);
    }

    private static boolean implementsGeneratedApi(Class<?> controller) {
        return Arrays.stream(controller.getInterfaces())
                .anyMatch(type -> type.getPackageName().equals(GENERATED_API_PACKAGE));
    }
}
