# ADR-005: Escolha de Serviço de Fila/Streaming - SQS vs Kinesis vs Kafka

**Status:** DECIDED ✅  
**Data:** 11 de Setembro 2026  
**Decisão:** USE SQS (Simple Queue Service)

---

## 1. CONTEXTO

POD99 precisa processar **~5.000 TPS** (transações por segundo) com arquitetura event-driven. O documento de requisitos (Seção 3.3) pede avaliação de:

> "SQS vs Kinesis vs Kafka (MSK) para alta volumetria e ordenação por chave partição"

Esta ADR compara os três serviços nos critérios técnicos, custo e trade-offs para recomendar o mais adequado.

---

## 2. CARACTERÍSTICAS TÉCNICAS

### 2.1 Throughput

#### SQS (Simple Queue Service)
- **Throughput máximo**: Unlimited (depende de throttling)
- **Unidade de cobrança**: Por mensagem (0.4M = $0.40)
- **Garantia de entrega**: At-least-once
- **Taxa de transferência**: Escalável automaticamente
- **Para 5k TPS**: 
  - 5.000 msg/s = 432.000.000 msg/dia
  - ✅ Suporta perfeitamente

#### Kinesis Data Streams
- **Throughput máximo**: Ilimitado (shards escaláveis)
- **Unidade de cobrança**: Por shard-hour ($0.36/hora)
- **Garantia de entrega**: Exactly-once (com processador)
- **Taxa de transferência**: 1.000 registros/segundo por shard
- **Para 5k TPS**:
  - 5 shards × 1.000 rec/s = 5.000 rec/s
  - ✅ Suporta com 5 shards on-demand

#### Kafka (Amazon MSK)
- **Throughput máximo**: Depende de brokers
- **Unidade de cobrança**: Por broker-hour (~$0.50/hora) + storage
- **Garantia de entrega**: Exactly-once (com configuração)
- **Taxa de transferência**: Customizável (tipicamente 10k-100k msg/s por broker)
- **Para 5k TPS**:
  - 1 broker é suficiente
  - ✅ Suporta, mas precisa gerenciar

---

### 2.2 Ordenação e Particionamento

#### SQS
```
❌ Standard Queue: Sem garantia de ordem
✅ FIFO Queue: Ordem garantida, MAS:
   - Throughput máximo: 3.000 msg/s (300 com batch)
   - Mais caro que Standard
   - Para POD99 (5k TPS) → PROBLEMA!
   
Solução: SQS Standard + ordering por id_conta em consumer
```

#### Kinesis
```
✅ Partition Key: Garante ordem por chave
   - Todos os eventos com mesma id_conta → mesma partição
   - Preserva ordem dentro da partição
   - Para POD99: IDEAL para ordenação por conta
   
Exemplo:
  event = { id_conta: "ACC-001", ... }
  partition_key = event.id_conta  // Garante FIFO por conta
```

#### Kafka
```
✅ Topic Partition: Garante ordem por chave
   - Semelhante ao Kinesis
   - Partition key = id_conta → mesmo partition
   - Preserva ordem global por partition
   
Exemplo:
  ProducerRecord<String, Event> record = 
    new ProducerRecord<>("transactions", "ACC-001", event);
    // ACC-001 sempre → partition 0
```

---

### 2.3 Latência

#### SQS
```
- Recebimento: ~1-2ms
- Visibilidade: Imediata após publicar
- Consumer polling: 0-20s (long-polling default)
- P99: ~5-10ms

⚠️ Problema: Latência de polling pode adicionar segundos
```

#### Kinesis
```
- Recebimento: ~200ms (propagação para shards)
- Visibilidade: Imediata após publicar
- Consumer (Iterator): ~1 segundo
- P99: ~500ms

✅ Melhor para latência crítica (<1s)
```

