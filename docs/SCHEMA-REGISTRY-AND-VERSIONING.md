# Schema Registry e Versionamento de CloudEvents

**Status:** Arquitetura + Implementação Parcial  
**Data:** 11 de Setembro 2026  
**Objetivo:** Documentar estratégia de versionamento de schemas para eventos CloudEvents  
**Gap ID:** 5 (Baixa prioridade)

---

## 1. PROBLEMA

Quando a estrutura de eventos muda, consumidores antigos podem receber eventos com schema desconhecido:

```json
// v1 (antigo)
{
  "id_conta": "ACC-001",
  "valor": 100.00
}

// v2 (novo - adicionou campos)
{
  "id_conta": "ACC-001",
  "valor": 100.00,
  "id_estabelecimento": "EST-001",   // ← NOVO
  "metadata": { "taxa": 0.5 }        // ← NOVO
}
```

**Impacto:** Consumidor v1 morre ao processar evento v2 (campos desconhecidos).

---

## 2. SOLUÇÃO: SCHEMA REGISTRY + VERSIONING

### 2.1 Estratégia: Versionamento Semântico

Use versionamento semântico nos CloudEvents:

```json
{
  "specversion": "1.0",
  "type": "com.pod99.TransacaoAutorizada",
  "subject": "authorizations/ACC-001",
  "datacontenttype": "application/json",
  "source": "pod99/authorization",
  
  // ← NOVO: Versão do schema
  "schemaurl": "https://schemas.pod99.com/v2/TransacaoAutorizada.json",
  
  // ← NOVO: ID único para compatibilidade
  "dataschema": "com.pod99.TransacaoAutorizada#v2",
  
  "id": "evt-abc123",
  "time": "2026-09-11T19:30:00Z",
  
  "data": {
    "id_autorizacao": "AUTH-123",
    "id_conta": "ACC-001",
    "valor": 100.00,
    "moeda": "BRL",
    "id_estabelecimento": "EST-001",  // v2
    "metadata": {                      // v2
      "taxa": 0.5,
      "timestamp": "2026-09-11T19:30:00Z"
    }
  }
}
```

---

### 2.2 Estrutura de Schemas (Arquivo)

```
docs/schemas/
  ├── TransacaoAutorizada/
  │   ├── v1.json
  │   ├── v2.json
  │   ├── v3.json
  │   └── CHANGELOG.md
  ├── ContaLimitada/
  │   ├── v1.json
  │   └── CHANGELOG.md
  └── README.md
```

**Exemplo: v1.json**
```json
{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "title": "Transacao Autorizada",
  "version": "1.0.0",
  "type": "object",
  "required": ["id_autorizacao", "id_conta", "valor", "moeda"],
  "properties": {
    "id_autorizacao": { "type": "string" },
    "id_conta": { "type": "string" },
    "valor": { "type": "number" },
    "moeda": { "type": "string", "enum": ["BRL", "USD"] }
  }
}
```

**Exemplo: v2.json (Backward compatible)**
```json
{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "title": "Transacao Autorizada",
  "version": "2.0.0",
  "type": "object",
  "required": ["id_autorizacao", "id_conta", "valor", "moeda"],
  "properties": {
    "id_autorizacao": { "type": "string" },
    "id_conta": { "type": "string" },
    "valor": { "type": "number" },
    "moeda": { "type": "string", "enum": ["BRL", "USD"] },
    
    // ← v2: Novos campos opcionais
    "id_estabelecimento": { "type": "string" },
    "metadata": { 
      "type": "object",
      "additionalProperties": true  // Aceita qualquer campo
    }
  }
}
```

---

## 3. IMPLEMENTAÇÃO: SCHEMA VERSIONING EM JAVA

### 3.1 Classe para Schema Versioning

