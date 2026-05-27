from __future__ import annotations

import json
import logging
import math
import time
from contextlib import asynccontextmanager
from threading import Lock
from typing import Any

import numpy as np
from fastapi import FastAPI, HTTPException, Request
from pydantic import BaseModel, Field


MODEL_NAME = "BAAI/bge-m3"
DIMENSIONS = 1024
DEFAULT_MAX_LENGTH = 8192
BODY_PREVIEW_LENGTH = 120

logger = logging.getLogger("datalaburo.embedding_service")


class ModelState:
    def __init__(self) -> None:
        self.model: Any | None = None
        self.device: str = "unknown"
        self.loaded: bool = False
        self.load_error: str | None = None
        self.load_elapsed_ms: int | None = None

    def load(self) -> None:
        started = time.perf_counter()
        try:
            from FlagEmbedding import BGEM3FlagModel

            device = detect_device()
            use_fp16 = device == "cuda"
            self.device = device
            try:
                self.model = BGEM3FlagModel(MODEL_NAME, use_fp16=use_fp16, device=device)
            except TypeError:
                self.model = BGEM3FlagModel(MODEL_NAME, use_fp16=use_fp16)
            self.loaded = True
            self.load_error = None
        except Exception as exc:  # noqa: BLE001 - surfaced through /model-info and 503 responses.
            self.model = None
            self.loaded = False
            self.load_error = f"{type(exc).__name__}: {exc}"
        finally:
            self.load_elapsed_ms = elapsed_ms(started)


class EmbeddingRequest(BaseModel):
    model: str = MODEL_NAME
    input: str
    normalize: bool = True
    maxLength: int = Field(default=DEFAULT_MAX_LENGTH, ge=1, le=DEFAULT_MAX_LENGTH)


class EmbeddingResponse(BaseModel):
    model: str
    dimensions: int
    embedding: list[float]
    elapsedMs: int


class HealthResponse(BaseModel):
    status: str
    model: str
    loaded: bool


class ModelInfoResponse(BaseModel):
    model: str
    dimensions: int
    device: str
    loaded: bool
    loadElapsedMs: int | None = None
    loadError: str | None = None


state = ModelState()
inference_lock = Lock()


@asynccontextmanager
async def lifespan(app: FastAPI):
    state.load()
    yield


app = FastAPI(
    title="Datalaburo Local Embedding Service",
    version="0.1.0",
    lifespan=lifespan,
)


@app.middleware("http")
async def log_embedding_request(request: Request, call_next):
    if (
        logger.isEnabledFor(logging.DEBUG)
        and request.method == "POST"
        and request.url.path == "/v1/embeddings"
    ):
        body = await request.body()
        log_embedding_request_body(request, body)

        received = False

        async def receive():
            nonlocal received
            if received:
                return {"type": "http.request", "body": b"", "more_body": False}
            received = True
            return {"type": "http.request", "body": body, "more_body": False}

        request = Request(request.scope, receive)

    return await call_next(request)


@app.get("/health", response_model=HealthResponse)
def health() -> HealthResponse:
    return HealthResponse(
        status="ok" if state.loaded else "model_unavailable",
        model=MODEL_NAME,
        loaded=state.loaded,
    )


@app.get("/model-info", response_model=ModelInfoResponse)
def model_info() -> ModelInfoResponse:
    return ModelInfoResponse(
        model=MODEL_NAME,
        dimensions=DIMENSIONS,
        device=state.device,
        loaded=state.loaded,
        loadElapsedMs=state.load_elapsed_ms,
        loadError=state.load_error,
    )


@app.post("/v1/embeddings", response_model=EmbeddingResponse)
def create_embedding(request: EmbeddingRequest) -> EmbeddingResponse:
    validate_request(request)
    if not state.loaded or state.model is None:
        detail = "Model is not loaded"
        if state.load_error:
            detail = f"{detail}: {state.load_error}"
        raise HTTPException(status_code=503, detail=detail)

    started = time.perf_counter()
    try:
        with inference_lock:
            output = state.model.encode(
                [request.input],
                batch_size=1,
                max_length=request.maxLength,
                return_dense=True,
                return_sparse=False,
                return_colbert_vecs=False,
            )
        vector = extract_dense_vector(output)
        if request.normalize:
            vector = normalize_vector(vector)
        validate_vector(vector)
    except HTTPException:
        raise
    except Exception as exc:  # noqa: BLE001 - returned as an explicit local inference error.
        raise HTTPException(status_code=500, detail=f"Inference failed: {type(exc).__name__}: {exc}") from exc

    return EmbeddingResponse(
        model=MODEL_NAME,
        dimensions=DIMENSIONS,
        embedding=[float(value) for value in vector.tolist()],
        elapsedMs=elapsed_ms(started),
    )


