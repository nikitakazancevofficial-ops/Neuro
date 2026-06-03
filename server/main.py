import asyncio
import base64
import codecs
import hashlib
import hmac
import json
import ipaddress
import mimetypes
import os
import re
import secrets
import socket
import subprocess
import sys
import tempfile
import time
import uuid
from contextlib import asynccontextmanager
from html import escape
from pathlib import Path
from typing import Any, Dict, List, Optional
from urllib.parse import urlparse

import httpx
import uvicorn
from fastapi import FastAPI, File, HTTPException, UploadFile
from fastapi.responses import HTMLResponse, Response, StreamingResponse
from fastapi.staticfiles import StaticFiles
from pydantic import BaseModel, Field

from music_service import (
    MUSIC_WORKER_URL,
    SUPPORTED_AUDIO_EXTENSIONS,
    MusicGenerationRequest,
    MusicService,
    align_lyrics_timeline,
    current_lyrics_line,
    is_music_generation_request,
    parse_music_plan,
)


for output_stream in (sys.stdout, sys.stderr):
    try:
        output_stream.reconfigure(encoding="utf-8", errors="backslashreplace")
    except (AttributeError, ValueError):
        pass


LLM_BASE_URL = os.getenv("LLM_BASE_URL", "http://127.0.0.1:1234/v1").rstrip("/")
LM_STUDIO_NATIVE_URL = os.getenv(
    "LM_STUDIO_NATIVE_URL",
    re.sub(r"/v1(?:/.*)?$", "", LLM_BASE_URL),
).rstrip("/")
LLM_MODEL = os.getenv("LLM_MODEL", "nvidia/nemotron-3-nano-omni")
MODEL_NEMOTRON_EXTENDED = "nvidia/nemotron-3-nano-omni"
MODEL_QWEN_STANDARD = "qwen/qwen3.5-9b"
MODEL_GLM_STANDARD = "zai-org/glm-4.6v-flash"
MODEL_GEMMA_STANDARD = "google/gemma-4-e4b"
MODEL_GEMMA_INSTANT = "google/gemma-3-12b"
MODEL_OMNICODER_9B = "omnicoder-9b"
ALLOWED_LLM_MODELS = {
    MODEL_NEMOTRON_EXTENDED,
    MODEL_QWEN_STANDARD,
    MODEL_GLM_STANDARD,
    MODEL_GEMMA_STANDARD,
    MODEL_GEMMA_INSTANT,
    MODEL_OMNICODER_9B,
}
CONTEXT_TOKENS = int(os.getenv("CONTEXT_TOKENS", "262144"))
SERVER_HOST = os.getenv("SERVER_HOST", "0.0.0.0")
SERVER_PORT = int(os.getenv("SERVER_PORT", "3510"))
MAX_RESPONSE_TOKENS = int(os.getenv("MAX_RESPONSE_TOKENS", "32768"))
WHISPER_MODEL = os.getenv(
    "WHISPER_MODEL",
    str(Path(__file__).resolve().parent / "models" / "faster-whisper-large-v3-turbo"),
)
IMAGE_WORKER_URL = os.getenv("IMAGE_WORKER_URL", "http://127.0.0.1:3511").rstrip("/")
PUBLIC_SERVER_URL = os.getenv("PUBLIC_SERVER_URL", f"http://127.0.0.1:{SERVER_PORT}").rstrip("/")
UNLOAD_LLM_BEFORE_HEAVY_WORKER = os.getenv(
    "UNLOAD_LLM_BEFORE_HEAVY_WORKER",
    os.getenv("UNLOAD_LLM_BEFORE_FLUX", "1"),
) == "1"
MAX_IMAGE_REVIEW_ATTEMPTS = int(os.getenv("MAX_IMAGE_REVIEW_ATTEMPTS", "3"))
STREAM_PIPELINE_VERSION = "real-sse-v3"
PASSWORD_HASH_SCHEME = "pbkdf2_sha256"
PASSWORD_HASH_ITERATIONS = 600_000


def detected_lan_ipv4_addresses() -> List[str]:
    addresses: List[str] = []

    try:
        addresses.extend(socket.gethostbyname_ex(socket.gethostname())[2])
    except OSError:
        pass

    try:
        with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as udp_socket:
            udp_socket.connect(("8.8.8.8", 80))
            addresses.append(udp_socket.getsockname()[0])
    except OSError:
        pass

    result: List[str] = []
    for address in addresses:
        try:
            parsed = ipaddress.ip_address(address)
        except ValueError:
            continue
        if parsed.version != 4 or parsed.is_loopback or parsed.is_link_local:
            continue
        if address not in result:
            result.append(address)
    return result


def phone_setup_urls() -> List[str]:
    urls: List[str] = []
    if "127.0.0.1" not in PUBLIC_SERVER_URL and "localhost" not in PUBLIC_SERVER_URL:
        urls.append(PUBLIC_SERVER_URL)
    for address in detected_lan_ipv4_addresses():
        candidate = f"http://{address}:{SERVER_PORT}"
        if candidate not in urls:
            urls.append(candidate)
    return urls


SYSTEM_PROMPT = (
    "Ты Neuro, русскоязычный AI-помощник. Всегда отвечай только на русском языке, "
    "ясно, полезно и по делу. Учитывай всю историю текущего чата и не теряй контекст "
    "предыдущих сообщений. Если пишешь код, оформляй его в markdown-блоках с тройными "
    "кавычками и названием языка. Не переходи на английский язык, даже если рассуждения "
    "или служебные подсказки пришли на английском."
)
SYSTEM_PROMPT += (
    "\n\nВАЖНО: у тебя есть встроенный локальный генератор изображений FLUX.2 Klein. "
    "Ты умеешь создавать изображения прямо в этом чате: сервер сам перехватывает запрос, "
    "готовит улучшенный английский промпт и запускает генерацию. Никогда не говори пользователю, "
    "что ты не умеешь рисовать, не предлагай сторонние сервисы и не проси вставлять промпт вручную. "
    "Если пользователь просит изменить уже созданное изображение, воспринимай это как запрос на новую "
    "генерацию с сохранением прошлого сюжета и применением новых пожеланий."
)
SYSTEM_PROMPT += (
    "\n\nВАЖНО: у тебя также есть встроенный локальный генератор музыки ACE-Step 1.5. "
    "Ты умеешь создавать полноценные песни и инструментальные треки прямо в чате, а также "
    "готовить cover-версии из загруженного аудио. Когда пользователь просит сгенерировать музыку, "
    "сервер сам составляет подробное англоязычное описание стиля, структурированный текст песни "
    "и метаданные, затем запускает локальную модель. Не говори, что не умеешь создавать музыку."
)
IMAGE_PROMPT_SUFFIX = (
    "Ответь только на русском языке. Пользователь прикрепил изображение: внимательно "
    "проанализируй именно картинку и опиши, что на ней видно. Не отвечай общими словами."
)
THINKING_BLOCK_RE = re.compile(
    r"<\s*(think|thinking|reasoning|analysis|thought|reflection)\s*>[\s\S]*?(?:<\s*/\s*\1\s*>|$)",
    re.IGNORECASE,
)
DATA_FILE = Path(__file__).with_name("storage.json")
UPLOAD_DIR = Path(__file__).parent / "uploads"
UPLOAD_DIR.mkdir(exist_ok=True)
GENERATED_DIR = Path(__file__).parent / "generated"
GENERATED_DIR.mkdir(exist_ok=True)
MUSIC_PLANNER_MODEL = os.getenv("MUSIC_PLANNER_MODEL", MODEL_QWEN_STANDARD)
MUSIC_PLANNER_TIMEOUT_SECONDS = max(15.0, float(os.getenv("MUSIC_PLANNER_TIMEOUT_SECONDS", "90")))
IMAGE_REQUEST_RE = re.compile(
    r"(?:сгенерир|создай|создать|нарисуй|нарисовать|сделай|сделать|generate|create|draw|make)"
    r"[\s\S]{0,90}(?:фото|изображен|картин|арт|image|photo|picture)"
    r"|(?:фото|изображен|картин|арт|image|photo|picture)"
    r"[\s\S]{0,90}(?:сгенерир|создай|создать|нарисуй|нарисовать|сделай|сделать|generate|create|draw|make)",
    re.IGNORECASE,
)
IMAGE_GENERATION_ACTION_RE = re.compile(
    r"(?:[а-яёa-z]?генерир|нарис|изобраз|отрендер|generate|draw|render)",
    re.IGNORECASE,
)
NON_IMAGE_GENERATION_TARGET_RE = re.compile(
    r"(?:код|скрипт|программ|текст|ответ|список|план|таблиц|json|sql|парол|"
    r"описан|промпт|резюме|письм|стать|code|script|text|answer|list|plan|table|password)",
    re.IGNORECASE,
)
IMAGE_VISUAL_DESIRE_RE = re.compile(
    r"(?:хоч(?:у|ется|ел(?:а|и)?|ешь)|жел(?:аю|аешь)|можно|давай|покажи|увидеть|"
    r"посмотреть|создай|создать|сделай|сделать|нарисуй|нарисовать|"
    r"сгенерируй|сгенерировать|мож(?:ешь|ете)\s+(?:сделать|создать|нарисовать|"
    r"сгенерировать|показать)|generate|create|draw|show)"
    r"[\s\S]{0,120}(?:фото|изображен|картин|арт|портрет|обложк|кот|кош|собак|"
    r"персонаж|пейзаж|интерьер|image|photo|picture|portrait|cat|dog)",
    re.IGNORECASE,
)
IMAGE_REFINEMENT_RE = re.compile(
    r"(?:сделай|давай|пусть|можно|добавь|добавить|убери|убрать|измени|изменить|"
    r"поменяй|повтори|ещ[её]|более|менее|максимальн|реалист|фотореалист|"
    r"мультяш|стиль|фон|свет|цвет|формат|ракурс|generate|create|draw|realistic|"
    r"photoreal|cartoon|style|"
    r"(?:а\s+если|хоч(?:у|ется|ел(?:а|и)?|ешь))[\s\S]{0,100}"
    r"(?:кот|кош|собак|персонаж|фото|изображен|картин|арт|портрет|фон|свет|"
    r"цвет|ракурс|реалист|мультяш|стиль|cat|dog|image|photo|picture|portrait))",
    re.IGNORECASE,
)
CASUAL_CHAT_RE = re.compile(
    r"^\s*(?:привет(?:ик)?|здравствуй(?:те)?|доброе\s+(?:утро|день|вечер)|"
    r"как\s+дела|спасибо|благодарю|пока|до\s+свидания|hello|hi|hey|thanks|bye)"
    r"[\s!,.?]*$",
    re.IGNORECASE,
)
MANDATORY_PROFILE_FACTS: List[str] = []
IMAGE_ASPECT_DIMENSIONS = {
    "1:1": (768, 768),
    "4:5": (768, 960),
    "3:2": (960, 640),
    "16:9": (1024, 576),
    "9:16": (576, 1024),
}
IMAGE_PROMPT_MAX_CHARS = 24000

@asynccontextmanager
async def lifespan(_: FastAPI):
    for job_id, job in list(state.setdefault("image_jobs", {}).items()):
        if isinstance(job, dict) and job.get("status") in {"planning", "generating", "reviewing"}:
            schedule_image_job(str(job_id))
    yield


app = FastAPI(title="Neuro Local Server", lifespan=lifespan)
state_lock = asyncio.Lock()
image_worker_request_lock = asyncio.Lock()

# Монтируем статику для загруженных файлов
app.mount("/uploads", StaticFiles(directory=str(UPLOAD_DIR)), name="uploads")
app.mount("/generated", StaticFiles(directory=str(GENERATED_DIR)), name="generated")
music_service = MusicService(Path(__file__).parent, PUBLIC_SERVER_URL, MUSIC_WORKER_URL)
app.mount("/music/uploads", StaticFiles(directory=str(music_service.upload_dir)), name="music_uploads")


class LoginRequest(BaseModel):
    email: str
    password: str


class RegisterRequest(BaseModel):
    email: str
    password: str


class AuthResponse(BaseModel):
    access_token: Optional[str] = None
    error: Optional[str] = None


class SimpleResponse(BaseModel):
    message: Optional[str] = None
    error: Optional[str] = None


class ChatInfo(BaseModel):
    id: int
    title: str


class ChatRequest(BaseModel):
    message: str
    chat_id: Optional[int] = None
    images: List[str] = Field(default_factory=list)
    client_context: Dict[str, Any] = Field(default_factory=dict)
    model: Optional[str] = None
    reasoning_mode: Optional[str] = None
    thinking_effort: Optional[str] = None
    response_language: str = "ru"
    regenerate: bool = False
    request_id: Optional[str] = None


class ChatResponse(BaseModel):
    reply: Optional[str] = None
    chat_id: Optional[int] = None
    memory_updated: Optional[str] = None
    error: Optional[str] = None


class ServerMessage(BaseModel):
    role: str
    content: str
    images: List[str] = Field(default_factory=list)
    image_generation: Optional[Dict[str, Any]] = None
    music_generation: Optional[Dict[str, Any]] = None


class UploadResponse(BaseModel):
    url: str


class TranscriptionResponse(BaseModel):
    text: str
    language: str = "ru"


class ImageGenerationRequest(BaseModel):
    prompt: str = Field(min_length=2, max_length=24000)
    width: int = Field(default=768, ge=256, le=1024)
    height: int = Field(default=768, ge=256, le=1024)
    steps: int = Field(default=4, ge=1, le=20)
    guidance_scale: float = Field(default=1.0, ge=0.0, le=10.0)
    seed: Optional[int] = None
    reference_image_path: Optional[str] = None
    reference_image_paths: List[str] = Field(default_factory=list)


class ImageGenerationResponse(BaseModel):
    url: str
    seed: int
    width: int
    height: int
    steps: int
    elapsed_seconds: float
    reference_used: bool = False
    reference_count: int = 0


