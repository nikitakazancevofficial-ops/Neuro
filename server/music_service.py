"""ACE-Step 1.5 integration helpers for Neuro.

The official ACE-Step API owns model loading and audio synthesis. Neuro keeps a
small facade around it so the Android client can use one backend URL for chat,
uploads, progress polling, playback and synchronized lyrics.
"""

from __future__ import annotations

import asyncio
import difflib
import json
import mimetypes
import os
import re
import subprocess
import time
import unicodedata
import uuid
from copy import deepcopy
from pathlib import Path
from typing import Any, Awaitable, Callable, Dict, List, Optional
from urllib.parse import urlparse

import httpx
from fastapi import HTTPException
from pydantic import BaseModel, Field


MUSIC_WORKER_URL = os.getenv("MUSIC_WORKER_URL", "http://127.0.0.1:3512").rstrip("/")
SUPPORTED_AUDIO_EXTENSIONS = {".aac", ".flac", ".m4a", ".mp3", ".ogg", ".opus", ".wav", ".webm"}
TERMINAL_STATUSES = {"completed", "failed"}

MUSIC_ACTION_RE = re.compile(
    r"(?:сгенерир|создай|создать|сочини|сочинить|сделай|сделать|напиши|написать|"
    r"generate|create|compose|make|write)",
    re.IGNORECASE,
)
MUSIC_TARGET_RE = re.compile(
    r"(?:песн|музык|трек|композиц|саундтрек|бит|мелоди|song|music|track|soundtrack|beat|melody)",
    re.IGNORECASE,
)
COVER_RE = re.compile(r"(?:кавер|перепой|перепеть|cover|remix|аранжиров)", re.IGNORECASE)
SECTION_RE = re.compile(r"^\s*\[?([^\]\n:]{2,40})\]?\s*:?\s*$")
MIN_MUSIC_DURATION_SECONDS = 10.0
MAX_MUSIC_DURATION_SECONDS = 600.0


class MusicGenerationRequest(BaseModel):
    user_prompt: str = Field(min_length=2, max_length=12000)
    task_type: str = "text2music"
    source_audio_url: Optional[str] = None
    caption: Optional[str] = Field(default=None, max_length=12000)
    lyrics: Optional[str] = Field(default=None, max_length=24000)
    vocal_language: Optional[str] = None
    duration: Optional[float] = Field(
        default=None,
        ge=MIN_MUSIC_DURATION_SECONDS,
        le=MAX_MUSIC_DURATION_SECONDS,
    )
    bpm: Optional[int] = Field(default=None, ge=30, le=300)
    keyscale: Optional[str] = None
    timesignature: Optional[str] = None
    instrumental: bool = False
    audio_cover_strength: float = Field(default=0.7, ge=0.0, le=1.0)
    inference_steps: int = Field(default=8, ge=1, le=20)
    batch_size: int = Field(default=1, ge=1, le=8)
    response_language: str = "ru"


def is_music_generation_request(message: str) -> bool:
    clean = " ".join(str(message or "").strip().split())
    if not clean:
        return False
    return bool(
        (MUSIC_ACTION_RE.search(clean) and MUSIC_TARGET_RE.search(clean))
        or (COVER_RE.search(clean) and MUSIC_TARGET_RE.search(clean))
    )


def normalize_task_type(value: str) -> str:
    clean = str(value or "text2music").strip().lower()
    return clean if clean in {"text2music", "cover"} else "text2music"


def optional_int(value: Any) -> Optional[int]:
    try:
        return int(value)
    except (TypeError, ValueError):
        return None


def _clamp_music_duration(value: float) -> float:
    return min(MAX_MUSIC_DURATION_SECONDS, max(MIN_MUSIC_DURATION_SECONDS, float(value)))


def estimate_music_duration(
    user_prompt: str = "",
    lyrics: str = "",
    instrumental: bool = False,
) -> float:
    """Choose a natural duration when neither the user nor the planner specifies one."""

    clean_prompt = str(user_prompt or "").casefold()
    minute_match = re.search(r"(\d+(?:[.,]\d+)?)\s*(?:min(?:ute)?s?|мин(?:ут[аы]?)?)", clean_prompt)
    if minute_match:
        return _clamp_music_duration(float(minute_match.group(1).replace(",", ".")) * 60.0)
    second_match = re.search(r"(\d+(?:[.,]\d+)?)\s*(?:sec(?:ond)?s?|сек(?:унд[аы]?)?)", clean_prompt)
    if second_match:
        return _clamp_music_duration(float(second_match.group(1).replace(",", ".")))

    lyric_lines = _lyrics_lines(lyrics)
    if lyric_lines:
        return _clamp_music_duration(28.0 + sum(max(5.0, len(line["text"].split()) * 1.15) for line in lyric_lines))
    return 210.0 if instrumental else 240.0


def _language_for_lyrics(response_language: str) -> str:
    clean = str(response_language or "ru").strip().lower().split("-", 1)[0]
    return clean if clean in {"ru", "en", "uk", "de", "es", "fr", "it", "pt", "pl", "tr", "zh", "ja"} else "ru"


