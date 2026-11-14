# Serviço de Transações

## Objetivo

Receber solicitações de transação pela API, persistir cada uma no banco PostgreSQL que pertence a este serviço, publicar um evento no RabbitMQ para processamento assíncrono pelo Serviço de Análise e acompanhar resultados do Kafka. A interface web foi movida para o Portal BFF.

Este serviço é responsável pela entrada e pelos dados originais da transação. Não acessa o banco do Serviço de Análise nem o Redis. Ele consome os eventos de análise concluída do Kafka para atualizar o estado local da transação.

## Arquitetura e fluxo

```mermaid
flowchart LR
    Portal["Portal BFF"] -->|"POST /api/transacoes"| API["TransactionController"]
    API --> Service["TransactionService"]
    Service --> DB["PostgreSQL: transactions"]
    Service --> Publisher["TransactionPublisher"]
    Publisher -->|"transactions.exchange<br/>transactions.received"| Queue["RabbitMQ<br/>transactions.queue"]
    Kafka["Kafka<br/>analysis.completed"] --> StatusConsumer["AnalysisStatusConsumer"]
    StatusConsumer --> Service
    Portal -->|"GET /api/transacoes"| API
    Portal -->|"GET /api/transacoes/{id}/status"| API
```

1. `POST /api/transacoes` recebe valor e estabelecimento; ambos são validados.
2. `TransactionService` gera um UUID e registra a transação no banco próprio.
3. Após salvar, `TransactionPublisher` serializa e publica `TransactionReceived` no exchange `transactions.exchange`, com a routing key `transactions.received`.
4. A API responde com HTTP `202 Accepted`, o ID e o estado `RECEIVED`. A análise é assíncrona e pode ainda não estar disponível.
5. Após o Serviço de Análise publicar `AnalysisCompleted` no Kafka, `AnalysisStatusConsumer` atualiza a transação no banco deste serviço. O endpoint de acompanhamento passa a retornar `ANALYZED` e o resultado (`APPROVED` ou `REVIEW`).

O RabbitMQ oferece entrega **pelo menos uma vez**; portanto, o consumidor precisa lidar com mensagens repetidas. A garantia de não duplicar análises é implementada pelo Serviço de Análise.

## API

### Criar transação

`POST /api/transacoes`

Exemplo de requisição:

```json
{
  "valor": 125.50,
  "estabelecimento": "Loja exemplo"
}
```

`valor` deve ser positivo e `estabelecimento` não pode estar em branco. Exemplo de resposta:

```json
{
  "id": "UUID-GERADO",
  "status": "RECEIVED"
}
```

### Listar transações recentes

`GET /api/transacoes`

Retorna as 100 transações mais recentes, incluindo valor, estabelecimento, timestamps e estado/análise disponíveis. O painel usa esse endpoint para atualizar a tabela e os indicadores.

### Consultar estado e resultado da transação

`GET /api/transacoes/{transactionId}/status`

- Antes de o evento Kafka ser consumido, `status` é `RECEIVED`; `analysisStatus` e `analyzedAt` são `null`.
- Após o evento ser processado, `status` passa a `ANALYZED`; `analysisStatus` contém `APPROVED` ou `REVIEW`.
- Retorna HTTP `404 Not Found` se o ID não existir no banco deste serviço.

Exemplo após a análise:

```json
{
  "id": "UUID-DA-TRANSACAO",
  "amount": 125.50,
  "merchant": "Loja exemplo",
  "status": "ANALYZED",
  "analysisStatus": "APPROVED",
  "analysisReason": "Valor dentro do limite didático configurado",
  "createdAt": "2026-11-13T12:00:00Z",
  "analyzedAt": "2026-11-13T12:00:01Z"
}
```

### Decidir análise manualmente

`PUT /api/transacoes/{transactionId}/review-decision`

Registra uma decisão manual `APPROVED` ou `REJECTED` somente quando a análise automática está `REVIEW` e ainda não há decisão anterior. O pedido exige `decision`, `reviewedBy` e `reason`. A decisão e sua justificativa são armazenadas separadamente; `analysisStatus` e `analysisReason` continuam representando o resultado automático. Retorna `404` para uma transação inexistente e `409` para estados que não podem ser revisados. A decisão é exposta pelo portal; esta API não tem autenticação.

Exemplo de pedido:

```json
{
  "decision": "APPROVED",
  "reviewedBy": "Operador de demonstração",
  "reason": "Informações verificadas"
}
```

### Portal web

Abra [http://localhost:8080](http://localhost:8080) para criar e acompanhar transações, pesquisar, visualizar gráficos, consultar saúde e operações e registrar decisões manuais de análise `REVIEW`. O Portal BFF serve a interface e agrega as APIs dos dois microserviços; os arquivos web não pertencem a este módulo.

## Estrutura de pastas

```text
transaction-service/
├── Dockerfile
├── pom.xml
├── README.md
└── src/
    ├── main/
    │   ├── java/br/com/ada/transactions/
    │   │   ├── RabbitConfiguration.java
    │   │   ├── Transaction.java
    │   │   ├── TransactionApplication.java
    │   │   ├── TransactionController.java
    │   │   ├── AnalysisCompleted.java
    │   │   ├── AnalysisStatusConsumer.java
    │   │   ├── TransactionPublisher.java
    │   │   ├── TransactionReceived.java
    │   │   ├── TransactionRepository.java
    │   │   ├── TransactionRequest.java
    │   │   ├── TransactionResponse.java
    │   │   ├── TransactionStatusResponse.java
    │   │   └── TransactionService.java
    │   └── resources/
    │       └── application.yml
    └── test/
        └── java/br/com/ada/transactions/
            └── TransactionServiceTest.java
```

## Tecnologias e configuração

- Java 17 e Spring Boot.
- Spring Web e Bean Validation para a API.
- Spring Data JPA e PostgreSQL para persistência.
- Spring AMQP e RabbitMQ para publicação assíncrona.
- Spring Kafka para consumir os eventos de análise concluída.
- Porta HTTP: `8081`.
- Porta interna de health checks: `8181` (não publicada no host pelo Compose).
- Porta interna de health checks: `8181` (não publicada no host pelo Compose).
- Banco local no Compose: `transactions`, publicado na porta `5433`.
- Exchange, fila e routing key: `transactions.exchange`, `transactions.queue` e `transactions.received`.

Configurações padrão e variáveis de ambiente estão em `src/main/resources/application.yml`. Em execução local, os serviços de infraestrutura são fornecidos pelo Docker Compose da raiz; os passos completos estão no [README principal](../README.md).

## Testes

`TransactionServiceTest` verifica persistência/publicação de eventos, listagem, acompanhamento do resultado e regras da decisão manual, além de assegurar que uma falha ao salvar impede a publicação. O build da imagem Docker executa os testes Maven antes de empacotar o serviço.
