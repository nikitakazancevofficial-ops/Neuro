import asyncio
import unittest
from unittest.mock import AsyncMock, patch

import main


class FakeJsonResponse:
    def __init__(self, payload, status_code=200):
        self._payload = payload
        self.status_code = status_code
        self.text = ""

    def json(self):
        return self._payload


class FakeAsyncClient:
    def __init__(self, response, *args, **kwargs):
        self.response = response
        self.posts = []

    async def __aenter__(self):
        return self

    async def __aexit__(self, *args):
        return None

    async def post(self, url, json):
        self.posts.append((url, json))
        return self.response


class ImageRoutingTests(unittest.IsolatedAsyncioTestCase):
    def setUp(self):
        self.original_state = main.state
        self.original_save_state = main.save_state
        self.original_schedule_image_job = main.schedule_image_job
        main.state = {
            "users": {},
            "chats": {"1": "Test"},
            "messages": {"1": []},
            "image_jobs": {},
            "profile_facts": [
                "Тестового пользователя зовут Алекс.",
                "Тестовый пользователь предпочитает тёмную тему.",
                "Тестовый пользователь любит изучать технологии.",
                "Тестовый пользователь ценит краткие ответы.",
            ],
        }
        main.save_state = lambda: None
        main.schedule_image_job = lambda _: None

    def tearDown(self):
        main.state = self.original_state
        main.save_state = self.original_save_state
        main.schedule_image_job = self.original_schedule_image_job

    def test_normal_chat_does_not_wake_image_router(self):
        self.assertFalse(main.should_route_image_generation("привет", None))
        self.assertFalse(main.should_call_image_router("привет", None, []))
        self.assertFalse(
            main.should_route_image_generation(
                "я хочу узнать погоду",
                {"id": "previous", "prompt": "cute cat"},
            )
        )

    def test_attached_image_wakes_image_router(self):
        self.assertTrue(
            main.should_call_image_router(
                "что изображено на фотографии?",
                None,
                ["http://localhost:3510/uploads/source.png"],
            )
        )

    def test_indirect_visual_request_wakes_image_router(self):
        self.assertTrue(
            main.should_call_image_router(
                "я бы хотел увидеть милого, но злого кота",
                None,
                [],
            )
        )

    def test_greeting_skips_multistage_thinking(self):
        self.assertFalse(
            main.should_use_multistage_chat(
                main.ChatRequest(message="привет", reasoning_mode="thinking")
            )
        )
        self.assertTrue(
            main.should_use_multistage_chat(
                main.ChatRequest(
                    message="сравни две архитектуры и подробно объясни риски",
                    reasoning_mode="thinking",
                )
            )
        )

    def test_indirect_visual_request_routes_to_flux(self):
        self.assertTrue(
            main.should_route_image_generation(
                "я бы хотел увидеть милого, но злого кота",
                None,
            )
        )

    def test_capability_wording_for_image_routes_to_flux(self):
        self.assertTrue(
            main.should_route_image_generation(
                "а ты можешь сделать милого красивого котика, но злого, который шипит",
                None,
            )
        )

    def test_full_profile_is_always_attached(self):
        main.state["memory_settings"] = {"use_saved_memory": False}
        main.state["memories"] = [
            {"text": "Тестовый пользователь ценит краткие ответы."},
            {"text": "Тестовый пользователь ценит краткие ответы."},
            {"text": "Пользователь предпочитает тёмную тему."},
        ]
        history = main.history_for_llm(1)
        system_prompt = history[0]["content"]
        self.assertIn("Тестового пользователя зовут Алекс.", system_prompt)
        self.assertIn("Тестовый пользователь предпочитает тёмную тему.", system_prompt)
        self.assertIn("Тестовый пользователь любит изучать технологии.", system_prompt)
        self.assertIn("Тестовый пользователь ценит краткие ответы.", system_prompt)
        self.assertEqual(system_prompt.count("Тестовый пользователь ценит краткие ответы."), 1)

    def test_selected_response_language_overrides_previous_language_rules(self):
        history = main.history_for_llm(1, response_language="en")
        system_prompt = history[0]["content"]
        self.assertIn("Reply only in English", system_prompt)
        self.assertIn("regardless of the language", system_prompt)

    def test_unknown_response_language_falls_back_to_russian(self):
        self.assertEqual(main.normalize_response_language("unknown"), "ru")

    def test_repairs_windows_cp1251_mojibake(self):
        self.assertEqual(
            main.repair_mojibake_text("\u0420\u0457\u0421\u0402\u0420\u0451\u0420\u0406\u0420\u00b5\u0421\u201a"),
            "\u043f\u0440\u0438\u0432\u0435\u0442",
        )

    def test_repairs_windows_gbk_mojibake_recursively(self):
        damaged = {"chats": {"1": "\u950c\u8909\u61c8\u80c1\u68b0\u890c"}}
        self.assertEqual(
            main.repair_mojibake_tree(damaged),
            {"chats": {"1": "\u043f\u0440\u0438\u0432\u0435\u0442"}},
        )

    def test_repairs_cp1251_mojibake_with_undefined_byte(self):
        damaged = "\u0420\u0098\u0420\u00b7\u0420\u0455\u0420\u00b1\u0421\u0402\u0420\u00b0\u0420\u00b6\u0420\u00b5\u0420\u0405\u0420\u0451\u0420\u00b5"
        self.assertEqual(main.repair_mojibake_text(damaged), "\u0418\u0437\u043e\u0431\u0440\u0430\u0436\u0435\u043d\u0438\u0435")

    def test_service_messages_receive_full_profile(self):
        messages = [{"role": "system", "content": "Service instruction"}]
        main.attach_full_user_profile(messages)
        self.assertIn("Тестового пользователя зовут Алекс.", messages[0]["content"])
        self.assertIn("предпочитает тёмную тему", messages[0]["content"])

    async def test_login_migrates_legacy_password_to_hash(self):
        main.state["users"] = {"dev@example.test": {"password": "legacy-password"}}
        response = await main.login(main.LoginRequest(email="dev@example.test", password="legacy-password"))
        self.assertIsNotNone(response.access_token)
        self.assertTrue(main.state["users"]["dev@example.test"]["password"].startswith("pbkdf2_sha256$"))

    async def test_register_hashes_new_password(self):
        response = await main.register(main.RegisterRequest(email="new@example.test", password="new-password"))
        self.assertIsNone(response.error)
        stored_password = main.state["users"]["new@example.test"]["password"]
        self.assertNotEqual(stored_password, "new-password")
        self.assertTrue(main.verify_password(stored_password, "new-password"))

    def test_outdated_image_denial_is_removed_from_recent_context(self):
        main.state["messages"]["2"] = [
            {
                "role": "assistant",
                "content": "Я не могу генерировать изображения, используй DALL-E.",
            },
            {"role": "user", "content": "Привет"},
        ]
        prompt = main.recent_chat_history_prompt(1)
        self.assertNotIn("DALL-E", prompt)
        self.assertIn("Привет", prompt)

    def test_outdated_image_denial_is_replaced_in_current_chat_context(self):
        message = main.message_for_llm(
            {
                "role": "assistant",
                "content": "Я не могу генерировать изображения, используй Midjourney.",
            }
        )
        self.assertNotIn("не могу", message["content"].casefold())
        self.assertIn("могу создавать изображения", message["content"].casefold())

    def test_realism_follow_up_routes_with_previous_image(self):
        previous = {"id": "previous", "prompt": "cute but evil cat"}
        self.assertTrue(
            main.should_route_image_generation(
                "а если я хочу максимально реалистичного кота?",
                previous,
            )
        )
        plan = main.fallback_image_plan(
            "а если я хочу максимально реалистичного кота?",
            previous,
        )
        self.assertIn("cute but evil cat", plan["prompt"])
        self.assertIn("Photorealistic professional photography", plan["prompt"])
        self.assertIn("no cartoon", plan["prompt"])

    def test_long_detailed_generation_can_use_twenty_steps(self):
        request = "максимально реалистичный сложный кинематографичный очень детальный фон освещение " + (
            "подробное описание сцены " * 130
        )
        plan = main.fallback_image_plan(request)
        self.assertEqual(plan["steps"], 20)
        self.assertGreater(len(plan["prompt"]), 2000)

    def test_full_body_generation_uses_portrait_orientation(self):
        plan = main.parse_image_plan(
            '{"prompt":"photorealistic full body angry hissing cat","aspect_ratio":"1:1","steps":5}',
            "сгенерируй кота в полный рост",
        )
        self.assertEqual(plan["aspect_ratio"], "4:5")

    def test_fallback_respects_explicit_request_not_to_use_reference(self):
        plan = main.fallback_image_plan(
            "по информации с фото нарисуй инфографику, само фото как референс не используй",
            attached_images=[f"{main.PUBLIC_SERVER_URL}/uploads/source.png"],
        )
        self.assertFalse(plan["use_reference"])
        self.assertEqual(plan["reference_source"], "none")

    def test_old_generated_urls_are_rewritten_to_current_ip(self):
        job = {
            "id": "old",
            "chat_id": 1,
            "status": "completed",
            "user_prompt": "кот",
            "url": "http://192.0.2.10:3510/generated/cat.png",
            "created_at": 1,
        }
        snapshot = main.image_job_snapshot(job)
        self.assertEqual(
            snapshot["url"],
            f"{main.PUBLIC_SERVER_URL}/generated/cat.png",
        )

    def test_thinking_only_content_is_not_exposed_as_answer(self):
        self.assertEqual(main.strip_thinking_blocks("<think>internal notes</think>"), "")
        self.assertEqual(
            main.strip_thinking_blocks("<think>internal notes</think>Готовый ответ"),
            "Готовый ответ",
        )

    async def test_agent_router_is_skipped_for_normal_chat(self):
        response = FakeJsonResponse(
            {"choices": [{"message": {"content": '{"generate_image":false}'}}]}
        )
        client = FakeAsyncClient(response)
        with patch.object(main.httpx, "AsyncClient", return_value=client):
            plan = await main.route_image_generation(1, "привет, как дела?")
        self.assertIsNone(plan)
        self.assertEqual(len(client.posts), 0)

    async def test_agent_router_uses_attached_image_as_reference_and_selects_orientation(self):
        response = FakeJsonResponse(
            {
                "choices": [
                    {
                        "message": {
                            "content": (
                                '{"generate_image":true,"prompt":"remove all hello text",'
                                '"aspect_ratio":"3:2","steps":5,"use_reference":true,'
                                '"reference_source":"attached","max_attempts":3}'
                            )
                        }
                    }
                ]
            }
        )
        client = FakeAsyncClient(response)
        attached = f"{main.PUBLIC_SERVER_URL}/uploads/source.png"
        with patch.object(main.httpx, "AsyncClient", return_value=client):
            plan = await main.route_image_generation(1, "убери отсюда текст привет", [attached])
        self.assertEqual(plan["reference_url"], attached)
        self.assertEqual(plan["reference_source"], "attached")
        self.assertEqual(plan["aspect_ratio"], "3:2")
        self.assertEqual(plan["max_attempts"], 3)

    async def test_agent_router_can_answer_question_about_attached_image_without_flux(self):
        response = FakeJsonResponse(
            {"choices": [{"message": {"content": '{"generate_image":false}'}}]}
        )
        client = FakeAsyncClient(response)
        attached = f"{main.PUBLIC_SERVER_URL}/uploads/source.png"
        with patch.object(main.httpx, "AsyncClient", return_value=client):
            plan = await main.route_image_generation(1, "что изображено на фото?", [attached])
        self.assertIsNone(plan)

    async def test_agent_router_can_extract_table_from_attached_image_without_flux(self):
        response = FakeJsonResponse(
            {"choices": [{"message": {"content": '{"generate_image":false}'}}]}
        )
        client = FakeAsyncClient(response)
        attached = f"{main.PUBLIC_SERVER_URL}/uploads/source.png"
        with patch.object(main.httpx, "AsyncClient", return_value=client):
            plan = await main.route_image_generation(
                1,
                "сделай таблицу по информации с этого фото",
                [attached],
            )
        self.assertIsNone(plan)

    async def test_agent_router_can_generate_without_using_attached_image_as_reference(self):
        response = FakeJsonResponse(
            {
                "choices": [
                    {
                        "message": {
                            "content": (
                                '{"generate_image":true,"prompt":"clean infographic based on extracted data",'
                                '"aspect_ratio":"16:9","steps":5,"use_reference":false,'
                                '"reference_source":"none","max_attempts":2}'
                            )
                        }
                    }
                ]
            }
        )
        client = FakeAsyncClient(response)
        attached = f"{main.PUBLIC_SERVER_URL}/uploads/source.png"
        with patch.object(main.httpx, "AsyncClient", return_value=client):
            plan = await main.route_image_generation(
                1,
                "по информации с фото нарисуй новую инфографику, само фото не используй",
                [attached],
            )
        self.assertIsNone(plan["reference_url"])
        self.assertFalse(plan["use_reference"])
        self.assertEqual(plan["aspect_ratio"], "16:9")

    async def test_agent_router_reuses_recent_chat_attachment_for_this_object(self):
        attached = f"{main.PUBLIC_SERVER_URL}/uploads/phone.png"
        main.state["messages"]["1"] = [
            {"role": "user", "content": "что на фото?", "images": [attached]},
            {"role": "assistant", "content": "Это телефон.", "images": []},
        ]
        response = FakeJsonResponse(
            {
                "choices": [
                    {
                        "message": {
                            "content": (
                                '{"generate_image":true,"prompt":"person holding the exact same phone",'
                                '"aspect_ratio":"4:5","steps":8,"use_reference":true,'
                                '"reference_source":"attached","max_attempts":1}'
                            )
                        }
                    }
                ]
            }
        )
        client = FakeAsyncClient(response)
        with patch.object(main.httpx, "AsyncClient", return_value=client):
            plan = await main.route_image_generation(
                1,
                "сделай фото как человек держит этот телефон в руке",
            )
        self.assertEqual(plan["reference_url"], attached)
        self.assertEqual(plan["max_attempts"], main.MAX_IMAGE_REVIEW_ATTEMPTS)

    async def test_visual_review_can_trigger_second_reference_pass(self):
        generated_first = f"{main.PUBLIC_SERVER_URL}/generated/first.png"
        generated_second = f"{main.PUBLIC_SERVER_URL}/generated/second.png"
        main.state["image_jobs"]["job"] = {
            "id": "job",
            "chat_id": 1,
            "status": "planning",
            "user_prompt": "сделай кота намного злее",
            "prompt": "angry cat",
            "aspect_ratio": "4:5",
            "width": 768,
            "height": 960,
            "steps": 5,
            "guidance_scale": 1.0,
            "seed": None,
            "url": None,
            "error": None,
            "created_at": 1,
            "started_at": None,
            "completed_at": None,
            "elapsed_seconds": None,
            "source_job_id": None,
            "reference_url": f"{main.PUBLIC_SERVER_URL}/generated/original.png",
            "reply": None,
            "attempt": 0,
            "max_attempts": 2,
            "review_satisfied": None,
            "review_feedback": None,
        }
        main.state["messages"]["1"] = [
            {"role": "assistant", "content": "", "images": [], "image_job_id": "job"}
        ]
        responses = [
            main.ImageGenerationResponse(
                url=generated_first,
                seed=1,
                width=768,
                height=960,
                steps=5,
                elapsed_seconds=1.0,
                reference_used=True,
                reference_count=1,
            ),
            main.ImageGenerationResponse(
                url=generated_second,
                seed=2,
                width=768,
                height=960,
                steps=5,
                elapsed_seconds=1.5,
                reference_used=True,
                reference_count=2,
            ),
        ]
        reviews = [
            {
                "satisfied": False,
                "feedback": "cat is not angry enough",
                "revised_prompt": "extremely furious hissing cat",
                "reply": "",
            },
            {
                "satisfied": True,
                "feedback": "request satisfied",
                "revised_prompt": "extremely furious hissing cat",
                "reply": "Вот брат, теперь кот действительно очень злой.",
            },
        ]
        with (
            patch.object(main, "generate_image", new=AsyncMock(side_effect=responses)) as generate,
            patch.object(main, "review_generated_image", new=AsyncMock(side_effect=reviews)),
            patch.object(main, "unload_loaded_llms_before_flux", new=AsyncMock()),
            patch.object(main, "reference_url_to_worker_path", side_effect=lambda value: value),
        ):
            await main.run_image_job("job")
        self.assertEqual(generate.await_count, 2)
        second_request = generate.await_args_list[1].args[0]
        self.assertEqual(
            second_request.reference_image_paths,
            [
                f"{main.PUBLIC_SERVER_URL}/generated/original.png",
                generated_first,
            ],
        )
        self.assertEqual(main.state["image_jobs"]["job"]["status"], "completed")
        self.assertEqual(main.state["image_jobs"]["job"]["attempt"], 2)
        self.assertTrue(main.state["image_jobs"]["job"]["reference_used"])
        self.assertEqual(main.state["image_jobs"]["job"]["reference_count"], 2)
        self.assertIn("действительно очень злой", main.state["messages"]["1"][0]["content"])

    async def test_visual_review_failure_is_not_reported_as_completed(self):
        main.state["image_jobs"]["job"] = {
            "id": "job",
            "chat_id": 1,
            "status": "planning",
            "user_prompt": "сохрани именно этот телефон",
            "prompt": "person holding the exact same phone",
            "aspect_ratio": "4:5",
            "width": 768,
            "height": 960,
            "steps": 8,
            "guidance_scale": 1.0,
            "seed": None,
            "url": None,
            "error": None,
            "created_at": 1,
            "started_at": None,
            "completed_at": None,
            "elapsed_seconds": None,
            "source_job_id": None,
            "reference_url": f"{main.PUBLIC_SERVER_URL}/uploads/phone.png",
            "original_reference_url": f"{main.PUBLIC_SERVER_URL}/uploads/phone.png",
            "reply": None,
            "attempt": 0,
            "max_attempts": 1,
            "review_satisfied": None,
            "review_feedback": None,
        }
        main.state["messages"]["1"] = [
            {"role": "assistant", "content": "", "images": [], "image_job_id": "job"}
        ]
        generated = main.ImageGenerationResponse(
            url=f"{main.PUBLIC_SERVER_URL}/generated/wrong-phone.png",
            seed=1,
            width=768,
            height=960,
            steps=8,
            elapsed_seconds=1.0,
            reference_used=True,
            reference_count=1,
        )
        review = {
            "satisfied": False,
            "feedback": "wrong phone camera layout",
            "revised_prompt": "preserve exact phone camera layout",
            "reply": "Не удалось точно сохранить модель телефона.",
        }
        with (
            patch.object(main, "generate_image", new=AsyncMock(return_value=generated)),
            patch.object(main, "review_generated_image", new=AsyncMock(return_value=review)),
            patch.object(main, "unload_loaded_llms_before_flux", new=AsyncMock()),
            patch.object(main, "reference_url_to_worker_path", side_effect=lambda value: value),
        ):
            await main.run_image_job("job")
        job = main.state["image_jobs"]["job"]
        self.assertEqual(job["status"], "failed")
        self.assertIn("wrong phone camera layout", job["error"])

    async def test_three_generated_images_remain_as_three_chat_messages(self):
        for index in range(3):
            await main.create_image_job(
                1,
                f"image {index}",
                {
                    "prompt": f"prompt {index}",
                    "aspect_ratio": "1:1",
                    "steps": 4,
                },
            )

        messages = main.state["messages"]["1"]
        self.assertEqual(len(messages), 3)
        self.assertEqual(
            len({message["image_job_id"] for message in messages}),
            3,
        )


if __name__ == "__main__":
    unittest.main()
