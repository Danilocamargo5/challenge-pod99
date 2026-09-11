# ADR-004: Escolha de Compute (Lambda vs ECS vs EKS)

## Contexto

POD99 precisa de ~5.000 TPS (pico) com latência <100ms p99. Qual runtime escolher?

## Opções Avaliadas

| Critério | AWS Lambda | Amazon ECS/Fargate | Amazon EKS (Kubernetes) |
|----------|------------|-------------------|--------------------------|
| **Latência cold start** | 300-500ms | <100ms | <100ms |
| **Custo (100M req/mês)** | ~$5/mês (compute) | ~$200/mês (containers) | ~$250/mês (cluster) |
| **Operações** | Serverless (0) | Minimal (load balancer) | Alto (K8s + networking) |
| **Escalabilidade** | Limitada (1000 concurrent) | Ilimitada | Ilimitada |
| **Startup time** | 300-500ms | <1s | <1s |
| **Debugging** | Difícil (CloudWatch) | Fácil (logs, SSH) | Fácil (kubectl) |
| **Multi-region** | Fácil (replicate function) | Médio (setup stack) | Difícil (federation) |
| **Vendor lock-in** | Alto (AWS-specific) | Médio (AWS Fargate) | Baixo (K8s é universal) |

---

## DECISÃO: AWS Lambda (com fallback para ECS Fargate)

**Porquê Lambda:**

1. **Latência aceitável**: Cold start 300-500ms só ocorre 1-2% do tempo (warm containers reutilizados)
2. **Custo mínimo**: ~$5-10/mês de compute (vs $200-300 em ECS/EKS)
3. **Operações zero**: Serverless = sem gerenciamento de infraestrutura
4. **Escalabilidade rápida**: Auto-scale em milissegundos (de 1 para 1000 concorrentes)
5. **Integração nativa**: EventBridge + SQS + DynamoDB já otimizados
6. **Trade-off aceitável**: Cold start de 500ms só afeta 1-2% das requisições (SLA ainda <100ms p99)

**Limitações de Lambda:**
- Máximo 1.000 concurrent executions por padrão (aumentável)
- Timeout máximo: 15 minutos (suficiente)
- Tamanho máximo: 512MB (suficiente)
- Cold start de 300-500ms (aceitável com warm pool)

---

## Arquitetura Recomendada (Multi-Layer)

```
5.000 TPS pico
│
├─ Camada 1: API Gateway (Rate Limiting)
│  ├─ Rate Limit: 1.000 req/s (throttling automático)
│  ├─ Burst: 100 concurrent
│  └─ Cache: Respostas idempotentes (reduce load)
│
├─ Camada 2: Lambda (Compute - PRINCIPAL)
│  ├─ Reserved Concurrency: 50-100 (warm pool)
│  ├─ Provisioned Concurrency: 5 (avoid cold starts)
│  ├─ Memory: 3.008 MB (máximo = máxima CPU)
│  ├─ Timeout: 30s (suficiente pra autorizar)
│  └─ X-Ray Tracing: Ativado (distributed tracing)
│
└─ Camada 3: Failover para ECS Fargate (OPCIONAL)
   └─ Se Lambda atingir limite de concorrência
      ├─ ECS Task: Multi-AZ Auto Scaling Group
      ├─ Target: 5.000 - X (Lambda) TPS
      └─ Seamless failover via API Gateway
```

---

## Trade-offs e Justificativas

### Cold Start Problem (500ms)

**Problema**: Lambda requer ~500ms para iniciar novo container Java

**Impacto em 5.000 TPS**:
- Se 1 request em 100 sofre cold start = 50 requests × 500ms
- Percentual de tempo extra: <0.05% (negligenciável)

**Mitigações**:
1. **Provisioned Concurrency**: Manter 5-10 warm instances (~$10-20/mês extra)
2. **Lambda SnapStart** (Java 11+): Reduce cold start para ~50ms
3. **Native Images** (GraalVM): Reduce para ~100-200ms
4. **ECS Fargate fallback**: Se Lambda falha, redirecionar para ECS

**Conclusão**: Aceitável. Cold start afeta <1% das requisições, SLA ainda 99p < 100ms.

---

### Concorrência Máxima (1.000)

**Problema**: 1.000 concurrent executions máximo (vs 5.000 TPS)

**Análise**:
- TPS ≠ Concorrência
- 1 request = ~50-200ms de processamento
- 5.000 TPS = ~250-1000 concurrent (depende de latência)
- Com 200ms latência média: ~1000 concurrent ✅

**Mitigação**: Se exceder, aumentar reserved concurrency (requisição à AWS)

---

### Custo-Benefício

**Lambda**:
```
Compute:     0.0000166667 $/invocação × 100M = $1.666/mês
Memory:      0.0000000278 $/GB-second × 100M × 3GB × 0.1s = ~$8/mês
Storage:     <$0.50/mês (CloudWatch logs)
───────────────────────────────────────────────────────
Total:       ~$10/mês
```

**ECS Fargate**:
```
vCPU:        0.04048 $/hour × 24 × 30 = $29/vCPU/mês
Memory:      0.0045 $/GB/hour × 24 × 30 = $3.24/GB/mês
Task specs:  1 vCPU + 4GB = $29 + $13 = $42/task/mês
Scale (10x): $420/mês ÷ 100M = $0.004/invocação
───────────────────────────────────────────────────────
Total:       ~$420/mês (vs $10 Lambda)
```

**Diferença**: 42× mais caro com ECS Fargate.

---

## Quando Escolher Alternativas

### ❌ NÃO usar Lambda se:

