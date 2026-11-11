# ADR-003: cache Redis

- **Status:** Aceito
- **Contexto:** O projeto precisa acelerar consultas de resultados de análise e documentar expiração e invalidação.
- **Decisão:** `analysis-service` usa `@Cacheable` para resultados encontrados por ID, com TTL de 5 minutos configurado em `application.yml`. Resultados inexistentes (`null`) não são armazenados. A gravação de uma análise invalida a entrada para aquele ID.
- **Alternativas consideradas:** Consultar sempre o banco; não expirar o cache e depender apenas de invalidação.
- **Justificativa:** Um TTL curto reduz leituras repetidas no banco sem manter cópias por muito tempo. A invalidação evita servir uma análise antiga após atualização.
- **Consequências:** Um resultado pode permanecer em cache por até 5 minutos quando não houver invalidação; o Redis é uma dependência adicional de execução.
