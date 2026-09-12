#!/usr/bin/env python3
"""Quick LocalStack setup - cria tabelas e filas (sem dados ainda)"""
import boto3
import time

AWS_ENDPOINT = "http://localhost:4566"
AWS_REGION = "us-east-1"

dynamodb = boto3.client("dynamodb", endpoint_url=AWS_ENDPOINT, region_name=AWS_REGION,
                       aws_access_key_id="test", aws_secret_access_key="test")
sqs = boto3.client("sqs", endpoint_url=AWS_ENDPOINT, region_name=AWS_REGION,
                  aws_access_key_id="test", aws_secret_access_key="test")
events = boto3.client("events", endpoint_url=AWS_ENDPOINT, region_name=AWS_REGION,
                     aws_access_key_id="test", aws_secret_access_key="test")

print("╔════════════════════════════════════════════════════════════════╗")
print("║  LocalStack Setup (Quick)                                      ║")
print("╚════════════════════════════════════════════════════════════════╝\n")

# Wait for LocalStack
print("⏳ Aguardando LocalStack...")
for i in range(30):
    try:
        dynamodb.list_tables()
        print("✅ LocalStack pronto!\n")
        break
    except:
        if i == 29:
            print("❌ LocalStack não respondeu!")
            exit(1)
        time.sleep(1)

# DynamoDB
print("📊 Criando tabelas DynamoDB...")
for name in ["pod99-limits", "pod99-authorizations", "pod99-accounting", "pod99-locks", "pod99-rate-limit"]:
    try:
        dynamodb.create_table(TableName=name, KeySchema=[{"AttributeName": name.split("-")[-1] if name == "pod99-limits" else "id", "KeyType": "HASH"}] if name == "pod99-limits" else [{"AttributeName": "id", "KeyType": "HASH"}], 
                             AttributeDefinitions=[{"AttributeName": "id_contrato" if name == "pod99-limits" else "id_autorizacao" if name == "pod99-authorizations" else "event_id" if name == "pod99-accounting" else "lock_key" if name == "pod99-locks" else "account_id", "AttributeType": "S"}], BillingMode="PAY_PER_REQUEST")
        print(f"  ✅ {name}")
    except:
        print(f"  ℹ️ {name} já existe")

# SQS
print("\n📦 Criando filas SQS...")
for name in ["pod99-accounting-dlq.fifo", "pod99-accounting-queue.fifo"]:
    try:
        sqs.create_queue(QueueName=name, Attributes={"FifoQueue": "true", "ContentBasedDeduplication": "true"})
        print(f"  ✅ {name}")
    except:
        print(f"  ℹ️ {name} já existe")

print("\n✨ Setup completo!")
print("🚀 Próximo: Terminal 3 → ./mvnw spring-boot:run\n")
