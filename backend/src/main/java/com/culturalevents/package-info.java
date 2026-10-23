/**
 * Convenção de pacotes do backend: cada capability de negócio vive em
 * {@code com.culturalevents.<capability>.{api,application,domain,infrastructure}}.
 * <ul>
 *   <li>{@code api} — controllers e adapters HTTP.</li>
 *   <li>{@code application} — casos de uso / serviços de orquestração.</li>
 *   <li>{@code domain} — entidades e regras de negócio; não depende de api, application ou infrastructure.</li>
 *   <li>{@code infrastructure} — repositórios JPA e outros adapters de saída.</li>
 * </ul>
 * {@link com.culturalevents.shared} é a exceção à regra: qualquer camada de qualquer
 * capability pode depender de {@code shared} (erros e paginação compartilhados).
 * A regra de dependência entre essas camadas é verificada automaticamente por
 * {@code com.culturalevents.architecture.ArchitectureTest} (ArchUnit).
 *
 * <p>Esta change ({@code platform-foundation}) não cria nenhum pacote de capability de
 * negócio concreto — apenas {@code shared} existe até que a primeira capability de
 * negócio seja adicionada seguindo esta convenção.
 */
package com.culturalevents;
