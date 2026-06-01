import os
import time
from pathlib import Path

from huggingface_hub import snapshot_download


ROOT = Path(__file__).resolve().parent
MODEL_DIR = ROOT / "models" / "FLUX.2-klein-4B"


def main() -> None:
    MODEL_DIR.mkdir(parents=True, exist_ok=True)
    os.environ.setdefault("HF_HUB_DOWNLOAD_TIMEOUT", "120")
    os.environ.setdefault("HF_HUB_ETAG_TIMEOUT", "30")
    print(f"[flux] downloading official model to {MODEL_DIR}")
    for attempt in range(1, 31):
        try:
            snapshot_download(
                repo_id="black-forest-labs/FLUX.2-klein-4B",
                local_dir=str(MODEL_DIR),
                max_workers=1,
                resume_download=True,
            )
            print("[flux] model download complete")
            return
        except Exception as exc:
            print(f"[flux] download interrupted attempt={attempt}/30: {exc}")
            if attempt == 30:
                raise
            print("[flux] resuming in 8 seconds...")
            time.sleep(8)


if __name__ == "__main__":
    main()