def validate_request(request: EmbeddingRequest) -> None:
    if request.model != MODEL_NAME:
        raise HTTPException(status_code=400, detail=f"Unsupported model: {request.model}")
    if request.input is None or not request.input.strip():
        raise HTTPException(status_code=400, detail="Input text must not be blank")


def log_embedding_request_body(request: Request, body: bytes | None) -> None:
    body_info = describe_body_for_log(body)
    logger.debug(
        "BGE-M3 raw request: method=%s path=%s contentType=%s contentLength=%s "
        "transferEncoding=%s rawBodyLength=%s bodyEmpty=%s bodyPreview=%r "
        "jsonKeys=%s hasModel=%s hasInput=%s hasNormalize=%s inputLength=%s jsonParseError=%s",
        request.method,
        request.url.path,
        request.headers.get("content-type"),
        request.headers.get("content-length"),
        request.headers.get("transfer-encoding"),
        body_info["raw_body_length"],
        body_info["body_empty"],
        body_info["preview"],
        body_info["json_keys"],
        body_info["has_model"],
        body_info["has_input"],
        body_info["has_normalize"],
        body_info["input_length"],
        body_info["json_parse_error"],
    )


def describe_body_for_log(body: bytes | None) -> dict[str, Any]:
    if body is None:
        return {
            "raw_body_length": None,
            "body_empty": True,
            "preview": "",
            "json_keys": [],
            "has_model": False,
            "has_input": False,
            "has_normalize": False,
            "input_length": None,
            "json_parse_error": "body is None",
        }

    decoded = body.decode("utf-8", errors="replace")
    preview_source = decoded
    json_keys: list[str] = []
    has_model = False
    has_input = False
    has_normalize = False
    input_length: int | None = None
    json_parse_error: str | None = None

    if decoded.strip():
        try:
            parsed = json.loads(decoded)
            if isinstance(parsed, dict):
                json_keys = sorted(parsed.keys())
                has_model = "model" in parsed
                has_input = "input" in parsed
                has_normalize = "normalize" in parsed
                input_value = parsed.get("input")
                if isinstance(input_value, str):
                    input_length = len(input_value)
                    redacted_input = f"<redacted length={input_length}>"
                else:
                    redacted_input = "<redacted non-string>"

                redacted = dict(parsed)
                if "input" in redacted:
                    redacted["input"] = redacted_input
                preview_source = json.dumps(redacted, ensure_ascii=False)
            else:
                json_parse_error = f"JSON root is {type(parsed).__name__}, expected object"
        except json.JSONDecodeError as exc:
            json_parse_error = f"{exc.msg} at pos {exc.pos}"

    preview = " ".join(preview_source.split())[:BODY_PREVIEW_LENGTH]
    return {
        "raw_body_length": len(body),
        "body_empty": len(body) == 0,
        "preview": preview,
        "json_keys": json_keys,
        "has_model": has_model,
        "has_input": has_input,
        "has_normalize": has_normalize,
        "input_length": input_length,
        "json_parse_error": json_parse_error,
    }


def extract_dense_vector(output: Any) -> np.ndarray:
    if not isinstance(output, dict) or "dense_vecs" not in output:
        raise HTTPException(status_code=500, detail="Model response did not include dense_vecs")

    dense_vecs = output["dense_vecs"]
    vector = np.asarray(dense_vecs, dtype=np.float32)
    if vector.ndim == 2:
        vector = vector[0]
    if vector.ndim != 1:
        raise HTTPException(status_code=500, detail=f"Unexpected dense vector shape: {vector.shape}")
    return vector


def normalize_vector(vector: np.ndarray) -> np.ndarray:
    norm = float(np.linalg.norm(vector))
    if norm == 0.0 or not math.isfinite(norm):
        raise HTTPException(status_code=500, detail="Dense vector norm is invalid")
    return vector / norm


def validate_vector(vector: np.ndarray) -> None:
    if vector.shape[0] != DIMENSIONS:
        raise HTTPException(
            status_code=500,
            detail=f"Expected {DIMENSIONS} dimensions, got {vector.shape[0]}",
        )
    if not np.isfinite(vector).all():
        raise HTTPException(status_code=500, detail="Embedding contains NaN or infinite values")


def detect_device() -> str:
    try:
        import torch

        if torch.cuda.is_available():
            return "cuda"
        if hasattr(torch.backends, "mps") and torch.backends.mps.is_available():
            return "mps"
    except Exception:  # noqa: BLE001 - device detection should not block CPU fallback.
        return "cpu"
    return "cpu"


def elapsed_ms(started: float) -> int:
    return int((time.perf_counter() - started) * 1000)