class ImageJobResponse(BaseModel):
    id: str
    chat_id: int
    status: str
    user_prompt: str
    prompt: str = ""
    aspect_ratio: str = "1:1"
    width: int = 768
    height: int = 768
    steps: int = 4
    guidance_scale: float = 1.0
    seed: Optional[int] = None
    url: Optional[str] = None
    error: Optional[str] = None
    created_at: int
    started_at: Optional[int] = None
    completed_at: Optional[int] = None
    elapsed_seconds: Optional[float] = None
    source_job_id: Optional[str] = None
    reference_url: Optional[str] = None
    reply: Optional[str] = None
    attempt: int = 0
    max_attempts: int = 1
    review_satisfied: Optional[bool] = None
    review_feedback: Optional[str] = None
    reference_used: bool = False
    reference_count: int = 0
    original_reference_url: Optional[str] = None


class ChatSearchResult(BaseModel):
    id: int
    title: str
    snippet: str


class PersonalizationSettings(BaseModel):
    base_style: str = "Дружелюбный"
    warmth: str = "По умолчанию"
    enthusiasm: str = "По умолчанию"
    headings_and_lists: str = "По умолчанию"
    emoji: str = "По умолчанию"
    fast_answers: bool = True
    custom_instructions: str = ""


class MemorySettings(BaseModel):
    reference_chat_history: bool = True
    use_saved_memory: bool = True
    nickname: str = ""
    profession: str = ""
    about: str = ""


class SavedMemory(BaseModel):
    id: str
    text: str
    created_at: int


def default_state() -> Dict[str, Any]:
    return {
        "users": {},
        "chats": {},
        "messages": {},
        "next_chat_id": 1,
        "personalization": PersonalizationSettings().model_dump(),
        "memory_settings": MemorySettings().model_dump(),
        "memories": [],
        "profile_facts": MANDATORY_PROFILE_FACTS.copy(),
        "image_jobs": {},
    }


CP1251_MOJIBAKE_RE = re.compile(r"(?:Р.|С.){2,}")
CJK_RE = re.compile(r"[\u3400-\u9fff]")


def mojibake_score(value: str) -> int:
    return (
        len(CP1251_MOJIBAKE_RE.findall(value)) * 8
        + len(CJK_RE.findall(value)) * 3
        + value.count("\ufffd") * 12
        + sum(value.count(marker) * 4 for marker in ("Ð", "Ñ", "вЂ", "Р ", "РЎ"))
    )


def cp1251_bytes_lossless(value: str) -> bytes:
    output = bytearray()
    for char in value:
        try:
            encoded = char.encode("cp1251")
        except UnicodeEncodeError:
            if ord(char) > 255:
                raise
            encoded = bytes([ord(char)])
        output.extend(encoded)
    return bytes(output)


def repair_mojibake_text(value: Any) -> Any:
    if not isinstance(value, str):
        return value
    repaired = value
    for _ in range(4):
        candidates = [repaired]
        try:
            candidates.append(cp1251_bytes_lossless(repaired).decode("utf-8"))
        except (UnicodeEncodeError, UnicodeDecodeError):
            pass
        for encoding in ("cp1251", "latin1", "gb18030"):
            try:
                candidates.append(repaired.encode(encoding).decode("utf-8"))
            except (UnicodeEncodeError, UnicodeDecodeError):
                continue
        best = min(candidates, key=mojibake_score)
        if mojibake_score(best) >= mojibake_score(repaired):
            break
        repaired = best
    return repaired


def repair_mojibake_tree(value: Any) -> Any:
    if isinstance(value, dict):
        return {key: repair_mojibake_tree(item) for key, item in value.items()}
    if isinstance(value, list):
        return [repair_mojibake_tree(item) for item in value]
    return repair_mojibake_text(value)


def load_state() -> Dict[str, Any]:
    if not DATA_FILE.exists():
        return default_state()
    try:
        data = json.loads(DATA_FILE.read_text(encoding="utf-8"))
        base = default_state()
        base.update(data)
        base["personalization"] = {
            **PersonalizationSettings().model_dump(),
            **(data.get("personalization") or {}),
        }
        base["memory_settings"] = {
            **MemorySettings().model_dump(),
            **(data.get("memory_settings") or {}),
        }
        base["memories"] = data.get("memories") or []
        base["profile_facts"] = data.get("profile_facts") or MANDATORY_PROFILE_FACTS.copy()
        repaired_base = repair_mojibake_tree(base)
        storage_changed = repaired_base != base or not bool(data.get("profile_facts"))
        base = repaired_base
        for messages in base.get("messages", {}).values():
            if isinstance(messages, list):
                compact_messages = []
                for message in messages:
                    if isinstance(message, dict):
                        message.setdefault("images", [])
                        duplicate_user_turn = (
                            compact_messages
                            and message.get("role") == "user"
                            and compact_messages[-1].get("role") == "user"
                            and compact_messages[-1].get("content") == message.get("content")
                            and (compact_messages[-1].get("images") or []) == (message.get("images") or [])
                        )
                        if duplicate_user_turn:
                            storage_changed = True
                            continue
                        compact_messages.append(message)
                messages[:] = compact_messages
        if storage_changed:
            DATA_FILE.write_text(
                json.dumps(base, ensure_ascii=False, indent=2),
                encoding="utf-8",
            )
        return base
    except Exception as exc:
        print(f"[storage] Cannot read {DATA_FILE}: {exc}")
        return default_state()


state = load_state()
whisper_lock = asyncio.Lock()
whisper_model: Optional[Any] = None
whisper_runtime: Optional[Dict[str, str]] = None
music_whisper_lock = asyncio.Lock()
music_whisper_model: Optional[Any] = None
image_job_tasks: Dict[str, asyncio.Task[Any]] = {}


def save_state() -> None:
    DATA_FILE.write_text(
        json.dumps(state, ensure_ascii=False, indent=2),
        encoding="utf-8",
    )


def hash_password(password: str) -> str:
    salt = secrets.token_bytes(16)
    digest = hashlib.pbkdf2_hmac(
        "sha256",
        password.encode("utf-8"),
        salt,
        PASSWORD_HASH_ITERATIONS,
    )
    return f"{PASSWORD_HASH_SCHEME}${PASSWORD_HASH_ITERATIONS}${salt.hex()}${digest.hex()}"


def verify_password(stored_password: str, candidate_password: str) -> bool:
    if not stored_password.startswith(f"{PASSWORD_HASH_SCHEME}$"):
        return hmac.compare_digest(stored_password, candidate_password)
    try:
        _, iterations, salt_hex, digest_hex = stored_password.split("$", 3)
        expected_digest = bytes.fromhex(digest_hex)
        candidate_digest = hashlib.pbkdf2_hmac(
            "sha256",
            candidate_password.encode("utf-8"),
            bytes.fromhex(salt_hex),
            int(iterations),
        )
    except (TypeError, ValueError):
        return False
    return hmac.compare_digest(expected_digest, candidate_digest)


def load_whisper_model_sync():
    from faster_whisper import WhisperModel

    attempts = [
        {"device": "cuda", "compute_type": "int8_float16"},
        {"device": "cuda", "compute_type": "int8_float32"},
        {"device": "cpu", "compute_type": "int8"},
    ]
    last_error = ""
    for attempt in attempts:
        try:
            print(
                "[whisper] loading "
                f"model={WHISPER_MODEL} device={attempt['device']} "
                f"compute_type={attempt['compute_type']}"
            )
            model = WhisperModel(
                WHISPER_MODEL,
                device=attempt["device"],
                compute_type=attempt["compute_type"],
            )
            print(
                "[whisper] ready "
                f"model={WHISPER_MODEL} device={attempt['device']} "
                f"compute_type={attempt['compute_type']}"
            )
            return model, attempt
        except Exception as exc:
            last_error = str(exc)
            print(
                "[whisper] load failed "
                f"device={attempt['device']} compute_type={attempt['compute_type']}: "
                f"{last_error[:500]}"
            )
    raise RuntimeError(f"Cannot load Whisper model {WHISPER_MODEL}. Last error: {last_error}")


async def get_whisper_model():
    global whisper_model, whisper_runtime
    if whisper_model is not None:
        return whisper_model

    async with whisper_lock:
        if whisper_model is None:
            whisper_model, whisper_runtime = await asyncio.to_thread(load_whisper_model_sync)
        return whisper_model


def transcribe_audio_sync(model: Any, audio_path: str) -> Dict[str, str]:
    segments, info = model.transcribe(
        audio_path,
        language="ru",
        task="transcribe",
        beam_size=5,
        vad_filter=True,
    )
    text = " ".join(segment.text.strip() for segment in segments if segment.text.strip())
    return {"text": " ".join(text.split()), "language": info.language or "ru"}


def load_music_whisper_model_sync():
    from faster_whisper import WhisperModel

    print(f"[music-whisper] loading model={WHISPER_MODEL} device=cpu compute_type=int8")
    model = WhisperModel(WHISPER_MODEL, device="cpu", compute_type="int8")
    print(f"[music-whisper] ready model={WHISPER_MODEL} device=cpu compute_type=int8")
    return model


async def get_music_whisper_model():
    global music_whisper_model
    if music_whisper_model is not None:
        return music_whisper_model
    async with music_whisper_lock:
        if music_whisper_model is None:
            music_whisper_model = await asyncio.to_thread(load_music_whisper_model_sync)
        return music_whisper_model


def transcribe_music_words_sync(model: Any, audio_path: str, language: str) -> List[Dict[str, Any]]:
    supported_language = str(language or "").strip().lower().split("-", 1)[0] or None
    segments, _ = model.transcribe(
        audio_path,
        language=supported_language,
        task="transcribe",
        beam_size=3,
        vad_filter=True,
        word_timestamps=True,
    )
    words: List[Dict[str, Any]] = []
    for segment in segments:
        for word in segment.words or []:
            words.append({"text": word.word, "start": word.start, "end": word.end})
    return words


async def align_music_lyrics(audio_path: str, lyrics: str, duration: float, language: str) -> List[Dict[str, Any]]:
    if not lyrics.strip() or lyrics.strip().casefold() == "[instrumental]":
        return []
    model = await get_music_whisper_model()
    words = await asyncio.to_thread(transcribe_music_words_sync, model, audio_path, language)
    return align_lyrics_timeline(lyrics, duration, words)


music_service.timeline_aligner = align_music_lyrics


def chat_key(chat_id: int) -> str:
    return str(chat_id)


async def ensure_chat(chat_id: Optional[int] = None) -> int:
    async with state_lock:
        chats = state["chats"]
        messages = state["messages"]
        key = chat_key(chat_id) if chat_id is not None else None
        if key is not None and key in chats:
            return int(chat_id)

        new_id = int(state.get("next_chat_id", 1))
        state["next_chat_id"] = new_id + 1
        chats[chat_key(new_id)] = f"Chat {new_id}"
        messages[chat_key(new_id)] = []
        save_state()
        return new_id


def make_title(text: str, chat_id: int) -> str:
    clean = " ".join((text or "").strip().split())
    if not clean:
        return f"Chat {chat_id}"
    return clean[:42] + ("..." if len(clean) > 42 else "")


async def append_message(chat_id: int, role: str, content: str, images: Optional[List[str]] = None) -> None:
    async with state_lock:
        key = chat_key(chat_id)
        state["messages"].setdefault(key, []).append(
            {"role": role, "content": content, "images": images or []}
        )
        if role == "user" and state["chats"].get(key, "").startswith("Chat "):
            state["chats"][key] = make_title(content, chat_id)
        save_state()


async def append_image_job_message(chat_id: int, job_id: str) -> None:
    async with state_lock:
        state["messages"].setdefault(chat_key(chat_id), []).append(
            {
                "role": "assistant",
                "content": "",
                "images": [],
                "image_job_id": job_id,
            }
        )
        save_state()


async def append_music_job_message(chat_id: int, job_id: str) -> None:
    async with state_lock:
        state["messages"].setdefault(chat_key(chat_id), []).append(
            {
                "role": "assistant",
                "content": "",
                "images": [],
                "music_job_id": job_id,
            }
        )
        save_state()


async def update_image_job_message(chat_id: int, job_id: str, content: str) -> None:
    async with state_lock:
        for message in state["messages"].setdefault(chat_key(chat_id), []):
            if message.get("image_job_id") == job_id:
                message["content"] = content
                break
        save_state()


async def prepare_user_turn(chat_id: int, request: ChatRequest) -> None:
    async with state_lock:
        key = chat_key(chat_id)
        messages = state["messages"].setdefault(key, [])
        if request.regenerate and messages and messages[-1].get("role") == "assistant":
            messages.pop()

        last_message = messages[-1] if messages else None
        same_request = (
            request.request_id
            and last_message is not None
            and last_message.get("request_id") == request.request_id
        )
        same_regenerated_turn = (
            request.regenerate
            and
            last_message is not None
            and last_message.get("role") == "user"
            and str(last_message.get("content", "")) == request.message
            and (last_message.get("images") or []) == request.images
        )
        if not same_request and not same_regenerated_turn:
            messages.append(
                {
                    "role": "user",
                    "content": request.message,
                    "images": request.images,
                    "request_id": request.request_id,
                }
            )
            if state["chats"].get(key, "").startswith("Chat "):
                state["chats"][key] = make_title(request.message, chat_id)
        save_state()


def sse_event(payload: Dict[str, Any]) -> str:
    return f"data: {json.dumps(payload, ensure_ascii=True)}\n\n"


def chat_completions_url() -> str:
    if LLM_BASE_URL.endswith("/chat/completions"):
        return LLM_BASE_URL
    return f"{LLM_BASE_URL}/chat/completions"


def models_url() -> str:
    if LLM_BASE_URL.endswith("/v1"):
        return f"{LLM_BASE_URL}/models"
    return f"{LLM_BASE_URL}/v1/models"


def extract_delta(choice: Dict[str, Any]) -> Dict[str, str]:
    delta = choice.get("delta") or {}
    message = choice.get("message") or {}
    source = delta if isinstance(delta, dict) and delta else message if isinstance(message, dict) else {}

    content = source.get("content") if isinstance(source.get("content"), str) else ""
    if not content and isinstance(choice.get("text"), str):
        content = choice.get("text") or ""
    reasoning = ""
    for key in ("reasoning_content", "reasoning", "reasoning_text", "thoughts"):
        value = source.get(key)
        if isinstance(value, str) and value:
            reasoning = value
            break
    return {"content": content, "reasoning": reasoning}


def asset_url_to_path(image: str) -> Optional[Path]:
    for marker, directory in (("/uploads/", UPLOAD_DIR), ("/generated/", GENERATED_DIR)):
        if marker not in image:
            continue
        file_name = image.split(marker, 1)[1].split("?", 1)[0].split("#", 1)[0]
        safe_name = Path(file_name).name
        path = directory / safe_name
        if path.exists() and path.is_file():
            return path
    return None


