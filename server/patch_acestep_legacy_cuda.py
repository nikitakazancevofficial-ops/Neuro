"""Make the bundled ACE-Step CUDA dtype recommendation effective.

ACE-Step 1.5 reports that pre-Ampere GPUs can recover from float16 overflow by
setting ACESTEP_DTYPE=float32, but the downloaded CUDA loader does not read that
variable. Keep the vendor checkout reproducibly patched after setup or updates.
"""

from __future__ import annotations

from pathlib import Path


ROOT = Path(__file__).resolve().parent
TARGET = (
    ROOT
    / "vendors"
    / "ACE-Step-1.5"
    / "acestep"
    / "core"
    / "generation"
    / "handler"
    / "init_service_orchestrator.py"
)

MAP_MARKER = """_CUDA_DTYPE_MAP = {
    "float32": torch.float32,
    "float16": torch.float16,
    "bfloat16": torch.bfloat16,
}
"""
MAP_INSERT_AFTER = """_ROCM_DTYPE_MAP = {
    "float32": torch.float32,
    "float16": torch.float16,
    "bfloat16": torch.bfloat16,
}
"""
RESOLVER = '''

def _resolve_cuda_dtype() -> torch.dtype:
    """Return the requested CUDA model dtype or the hardware default."""

    raw = os.environ.get("ACESTEP_DTYPE", "auto").strip().lower()
    if raw in ("", "auto"):
        return torch.bfloat16 if gpu_config.cuda_supports_bfloat16() else torch.float16

    dtype = _CUDA_DTYPE_MAP.get(raw)
    if dtype is None:
        logger.warning(
            f"[initialize_service] Unknown ACESTEP_DTYPE={raw!r}; "
            "falling back to the CUDA hardware default."
        )
        return torch.bfloat16 if gpu_config.cuda_supports_bfloat16() else torch.float16

    if dtype == torch.bfloat16 and not gpu_config.cuda_supports_bfloat16():
        logger.warning(
            "[initialize_service] ACESTEP_DTYPE=bfloat16 is unavailable on this CUDA GPU; "
            "falling back to float16."
        )
        return torch.float16

    logger.info(f"[initialize_service] Using ACESTEP_DTYPE={raw} override for CUDA.")
    return dtype
'''
RESOLVER_INSERT_BEFORE = "\n\nclass InitServiceOrchestratorMixin:"
OLD_CUDA_BRANCH = """            elif resolved_device == "cuda":
                if gpu_config.cuda_supports_bfloat16():
                    self.dtype = torch.bfloat16
                else:
                    self.dtype = torch.float16
                    logger.info(
                        "[initialize_service] Pre-Ampere CUDA detected: "
                        "using float16 instead of bfloat16."
                    )
"""
NEW_CUDA_BRANCH = """            elif resolved_device == "cuda":
                self.dtype = _resolve_cuda_dtype()
                if self.dtype == torch.float16 and not gpu_config.cuda_supports_bfloat16():
                    logger.info(
                        "[initialize_service] Pre-Ampere CUDA detected: "
                        "using float16 instead of bfloat16."
                    )
"""


def apply_patch() -> None:
    if not TARGET.is_file():
        raise SystemExit(f"ACE-Step loader is missing: {TARGET}")

    source = TARGET.read_text(encoding="utf-8")
    changed = False

    if MAP_MARKER not in source:
        if MAP_INSERT_AFTER not in source:
            raise SystemExit("ACE-Step loader layout changed: dtype map insertion point is missing")
        source = source.replace(MAP_INSERT_AFTER, MAP_INSERT_AFTER + MAP_MARKER, 1)
        changed = True

    if "def _resolve_cuda_dtype()" not in source:
        if RESOLVER_INSERT_BEFORE not in source:
            raise SystemExit("ACE-Step loader layout changed: resolver insertion point is missing")
        source = source.replace(RESOLVER_INSERT_BEFORE, RESOLVER + RESOLVER_INSERT_BEFORE, 1)
        changed = True

    if OLD_CUDA_BRANCH in source:
        source = source.replace(OLD_CUDA_BRANCH, NEW_CUDA_BRANCH, 1)
        changed = True
    elif NEW_CUDA_BRANCH not in source:
        raise SystemExit("ACE-Step loader layout changed: CUDA dtype branch is missing")

    if changed:
        TARGET.write_text(source, encoding="utf-8")
        print("ACE-Step CUDA dtype compatibility patch applied.")
    else:
        print("ACE-Step CUDA dtype compatibility patch is ready.")


if __name__ == "__main__":
    apply_patch()