def _fallback_lyrics(language: str) -> str:
    if language == "en":
        return (
            "[Verse 1]\n"
            "The evening settles softly over streets of fading light,\n"
            "I carry quiet questions through the slowly growing night.\n\n"
            "[Chorus]\n"
            "Let the distant skyline answer in a low and tender tone,\n"
            "I am looking for a meaning in the places I have known.\n\n"
            "[Verse 2]\n"
            "Every road becomes a memory, every window holds a spark,\n"
            "Still I keep a little hope alive and glowing in the dark.\n\n"
            "[Chorus]\n"
            "Let the distant skyline answer in a low and tender tone,\n"
            "I am looking for a meaning in the places I have known."
        )
    return (
        "[Куплет 1]\n"
        "Вечер медленно ложится на знакомые дома,\n"
        "Я ищу простой ответ там, где сгущается зима.\n\n"
        "[Припев]\n"
        "Пусть далёкий свет расскажет, куда дальше нам идти,\n"
        "Я храню немного веры на своём большом пути.\n\n"
        "[Куплет 2]\n"
        "Каждый город станет памятью, в окне погаснет день,\n"
        "Но внутри ещё останется моя живая тень.\n\n"
        "[Припев]\n"
        "Пусть далёкий свет расскажет, куда дальше нам идти,\n"
        "Я храню немного веры на своём большом пути."
    )


def fallback_music_plan(
    user_prompt: str,
    response_language: str = "ru",
    task_type: str = "text2music",
    instrumental: bool = False,
) -> Dict[str, Any]:
    """Return a useful plan when the external chat planner is unavailable."""

    clean = " ".join(str(user_prompt or "").strip().split())
    language = _language_for_lyrics(response_language)
    lowered = clean.casefold()
    mood = "melancholic, reflective" if any(word in lowered for word in ("груст", "невес", "печал", "sad", "melanch")) else "expressive, cinematic"
    caption = (
        f"{mood} contemporary song with a memorable melodic hook, emotionally precise arrangement, "
        f"natural dynamics, polished studio mix, coherent verses and chorus. Theme: {clean}"
    )
    return {
        "title": "Новый трек" if language != "en" else "New track",
        "task_type": normalize_task_type(task_type),
        "caption": caption[:12000],
        "lyrics": "[Instrumental]" if instrumental else _fallback_lyrics(language),
        "vocal_language": language,
        "duration": estimate_music_duration(clean, instrumental=instrumental),
        "bpm": 78 if "melancholic" in mood else 104,
        "keyscale": "A minor" if "melancholic" in mood else "C major",
        "timesignature": "4",
        "instrumental": instrumental,
    }


def _extract_json_object(raw: str) -> Dict[str, Any]:
    clean = str(raw or "").strip()
    if clean.startswith("```"):
        clean = re.sub(r"^```(?:json)?\s*", "", clean, flags=re.IGNORECASE)
        clean = re.sub(r"\s*```$", "", clean)
    start = clean.find("{")
    end = clean.rfind("}")
    if start < 0 or end <= start:
        return {}
    try:
        parsed = json.loads(clean[start : end + 1], strict=False)
    except json.JSONDecodeError:
        return {}
    return parsed if isinstance(parsed, dict) else {}


def parse_music_plan(
    raw: str,
    user_prompt: str,
    response_language: str = "ru",
    task_type: str = "text2music",
    instrumental: bool = False,
) -> Dict[str, Any]:
    fallback = fallback_music_plan(user_prompt, response_language, task_type, instrumental)
    parsed = _extract_json_object(raw)
    if not parsed:
        return fallback

    result = dict(fallback)
    title = str(parsed.get("title") or "").strip()
    caption = str(parsed.get("caption") or parsed.get("style") or "").strip()
    lyrics = str(parsed.get("lyrics") or "").strip()
    language = str(parsed.get("vocal_language") or parsed.get("language") or "").strip().lower()
    keyscale = str(parsed.get("keyscale") or parsed.get("key_scale") or "").strip()
    timesignature = str(parsed.get("timesignature") or parsed.get("time_signature") or "").strip()
    if title:
        result["title"] = title[:120]
    if caption:
        result["caption"] = caption[:12000]
    if instrumental:
        result["lyrics"] = "[Instrumental]"
    elif lyrics:
        result["lyrics"] = lyrics[:24000]
    if language:
        result["vocal_language"] = language[:16]
    if keyscale:
        result["keyscale"] = keyscale[:40]
    if timesignature:
        result["timesignature"] = timesignature[:8]
    try:
        result["duration"] = _clamp_music_duration(float(parsed.get("duration") or result["duration"]))
    except (TypeError, ValueError):
        pass
    try:
        result["bpm"] = min(300, max(30, int(parsed.get("bpm") or result["bpm"])))
    except (TypeError, ValueError):
        pass
    result["task_type"] = normalize_task_type(task_type)
    result["instrumental"] = instrumental
    return result


