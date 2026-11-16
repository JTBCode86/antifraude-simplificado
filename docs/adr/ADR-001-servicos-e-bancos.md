# ADR-001: divisão dos serviços e bancos

[← Voltar ao README principal](../../README.md)

- **Status:** Aceito
- **Contexto:** O projeto precisa demonstrar serviços separados, cada um responsável pelo seu próprio banco.
- **Decisão:** Separar a entrada de transações do processamento antifraude. `transaction-service` persiste transações no PostgreSQL `transactions`; `analysis-service` persiste resultados no PostgreSQL `analyses`. Nenhum serviço acessa o banco do outro.
- **Alternativas consideradas:** Um único serviço e banco; dois serviços compartilhando o mesmo banco.
- **Justificativa:** A separação evidencia limites de responsabilidade e propriedade dos dados, além de atender ao requisito de autonomia dos bancos. A execução local usa Docker Compose para facilitar a demonstração.
- **Consequências:** Há mais componentes para iniciar e a comunicação é eventual: a análise pode não estar disponível imediatamente após a criação da transação.
