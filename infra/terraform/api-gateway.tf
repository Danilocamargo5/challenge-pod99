# ==================================================================================
# API Gateway v2 - REMOVIDO (não funciona no LocalStack 4.14.0)
# ==================================================================================

# LocalStack 4.14.0 não suporta ApiGatewayV2 com credenciais corretas
# Aplicação roda direto em localhost:8080

output "app_endpoint" {
  value = "http://localhost:8080"
}
