import asyncio
import gc
import os
import secrets
import subprocess
import time
from pathlib import Path
from typing import Any, List, Optional

from fastapi import FastAPI, HTTPException
from PIL import Image
from pydantic import BaseModel, Field


ROOT = Path(__file__).resolve().parent
MODEL_DIR = Path(
    os.getenv("FLUX_MODEL_PATH", str(ROOT / "models" / "FLUX.2-klein-4B"))
)
GENERATED_DIR = ROOT / "generated"
GENERATED_DIR.mkdir(exist_ok=True)
MIN_FREE_VRAM_MB = int(os.getenv("FLUX_MIN_FREE_VRAM_MB", "4096"))
AUTO_UNLOAD = os.getenv("FLUX_AUTO_UNLOAD", "1") == "1"

app = FastAPI(title="Neuro FLUX.2 Klein Image Worker")
pipeline_lock = asyncio.Lock()
pipeline: Optional[Any] = None
runtime: dict[str, Any] = {}


class ImageGenerationRequest(BaseModel):
    prompt: str = Field(min_length=2, max_length=24000)
    width: int = Field(default=768, ge=256, le=1024)
    height: int = Field(default=768, ge=256, le=1024)
    steps: int = Field(default=4, ge=1, le=20)
    guidance_scale: float = Field(default=1.0, ge=0.0, le=10.0)
    seed: Optional[int] = None
    reference_image_path: Optional[str] = None
    reference_image_paths: List[str] = Field(default_factory=list)


class ImageGenerationResult(BaseModel):
    file_name: str
    seed: int
    width: int
    height: int
    steps: int
    elapsed_seconds: float
    reference_used: bool = False
    reference_count: int = 0


def rounded_dimension(value: int) -> int:
    return max(256, min(1024, value - value % 16))


def cuda_status() -> dict[str, Any]:
    import torch

    if not torch.cuda.is_available():
        return {"cuda": False, "free_vram_mb": 0, "total_vram_mb": 0}
    try:
        output = subprocess.check_output(
            [
                "nvidia-smi",
                "--query-gpu=name,memory.free,memory.total",
                "--format=csv,noheader,nounits",
            ],
            text=True,
            timeout=8,
        ).strip()
        gpu, free_vram_mb, total_vram_mb = [part.strip() for part in output.splitlines()[0].split(",")]
        return {
            "cuda": True,
            "gpu": gpu,
            "free_vram_mb": int(free_vram_mb),
            "total_vram_mb": int(total_vram_mb),
        }
    except Exception:
        pass
    free_bytes, total_bytes = torch.cuda.mem_get_info()
    return {
        "cuda": True,
        "gpu": torch.cuda.get_device_name(0),
        "free_vram_mb": round(free_bytes / 1024 / 1024),
        "total_vram_mb": round(total_bytes / 1024 / 1024),
    }


def ensure_flux_pipeline() -> Any:
    global pipeline, runtime
    if pipeline is not None:
        return pipeline

    import torch
    from diffusers import Flux2KleinPipeline

    status = cuda_status()
    if not status["cuda"]:
        raise RuntimeError("CUDA is unavailable. FLUX.2 Klein requires an NVIDIA GPU.")
    if status["free_vram_mb"] < MIN_FREE_VRAM_MB:
        raise RuntimeError(
            "Not enough free VRAM for FLUX.2 Klein: "
            f"{status['free_vram_mb']} MB free, at least {MIN_FREE_VRAM_MB} MB required. "
            "Unload the LLM model in LM Studio before image generation, then try again."
        )
    if not MODEL_DIR.exists():
        raise RuntimeError(
            f"FLUX.2 Klein model is not installed at {MODEL_DIR}. "
            "Run setup_flux_klein.bat first."
        )

    torch.cuda.empty_cache()
    gc.collect()
    print(f"[flux] loading model={MODEL_DIR} dtype=float16 with CPU offload")
    loaded = Flux2KleinPipeline.from_pretrained(
        str(MODEL_DIR),
        torch_dtype=torch.float16,
        low_cpu_mem_usage=True,
    )
    loaded.enable_model_cpu_offload()
    if hasattr(loaded, "enable_vae_tiling"):
        loaded.enable_vae_tiling()
    pipeline = loaded
    runtime = {
        "model": "black-forest-labs/FLUX.2-klein-4B",
        "dtype": "float16",
        "offload": "model_cpu_offload",
        **cuda_status(),
    }
    print(f"[flux] ready runtime={runtime}")
    return pipeline


def unload_flux_pipeline() -> None:
    global pipeline, runtime
    pipeline = None
    runtime = {}
    gc.collect()
    try:
        import torch

        if torch.cuda.is_available():
            torch.cuda.empty_cache()
    except Exception:
        pass
    print("[flux] pipeline unloaded")


def generate_image_sync(request: ImageGenerationRequest) -> ImageGenerationResult:
    import torch

    active_pipeline = ensure_flux_pipeline()
    seed = request.seed if request.seed is not None else secrets.randbelow(2**31 - 1)
    width = rounded_dimension(request.width)
    height = rounded_dimension(request.height)
    started = time.perf_counter()
    generator = torch.Generator(device="cpu").manual_seed(seed)
    reference_images = []
    reference_paths = list(
        dict.fromkeys(
            [
                *request.reference_image_paths,
                *([request.reference_image_path] if request.reference_image_path else []),
            ]
        )
    )
    for reference_value in reference_paths:
        reference_path = Path(reference_value).resolve()
        allowed_roots = {
            (ROOT / "generated").resolve(),
            (ROOT / "uploads").resolve(),
        }
        if not reference_path.is_file() or reference_path.parent not in allowed_roots:
            raise RuntimeError(f"Reference image is unavailable: {reference_path}")
        reference_images.append(Image.open(reference_path).convert("RGB"))
        print(f"[flux] using reference={reference_path}")
    try:
        result = active_pipeline(
            image=reference_images or None,
            prompt=request.prompt,
            width=width,
            height=height,
            num_inference_steps=request.steps,
            guidance_scale=request.guidance_scale,
            generator=generator,
        )
        file_name = f"flux_{int(time.time())}_{secrets.token_hex(4)}.png"
        result.images[0].save(GENERATED_DIR / file_name, format="PNG")
        return ImageGenerationResult(
            file_name=file_name,
            seed=seed,
            width=width,
            height=height,
            steps=request.steps,
            elapsed_seconds=round(time.perf_counter() - started, 2),
            reference_used=bool(reference_images),
            reference_count=len(reference_images),
        )
    finally:
        if AUTO_UNLOAD:
            unload_flux_pipeline()


@app.get("/health")
async def health():
    return {
        "status": "ok",
        "model_installed": MODEL_DIR.exists(),
        "model_loaded": pipeline is not None,
        "auto_unload": AUTO_UNLOAD,
        "runtime": runtime,
        **cuda_status(),
    }


@app.post("/generate", response_model=ImageGenerationResult)
async def generate_image(request: ImageGenerationRequest):
    async with pipeline_lock:
        try:
            return await asyncio.to_thread(generate_image_sync, request)
        except RuntimeError as exc:
            raise HTTPException(status_code=503, detail=str(exc)) from exc
        except Exception as exc:
            raise HTTPException(status_code=500, detail=f"FLUX generation failed: {exc}") from exc


@app.post("/unload")
async def unload_image_pipeline():
    async with pipeline_lock:
        await asyncio.to_thread(unload_flux_pipeline)
    return {"status": "ok", "model_loaded": False}


if __name__ == "__main__":
    import uvicorn

    uvicorn.run(app, host="127.0.0.1", port=3511)