#### Kafka
```
- Recebimento: ~5-10ms (depende de acks)
- Visibilidade: Imediata
- Consumer lag: Configurável
- P99: ~10-20ms

✅ Melhor latência se configurar acks=1
```

---

### 2.4 Garantias de Entrega

#### SQS
```
At-least-once (pode duplicar)
- Mensagem pode ser recebida 1+ vezes
- Requer idempotência no consumer
- DLQ para retry automático

Para POD99:
✅ Idempotência por event_id (já implementado)
```

#### Kinesis
```
At-least-once (sem duplicação se usar Iterator corretamente)
- Registros não são deletados (retention 24h default)
- Consumer pode re-processar
- Requer idempotência

Para POD99:
✅ Idempotência por event_id (necessária)
```

#### Kafka
```
Exactly-once (com transações)
- Configurar: enable.idempotence=true
- Requer producer/consumer coordenação
- Mais complexo de implementar

Para POD99:
✅ Possível, mas overhead (Kinesis mais simples)
```

---

### 2.5 Scaling e Operações

#### SQS
```
✅ Autoscaling: Automático
✅ Operações: Zero-ops (serverless)
✅ Monitoramento: CloudWatch nativo
❌ Customização: Limitada

Scaling behavior:
- Aumenta automaticamente se receber 5k+ msg/s
- Reduz automaticamente quando tráfego cai
- Custo sobe automaticamente (paga por uso)
```

#### Kinesis
```
✅ On-Demand Mode: Autoscaling automático
⚠️ Provisioned Mode: Requer manual capacity planning
✅ Operações: Serverless (on-demand)
✅ Monitoramento: CloudWatch
⚠️ Customização: Moderada (shard count)

Scaling behavior (on-demand):
- Escalas a 200 MB/s automaticamente
- Inicia em 4 MB/s (custo mínimo)
- Para POD99 (5k TPS ~2.5 MB/s): ✅ On-demand é ideal
```

#### Kafka
```
❌ Autoscaling: Manual (precisa provisionar brokers)
❌ Operações: Alto overhead (gerenciar cluster)
⚠️ Monitoramento: Requer ferramentas externas (Prometheus, ELK)
✅ Customização: Total (tuning, replicação, etc)

Scaling behavior:
- Adicionar brokers manualmente
- Gerenciar rebalanceamento de partições
- Alto operational burden
```

---

### 2.6 Compatibilidade com POD99 Stack

#### SQS
```
✅ EventBridge → SQS native integration
✅ Lambda consumer (AccountingEventListener)
✅ Terraform support (aws_sqs_queue)
✅ Java SDK (AWS SDK v2)
✅ Idempotência já implementada

Acoplamento: Médio (AWS-specific, portável via mensagens)
```

#### Kinesis
```
✅ EventBridge → Kinesis via custom integration
⚠️ Lambda consumer (precisa de KinesisEventSource mapping)
✅ Terraform support (aws_kinesis_stream)
✅ Java SDK (AWS SDK v2)
✅ Idempotência já implementada

Acoplamento: Médio (AWS-specific, alternativa viável)
```

#### Kafka
```
❌ EventBridge → Kafka (sem integração nativa)
❌ Lambda consumer (requer connectors complexos)
✅ Terraform support (aws_msk_cluster)
✅ Java SDK (Apache Kafka)
✅ Idempotência já implementada

Acoplamento: Alto (requer trabalho extra de integração)
Alternativa: Usar AWS EventBridge → SQS → bridge para Kafka
```

---

## 3. ANÁLISE DE CUSTO

### 3.1 Cenário Base: 5.000 TPS por 30 dias

**Assumindo:**
- 5.000 transações/segundo (pico)
- 3 eventos por transação (autorização, limite, contabilização)
- Tamanho médio do evento: 2 KB
- Operação: 24/7 por 30 dias

**Volume total:**
```
5.000 TPS × 3 eventos × 86.400 segundos/dia × 30 dias
= 12.960.000.000 eventos/mês
= 12,96 bilhões de eventos
= 25.920 GB = 25,92 TB de dados
```