def _lyrics_lines(lyrics: str) -> List[Dict[str, str]]:
    if not lyrics or lyrics.strip().casefold() == "[instrumental]":
        return []
    section = ""
    lines: List[Dict[str, str]] = []
    for raw_line in lyrics.splitlines():
        line = raw_line.strip()
        if not line:
            continue
        section_match = SECTION_RE.match(line)
        if section_match and (line.startswith("[") or line.endswith(":")):
            section = section_match.group(1).strip()
            continue
        lines.append({"section": section, "text": line})
    return lines


def build_lyrics_timeline(lyrics: str, duration: float) -> List[Dict[str, Any]]:
    """Build an estimated timeline until Whisper alignment is available."""

    lines = _lyrics_lines(lyrics)
    if not lines:
        return []
    line_duration = max(1.0, float(duration or 120.0) / len(lines))
    return [
        {
            **line,
            "start_seconds": round(index * line_duration, 3),
            "end_seconds": round((index + 1) * line_duration, 3),
            "timing_source": "estimated",
        }
        for index, line in enumerate(lines)
    ]


def _normalized_words(value: str) -> List[str]:
    normalized = unicodedata.normalize("NFKC", str(value or "")).casefold()
    return re.findall(r"[\w']+", normalized, flags=re.UNICODE)


def align_lyrics_timeline(
    lyrics: str,
    duration: float,
    transcribed_words: List[Dict[str, Any]],
) -> List[Dict[str, Any]]:
    """Align lyric lines to Whisper word timestamps while preserving song sections."""

    lines = _lyrics_lines(lyrics)
    words = [
        {
            "text": str(word.get("text") or word.get("word") or "").strip(),
            "start": float(word.get("start") or 0.0),
            "end": float(word.get("end") or word.get("start") or 0.0),
        }
        for word in transcribed_words
        if str(word.get("text") or word.get("word") or "").strip()
    ]
    if not lines or not words:
        return build_lyrics_timeline(lyrics, duration)

    normalized_words = [_normalized_words(word["text"])[0] if _normalized_words(word["text"]) else "" for word in words]
    total_tokens = max(1, sum(max(1, len(_normalized_words(line["text"]))) for line in lines))
    vocal_start = max(0.0, words[0]["start"])
    vocal_end = min(float(duration), max(vocal_start + 1.0, words[-1]["end"]))
    cursor = 0
    consumed_tokens = 0
    starts: List[float] = []
    sources: List[str] = []

    for line in lines:
        line_tokens = _normalized_words(line["text"])
        expected_position = int(round(consumed_tokens / total_tokens * len(words)))
        search_start = max(cursor, expected_position - 10)
        search_end = min(len(words), max(search_start + 1, expected_position + max(14, len(line_tokens) * 3)))
        best_score = 0.0
        best_range: Optional[tuple[int, int]] = None
        for start in range(search_start, search_end):
            for size in range(max(1, len(line_tokens) - 2), len(line_tokens) + 4):
                end = min(len(words), start + size)
                if end <= start:
                    continue
                candidate = [word for word in normalized_words[start:end] if word]
                score = difflib.SequenceMatcher(None, line_tokens, candidate).ratio()
                if score > best_score:
                    best_score = score
                    best_range = (start, end)

        if best_range is not None and best_score >= 0.34:
            start, end = best_range
            starts.append(words[start]["start"])
            sources.append("whisper_aligned")
            cursor = max(cursor, end)
        else:
            fallback_ratio = consumed_tokens / total_tokens
            starts.append(vocal_start + (vocal_end - vocal_start) * fallback_ratio)
            sources.append("whisper_estimated")
        consumed_tokens += max(1, len(line_tokens))

    for index in range(1, len(starts)):
        starts[index] = max(starts[index], starts[index - 1] + 0.05)
    return [
        {
            **line,
            "start_seconds": round(starts[index], 3),
            "end_seconds": round(starts[index + 1] if index + 1 < len(starts) else max(starts[index] + 0.5, vocal_end), 3),
            "timing_source": sources[index],
        }
        for index, line in enumerate(lines)
    ]


def current_lyrics_line(timeline: List[Dict[str, Any]], position_seconds: float) -> Optional[Dict[str, Any]]:
    for line in timeline:
        if float(line.get("start_seconds", 0.0)) <= position_seconds < float(line.get("end_seconds", 0.0)):
            return line
    return timeline[-1] if timeline and position_seconds >= float(timeline[-1].get("end_seconds", 0.0)) else None


def worker_failure_message(row: Dict[str, Any], response_language: str = "en") -> str:
    """Return a short user-facing explanation for an ACE-Step job failure."""

    details = "\n".join(
        str(value or "")
        for value in (row.get("error"), row.get("progress_text"), row.get("result"))
    )
    language = _language_for_lyrics(response_language)
    if "NaN or Inf latents" in details or "Float16 overflow" in details:
        if language == "ru":
            return (
                "ACE-Step остановил генерацию из-за числовой нестабильности модели. "
                "Neuro включил стабильный режим для вашей видеокарты. "
                "Перезапустите start_neuro_all.bat и повторите запрос."
            )
        if language == "uk":
            return (
                "ACE-Step зупинив генерацію через числову нестабільність моделі. "
                "Neuro увімкнув стабільний режим для вашої відеокарти. "
                "Перезапустіть start_neuro_all.bat і повторіть запит."
            )
        return (
            "ACE-Step stopped generation because the model became numerically unstable. "
            "Neuro enabled the stable mode for your GPU. Restart start_neuro_all.bat and try again."
        )

    if language == "ru":
        return "ACE-Step не смог завершить трек. Повторите запрос или перезапустите start_neuro_all.bat."
    if language == "uk":
        return "ACE-Step не зміг завершити трек. Повторіть запит або перезапустіть start_neuro_all.bat."
    return "ACE-Step could not finish the track. Try again or restart start_neuro_all.bat."


