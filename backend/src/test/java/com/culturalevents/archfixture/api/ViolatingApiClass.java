package com.culturalevents.archfixture.api;

import com.culturalevents.archfixture.infrastructure.FixtureInfrastructureClass;

/**
 * Classe de fixture que viola deliberadamente a regra de arquitetura (api dependendo
 * de infrastructure), usada apenas para provar que a {@code ArchRule} correspondente
 * de fato rejeita essa violação. Nunca deve existir em {@code src/main}.
 */
public class ViolatingApiClass {

    private final FixtureInfrastructureClass infrastructure = new FixtureInfrastructureClass();

    public void callInfrastructure() {
        infrastructure.doSomething();
    }
}