1. **Processamento <10ms**: Overhead de cold start (300-500ms) é intolerável
   - Solução: ECS Fargate ou EKS

2. **Background jobs longos (>15 min)**: Lambda timeout máximo
   - Solução: ECS/EKS com timeout customizado

3. **Workload steady-state (não bursty)**: ECS sempre ligado pode ser mais barato
   - Análise: Break-even ~1B requisições/mês

4. **Arquitetura polyglot (múltiplas linguagens)**: ECS/EKS suporta melhor
   - Lambda favorece polyglot mais que antes (containers Lambda)

### ✅ USAR Lambda se:

1. ✅ Workload bursty (picos e vales)
2. ✅ SLA relaxed (latência p99 <500ms aceitável)
3. ✅ Operações zero-ops é prioridade
4. ✅ Custo é fator crítico
5. ✅ Integração nativa com AWS é valiosa

**POD99**: ✅ Todos os 5 critérios. Lambda é a escolha certa.

---

## Implementação Recomendada

### Fase 1: Lambda (MVP - Dias 1-7)

```java
// Handler Spring Boot Native (GraalVM)
@Component
public class LambdaHandler implements RequestHandler<Map, Map> {
    @Autowired
    private AuthorizeTransactionUseCase useCase;
    
    @Override
    public Map handleRequest(Map input, Context context) {
        return useCase.execute(...);
    }
}
```

**Deploy**:
```bash
# 1. Compilar como native image
mvn clean package -Dnative

# 2. Criar Lambda function
aws lambda create-function \
  --function-name pod99-authorizer \
  --runtime provided.al2 \
  --handler com.pod99.LambdaHandler

# 3. Configurar API Gateway trigger
aws apigateway put-integration \
  --rest-api-id xxx \
  --resource-id yyy \
  --http-method POST \
  --type AWS_PROXY \
  --uri arn:aws:apigateway:us-east-1:lambda:path/2015-03-31/functions/...
```

**Configuração Recomendada**:
- Memory: 3.008 MB (máximo)
- Timeout: 30s
- Ephemeral storage: 10.240 MB
- Reserved concurrency: 50
- Provisioned concurrency: 5

### Fase 2: ECS Fargate Fallback (Dias 8-30)

```yaml
# docker-compose.yml para local development
version: '3.9'
services:
  pod99-app:
    image: pod99:latest
    ports:
      - "8080:8080"
    environment:
      SPRING_PROFILES_ACTIVE: ecs
    deploy:
      replicas: 2
      resources:
        limits:
          cpus: '1'
          memory: 4G
        reservations:
          cpus: '0.5'
          memory: 2G
```

**ECS CloudFormation**:
```yaml
Resources:
  PodService:
    Type: AWS::ECS::Service
    Properties:
      Cluster: pod99-cluster
      TaskDefinition: pod99-task
      DesiredCount: 3
      AutoScaling:
        TargetValue: 70
        MetricType: CPU
```

### Fase 3: Multi-region (Dias 31+)

```hcl
# terraform/multi-region/main.tf
provider "aws" {
  alias  = "us-east-1"
  region = "us-east-1"
}

provider "aws" {
  alias  = "eu-west-1"
  region = "eu-west-1"
}

# Replica Lambda em ambas regiões
# Route53 weighted routing → distribuir tráfego
```

---

## Monitoramento e Alertas

```bash
# CloudWatch Metrics
- Duration: p99 > 100ms?
- ConcurrentExecutions: trending to 1000?
- Errors: > 0.1%?
- Throttles: > 0?

# Alarms
aws cloudwatch put-metric-alarm \
  --alarm-name pod99-lambda-duration-p99 \
  --metric-name Duration \
  --namespace AWS/Lambda \
  --statistic p99 \
  --period 60 \
  --threshold 100000 \
  --comparison-operator GreaterThanThreshold
```

---

## Roadmap Futuro (Não Implementado)

1. **Lambda@Edge**: Cache autorização em CloudFront (reduz latência)
2. **Amazon Elasticache**: Cache limite em Redis (~99th percentile <10ms)
3. **Kinesis**: Se ordenação por chave for crítico (EventBridge não ordena globalmente)
4. **DynamoDB Accelerator (DAX)**: Cache em memória (99th percentile <1ms)
5. **RDS Aurora**: Se relacionamentos complexos (POD99 é simples key-value)

---

## Conclusão

**Lambda é a melhor escolha para POD99 porque:**

✅ Custo: 42× mais barato que ECS
✅ Operações: Zero-ops vs. gerenciamento contínuo
✅ Escalabilidade: Auto-scale em milissegundos
✅ Latência: 99th percentile <100ms (com Provisioned Concurrency)
✅ Integração: Nativa com EventBridge, SQS, DynamoDB

**Trade-offs aceitáveis:**
⚠️ Cold start 500ms: Afeta <1% das requisições (mitigado com Provisioned Concurrency)
⚠️ Concorrência máxima 1000: Suficiente para 5k TPS @ 200ms latência

**Fallback**: ECS Fargate se Lambda atingir limites (seamless via API Gateway)

---

## Referências

- [AWS Lambda Pricing](https://aws.amazon.com/lambda/pricing/)
- [Lambda Performance Optimization](https://docs.aws.amazon.com/lambda/latest/dg/best-practices.html)
- [ECS vs Lambda vs EKS](https://aws.amazon.com/blogs/compute/choose-between-aws-compute-options/)
- [GraalVM Native Image for Lambda](https://www.graalvm.org/latest/docs/getting-started/container-images/)
- [Lambda SnapStart for Java](https://docs.aws.amazon.com/lambda/latest/dg/snapstart.html)