```java
package com.pod99.common.domain;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Service para versionamento de schemas de eventos
 * 
 * Migra eventos de versões antigas para versões novas
 */
@Service
@RequiredArgsConstructor
public class SchemaVersioningService {
    
    private final ObjectMapper mapper;
    
    /**
     * Detecta versão do evento baseado em dataschema
     * 
     * @param event CloudEvent em JSON
     * @return Versão (ex: "v1", "v2")
     */
    public String detectVersion(String event) throws JsonProcessingException {
        JsonNode root = mapper.readTree(event);
        String dataschema = root.get("dataschema").asText("com.pod99.TransacaoAutorizada#v1");
        
        // Extrair versão do dataschema (ex: "v2" de "com.pod99.TransacaoAutorizada#v2")
        return dataschema.split("#")[1];
    }
    
    /**
     * Migra evento de v1 → v2 (adiciona campos faltantes com defaults)
     */
    public String migrateV1ToV2(String eventV1) throws JsonProcessingException {
        JsonNode root = mapper.readTree(eventV1);
        ObjectNode data = (ObjectNode) root.get("data");
        
        // Adicionar campos v2 com valores padrão
        if (!data.has("id_estabelecimento")) {
            data.put("id_estabelecimento", "UNKNOWN");
        }
        
        if (!data.has("metadata")) {
            data.putObject("metadata").put("migrated_from", "v1");
        }
        
        // Atualizar schema
        ((ObjectNode) root).put("dataschema", "com.pod99.TransacaoAutorizada#v2");
        ((ObjectNode) root).put("schemaurl", "https://schemas.pod99.com/v2/TransacaoAutorizada.json");
        
        return mapper.writeValueAsString(root);
    }
    
    /**
     * Normaliza evento para versão alvo
     * 
     * @param event CloudEvent
     * @param targetVersion Versão alvo (ex: "v2")
     * @return Evento normalizado
     */
    public String normalizeToVersion(String event, String targetVersion) throws JsonProcessingException {
        String currentVersion = detectVersion(event);
        
        if (currentVersion.equals(targetVersion)) {
            return event;  // Já na versão correta
        }
        
        String normalized = event;
        
        // Executar migrações progressivas
        // Ex: v1 → v2 → v3
        if ("v1".equals(currentVersion) && targetVersion.matches("v[2-9].*")) {
            normalized = migrateV1ToV2(normalized);
        }
        
        // Adicionar migrações V2→V3, V3→V4, etc conforme necessário
        
        return normalized;
    }
}
```

### 3.2 Validador com Versionamento

```java
package com.pod99.common.domain;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.ValidationMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.Set;

/**
 * Validador de schemas com suporte a versionamento
 * 
 * Usa json-schema-validator (networknt)
 */
@Service
@RequiredArgsConstructor
public class VersionedSchemaValidator {
    
    private final ObjectMapper mapper;
    private final Map<String, JsonSchema> schemaCache = new ConcurrentHashMap<>();
    
    /**
     * Carrega schema para versão
     * 
     * @param eventType Tipo de evento (ex: "TransacaoAutorizada")
     * @param version Versão (ex: "v2")
     * @return JsonSchema para validação
     */
    private JsonSchema loadSchema(String eventType, String version) {
        String key = eventType + "#" + version;
        
        return schemaCache.computeIfAbsent(key, k -> {
            try {
                // Carregamento do schema do arquivo/repository
                String schemaJson = loadSchemaFromFile(eventType, version);
                JsonNode schemaNode = mapper.readTree(schemaJson);
                return JsonSchemaFactory.getInstance().getSchema(schemaNode);
            } catch (Exception e) {
                throw new RuntimeException("Falha ao carregar schema: " + key, e);
            }
        });
    }
    
    /**
     * Valida evento contra schema versionado
     * 
     * @param event CloudEvent JSON
     * @return Lista de erros (vazia = válido)
     */
    public Set<ValidationMessage> validate(String event) {
        try {
            JsonNode root = mapper.readTree(event);
            
            // Detectar tipo e versão
            String type = root.get("type").asText();
            String dataschema = root.get("dataschema").asText();
            String version = dataschema.split("#")[1];  // "v2" de "...#v2"
            String eventType = type.substring(type.lastIndexOf(".") + 1);  // "TransacaoAutorizada"
            
            // Validar data
            JsonNode data = root.get("data");
            JsonSchema schema = loadSchema(eventType, version);
            
            return schema.validate(data);
            
        } catch (Exception e) {
            return Set.of(new ValidationMessage.Builder()
                .message("Erro ao validar schema: " + e.getMessage())
                .build());
        }
    }
    
    private String loadSchemaFromFile(String eventType, String version) {
        // Implementar carregamento do arquivo
        // Ex: docs/schemas/{eventType}/{version}.json
        // ...
        return "{}";
    }
}
```

---

## 4. INTEG RAÇÃO COM POD99

### 4.1 Atualizar TransacaoAutorizadaEvent

```java
public class TransacaoAutorizadaEvent {
    
    public static TransacaoAutorizadaEvent from(Authorization auth, String traceId) {
        return TransacaoAutorizadaEvent.builder()
            .eventId(UUID.randomUUID().toString())
            .idAutorizacao(auth.getIdAutorizacao())
            .idConta(auth.getIdConta())
            .valor(auth.getValor())
            .moeda(auth.getMoeda())
            .tipoOperacao(auth.getTipoOperacao())
            .idEstabelecimento(auth.getIdEstabelecimento())     // v2
            .metadata(auth.getMetadata())                        // v2
            .status(auth.getStatus().toString())
            .timestamp(LocalDateTime.now())
            .traceId(traceId)
            
            // Schema versioning
            .dataschema("com.pod99.TransacaoAutorizada#v2")      // ← Versão
            .schemaurl("https://schemas.pod99.com/v2/TransacaoAutorizada.json")
            
            .build();
    }
}
```

### 4.2 Validar no EventBridgePublisher

