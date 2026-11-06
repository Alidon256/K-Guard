# K-Guard

K-Guard is a FastAPI image-safety API using Qwen2.5-VL and Apple Silicon MPS when available.

## Run locally

```bash
source gv_env/bin/activate
export KGUARD_API_KEY="$(openssl rand -hex 32)"
python -m uvicorn app:app --host 0.0.0.0 --port 8000
```

The API documentation is available at `http://127.0.0.1:8000/docs`.

## Scan an image

```bash
curl -X POST http://127.0.0.1:8000/scan \
  -H "X-API-Key: $KGUARD_API_KEY" \
  -F "file=@images.jfif" \
  -F "extracted_text="
```

The `file` field is required and `extracted_text` is optional. Do not commit API keys,
ngrok credentials, model credentials, or virtual environments.

## Public development tunnel

With the API running, authenticate ngrok locally and start a tunnel:

```bash
ngrok config add-authtoken YOUR_NGROK_TOKEN
ngrok http 8000
```

Use the generated HTTPS forwarding URL with the `/scan` endpoint. The free ngrok URL
changes when the tunnel restarts.

## Evaluation data

The `guardvision_visual_test_images/` directory contains the visual test set and
`kguard_evaluation_report.json` contains the latest evaluation output. The report's
classification scores use provisional labels inferred from filenames because the
dataset has no formal annotation manifest.
