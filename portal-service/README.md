# Portal BFF

## Objetivo

Fornecer uma interface web única para a demonstração do fluxo antifraude e uma API BFF que agrega os Serviços de Transações e Análise. O portal mantém a interface separada dos microserviços; não lê diretamente seus bancos de dados.

## Como executar

Na pasta raiz do projeto:

```powershell
docker compose up --build
```

Abra [http://localhost:8080](http://localhost:8080). O Portal BFF permite:

- Criar e acompanhar transações, consultar os resultados e visualizar gráficos.
- Aprovar ou rejeitar uma transação cuja análise automática esteja `REVIEW`. A ação manual não altera o resultado automático e requer responsável e justificativa.
- Executar um teste guiado de idempotência que publica duas cópias da mesma mensagem e confirma a existência de uma única análise.
- Abrir a página **Observabilidade** pelo menu para acompanhar a saúde dos serviços e dependências, visualizar eventos Kafka recentes e consultar contagem e memória das chaves do cache Redis.

O Compose também expõe as APIs dos microserviços nas portas `8081` e `8082` para diagnóstico local. As portas de gerenciamento Actuator (`8181` e `8182`) são internas à rede Docker.

## API do BFF

| Método | Rota | Descrição |
|---|---|---|
| `GET` / `POST` | `/api/transacoes` | Lista ou cria transações. |
| `GET` | `/api/transacoes/{id}/status` | Consulta o estado atualizado pelo evento Kafka. |
| `PUT` | `/api/transacoes/{id}/review-decision` | Registra decisão manual para uma análise `REVIEW`. |
| `GET` | `/api/analises/{id}` | Consulta o resultado automático. |
| `GET` | `/api/portal/health` | Consulta a saúde dos serviços e dependências. |
| `GET` | `/api/portal/kafka/events?limit=...` | Lê eventos recentes do tópico de análise. |
| `GET` | `/api/portal/redis` | Consulta informações agregadas das chaves de análise no cache. |
| `POST` | `/api/portal/demo/idempotency` | Executa o teste guiado de idempotência. |

O portal aplica timeouts às chamadas HTTP dos serviços e propaga erros de integração à interface. As operações de Kafka, Redis e idempotência usam conexões internas à rede Docker.

## Limitações e segurança

Este projeto é destinado a uma demonstração local. O portal e as APIs não implementam autenticação ou autorização; o nome do responsável por uma decisão manual é autodeclarado. Não exponha o Compose ou essas APIs a redes públicas. Para uso fora do ambiente local, implementar autenticação, autorização, TLS e gestão segura de segredos antes de disponibilizar o serviço.
