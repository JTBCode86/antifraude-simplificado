# Antifraude simplificado

Projeto didático individual para demonstrar dois serviços Spring Boot, bancos isolados, uma fila RabbitMQ, um tópico Kafka e cache Redis. A regra de análise é fictícia e serve apenas para demonstrar o fluxo; não deve ser usada para decisões financeiras reais.

## Arquitetura

**Diagrama editável:** [Abrir arquitetura no draw.io](docs/arquitetura-antifraude.drawio)

| Microserviço | Responsabilidade | Documentação |
|---|---|---|
| **Transações** | Recebe e registra transações; publica eventos no RabbitMQ. | [Ver README](transaction-service/README.md) |
| **Análise** | Analisa transações; consulta o Redis e publica resultados no Kafka. | [Ver README](analysis-service/README.md) |

```mermaid
flowchart LR
    Cliente["Cliente"] -->|"POST transacoes"| T["Servico de Transacoes"]
    T --> DB1["Banco de transacoes (PostgreSQL)"]
    T -->|"TransacaoRecebida"| Q["RabbitMQ"]
    Q --> A["Servico de Analise"]
    A --> DB2["Banco de analises (PostgreSQL)"]
    A -->|"AnalysisCompleted"| K["Kafka: analysis.completed"]
    Cliente -->|"Consultar analise por ID"| A
    A --> C["Cache Redis (5 min)"]
```

Cada serviço é dono do seu banco. O Serviço de Transações grava e publica uma mensagem no RabbitMQ; o Serviço de Análise consome a fila, grava o resultado e publica `AnalysisCompleted` no tópico Kafka `analysis.completed`. RabbitMQ distribui o trabalho para análise; Kafka fornece um fluxo de resultados para consumidores independentes. O consumidor RabbitMQ usa o ID da transação como chave única, então uma entrega repetida não cria outra análise nem publica outro resultado. A entrega da fila é **pelo menos uma vez**: uma mensagem pode ser recebida novamente.

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
| Kafka (broker local) | localhost:9092 |
| Redis | localhost:6379 |
| Banco de transações | localhost:5433 |
| Banco de análises | localhost:5434 |

## Testes automatizados

Os testes unitários usam JUnit 5 e Mockito e cobrem a criação/publicação de transações, a regra didática de análise, a idempotência e a consulta de resultados. O build Docker executa esses testes antes de empacotar cada serviço.

Para executá-los sem instalar Java ou Maven na máquina, abra o PowerShell na pasta do projeto:

```powershell
docker run --rm `
  --mount "type=bind,source=$($PWD.Path),target=/workspace" `
  --workdir /workspace `
  maven:3.9.9-eclipse-temurin-17 `
  mvn -B test
```

Uma saída com `BUILD SUCCESS` e `Tests run: 7, Failures: 0, Errors: 0` indica que os testes passaram.

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

### Verificar o tópico Kafka

Após processar uma transação, abra outro terminal PowerShell na pasta do projeto e execute:

```powershell
docker compose exec kafka /opt/kafka/bin/kafka-console-consumer.sh `
  --bootstrap-server localhost:9092 `
  --topic analysis.completed `
  --from-beginning
```

O consumidor de console exibe eventos JSON `AnalysisCompleted`; pressione `Ctrl+C` para encerrá-lo. O tópico é criado automaticamente quando o Serviço de Análise inicia. O Kafka usa uma partição e uma réplica porque o broker local é único; isso é adequado para demonstração, não para alta disponibilidade.

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
- [ADR-004: tópico Kafka de resultados](docs/adr/ADR-004-topico-kafka.md)

O perfil de execução deste projeto é **A (com Docker)**. Retry com backoff, DLQ, Outbox e teste de contrato são melhorias opcionais; não fazem parte do escopo inicial. A publicação no Kafka e a gravação no banco são duas operações separadas; Outbox seria o próximo passo para garantir que o evento publicado nunca fique divergente do resultado persistido.

## Apresentação

Roteiro sugerido para até 10 minutos, incluindo perguntas:

1. Mostrar o fluxo e o perfil A.
2. Criar uma transação, consultar o resultado e mostrar o evento no tópico Kafka.
3. Explicar os papéis distintos de RabbitMQ e Kafka, a idempotência, os bancos separados e o cache.
4. Apresentar os ADRs e comentar o que foi conferido manualmente e qualquer uso de IA.
