package com.culturalevents.archfixture.domain;

import com.culturalevents.archfixture.infrastructure.FixtureInfrastructureClass;

/**
 * Classe de fixture que viola deliberadamente a regra de arquitetura (domain
 * dependendo de infrastructure), usada apenas para provar que a {@code ArchRule}
 * de fato rejeita essa violação. Nunca deve existir em {@code src/main}.
 */
public class ViolatingDomainClass {

    private final FixtureInfrastructureClass infrastructure = new FixtureInfrastructureClass();

    public void callInfrastructure() {
        infrastructure.doSomething();
    }
}