---

### 3.2 SQS (Simple Queue Service)

**Requisições (Put, Get, Delete):**
```
Preço: $0.40 por 1 milhão de requisições

Com batch (Put 10, Get 10, Delete 10):
  12,96B mensagens ÷ 10 × ($0.40 / 1.000.000)
  = $518/mês
```

**Data Transfer Out (DLQ, monitoring):**
```
Preço: $0.09 por GB (DLQ, CloudWatch Logs)

Assumindo 1% de retry (DLQ):
  25.920 GB × 1% × $0.09
  = $23/mês
```

**TOTAL SQS STANDARD: ~$540/mês**

```
Breakdown:
  Requisições (batch): $518
  Data transfer (DLQ):  $23
  ───────────────────
  TOTAL:               $540/mês
```

---

### 3.3 Kinesis Data Streams

**On-Demand Mode (recomendado para 5k TPS):**
```
Preço: $0.40 por 1 milhão de registros + $0.217/GB

Registros:
  12,96B registros × ($0.40 / 1.000.000)
  = $5.184/mês

Data transfer:
  25.920 GB × $0.217
  = $5.624/mês
```

**Enhanced Fan-Out (se usar Kinesis Firehose):**
```
  12,96B registros × ($0.076 / 1.000.000)
  = $985/mês
```

**TOTAL KINESIS ON-DEMAND: ~$11.793/mês**

```
Breakdown:
  Registros:          $5.184
  Data transfer:      $5.624
  Enhanced fan-out:   $985
  ───────────────────
  TOTAL:             $11.793/mês
```

**OU com Provisioned Mode (5 shards):**
```
TOTAL Provisioned: ~$6.920/mês
  (Mais barato se tráfego previsível, mas requer capacity planning)
```

---

### 3.4 Kafka (Amazon MSK - Managed Streaming)

**Broker Nodes (3 brokers mínimo):**
```
kafka.m5.large: $0.119/hora por broker
  3 brokers × $0.119/hora × 24h × 30 dias
  = $771.84/mês
```

**Storage (EBS):**
```
7 dias retenção (padrão):
  ~$30/mês (com compactação)
```

**Traffic (Data Transfer):**
```
Inter-broker traffic:
  25.920 GB × $0.01
  = $259/mês
```

**CloudWatch Monitoring:**
```
  ~$15/mês
```

**TOTAL KAFKA MSK: ~$1.076/mês**

```
Breakdown:
  Broker nodes:       $771.84
  Storage:            $30
  Traffic:            $259
  CloudWatch:         $15
  ───────────────────
  TOTAL:            ~$1.076/mês

⚠️ MAS: Requer operational overhead (não é zero-ops!)
```

---

### 3.5 Comparação Visual

```
Custo Mensal (5k TPS, 30 dias)

SQS Standard:
  ████                                    $540

Kafka MSK (m5.large, 3 brokers):
  ██████████████                         $1.076

Kinesis Provisioned (5 shards):
  █████████████                          $6.920

Kinesis On-Demand:
  ███████████████████████                $11.793

─────────────────────────────────────────────────
  $0    $2k   $4k   $6k   $8k   $10k  $12k
```

---

### 3.6 Custo Anual (Projeção)

| Serviço | Mensal | Anual | Economia vs SQS |
|---------|--------|-------|-----------------|
| **SQS Standard** | $540 | $6.480 | - |
| **Kafka MSK** | $1.076 | $14.400 | -122% (mais caro) |
| **Kinesis Provisioned** | $6.920 | $83.040 | -1.182% (MUITO caro) |
| **Kinesis On-Demand** | $11.793 | $141.516 | -2.086% (MUITO MUITO caro) |

---

### 3.7 Custo por Transação

