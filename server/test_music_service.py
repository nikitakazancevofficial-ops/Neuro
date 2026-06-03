import tempfile
import unittest
from pathlib import Path
from unittest import mock

import httpx

from music_service import (
    MusicGenerationRequest,
    MusicService,
    align_lyrics_timeline,
    build_lyrics_timeline,
    current_lyrics_line,
    estimate_music_duration,
    fallback_music_plan,
    is_music_generation_request,
    parse_music_plan,
    optional_int,
    worker_failure_message,
)


class FakeMusicService(MusicService):
    def __init__(self, root: Path):
        super().__init__(root, "http://192.168.1.10:3510", "http://127.0.0.1:3512")
        self.worker_requests = []
        self.query_count = 0

    async def _read_worker_audio(self, worker_file):
        return b"fake-mp3", "audio/mpeg"

    async def _ensure_worker_available(self, *_):
        return None

    async def _worker_post(self, path, payload):
        self.worker_requests.append((path, payload))
        if path == "/release_task":
            return {"code": 200, "data": {"task_id": "ace-task"}}
        self.query_count += 1
        if self.query_count == 1:
            return {"code": 200, "data": [{"task_id": "ace-task", "status": 0}]}
        return {
            "code": 200,
            "data": [
                {
                    "task_id": "ace-task",
                    "status": 1,
                    "result": (
                        '[{"file":"/v1/audio?path=test.mp3","prompt":"sad indie folk",'
                        '"lyrics":"[Verse 1]\\nA quiet road\\n\\n[Chorus]\\nCome home",'
                        '"metas":{"duration":30},"seed_value":"42",'
                        '"lm_model":"acestep-5Hz-lm-1.7B","dit_model":"acestep-v15-turbo"}]'
                    ),
                }
            ],
        }

    async def _release_task(self, payload):
        source_path = payload.get("src_audio_path")
        if source_path:
            self.worker_requests.append(("/release_task", payload, "src_audio", Path(source_path).name))
            return {"code": 200, "data": {"task_id": "ace-task"}}
        return await self._worker_post("/release_task", payload)


