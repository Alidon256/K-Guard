import asyncio
import io
import json
import logging
import os
import secrets
from contextlib import asynccontextmanager
from pathlib import Path
from typing import Any

import torch
from fastapi import Depends, FastAPI, File, Form, HTTPException, UploadFile
from fastapi.security import APIKeyHeader
from PIL import Image, ImageOps, UnidentifiedImageError
from starlette.concurrency import run_in_threadpool
from transformers import AutoProcessor, Qwen2_5_VLForConditionalGeneration


logger = logging.getLogger("kguard")

MODEL_ID = "Qwen/Qwen2.5-VL-7B-Instruct"
SAFETY_PROMPT_PATH = Path(__file__).with_name("safety_prompt.txt")
MAX_IMAGE_SIZE = 10 * 1024 * 1024
MAX_NEW_TOKENS = 150
API_KEY = os.getenv("KGUARD_API_KEY")
api_key_header = APIKeyHeader(name="X-API-Key", auto_error=False)

if torch.backends.mps.is_available():
    DEVICE = torch.device("mps")
    MODEL_DTYPE = torch.float16
else:
    DEVICE = torch.device("cpu")
    MODEL_DTYPE = torch.float32

# Transformers' generated model stubs currently type `.to()` and `.generate()`
# incompatibly, so these runtime-managed handles remain intentionally dynamic.
processor: Any | None = None
model: Any | None = None
inference_lock = asyncio.Lock()


@asynccontextmanager
async def lifespan(_: FastAPI):
    global model, processor

    logger.info("Loading %s on %s", MODEL_ID, DEVICE)
    processor = AutoProcessor.from_pretrained(MODEL_ID)
    loaded_model: Any = Qwen2_5_VLForConditionalGeneration.from_pretrained(
        MODEL_ID,
        torch_dtype=MODEL_DTYPE,
        low_cpu_mem_usage=True,
    )
    loaded_model.to(DEVICE)
    loaded_model.eval()
    model = loaded_model
    logger.info("KGuard model is ready")
    yield
    model = None
    processor = None
    if DEVICE.type == "mps":
        torch.mps.empty_cache()


app = FastAPI(title="KGuard AI", lifespan=lifespan)


def load_system_prompt() -> str:
    try:
        return SAFETY_PROMPT_PATH.read_text(encoding="utf-8").strip()
    except OSError as exc:
        raise RuntimeError("safety_prompt.txt could not be read.") from exc


def prepare_image(image_bytes: bytes) -> Image.Image:
    try:
        with Image.open(io.BytesIO(image_bytes)) as source:
            return ImageOps.exif_transpose(source).convert("RGB")
    except (UnidentifiedImageError, OSError, ValueError) as exc:
        raise HTTPException(status_code=400, detail="Invalid image uploaded.") from exc


def validate_result(response: str) -> dict[str, str]:
    try:
        result = json.loads(response)
    except json.JSONDecodeError as exc:
        raise RuntimeError(f"Model returned invalid JSON: {response}") from exc

    required_keys = {"action", "reason", "category"}
    if not isinstance(result, dict) or set(result) != required_keys:
        raise RuntimeError("Invalid KGuard response structure.")

    if result["action"] not in {"BLUR", "ALLOW"}:
        raise RuntimeError("Invalid action returned by model.")
    if result["category"] not in {
        "Nudity",
        "Obscenity",
        "Graphic Violence",
        "Cyberbullying",
        "Predatory Behavior",
        "None",
    }:
        raise RuntimeError("Invalid safety category returned.")
    if not isinstance(result["reason"], str):
        raise RuntimeError("Invalid reason returned.")

    return result


def run_kguard_inference(
    image: Image.Image,
    extracted_text: str,
    system_prompt: str,
) -> dict[str, str]:
    if processor is None or model is None:
        raise RuntimeError("KGuard model is not initialized.")

    context = extracted_text.strip() or "No extracted text available."
    messages = [
        {
            "role": "system",
            "content": [{"type": "text", "text": system_prompt}],
        },
        {
            "role": "user",
            "content": [
                {"type": "image", "image": image},
                {
                    "type": "text",
                    "text": (
                        "Analyze this screen capture for child online safety.\n\n"
                        f"Extracted screen text:\n{context}\n\n"
                        "Return the required JSON decision."
                    ),
                },
            ],
        },
    ]

    # This single call expands <|image_pad|> to the exact visual-token count
    # described by image_grid_thw. Do not separately tokenize the chat text.
    inputs = processor.apply_chat_template(
        messages,
        tokenize=True,
        add_generation_prompt=True,
        return_dict=True,
        return_tensors="pt",
    ).to(DEVICE)

    image_token_id = processor.image_token_id
    image_token_count = int((inputs["input_ids"] == image_token_id).sum().item())
    grid = inputs.get("image_grid_thw")
    if image_token_count == 0 or grid is None:
        raise RuntimeError("Multimodal input is missing image tokens or image grid.")
    logger.info(
        "Prepared image tokens=%d grid=%s",
        image_token_count,
        grid.tolist(),
    )

    with torch.inference_mode():
        output_ids = model.generate(
            **inputs,
            max_new_tokens=MAX_NEW_TOKENS,
            do_sample=False,
            use_cache=True,
        )

    prompt_length = inputs["input_ids"].shape[1]
    generated_ids = output_ids[:, prompt_length:]
    response = processor.batch_decode(
        generated_ids,
        skip_special_tokens=True,
        clean_up_tokenization_spaces=False,
    )[0].strip()
    return validate_result(response)


@app.post("/scan")
async def scan_frame(
    file: UploadFile = File(...),
    extracted_text: str = Form(""),
    api_key: str | None = Depends(api_key_header),
) -> dict[str, Any]:
    try:
        if not API_KEY or not api_key or not secrets.compare_digest(api_key, API_KEY):
            raise HTTPException(status_code=401, detail="Invalid or missing API key.")

        image_bytes = await file.read()
        if not image_bytes:
            raise HTTPException(status_code=400, detail="Empty image upload.")
        if len(image_bytes) > MAX_IMAGE_SIZE:
            raise HTTPException(
                status_code=413,
                detail="Image exceeds maximum 10MB size.",
            )

        image = prepare_image(image_bytes)
        system_prompt = load_system_prompt()
        async with inference_lock:
            result = await run_in_threadpool(
                run_kguard_inference,
                image,
                extracted_text,
                system_prompt,
            )

        return {
            "status": "success",
            "model": MODEL_ID,
            "device": str(DEVICE),
            "result": result,
        }
    except HTTPException:
        raise
    except Exception as exc:
        logger.exception("KGuard inference failed")
        raise HTTPException(
            status_code=500,
            detail="KGuard image analysis failed.",
        ) from exc
    finally:
        await file.close()


if __name__ == "__main__":
    import uvicorn

    uvicorn.run(
        "app:app",
        host=os.getenv("HOST", "0.0.0.0"),
        port=int(os.getenv("PORT", "8000")),
        reload=False,
    )
