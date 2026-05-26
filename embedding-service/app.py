from __future__ import annotations

import math
import time
from contextlib import asynccontextmanager
from threading import Lock
from typing import Any

import numpy as np
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel, Field


MODEL_NAME = "BAAI/bge-m3"
DIMENSIONS = 1024
DEFAULT_MAX_LENGTH = 8192


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
