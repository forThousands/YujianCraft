import tempfile
import unittest
import re
from pathlib import Path

import server


class ControlPanelTests(unittest.TestCase):
    def test_reads_all_current_source_groups(self):
        state = server.source_state()
        self.assertRegex(state["version"], r"^\d+\.\d+\.\d+$")
        self.assertEqual(6, len(state["materials"]))
        self.assertGreaterEqual(len(state["effects"]), 27)
        self.assertEqual(3, len(state["combat"]))
        self.assertEqual(4, len(state["riding"]))
        self.assertEqual(6, len(state["presentationDefaults"]))
        self.assertEqual(13, len(state["recipes"]))
        self.assertGreaterEqual(len(state["recipeCatalog"]), 13)

    def test_semantic_version_increment(self):
        self.assertEqual("0.9.2", server.bump_version("0.9.1", "patch"))
        self.assertEqual("0.10.0", server.bump_version("0.9.1", "minor"))
        self.assertEqual("1.0.0", server.bump_version("0.9.1", "major"))
        with self.assertRaises(server.PanelError):
            server.bump_version("0.9.1", "unknown")

    def test_current_values_pass_validation(self):
        state = server.source_state()
        payload = {
            "materials": {item["key"]: {
                "durability": item["durability"], "damage": item["damage"],
                "flightSpeed": item["flightSpeed"], "glowColor": item["glowColor"],
            } for item in state["materials"]},
            "combat": {item["key"]: item["value"] for item in state["combat"]},
            "effects": {item["key"]: item["value"] for item in state["effects"]},
            "riding": {item["key"]: item["value"] for item in state["riding"]},
            "presentationDefaults": {
                item["key"]: item["value"] for item in state["presentationDefaults"]
            },
        }
        normalized = server.normalized_payload(payload, state)
        self.assertEqual(set(payload["materials"]), set(normalized["materials"]))
        self.assertEqual(set(payload["effects"]), set(normalized["effects"]))
        self.assertEqual(payload["presentationDefaults"], normalized["presentationDefaults"])

    def test_package_presentation_boolean_round_trip(self):
        source = "public static final boolean DEFAULT_TEST_EFFECT = true;\n"
        self.assertTrue(server.parse_named_boolean(source, "DEFAULT_TEST_EFFECT"))
        updated = server.replace_named_boolean(source, "DEFAULT_TEST_EFFECT", False)
        self.assertFalse(server.parse_named_boolean(updated, "DEFAULT_TEST_EFFECT"))
        with self.assertRaises(server.PanelError):
            server.replace_named_boolean(source + source, "DEFAULT_TEST_EFFECT", False)

    def test_recipe_validation_and_path_safety(self):
        recipe = {
            "id": "test/example", "type": "shaped", "category": "misc",
            "result": "minecraft:diamond", "count": 2,
            "grid": ["", "minecraft:stick", "", "", "minecraft:diamond", "", "", "", ""],
        }
        self.assertEqual(recipe, server.validate_recipes([recipe])[0])
        with self.assertRaises(server.PanelError):
            server.validate_recipes([{**recipe, "id": "../outside"}])
        with self.assertRaises(server.PanelError):
            server.validate_recipes([recipe, recipe])

    def test_recipe_documents_match_vanilla_formats(self):
        shaped = {
            "id": "test", "type": "shaped", "category": "combat",
            "result": "minecraft:diamond_sword", "count": 1,
            "grid": ["", "minecraft:diamond", "", "", "minecraft:diamond", "", "", "minecraft:stick", ""],
        }
        document = server.recipe_document(shaped)
        self.assertEqual(["A", "A", "B"], document["pattern"])
        self.assertEqual("equipment", document["category"])
        self.assertEqual({"A", "B"}, set(document["key"]))

        shapeless = {**shaped, "type": "shapeless", "count": 3}
        document = server.recipe_document(shapeless)
        self.assertEqual("minecraft:crafting_shapeless", document["type"])
        self.assertEqual(3, document["result"]["count"])
        self.assertEqual(3, len(document["ingredients"]))

    def test_toml_update_preserves_sections_and_comments(self):
        with tempfile.TemporaryDirectory() as temp:
            path = Path(temp) / "sample.toml"
            path.write_text("# keep\n[iron]\n\tdamage = 6.0\n\tflightSpeed = 1.0\n", encoding="utf-8")
            server.update_toml_values(path, {("iron", "damage"): 7.5, ("iron", "flightSpeed"): 1.2})
            updated = path.read_text(encoding="utf-8")
            self.assertIn("# keep", updated)
            self.assertIn("damage = 7.5", updated)
            self.assertIn("flightSpeed = 1.2", updated)


if __name__ == "__main__":
    unittest.main()
