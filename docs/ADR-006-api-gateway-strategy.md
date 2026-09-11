# ADR-006: API Gateway Strategy
## Decisão: API Gateway vs Service Mesh vs BFF (Backend for Frontend)

**Data:** 2024-01-01  
**Status:** ACCEPTED  
**Contexto:** POD99 - Plataforma de Autorização de Transações  
**Impacto:** Arquitetura de entrada (edge), autenticação, rate limiting  

---

## 1. Problema

A plataforma POD99 precisa:
- ✅ Autenticar clientes (Cognito, OAuth2, JWT)
- ✅ Controlar rate limiting e quotas por consumidor
- ✅ Validar contratos de API (versionamento, deprecação)
- ✅ Rotear requests para múltiplos backends
- ✅ Observabilidade e tracing distribuído
- ✅ Gerir custos operacionais

**Pergunta:** Como implementar API Management de forma escalável e segura?

---

## 2. Opções Consideradas

### Opção A: API Gateway AWS
**Descrição:** Usar AWS API Gateway como proxy centralizado

```
Cliente → API Gateway → Lambda Authorizer → App (Lambda/ECS/EKS)
```

**Características:**
- ✅ Managed service (AWS cuida de escala, disponibilidade)
- ✅ Integração nativa com Cognito, Lambda Authorizer, OAuth2
- ✅ Rate limiting nativo (usage plans, throttling, quotas)
- ✅ Caching de respostas
- ✅ OpenAPI/Swagger gerado automaticamente
- ✅ CloudWatch Logs nativo
- ⚠️ Custos baseados em requisições e data transfer
- ⚠️ Cold start em Lambda Authorizer (~100-200ms)
- ⚠️ Menos controle fino sobre comportamento

**Quando usar:**
- APIs REST/HTTP públicas
- Autenticação centralizada (Cognito)
- Time pequeno/médio
- MVP rápido

---

### Opção B: Service Mesh (Istio/Linkerd)
**Descrição:** Usar sidecar proxies para comunicação entre serviços

```
Cliente → [Ingress (nginx/Istio)] → [Sidecar] → App → [Sidecar] → [Sidecar] → App
```

**Características:**
- ✅ Segurança em nível de serviço (mTLS)
- ✅ Controle fino de traffic (circuit breaker, retry, timeout)
- ✅ Observabilidade completa (traces distribuídos, metrics)
- ✅ Funciona com qualquer linguagem/framework
- ✅ Resiliência avançada (bulkhead, timeout policies)
- ⚠️ Complexidade operacional MUITO alta
- ⚠️ Requer Kubernetes (learning curve)
- ⚠️ Custo de infrastructure (recursos por sidecar)
- ⚠️ Latência adicional (proxy intercept)

**Quando usar:**
- Microsserviços complexos (10+ serviços)
- Segurança em nível de serviço (banking, saúde)
- Time DevOps experiente
- Escala alta (1000+ TPS)

---

### Opção C: Backend for Frontend (BFF)
**Descrição:** Criar um servidor intermediário que agrega APIs

```
Web Client → BFF (Node.js/Go) → Serviço A
Mobile      → BFF (Node.js/Go) → Serviço B
            ↘                   ↙ Serviço C
```

**Características:**
- ✅ Flexibilidade total (qualquer lógica custom)
- ✅ Agregação de dados (query múltiplos serviços)
- ✅ Transformação de respostas (adaptar por cliente)
- ✅ Autenticação e autorização customizadas
- ✅ Rate limiting granular
- ⚠️ Código adicional para manter (outro serviço)
- ⚠️ Cascata de erros (BFF falha = todos falham)
- ⚠️ Custo de infrastructure adicional

**Quando usar:**
- Múltiplos clients (web, mobile, IoT)
- Necesário agregação de dados
- Lógica de negócio complexa no edge
- Team grande

---

## 3. Análise Comparativa

| Critério | API Gateway | Service Mesh | BFF |
|----------|-------------|-------------|-----|
| **Escalabilidade** | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐ |
| **Segurança** | ⭐⭐⭐⭐ | ⭐⭐⭐⭐⭐ | ⭐⭐⭐ |
| **Observabilidade** | ⭐⭐⭐ | ⭐⭐⭐⭐⭐ | ⭐⭐⭐ |
| **Complexidade Operacional** | ⭐⭐⭐⭐ | ⭐ | ⭐⭐ |
| **Custo** | Baixo-Médio | Médio-Alto | Médio |
| **Time Skills** | AWS | Kubernetes | Backend Dev |
| **Time to MVP** | ⭐⭐⭐⭐⭐ | ⭐ | ⭐⭐⭐ |

---

## 4. Decisão Arquitetural

### **Escolha: API Gateway AWS + Lambda Authorizer**

**Justificativa:**

