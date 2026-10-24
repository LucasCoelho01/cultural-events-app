package com.culturalevents.platform;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

import com.culturalevents.AbstractPostgresIntegrationTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Teste de integração base: sobe o contexto Spring completo com ddl-auto=validate sobre o
 * Postgres compartilhado (migrations de database/migrations já aplicadas), provando que o
 * schema e o mapeamento JPA continuam compatíveis.
 */
class PlatformFoundationIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void contextoSobeComMigrationsAplicadasEDdlAutoValidate() {
        assertThat(applicationContext).isNotNull();
    }
}
