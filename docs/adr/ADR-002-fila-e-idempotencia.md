# ADR-002: RabbitMQ, entrega e idempotência

[← Voltar ao README principal](../../README.md)

- **Status:** Aceito
- **Contexto:** O Serviço de Análise deve receber transações sem acoplar o processamento ao tempo de resposta da API, e mensagens repetidas não podem gerar gravações duplicadas.
- **Decisão:** `transaction-service` publica JSON no exchange `transactions.exchange`, com routing key `transactions.received`. `analysis-service` consome `transactions.queue` e registra o ID da transação como chave única. A gravação usa `ON CONFLICT DO NOTHING`. Em falhas de processamento, o consumidor faz até três tentativas, com esperas de 1 e 2 segundos; após esgotá-las, a mensagem é rejeitada e encaminhada pelo RabbitMQ para `transactions.queue.dlq`.
- **Alternativas consideradas:** Chamada HTTP síncrona entre serviços; Kafka.
- **Justificativa:** RabbitMQ atende ao fluxo simples de produtor e consumidor e foi utilizado nas aulas. A fila desacopla os serviços. A garantia é **pelo menos uma vez**: o consumidor pode receber mensagens repetidas, por isso o processamento é idempotente.
- **Consequências:** O resultado é eventual, não imediato. A DLQ exige inspeção e reprocessamento manual, ainda sem fluxo automatizado. As tentativas ocupam o consumidor durante a espera. A transferência para a DLQ com fila clássica não oferece garantia absoluta em caso de falha do broker. Sem Outbox, uma falha entre persistir dados e publicar um evento pode exigir reconciliação.
