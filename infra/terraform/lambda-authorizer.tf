# ==================================================================================
# Lambda Authorizer - COMENTADO (não suportado em HTTP API Gateway do LocalStack)
# ==================================================================================

# Para HTTP API Gateway, use JWT nativo ou remova autenticação
# Autenticação será feita no próprio app Spring Boot via JwtAuthenticationFilter

# Route com autenticação será adicionada quando necessário
# Por enquanto, rotas SEM autenticação no API Gateway
# A validação de JWT é feita no app (JwtAuthenticationFilter)
