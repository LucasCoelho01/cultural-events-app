## 1. Contrato

- [x] 1.1 Adicionar `GET /cities/{uf}/{slug}/events/{eventSlug}` (`getPublishedEvent`, tag `events`; 200 `Event`, 400 `BadRequest`, 404 `EventNotFound`) em `api/openapi.yaml`
- [x] 1.2 Validar o contrato: `./mvnw generate-sources` no `backend/` e `npm run generate:api` no `frontend/`

## 2. Back-end

- [x] 2.1 `EventRepository.findByCityIdAndSlug` e implementação em `EventRepositoryAdapter`
- [x] 2.2 `EventsService.getPublished` (cidade inexistente 404; evento ausente ou não `PUBLISHED` 404 com mensagem idêntica)
- [x] 2.3 `EventsController.getPublishedEvent`
- [x] 2.4 Teste unitário do serviço (mock de repositório e `CityLookup`): publicado, não publicado, outro slug, cidade inexistente
- [x] 2.5 Teste de integração com Testcontainers (`EventsPublicApiIntegrationTest`): 200 com categoria, local e sessões ordenadas; 404 para `DRAFT`, `CANCELLED`, `ARCHIVED`; 404 para evento de outra cidade; 404 para cidade inexistente; 400 para UF malformada; ids internos ausentes
- [x] 2.6 Rodar `./mvnw verify` (369 testes; `clean` bloqueado por arquivo em uso) (inclui ArchUnit e `NoHardcodedCityTest`)
- [x] 2.7 Adicionar a requisição na coleção Postman em `postman/`

## 3. Front-end: formatação pura

- [x] 3.1 `src/format/price.ts` (`formatPrice`) e `src/format/date.ts` (`formatDateTime`, `nextOccurrence`, `futureOccurrences`, com `now` injetado)
- [x] 3.2 Testes unitários: gratuito, faixa, preço único, sem preço, fuso `America/Recife` (virada de dia), fuso inválido, próxima sessão (ignora passadas e não `SCHEDULED`), sem sessões

## 4. Front-end: API e páginas

- [x] 4.1 `src/api/events.ts` (`useEvents`, `usePublishedEvent`) e `src/api/venues.ts` (`useVenue`)
- [x] 4.2 `EventCard` (link para `/:uf/:cidade/eventos/:slug`), `Pagination` (Anterior/Próxima por `PageMetadata`) e `EventList` com estados de carregando, vazio e erro
- [x] 4.3 `CityPage` usa a lista, com a página em `?page=` e o fuso de `useCity`
- [x] 4.4 `EventPage` (detalhe) com descrição, sessões futuras, local com endereço, classificação, preço e link de ingresso; degradação se `useVenue` falhar
- [x] 4.5 Rota `/:uf/:cidade/eventos/:slug` em `App.tsx`
- [x] 4.6 Handlers MSW com `zz/cidade-teste` (cidade, eventos, detalhe, local)
- [x] 4.7 Testes de página: lista com cards e links; paginação e `?page=` (base 1; ausente, inválido e além do total); sessões futuras e endereço do local no detalhe; vazio; carregando; erro 500 e cidade 404; detalhe completo; detalhe com campos ausentes; detalhe 404; falha do local
- [x] 4.8 Verificar que `frontend/src` não contém "São Paulo" nem "sao-paulo" (fora do arquivo gerado)

## 5. Verificação

- [x] 5.1 `npm run lint`, `npm run build` e `npm test` em `frontend/`
- [ ] 5.2 Validação manual com back-end e Postgres rodando: lista, paginação e detalhe numa cidade real do banco (parcial: API verificada na 8081; falta a UI via proxy com o back-end novo na 8080)
- [x] 5.3 Atualizar README do `frontend/` com as novas rotas
