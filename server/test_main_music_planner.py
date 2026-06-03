import asyncio
import unittest
from unittest import mock

import main


class MusicPlannerTimeoutTests(unittest.IsolatedAsyncioTestCase):
    async def test_stalled_llm_uses_local_music_plan(self):
        async def stalled_llm(*_):
            await asyncio.Event().wait()

        with mock.patch.object(main, "complete_llm", side_effect=stalled_llm):
            with mock.patch.object(main, "MUSIC_PLANNER_TIMEOUT_SECONDS", 0.01):
                plan = await main.plan_music_generation(
                    "make a calm instrumental night track",
                    response_language="en",
                    instrumental=True,
                )

        self.assertEqual(plan["lyrics"], "[Instrumental]")
        self.assertIn("Theme: make a calm instrumental night track", plan["caption"])


if __name__ == "__main__":
    unittest.main()