def upload_url_to_path(image: str) -> Optional[Path]:
    path = asset_url_to_path(image)
    return path if path is not None and path.parent == UPLOAD_DIR else None


def image_to_model_url(image: str) -> str:
    if image.startswith("data:image/"):
        return image

    local_path = asset_url_to_path(image)
    if local_path is None:
        return ""

    mime = mimetypes.guess_type(local_path.name)[0] or "image/jpeg"
    encoded = base64.b64encode(local_path.read_bytes()).decode("ascii")
    return f"data:{mime};base64,{encoded}"


def build_user_message_content(text: str, images: List[str]) -> Any:
    clean_text = text.strip() or "Опиши изображение."
    if not images:
        return clean_text

    content: List[Dict[str, Any]] = [
        {"type": "text", "text": f"{clean_text}\n\n{IMAGE_PROMPT_SUFFIX}"}
    ]
    for image in images:
        image_payload = image_to_model_url(image)
        if image_payload:
            content.append(
                {
                    "type": "image_url",
                    "image_url": {"url": image_payload, "detail": "high"},
                }
            )
    return content


def build_visual_service_content(text: str, images: List[str]) -> Any:
    content: List[Dict[str, Any]] = [{"type": "text", "text": text}]
    for image in images:
        image_payload = image_to_model_url(image)
        if image_payload:
            content.append(
                {
                    "type": "image_url",
                    "image_url": {"url": image_payload, "detail": "high"},
                }
            )
    return content if len(content) > 1 else text


def strip_thinking_blocks(content: str) -> str:
    return THINKING_BLOCK_RE.sub("", content).strip()


def message_for_llm(message: Dict[str, Any]) -> Dict[str, Any]:
    role = str(message.get("role", "user"))
    content = str(message.get("content", ""))
    images = message.get("images") or []
    if role == "user" and images:
        return {"role": role, "content": build_user_message_content(content, images)}
    if role == "assistant":
        visible_content = strip_thinking_blocks(content)
        if is_outdated_image_limitation(visible_content):
            visible_content = (
                "Я могу создавать изображения прямо в этом чате "
                "с помощью встроенного локального генератора."
            )
        return {"role": role, "content": visible_content}
    return {"role": role, "content": content}


def format_client_context(client_context: Dict[str, Any]) -> str:
    if not client_context:
        return ""

    parts = []
    date_time = client_context.get("date_time")
    timezone = client_context.get("timezone")
    locale = client_context.get("locale")
    latitude = client_context.get("latitude")
    longitude = client_context.get("longitude")
    accuracy = client_context.get("accuracy_meters")

    if date_time:
        parts.append(f"Дата и время пользователя: {date_time}")
    if timezone:
        parts.append(f"Часовой пояс: {timezone}")
    if locale:
        parts.append(f"Язык/локаль устройства: {locale}")
    if latitude is not None and longitude is not None:
        location = f"Координаты пользователя: {latitude}, {longitude}"
        if accuracy is not None:
            location += f" (точность около {accuracy} м)"
        parts.append(location)

    if not parts:
        return ""
    return "Контекст устройства пользователя:\n" + "\n".join(f"- {part}" for part in parts)


def personalization_prompt() -> str:
    settings = PersonalizationSettings(**state.get("personalization", {}))
    parts = [
        "Персонализация ответа пользователя:",
        f"- Базовый стиль и тон: {settings.base_style}",
        f"- Доброжелательность: {settings.warmth}",
        f"- Энтузиазм: {settings.enthusiasm}",
        f"- Заголовки и списки: {settings.headings_and_lists}",
        f"- Эмодзи: {settings.emoji}",
    ]
    if settings.fast_answers:
        parts.append("- Пользователь предпочитает быстрые, достаточно краткие ответы без лишнего ожидания.")
    if settings.custom_instructions.strip():
        parts.append(f"- Пользовательские инструкции: {settings.custom_instructions.strip()}")
    return "\n".join(parts)


def full_user_profile_prompt() -> str:
    settings = MemorySettings(**state.get("memory_settings", {}))
    parts = [
        "ОБЯЗАТЕЛЬНЫЙ ПОЛНЫЙ ПРОФИЛЬ ПОЛЬЗОВАТЕЛЯ. Он прикладывается к каждому запросу модели. "
        "Всегда учитывай эти факты естественно и не противоречь им:",
    ]
    seen = set()
    for fact in state.get("profile_facts", MANDATORY_PROFILE_FACTS):
        text = normalize_saved_memory_text(str(fact))
        key = text.casefold()
        if text and key not in seen:
            seen.add(key)
            parts.append(f"- {text}")
    if settings.nickname.strip():
        parts.append(f"- Предпочтительное обращение: {settings.nickname.strip()}")
    if settings.profession.strip():
        parts.append(f"- Профессия или роль: {settings.profession.strip()}")
    if settings.about.strip():
        parts.append(f"- Дополнительная информация: {settings.about.strip()}")
    for memory in state.get("memories", []):
        text = normalize_saved_memory_text(str(memory.get("text", "")))
        key = text.casefold()
        if text and key not in seen:
            seen.add(key)
            parts.append(f"- {text}")
    return "\n".join(parts)


def attach_full_user_profile(messages: List[Dict[str, Any]]) -> List[Dict[str, Any]]:
    if not messages or messages[0].get("role") != "system":
        return messages
    messages[0]["content"] = f"{messages[0].get('content', '')}\n\n{full_user_profile_prompt()}"
    return messages


def memory_prompt() -> str:
    settings = MemorySettings(**state.get("memory_settings", {}))
    parts = [
        full_user_profile_prompt(),
        "",
        "Долговременная память о пользователе. Это сохранённые факты из прошлых чатов, "
        "они действуют во всех новых чатах и запросах. Учитывай их естественно. "
        "Если пользователь спрашивает, что ты знаешь или помнишь о нём, обязательно "
        "перечисли эти факты и не отвечай, что ничего не знаешь:"
    ]
    return "\n".join(parts)


def normalize_saved_memory_text(value: str) -> str:
    text = " ".join(value.strip().split())
    text = re.sub(
        r"^Пользователь сообщил:\s*(?:то\s+)?что\s+",
        "",
        text,
        flags=re.IGNORECASE,
    )
    return text


def recent_chat_history_prompt(current_chat_id: int) -> str:
    settings = MemorySettings(**state.get("memory_settings", {}))
    if not settings.reference_chat_history:
        return ""

    recent = []
    for cid in sorted(state.get("messages", {}), key=lambda value: int(value), reverse=True):
        if cid == chat_key(current_chat_id):
            continue
        for message in state["messages"].get(cid, [])[-4:]:
            role = "Пользователь" if message.get("role") == "user" else "Neuro"
            text = strip_thinking_blocks(str(message.get("content", ""))).strip()
            if message.get("role") == "assistant" and is_outdated_image_limitation(text):
                continue
            if text:
                recent.append(f"- {role}: {preview_text(text, limit=220)}")
        if len(recent) >= 8:
            break
    if not recent:
        return ""
    return "Краткий контекст недавних чатов пользователя. Используй только если это уместно:\n" + "\n".join(recent[-8:])


def is_outdated_image_limitation(text: str) -> bool:
    clean = text.casefold()
    limitation_markers = (
        "не могу нарисовать",
        "не могу рисовать",
        "не могу напрямую генерировать изображения",
        "не могу генерировать изображения",
        "не умею генерировать",
        "dall-e",
        "midjourney",
        "stable diffusion",
    )
    return any(marker in clean for marker in limitation_markers)


def clean_memory_candidate(value: str) -> str:
    text = " ".join(value.strip(" .,!?:;\n\t").split())
    if not text:
        return ""
    text = re.sub(r"^(?:то\s+)?что\s+", "", text, flags=re.IGNORECASE)
    replacements = [
        (r"\bя люблю\b", "Пользователь любит"),
        (r"\bя обожаю\b", "Пользователь обожает"),
        (r"\bя предпочитаю\b", "Пользователь предпочитает"),
        (r"\bя программирую\b", "Пользователь программирует"),
        (r"\bя работаю\b", "Пользователь работает"),
        (r"\bя занимаюсь\b", "Пользователь занимается"),
        (r"\bя учусь\b", "Пользователь учится"),
        (r"\bя живу\b", "Пользователь живёт"),
        (r"\bмне нравится\b", "Пользователю нравится"),
        (r"\bобожаю\b", "обожает"),
        (r"\bлюблю\b", "любит"),
        (r"\bпредпочитаю\b", "предпочитает"),
    ]
    for pattern, replacement in replacements:
        text = re.sub(pattern, replacement, text, flags=re.IGNORECASE)
    if text.lower().startswith("я "):
        text = "Пользователь " + text[2:]
    elif not text.lower().startswith(("пользователь", "пользователю")):
        text = "Пользователь сообщил: " + text
    return text[:240].rstrip(" .") + "."


def extract_memory_candidate(message: str) -> str:
    clean = " ".join(message.strip().split())
    if not clean:
        return ""

    explicit = re.search(
        r"(?:запомни|запиши в память|сохрани в память|помни)(?:,?\s+пожалуйста)?\s+(.+)",
        clean,
        flags=re.IGNORECASE,
    )
    if explicit:
        return clean_memory_candidate(explicit.group(1))

    implicit = re.search(
        r"\b(я\s+(?:люблю|обожаю|предпочитаю|работаю|занимаюсь|программирую|учусь|живу|хочу,?\s+чтобы|мне\s+нравится).+)",
        clean,
        flags=re.IGNORECASE,
    )
    if implicit:
        return clean_memory_candidate(implicit.group(1))
    return ""


def should_consider_memory(message: str) -> bool:
    clean = " ".join(message.casefold().split())
    markers = (
        "запомни",
        "запиши в память",
        "сохрани в память",
        "помни",
        "у меня ",
        "мой ",
        "моя ",
        "моё ",
        "мои ",
        "меня зовут",
        "я люблю",
        "я обожаю",
        "мне нравится",
        "я предпочитаю",
        "я работаю",
        "я учусь",
        "я живу",
    )
    return any(marker in clean for marker in markers)


def parse_memory_model_answer(answer: str) -> str:
    visible = strip_thinking_blocks(answer).strip()
    if not visible:
        return ""
    match = re.search(r"\{[\s\S]*\}", visible)
    if not match:
        return ""
    try:
        payload = json.loads(match.group(0))
    except json.JSONDecodeError:
        return ""
    memory = payload.get("memory")
    if not isinstance(memory, str):
        return ""
    text = " ".join(memory.strip().split())
    if not text or text.casefold() in {"null", "none", "нет"}:
        return ""
    return text[:240].rstrip(" .") + "."


async def formulate_memory_with_llm(message: str, model: str) -> str:
    history = [
        {
            "role": "system",
            "content": (
                "Ты редактор долговременной памяти пользователя. Реши, содержит ли сообщение "
                "устойчивый личный факт, который пригодится в будущих чатах: предпочтение, интерес, "
                "устройство, имя, занятие, работа, учёба или важное пожелание. Не сохраняй вопрос, "
                "разовую задачу или временное настроение. Ответь строго одним JSON-объектом без markdown: "
                '{"memory":"краткая запись от третьего лица"} или {"memory":null}. '
                "Формулируй запись естественно на русском языке, например: "
                '{"memory":"Пользователь использует iPhone 16 Pro."}'
            ),
        },
        {"role": "user", "content": message},
    ]
    attach_full_user_profile(history)
    timeout = httpx.Timeout(18.0, connect=8.0)
    async with httpx.AsyncClient(timeout=timeout) as client:
        for attempt_model in model_fallback_chain(model):
            try:
                response = await client.post(
                    chat_completions_url(),
                    json={
                        "model": attempt_model,
                        "messages": history,
                        "stream": False,
                        "temperature": 0.1,
                        "max_tokens": 160,
                    },
                )
                if response.status_code >= 400:
                    continue
                data = response.json()
                choices = data.get("choices") or []
                if not choices:
                    continue
                answer = (choices[0].get("message") or {}).get("content") or choices[0].get("text") or ""
                return parse_memory_model_answer(answer)
            except Exception as exc:
                print(f"[memory] model failed model={attempt_model}: {str(exc)[:300]}")
    return ""


async def remember_from_message(message: str, model: str) -> Optional[str]:
    settings = MemorySettings(**state.get("memory_settings", {}))
    if not settings.use_saved_memory or not should_consider_memory(message):
        return None

    candidate = await formulate_memory_with_llm(message, model)
    if not candidate:
        candidate = extract_memory_candidate(message)
    if not candidate:
        return None

    normalized = normalize_saved_memory_text(candidate).casefold()
    async with state_lock:
        memories = state.setdefault("memories", [])
        if any(
            normalize_saved_memory_text(str(item.get("text", ""))).casefold() == normalized
            for item in memories
        ):
            return None
        memories.append(
            {
                "id": uuid.uuid4().hex[:12],
                "text": candidate,
                "created_at": int(time.time()),
            }
        )
        state["memories"] = memories[-80:]
        save_state()
    return candidate


RESPONSE_LANGUAGE_NAMES = {
    "ru": "Russian",
    "en": "English",
    "uk": "Ukrainian",
    "de": "German",
    "es": "Spanish",
    "fr": "French",
    "it": "Italian",
    "pt": "Portuguese",
    "pl": "Polish",
    "tr": "Turkish",
    "zh": "Chinese",
    "ja": "Japanese",
}


def normalize_response_language(language: str) -> str:
    clean = str(language or "ru").strip().lower().split("-", 1)[0]
    return clean if clean in RESPONSE_LANGUAGE_NAMES else "ru"


def response_language_prompt(language: str) -> str:
    normalized = normalize_response_language(language)
    name = RESPONSE_LANGUAGE_NAMES[normalized]
    return (
        "CRITICAL RESPONSE LANGUAGE OVERRIDE. "
        f"Reply only in {name}. The user selected this language in Neuro settings. "
        "Use it for the visible answer regardless of the language of the user's message, "
        "previous assistant messages, memory facts, device locale, or any earlier language instruction. "
        "Keep code, product names, URLs, and necessary technical identifiers unchanged."
    )


