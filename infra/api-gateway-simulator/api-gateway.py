from fastapi import FastAPI, Header, HTTPException, Request
from fastapi.responses import Response, JSONResponse
import boto3
import httpx
import json
import os
from datetime import datetime

app = FastAPI(
    title="POD99 API Gateway Simulator",
    version="1.0.0"
)

LOCALSTACK_URL = os.getenv("LOCALSTACK_URL", "http://localstack:4566")
BACKEND_URL = os.getenv("BACKEND_URL", "http://host.docker.internal:8080")
AWS_REGION = os.getenv("AWS_REGION", "us-east-1")
LAMBDA_AUTHORIZER_NAME = os.getenv(
    "LAMBDA_AUTHORIZER_NAME",
    "pod99-lambda-authorizer"
)

lambda_client = boto3.client(
    "lambda",
    region_name=AWS_REGION,
    endpoint_url=LOCALSTACK_URL,
    aws_access_key_id="test",
    aws_secret_access_key="test"
)

def invoke_authorizer(authorization_token: str, method_arn: str) -> dict:
    event = {
        "type": "TOKEN",
        "authorizationToken": authorization_token,
        "methodArn": method_arn
    }

    response = lambda_client.invoke(
        FunctionName=LAMBDA_AUTHORIZER_NAME,
        InvocationType="RequestResponse",
        Payload=json.dumps(event).encode("utf-8")
    )

    payload = json.loads(response["Payload"].read())

    if "FunctionError" in response:
        raise HTTPException(
            status_code=500,
            detail="Failed to execute Lambda Authorizer"
        )

    return payload

def extract_account_id(authorizer_response: dict) -> str:
    try:
        statements = authorizer_response["policyDocument"]["Statement"]

        effect = statements[0]["Effect"]

        if effect != "Allow":
            raise HTTPException(
                status_code=401,
                detail="Unauthorized by Lambda Authorizer"
            )

        context = authorizer_response.get("context", {})
        account_id = context.get("accountId")

        if not account_id:
            raise HTTPException(
                status_code=401,
                detail="Lambda Authorizer did not return accountId"
            )

        return account_id

    except KeyError:
        raise HTTPException(
            status_code=500,
            detail="Invalid Lambda Authorizer response"
        )

@app.post("/v1/contratos/{id_contrato}/autorizacoes")
async def authorize_transaction(
    id_contrato: str,
    request: Request,
    authorization: str = Header(None, alias="Authorization"),
    idempotency_key: str = Header(None, alias="Idempotency-Key")
):
    if not authorization:
        raise HTTPException(
            status_code=401,
            detail="Missing Authorization header"
        )
    
    if not idempotency_key:
        return JSONResponse(
            status_code=422,
            content={
                "type": "https://api.pod99.com/errors/validation-error",
                "title": "Validation Error",
                "status": 422,
                "detail": "Missing required header: Idempotency-Key",
                "instance": f"/v1/contratos/{id_contrato}/autorizacoes",
                "timestamp": datetime.utcnow().isoformat() + "Z"
            }
        )

    method_arn = (
        f"arn:aws:execute-api:{AWS_REGION}:000000000000:"
        f"local/local/POST/v1/contratos/{id_contrato}/autorizacoes"
    )

    authorizer_response = invoke_authorizer(
        authorization_token=authorization,
        method_arn=method_arn
    )

    account_id = extract_account_id(authorizer_response)

    body = await request.body()

    backend_url = (
        f"{BACKEND_URL}/v1/contratos/"
        f"{id_contrato}/autorizacoes"
    )

    async with httpx.AsyncClient() as client:
        backend_response = await client.post(
            backend_url,
            content=body,
            headers={
                "Content-Type": request.headers.get(
                    "content-type",
                    "application/json"
                ),
                "Authorization": authorization,
                "Idempotency-Key": idempotency_key,
                "X-Account-Id": account_id
            }
        )

    return Response(
        content=backend_response.content,
        status_code=backend_response.status_code,
        media_type=backend_response.headers.get(
            "content-type",
            "application/json"
        )
    )