1. **MVP Rápido:** POD99 tem prazo de 5 dias; API Gateway permite implementação em poucas horas
2. **Integração AWS:** Cognito, Lambda, DynamoDB, EventBridge já estão no stack
3. **Managed Service:** Reduz carga operacional (não gerenciar infrastructure)
4. **Rate Limiting Nativo:** Usage plans e throttling já resolvem quotas por consumidor
5. **Security:** Lambda Authorizer + IAM policies são suficientes para MVP
6. **Escalabilidade:** API Gateway escala automaticamente para 40k TPS (além do nosso 5k TPS)
7. **Custo Efetivo:** Para 5k TPS, AWS API Gateway é mais barato que rodar Istio ou BFF

**Limitações Aceitas:**

- ⚠️ Sem mTLS entre serviços (remediar com encryption em transit)
- ⚠️ Observabilidade limitada vs Istio (usar X-Ray + CloudWatch)
- ⚠️ Sem circuit breaker nativo (implementar no app com Resilience4j)

---

## 5. Roadmap Futuro (Pós-MVP)

**Fase 2 (6-12 meses):**
- Avaliar Istio se microsserviços crescerem além de 3
- Implementar mTLS entre componentes
- Aprofundar X-Ray tracing

**Fase 3 (12+ meses):**
- Considerar BFF se necessário agregação entre múltiplos domínios
- Multi-region replication
- Disaster recovery

---

## 6. Implementação

### 6.1 API Gateway Configuration (Terraform)
```hcl
resource "aws_apigatewayv2_api" "pod99_api" {
  name          = "pod99-api"
  protocol_type = "HTTP"
  
  cors_configuration {
    allow_origins = ["*"]
    allow_methods = ["GET", "POST", "PUT", "DELETE"]
    allow_headers = ["Content-Type", "Authorization", "Idempotency-Key"]
  }
}

resource "aws_apigatewayv2_stage" "prod" {
  api_id      = aws_apigatewayv2_api.pod99_api.id
  name        = "prod"
  auto_deploy = true
}
```

### 6.2 Lambda Authorizer (JWT Validation)
```java
public Map<String, Object> handleRequest(Map<String, Object> event, Context context) {
    String token = extractToken(event);              // "Bearer <jwt>"
    String accountId = validateJWT(token);           // Validar assinatura
    
    if (accountId != null) {
        return allowPolicy(event, accountId);        // 200 + IAM policy
    }
    
    return denyPolicy(event, "Unauthorized");        // 401
}
```

### 6.3 Routes Configuration
```hcl
resource "aws_apigatewayv2_route" "authorize_post" {
  api_id    = aws_apigatewayv2_api.pod99_api.id
  route_key = "POST /v1/contratos/{id_contrato}/autorizacoes"
  target    = "integrations/${aws_apigatewayv2_integration.lambda.id}"
  
  # Opcional: adicionar authorizer
  # authorization_type = "CUSTOM"
  # authorizer_id      = aws_apigatewayv2_authorizer.jwt.id
}
```

---

## 7. Métricas de Sucesso

- ✅ Latência p99 < 100ms (incluindo API Gateway overhead ~5-10ms)
- ✅ Throughput sustentado de 5.000 TPS
- ✅ Taxa de erro < 0.1%
- ✅ Disponibilidade > 99.99%
- ✅ Tempo de implementação < 2 dias

---

## 8. Referências

- [AWS API Gateway Pricing](https://aws.amazon.com/apigateway/pricing/)
- [Lambda Authorizer](https://docs.aws.amazon.com/apigateway/latest/developerguide/api-gateway-lambda-function-reuse.html)
- [Istio Architecture](https://istio.io/latest/docs/ops/architecture/)
- [BFF Pattern](https://martinfowler.com/articles/patterns-of-distributed-systems/backend-for-frontend.html)

---

## 9. Discussão em Defesa

**Possíveis Perguntas:**

1. **Q:** "Por que não usar Istio?"
   - **A:** MVP precisa estar pronto em 5 dias. Istio requer Kubernetes + 2-3 semanas de setup. API Gateway é 10x mais rápido.

2. **Q:** "E se precisermos de mTLS?"
   - **A:** Roadmap Fase 2. Por enquanto, usar encryption em transit (TLS 1.3 no API Gateway).

3. **Q:** "E se mudar de cloud provider?"
   - **A:** API Gateway é AWS-specific, mas negócio é. BFF seria mais portável, mas requereria mais dev.

4. **Q:** "Como escala pra 50k TPS?"
   - **A:** API Gateway escala até 40k TPS nativo. Pra mais, usar múltiplas APIs + load balancing com Route53.

5. **Q:** "Qual o custo mensal estimado?"
   - **A:** Para 5k TPS (432M requisições/mês): ~$21.6k/mês em API Gateway (sem storage, compute é em Lambda).

---

**Aprovado em:** <data>  
**Por:** Arquiteto de Aplicação POD99