```java
@Service
public class EventBridgePublisher {
    
    @Autowired
    private VersionedSchemaValidator schemaValidator;
    
    public void publish(TransacaoAutorizadaEvent event) {
        // Serializar para JSON
        String eventJson = mapper.writeValueAsString(event);
        
        // ← Validar schema
        Set<ValidationMessage> errors = schemaValidator.validate(eventJson);
        if (!errors.isEmpty()) {
            throw new InvalidEventException("Schema validation failed: " + errors);
        }
        
        // Publicar se válido
        eventBridge.putEvents(eventJson);
    }
}
```

### 4.3 Consumidor Tolerante (SQS Listener)

```java
@Service
public class AccountingEventListener {
    
    @Autowired
    private SchemaVersioningService versioningService;
    
    @SqsListener("pod99-accounting-queue")
    public void handleEvent(String message) {
        try {
            // Detectar versão
            String version = versioningService.detectVersion(message);
            
            // Normalizar para versão esperada (v2)
            String normalized = versioningService.normalizeToVersion(message, "v2");
            
            // Processar evento normalizado
            TransacaoAutorizadaEvent event = mapper.readValue(
                normalized, TransacaoAutorizadaEvent.class);
            
            accountingService.recordTransaction(event);
            
        } catch (Exception e) {
            log.error("Erro ao processar evento: {}", e.getMessage());
            // Reprocessar via DLQ
        }
    }
}
```

---

## 5. CHANGELOG E VERSIONAMENTO

**docs/schemas/TransacaoAutorizada/CHANGELOG.md:**

```markdown
# Changelog - TransacaoAutorizada Event

## [2.0.0] - 2026-09-11

### Added
- `id_estabelecimento` (string): ID do estabelecimento (Seção 2.3)
- `metadata` (object): Dados adicionais customizáveis

### Changed
- Schema namespace: `com.pod99.TransacaoAutorizada`

### Backward Compatible
- ✅ v1 → v2 automático via SchemaVersioningService
- ✅ Campos v1 continuam obrigatórios
- ✅ Campos v2 são opcionais (defaults: "UNKNOWN", {})

### Migration Path
```
v1 (2026-01-01 - 2026-09-10)
  ↓ (automatic migration)
v2 (2026-09-11 onwards)
```

## [1.0.0] - 2026-01-01

### Added
- Initial schema
- `id_autorizacao`, `id_conta`, `valor`, `moeda`, `tipo_operacao`

---
```

---

## 6. BOAS PRÁTICAS

### ✅ DO's
- ✅ Adicione campos opcionais (backward compatible)
- ✅ Nunca remova campos (apenas deprecate)
- ✅ Use defaults para novos campos em consumidores antigos
- ✅ Valide schemas em produção
- ✅ Documente mudanças em CHANGELOG
- ✅ Use versionamento semântico

### ❌ DON'Ts
- ❌ Remover campos sem migração
- ❌ Mudar tipo de campo (int → string)
- ❌ Não validar eventos recebidos
- ❌ Versionamento arbitrário (v1, v1a, v1-beta)

---

## 7. STATUS DE IMPLEMENTAÇÃO

| Item | Status | Notas |
|------|--------|-------|
| Schemas JSON | ⚠️ Parcial | Estrutura de arquivos criada, schemas v1/v2 faltam |
| SchemaVersioningService | ⚠️ Esboço | Código exemplo, precisa implementação completa |
| VersionedSchemaValidator | ⚠️ Esboço | Requer dependência `json-schema-validator` |
| Integração EventBridge | ⚠️ Parcial | TransacaoAutorizadaEvent tem v2, validação pendente |
| Consumer (SQS Listener) | ⚠️ Parcial | Tolerância básica existe, migração automática falta |
| CHANGELOG | ✅ Feito | Documentado |

---

## 8. PRÓXIMOS PASSOS

1. **Implementação Completa:**
   - Criar arquivos `docs/schemas/TransacaoAutorizada/v1.json` e `v2.json`
   - Implementar carregamento de schemas (arquivo/S3)
   - Adicionar dependência `com.networknt:json-schema-validator:1.0.84`

2. **Testes:**
   - Unit tests para SchemaVersioningService
   - Integration tests para validação
   - Load test com eventos versionados

3. **Deploymemt:**
   - Versionear schemas no repositório
   - CI/CD: Validar schemas em cada commit
   - Monitoramento: Alertas para eventos inválidos

4. **Observabilidade:**
   - Métrica: Contagem por versão de schema
   - Log: Eventos que sofreram migração
   - Trace: X-Ray com schema-version tag

---

## REFERÊNCIAS

- [CloudEvents Spec](https://cloudevents.io/)
- [JSON Schema](https://json-schema.org/)
- [JSON Schema Validator (networknt)](https://github.com/networknt/json-schema-validator)
- [AWS EventBridge + Schema](https://docs.aws.amazon.com/eventbridge/latest/userguide/eb-schema.html)
- [Avro Schema Registry](https://docs.confluent.io/platform/current/schema-registry/index.html) (alternativa)

