# Neuro v1.1.0

This release turns Neuro into a more complete local-first AI assistant with a ready Android APK and a one-file Windows launcher.

## Highlights

- Ready-to-install Android APK attached to the GitHub release.
- One-file Windows startup through `start_neuro.bat`.
- Visible `Neuro Server` console with the phone URL, backend status and log paths.
- AI Music studio powered by local ACE-Step 1.5.
- Chat-to-song workflow: Neuro writes structured lyrics, style prompts and metadata before generation.
- Manual music mode for direct user-written styles and lyrics.
- Optional duration control, with natural AI-chosen duration up to the ACE-Step 10-minute runtime limit.
- Music library, player, lyric timeline and regenerate button.
- Fresh semantically related cover artwork for generated tracks.

## Basic Setup

1. Install the APK on Android.
2. Start LM Studio and enable the OpenAI-compatible server on `http://127.0.0.1:1234/v1`.
3. Download or clone the repository on Windows.
4. Run `start_neuro.bat`.
5. Enter the shown URL in the Android app under `Settings -> PC connection`.

Large model weights are not included in the repository or APK. Optional FLUX and ACE-Step models are installed locally with the provided setup scripts.