def history_for_llm(
    chat_id: int,
    client_context: Optional[Dict[str, Any]] = None,
    response_language: str = "ru",
) -> List[Dict[str, Any]]:
    messages = [
        message_for_llm(message)
        for message in state["messages"].get(chat_key(chat_id), [])
        if not message.get("image_job_id")
    ]
    context_block = format_client_context(client_context or {})
    system_content = SYSTEM_PROMPT
    system_content = f"{system_content}\n\n{personalization_prompt()}"
    saved_memory = memory_prompt()
    if saved_memory:
        system_content = f"{system_content}\n\n{saved_memory}"
    recent_history = recent_chat_history_prompt(chat_id)
    if recent_history:
        system_content = f"{system_content}\n\n{recent_history}"
    if context_block:
        system_content = f"{system_content}\n\n{context_block}"
    system_content = f"{system_content}\n\n{response_language_prompt(response_language)}"
    return [{"role": "system", "content": system_content}, *messages]


def resolve_llm_model(request: ChatRequest) -> str:
    requested = (request.model or "").strip()
    if requested in ALLOWED_LLM_MODELS:
        return requested
    if requested:
        print(f"[model] rejected unknown model='{requested}', fallback='{LLM_MODEL}'")
    return LLM_MODEL if LLM_MODEL in ALLOWED_LLM_MODELS else MODEL_NEMOTRON_EXTENDED


def model_fallback_chain(model: str) -> List[str]:
    preferred = [
        model,
        MODEL_QWEN_STANDARD,
        MODEL_GEMMA_STANDARD,
        MODEL_NEMOTRON_EXTENDED,
        MODEL_OMNICODER_9B,
        MODEL_GEMMA_INSTANT,
    ]
    chain = []
    for item in preferred:
        if item in ALLOWED_LLM_MODELS and item not in chain:
            chain.append(item)
    return chain

def make_search_snippet(text: str, query: str, limit: int = 86) -> str:
    clean = " ".join((text or "").split())
    if not clean:
        return ""
    index = clean.lower().find(query.lower())
    if index < 0:
        return clean[:limit] + ("..." if len(clean) > limit else "")
    start = max(0, index - 24)
    end = min(len(clean), index + len(query) + 58)
    prefix = "..." if start > 0 else ""
    suffix = "..." if end < len(clean) else ""
    return f"{prefix}{clean[start:end]}{suffix}"


def preview_text(value: str, limit: int = 180) -> str:
    compact = " ".join(value.split())
    return compact[:limit] + ("..." if len(compact) > limit else "")


def log_llm_request(payload: Dict[str, Any], chat_id: int) -> None:
    print(
        f"[stream] pipeline={STREAM_PIPELINE_VERSION} chat={chat_id} "
        f"model={payload.get('model')} stream={payload.get('stream')}"
    )
    for index, message in enumerate(payload.get("messages", [])):
        role = message.get("role")
        content = message.get("content")
        if isinstance(content, str):
            print(f"[stream] message[{index}] role={role} text='{preview_text(content)}'")
            continue

        if isinstance(content, list):
            parts = []
            for part in content:
                if part.get("type") == "text":
                    parts.append(f"text='{preview_text(str(part.get('text', '')))}'")
                elif part.get("type") == "image_url":
                    image_url = (part.get("image_url") or {}).get("url", "")
                    detail = (part.get("image_url") or {}).get("detail")
                    parts.append(
                        "image_url="
                        f"{'data:image' if str(image_url).startswith('data:image/') else 'other'} "
                        f"len={len(str(image_url))} detail={detail}"
                    )
            print(f"[stream] message[{index}] role={role} parts={'; '.join(parts)}")


async def iter_sse_data(response: httpx.Response):
    buffer = ""
    decoder = codecs.getincrementaldecoder("utf-8")()

    async for raw_chunk in response.aiter_raw():
        if not raw_chunk:
            continue

        buffer += decoder.decode(raw_chunk)
        buffer = buffer.replace("\r\n", "\n").replace("\r", "\n")

        while "\n\n" in buffer:
            raw_event, buffer = buffer.split("\n\n", 1)
            data_lines = []
            for raw_line in raw_event.split("\n"):
                line = raw_line.strip()
                if line.startswith("data:"):
                    data_lines.append(line[5:].strip())
            if data_lines:
                yield "\n".join(data_lines)

    tail = decoder.decode(b"", final=True)
    if tail:
        buffer += tail
        buffer = buffer.replace("\r\n", "\n").replace("\r", "\n")

    for raw_line in buffer.splitlines():
        line = raw_line.strip()
        if line.startswith("data:"):
            yield line[5:].strip()


async def stream_llm(
    history: List[Dict[str, Any]],
    chat_id: int,
    model: str,
    memory_updated: Optional[str] = None,
):
    timeout = httpx.Timeout(None, connect=15.0, write=30.0, pool=None)
    visible_answer = ""
    reasoning_answer = ""
    chunk_seq = 0
    request_started_at = time.perf_counter()
    first_chunk_at: Optional[float] = None
    last_error = ""

    try:
        for attempt_model in model_fallback_chain(model):
            payload = {
                "model": attempt_model,
                "messages": history,
                "stream": True,
                "temperature": 0.7,
                "max_tokens": MAX_RESPONSE_TOKENS,
            }
            log_llm_request(payload, chat_id)
            yield sse_event(
                {
                    "type": "start",
                    "chat_id": chat_id,
                    "model": attempt_model,
                    "stream_version": STREAM_PIPELINE_VERSION,
                }
            )
            if memory_updated:
                yield sse_event({"type": "memory_updated", "content": memory_updated})

            try:
                async with httpx.AsyncClient(timeout=timeout) as client:
                    async with client.stream("POST", chat_completions_url(), json=payload) as response:
                        if response.status_code >= 400:
                            body = await response.aread()
                            detail = body.decode(errors="ignore")[:1000]
                            raise RuntimeError(f"LLM HTTP {response.status_code}: {detail}")

                        async for line in iter_sse_data(response):
                            if line == "[DONE]":
                                break

                            try:
                                data = json.loads(line)
                            except json.JSONDecodeError:
                                continue

                            if isinstance(data.get("error"), dict):
                                raise RuntimeError(str(data.get("error")))
                            if isinstance(data.get("error"), str):
                                raise RuntimeError(data.get("error"))

                            choices = data.get("choices") or []
                            if not choices:
                                continue

                            piece = extract_delta(choices[0])
                            if piece["reasoning"]:
                                reasoning_answer += piece["reasoning"]
                                yield sse_event(
                                    {
                                        "type": "reasoning",
                                        "content": piece["reasoning"],
                                        "reasoning": piece["reasoning"],
                                    }
                                )
                                await asyncio.sleep(0)
                            if piece["content"]:
                                chunk_seq += 1
                                now = time.perf_counter()
                                if first_chunk_at is None:
                                    first_chunk_at = now
                                    print(
                                        f"[stream] first upstream chunk after "
                                        f"{(first_chunk_at - request_started_at) * 1000:.0f}ms"
                                    )
                                print(
                                    f"[stream] upstream chunk #{chunk_seq} "
                                    f"chars={len(piece['content'])}"
                                )
                                visible_answer += piece["content"]
                                yield sse_event(
                                    {
                                        "type": "chunk",
                                        "content": piece["content"],
                                        "seq": chunk_seq,
                                    }
                                )
                                await asyncio.sleep(0)

                if not visible_answer.strip() and not reasoning_answer.strip():
                    raise RuntimeError("LLM returned an empty stream")

                if not strip_thinking_blocks(visible_answer):
                    recovered_answer = await recover_visible_llm_answer(history, attempt_model)
                    if recovered_answer:
                        chunk_seq += 1
                        visible_answer += recovered_answer
                        yield sse_event(
                            {
                                "type": "chunk",
                                "content": recovered_answer,
                                "seq": chunk_seq,
                            }
                        )

                stored = visible_answer
                if reasoning_answer:
                    stored = f"<think>{reasoning_answer}</think>{visible_answer}"
                if stored.strip():
                    await append_message(chat_id, "assistant", stored)
                print(
                    f"[stream] done chat={chat_id} model={attempt_model} "
                    f"upstream chunks={chunk_seq}"
                )
                yield sse_event({"type": "done", "chat_id": chat_id, "title": state["chats"].get(chat_key(chat_id))})
                yield "data: [DONE]\n\n"
                return
            except Exception as exc:
                last_error = str(exc)
                print(f"[stream] model failed model={attempt_model}: {last_error[:500]}")
                if visible_answer.strip() or reasoning_answer.strip():
                    raise
                await asyncio.sleep(0)

        yield sse_event(
            {
                "type": "error",
                "error": (
                    f"Cannot reach {LLM_BASE_URL}. All fallback models failed. "
                    f"Last model error: {last_error}"
                ),
            }
        )
    except asyncio.CancelledError:
        stored = visible_answer
        if reasoning_answer:
            stored = f"<think>{reasoning_answer}</think>{visible_answer}"
        if stored.strip():
            await append_message(chat_id, "assistant", stored)
        raise
    except Exception as exc:
        yield sse_event(
            {
                "type": "error",
                "error": (
                    f"Cannot reach {LLM_BASE_URL} with model {model}. "
                    f"Check that the OpenAI-compatible server is running. Details: {exc}"
                ),
            }
        )


async def complete_llm(history: List[Dict[str, Any]], model: str) -> str:
    timeout = httpx.Timeout(None, connect=15.0, write=30.0, pool=None)
    last_error = ""
    async with httpx.AsyncClient(timeout=timeout) as client:
        for attempt_model in model_fallback_chain(model):
            payload = {
                "model": attempt_model,
                "messages": history,
                "stream": False,
                "temperature": 0.7,
                "max_tokens": MAX_RESPONSE_TOKENS,
            }
            try:
                response = await client.post(chat_completions_url(), json=payload)
                if response.status_code >= 400:
                    raise RuntimeError(f"LLM HTTP {response.status_code}: {response.text[:1000]}")
                data = response.json()
                if isinstance(data.get("error"), dict):
                    raise RuntimeError(str(data.get("error")))
                if isinstance(data.get("error"), str):
                    raise RuntimeError(data.get("error"))
                choices = data.get("choices") or []
                if not choices:
                    raise RuntimeError("LLM returned no choices")
                message = choices[0].get("message") or {}
                answer = message.get("content") or choices[0].get("text") or ""
                visible_answer = strip_thinking_blocks(answer)
                if visible_answer:
                    return visible_answer
                raise RuntimeError("LLM returned an empty response")
            except Exception as exc:
                last_error = str(exc)
                print(f"[chat] model failed model={attempt_model}: {last_error[:500]}")
                continue
    raise HTTPException(status_code=502, detail=f"All fallback models failed. Last error: {last_error}")


async def plan_music_generation(
    user_prompt: str,
    response_language: str = "ru",
    task_type: str = "text2music",
    instrumental: bool = False,
) -> Dict[str, Any]:
    """Ask the local chat model for a Suno-style blueprint before ACE-Step synthesis."""

    history = [
        {
            "role": "system",
            "content": (
                "You are the music planning stage for a local ACE-Step 1.5 generator. "
                "Return exactly one JSON object without markdown. Create a detailed English caption "
                "for the musical style, arrangement, mood, vocal character and production. Write a "
                "complete, coherent and rhymed song lyric with explicit section tags such as "
                "[Verse 1], [Chorus], [Verse 2], [Bridge], [Final Chorus]. Respect the user's desired "
                "theme and language. Do not imitate a living artist or reuse copyrighted lyrics. "
                "Choose a natural song duration in seconds from 10 to 600 based on the requested "
                "song and the lyric structure. Do not default every song to the same duration. "
                "Schema: "
                '{"title":"short original song title","caption":"English ACE-Step style prompt","lyrics":"structured lyrics",'
                '"vocal_language":"ru|en|uk|de|es|fr|it|pt|pl|tr|zh|ja",'
                '"duration":240,"bpm":90,"keyscale":"A minor","timesignature":"4"}. '
                "For an instrumental request return lyrics exactly as [Instrumental]."
            ),
        },
        {
            "role": "user",
            "content": (
                f"Task type: {task_type}\n"
                f"Instrumental: {instrumental}\n"
                f"Preferred lyric language: {normalize_response_language(response_language)}\n"
                f"User request: {user_prompt}"
            ),
        },
    ]
    try:
        raw = await asyncio.wait_for(
            complete_llm(history, MUSIC_PLANNER_MODEL),
            timeout=MUSIC_PLANNER_TIMEOUT_SECONDS,
        )
        plan = parse_music_plan(raw, user_prompt, response_language, task_type, instrumental)
        print(f"[music-planner] prepared JSON: {json.dumps(plan, ensure_ascii=False)}")
        return plan
    except Exception as exc:
        print(f"[music-planner] local LLM failed, using fallback: {exc}")
        plan = parse_music_plan("", user_prompt, response_language, task_type, instrumental)
        print(f"[music-planner] fallback JSON: {json.dumps(plan, ensure_ascii=False)}")
        return plan


music_service.planner = plan_music_generation


async def complete_llm_stage(
    history: List[Dict[str, Any]],
    model: str,
    instruction: str,
    max_tokens: int = 1200,
) -> str:
    staged_history = [
        {
            "role": "system",
            "content": f"{history[0]['content']}\n\n{instruction}",
        },
        *history[1:],
    ]
    timeout = httpx.Timeout(None, connect=15.0, write=30.0, pool=None)
    async with httpx.AsyncClient(timeout=timeout) as client:
        last_error = ""
        for attempt_model in model_fallback_chain(model):
            try:
                response = await client.post(
                    chat_completions_url(),
                    json={
                        "model": attempt_model,
                        "messages": staged_history,
                        "stream": False,
                        "temperature": 0.25,
                        "max_tokens": max_tokens,
                    },
                )
                if response.status_code >= 400:
                    raise RuntimeError(f"LLM HTTP {response.status_code}: {response.text[:700]}")
                data = response.json()
                choices = data.get("choices") or []
                if not choices:
                    raise RuntimeError("LLM returned no choices")
                message = choices[0].get("message") or {}
                answer = message.get("content") or choices[0].get("text") or ""
                if answer.strip():
                    return strip_thinking_blocks(answer).strip()
                raise RuntimeError("LLM returned an empty stage response")
            except Exception as exc:
                last_error = str(exc)
                print(f"[thinking] stage model failed model={attempt_model}: {last_error[:400]}")
    raise RuntimeError(f"Thinking stage failed: {last_error}")