```
12,96 bilhões eventos/mês

SQS:           $540 ÷ 12,96B = $0,0000417/evento (0.004¢)
Kafka:        $1.076 ÷ 12,96B = $0,0000830/evento (0.008¢)
Kinesis Prov: $6.920 ÷ 12,96B = $0,000534/evento (0.053¢)
Kinesis OD:  $11.793 ÷ 12,96B = $0,000910/evento (0.091¢)

✅ SQS é 2x mais barato que Kafka
✅ SQS é 10x-20x mais barato que Kinesis
```

---

## 4. TRADE-OFFS E MATRIZ DE DECISÃO

### 4.1 Trade-offs por Serviço

#### SQS - Trade-offs

**VANTAGENS:**
```
✅ Custo: $540/mês (mais barato)
✅ Zero-ops: Serverless completo
✅ Escalabilidade: Automática
✅ Integração: EventBridge nativo
✅ Confiabilidade: 99.99% SLA
✅ Simplicidade: Fácil de usar
✅ Monitoramento: CloudWatch nativo
```

**DESVANTAGENS:**
```
❌ Sem ordem garantida (padrão)
❌ At-least-once (pode duplicar)
❌ Latência: Até 20s (polling)
❌ FIFO não escala (máx 3k TPS)
❌ Sem retenção longa (max 14 dias)
❌ Consumer precisa tratar duplicatas
```

**Trade-off aceitável para POD99?**
```
✅ SIM - Porque:
   - Idempotência já implementada (event_id)
   - Ordering por id_conta (não global) é suficiente
   - Latência 20s é OK para contabilização assíncrona
   - Custo 12x menor que Kinesis
```

---

#### Kinesis - Trade-offs

**VANTAGENS:**
```
✅ Ordem garantida: Por partition key (id_conta)
✅ Latência: 200ms-500ms (streaming real-time)
✅ Retenção: Customizável (até 365 dias)
✅ On-Demand: Autoscaling automático
✅ Exactly-once: Com configuração (melhor que SQS)
✅ Reprocessamento: Fácil (iterator pode replay)
✅ Confiabilidade: 99.99% SLA
```

**DESVANTAGENS:**
```
❌ Custo: $6.920-$11.793/mês (12x-20x mais caro)
❌ Provisioned: Requer capacity planning manual
❌ Complexidade: Mais config que SQS
❌ Integração EventBridge: Não é nativa (custom setup)
❌ Lambda: Requer KinesisEventSourceMapping
❌ Data transfer: Charges altas
```

**Trade-off aceitável para POD99?**
```
❌ NÃO - Porque:
   - Custo não justificado ($11.793 vs $540)
   - Ordem por partition key é luxo (não crítico)
   - Latência 200ms vs 20s não importa para contabilização
   - Integração EventBridge é complexa
   - Overkill para 5k TPS
   
✅ PODERIA fazer sentido SE:
   - TPS crescesse para 100k+
   - Precisasse retenção de 30+ dias
   - Latência <500ms fosse crítico
   - Budget não importasse
```

---

#### Kafka - Trade-offs

**VANTAGENS:**
```
✅ Ordem garantida: Por partition (como Kinesis)
✅ Retenção: Ilimitada (com storage)
✅ Throughput: Altíssimo (100k+ msg/s por cluster)
✅ Flexibilidade: Tuning completo
✅ Portabilidade: Não é AWS-locked
✅ Polyglot: Linguagens diversas (Java, Go, Python, Node)
✅ Ecossistema: Kafka Connect, Streams, etc
```

**DESVANTAGENS:**
```
❌ Custo: $1.076-$1.832/mês (2x mais que SQS)
❌ Operações: Alto overhead (gerenciar cluster)
❌ Integração EventBridge: Zero (não existe)
❌ Complexidade: Requer expertise Kafka
❌ Escalabilidade: Manual (add brokers manualmente)
❌ Monitoramento: Precisa ferramentas externas
❌ Disaster recovery: Manual
❌ Para POD99: Overkill (não precisa 100k TPS)
```

