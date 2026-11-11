# Antifraude simplificado

Projeto didático individual para demonstrar dois serviços Spring Boot, bancos isolados, comunicação assíncrona com RabbitMQ e cache Redis. A regra de análise é fictícia e serve apenas para demonstrar o fluxo; não deve ser usada para decisões financeiras reais.

## Arquitetura

```mermaid
flowchart LR
    Cliente -->|POST /api/transacoes| T[Serviço de Transações]
    T --> DB1[(PostgreSQL: transações)]
    T -->|TransacaoRecebida| Q[RabbitMQ]
    Q --> A[Serviço de Análise]
    A --> DB2[(PostgreSQL: análises)]
    Cliente -->|GET /api/analises/{id}| A
    A --> C[(Redis: resultados por 5 min)]
```

Cada serviço é dono do seu banco. O Serviço de Transações grava e publica um evento; o Serviço de Análise consome a fila e grava o resultado. O consumidor usa o ID da transação como chave única, então uma entrega repetida não cria outra análise. A entrega é **pelo menos uma vez**: uma mensagem pode ser recebida novamente.

## Requisitos

- Docker Desktop em execução com suporte a containers Linux.
- Docker Compose v2 (`docker compose`).
- O projeto usa Java 17 dentro das imagens; não é necessário substituir o Java 8 instalado na máquina.

## Como executar

Na pasta `antifraude-simplificado`, execute:

```powershell
docker compose up --build
```

Aguarde os serviços ficarem prontos. Endpoints e ferramentas locais:

| Componente | Endereço |
|---|---|
| Serviço de Transações | http://localhost:8081 |
| Serviço de Análise | http://localhost:8082 |
| Painel RabbitMQ | http://localhost:15672 (app / app) |
| Redis | localhost:6379 |
| Banco de transações | localhost:5433 |
| Banco de análises | localhost:5434 |

Crie uma transação:

```powershell
$resposta = Invoke-RestMethod -Method Post `
  -Uri http://localhost:8081/api/transacoes `
  -ContentType 'application/json' `
  -Body '{"valor":125.50,"estabelecimento":"Loja exemplo"}'
$resposta
```

O serviço responde com o ID e o estado `RECEIVED`. A análise é assíncrona; consulte o resultado com esse ID:

```powershell
Invoke-RestMethod -Uri "http://localhost:8082/api/analises/$($resposta.id)"
```

Se a resposta ainda for `404`, aguarde a mensagem ser processada e tente novamente. Valores acima de 1.000 recebem o resultado didático `REVIEW`; os demais recebem `APPROVED`.

### Verificar idempotência

1. Crie uma transação e copie o ID retornado.
2. No painel RabbitMQ, abra **Exchanges**, selecione `transactions.exchange` e publique duas vezes a mesma mensagem JSON, usando o ID copiado e os campos do evento:

   ```json
   {
     "id": "ID-DA-TRANSACAO",
     "amount": 125.50,
     "merchant": "Loja exemplo",
     "createdAt": "2026-11-11T12:00:00Z"
   }
   ```

   Use `transactions.received` como routing key.
3. Consulte `GET /api/analises/{id}`. Deve existir somente um resultado para aquele ID.

O banco também impõe unicidade pelo ID e usa `ON CONFLICT DO NOTHING`, protegendo contra entregas repetidas mesmo se houver mais de um consumidor.

### Verificar cache

Consulte duas vezes seguidas o mesmo resultado. O endpoint usa `@Cacheable` e o Redis mantém somente resultados encontrados por 5 minutos. Resultados inexistentes não são armazenados. A gravação/atualização da análise invalida a entrada correspondente.

Para parar os serviços:

```powershell
docker compose down
```

Para apagar também os dados persistidos dos bancos (ação destrutiva):

```powershell
docker compose down -v
```

## Decisões e escopo

- [ADR-001: divisão dos serviços e bancos](docs/adr/ADR-001-servicos-e-bancos.md)
- [ADR-002: RabbitMQ, entrega e idempotência](docs/adr/ADR-002-fila-e-idempotencia.md)
- [ADR-003: cache Redis](docs/adr/ADR-003-cache.md)

O perfil de execução deste projeto é **A (com Docker)**. Retry com backoff, DLQ, Outbox e teste de contrato são melhorias opcionais; não fazem parte do escopo inicial.

## Apresentação

Roteiro sugerido para até 10 minutos, incluindo perguntas:

1. Mostrar o fluxo e o perfil A.
2. Criar uma transação e consultar o resultado.
3. Explicar a fila, a idempotência, os bancos separados e o cache.
4. Apresentar os ADRs e comentar o que foi conferido manualmente e qualquer uso de IA.