async def recover_visible_llm_answer(history: List[Dict[str, Any]], model: str) -> str:
    try:
        return await complete_llm_stage(
            history,
            model,
            (
                "Дай пользователю готовый финальный ответ прямо сейчас. "
                "Не пиши внутренние рассуждения, теги think, анализ задачи или служебные заметки. "
                "Ответ должен быть видимым, полезным и написанным только на русском языке."
            ),
            max_tokens=1200,
        )
    except Exception as exc:
        print(f"[stream] visible answer recovery failed: {exc}")
        return "Да, помогу. Напиши, пожалуйста, какую задачу должен решать код и на каком языке его сделать."


async def stream_multistage_llm(
    history: List[Dict[str, Any]],
    chat_id: int,
    model: str,
    request: ChatRequest,
    memory_updated: Optional[str] = None,
):
    notes = []
    stages = [
        (
            "Анализирую задачу и собираю важные факты...",
            (
                "Составь краткую внутреннюю рабочую записку для другого ассистента. "
                "Разбери задачу пользователя, выдели факты, ограничения и необходимые вычисления. "
                "Не давай финальный ответ пользователю. Пиши конкретно и компактно."
            ),
        ),
        (
            "Проверяю расчёты, логику и детали...",
            (
                "Проверь задачу пользователя как внимательный рецензент. Найди возможные ошибки, "
                "пересчитай важные значения, отдели факты от предположений и составь компактные "
                "рекомендации для финального ответа. Не обращайся напрямую к пользователю."
            ),
        ),
    ]
    if request.thinking_effort == "extended":
        stages.append(
            (
                "Уточняю итоговую стратегию ответа...",
                (
                    "Подготовь финальный внутренний план ответа: что обязательно сообщить, "
                    "какой порядок выбрать и какие оговорки сохранить. Не пиши сам ответ пользователю."
                ),
            )
        )

    for status, instruction in stages:
        yield sse_event({"type": "reasoning", "content": status, "reasoning": status})
        try:
            notes.append(await complete_llm_stage(history, model, instruction))
        except Exception as exc:
            print(f"[thinking] continuing without stage notes: {exc}")
            break

    if notes:
        working_notes = "\n\n".join(
            f"Этап {index + 1}:\n{note}" for index, note in enumerate(notes)
        )
        history = [
            {
                "role": "system",
                "content": (
                    f"{history[0]['content']}\n\n"
                    "Ниже внутренние проверенные рабочие заметки. Используй их для точного "
                    "финального ответа, но не упоминай их существование и не цитируй дословно:\n"
                    f"{working_notes}"
                ),
            },
            *history[1:],
        ]
    async for event in stream_llm(history, chat_id, model, memory_updated):
        yield event


@app.post("/upload", response_model=UploadResponse)
async def upload_file(file: UploadFile = File(...)):
    if not file.content_type or not file.content_type.startswith("image/"):
        raise HTTPException(status_code=400, detail="Only image files are allowed")

    ext = Path(file.filename or "image.jpg").suffix or ".jpg"
    unique_name = f"{uuid.uuid4().hex}{ext}"
    save_path = UPLOAD_DIR / unique_name

    content = await file.read()
    save_path.write_bytes(content)

    return UploadResponse(url=f"{PUBLIC_SERVER_URL}/uploads/{unique_name}")


@app.post("/transcribe", response_model=TranscriptionResponse)
async def transcribe_audio(file: UploadFile = File(...)):
    audio_extensions = {".m4a", ".mp4", ".wav", ".mp3", ".aac", ".ogg", ".webm", ".flac"}
    ext = Path(file.filename or "voice.m4a").suffix.lower() or ".m4a"
    is_audio_type = bool(file.content_type and file.content_type.startswith("audio/"))
    if not is_audio_type and ext not in audio_extensions:
        raise HTTPException(status_code=400, detail="Only audio files are allowed")

    temp_path = ""
    try:
        content = await file.read()
        if not content:
            raise HTTPException(status_code=400, detail="Audio file is empty")

        with tempfile.NamedTemporaryFile(delete=False, suffix=ext) as tmp:
            tmp.write(content)
            temp_path = tmp.name

        model = await get_whisper_model()
        result = await asyncio.to_thread(transcribe_audio_sync, model, temp_path)
        return TranscriptionResponse(
            text=result["text"],
            language=result.get("language") or "ru",
        )
    except HTTPException:
        raise
    except Exception as exc:
        print(f"[whisper] transcription failed: {exc}")
        raise HTTPException(status_code=500, detail=f"Transcription failed: {exc}")
    finally:
        if temp_path:
            try:
                Path(temp_path).unlink(missing_ok=True)
            except Exception:
                pass


async def stream_music_job_events(job_id: str, chat_id: Optional[int] = None):
    previous = ""
    while True:
        job = music_service.jobs.get(job_id)
        if not isinstance(job, dict):
            yield sse_event({"type": "error", "error": "Music generation job not found"})
            break
        snapshot = music_service.snapshot(job)
        serialized = json.dumps(snapshot, ensure_ascii=True, sort_keys=True)
        if serialized != previous:
            yield sse_event({"type": "music_generation", "music_generation": snapshot})
            previous = serialized
        if snapshot.get("status") in {"completed", "failed"}:
            break
        await asyncio.sleep(0.8)
    if chat_id is not None:
        yield sse_event({"type": "done", "chat_id": chat_id, "title": state["chats"].get(chat_key(chat_id))})
    yield "data: [DONE]\n\n"


@app.post("/music/uploads", response_model=UploadResponse)
async def upload_music_source(file: UploadFile = File(...)):
    is_audio = bool(file.content_type and file.content_type.startswith("audio/"))
    extension = Path(file.filename or "track.wav").suffix.lower()
    if not is_audio and extension not in SUPPORTED_AUDIO_EXTENSIONS:
        raise HTTPException(status_code=400, detail="Only audio files are allowed")
    url = await music_service.save_upload(file.filename or "track.wav", await file.read())
    return UploadResponse(url=url)


@app.get("/music/health")
async def music_generation_health():
    return await music_service.health()


@app.post("/music/generate")
async def generate_music(request: MusicGenerationRequest):
    return await music_service.create_job(request)


@app.post("/music/cover")
async def generate_music_cover(request: MusicGenerationRequest):
    return await music_service.create_job(request.model_copy(update={"task_type": "cover"}))


@app.get("/music/jobs/{job_id}")
async def get_music_job(job_id: str):
    job = music_service.jobs.get(job_id)
    if not isinstance(job, dict):
        raise HTTPException(status_code=404, detail="Music generation job not found")
    return music_service.snapshot(job)


@app.get("/music/jobs/{job_id}/stream")
async def stream_music_job(job_id: str):
    if job_id not in music_service.jobs:
        raise HTTPException(status_code=404, detail="Music generation job not found")
    return StreamingResponse(
        stream_music_job_events(job_id),
        media_type="text/event-stream",
        headers={"Cache-Control": "no-cache", "Connection": "keep-alive", "X-Accel-Buffering": "no"},
    )


@app.get("/music/jobs/{job_id}/audio/{index}")
async def get_music_audio(job_id: str, index: int):
    content, content_type, filename = await music_service.audio_bytes(job_id, index)
    return Response(
        content=content,
        media_type=content_type,
        headers={"Content-Disposition": f'inline; filename="{filename}"'},
    )


@app.get("/music/jobs/{job_id}/lyrics/current")
async def get_current_music_lyric(job_id: str, position_seconds: float = 0.0):
    job = music_service.jobs.get(job_id)
    if not isinstance(job, dict):
        raise HTTPException(status_code=404, detail="Music generation job not found")
    timeline = job.get("lyrics_timeline") or []
    return {
        "position_seconds": max(0.0, position_seconds),
        "current_line": current_lyrics_line(timeline, max(0.0, position_seconds)),
        "timeline": timeline,
    }


@app.get("/music/library")
async def get_music_library():
    return music_service.library_snapshot()


@app.get("/music/library/{track_id}")
async def get_music_library_track(track_id: str):
    return music_service.library_track(track_id)


@app.post("/music/library/{track_id}/regenerate")
async def regenerate_music_library_track(track_id: str):
    return await music_service.regenerate_track(track_id)


@app.get("/music/library/{track_id}/audio")
async def get_music_library_audio(track_id: str):
    content, content_type, filename = await music_service.library_audio_bytes(track_id)
    return Response(
        content=content,
        media_type=content_type,
        headers={"Content-Disposition": f'inline; filename="{filename}"'},
    )


def is_image_generation_request(message: str) -> bool:
    clean = " ".join(message.strip().split())
    if IMAGE_REQUEST_RE.search(clean):
        return True
    if not IMAGE_GENERATION_ACTION_RE.search(clean):
        return False
    return not NON_IMAGE_GENERATION_TARGET_RE.search(clean)


def latest_image_job(chat_id: int) -> Optional[Dict[str, Any]]:
    jobs = state.setdefault("image_jobs", {})
    for message in reversed(state.get("messages", {}).get(chat_key(chat_id), [])):
        job_id = message.get("image_job_id")
        job = jobs.get(job_id)
        if isinstance(job, dict) and job.get("status") != "failed":
            return job
    return None


def latest_user_attached_images(chat_id: int) -> List[str]:
    for message in reversed(state.get("messages", {}).get(chat_key(chat_id), [])):
        if message.get("role") == "user" and message.get("images"):
            return [public_asset_url(str(image)) for image in message.get("images", [])]
    return []


def requires_identity_preservation(user_prompt: str, has_reference: bool) -> bool:
    if not has_reference:
        return False
    clean = user_prompt.casefold()
    return any(
        marker in clean
        for marker in (
            "этот ",
            "эта ",
            "это ",
            "именно ",
            "тот же",
            "такой же",
            "сохрани",
            "не меняй",
            "same ",
            "this ",
            "preserve",
            "exact ",
        )
    )


def is_image_refinement_request(message: str, previous_job: Optional[Dict[str, Any]]) -> bool:
    if previous_job is None:
        return False
    clean = " ".join(message.strip().split())
    if not clean or NON_IMAGE_GENERATION_TARGET_RE.search(clean):
        return False
    return bool(IMAGE_REFINEMENT_RE.search(clean))


def should_route_image_generation(
    message: str,
    previous_job: Optional[Dict[str, Any]],
) -> bool:
    clean = " ".join(message.strip().split())
    if not clean or NON_IMAGE_GENERATION_TARGET_RE.search(clean):
        return False
    return bool(
        is_image_generation_request(clean)
        or IMAGE_VISUAL_DESIRE_RE.search(clean)
        or is_image_refinement_request(clean, previous_job)
    )


def should_call_image_router(
    message: str,
    previous_job: Optional[Dict[str, Any]],
    attached_images: List[str],
) -> bool:
    return bool(
        attached_images
        or should_route_image_generation(message, previous_job)
    )


def should_use_multistage_chat(request: ChatRequest) -> bool:
    if request.reasoning_mode != "thinking":
        return False
    clean = " ".join(request.message.strip().split())
    return not (len(clean) <= 80 and CASUAL_CHAT_RE.fullmatch(clean))


def public_asset_url(value: str) -> str:
    for marker in ("/generated/", "/uploads/"):
        if marker in value:
            file_name = Path(value.split(marker, 1)[1].split("?", 1)[0].split("#", 1)[0]).name
            return f"{PUBLIC_SERVER_URL}{marker}{file_name}"
    return value


def image_job_snapshot(job: Dict[str, Any]) -> Dict[str, Any]:
    snapshot = ImageJobResponse(**job).model_dump()
    if snapshot.get("url"):
        snapshot["url"] = public_asset_url(str(snapshot["url"]))
    return snapshot


async def update_image_job(job_id: str, **changes: Any) -> Dict[str, Any]:
    async with state_lock:
        jobs = state.setdefault("image_jobs", {})
        job = jobs.get(job_id)
        if not isinstance(job, dict):
            raise RuntimeError(f"Image job {job_id} not found")
        job.update(changes)
        jobs[job_id] = job
        save_state()
        return image_job_snapshot(job)


def fallback_image_plan(
    user_prompt: str,
    previous_job: Optional[Dict[str, Any]] = None,
    attached_images: Optional[List[str]] = None,
) -> Dict[str, Any]:
    prompt_lower = user_prompt.casefold()
    forbid_reference = any(
        marker in prompt_lower
        for marker in (
            "не используй фото",
            "не используй изображение",
            "без референс",
            "не используй как референс",
            "как референс не используй",
            "do not use the photo",
            "without reference",
        )
    )
    if any(marker in prompt_lower for marker in ("обои", "сторис", "вертикал", "portrait", "story")):
        aspect_ratio = "9:16"
    elif any(marker in prompt_lower for marker in ("в полный рост", "полный рост", "full body", "full-body")):
        aspect_ratio = "4:5"
    elif any(marker in prompt_lower for marker in ("широк", "пейзаж", "landscape", "баннер", "wallpaper")):
        aspect_ratio = "16:9"
    else:
        aspect_ratio = "1:1"
    detail_markers = ("деталь", "реалист", "cinematic", "подроб", "сложн", "фон", "освещ")
    detail_score = sum(marker in prompt_lower for marker in detail_markers)
    if len(user_prompt) >= 2500 or detail_score >= 6:
        steps = 20
    elif len(user_prompt) >= 1200 or detail_score >= 5:
        steps = 16
    elif len(user_prompt) >= 600 or detail_score >= 3:
        steps = 12
    elif len(user_prompt) >= 260 or detail_score >= 2:
        steps = 8
    else:
        steps = 4
    previous_prompt = str((previous_job or {}).get("prompt") or "").strip()
    if previous_prompt:
        prompt = (
            f"{previous_prompt}. Apply this requested refinement: {user_prompt}. "
            "Preserve the original subject and composition unless the refinement asks to change them."
        )
    else:
        prompt = (
            "Create a polished, high-quality image that faithfully follows this request: "
            f"{user_prompt}. Clean composition, coherent lighting, detailed finish."
        )
    if any(marker in prompt_lower for marker in ("реалист", "фотореал", "realistic", "photoreal")):
        prompt += (
            " Photorealistic professional photography, natural anatomy, realistic fur and textures, "
            "physically accurate lighting, shallow depth of field, no illustration, no cartoon, no CGI."
        )
    return {
        "prompt": prompt,
        "aspect_ratio": aspect_ratio,
        "steps": steps,
        "reference_source": "none" if forbid_reference else "attached" if attached_images else "previous" if previous_job else "none",
        "use_reference": bool(attached_images or previous_job) and not forbid_reference,
        "max_attempts": 3 if any(marker in prompt_lower for marker in ("убери", "удали", "remove", "точно", "максимальн")) else 2,
    }