**Trade-off aceitável para POD99?**
```
❌ NÃO - Porque:
   - Custo 2x mais que SQS (desnecessário)
   - Operações custosas (requer DevOps expertise)
   - Sem integração EventBridge (workaround complexo)
   - Overkill para 5k TPS
   - Não agrega valor para contabilização assíncrona
   
✅ PODERIA fazer sentido SE:
   - Precisasse de Kafka para outras equipes/casos
   - TPS crescesse para 50k+ (amortiza custo operacional)
   - Precisasse de retenção de 90+ dias
   - Já existisse expertise Kafka na empresa
```

---

### 4.2 Seletor: Quando Usar Cada Um

**SQS (Simple Queue):**
```
USE SE:
• Custo é prioridade
• Ordem não é crítico (ou por id_conta é OK)
• At-least-once é aceitável (idempotência no consumer)
• AWS-only é OK
• Latência de 20s é tolerável
• TPS < 50k
• Equipe quer zero-ops

EXEMPLOS: Contabilização, notificações, logs
```

**Kinesis (Streaming):**
```
USE SE:
• Latência < 500ms é crítico
• Ordem por chave é requisito
• Retenção > 24h é necessária
• Real-time analytics/dashboards
• TPS 50k-500k
• Budget generoso
• AWS-only é OK

EXEMPLOS: Real-time fraud detection, clickstream
```

**Kafka (Full Control):**
```
USE SE:
• Ordem garantida é crítico
• TPS > 100k
• Retenção > 30 dias necessária
• Portabilidade multi-cloud é requisito
• Ecossistema Kafka precisa (Streams, Connect)
• Time tem expertise Kafka
• Polyglot (múltiplas linguagens)

EXEMPLOS: Data warehouse, event hub central, streaming
```

---

### 4.3 Matriz Comparativa Simplificada

```
┌──────────────────┬─────────┬──────────┬────────┐
│ Critério         │   SQS   │ Kinesis  │ Kafka  │
├──────────────────┼─────────┼──────────┼────────┤
│ Custo            │ ⭐⭐⭐⭐⭐ │ ⭐⭐     │ ⭐⭐⭐  │
│ Latência         │ ⭐⭐    │ ⭐⭐⭐⭐  │ ⭐⭐⭐⭐ │
│ Ordem garantida  │ ⭐     │ ⭐⭐⭐⭐⭐ │ ⭐⭐⭐⭐⭐ │
│ Zero-ops         │ ⭐⭐⭐⭐⭐ │ ⭐⭐⭐   │ ⭐     │
│ Escalabilidade   │ ⭐⭐⭐⭐⭐ │ ⭐⭐⭐⭐  │ ⭐⭐⭐⭐ │
│ Integração AWS   │ ⭐⭐⭐⭐⭐ │ ⭐⭐⭐⭐  │ ⭐     │
│ Retenção         │ ⭐⭐    │ ⭐⭐⭐⭐  │ ⭐⭐⭐⭐⭐ │
│ Curva aprendizado│ ⭐⭐⭐⭐⭐ │ ⭐⭐⭐   │ ⭐⭐   │
└──────────────────┴─────────┴──────────┴────────┘

Nota: ⭐⭐⭐⭐⭐ = Melhor, ⭐ = Pior
```

---

### 4.4 Análise Específica para POD99

#### Requisitos POD99

```
1. 5.000 TPS
2. Latência p99 < 100ms (para autorização)
3. Idempotência garantida
4. Event-driven (EventBridge + SQS)
5. Async processing (contabilização)
6. Ordering por id_conta (não global)
7. Cost-effective (Itaú Unibanco quer economizar)
8. Zero-ops (equipe pequena)
9. AWS native (já usando AWS)
```

#### Avaliação contra cada serviço

