# ADR-002: RabbitMQ, entrega e idempotência

- **Status:** Aceito
- **Contexto:** O Serviço de Análise deve receber transações sem acoplar o processamento ao tempo de resposta da API, e mensagens repetidas não podem gerar gravações duplicadas.
- **Decisão:** `transaction-service` publica JSON no exchange `transactions.exchange`, com routing key `transactions.received`. `analysis-service` consome `transactions.queue` e registra o ID da transação como chave única. A gravação usa `ON CONFLICT DO NOTHING`.
- **Alternativas consideradas:** Chamada HTTP síncrona entre serviços; Kafka.
- **Justificativa:** RabbitMQ atende ao fluxo simples de produtor e consumidor e foi utilizado nas aulas. A fila desacopla os serviços. A garantia é **pelo menos uma vez**: o consumidor pode receber mensagens repetidas, por isso o processamento é idempotente.
- **Consequências:** O resultado é eventual, não imediato. Retry com backoff, DLQ e Outbox são melhorias futuras; sem Outbox, uma falha entre persistir a transação e publicar o evento pode exigir reconciliação.
