# ADR-003: API Versionamento e Compatibilidade

## Contexto
A POD99 é uma plataforma financeira que será usada por múltiplos clientes. Precisamos evoluir a API **sem quebrar clientes**.

Cenários:
- Cliente A: integrado com v1
- Cliente B: integrado com v2
- Precisamos adicionar novo campo (ex: risco_score)

## Problema
Sem estratégia clara de versionamento, adicionar um campo quebra clientes legados.

## Decisão
✅ **URL-based versioning com políticas de backward compatibility**

### Estratégia

```
GET /v1/contratos/{id}/autorizacoes    ← Cliente legado
GET /v2/contratos/{id}/autorizacoes    ← Cliente novo
```

### Request (v1)
```json
{
  "id_conta": "ACC-001",
  "valor": 100.00,
  "moeda": "BRL",
  "tipo_operacao": "DEBITO"
}
```

### Response (v1)
```json
{
  "id_autorizacao": "AUTH-123",
  "saldo_reservado": 99900.00,
  "correlation_id": "trace-123",
  "status": "APPROVED"
}
```

### Response (v2)
```json
{
  "id_autorizacao": "AUTH-123",
  "saldo_reservado": 99900.00,
  "correlation_id": "trace-123",
  "status": "APPROVED",
  "risco_score": 0.15,           // ← Novo campo (adicional)
  "motivo_recusa": null,         // ← Novo campo (adicional)
  "features": {                  // ← Novo objeto (adicional)
    "pagamento_agendado": false,
    "pix_diferido": false
  }
}
```

**Regra**: Novos campos são **apenas adicionados** (nunca removidos/renomeados).

### Evolution Rules

| Operação | v1→v2 | Motivo |
|----------|-------|--------|
| Adicionar campo | ✅ OK | Cliente ignora, compatível |
| Remover campo | ❌ QUEBRA | Cliente quebra se esperava |
| Renomear campo | ❌ QUEBRA | Cliente não encontra novo nome |
| Mudar tipo | ❌ QUEBRA | Cliente não consegue parsear |
| Mudar default | ✅ OK | Se não era obrigatório |

### Deprecation Policy

Quando depreciar v1:
1. **v1 released**: Initial
2. **6 months later**: Announce v2
3. **12 months later**: Deprecate v1 (warnings em headers)
4. **18 months later**: Suporte acaba

```
Response header:
Deprecation: true
Sunset: 2027-03-31T00:00:00Z (data de término)
```

## Implementation

```java
// Controller rota v1 → v2 com adaptação
@PostMapping("/v1/contratos/{id}/autorizacoes")
public ResponseEntity<?> authorizeV1(@PathVariable String id, 
                                      @RequestBody AuthorizeRequestV1 req) {
    // Converte V1 → V2 internamente
    AuthorizeRequestV2 reqV2 = convert(req);
    AuthorizeResponseV2 respV2 = authorizeUseCase.execute(id, reqV2);
    // Converte V2 → V1 na resposta
    AuthorizeResponseV1 respV1 = convert(respV2);
    
    return ResponseEntity.status(HttpStatus.CREATED)
        .header("Deprecation", "true")
        .header("Sunset", "2027-03-31T00:00:00Z")
        .body(respV1);
}

@PostMapping("/v2/contratos/{id}/autorizacoes")
public ResponseEntity<?> authorizeV2(@PathVariable String id,
                                      @RequestBody AuthorizeRequestV2 req) {
    AuthorizeResponseV2 resp = authorizeUseCase.execute(id, req);
    return ResponseEntity.status(HttpStatus.CREATED).body(resp);
}
```

## Por que não alternativas?

❌ **Header-based versioning** (Accept: application/vnd.pod99.v1+json)
- Menos visível na URL
- Dificulta debugging (cliente vê /contratos, não sabe qual versão)

❌ **Single version com feature flags**
- Complexidade: precisa conditional logic em toda response
- Difícil para clientes: como saber qual versão está usando?

## Benefícios
✅ **Explícito**: URL deixa claro qual versão
✅ **Seguro**: Novos campos = backward compatible
✅ **Suportável**: Cada versão roda paralelo
✅ **Deprecável**: Headers avisar antes de suporte acabar

## Trade-offs
⚠️ **Duplicação**: Mesma lógica em /v1 e /v2 endpoints
  - Mitigado: Conversores reutilizáveis

⚠️ **Múltiplas rotas**: Mais URLs para manter
  - Mitigado: Automação via Swagger/OpenAPI

## Decisão Final
**APROVADO** - URL-based versionamento é o padrão REST, claro e manutenível.