**SQS:**
```
✅ 5.000 TPS:                 Suporta (unlimited)
✅ Latência p99 < 100ms:      PARCIAL (20s para async é OK)
✅ Idempotência:              Implementada (event_id)
✅ EventBridge nativo:        SIM
✅ Async processing:          IDEAL
⚠️ Ordering por id_conta:     Não garantido, mas consumer trata
✅ Cost-effective:            EXCELENTE ($540/mês)
✅ Zero-ops:                  SIM (serverless)
✅ AWS native:                SIM

SCORE: 9/10 ✅
```

**Kinesis:**
```
✅ 5.000 TPS:                 Suporta (on-demand)
✅ Latência p99 < 100ms:      SIM (200ms)
✅ Idempotência:              Implementada (event_id)
❌ EventBridge nativo:        Custom setup necessário
✅ Async processing:          Suporta
✅ Ordering por id_conta:     GARANTIDO
❌ Cost-effective:            NÃO ($11k/mês vs $540)
✅ Zero-ops:                  SIM (on-demand)
✅ AWS native:                SIM

SCORE: 6/10 ⚠️ (custo não justificado)
```

**Kafka:**
```
✅ 5.000 TPS:                 Suporta (overkill)
✅ Latência p99 < 100ms:      SIM (5-10ms)
✅ Idempotência:              Implementada (event_id)
❌ EventBridge nativo:        Zero integração
✅ Async processing:          Suporta
✅ Ordering por id_conta:     GARANTIDO
❌ Cost-effective:            NÃO ($1.2k/mês + ops)
❌ Zero-ops:                  NÃO (precisa gerenciar cluster)
⚠️ AWS native:                Parcial (MSK, mas não nativo)

SCORE: 4/10 ❌ (overkill, custoso)
```

---

## 5. RECOMENDAÇÃO FINAL

### ✅ USE: **SQS (Simple Queue Service)**

#### Justificativa

```
┌──────────────────────────────────────────────────────────┐
│  SQS é a escolha CORRETA para POD99 porque:             │
├──────────────────────────────────────────────────────────┤
│                                                          │
│ 1. CUSTO (Fator crítico para Itaú)                      │
│    $540/mês vs $1.2k-$11k (outros)                      │
│    Economiza $10.8k-$131k/ano                           │
│    ✅ ROI imediato                                       │
│                                                          │
│ 2. INTEGRAÇÃO (Já implementado)                         │
│    EventBridge → SQS é NATIVO                           │
│    Não precisa mudar Terraform                          │
│    Não precisa recodificar AccountingEventListener      │
│    ✅ Reduz risco                                        │
│                                                          │
│ 3. OPERAÇÕES (Zero-ops)                                 │
│    Serverless completo                                  │
│    Autoscaling automático                               │
│    Não precisa DevOps experts                           │
│    ✅ Equipe pode focar em features                      │
│                                                          │
│ 4. IDEMPOTÊNCIA (Já coberto)                            │
│    event_id gerado                                      │
│    Consumer trata duplicatas                            │
│    At-least-once é aceitável                            │
│    ✅ Sem overhead extra                                 │
│                                                          │
│ 5. CASO DE USO ACEITA                                   │
│    Contabilização é ASYNC (20s latência OK)             │
│    Não precisa ordem global (por id_conta é suficiente)│
│    5k TPS está longe do limite SQS                      │
│    ✅ Tecnicamente adequado                              │
│                                                          │
│ 6. ESCALABILIDADE FUTURA                                │
│    Cresce de 5k para 50k TPS sem mudança                │
│    Custo sobe linearmente (previsível)                  │
│    ✅ Caminho claro para crescimento                     │
└──────────────────────────────────────────────────────────┘
```

---

### 📊 Decisão Comparativa

