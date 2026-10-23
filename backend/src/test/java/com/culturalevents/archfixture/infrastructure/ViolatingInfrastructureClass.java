package com.culturalevents.archfixture.infrastructure;

import com.culturalevents.archfixture.api.FixtureApiClass;

/**
 * Classe de fixture que viola deliberadamente a regra de arquitetura (infrastructure
 * dependendo de api), usada apenas para provar que a {@code ArchRule} correspondente
 * de fato rejeita essa violação. Nunca deve existir em {@code src/main}.
 */
public class ViolatingInfrastructureClass {

    private final FixtureApiClass api = new FixtureApiClass();

    public void callApi() {
        api.doSomething();
    }
}