def parse_image_plan(
    raw: str,
    user_prompt: str,
    previous_job: Optional[Dict[str, Any]] = None,
    attached_images: Optional[List[str]] = None,
) -> Dict[str, Any]:
    fallback = fallback_image_plan(user_prompt, previous_job, attached_images)
    match = re.search(r"\{[\s\S]*\}", raw)
    if not match:
        return fallback
    try:
        parsed = json.loads(match.group(0))
    except json.JSONDecodeError:
        return fallback
    prompt = str(parsed.get("prompt") or "").strip()
    aspect_ratio = str(parsed.get("aspect_ratio") or "1:1").strip()
    if aspect_ratio not in IMAGE_ASPECT_DIMENSIONS:
        aspect_ratio = "1:1"
    try:
        steps = int(parsed.get("steps", 4))
    except (TypeError, ValueError):
        steps = 4
    try:
        max_attempts = int(parsed.get("max_attempts", fallback["max_attempts"]))
    except (TypeError, ValueError):
        max_attempts = fallback["max_attempts"]
    reference_source = str(parsed.get("reference_source") or fallback["reference_source"]).strip()
    if reference_source not in {"none", "attached", "previous"}:
        reference_source = fallback["reference_source"]
    use_reference = parsed.get("use_reference", fallback["use_reference"])
    prompt_lower = f"{user_prompt} {prompt}".casefold()
    if aspect_ratio == "1:1" and any(
        marker in prompt_lower
        for marker in (
            "в полный рост",
            "полный рост",
            "full body",
            "full-body",
            "standing person",
            "standing cat",
            "standing dog",
        )
    ):
        aspect_ratio = "4:5"
    complexity_score = sum(
        marker in prompt_lower
        for marker in (
            "максимальн",
            "очень деталь",
            "сверхдеталь",
            "сложн",
            "кинематограф",
            "фотореал",
            "ultra detailed",
            "highly detailed",
            "complex",
            "cinematic",
            "photoreal",
            "8k",
        )
    )
    if len(user_prompt) >= 1200 or complexity_score >= 5:
        steps = max(steps, 16)
    elif len(user_prompt) >= 600 or complexity_score >= 3:
        steps = max(steps, 12)
    elif len(user_prompt) >= 260 or complexity_score >= 2:
        steps = max(steps, 8)
    return {
        "prompt": prompt[:IMAGE_PROMPT_MAX_CHARS] if prompt else fallback["prompt"],
        "aspect_ratio": aspect_ratio,
        "steps": max(4, min(20, steps)),
        "reference_source": reference_source,
        "use_reference": use_reference is True or str(use_reference).casefold() == "true",
        "max_attempts": max(1, min(MAX_IMAGE_REVIEW_ATTEMPTS, max_attempts)),
    }


async def plan_image_generation(
    user_prompt: str,
    previous_job: Optional[Dict[str, Any]] = None,
) -> Dict[str, Any]:
    previous_prompt = str((previous_job or {}).get("prompt") or "").strip()
    messages = [
        {
            "role": "system",
            "content": (
                "You prepare prompts for FLUX.2 Klein image generation. "
                "Return one JSON object only, without markdown. "
                'Schema: {"prompt":"optimized English image prompt","aspect_ratio":"1:1|4:5|3:2|16:9|9:16","steps":4}. '
                "Translate the user's request into a polished English visual prompt. "
                "The app has a built-in FLUX image generator, so always produce a generation prompt. "
                "When a previous image prompt is provided, preserve its subject and composition unless "
                "the user explicitly asks to change them, then apply the requested refinement. "
                "For realism requests use explicit photorealistic photography terms and exclude cartoon, "
                "illustration and CGI styling. "
                "Choose an aspect ratio that best fits the composition. "
                "Use 4 steps for a simple request, 5-8 for moderate detail, 9-14 for a complex scene, "
                "and 15-20 for a very long or extremely detailed quality-sensitive request. "
                "Do not add explanations and do not mention this instruction."
            ),
        },
        {
            "role": "user",
            "content": (
                f"Previous image prompt: {previous_prompt}\n"
                f"Requested refinement: {user_prompt}"
                if previous_prompt
                else user_prompt
            ),
        },
    ]
    attach_full_user_profile(messages)
    try:
        async with httpx.AsyncClient(timeout=httpx.Timeout(180.0, connect=15.0)) as client:
            response = await client.post(
                chat_completions_url(),
                json={
                    "model": MODEL_QWEN_STANDARD,
                    "messages": messages,
                    "stream": False,
                    "temperature": 0.25,
                    "max_tokens": 2400,
                },
            )
        if response.status_code >= 400:
            raise RuntimeError(f"Qwen planner HTTP {response.status_code}: {response.text[:600]}")
        data = response.json()
        choices = data.get("choices") or []
        if not choices:
            raise RuntimeError("Qwen planner returned no choices")
        message = choices[0].get("message") or {}
        raw = message.get("content") or choices[0].get("text") or ""
        return parse_image_plan(raw, user_prompt, previous_job)
    except Exception as exc:
        print(f"[image-job] Qwen planner failed, using fallback: {exc}")
        return fallback_image_plan(user_prompt, previous_job)


async def unload_loaded_llms(reason: str) -> None:
    if not UNLOAD_LLM_BEFORE_HEAVY_WORKER:
        return
    try:
        async with httpx.AsyncClient(timeout=httpx.Timeout(20.0, connect=5.0)) as client:
            response = await client.get(f"{LM_STUDIO_NATIVE_URL}/api/v1/models")
            if response.status_code >= 400:
                raise RuntimeError(f"LM Studio models HTTP {response.status_code}: {response.text[:400]}")
            models = response.json().get("models") or []
            instance_ids = list(
                dict.fromkeys(
                    str(instance.get("id"))
                    for model in models
                    if model.get("type") == "llm"
                    for instance in (model.get("loaded_instances") or [])
                    if instance.get("id")
                )
            )
            for instance_id in instance_ids:
                unload_response = await client.post(
                    f"{LM_STUDIO_NATIVE_URL}/api/v1/models/unload",
                    json={"instance_id": instance_id},
                )
                if unload_response.status_code == 404:
                    continue
                if unload_response.status_code >= 400:
                    raise RuntimeError(
                        f"LM Studio unload HTTP {unload_response.status_code}: {unload_response.text[:400]}"
                    )
                print(f"[resources] unloaded LM Studio instance={instance_id} before {reason}")
    except Exception as exc:
        print(f"[resources] LM Studio unload skipped before {reason}: {exc}")


async def unload_loaded_llms_before_flux() -> None:
    await unload_loaded_llms("FLUX")


async def unload_loaded_llms_before_music() -> None:
    await unload_loaded_llms("ACE-Step")


music_service.before_synthesis = unload_loaded_llms_before_music


def choose_reference_url(
    plan: Dict[str, Any],
    previous_job: Optional[Dict[str, Any]],
    attached_images: Optional[List[str]] = None,
) -> Optional[str]:
    if not plan.get("use_reference"):
        return None
    attached_images = attached_images or []
    reference_source = str(plan.get("reference_source") or "")
    if reference_source == "attached" and attached_images:
        return public_asset_url(str(attached_images[-1]))
    previous_url = str((previous_job or {}).get("url") or "")
    if reference_source == "previous" and previous_url:
        return public_asset_url(previous_url)
    if attached_images:
        return public_asset_url(str(attached_images[-1]))
    return public_asset_url(previous_url) if previous_url else None


async def route_image_generation(
    chat_id: int,
    user_prompt: str,
    attached_images: Optional[List[str]] = None,
) -> Optional[Dict[str, Any]]:
    previous_job = latest_image_job(chat_id)
    current_attached_images = attached_images or []
    if not should_call_image_router(user_prompt, previous_job, current_attached_images):
        print("[image-router] skipped for normal text chat")
        return None
    attached_images = current_attached_images or latest_user_attached_images(chat_id)
    previous_prompt = str((previous_job or {}).get("prompt") or "").strip()
    previous_url = str((previous_job or {}).get("url") or "").strip()
    visual_inputs = list(dict.fromkeys([*attached_images, *([previous_url] if previous_url else [])]))
    messages = [
        {
            "role": "system",
            "content": (
                "Route the user's Russian or English message. Return JSON only, without explanation. "
                "The app has a built-in FLUX.2 Klein image generator. Never claim that image generation is unavailable. "
                "Set generate_image=true only when the user wants a new image now, including indirect wording "
                "such as 'мне нужно фото города', 'покажи уютный интерьер', or 'хочу картинку для обложки'. "
                "When a previous image prompt is provided and the user asks to change style, realism, lighting, "
                "background, composition, subject details, or says a brief refinement such as "
                "'а если максимально реалистично?', set generate_image=true and return a revised prompt that "
                "preserves the previous subject while applying the new request. "
                "Set it to false for questions, discussion of existing images, capability questions, code, text, or plans. "
                "Understand brief requests and typos. "
                'For normal chat return {"generate_image":false}. '
                'For image generation return {"generate_image":true,"prompt":"optimized English visual prompt",'
                '"aspect_ratio":"1:1|4:5|3:2|16:9|9:16","steps":4}. '
                "Choose the aspect ratio for the intended composition. "
                "Use 4 steps for a simple subject, 5-8 for moderate detail, 9-14 for a complex scene, "
                "and 15-20 for a very long or extremely detailed quality-sensitive request. "
                "For realism requests use photorealistic professional photography terms and explicitly avoid "
                "cartoon, illustration and CGI styling. "
                "Decide semantically from the full message and visual context, not by keyword matching. "
                "When attached or previous generated images are provided, inspect them and decide whether FLUX "
                "should receive one as an image-to-image reference. Use a reference for edits, removals, preserving "
                "identity or composition, and refinements of the same subject. If the user refers to 'this', 'that', "
                "or 'the same' object from an earlier attachment in the current chat, use that recent attachment as "
                "the reference and preserve its exact visual identity. Do not substitute a generic object or another "
                "model. An attached image by itself does not "
                "mean generation and does not automatically become a FLUX reference. For questions about an image, "
                "descriptions, OCR, extracting information, or making a table from the image, set generate_image=false "
                "so the normal vision chat answers directly. If the user wants a new unrelated image based on textual "
                "information from an attachment, generate it with use_reference=false and reference_source=none. "
                "An explicit request to create, draw or render a new visual remains generation even when an attachment "
                "is present and even when the user explicitly says not to use that attachment as a visual reference. "
                "Select the best composition ratio instead of defaulting to square: prefer 4:5 for full-body subjects "
                "and portraits, 3:2 or 16:9 for landscapes and horizontal compositions, 9:16 for phone wallpapers and "
                "stories, and 1:1 only for naturally square centered compositions. "
                "Include use_reference, reference_source and max_attempts in the "
                "JSON for generation. max_attempts must be 1, 2, or 3; use review iterations for exact edits or "
                "strong quality demands."
            ),
        },
        {
            "role": "user",
            "content": build_visual_service_content(
                (
                    f"Previous generated image prompt: {previous_prompt or '(none)'}\n"
                    f"Previous generated image available: {'yes' if previous_url else 'no'}\n"
                    f"Images attached to current message: {len(current_attached_images)}\n"
                    f"Recent reusable user attachments from this chat: {len(attached_images)}\n"
                    f"Current user message: {user_prompt}"
                ),
                visual_inputs,
            ),
        },
    ]
    attach_full_user_profile(messages)
    try:
        async with httpx.AsyncClient(timeout=httpx.Timeout(90.0, connect=15.0)) as client:
            response = await client.post(
                chat_completions_url(),
                json={
                    "model": MODEL_QWEN_STANDARD,
                    "messages": messages,
                    "stream": False,
                    "temperature": 0.1,
                    "max_tokens": 6000,
                    "response_format": {
                        "type": "json_schema",
                        "json_schema": {
                            "name": "image_generation_route",
                            "schema": {
                                "type": "object",
                                "properties": {
                                    "generate_image": {"type": "boolean"},
                                    "prompt": {"type": "string"},
                                    "aspect_ratio": {
                                        "type": "string",
                                        "enum": ["1:1", "4:5", "3:2", "16:9", "9:16"],
                                    },
                                    "steps": {"type": "integer", "minimum": 4, "maximum": 20},
                                    "use_reference": {"type": "boolean"},
                                    "reference_source": {
                                        "type": "string",
                                        "enum": ["none", "attached", "previous"],
                                    },
                                    "max_attempts": {"type": "integer", "minimum": 1, "maximum": 3},
                                },
                                "required": ["generate_image"],
                            },
                        },
                    },
                },
            )
        if response.status_code >= 400:
            raise RuntimeError(f"Qwen image router HTTP {response.status_code}: {response.text[:600]}")
        data = response.json()
        choices = data.get("choices") or []
        if not choices:
            raise RuntimeError("Qwen image router returned no choices")
        message = choices[0].get("message") or {}
        raw = (
            message.get("content")
            or choices[0].get("text")
            or message.get("reasoning_content")
            or ""
        )
        match = re.search(r"\{[\s\S]*\}", raw)
        if not match:
            raise RuntimeError(f"Qwen image router returned invalid JSON: {raw[:300]}")
        parsed = json.loads(match.group(0))
        generate_image = parsed.get("generate_image")
        if generate_image is not True and str(generate_image).casefold() != "true":
            return None
        plan = parse_image_plan(
            json.dumps(parsed, ensure_ascii=False),
            user_prompt,
            previous_job,
            attached_images,
        )
        plan["reference_url"] = choose_reference_url(plan, previous_job, attached_images)
        if requires_identity_preservation(user_prompt, bool(plan["reference_url"])):
            plan["max_attempts"] = MAX_IMAGE_REVIEW_ATTEMPTS
        if previous_job is not None:
            plan["source_job_id"] = str(previous_job.get("id") or "")
        return plan
    except Exception as exc:
        print(f"[image-router] Qwen router failed, using conservative fallback: {exc}")
        if should_route_image_generation(user_prompt, previous_job):
            plan = fallback_image_plan(user_prompt, previous_job, attached_images)
            plan["reference_url"] = choose_reference_url(plan, previous_job, attached_images)
            if requires_identity_preservation(user_prompt, bool(plan["reference_url"])):
                plan["max_attempts"] = MAX_IMAGE_REVIEW_ATTEMPTS
            if previous_job is not None:
                plan["source_job_id"] = str(previous_job.get("id") or "")
            return plan
        return None


