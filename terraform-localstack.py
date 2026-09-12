#!/usr/bin/env python3
"""
terraform-localstack.py - Substitui Terraform usando boto3
Cria toda a infraestrutura (DynamoDB, SQS, EventBridge) no LocalStack
"""

import json
import time
import sys
from typing import Optional

try:
    import boto3
except ImportError:
    print("❌ boto3 não instalado. Instalando...")
    import subprocess
    subprocess.check_call([sys.executable, "-m", "pip", "install", "-q", "boto3"])
    import boto3

# ==================================================================================
# Configuration
# ==================================================================================

AWS_ENDPOINT = "http://localhost:4566"
AWS_REGION = "us-east-1"
AWS_ACCESS_KEY = "test"
AWS_SECRET_KEY = "test"

# ==================================================================================
# Initialize AWS Clients
# ==================================================================================

dynamodb = boto3.client(
    "dynamodb",
    endpoint_url=AWS_ENDPOINT,
    region_name=AWS_REGION,
    aws_access_key_id=AWS_ACCESS_KEY,
    aws_secret_access_key=AWS_SECRET_KEY,
)

sqs = boto3.client(
    "sqs",
    endpoint_url=AWS_ENDPOINT,
    region_name=AWS_REGION,
    aws_access_key_id=AWS_ACCESS_KEY,
    aws_secret_access_key=AWS_SECRET_KEY,
)

events = boto3.client(
    "events",
    endpoint_url=AWS_ENDPOINT,
    region_name=AWS_REGION,
    aws_access_key_id=AWS_ACCESS_KEY,
    aws_secret_access_key=AWS_SECRET_KEY,
)


# ==================================================================================
# DynamoDB Tables
# ==================================================================================

def create_dynamodb_tables():
    """Cria 5 tabelas DynamoDB"""
    print("📊 Criando tabelas DynamoDB...")
    
    tables = [
        {
            "TableName": "pod99-limits",
            "KeySchema": [{"AttributeName": "id_contrato", "KeyType": "HASH"}],
            "AttributeDefinitions": [{"AttributeName": "id_contrato", "AttributeType": "S"}],
        },
        {
            "TableName": "pod99-authorizations",
            "KeySchema": [{"AttributeName": "id_autorizacao", "KeyType": "HASH"}],
            "AttributeDefinitions": [{"AttributeName": "id_autorizacao", "AttributeType": "S"}],
        },
        {
            "TableName": "pod99-accounting",
            "KeySchema": [{"AttributeName": "event_id", "KeyType": "HASH"}],
            "AttributeDefinitions": [{"AttributeName": "event_id", "AttributeType": "S"}],
        },
        {
            "TableName": "pod99-locks",
            "KeySchema": [{"AttributeName": "lock_key", "KeyType": "HASH"}],
            "AttributeDefinitions": [{"AttributeName": "lock_key", "AttributeType": "S"}],
        },
        {
            "TableName": "pod99-rate-limit",
            "KeySchema": [{"AttributeName": "account_id", "KeyType": "HASH"}],
            "AttributeDefinitions": [{"AttributeName": "account_id", "AttributeType": "S"}],
        },
    ]
    
    for table in tables:
        try:
            dynamodb.create_table(
                TableName=table["TableName"],
                KeySchema=table["KeySchema"],
                AttributeDefinitions=table["AttributeDefinitions"],
                BillingMode="PAY_PER_REQUEST",
            )
            print(f"  ✅ {table['TableName']}")
        except dynamodb.exceptions.ResourceInUseException:
            print(f"  ℹ️ {table['TableName']} já existe")
        except Exception as e:
            print(f"  ❌ {table['TableName']}: {e}")


# ==================================================================================
# SQS Queues
# ==================================================================================

def create_sqs_queues():
    """Cria 2 filas SQS (FIFO + DLQ)"""
    print("\n📦 Criando filas SQS...")
    
    # DLQ
    try:
        sqs.create_queue(
            QueueName="pod99-accounting-dlq.fifo",
            Attributes={
                "FifoQueue": "true",
                "ContentBasedDeduplication": "true",
                "MessageRetentionPeriod": "1209600",
            },
        )
        print("  ✅ pod99-accounting-dlq.fifo")
    except sqs.exceptions.QueueNameExists:
        print("  ℹ️ pod99-accounting-dlq.fifo já existe")
    except Exception as e:
        print(f"  ❌ pod99-accounting-dlq.fifo: {e}")
    
    # Main Queue
    try:
        sqs.create_queue(
            QueueName="pod99-accounting-queue.fifo",
            Attributes={
                "FifoQueue": "true",
                "ContentBasedDeduplication": "true",
                "MessageRetentionPeriod": "86400",
                "VisibilityTimeout": "300",
            },
        )
        print("  ✅ pod99-accounting-queue.fifo")
    except sqs.exceptions.QueueNameExists:
        print("  ℹ️ pod99-accounting-queue.fifo já existe")
    except Exception as e:
        print(f"  ❌ pod99-accounting-queue.fifo: {e}")


