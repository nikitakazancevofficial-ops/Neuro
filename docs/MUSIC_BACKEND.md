# Neuro Music Backend

Neuro integrates the official [ACE-Step 1.5](https://github.com/ace-step/ACE-Step-1.5)
REST worker as an optional local service. The Android music interface, player,
cover artwork generation and synchronized lyric timeline use the same backend
contract.

## Install

ACE-Step requires Python 3.11-3.12. Neuro uses Python 3.12 for its isolated music
environment:

```powershell
.\setup_acestep_music.bat
```

The installer clones the official ACE-Step repository into
`server\vendors\ACE-Step-1.5`, creates its own environment and downloads the
official main checkpoint into `server\models\ACE-Step-1.5`. Partial downloads can
be resumed by running the file again.

All heavy ACE-Step files stay inside the project folder on the `E:` drive:

- Models: `E:\Portfolio\server\models\ACE-Step-1.5`
- Source and isolated environment: `E:\Portfolio\server\vendors\ACE-Step-1.5`
- Download, Python, CUDA and temporary caches: `E:\Portfolio\server\.local_storage\acestep`

The launcher intentionally overrides `TEMP`, `TMP`, `uv`, `pip`, Hugging Face,
Torch, Triton, Numba, CUDA, ModelScope and XDG cache paths before installation
and before starting the worker.

For Pascal GPUs such as the GTX 1080 Ti, Neuro uses `acestep-v15-turbo`,
PyTorch (`pt`), `float32` generation and DiT CPU offload. `float32` avoids the
NaN/Inf overflow that can affect long float16 songs on pre-Ampere NVIDIA GPUs.
Neuro already prepares the English style prompt, structured lyrics and metadata
through the selected local LM, so the additional ACE-Step LM stays disabled.

To validate prerequisites without cloning code or downloading weights:

```powershell
.\setup_acestep_music.bat --check
```

## Run

```powershell
.\start_neuro.bat
```

When the checkpoint exists, `start_neuro.bat` starts the music worker
automatically. Phones access it only through the main Neuro backend. On Windows,
Neuro recycles the local ACE-Step worker after saving each song so its large
`float32` DiT allocation cannot crowd out FLUX artwork generation or the
operating system. The next music request starts the worker again automatically.
FLUX uses the same on-demand lifecycle after it returns an image. Before either
heavy worker starts, Neuro unloads active LM Studio instances to leave the GPU
available for generation. LM Studio itself keeps running and reloads the
selected chat model when it is needed again.

Neuro keeps the official ACE-Step runtime and the pre-downloaded model files in
one shared location. `setup_acestep_music.bat` creates a Windows junction from
`server/vendors/ACE-Step-1.5/checkpoints` to
`server/models/ACE-Step-1.5`, so the first generation request does not download
a second copy of the checkpoints.

Before FLUX or ACE-Step starts, Neuro checks the Windows system drive. If recent
disk I/O retries, filesystem problems or critically low free space are detected,
`start_neuro.bat` runs the lightweight chat backend only. This prevents a heavy
model load from adding pressure to an unhealthy system SSD. After the SSD has
been checked or replaced, `start_neuro.bat` or `start_neuro_all.bat` automatically starts the chat
backend, FLUX and ACE-Step together. There is intentionally no software override
while Windows reports storage-related crashes.

To run only the music worker for diagnostics:

```powershell
.\run_acestep_music_worker.bat
```

## API

| Endpoint | Purpose |
| --- | --- |
| `GET /music/health` | Check the ACE-Step worker |
| `POST /music/uploads` | Upload source audio for cover generation |
| `POST /music/generate` | Create a text-to-music job |
| `POST /music/cover` | Create a cover job |
| `POST /music/library/{id}/regenerate` | Create a fresh variant with the saved style and lyrics |
| `GET /music/jobs/{id}` | Read job status, lyrics, metadata and tracks |
| `GET /music/jobs/{id}/stream` | Stream job snapshots over SSE |
| `GET /music/jobs/{id}/audio/{index}` | Play or download a generated track |
| `GET /music/jobs/{id}/lyrics/current?position_seconds=12.5` | Read the active lyric line |

## Generation Flow

1. Neuro detects a music request in chat.
2. The selected local LM prepares an English ACE-Step caption, structured lyrics
   and metadata.
3. If LM Studio does not finish the plan within 90 seconds, Neuro continues with
   a built-in local plan instead of leaving the app stuck. Override the limit
   with `MUSIC_PLANNER_TIMEOUT_SECONDS` in `run_server.local.bat`.
4. Neuro submits the plan to the official asynchronous ACE-Step API.
5. The app receives monotonic progress snapshots and the prepared lyrics during
   synthesis.
6. After ACE-Step releases its GPU allocation, a separate CPU Whisper pass
   aligns lyric lines to word timestamps in the generated vocals. If alignment
   cannot run, the player falls back to an explicitly marked `estimated`
   timeline.
7. FLUX creates a semantically related but visually fresh square cover with a
   new seed and composition direction for every result.

The Android studio also has a manual mode. When the user enters both an ACE-Step
style and finished lyrics with section markers such as `[Verse 1]` and
`[Chorus]`, Neuro sends them directly to synthesis without calling the LLM
planner. Instrumental manual mode only requires the style.

When duration is left empty, Neuro chooses a natural length from the request and
lyric structure instead of imposing a fixed 2:30 template. The Android studio
also accepts an optional manual duration in minutes. The official ACE-Step
runtime has a physical maximum of 600 seconds (10 minutes), so Neuro clamps
requests to that supported range.

ACE-Step also supports repaint, stem extraction, multi-track completion and
vocal-to-BGM workflows. They can be exposed as additional Android tools later
without changing the base worker setup.