async def create_image_job(
    chat_id: int,
    user_prompt: str,
    initial_plan: Optional[Dict[str, Any]] = None,
    response_language: str = "ru",
) -> Dict[str, Any]:
    job_id = uuid.uuid4().hex[:16]
    initial_aspect_ratio = str((initial_plan or {}).get("aspect_ratio") or "1:1")
    if initial_aspect_ratio not in IMAGE_ASPECT_DIMENSIONS:
        initial_aspect_ratio = "1:1"
    initial_width, initial_height = IMAGE_ASPECT_DIMENSIONS[initial_aspect_ratio]
    job = {
        "id": job_id,
        "chat_id": chat_id,
        "status": "planning",
        "user_prompt": user_prompt,
        "response_language": normalize_response_language(response_language),
        "prompt": str((initial_plan or {}).get("prompt") or ""),
        "aspect_ratio": initial_aspect_ratio,
        "width": initial_width,
        "height": initial_height,
        "steps": int((initial_plan or {}).get("steps") or 4),
        "guidance_scale": 1.0,
        "seed": None,
        "url": None,
        "error": None,
        "created_at": int(time.time()),
        "started_at": None,
        "completed_at": None,
        "elapsed_seconds": None,
        "source_job_id": str((initial_plan or {}).get("source_job_id") or "") or None,
        "reference_url": str((initial_plan or {}).get("reference_url") or "") or None,
        "original_reference_url": str((initial_plan or {}).get("reference_url") or "") or None,
        "reply": None,
        "attempt": 0,
        "max_attempts": int((initial_plan or {}).get("max_attempts") or 1),
        "review_satisfied": None,
        "review_feedback": None,
        "reference_used": False,
        "reference_count": 0,
    }
    async with state_lock:
        state.setdefault("image_jobs", {})[job_id] = job
        save_state()
    await append_image_job_message(chat_id, job_id)
    schedule_image_job(job_id)
    return image_job_snapshot(job)


def reference_url_to_worker_path(reference_url: Optional[str]) -> Optional[str]:
    if not reference_url:
        return None
    path = asset_url_to_path(reference_url)
    return str(path.resolve()) if path is not None else None


def reference_urls_to_worker_paths(reference_urls: List[str]) -> List[str]:
    paths = []
    for reference_url in reference_urls:
        path = reference_url_to_worker_path(reference_url)
        if path and path not in paths:
            paths.append(path)
    return paths


def generated_image_ready_reply(response_language: str) -> str:
    language = normalize_response_language(response_language)
    if language == "en":
        return "Done. Here is the finished image."
    if language == "uk":
        return "Готово. Ось фінальне зображення."
    return "Готово. Вот итоговое изображение."


def parse_image_review(raw: str, prompt: str, response_language: str = "ru") -> Dict[str, Any]:
    match = re.search(r"\{[\s\S]*\}", raw)
    if not match:
        return {
            "satisfied": True,
            "feedback": "Visual reviewer returned no structured correction.",
            "revised_prompt": prompt,
            "reply": generated_image_ready_reply(response_language),
        }
    try:
        parsed = json.loads(match.group(0))
    except json.JSONDecodeError:
        return {
            "satisfied": True,
            "feedback": "Visual reviewer returned invalid JSON.",
            "revised_prompt": prompt,
            "reply": generated_image_ready_reply(response_language),
        }
    satisfied = parsed.get("satisfied")
    revised_prompt = str(parsed.get("revised_prompt") or prompt).strip()
    return {
        "satisfied": satisfied is True or str(satisfied).casefold() == "true",
        "feedback": str(parsed.get("feedback") or "").strip(),
        "revised_prompt": revised_prompt[:IMAGE_PROMPT_MAX_CHARS] or prompt,
        "reply": str(parsed.get("reply") or generated_image_ready_reply(response_language)).strip(),
    }


async def review_generated_image(
    user_prompt: str,
    generated_url: str,
    prompt: str,
    attempt: int,
    max_attempts: int,
    original_reference_url: Optional[str] = None,
    response_language: str = "ru",
) -> Dict[str, Any]:
    visible_reply_language = RESPONSE_LANGUAGE_NAMES[normalize_response_language(response_language)]
    messages = [
        {
            "role": "system",
            "content": (
                "You are a strict visual quality reviewer for a local FLUX image generator. "
                "Inspect the generated image against the user's request and the FLUX prompt. "
                "When an original reference image is supplied, compare the generated result against it strictly. "
                "For a specific product or object, preserve its exact model, silhouette, camera layout, markings, "
                "colors and distinctive components. A generic substitute is a failure even when the scene is realistic. "
                "Check the requested subject, edits, intensity, composition, orientation, realism and any text removal. "
                "If an important request is not visibly satisfied and attempts remain, set satisfied=false and produce "
                "a stronger corrected English revised_prompt for the next image-to-image pass. "
                "If the result satisfies the request, set satisfied=true. "
                f"Write reply in natural concise {visible_reply_language} for the user. Return JSON only."
            ),
        },
        {
            "role": "user",
            "content": build_visual_service_content(
                (
                    f"User request: {user_prompt}\n"
                    f"Prompt used: {prompt}\n"
                    f"Attempt: {attempt} of {max_attempts}\n"
                    f"Original identity reference supplied: {'yes' if original_reference_url else 'no'}\n"
                    "If two images are attached, the first is the original identity reference and the second is the "
                    "generated result. Inspect the generated result strictly."
                ),
                [*([original_reference_url] if original_reference_url else []), generated_url],
            ),
        },
    ]
    attach_full_user_profile(messages)
    try:
        async with httpx.AsyncClient(timeout=httpx.Timeout(120.0, connect=15.0)) as client:
            response = await client.post(
                chat_completions_url(),
                json={
                    "model": MODEL_QWEN_STANDARD,
                    "messages": messages,
                    "stream": False,
                    "temperature": 0.1,
                    "max_tokens": 900,
                    "response_format": {
                        "type": "json_schema",
                        "json_schema": {
                            "name": "image_visual_review",
                            "schema": {
                                "type": "object",
                                "properties": {
                                    "satisfied": {"type": "boolean"},
                                    "feedback": {"type": "string"},
                                    "revised_prompt": {"type": "string"},
                                    "reply": {"type": "string"},
                                },
                                "required": ["satisfied", "feedback", "revised_prompt", "reply"],
                            },
                        },
                    },
                },
            )
        if response.status_code >= 400:
            raise RuntimeError(f"Qwen image review HTTP {response.status_code}: {response.text[:600]}")
        data = response.json()
        choices = data.get("choices") or []
        if not choices:
            raise RuntimeError("Qwen image review returned no choices")
        message = choices[0].get("message") or {}
        raw = message.get("content") or choices[0].get("text") or message.get("reasoning_content") or ""
        return parse_image_review(raw, prompt, response_language)
    except Exception as exc:
        print(f"[image-review] Qwen review failed, accepting generated result: {exc}")
        return {
            "satisfied": True,
            "feedback": f"Visual review unavailable: {exc}",
            "revised_prompt": prompt,
            "reply": generated_image_ready_reply(response_language),
        }


async def run_image_job(job_id: str) -> None:
    try:
        current = state.setdefault("image_jobs", {}).get(job_id)
        if not isinstance(current, dict):
            return
        started_at = int(time.time())
        await update_image_job(job_id, status="planning", started_at=started_at, error=None)
        plan = (
            {
                "prompt": str(current.get("prompt") or ""),
                "aspect_ratio": str(current.get("aspect_ratio") or "1:1"),
                "steps": int(current.get("steps") or 4),
                "max_attempts": int(current.get("max_attempts") or 1),
            }
            if current.get("prompt")
            else await plan_image_generation(
                str(current.get("user_prompt", "")),
                state.setdefault("image_jobs", {}).get(current.get("source_job_id")),
            )
        )
        aspect_ratio = str(plan["aspect_ratio"])
        width, height = IMAGE_ASPECT_DIMENSIONS[aspect_ratio]
        prompt = str(plan["prompt"])
        steps = int(plan["steps"])
        max_attempts = max(1, min(MAX_IMAGE_REVIEW_ATTEMPTS, int(plan.get("max_attempts") or 1)))
        original_reference_url = str(
            current.get("original_reference_url") or current.get("reference_url") or ""
        ) or None
        composition_reference_url = original_reference_url
        total_elapsed_seconds = 0.0
        for attempt in range(1, max_attempts + 1):
            await update_image_job(
                job_id,
                status="generating",
                prompt=prompt,
                aspect_ratio=aspect_ratio,
                width=width,
                height=height,
                steps=steps,
                attempt=attempt,
                max_attempts=max_attempts,
                reference_url=composition_reference_url,
                original_reference_url=original_reference_url,
            )
            reference_urls = list(
                dict.fromkeys(
                    [
                        *([original_reference_url] if original_reference_url else []),
                        *(
                            [composition_reference_url]
                            if composition_reference_url and composition_reference_url != original_reference_url
                            else []
                        ),
                    ]
                )
            )
            await unload_loaded_llms_before_flux()
            result = await generate_image(
                ImageGenerationRequest(
                    prompt=prompt,
                    width=width,
                    height=height,
                    steps=steps,
                    guidance_scale=1.0,
                    reference_image_paths=reference_urls_to_worker_paths(reference_urls),
                )
            )
            total_elapsed_seconds += result.elapsed_seconds
            await update_image_job(
                job_id,
                status="reviewing",
                seed=result.seed,
                url=result.url,
                width=result.width,
                height=result.height,
                steps=result.steps,
                elapsed_seconds=round(total_elapsed_seconds, 2),
                reference_used=result.reference_used,
                reference_count=result.reference_count,
            )
            review = await review_generated_image(
                str(current.get("user_prompt", "")),
                result.url,
                prompt,
                attempt,
                max_attempts,
                original_reference_url,
                str(current.get("response_language") or "ru"),
            )
            await update_image_job(
                job_id,
                review_satisfied=bool(review["satisfied"]),
                review_feedback=str(review["feedback"]),
            )
            if review["satisfied"]:
                reply = str(review["reply"])
                await update_image_job(
                    job_id,
                    status="completed",
                    reply=reply,
                    completed_at=int(time.time()),
                )
                await update_image_job_message(int(current["chat_id"]), job_id, reply)
                print(f"[image-job] completed id={job_id} attempts={attempt} url={result.url}")
                break
            if attempt >= max_attempts:
                reply = str(review["reply"])
                error = (
                    "Не удалось сохранить точное соответствие исходному изображению после "
                    f"{attempt} попыток. {review['feedback']}"
                )
                await update_image_job(
                    job_id,
                    status="failed",
                    reply=reply,
                    error=error,
                    completed_at=int(time.time()),
                )
                await update_image_job_message(int(current["chat_id"]), job_id, reply)
                print(f"[image-job] failed visual review id={job_id} attempts={attempt}: {error}")
                break
            prompt = str(review["revised_prompt"])
            composition_reference_url = result.url
    except asyncio.CancelledError:
        raise
    except Exception as exc:
        detail = exc.detail if isinstance(exc, HTTPException) else str(exc)
        print(f"[image-job] failed id={job_id}: {detail}")
        try:
            await update_image_job(
                job_id,
                status="failed",
                error=str(detail),
                completed_at=int(time.time()),
            )
        except Exception:
            pass


def schedule_image_job(job_id: str) -> None:
    running = image_job_tasks.get(job_id)
    if running is not None and not running.done():
        return
    task = asyncio.create_task(run_image_job(job_id))
    image_job_tasks[job_id] = task
    task.add_done_callback(lambda _: image_job_tasks.pop(job_id, None))


async def stream_image_job(job: Dict[str, Any]):
    yield sse_event(
        {
            "type": "start",
            "chat_id": job["chat_id"],
            "model": MODEL_QWEN_STANDARD,
            "stream_version": STREAM_PIPELINE_VERSION,
        }
    )
    yield sse_event({"type": "reasoning", "reasoning": "Продумываю композицию и детали сцены..."})
    yield sse_event({"type": "reasoning", "reasoning": "Подбираю формат изображения и готовлю английский промпт..."})
    yield sse_event({"type": "image_generation", "image_generation": image_job_snapshot(job)})
    yield sse_event({"type": "done", "chat_id": job["chat_id"], "title": state["chats"].get(chat_key(job["chat_id"]))})
    yield "data: [DONE]\n\n"


@app.get("/images/health")
async def image_generation_health():
    try:
        async with httpx.AsyncClient(timeout=4.0) as client:
            response = await client.get(f"{IMAGE_WORKER_URL}/health")
        if response.status_code >= 400:
            raise HTTPException(status_code=503, detail=response.text[:500])
        return response.json()
    except HTTPException:
        raise
    except Exception as exc:
        raise HTTPException(
            status_code=503,
            detail=(
                "FLUX image worker is not running. "
                "Run setup_flux_klein.bat once, then run_flux_worker.bat. "
                f"Details: {exc}"
            ),
        ) from exc


def local_worker_port(worker_url: str) -> Optional[int]:
    parsed = urlparse(worker_url)
    if parsed.hostname not in {"127.0.0.1", "localhost", "::1"}:
        return None
    return parsed.port


async def try_start_local_worker(worker_url: str, launcher_name: str) -> bool:
    port = local_worker_port(worker_url)
    launcher = Path(__file__).resolve().parent.parent / launcher_name
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
                response = await client.get(f"{worker_url}/health")
            if response.status_code < 400:
                return True
        except httpx.RequestError:
            continue
    return False


async def ensure_image_worker_available() -> None:
    try:
        async with httpx.AsyncClient(timeout=4.0) as client:
            response = await client.get(f"{IMAGE_WORKER_URL}/health")
        if response.status_code < 400:
            return
    except httpx.RequestError:
        pass
    if not await try_start_local_worker(IMAGE_WORKER_URL, "run_flux_worker.bat"):
        raise RuntimeError("FLUX image worker is unavailable and could not be started automatically.")


