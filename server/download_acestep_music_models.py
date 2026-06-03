"""Download the ACE-Step models selected for Neuro without duplicate fallbacks."""

from __future__ import annotations

import argparse
import sys
from pathlib import Path

from acestep.model_downloader import download_main_model, download_submodel


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--checkpoints-dir", required=True)
    parser.add_argument("--source", choices=("huggingface", "modelscope"), default="modelscope")
    args = parser.parse_args()

    checkpoints_dir = Path(args.checkpoints_dir).resolve()
    checkpoints_dir.mkdir(parents=True, exist_ok=True)

    print("[neuro-music] Downloading the official ACE-Step 1.5 base bundle...")
    main_ok, main_message = download_main_model(
        checkpoints_dir=checkpoints_dir,
        prefer_source=args.source,
    )
    print(main_message)
    if not main_ok:
        return 1

    print("[neuro-music] Downloading the 0.6B LM recommended for GTX 1080 Ti class GPUs...")
    lm_ok, lm_message = download_submodel(
        "acestep-5Hz-lm-0.6B",
        checkpoints_dir=checkpoints_dir,
        prefer_source=args.source,
    )
    print(lm_message)
    return 0 if lm_ok else 1


if __name__ == "__main__":
    sys.exit(main())

