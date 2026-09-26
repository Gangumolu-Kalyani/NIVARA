import boto3
import json

client = boto3.client("bedrock-runtime", region_name="us-east-1")

def chat_with_qwen(prompt):
    response = client.invoke_model(
        modelId="qwen.qwen3-32b-v1:0",
        body=json.dumps({
            "messages": [{"role": "user", "content": prompt}],
            "max_tokens": 512
        }),
        contentType="application/json",
        accept="application/json"
    )
    result = json.loads(response["body"].read())
    print(result)

# Test it
chat_with_qwen("Hello! What can you help me with?")
