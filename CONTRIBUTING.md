# Contributing to Neuro

Neuro is an early-stage local AI assistant. Contributions, experiments and focused bug reports are welcome.

## Local Development

1. Copy `local.properties.example` to `local.properties`.
2. Copy `run_server.local.bat.example` to `run_server.local.bat`.
3. Configure your Android SDK, LAN address and LM Studio endpoint.
4. Start `start_neuro.bat`.
5. Build with `gradlew.bat :app:assembleDebug`.

## Before Opening a Pull Request

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest
Push-Location server
.\.venv\Scripts\python.exe -m unittest test_image_routing.py
Pop-Location
```

Keep model weights, local memory, generated files, uploads, logs and environment folders out of Git.