class MusicRoutingTests(unittest.IsolatedAsyncioTestCase):
    def test_music_request_detection(self):
        self.assertTrue(is_music_generation_request("сгенерируй невесёлую песню про Америку"))
        self.assertTrue(is_music_generation_request("создай атмосферный саундтрек"))
        self.assertFalse(is_music_generation_request("расскажи, что такое музыка"))
        self.assertFalse(is_music_generation_request("привет"))

    def test_fallback_plan_is_ready_for_ace_step(self):
        plan = fallback_music_plan("сгенерируй невесёлую песню на тему Америки")
        self.assertIn("melancholic", plan["caption"])
        self.assertIn("[Куплет 1]", plan["lyrics"])
        self.assertEqual(plan["vocal_language"], "ru")

    def test_llm_plan_parser_keeps_english_caption_and_structured_lyrics(self):
        plan = parse_music_plan(
            '{"title":"Тихая дорога","caption":"melancholic indie folk, restrained drums",'
            '"lyrics":"[Verse 1]\\nТихий вечер\\n\\n[Припев]\\nДорога домой",'
            '"vocal_language":"ru","duration":120,"bpm":76,'
            '"keyscale":"A minor","timesignature":"4"}',
            "грустная песня",
        )
        self.assertEqual(plan["caption"], "melancholic indie folk, restrained drums")
        self.assertEqual(plan["title"], "Тихая дорога")
        self.assertEqual(plan["bpm"], 76)
        self.assertIn("[Припев]", plan["lyrics"])

    def test_llm_plan_parser_accepts_literal_newlines_inside_json_lyrics(self):
        plan = parse_music_plan(
            '{"title":"Night Drive","caption":"moody synthwave","lyrics":"[Verse 1]\nCity lights\nQuiet roads",'
            '"vocal_language":"en"}',
            "make a night song",
            response_language="en",
        )
        self.assertEqual(plan["lyrics"], "[Verse 1]\nCity lights\nQuiet roads")

    def test_timeline_exposes_current_sung_line_for_player(self):
        timeline = build_lyrics_timeline("[Verse]\nFirst line\nSecond line", 20)
        self.assertEqual(len(timeline), 2)
        self.assertEqual(current_lyrics_line(timeline, 3)["text"], "First line")
        self.assertEqual(current_lyrics_line(timeline, 13)["text"], "Second line")
        self.assertEqual(timeline[0]["timing_source"], "estimated")

    def test_duration_estimator_respects_user_request_and_model_limit(self):
        self.assertEqual(estimate_music_duration("make it 5 minutes long"), 300.0)
        self.assertEqual(estimate_music_duration("make it 25 minutes long"), 600.0)
        self.assertNotEqual(estimate_music_duration(lyrics="[Verse]\nOne line\nSecond line"), 150.0)

    def test_whisper_alignment_uses_real_vocal_timestamps(self):
        timeline = align_lyrics_timeline(
            "[Verse]\nHello world\nCome home",
            20,
            [
                {"word": "Hello", "start": 3.0, "end": 3.4},
                {"word": "world", "start": 3.5, "end": 3.9},
                {"word": "Come", "start": 9.0, "end": 9.3},
                {"word": "home", "start": 9.4, "end": 9.8},
            ],
        )
        self.assertEqual(timeline[0]["start_seconds"], 3.0)
        self.assertEqual(timeline[1]["start_seconds"], 9.0)
        self.assertEqual(timeline[0]["timing_source"], "whisper_aligned")

    def test_worker_nan_failure_is_short_and_actionable(self):
        message = worker_failure_message(
            {
                "status": 2,
                "result": '[{"status":2,"stage":"failed"}]',
                "progress_text": "RuntimeError: Generation produced NaN or Inf latents. Float16 overflow.",
            },
            "ru",
        )
        self.assertIn("стабильный режим", message)
        self.assertIn("start_neuro_all.bat", message)
        self.assertNotIn("[{", message)

    def test_worker_unknown_failure_does_not_expose_internal_json(self):
        message = worker_failure_message({"status": 2, "result": '[{"status":2,"stage":"failed"}]'}, "en")
        self.assertEqual(message, "ACE-Step could not finish the track. Try again or restart start_neuro_all.bat.")

    def test_non_numeric_worker_bpm_is_hidden_from_android(self):
        self.assertIsNone(optional_int("N/A"))
        self.assertEqual(optional_int("74"), 74)

    async def test_text_to_music_job_calls_official_async_api(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            service = FakeMusicService(Path(temp_dir))

            async def planner(*_):
                return {
                    "caption": "sad indie folk",
                    "lyrics": "[Verse 1]\nA quiet road\n\n[Chorus]\nCome home",
                    "vocal_language": "en",
                    "duration": 30,
                    "bpm": 74,
                    "keyscale": "A minor",
                    "timesignature": "4",
                    "instrumental": False,
                }

            service.planner = planner
            resource_preparations = []

            async def prepare_resources():
                resource_preparations.append("ready")

            service.before_synthesis = prepare_resources
            service.schedule = lambda _: None
            job = await service.create_job(MusicGenerationRequest(user_prompt="make a sad song"))
            await service.run_job(job["id"])
            completed = service.jobs[job["id"]]
            self.assertEqual(completed["status"], "completed")
            release_payload = service.worker_requests[0][1]
            self.assertEqual(release_payload["task_type"], "text2music")
            self.assertFalse(release_payload["thinking"])
            self.assertFalse(release_payload["use_format"])
            self.assertFalse(release_payload["use_cot_caption"])
            self.assertFalse(release_payload["use_cot_language"])
            self.assertEqual(release_payload["prompt"], "sad indie folk")
            self.assertEqual(resource_preparations, ["ready"])
            self.assertEqual(len(completed["tracks"]), 1)
            library_track = service.library_snapshot()[0]
            self.assertEqual(library_track["audio_url"], "http://192.168.1.10:3510/music/library/{}-0/audio".format(job["id"]))
            self.assertTrue(Path(library_track["audio_path"]).exists())

    async def test_cover_job_passes_uploaded_source_audio(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            service = FakeMusicService(Path(temp_dir))
            service.schedule = lambda _: None
            source_url = await service.save_upload("source.wav", b"RIFF-test")
            job = await service.create_job(
                MusicGenerationRequest(
                    user_prompt="сделай джазовый кавер",
                    task_type="cover",
                    source_audio_url=source_url,
                    caption="intimate jazz piano cover",
                    lyrics="[Instrumental]",
                    instrumental=True,
                    duration=30,
                )
            )
            await service.run_job(job["id"])
            release_payload = service.worker_requests[0][1]
            self.assertEqual(release_payload["task_type"], "cover")
            self.assertFalse(release_payload["thinking"])
            self.assertTrue(release_payload["src_audio_path"].endswith(".wav"))
            self.assertEqual(release_payload["audio_cover_strength"], 0.7)
            self.assertEqual(service.worker_requests[0][2], "src_audio")
            self.assertEqual(service.worker_requests[0][3], Path(release_payload["src_audio_path"]).name)

    async def test_manual_song_skips_llm_planner(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            service = FakeMusicService(Path(temp_dir))

            async def unexpected_planner(*_):
                raise AssertionError("manual mode must not call the LLM planner")

            service.planner = unexpected_planner
            service.schedule = lambda _: None
            job = await service.create_job(
                MusicGenerationRequest(
                    user_prompt="manual song",
                    caption="dream pop, warm female vocal, live drums",
                    lyrics="[Verse 1]\nNight road\n\n[Chorus]\nCome home",
                    response_language="en",
                    duration=30,
                )
            )
            await service.run_job(job["id"])
            payload = service.worker_requests[0][1]
            self.assertEqual(payload["prompt"], "dream pop, warm female vocal, live drums")
            self.assertEqual(payload["lyrics"], "[Verse 1]\nNight road\n\n[Chorus]\nCome home")
            self.assertEqual(service.jobs[job["id"]]["status"], "completed")

    async def test_regenerate_track_reuses_saved_style_and_lyrics(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            service = FakeMusicService(Path(temp_dir))
            service.schedule = lambda _: None
            service.library["original"] = {
                "id": "original",
                "caption": "dream pop, warm vocals",
                "lyrics": "[Verse]\nNight road\n\n[Chorus]\nCome home",
                "vocal_language": "en",
                "duration": 185,
                "bpm": 84,
                "keyscale": "A minor",
                "timesignature": "4",
            }
            job = await service.regenerate_track("original")
            self.assertEqual(job["caption"], "dream pop, warm vocals")
            self.assertEqual(job["lyrics"], "[Verse]\nNight road\n\n[Chorus]\nCome home")
            self.assertEqual(job["duration"], 185)

    async def test_worker_connection_error_is_actionable(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            service = MusicService(Path(temp_dir), "http://192.168.1.10:3510", "http://127.0.0.1:3512")
            request = httpx.Request("GET", "http://127.0.0.1:3512/health")
            error = httpx.ConnectError("All connection attempts failed", request=request)
            with mock.patch("music_service.httpx.AsyncClient.get", new=mock.AsyncMock(side_effect=error)):
                with self.assertRaisesRegex(RuntimeError, "ACE-Step music worker is offline"):
                    await service._ensure_worker_available()

    async def test_worker_connection_error_is_localized_for_russian_ui(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            service = MusicService(Path(temp_dir), "http://192.168.1.10:3510", "http://127.0.0.1:3512")
            request = httpx.Request("GET", "http://127.0.0.1:3512/health")
            error = httpx.ConnectError("All connection attempts failed", request=request)
            with mock.patch("music_service.httpx.AsyncClient.get", new=mock.AsyncMock(side_effect=error)):
                with self.assertRaisesRegex(RuntimeError, "Музыкальный движок ACE-Step не запущен"):
                    await service._ensure_worker_available("ru")


if __name__ == "__main__":
    unittest.main()
