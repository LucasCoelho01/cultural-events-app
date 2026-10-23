package com.culturalevents.archfixture.application;

import com.culturalevents.archfixture.api.FixtureApiClass;

/**
 * Classe de fixture que viola deliberadamente a regra de arquitetura (application
 * dependendo de api), usada apenas para provar que a {@code ArchRule} correspondente
 * de fato rejeita essa violação. Nunca deve existir em {@code src/main}.
 */
public class ViolatingApplicationClass {

    private final FixtureApiClass api = new FixtureApiClass();

    public void callApi() {
        api.doSomething();
    }
}
