# ADR-004: tópico Kafka de resultados

- **Status:** Aceito
- **Contexto:** Além da fila de trabalho RabbitMQ, o projeto deve demonstrar um tópico Kafka sem duplicar o processamento da mesma transação por dois brokers.
- **Decisão:** RabbitMQ permanece como canal de entrada para o trabalho de análise. Depois do commit de uma nova análise, `analysis-service` publica um evento `AnalysisCompleted` no tópico `analysis.completed`, usando o ID da transação como chave. O tópico tem uma partição e uma réplica no ambiente local.
- **Alternativas consideradas:** Substituir RabbitMQ por Kafka; publicar a mesma transação nos dois brokers e consumir ambos; não adicionar um fluxo de eventos de resultados.
- **Justificativa:** Cada tecnologia tem um papel distinto: RabbitMQ distribui trabalho para o consumidor atual; Kafka mantém um fluxo de resultados que pode ser consumido por serviços independentes, como auditoria ou relatórios. Publicar apenas quando uma nova linha é inserida também evita emitir outro evento quando a mensagem de entrada é repetida.
- **Consequências:** Consumidores Kafka devem ser idempotentes, pois podem receber eventos repetidos. Publicar após o commit evita anunciar uma análise que não foi persistida, mas uma falha entre o commit e a publicação ainda pode deixar o evento faltando. O padrão Outbox é uma evolução futura. O broker local de nó único não oferece alta disponibilidade.
