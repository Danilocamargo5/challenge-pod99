# ADR-002: Event-Driven Architecture com EventBridge + SQS

## Contexto
Após autorizar uma transação, múltiplos sistemas precisam ser notificados:
- **Contabilidade**: registrar lançamento
- **Antifraude**: analisar padrão
- **Notificações**: alertar cliente
- **Data Platform**: armazenar para análise

## Problema
Um monolítico síncrono causaria:
- Acoplamento forte entre serviços
- Se antifraude falhar, autorização falha
- Difícil de escalar (múltiplas chamadas HTTP)

## Decisão
✅ **EventBridge (roteamento) + SQS (filas) para fan-out desacoplado**

### Fluxo
```
Authorization Service (Síncrono)
  ↓ publica evento
EventBridge Rule (filtra)
  ├→ SQS-Accounting
  ├→ SQS-Fraud
  └→ SQS-Notifications
     ↓ (cada consumer idempotente)
  Consumer 1, 2, 3 (Async, independent)
```

### Por que não alternativas?

❌ **SNS + SQS**
- Overhead: SNS é intermediário desnecessário
- Melhor: EventBridge conecta direto

❌ **Kafka / MSK**
- Complexidade: cluster, rebalancing, monitoring
- Overkill: não precisa de ordenação global (apenas por evento)
- Latência: ~100ms (aceitável mas não ganho)

❌ **SQS direto**
- Roteamento: sem filtros (tudo vai pra mesma fila)
- Escalabilidade: múltiplas filas, sem centralização

## Implementação
```yaml
EventBridge:
  - source: "pod99.authorization"
  - detail-type: "TransacaoAutorizada"
  - targets: [SQS-Accounting, SQS-Fraud, SQS-Notifications]

SQS Queues:
  - MessageRetention: 24h (auditoria)
  - VisibilityTimeout: 300s (processamento)
  - DLQ: auto (3 retries → DLQ)

Consumers:
  - Idempotentes (via event_id)
  - Tolerantes a reordenação
  - Retry automático (Spring SQS listener)
```

## Event Schema (CloudEvents)
```json
{
  "event_id": "uuid",           // Chave deduplicação
  "event_type": "TransacaoAutorizada",
  "event_version": "1.0",       // Evolução de schema
  "occurred_at": "2026-09-11T...",  // Fato de negócio
  "correlation_id": "trace-123",    // Rastreio
  "id_autorizacao": "AUTH-xyz",
  "id_contrato": "CONTA-001",
  "valor": 100.00,
  "saldo_reservado": 99900.00
}
```

## Benefícios
✅ Desacoplamento: Consumer falha, autorização continua
✅ Fan-out: múltiplos consumidores, 1 publicação
✅ Durabilidade: 24h de retenção (auditoria)
✅ Escalabilidade: consumidores trabalham em paralelo
✅ Simples: Serverless, sem operações

## Trade-offs
⚠️ Latência: ~100ms (aceitável pra domínio financeiro)
⚠️ Eventual consistency: Contabilidade não registra na mesma transação
  - Mitigado: timeout de 24h + reconciliação noturna
⚠️ Reordenação: eventos podem chegar fora de ordem
  - Mitigado: Consumidores devem ser idempotentes por event_id

## Decisão Final
**APROVADO** - EventBridge + SQS é o padrão AWS moderno, escalável e simples.