Planner = Callable[[str, str, str, bool], Awaitable[Dict[str, Any]]]
CoverGenerator = Callable[[Dict[str, Any]], Awaitable[Optional[str]]]
ResourcePreparer = Callable[[], Awaitable[None]]
TimelineAligner = Callable[[str, str, float, str], Awaitable[List[Dict[str, Any]]]]


class MusicService:
    def __init__(self, root: Path, public_server_url: str, worker_url: str = MUSIC_WORKER_URL) -> None:
        self.root = root
        self.public_server_url = public_server_url.rstrip("/")
        self.worker_url = worker_url.rstrip("/")
        self.upload_dir = root / "music_uploads"
        self.upload_dir.mkdir(exist_ok=True)
        self.generated_dir = root / "music_generated"
        self.generated_dir.mkdir(exist_ok=True)
        self.library_path = root / "music_library.json"
        self.library: Dict[str, Dict[str, Any]] = self._load_library()
        self.jobs: Dict[str, Dict[str, Any]] = {}
        self.tasks: Dict[str, asyncio.Task[Any]] = {}
        self.planner: Optional[Planner] = None
        self.cover_generator: Optional[CoverGenerator] = None
        self.before_synthesis: Optional[ResourcePreparer] = None
        self.timeline_aligner: Optional[TimelineAligner] = None
        self.lock = asyncio.Lock()

    def snapshot(self, job: Dict[str, Any]) -> Dict[str, Any]:
        return deepcopy(job)

    def _load_library(self) -> Dict[str, Dict[str, Any]]:
        if not self.library_path.exists():
            return {}
        try:
            parsed = json.loads(self.library_path.read_text(encoding="utf-8"))
            tracks = parsed.get("tracks", {}) if isinstance(parsed, dict) else {}
            return tracks if isinstance(tracks, dict) else {}
        except Exception as exc:
            print(f"[music-library] cannot load library: {exc}")
            return {}

    def _save_library(self) -> None:
        payload = {"version": 1, "tracks": self.library}
        temp_path = self.library_path.with_suffix(".tmp")
        temp_path.write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")
        temp_path.replace(self.library_path)

    def library_snapshot(self) -> List[Dict[str, Any]]:
        return sorted(
            (self._public_track(track) for track in self.library.values()),
            key=lambda track: int(track.get("created_at") or 0),
            reverse=True,
        )

    def library_track(self, track_id: str) -> Dict[str, Any]:
        track = self.library.get(track_id)
        if not isinstance(track, dict):
            raise HTTPException(status_code=404, detail="Music track not found")
        return self._public_track(track)

    def _public_track(self, track: Dict[str, Any]) -> Dict[str, Any]:
        snapshot = deepcopy(track)
        track_id = str(snapshot.get("id") or "")
        snapshot["audio_url"] = f"{self.public_server_url}/music/library/{track_id}/audio"
        snapshot["bpm"] = optional_int(snapshot.get("bpm"))
        cover_url = str(snapshot.get("cover_url") or "")
        if "/generated/" in cover_url:
            snapshot["cover_url"] = f"{self.public_server_url}/generated/{cover_url.rsplit('/generated/', 1)[1]}"
        return snapshot

    def job_snapshot(self, job_id: str) -> Optional[Dict[str, Any]]:
        job = self.jobs.get(job_id)
        if isinstance(job, dict):
            return self.snapshot(job)
        tracks = [track for track in self.library_snapshot() if track.get("job_id") == job_id]
        if not tracks:
            return None
        first = tracks[0]
        return {
            "id": job_id,
            "status": "completed",
            "stage": "completed",
            "progress": 1.0,
            "title": first.get("title"),
            "caption": first.get("caption") or "",
            "lyrics": first.get("lyrics") or "",
            "lyrics_timeline": first.get("lyrics_timeline") or [],
            "tracks": tracks,
            "error": None,
        }

    async def update(self, job_id: str, **changes: Any) -> Dict[str, Any]:
        async with self.lock:
            job = self.jobs[job_id]
            job.update(changes)
            return self.snapshot(job)

    def source_audio_path(self, url: Optional[str]) -> Optional[Path]:
        if not url or "/music/uploads/" not in url:
            return None
        name = Path(url.split("/music/uploads/", 1)[1].split("?", 1)[0]).name
        candidate = (self.upload_dir / name).resolve()
        if candidate.parent != self.upload_dir.resolve() or not candidate.exists():
            return None
        return candidate

    async def save_upload(self, filename: str, content: bytes) -> str:
        ext = Path(filename or "track.wav").suffix.lower() or ".wav"
        if ext not in SUPPORTED_AUDIO_EXTENSIONS:
            raise HTTPException(status_code=400, detail="Unsupported audio format")
        if not content:
            raise HTTPException(status_code=400, detail="Audio file is empty")
        name = f"{uuid.uuid4().hex}{ext}"
        (self.upload_dir / name).write_bytes(content)
        return f"{self.public_server_url}/music/uploads/{name}"

    async def create_job(self, request: MusicGenerationRequest) -> Dict[str, Any]:
        task_type = normalize_task_type(request.task_type)
        source_path = self.source_audio_path(request.source_audio_url)
        if task_type == "cover" and source_path is None:
            raise HTTPException(status_code=400, detail="Cover generation requires an uploaded source audio file")
        job_id = uuid.uuid4().hex[:16]
        job = {
            "id": job_id,
            "status": "queued",
            "stage": "queued",
            "progress": 0.0,
            "user_prompt": request.user_prompt,
            "task_type": task_type,
            "source_audio_url": request.source_audio_url,
            "source_audio_path": str(source_path) if source_path else None,
            "caption": request.caption or "",
            "lyrics": request.lyrics or "",
            "lyrics_timeline": [],
            "vocal_language": request.vocal_language or "",
            "duration": request.duration,
            "bpm": request.bpm,
            "keyscale": request.keyscale or "",
            "timesignature": request.timesignature or "",
            "instrumental": request.instrumental,
            "audio_cover_strength": request.audio_cover_strength,
            "inference_steps": request.inference_steps,
            "batch_size": request.batch_size,
            "response_language": request.response_language,
            "worker_task_id": None,
            "tracks": [],
            "error": None,
            "created_at": int(time.time()),
            "started_at": None,
            "completed_at": None,
        }
        async with self.lock:
            self.jobs[job_id] = job
        self.schedule(job_id)
        return self.snapshot(job)

    async def regenerate_track(self, track_id: str) -> Dict[str, Any]:
        track = self.library.get(track_id)
        if not isinstance(track, dict):
            raise HTTPException(status_code=404, detail="Music track not found")
        lyrics = str(track.get("lyrics") or "").strip()
        return await self.create_job(
            MusicGenerationRequest(
                user_prompt=str(track.get("caption") or track.get("prompt") or "regenerate track"),
                task_type="text2music",
                caption=str(track.get("caption") or track.get("prompt") or ""),
                lyrics=lyrics,
                vocal_language=str(track.get("vocal_language") or ""),
                duration=_clamp_music_duration(float(track.get("duration") or estimate_music_duration(lyrics=lyrics))),
                bpm=optional_int(track.get("bpm")),
                keyscale=str(track.get("keyscale") or ""),
                timesignature=str(track.get("timesignature") or ""),
                instrumental=lyrics.casefold() == "[instrumental]",
            )
        )

    def schedule(self, job_id: str) -> None:
        running = self.tasks.get(job_id)
        if running is not None and not running.done():
            return
        task = asyncio.create_task(self.run_job(job_id))
        self.tasks[job_id] = task
        task.add_done_callback(lambda _: self.tasks.pop(job_id, None))

    def _worker_offline_error(self, exc: Exception, response_language: str = "en") -> RuntimeError:
        language = _language_for_lyrics(response_language)
        if language == "ru":
            message = (
                "Музыкальный движок ACE-Step не запущен. Запустите start_neuro.bat. "
                "Если Neuro включил безопасный режим, сначала проверьте системный SSD. "
                "После ремонта диска start_neuro.bat автоматически запустит все сервисы вместе."
            )
        elif language == "uk":
            message = (
                "Музичний рушій ACE-Step не запущено. Запустіть start_neuro.bat. "
                "Якщо Neuro увімкнув безпечний режим, спочатку перевірте системний SSD. "
                "Після ремонту диска start_neuro.bat автоматично запустить усі сервіси разом."
            )
        else:
            message = (
                "ACE-Step music worker is offline. Start start_neuro.bat. "
                "If Neuro started in safe mode, check the system SSD before enabling heavy AI workers. "
                "After the SSD is repaired, start_neuro.bat will automatically start every service together."
            )
        return RuntimeError(f"{message} Worker URL: {self.worker_url}. Details: {exc}")

    def _local_worker_port(self) -> Optional[int]:
        parsed = urlparse(self.worker_url)
        if parsed.hostname not in {"127.0.0.1", "localhost", "::1"}:
            return None
        return parsed.port

    async def _try_start_local_worker(self) -> bool:
        port = self._local_worker_port()
        launcher = self.root.parent / "run_acestep_music_worker.bat"
        if os.name != "nt" or port is None or not launcher.exists():
            return False

        creationflags = getattr(subprocess, "CREATE_NO_WINDOW", 0) | getattr(subprocess, "DETACHED_PROCESS", 0)
        subprocess.Popen(
            ["cmd.exe", "/c", str(launcher)],
            cwd=str(launcher.parent),
            stdin=subprocess.DEVNULL,
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL,
            creationflags=creationflags,
        )
        for _ in range(90):
            await asyncio.sleep(0.5)
            try:
                async with httpx.AsyncClient(timeout=2.0) as client:
                    response = await client.get(f"{self.worker_url}/health")
                if response.status_code < 400:
                    return True
            except httpx.RequestError:
                continue
        return False

    async def _release_local_worker_memory(self) -> None:
        if os.getenv("NEURO_MUSIC_KEEP_WORKER", "").strip().lower() in {"1", "true", "yes", "on"}:
            return
        port = self._local_worker_port()
        stop_script = self.root / "stop_local_port_process.ps1"
        if os.name != "nt" or port is None or not stop_script.exists():
            return

        def stop_worker() -> None:
            subprocess.run(
                [
                    "powershell.exe",
                    "-NoProfile",
                    "-ExecutionPolicy",
                    "Bypass",
                    "-File",
                    str(stop_script),
                    "-Port",
                    str(port),
                ],
                check=False,
                stdin=subprocess.DEVNULL,
                stdout=subprocess.DEVNULL,
                stderr=subprocess.DEVNULL,
                timeout=12,
            )

        await asyncio.to_thread(stop_worker)

    async def _ensure_worker_available(self, response_language: str = "en") -> None:
        try:
            async with httpx.AsyncClient(timeout=4.0) as client:
                response = await client.get(f"{self.worker_url}/health")
        except httpx.RequestError as exc:
            if await self._try_start_local_worker():
                return
            raise self._worker_offline_error(exc, response_language) from exc
        if response.status_code >= 400:
            raise RuntimeError(f"ACE-Step health check failed with HTTP {response.status_code}: {response.text[:500]}")

    async def _worker_post(self, path: str, payload: Dict[str, Any]) -> Dict[str, Any]:
        try:
            async with httpx.AsyncClient(timeout=None) as client:
                response = await client.post(f"{self.worker_url}{path}", json=payload)
        except httpx.RequestError as exc:
            raise self._worker_offline_error(exc) from exc
        return self._parse_worker_response(response)

    def _parse_worker_response(self, response: httpx.Response) -> Dict[str, Any]:
        if response.status_code >= 400:
            raise RuntimeError(f"ACE-Step HTTP {response.status_code}: {response.text[:800]}")
        data = response.json()
        if data.get("code") not in (None, 200):
            raise RuntimeError(str(data.get("error") or data))
        return data

    async def _release_task(self, payload: Dict[str, Any]) -> Dict[str, Any]:
        source_value = str(payload.get("src_audio_path") or "").strip()
        if not source_value:
            return await self._worker_post("/release_task", payload)

        source_path = Path(source_value).resolve()
        if not source_path.is_file() or source_path.parent != self.upload_dir.resolve():
            raise RuntimeError("Uploaded cover source audio is unavailable")

        form_payload: Dict[str, str] = {}
        for key, value in payload.items():
            if key == "src_audio_path" or value is None:
                continue
            if isinstance(value, bool):
                form_payload[key] = "true" if value else "false"
            else:
                form_payload[key] = str(value)

        content_type = mimetypes.guess_type(source_path.name)[0] or "application/octet-stream"
        try:
            async with httpx.AsyncClient(timeout=None) as client:
                with source_path.open("rb") as source_file:
                    response = await client.post(
                        f"{self.worker_url}/release_task",
                        data=form_payload,
                        files={"src_audio": (source_path.name, source_file, content_type)},
                    )
        except httpx.RequestError as exc:
            raise self._worker_offline_error(exc) from exc
        return self._parse_worker_response(response)

    async def _read_worker_audio(self, worker_file: str) -> tuple[bytes, str]:
        if not worker_file.startswith("/"):
            raise RuntimeError("ACE-Step returned an invalid audio URL")
        async with httpx.AsyncClient(timeout=None) as client:
            response = await client.get(f"{self.worker_url}{worker_file}")
        if response.status_code >= 400:
            raise RuntimeError(f"Cannot read ACE-Step audio: {response.text[:500]}")
        return response.content, response.headers.get("content-type", "audio/mpeg")

    async def _store_track(
        self,
        job_id: str,
        index: int,
        track: Dict[str, Any],
        payload: Dict[str, Any],
        title: str,
        lyrics: str,
        duration: float,
    ) -> Dict[str, Any]:
        worker_file = str(track.get("file") or "")
        audio, content_type = await self._read_worker_audio(worker_file)
        track_id = f"{job_id}-{index}"
        file_name = f"{track_id}.mp3"
        (self.generated_dir / file_name).write_bytes(audio)
        final_lyrics = str(track.get("lyrics") or lyrics)
        metas = track.get("metas") or {}
        actual_duration = float(metas.get("duration") or duration)
        saved = {
            "id": track_id,
            "job_id": job_id,
            "index": index,
            "title": title,
            "caption": track.get("prompt") or payload["prompt"],
            "lyrics": final_lyrics,
            "lyrics_timeline": build_lyrics_timeline(final_lyrics, actual_duration),
            "vocal_language": str(payload.get("vocal_language") or ""),
            "duration": actual_duration,
            "bpm": optional_int(metas.get("bpm") or payload.get("bpm")),
            "keyscale": metas.get("key_scale") or payload.get("key_scale") or "",
            "timesignature": metas.get("time_signature") or payload.get("time_signature") or "",
            "task_type": payload.get("task_type") or "text2music",
            "audio_url": f"{self.public_server_url}/music/library/{track_id}/audio",
            "audio_path": str(self.generated_dir / file_name),
            "content_type": content_type,
            "cover_url": None,
            "created_at": int(time.time()),
            "prompt": track.get("prompt") or payload["prompt"],
            "metas": metas,
            "seed": track.get("seed_value"),
            "lm_model": track.get("lm_model"),
            "dit_model": track.get("dit_model"),
        }
        self.library[track_id] = saved
        self._save_library()
        return deepcopy(saved)

    async def _align_track_timeline(self, track: Dict[str, Any]) -> Dict[str, Any]:
        if self.timeline_aligner is None or not track.get("audio_path"):
            return track
        try:
            track["lyrics_timeline"] = await self.timeline_aligner(
                str(track["audio_path"]),
                str(track.get("lyrics") or ""),
                float(track.get("duration") or 0.0),
                str(track.get("vocal_language") or ""),
            )
        except Exception as exc:
            print(f"[music-lyrics] Whisper alignment skipped: {exc}")
        self.library[str(track["id"])] = track
        self._save_library()
        return deepcopy(track)

    async def _create_cover(self, track: Dict[str, Any]) -> Dict[str, Any]:
        if self.cover_generator is None:
            return track
        try:
            track["cover_url"] = await self.cover_generator(track)
        except Exception as exc:
            print(f"[music-cover] artwork generation skipped: {exc}")
            track["cover_error"] = str(exc)
        self.library[str(track["id"])] = track
        self._save_library()
        return deepcopy(track)

    async def run_job(self, job_id: str) -> None:
        try:
            current = self.jobs[job_id]
            await self.update(job_id, status="checking_worker", stage="checking_worker", progress=0.02, started_at=int(time.time()))
            await self._ensure_worker_available(str(current["response_language"]))
            await self.update(job_id, status="planning", stage="planning", progress=0.04)
            if current.get("caption") and current.get("lyrics"):
                plan = {
                    "title": "Новый трек",
                    "task_type": current["task_type"],
                    "caption": current["caption"],
                    "lyrics": "[Instrumental]" if current["instrumental"] else current["lyrics"],
                    "vocal_language": current["vocal_language"] or _language_for_lyrics(current["response_language"]),
                    "duration": current["duration"] or estimate_music_duration(
                        str(current["user_prompt"]),
                        str(current["lyrics"]),
                        bool(current["instrumental"]),
                    ),
                    "bpm": current["bpm"],
                    "keyscale": current["keyscale"],
                    "timesignature": current["timesignature"],
                    "instrumental": current["instrumental"],
                }
            elif self.planner is not None:
                plan = await self.planner(
                    str(current["user_prompt"]),
                    str(current["response_language"]),
                    str(current["task_type"]),
                    bool(current["instrumental"]),
                )
            else:
                plan = fallback_music_plan(
                    str(current["user_prompt"]),
                    str(current["response_language"]),
                    str(current["task_type"]),
                    bool(current["instrumental"]),
                )

            duration = _clamp_music_duration(
                float(
                    current.get("duration")
                    or plan.get("duration")
                    or estimate_music_duration(
                        str(current["user_prompt"]),
                        str(current.get("lyrics") or plan.get("lyrics") or ""),
                        bool(current["instrumental"]),
                    )
                )
            )
            title = str(plan.get("title") or "Новый трек").strip()[:120]
            lyrics = "[Instrumental]" if current.get("instrumental") else str(current.get("lyrics") or plan.get("lyrics") or "")
            payload: Dict[str, Any] = {
                "task_type": current["task_type"],
                "prompt": str(current.get("caption") or plan.get("caption") or ""),
                "lyrics": lyrics,
                "vocal_language": str(current.get("vocal_language") or plan.get("vocal_language") or ""),
                "audio_duration": duration,
                "bpm": current.get("bpm") or plan.get("bpm"),
                "key_scale": str(current.get("keyscale") or plan.get("keyscale") or ""),
                "time_signature": str(current.get("timesignature") or plan.get("timesignature") or ""),
                # Neuro already prepared the caption, lyrics, language, and metadata.
                # Re-planning inside ACE-Step wastes VRAM needed by Pascal GPUs.
                "thinking": False,
                "use_format": False,
                "use_cot_caption": False,
                "use_cot_language": False,
                "inference_steps": int(current["inference_steps"]),
                "batch_size": int(current["batch_size"]),
                "audio_format": "mp3",
            }
            if current["task_type"] == "cover":
                payload["src_audio_path"] = str(current["source_audio_path"])
                payload["audio_cover_strength"] = float(current["audio_cover_strength"])

            print(f"[music-job] prepared ACE-Step payload job={job_id}: {json.dumps(payload, ensure_ascii=False)}")
            await self.update(
                job_id,
                status="submitting",
                stage="submitting",
                progress=0.12,
                caption=payload["prompt"],
                lyrics=lyrics,
                vocal_language=payload["vocal_language"],
                duration=duration,
                bpm=payload["bpm"],
                keyscale=payload["key_scale"],
                timesignature=payload["time_signature"],
                lyrics_timeline=build_lyrics_timeline(lyrics, duration),
            )
            if self.before_synthesis is not None:
                await self.before_synthesis()
            released = await self._release_task(payload)
            worker_task_id = str((released.get("data") or {}).get("task_id") or "")
            if not worker_task_id:
                raise RuntimeError(f"ACE-Step did not return task_id: {released}")
            await self.update(job_id, status="generating", stage="generating", progress=0.18, worker_task_id=worker_task_id)

            poll_started = time.monotonic()
            while True:
                result = await self._worker_post("/query_result", {"task_id_list": [worker_task_id]})
                rows = result.get("data") or []
                row = rows[0] if rows else {}
                status = int(row.get("status", 0))
                if status == 0:
                    elapsed = time.monotonic() - poll_started
                    await self.update(job_id, progress=min(0.92, 0.18 + elapsed / 180.0))
                    await asyncio.sleep(2.0)
                    continue
                if status == 2:
                    raise RuntimeError(worker_failure_message(row, str(current["response_language"])))

                raw_tracks = row.get("result") or "[]"
                tracks = json.loads(raw_tracks) if isinstance(raw_tracks, str) else raw_tracks
                if not isinstance(tracks, list) or not tracks:
                    raise RuntimeError("ACE-Step returned no generated tracks")
                prepared_tracks = []
                for index, track in enumerate(tracks):
                    worker_file = str(track.get("file") or "")
                    saved = await self._store_track(
                        job_id=job_id,
                        index=index,
                        track={**track, "file": worker_file},
                        payload=payload,
                        title=title,
                        lyrics=lyrics,
                        duration=duration,
                    )
                    prepared_tracks.append(saved)
                await self._release_local_worker_memory()
                await self.update(job_id, status="aligning_lyrics", stage="aligning_lyrics", progress=0.94, tracks=prepared_tracks)
                prepared_tracks = [await self._align_track_timeline(track) for track in prepared_tracks]
                await self.update(job_id, status="creating_cover", stage="creating_cover", progress=0.96, tracks=prepared_tracks)
                prepared_tracks = [await self._create_cover(track) for track in prepared_tracks]
                final_lyrics = str(prepared_tracks[0].get("lyrics") or lyrics)
                final_timeline = prepared_tracks[0].get("lyrics_timeline") or build_lyrics_timeline(final_lyrics, duration)
                await self.update(
                    job_id,
                    status="completed",
                    stage="completed",
                    progress=1.0,
                    tracks=prepared_tracks,
                    title=title,
                    lyrics=final_lyrics,
                    lyrics_timeline=final_timeline,
                    completed_at=int(time.time()),
                )
                return
        except Exception as exc:
            await self.update(
                job_id,
                status="failed",
                stage="failed",
                error=str(exc),
                completed_at=int(time.time()),
            )

    async def health(self) -> Dict[str, Any]:
        try:
            async with httpx.AsyncClient(timeout=4.0) as client:
                response = await client.get(f"{self.worker_url}/health")
            return {
                "status": "ok" if response.status_code < 400 else "error",
                "worker_url": self.worker_url,
                "worker_status": response.status_code,
                "detail": response.json() if response.headers.get("content-type", "").startswith("application/json") else response.text[:500],
            }
        except Exception as exc:
            return {
                "status": "offline",
                "worker_url": self.worker_url,
                "detail": str(self._worker_offline_error(exc)),
            }

    async def audio_bytes(self, job_id: str, index: int) -> tuple[bytes, str, str]:
        job = self.jobs.get(job_id)
        if not job or job.get("status") != "completed":
            raise HTTPException(status_code=404, detail="Generated music is not ready")
        tracks = job.get("tracks") or []
        if index < 0 or index >= len(tracks):
            raise HTTPException(status_code=404, detail="Track not found")
        local_path = Path(str(tracks[index].get("audio_path") or ""))
        if not local_path.exists() or local_path.parent.resolve() != self.generated_dir.resolve():
            raise HTTPException(status_code=404, detail="Generated audio file not found")
        content_type = str(tracks[index].get("content_type") or "audio/mpeg")
        filename = f"neuro_music_{job_id}_{index}.mp3"
        return local_path.read_bytes(), content_type, filename

    async def library_audio_bytes(self, track_id: str) -> tuple[bytes, str, str]:
        track = self.library_track(track_id)
        local_path = Path(str(track.get("audio_path") or ""))
        if not local_path.exists() or local_path.parent.resolve() != self.generated_dir.resolve():
            raise HTTPException(status_code=404, detail="Generated audio file not found")
        return (
            local_path.read_bytes(),
            str(track.get("content_type") or "audio/mpeg"),
            f"neuro_music_{track_id}.mp3",
        )