async def release_local_worker_memory(worker_url: str) -> None:
    if os.getenv("NEURO_KEEP_HEAVY_WORKERS", "").strip().lower() in {"1", "true", "yes", "on"}:
        return
    port = local_worker_port(worker_url)
    stop_script = Path(__file__).resolve().parent / "stop_local_port_process.ps1"
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


@app.post("/images/generate", response_model=ImageGenerationResponse)
async def generate_image(request: ImageGenerationRequest):
    async with image_worker_request_lock:
        try:
            await ensure_image_worker_available()
            async with httpx.AsyncClient(timeout=None) as client:
                response = await client.post(
                    f"{IMAGE_WORKER_URL}/generate",
                    json=request.model_dump(),
                )
            if response.status_code >= 400:
                try:
                    detail = response.json().get("detail", response.text)
                except Exception:
                    detail = response.text
                raise HTTPException(status_code=response.status_code, detail=detail)
            result = response.json()
            return ImageGenerationResponse(
                url=f"{PUBLIC_SERVER_URL}/generated/{result['file_name']}",
                seed=int(result["seed"]),
                width=int(result["width"]),
                height=int(result["height"]),
                steps=int(result["steps"]),
                elapsed_seconds=float(result["elapsed_seconds"]),
                reference_used=bool(result.get("reference_used")),
                reference_count=int(result.get("reference_count") or 0),
            )
        except HTTPException:
            raise
        except Exception as exc:
            raise HTTPException(
                status_code=503,
                detail=(
                    "Cannot reach the FLUX image worker. "
                    "Run setup_flux_klein.bat once, then run_flux_worker.bat. "
                    f"Details: {exc}"
                ),
            ) from exc
        finally:
            await release_local_worker_memory(IMAGE_WORKER_URL)


async def generate_music_artwork(track: Dict[str, Any]) -> Optional[str]:
    await unload_loaded_llms_before_flux()
    title = str(track.get("title") or "Original song")
    style = str(track.get("caption") or "")
    art_direction = secrets.choice(
        (
            "an intimate documentary moment with an unexpected focal subject",
            "a cinematic wide shot with layered depth and atmospheric perspective",
            "a refined close-up still life with tactile details and dramatic shadows",
            "an expressive editorial portrait-like composition without visible text",
            "a dreamlike but photoreal scene with strong color contrast and negative space",
        )
    )
    prompt = (
        f"Square album cover artwork for an original song titled '{title}'. "
        f"Visual mood inspired by this production brief: {style}. "
        f"Create a fresh visual interpretation using {art_direction}. "
        "Keep the emotional theme coherent while varying the subject, camera angle and composition. "
        "Cinematic editorial photography, expressive lighting, refined composition, "
        "no text, no logos, no watermark, premium streaming-service artwork."
    )
    result = await generate_image(
        ImageGenerationRequest(
            prompt=prompt[:24000],
            width=768,
            height=768,
            steps=8,
            guidance_scale=1.0,
            seed=secrets.randbelow(2**31 - 1),
        )
    )
    return result.url


music_service.cover_generator = generate_music_artwork


@app.get("/images/jobs/{job_id}", response_model=ImageJobResponse)
async def get_image_job(job_id: str):
    job = state.setdefault("image_jobs", {}).get(job_id)
    if not isinstance(job, dict):
        raise HTTPException(status_code=404, detail="Image job not found")
    return ImageJobResponse(**job)


@app.post("/images/unload")
async def unload_image_generation_pipeline():
    try:
        async with httpx.AsyncClient(timeout=20.0) as client:
            response = await client.post(f"{IMAGE_WORKER_URL}/unload")
        if response.status_code >= 400:
            raise HTTPException(status_code=response.status_code, detail=response.text[:500])
        return response.json()
    except HTTPException:
        raise
    except Exception as exc:
        raise HTTPException(
            status_code=503,
            detail=f"Cannot reach the FLUX image worker: {exc}",
        ) from exc


@app.post("/login", response_model=AuthResponse)
async def login(request: LoginRequest):
    async with state_lock:
        user = state["users"].get(request.email)
        if user is None:
            return AuthResponse(error="User not found")
        stored_password = user.get("password", "")
        if not verify_password(stored_password, request.password):
            return AuthResponse(error="Wrong password")
        if not stored_password.startswith(f"{PASSWORD_HASH_SCHEME}$"):
            user["password"] = hash_password(request.password)
            save_state()
    return AuthResponse(access_token=f"token_{secrets.token_hex(16)}")


@app.post("/register", response_model=SimpleResponse)
async def register(request: RegisterRequest):
    if not request.email or not request.password:
        return SimpleResponse(error="Email and password are required")
    async with state_lock:
        if request.email in state["users"]:
            return SimpleResponse(error="User with this email already exists")
        state["users"][request.email] = {"password": hash_password(request.password)}
        save_state()
    return SimpleResponse(message="Registration completed. You can sign in now.")


@app.get("/chats", response_model=List[ChatInfo])
async def get_chats():
    return [
        ChatInfo(id=int(cid), title=title)
        for cid, title in sorted(state["chats"].items(), key=lambda item: int(item[0]), reverse=True)
    ]


@app.get("/settings/personalization", response_model=PersonalizationSettings)
async def get_personalization():
    return PersonalizationSettings(**state.get("personalization", {}))


@app.put("/settings/personalization", response_model=PersonalizationSettings)
async def update_personalization(settings: PersonalizationSettings):
    async with state_lock:
        state["personalization"] = settings.model_dump()
        save_state()
    return settings


@app.get("/settings/memory", response_model=MemorySettings)
async def get_memory_settings():
    return MemorySettings(**state.get("memory_settings", {}))


@app.put("/settings/memory", response_model=MemorySettings)
async def update_memory_settings(settings: MemorySettings):
    async with state_lock:
        state["memory_settings"] = settings.model_dump()
        save_state()
    return settings


@app.get("/memories", response_model=List[SavedMemory])
async def get_memories():
    return [
        SavedMemory(
            id=str(memory.get("id", "")),
            text=normalize_saved_memory_text(str(memory.get("text", ""))),
            created_at=int(memory.get("created_at", 0)),
        )
        for memory in state.get("memories", [])
    ]


@app.delete("/memories/{memory_id}", response_model=SimpleResponse)
async def delete_memory(memory_id: str):
    async with state_lock:
        before = len(state.get("memories", []))
        state["memories"] = [
            memory for memory in state.get("memories", []) if str(memory.get("id")) != memory_id
        ]
        if len(state["memories"]) == before:
            raise HTTPException(status_code=404, detail="Memory not found")
        save_state()
    return SimpleResponse(message="Memory deleted")


@app.get("/chats/search", response_model=List[ChatSearchResult])
async def search_chats(q: str = ""):
    query = " ".join(q.strip().split())
    if not query:
        return []

    results: List[ChatSearchResult] = []
    query_lower = query.lower()
    for cid, title in sorted(state["chats"].items(), key=lambda item: int(item[0]), reverse=True):
        messages = state["messages"].get(cid, [])
        haystack_parts = [title]
        for message in messages:
            haystack_parts.append(str(message.get("content", "")))
            if message.get("images"):
                haystack_parts.append("изображение фото картинка")
        haystack = "\n".join(haystack_parts)
        if query_lower not in haystack.lower():
            continue

        snippet_source = title
        for message in messages:
            content = str(message.get("content", ""))
            if query_lower in content.lower():
                snippet_source = content
                break
        results.append(
            ChatSearchResult(
                id=int(cid),
                title=title,
                snippet=make_search_snippet(snippet_source, query),
            )
        )
    return results[:50]


@app.post("/chats", response_model=ChatInfo)
async def create_chat():
    chat_id = await ensure_chat()
    return ChatInfo(id=chat_id, title=state["chats"][chat_key(chat_id)])


@app.get("/chats/{chat_id}/messages", response_model=List[ServerMessage])
async def get_messages(chat_id: int):
    key = chat_key(chat_id)
    if key not in state["chats"]:
        raise HTTPException(status_code=404, detail="Chat not found")
    return [
        ServerMessage(
            role=msg.get("role", "assistant"),
            content=msg.get("content", ""),
            images=[public_asset_url(str(image)) for image in (msg.get("images") or [])],
            image_generation=(
                image_job_snapshot(state.setdefault("image_jobs", {})[msg["image_job_id"]])
                if msg.get("image_job_id") in state.setdefault("image_jobs", {})
                else None
            ),
            music_generation=music_service.job_snapshot(str(msg.get("music_job_id") or "")),
        )
        for msg in state["messages"].get(key, [])
    ]


@app.post("/chat", response_model=ChatResponse)
async def chat_non_stream(request: ChatRequest):
    chat_id = await ensure_chat(request.chat_id)
    model = resolve_llm_model(request)
    await prepare_user_turn(chat_id, request)
    memory_updated = None if request.regenerate else await remember_from_message(request.message, model)
    history = history_for_llm(chat_id, request.client_context, request.response_language)
    answer = await complete_llm(history, model)
    await append_message(chat_id, "assistant", answer)
    return ChatResponse(reply=answer, chat_id=chat_id, memory_updated=memory_updated)


@app.post("/chat/stream")
async def chat_stream(request: ChatRequest):
    chat_id = await ensure_chat(request.chat_id)
    model = resolve_llm_model(request)
    await prepare_user_turn(chat_id, request)
    image_plan = await route_image_generation(chat_id, request.message, request.images)
    if image_plan is not None:
        job = await create_image_job(chat_id, request.message, image_plan, request.response_language)
        return StreamingResponse(
            stream_image_job(job),
            media_type="text/event-stream",
            headers={
                "Cache-Control": "no-cache",
                "Connection": "keep-alive",
                "X-Accel-Buffering": "no",
            },
        )
    if is_music_generation_request(request.message):
        job = await music_service.create_job(
            MusicGenerationRequest(
                user_prompt=request.message,
                response_language=request.response_language,
            )
        )
        await append_music_job_message(chat_id, str(job["id"]))
        return StreamingResponse(
            stream_music_job_events(str(job["id"]), chat_id),
            media_type="text/event-stream",
            headers={
                "Cache-Control": "no-cache",
                "Connection": "keep-alive",
                "X-Accel-Buffering": "no",
            },
        )
    memory_updated = None if request.regenerate else await remember_from_message(request.message, model)
    history = history_for_llm(chat_id, request.client_context, request.response_language)
    return StreamingResponse(
        (
            stream_multistage_llm(history, chat_id, model, request, memory_updated)
            if should_use_multistage_chat(request)
            else stream_llm(history, chat_id, model, memory_updated)
        ),
        media_type="text/event-stream",
        headers={
            "Cache-Control": "no-cache",
            "Connection": "keep-alive",
            "X-Accel-Buffering": "no",
        },
    )


@app.get("/health")
async def health():
    llm_ok = False
    detail = None
    try:
        async with httpx.AsyncClient(timeout=5.0) as client:
            response = await client.get(models_url())
            llm_ok = response.status_code < 400
            if not llm_ok:
                detail = response.text[:300]
    except Exception as exc:
        detail = str(exc)
    return {"status": "ok", "llm_ok": llm_ok, "llm_detail": detail}


@app.get("/share/chats/{chat_id}")
async def share_chat(chat_id: int):
    key = chat_key(chat_id)
    if key not in state["chats"]:
        raise HTTPException(status_code=404, detail="Chat not found")

    title = escape(state["chats"].get(key, f"Chat {chat_id}"))
    rows = []
    for message in state["messages"].get(key, []):
        role = "Вы" if message.get("role") == "user" else "Neuro"
        content = escape(strip_thinking_blocks(str(message.get("content", ""))))
        images = "".join(
            f'<div><img src="{escape(str(image))}" style="max-width:280px;border-radius:18px;margin-top:8px"/></div>'
            for image in (message.get("images") or [])
        )
        rows.append(
            f"<section><strong>{escape(role)}</strong><p>{content}</p>{images}</section>"
        )

    body = "\n".join(rows)
    return HTMLResponse(
        f"""
        <!doctype html>
        <html lang="ru">
        <head>
          <meta charset="utf-8"/>
          <meta name="viewport" content="width=device-width,initial-scale=1"/>
          <title>{title}</title>
          <style>
            body{{font-family:system-ui,-apple-system,Segoe UI,sans-serif;max-width:760px;margin:0 auto;padding:28px;color:#111}}
            h1{{font-size:28px}}
            section{{padding:18px 0;border-bottom:1px solid #eee}}
            p{{white-space:pre-wrap;line-height:1.5}}
          </style>
        </head>
        <body><h1>{title}</h1>{body}</body>
        </html>
        """
    )


@app.get("/")
async def root():
    return {
        "status": "ok",
        "message": "Neuro Local Server is running",
        "phone_url": PUBLIC_SERVER_URL,
        "phone_setup_urls": phone_setup_urls(),
        "music_worker_url": MUSIC_WORKER_URL,
    }


@app.get("/config")
async def config():
    return {
        "model": LLM_MODEL,
        "llm_base_url": LLM_BASE_URL,
        "context_tokens": CONTEXT_TOKENS,
        "server_port": SERVER_PORT,
        "phone_setup_urls": phone_setup_urls(),
        "music_worker_url": MUSIC_WORKER_URL,
    }


if __name__ == "__main__":
    print("=" * 56)
    print("Neuro Local Server")
    print(f"Server: http://0.0.0.0:{SERVER_PORT}")
    print(f"LLM:    {LLM_MODEL}")
    print(f"API:    {LLM_BASE_URL}")
    print(f"Ctx:    {CONTEXT_TOKENS} tokens")
    print(f"Uploads: {UPLOAD_DIR}")
    print("-" * 56)
    print("Введите в приложении: Настройки -> Подключение к ПК")
    phone_urls = phone_setup_urls()
    if phone_urls:
        for phone_url in phone_urls:
            print(f"  {phone_url}")
    else:
        print(f"  http://<IP вашего ПК>:{SERVER_PORT}")
        print("  IP можно посмотреть командой: ipconfig")
    print("=" * 56)
    uvicorn.run(app, host=SERVER_HOST, port=SERVER_PORT)