```
                    POD99 ATUAL (5k TPS)

┌────────────────────────────────────────────────────┐
│ MELHOR CUSTO:        SQS ($540/mês)       ✅ ESCOLHER│
│ MELHOR LATÊNCIA:     Kafka (5ms)          ❌ Overkill│
│ MELHOR ORDEM:        Kinesis/Kafka        ❌ Overkill│
│ MELHOR OPS:          SQS                  ✅ Incluso  │
│ MELHOR INTEGRAÇÃO:   SQS + EventBridge    ✅ Nativo   │
└────────────────────────────────────────────────────┘

VENCEDOR: SQS ✅
```

---

### ⚠️ Quando Reconsiderar (Roadmap Futuro)

```
SE no futuro ocorrer:

1. TPS crescer para 50k+
   → Considerar Kinesis On-Demand
   → Ou Kafka (amortiza custo ops)

2. Precisar retenção > 7 dias
   → SQS max 14 dias
   → Considerar Kinesis (365 dias)

3. Latência crítico < 100ms
   → Kinesis/Kafka melhor
   → Mas SQS 20s é aceitável agora

4. Precisar ordem GLOBAL garantida
   → Kinesis/Kafka obrigatório
   → Mas SQS por id_conta atual é OK

5. Múltiplos subscribers para mesmo evento
   → Adicionar SNS (fan-out)
   → Manter SQS como final subscriber
```

---

### 🎯 Estratégia: SQS Agora + Plano B

```
IMPLEMENTAÇÃO:
✅ SQS Standard (atual - recomendado)

MONITORAMENTO:
  - Se SQS latência > 5s: Escalou problema
  - Se TPS > 10k: Considerar Kinesis
  - Se custo > $1k/mês: Revisitar

PLANO B (Se precisar mudar):
  - Kinesis On-Demand é drop-in replacement
  - Kafka requer recodificação (alto risco)
  - SNS + SQS (fan-out) sem custo adicional

ROADMAP SUGERIDO:
  MVP (5-30 dias):        SQS Standard ✅
  Prod (1-3 meses):       SQS + monitoring
  Escalabilidade (3-6m):  Avaliar crescimento
  Se TPS > 20k (6m+):     Migrar Kinesis
```

---

## 6. CONCLUSÃO

### ✅ **Recomendação: USE SQS**

**Por quê:**
- Melhor custo ($540/mês)
- Integração nativa EventBridge
- Zero-ops (serverless)
- Adequado para 5k TPS
- Idempotência já implementada
- Escalável até 50k+ TPS

**Quando mudar:**
- Se TPS > 50k
- Se latência < 100ms crítico
- Se ordem global obrigatória
- Se retenção > 14 dias necessária

**Risco:**
- Baixo (SQS é maduro, 99.99% SLA)
- Caminho claro para Kinesis se precisar

---

### Tabela Final: SQS vs Alternativas

| Aspecto | SQS ✅ | Kinesis | Kafka |
|---------|--------|---------|-------|
| Custo (5k TPS) | $540/mês | $11.793/mês | $1.076/mês |
| Custo anual | $6.480 | $141.516 | $14.400 |
| Adequado para POD99? | ✅ SIM | ❌ NÃO | ❌ NÃO |
| Integração EventBridge | ✅ Nativo | ⚠️ Custom | ❌ Zero |
| Zero-ops | ✅ SIM | ✅ SIM | ❌ NÃO |
| Escalabilidade | ✅ Auto | ✅ Auto | ⚠️ Manual |

**DECISÃO FINAL: SQS**

---

## REFERÊNCIAS

- [AWS SQS Pricing](https://aws.amazon.com/sqs/pricing/)
- [AWS Kinesis Pricing](https://aws.amazon.com/kinesis/pricing/)
- [AWS MSK Pricing](https://aws.amazon.com/msk/pricing/)
- [SQS vs Kinesis Comparison](https://docs.aws.amazon.com/kinesis/latest/dev/services-sqs.html)
- [EventBridge Routing](https://docs.aws.amazon.com/eventbridge/latest/userguide/eb-rules.html)

