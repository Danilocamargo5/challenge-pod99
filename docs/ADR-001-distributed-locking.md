# ADR-001: Distributed Locking with LockService

## Contexto
A plataforma de autorização POD99 precisa garantir que duas requisições concorrentes **não debitam o mesmo limite** simultaneamente. Isso é crítico em domínio financeiro.

## Problema
Em um ambiente distribuído com alta concorrência (~5k TPS), múltiplas threads podem:
1. Ler limite disponível = 1000
2. Ambas tentam debitar 100
3. Ambas conseguem (race condition)
4. Limite final fica incorreto

## Decisão
✅ **Implementar LockService com retry automático e ConditionExpression do DynamoDB**

### Características
- **Atomic Lock Acquisition**: PutItem + ConditionExpression ("attribute_not_exists")
- **Retry Strategy**: 5 tentativas com exponential backoff (100ms × tentativa)
- **LIFO Release**: Libera locks em ordem reversa para evitar deadlock
- **Auto-cleanup**: TTL de 30 segundos (se processo crash, lock se auto-limpa)

### Por que não alternativas?

❌ **Conditional Update simples**
- Problema: Se falha, cliente recebe erro direto
- Nosso caso: Cliente pode retentar, mas não automaticamente

❌ **Kafka/Kinesis**
- Overkill: Ordenação global não é necessária (apenas por contrato)
- Latência: 100ms+ (inaceitável pra autorização síncrona)

❌ **Redis distributed locks**
- Adiciona dependência externa
- Mais complexo pra demonstrar em ambiente local

## Implementação
```java
// Adquire locks sequencialmente com retry
List<String> locks = lockService.acquireTransactionLocks(idAutorizacao, idContrato);

try {
    // Executa operação protegida
    performAuthorization();
} finally {
    // Libera em ordem REVERSA (LIFO)
    lockService.releaseLocks(locks);
}
```

## Benefícios
✅ Atomicidade garantida (ConditionExpression)
✅ Resilência (retry automático 5×)
✅ Deadlock-free (LIFO release)
✅ Self-healing (TTL auto-cleanup)
✅ Simples de testar localmente (DynamoDB local)

## Trade-offs
⚠️ Latência: retry delay pode adicionar até 1.5s em race condition
- Aceitável: Melhor do que transação falhada
⚠️ Múltiplos locks: exige ordem consistente (sempre: auth → contrato)
- Mitigado: Ordem fixa no código

## Decisão Final
**APROVADO** - LockService é o padrão robusto, production-grade para nosso caso.