# ==================================================================================
# EventBridge Rule
# ==================================================================================

def create_eventbridge_rule():
    """Cria EventBridge rule"""
    print("\n📡 Criando EventBridge rule...")
    
    try:
        events.put_rule(
            Name="pod99-transacao-autorizada-rule",
            EventPattern=json.dumps({
                "source": ["pod99.authorization"],
                "detail-type": ["TransacaoAutorizada"],
            }),
            State="ENABLED",
        )
        print("  ✅ pod99-transacao-autorizada-rule")
        
        # Add target (SQS)
        events.put_targets(
            Rule="pod99-transacao-autorizada-rule",
            Targets=[
                {
                    "Id": "1",
                    "Arn": "arn:aws:sqs:us-east-1:000000000000:pod99-accounting-queue.fifo",
                    "RoleArn": "arn:aws:iam::000000000000:role/service-role/EventBridgeRole",
                }
            ],
        )
        print("  ✅ Conectado EventBridge → SQS")
    except events.exceptions.ResourceAlreadyExistsException:
        print("  ℹ️ pod99-transacao-autorizada-rule já existe")
    except Exception as e:
        print(f"  ⚠️ EventBridge: {e}")


# ==================================================================================
# Test Data
# ==================================================================================

def populate_test_data():
    """Popula 100 contas × 3 contratos = 300 registros"""
    print("\n💰 Populando dados de teste (100 contas × 3 contratos)...")
    
    for account_num in range(1, 101):
        account_id = f"ACC-{account_num:03d}"
        limit_amount = 50000 + (account_num * 1000)
        
        for contract_idx in range(1, 4):
            contract_num = (account_num - 1) * 3 + contract_idx
            contract_id = f"CONTA-{contract_num:03d}"
            
            try:
                dynamodb.put_item(
                    TableName="pod99-limits",
                    Item={
                        "id_contrato": {"S": contract_id},
                        "id_conta": {"S": account_id},
                        "limite": {"N": f"{limit_amount}.00"},
                        "disponivel": {"N": f"{limit_amount}.00"},
                        "reservado": {"N": "0.00"},
                        "version": {"N": "0"},
                    },
                )
            except Exception as e:
                print(f"  ❌ Erro ao inserir {contract_id}: {e}")
                return
        
        if account_num % 10 == 0:
            print(f"  ✅ {account_num}/100 contas criadas")
    
    print("  ✅ 300 registros de teste criados!")


# ==================================================================================
# Summary
# ==================================================================================

def show_summary():
    """Mostra resumo do que foi criado"""
    print("\n╔════════════════════════════════════════════════════════════════╗")
    print("║  ✅ SETUP COMPLETO!                                            ║")
    print("╚════════════════════════════════════════════════════════════════╝")
    print()
    
    try:
        tables = dynamodb.list_tables()
        print("📊 Tabelas DynamoDB:")
        for table in tables.get("TableNames", []):
            if "pod99" in table:
                print(f"   ✅ {table}")
    except:
        pass
    
    try:
        queues = sqs.list_queues(QueueNamePrefix="pod99")
        print("\n📦 Filas SQS:")
        for queue_url in queues.get("QueueUrls", []):
            print(f"   ✅ {queue_url.split('/')[-1]}")
    except:
        pass
    
    print("\n✨ Infraestrutura pronta!")
    print("🚀 Próximo passo: Terminal 3 → ./mvnw spring-boot:run")
    print()


# ==================================================================================
# Main
# ==================================================================================

if __name__ == "__main__":
    print("╔════════════════════════════════════════════════════════════════╗")
    print("║  Terraform Simulator - LocalStack Setup                        ║")
    print("╚════════════════════════════════════════════════════════════════╝")
    print()
    
    try:
        # Wait for LocalStack
        print("⏳ Aguardando LocalStack estar pronto...")
        for i in range(30):
            try:
                dynamodb.list_tables()
                print("✅ LocalStack pronto!\n")
                break
            except:
                if i == 29:
                    print("❌ LocalStack não respondeu!")
                    sys.exit(1)
                time.sleep(1)
        
        # Create infrastructure
        create_dynamodb_tables()
        create_sqs_queues()
        create_eventbridge_rule()
        populate_test_data()
        show_summary()
        
    except KeyboardInterrupt:
        print("\n\n❌ Interrompido pelo usuário")
        sys.exit(1)
    except Exception as e:
        print(f"\n❌ Erro: {e}")
        sys.exit(1)
