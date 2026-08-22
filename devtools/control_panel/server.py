from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import secrets
import shutil
import subprocess
import threading
import time
import webbrowser
from dataclasses import dataclass, field
from datetime import datetime
from http import HTTPStatus
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from typing import Any, Callable
from urllib.parse import parse_qs, urlparse


PANEL_DIR = Path(__file__).resolve().parent
PROJECT_ROOT = PANEL_DIR.parent.parent
STATIC_DIR = PANEL_DIR / "static"
LOCAL_SETTINGS = PANEL_DIR / ".local.json"
BACKUP_ROOT = PANEL_DIR / "backups"
MAX_REQUEST_BYTES = 1_000_000

MATERIAL_FILE = PROJECT_ROOT / "src/main/java/dev/swordflight/material/FlyingSwordMaterial.java"
EFFECT_FILE = PROJECT_ROOT / "src/main/java/dev/swordflight/config/EffectParameter.java"
SETTINGS_FILE = PROJECT_ROOT / "src/main/java/dev/swordflight/combat/SwordSettings.java"
SERVER_RIDING_FILE = PROJECT_ROOT / "src/main/java/dev/swordflight/flight/SwordRidingManager.java"
CLIENT_RIDING_FILE = PROJECT_ROOT / "src/main/java/dev/swordflight/client/ClientSwordRidingController.java"
CLIENT_OPTIONS_FILE = PROJECT_ROOT / "src/main/java/dev/swordflight/client/ClientOptions.java"
INPUT_FILE = PROJECT_ROOT / "src/main/java/dev/swordflight/client/ClientInputEvents.java"
BALANCE_LIMITS_FILE = PROJECT_ROOT / "src/main/java/dev/swordflight/config/SwordBalanceConfig.java"
LANG_FILE = PROJECT_ROOT / "src/main/resources/assets/swordflight/lang/zh_cn.json"
GRADLE_PROPERTIES = PROJECT_ROOT / "gradle.properties"
PROJECT_README = PROJECT_ROOT / "README.md"
RECIPE_MANIFEST = PANEL_DIR / "recipes.json"
GENERATED_RECIPE_DIR = PROJECT_ROOT / "src/generated/resources/data/swordflight/recipes"
GENERATED_ADVANCEMENT_ROOT = PROJECT_ROOT / "src/generated/resources/data/swordflight/advancements/recipes"

MATERIAL_PATTERN = re.compile(
    r'(?m)^(\s*)([A-Z]+)\("([a-z_]+)",\s*(Items\.[A-Z_]+),\s*(Items\.[A-Z_]+),\s*'
    r'(\d+),\s*([-+]?\d+(?:\.\d+)?)D,\s*([-+]?\d+(?:\.\d+)?)D,\s*0x([0-9A-Fa-f]{6})\)([,;])$'
)
EFFECT_PATTERN = re.compile(
    r'(?m)^(\s*)([A-Z0-9_]+)\(EffectConfigGroup\.([A-Z_]+),\s*"([a-z0-9_]+)",\s*'
    r'([-+]?\d+(?:\.\d+)?),\s*([-+]?\d+(?:\.\d+)?),\s*([-+]?\d+(?:\.\d+)?),\s*'
    r'([-+]?\d+(?:\.\d+)?),\s*(true|false)\)([,;])$'
)


class PanelError(RuntimeError):
    pass


def read_text(path: Path) -> str:
    return path.read_text(encoding="utf-8")


def atomic_write(path: Path, text: str) -> None:
    temporary = path.with_name(path.name + ".control-panel.tmp")
    temporary.write_text(text, encoding="utf-8", newline="")
    os.replace(temporary, path)


def java_number(value: float, integer: bool = False) -> str:
    if integer:
        return str(int(round(value)))
    rendered = f"{value:.10f}".rstrip("0").rstrip(".")
    return rendered if "." in rendered else rendered + ".0"


def parse_named_number(text: str, name: str, suffix: str = "D") -> float:
    pattern = re.compile(
        rf'(?m)^\s*(?:public|private)\s+static\s+final\s+(?:int|long|float|double)\s+'
        rf'{re.escape(name)}\s*=\s*([-+]?\d+(?:\.\d+)?){re.escape(suffix)}?;'
    )
    match = pattern.search(text)
    if not match:
        raise PanelError(f"找不到数值常量 {name}")
    return float(match.group(1))


def replace_named_number(text: str, name: str, value: float, java_type: str, suffix: str) -> str:
    pattern = re.compile(
        rf'(?m)^(\s*(?:public|private)\s+static\s+final\s+{java_type}\s+'
        rf'{re.escape(name)}\s*=\s*)([-+]?\d+(?:\.\d+)?){re.escape(suffix)}(;.*)$'
    )
    matches = list(pattern.finditer(text))
    if len(matches) != 1:
        raise PanelError(f"常量 {name} 应匹配 1 次，实际为 {len(matches)} 次")
    integer = java_type in {"int", "long"}
    literal = java_number(value, integer) + suffix
    return pattern.sub(lambda match: match.group(1) + literal + match.group(3), text, count=1)


def parse_named_boolean(text: str, name: str) -> bool:
    pattern = re.compile(
        rf'(?m)^\s*(?:public|private)\s+static\s+final\s+boolean\s+'
        rf'{re.escape(name)}\s*=\s*(true|false);'
    )
    match = pattern.search(text)
    if not match:
        raise PanelError(f"找不到布尔常量 {name}")
    return match.group(1) == "true"


def replace_named_boolean(text: str, name: str, value: bool) -> str:
    pattern = re.compile(
        rf'(?m)^(\s*(?:public|private)\s+static\s+final\s+boolean\s+'
        rf'{re.escape(name)}\s*=\s*)(true|false)(;.*)$'
    )
    matches = list(pattern.finditer(text))
    if len(matches) != 1:
        raise PanelError(f"布尔常量 {name} 应匹配 1 次，实际为 {len(matches)} 次")
    literal = "true" if value else "false"
    return pattern.sub(lambda match: match.group(1) + literal + match.group(3), text, count=1)


def parse_version() -> str:
    match = re.search(r'(?m)^mod_version=(\d+\.\d+\.\d+)$', read_text(GRADLE_PROPERTIES))
    if not match:
        raise PanelError("gradle.properties 中的 mod_version 不是标准三段版本号")
    return match.group(1)


def bump_version(version: str, part: str) -> str:
    major, minor, patch = (int(piece) for piece in version.split("."))
    if part == "major":
        return f"{major + 1}.0.0"
    if part == "minor":
        return f"{major}.{minor + 1}.0"
    if part == "patch":
        return f"{major}.{minor}.{patch + 1}"
    raise PanelError("版本递增方式只能是 patch、minor 或 major")


def set_version(version: str) -> None:
    text = read_text(GRADLE_PROPERTIES)
    updated, count = re.subn(r'(?m)^mod_version=\d+\.\d+\.\d+$', f"mod_version={version}", text)
    if count != 1:
        raise PanelError("无法唯一更新 mod_version")
    atomic_write(GRADLE_PROPERTIES, updated)
    readme = read_text(PROJECT_README)
    readme_updated, readme_count = re.subn(r'当前版本 `\d+\.\d+\.\d+`', f'当前版本 `{version}`', readme, count=1)
    if readme_count == 1:
        atomic_write(PROJECT_README, readme_updated)


def load_local_settings() -> dict[str, Any]:
    default_mods = PROJECT_ROOT.parent / ".minecraft/versions/1.20.1-Forge_47.4.22/mods"
    settings: dict[str, Any] = {"modsDir": str(default_mods.resolve())}
    if LOCAL_SETTINGS.exists():
        try:
            loaded = json.loads(read_text(LOCAL_SETTINGS))
            if isinstance(loaded, dict) and isinstance(loaded.get("modsDir"), str):
                settings["modsDir"] = loaded["modsDir"]
        except (OSError, json.JSONDecodeError):
            pass
    return settings


def save_local_settings(settings: dict[str, Any]) -> None:
    atomic_write(LOCAL_SETTINGS, json.dumps(settings, ensure_ascii=False, indent=2) + "\n")


def load_translations() -> dict[str, str]:
    return json.loads(read_text(LANG_FILE))


def item_label(item_id: str, translations: dict[str, str]) -> str:
    namespace, path = item_id.split(":", 1)
    if namespace == "swordflight":
        return translations.get(f"item.swordflight.{path}",
                                translations.get(f"block.swordflight.{path}", path))
    vanilla = {
        "diamond": "钻石", "amethyst_shard": "紫水晶碎片", "crafting_table": "工作台",
        "wooden_sword": "木剑", "stone_sword": "石剑", "iron_sword": "铁剑",
        "golden_sword": "金剑", "diamond_sword": "钻石剑", "netherite_sword": "下界合金剑",
    }
    return vanilla.get(path, path.replace("_", " "))


def load_recipes() -> list[dict[str, Any]]:
    if not RECIPE_MANIFEST.exists():
        raise PanelError(f"缺少配方清单：{RECIPE_MANIFEST}")
    try:
        document = json.loads(read_text(RECIPE_MANIFEST))
    except json.JSONDecodeError as exc:
        raise PanelError(f"配方清单 JSON 无效：{exc}") from exc
    recipes = document.get("recipes") if isinstance(document, dict) else None
    if not isinstance(recipes, list):
        raise PanelError("配方清单缺少 recipes 数组")
    return validate_recipes(recipes)


def validate_item_id(raw: Any, label: str, allow_empty: bool = False) -> str:
    value = str(raw or "").strip().lower()
    if allow_empty and not value:
        return ""
    if not re.fullmatch(r"[a-z0-9_.-]+:[a-z0-9_./-]+", value):
        raise PanelError(f"{label} 必须是 namespace:item_path 格式")
    return value


def validate_recipes(raw_recipes: Any) -> list[dict[str, Any]]:
    if not isinstance(raw_recipes, list):
        raise PanelError("recipes 必须是数组")
    if len(raw_recipes) > 256:
        raise PanelError("单个项目最多管理 256 个配方")
    result: list[dict[str, Any]] = []
    seen: set[str] = set()
    for index, raw in enumerate(raw_recipes):
        if not isinstance(raw, dict):
            raise PanelError(f"第 {index + 1} 个配方不是对象")
        recipe_id = str(raw.get("id", "")).strip().lower()
        if not re.fullmatch(r"[a-z0-9_./-]+", recipe_id):
            raise PanelError(f"第 {index + 1} 个配方 ID 无效")
        if any(part in {"", ".", ".."} for part in recipe_id.split("/")):
            raise PanelError(f"第 {index + 1} 个配方 ID 包含无效路径片段")
        if recipe_id in seen:
            raise PanelError(f"配方 ID 重复：{recipe_id}")
        seen.add(recipe_id)
        recipe_type = str(raw.get("type", "shaped"))
        if recipe_type not in {"shaped", "shapeless"}:
            raise PanelError(f"{recipe_id} 的类型只能是 shaped 或 shapeless")
        category = str(raw.get("category", "combat"))
        if category not in {"combat", "decorations", "misc"}:
            raise PanelError(f"{recipe_id} 的分类无效")
        grid = raw.get("grid")
        if not isinstance(grid, list) or len(grid) != 9:
            raise PanelError(f"{recipe_id} 必须包含 9 个合成格")
        safe_grid = [validate_item_id(value, f"{recipe_id} 的材料", True) for value in grid]
        if not any(safe_grid):
            raise PanelError(f"{recipe_id} 至少需要一种材料")
        result_item = validate_item_id(raw.get("result"), f"{recipe_id} 的产物")
        count = raw.get("count", 1)
        if isinstance(count, bool) or not isinstance(count, (int, float)) or not float(count).is_integer():
            raise PanelError(f"{recipe_id} 的产物数量必须是整数")
        count = int(count)
        if not 1 <= count <= 64:
            raise PanelError(f"{recipe_id} 的产物数量必须在 1 到 64 之间")
        result.append({
            "id": recipe_id, "type": recipe_type, "category": category,
            "result": result_item, "count": count, "grid": safe_grid,
        })
    return result


def trimmed_shaped_grid(grid: list[str]) -> list[list[str]]:
    occupied = [(index // 3, index % 3) for index, value in enumerate(grid) if value]
    if not occupied:
        raise PanelError("有序配方不能为空")
    min_row = min(row for row, _ in occupied)
    max_row = max(row for row, _ in occupied)
    min_col = min(col for _, col in occupied)
    max_col = max(col for _, col in occupied)
    return [[grid[row * 3 + col] for col in range(min_col, max_col + 1)]
            for row in range(min_row, max_row + 1)]


def recipe_document(recipe: dict[str, Any]) -> dict[str, Any]:
    result = {"item": recipe["result"]}
    if recipe["count"] != 1:
        result["count"] = recipe["count"]
    category = "equipment" if recipe["category"] == "combat" else "misc"
    if recipe["type"] == "shapeless":
        return {
            "type": "minecraft:crafting_shapeless", "category": category,
            "ingredients": [{"item": item} for item in recipe["grid"] if item],
            "result": result,
        }
    cells = trimmed_shaped_grid(recipe["grid"])
    symbols = list("ABCDEFGHI")
    item_symbols: dict[str, str] = {}
    for row in cells:
        for item in row:
            if item and item not in item_symbols:
                item_symbols[item] = symbols[len(item_symbols)]
    return {
        "type": "minecraft:crafting_shaped", "category": category,
        "key": {symbol: {"item": item} for item, symbol in item_symbols.items()},
        "pattern": ["".join(item_symbols.get(item, " ") for item in row) for row in cells],
        "result": result, "show_notification": True,
    }


def advancement_document(recipe: dict[str, Any]) -> dict[str, Any]:
    ingredient = next(item for item in recipe["grid"] if item)
    criterion = "has_" + ingredient.split(":", 1)[1].replace("/", "_")
    recipe_id = f"swordflight:{recipe['id']}"
    return {
        "parent": "minecraft:recipes/root",
        "criteria": {
            criterion: {"conditions": {"items": [{"items": [ingredient]}]},
                        "trigger": "minecraft:inventory_changed"},
            "has_the_recipe": {"conditions": {"recipe": recipe_id},
                               "trigger": "minecraft:recipe_unlocked"},
        },
        "requirements": [[criterion, "has_the_recipe"]],
        "rewards": {"recipes": [recipe_id]},
        "sends_telemetry_event": False,
    }


def write_json(path: Path, value: Any) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    atomic_write(path, json.dumps(value, ensure_ascii=False, indent=2) + "\n")


def save_recipes(raw_recipes: Any) -> list[dict[str, Any]]:
    recipes = validate_recipes(raw_recipes)
    old_recipes = load_recipes() if RECIPE_MANIFEST.exists() else []
    managed_paths: list[Path] = [RECIPE_MANIFEST]
    for recipe in old_recipes:
        managed_paths.append(GENERATED_RECIPE_DIR / f"{recipe['id']}.json")
        managed_paths.append(GENERATED_ADVANCEMENT_ROOT / recipe["category"] / f"{recipe['id']}.json")
    backup_sources([path for path in managed_paths if path.exists()], "recipes")

    old_ids = {(recipe["id"], recipe["category"]) for recipe in old_recipes}
    new_ids = {(recipe["id"], recipe["category"]) for recipe in recipes}
    for recipe_id, category in old_ids - new_ids:
        recipe_path = GENERATED_RECIPE_DIR / f"{recipe_id}.json"
        advancement_path = GENERATED_ADVANCEMENT_ROOT / category / f"{recipe_id}.json"
        if recipe_path.exists():
            recipe_path.unlink()
        if advancement_path.exists():
            advancement_path.unlink()

    write_json(RECIPE_MANIFEST, {"schemaVersion": 1, "recipes": recipes})
    for recipe in recipes:
        write_json(GENERATED_RECIPE_DIR / f"{recipe['id']}.json", recipe_document(recipe))
        write_json(GENERATED_ADVANCEMENT_ROOT / recipe["category"] / f"{recipe['id']}.json",
                   advancement_document(recipe))
    return recipes


def recipe_catalog(recipes: list[dict[str, Any]], translations: dict[str, str]) -> list[dict[str, str]]:
    item_ids = {item for recipe in recipes for item in recipe["grid"] if item}
    item_ids.update(recipe["result"] for recipe in recipes)
    for source in (MATERIAL_FILE, PROJECT_ROOT / "src/main/java/dev/swordflight/upgrade/FlyingSwordModule.java"):
        for constant in re.findall(r"Items\.([A-Z0-9_]+)", read_text(source)):
            item_ids.add("minecraft:" + constant.lower())
    return [{"id": item_id, "label": item_label(item_id, translations)} for item_id in sorted(item_ids)]


def find_worlds(mods_dir: str) -> list[dict[str, str]]:
    mods = Path(mods_dir).expanduser()
    version_dir = mods.parent
    saves = version_dir / "saves"
    if not saves.is_dir():
        return []
    worlds: list[dict[str, str]] = []
    for world in sorted((entry for entry in saves.iterdir() if entry.is_dir()), key=lambda p: p.name.lower()):
        serverconfig = world / "serverconfig"
        if serverconfig.is_dir():
            worlds.append({"name": world.name, "path": str(world.resolve())})
    return worlds


def parse_limit(name: str) -> float:
    return parse_named_number(read_text(BALANCE_LIMITS_FILE), name)


def source_state() -> dict[str, Any]:
    translations = load_translations()
    recipes = load_recipes()
    material_text = read_text(MATERIAL_FILE)
    materials: list[dict[str, Any]] = []
    for match in MATERIAL_PATTERN.finditer(material_text):
        key = match.group(3)
        materials.append({
            "enum": match.group(2),
            "key": key,
            "label": translations.get(f"material.swordflight.{key}", key),
            "durability": int(match.group(6)),
            "damage": float(match.group(7)),
            "flightSpeed": float(match.group(8)),
            "glowColor": "#" + match.group(9).upper(),
        })
    if len(materials) != 6:
        raise PanelError(f"预期读取 6 种飞剑材质，实际读取 {len(materials)} 种")

    group_labels = {
        "GLOBAL": "通用规则", "FLAME": "灼烧核心", "LIGHTNING": "引雷核心",
        "POISON": "蚀毒核心", "EXPLOSION": "爆裂核心", "ARROW_RAIN": "箭雨核心",
        "REFINEMENT": "属性强化",
    }
    effects: list[dict[str, Any]] = []
    for match in EFFECT_PATTERN.finditer(read_text(EFFECT_FILE)):
        key = match.group(4)
        effects.append({
            "enum": match.group(2),
            "group": match.group(3),
            "groupLabel": group_labels.get(match.group(3), match.group(3)),
            "key": key,
            "label": translations.get(f"effect_parameter.swordflight.{key}", key),
            "value": float(match.group(5)),
            "minimum": float(match.group(6)),
            "maximum": float(match.group(7)),
            "step": float(match.group(8)),
            "integer": match.group(9) == "true",
        })
    if not effects:
        raise PanelError("未能读取任何效果参数")

    settings_text = read_text(SETTINGS_FILE)
    server_riding = read_text(SERVER_RIDING_FILE)
    client_riding = read_text(CLIENT_RIDING_FILE)
    input_text = read_text(INPUT_FILE)
    client_options_text = read_text(CLIENT_OPTIONS_FILE)
    local = load_local_settings()
    return {
        "version": parse_version(),
        "materials": materials,
        "materialLimits": {
            "damage": [parse_limit("MIN_DAMAGE"), parse_limit("MAX_DAMAGE")],
            "flightSpeed": [parse_limit("MIN_SPEED"), parse_limit("MAX_SPEED")],
            "durability": [1, 100000],
        },
        "combat": [
            {"key": "minimumDockTicks", "label": "最短停靠时间", "unit": "刻",
             "value": parse_named_number(settings_text, "DEFAULT_MINIMUM_DOCK_TICKS", ""),
             "minimum": parse_named_number(settings_text, "MINIMUM_DOCK_TICKS", ""),
             "maximum": parse_named_number(settings_text, "MAXIMUM_DOCK_TICKS", ""), "step": 1, "integer": True},
            {"key": "automaticTargetRadius", "label": "自动索敌半径", "unit": "格",
             "value": parse_named_number(settings_text, "DEFAULT_AUTOMATIC_RADIUS"),
             "minimum": parse_named_number(settings_text, "MINIMUM_AUTOMATIC_RADIUS"),
             "maximum": parse_named_number(settings_text, "MAXIMUM_AUTOMATIC_RADIUS"), "step": 1, "integer": False},
            {"key": "crosshairLockRadius", "label": "准心锁定距离", "unit": "格",
             "value": parse_named_number(settings_text, "DEFAULT_LOCK_RADIUS"),
             "minimum": parse_named_number(settings_text, "MINIMUM_LOCK_RADIUS"),
             "maximum": parse_named_number(settings_text, "MAXIMUM_LOCK_RADIUS"), "step": 1, "integer": False},
        ],
        "effects": effects,
        "riding": [
            {"key": "baseFlightSpeed", "label": "御剑水平速度", "unit": "倍率",
             "value": parse_named_number(server_riding, "RIDING_FLIGHT_SPEED", "F"),
             "minimum": 0.01, "maximum": 0.5, "step": 0.005, "integer": False},
            {"key": "ascendSpeed", "label": "御剑上升速度", "unit": "格/刻",
             "value": parse_named_number(client_riding, "ASCEND_SPEED"),
             "minimum": 0.05, "maximum": 1.0, "step": 0.01, "integer": False},
            {"key": "descendSpeed", "label": "御剑下降速度", "unit": "格/刻",
             "value": abs(parse_named_number(client_riding, "DESCEND_SPEED")),
             "minimum": 0.05, "maximum": 1.0, "step": 0.01, "integer": False},
            {"key": "doubleTapWindow", "label": "双击空格判定窗口", "unit": "毫秒",
             "value": parse_named_number(input_text, "SWORD_RIDING_DOUBLE_TAP_MS", "L"),
             "minimum": 150, "maximum": 800, "step": 10, "integer": True},
        ],
        "presentationDefaults": [
            {"key": "flightSound", "constant": "DEFAULT_FLIGHT_SOUND", "label": "飞行破空声",
             "value": parse_named_boolean(client_options_text, "DEFAULT_FLIGHT_SOUND")},
            {"key": "swordTrail", "constant": "DEFAULT_SWORD_TRAIL", "label": "材质色发光尾迹",
             "value": parse_named_boolean(client_options_text, "DEFAULT_SWORD_TRAIL")},
            {"key": "swordBodyGlow", "constant": "DEFAULT_SWORD_BODY_GLOW", "label": "剑身全亮",
             "value": parse_named_boolean(client_options_text, "DEFAULT_SWORD_BODY_GLOW")},
            {"key": "inventoryGlint", "constant": "DEFAULT_INVENTORY_GLINT", "label": "物品栏附魔光",
             "value": parse_named_boolean(client_options_text, "DEFAULT_INVENTORY_GLINT")},
            {"key": "swordEnergyHighlight", "constant": "DEFAULT_SWORD_ENERGY_HIGHLIGHT", "label": "能量高光",
             "value": parse_named_boolean(client_options_text, "DEFAULT_SWORD_ENERGY_HIGHLIGHT")},
            {"key": "swordOutline", "constant": "DEFAULT_SWORD_OUTLINE", "label": "剑身发光轮廓",
             "value": parse_named_boolean(client_options_text, "DEFAULT_SWORD_OUTLINE")},
            {"key": "flameModuleVisual", "constant": "DEFAULT_FLAME_MODULE_VISUAL", "label": "烈焰边缘火星",
             "value": parse_named_boolean(client_options_text, "DEFAULT_FLAME_MODULE_VISUAL")},
            {"key": "lightningModuleVisual", "constant": "DEFAULT_LIGHTNING_MODULE_VISUAL", "label": "引雷短电弧",
             "value": parse_named_boolean(client_options_text, "DEFAULT_LIGHTNING_MODULE_VISUAL")},
            {"key": "poisonModuleVisual", "constant": "DEFAULT_POISON_MODULE_VISUAL", "label": "蚀毒雾状尾迹",
             "value": parse_named_boolean(client_options_text, "DEFAULT_POISON_MODULE_VISUAL")},
            {"key": "explosionModuleVisual", "constant": "DEFAULT_EXPLOSION_MODULE_VISUAL", "label": "爆裂橙红脉冲",
             "value": parse_named_boolean(client_options_text, "DEFAULT_EXPLOSION_MODULE_VISUAL")},
            {"key": "arrowRainModuleVisual", "constant": "DEFAULT_ARROW_RAIN_MODULE_VISUAL", "label": "箭雨细小风纹",
             "value": parse_named_boolean(client_options_text, "DEFAULT_ARROW_RAIN_MODULE_VISUAL")},
            {"key": "hitImpactVisual", "constant": "DEFAULT_HIT_IMPACT_VISUAL", "label": "命中闪光与冲击环",
             "value": parse_named_boolean(client_options_text, "DEFAULT_HIT_IMPACT_VISUAL")},
            {"key": "workbenchPreview", "constant": "DEFAULT_WORKBENCH_PREVIEW", "label": "工作台实时预览",
             "value": parse_named_boolean(client_options_text, "DEFAULT_WORKBENCH_PREVIEW")},
        ],
        "local": local,
        "worlds": find_worlds(local["modsDir"]),
        "recipes": recipes,
        "recipeCatalog": recipe_catalog(recipes, translations),
    }


def validate_number(raw: Any, minimum: float, maximum: float, integer: bool, label: str) -> float:
    if isinstance(raw, bool) or not isinstance(raw, (int, float)):
        raise PanelError(f"{label} 必须是数字")
    value = float(raw)
    if not (minimum <= value <= maximum):
        raise PanelError(f"{label} 必须位于 {minimum:g} 到 {maximum:g} 之间")
    if integer and not value.is_integer():
        raise PanelError(f"{label} 必须是整数")
    return value


def normalized_payload(payload: dict[str, Any], current: dict[str, Any]) -> dict[str, Any]:
    if not isinstance(payload, dict):
        raise PanelError("请求内容必须是对象")
    material_input = payload.get("materials")
    combat_input = payload.get("combat")
    effect_input = payload.get("effects")
    riding_input = payload.get("riding")
    presentation_input = payload.get("presentationDefaults")
    if not all(isinstance(group, dict) for group in
               (material_input, combat_input, effect_input, riding_input, presentation_input)):
        raise PanelError("缺少完整的数值分组")

    material_limits = current["materialLimits"]
    materials: dict[str, dict[str, Any]] = {}
    for item in current["materials"]:
        raw = material_input.get(item["key"])
        if not isinstance(raw, dict):
            raise PanelError(f"缺少材质 {item['label']}")
        color = str(raw.get("glowColor", "")).upper()
        if not re.fullmatch(r"#[0-9A-F]{6}", color):
            raise PanelError(f"{item['label']} 的尾迹颜色必须是 #RRGGBB")
        materials[item["key"]] = {
            "durability": int(validate_number(raw.get("durability"), *material_limits["durability"], True,
                                               item["label"] + "耐久")),
            "damage": validate_number(raw.get("damage"), *material_limits["damage"], False,
                                      item["label"] + "伤害"),
            "flightSpeed": validate_number(raw.get("flightSpeed"), *material_limits["flightSpeed"], False,
                                           item["label"] + "飞行速度"),
            "glowColor": color,
        }

    def validate_fields(source: list[dict[str, Any]], submitted: dict[str, Any]) -> dict[str, float]:
        result: dict[str, float] = {}
        for field in source:
            result[field["key"]] = validate_number(submitted.get(field["key"]), field["minimum"],
                                                    field["maximum"], field["integer"], field["label"])
        return result

    presentation_defaults: dict[str, bool] = {}
    for field in current["presentationDefaults"]:
        value = presentation_input.get(field["key"])
        if not isinstance(value, bool):
            raise PanelError(f"{field['label']} 必须是布尔值")
        presentation_defaults[field["key"]] = value

    return {
        "materials": materials,
        "combat": validate_fields(current["combat"], combat_input),
        "effects": validate_fields(current["effects"], effect_input),
        "riding": validate_fields(current["riding"], riding_input),
        "presentationDefaults": presentation_defaults,
    }


def backup_sources(paths: list[Path], reason: str) -> Path:
    stamp = datetime.now().strftime("%Y%m%d-%H%M%S-%f")
    destination = BACKUP_ROOT / f"{stamp}-{reason}"
    for path in paths:
        try:
            relative = path.resolve().relative_to(PROJECT_ROOT)
        except ValueError:
            digest = hashlib.sha1(str(path.parent).encode("utf-8")).hexdigest()[:10]
            relative = Path("external") / f"{path.parent.name}-{digest}" / path.name
        target = destination / relative
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(path, target)
    return destination


def save_source_values(payload: dict[str, Any]) -> dict[str, Any]:
    current = source_state()
    values = normalized_payload(payload, current)
    paths = [MATERIAL_FILE, EFFECT_FILE, SETTINGS_FILE, SERVER_RIDING_FILE, CLIENT_RIDING_FILE,
             INPUT_FILE, CLIENT_OPTIONS_FILE]
    backup_sources(paths, "save")

    material_text = read_text(MATERIAL_FILE)
    seen_materials: set[str] = set()

    def material_replacement(match: re.Match[str]) -> str:
        key = match.group(3)
        value = values["materials"].get(key)
        if value is None:
            return match.group(0)
        seen_materials.add(key)
        if (value["durability"] == int(match.group(6))
                and value["damage"] == float(match.group(7))
                and value["flightSpeed"] == float(match.group(8))
                and value["glowColor"][1:] == match.group(9).upper()):
            return match.group(0)
        return (f'{match.group(1)}{match.group(2)}("{key}", {match.group(4)}, {match.group(5)}, '
                f'{value["durability"]}, {java_number(value["damage"])}D, '
                f'{java_number(value["flightSpeed"])}D, 0x{value["glowColor"][1:]}){match.group(10)}')

    material_text = MATERIAL_PATTERN.sub(material_replacement, material_text)
    if seen_materials != set(values["materials"]):
        raise PanelError("材质源码结构发生变化，已拒绝不完整写入")

    effect_text = read_text(EFFECT_FILE)
    seen_effects: set[str] = set()

    def effect_replacement(match: re.Match[str]) -> str:
        key = match.group(4)
        if key not in values["effects"]:
            return match.group(0)
        seen_effects.add(key)
        if values["effects"][key] == float(match.group(5)):
            return match.group(0)
        integer = match.group(9) == "true"
        default = java_number(values["effects"][key], integer)
        return (f'{match.group(1)}{match.group(2)}(EffectConfigGroup.{match.group(3)}, "{key}", '
                f'{default}, {match.group(6)}, {match.group(7)}, {match.group(8)}, '
                f'{match.group(9)}){match.group(10)}')

    effect_text = EFFECT_PATTERN.sub(effect_replacement, effect_text)
    if seen_effects != set(values["effects"]):
        raise PanelError("效果参数源码结构发生变化，已拒绝不完整写入")

    settings_text = read_text(SETTINGS_FILE)
    settings_text = replace_named_number(settings_text, "DEFAULT_MINIMUM_DOCK_TICKS",
                                         values["combat"]["minimumDockTicks"], "int", "")
    settings_text = replace_named_number(settings_text, "DEFAULT_AUTOMATIC_RADIUS",
                                         values["combat"]["automaticTargetRadius"], "double", "D")
    settings_text = replace_named_number(settings_text, "DEFAULT_LOCK_RADIUS",
                                         values["combat"]["crosshairLockRadius"], "double", "D")

    server_riding = replace_named_number(read_text(SERVER_RIDING_FILE), "RIDING_FLIGHT_SPEED",
                                         values["riding"]["baseFlightSpeed"], "float", "F")
    client_riding = replace_named_number(read_text(CLIENT_RIDING_FILE), "ASCEND_SPEED",
                                         values["riding"]["ascendSpeed"], "double", "D")
    client_riding = replace_named_number(client_riding, "DESCEND_SPEED",
                                         -values["riding"]["descendSpeed"], "double", "D")
    input_text = replace_named_number(read_text(INPUT_FILE), "SWORD_RIDING_DOUBLE_TAP_MS",
                                      values["riding"]["doubleTapWindow"], "long", "L")
    client_options_text = read_text(CLIENT_OPTIONS_FILE)
    presentation_constants = {field["key"]: field["constant"] for field in current["presentationDefaults"]}
    for key, value in values["presentationDefaults"].items():
        client_options_text = replace_named_boolean(client_options_text, presentation_constants[key], value)

    atomic_write(MATERIAL_FILE, material_text)
    atomic_write(EFFECT_FILE, effect_text)
    atomic_write(SETTINGS_FILE, settings_text)
    atomic_write(SERVER_RIDING_FILE, server_riding)
    atomic_write(CLIENT_RIDING_FILE, client_riding)
    atomic_write(INPUT_FILE, input_text)
    atomic_write(CLIENT_OPTIONS_FILE, client_options_text)
    if "recipes" in payload:
        save_recipes(payload["recipes"])
    return source_state()


def update_toml_values(path: Path, values: dict[tuple[str, str], float]) -> None:
    if not path.exists():
        raise PanelError(f"测试世界配置不存在：{path}")
    text = read_text(path)
    lines = text.splitlines(keepends=True)
    section = ""
    touched: set[tuple[str, str]] = set()
    output: list[str] = []
    for line in lines:
        section_match = re.match(r'^\s*\[([^]]+)]\s*$', line)
        if section_match:
            section = section_match.group(1)
            output.append(line)
            continue
        value_match = re.match(r'^(\s*)([A-Za-z0-9_]+)(\s*=\s*)([-+]?\d+(?:\.\d+)?)(\s*)$', line.rstrip("\r\n"))
        key = (section, value_match.group(2)) if value_match else None
        if value_match and key in values:
            newline = "\r\n" if line.endswith("\r\n") else "\n" if line.endswith("\n") else ""
            output.append(value_match.group(1) + value_match.group(2) + value_match.group(3)
                          + java_number(values[key]) + value_match.group(5) + newline)
            touched.add(key)
        else:
            output.append(line)
    missing = set(values) - touched
    if missing:
        raise PanelError(f"世界配置缺少字段：{', '.join(f'{a}.{b}' for a, b in sorted(missing))}")
    atomic_write(path, "".join(output))


def sync_world(world_path: str, payload: dict[str, Any]) -> None:
    world = Path(world_path).expanduser().resolve()
    mods_dir = Path(load_local_settings()["modsDir"]).resolve()
    saves_dir = (mods_dir.parent / "saves").resolve()
    if saves_dir not in world.parents:
        raise PanelError("只能同步当前游戏目录 saves 下的世界")
    current = source_state()
    values = normalized_payload(payload, current)
    sword_toml = world / "serverconfig/swordflight-server.toml"
    effect_toml = world / "serverconfig/swordflight-effects-server.toml"
    backup_sources([sword_toml, effect_toml], "world-sync")
    sword_values: dict[tuple[str, str], float] = {}
    for key, item in values["materials"].items():
        sword_values[(key, "damage")] = item["damage"]
        sword_values[(key, "flightSpeed")] = item["flightSpeed"]
    effect_group = {item["key"]: item["group"].lower() for item in current["effects"]}
    effect_values = {(effect_group[key], key): value for key, value in values["effects"].items()}
    update_toml_values(sword_toml, sword_values)
    update_toml_values(effect_toml, effect_values)


@dataclass
class BuildState:
    running: bool = False
    phase: str = "idle"
    logs: list[str] = field(default_factory=list)
    success: bool | None = None
    version: str | None = None
    artifact: str | None = None
    error: str | None = None
    started_at: float | None = None
    finished_at: float | None = None

    def snapshot(self) -> dict[str, Any]:
        return {
            "running": self.running, "phase": self.phase, "logs": self.logs[-500:],
            "success": self.success, "version": self.version, "artifact": self.artifact,
            "error": self.error, "startedAt": self.started_at, "finishedAt": self.finished_at,
        }


BUILD = BuildState()
BUILD_LOCK = threading.Lock()


def build_log(message: str) -> None:
    with BUILD_LOCK:
        BUILD.logs.append(message.rstrip())


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for block in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest().upper()


def install_artifact(artifact: Path, mods_dir_text: str) -> Path:
    mods_dir = Path(mods_dir_text).expanduser().resolve()
    if not mods_dir.is_dir() or mods_dir.name.lower() != "mods":
        raise PanelError("mods 目录不存在或路径末级不是 mods")
    release_dir = (PROJECT_ROOT / "releases").resolve()
    if artifact.resolve().parent != release_dir:
        raise PanelError("构建产物不在 releases 目录")
    installed = sorted(mods_dir.glob("swordflight-*.jar"))
    for old in installed:
        if old.name == artifact.name:
            continue
        backup = release_dir / f"installed-backup-{old.name}"
        if backup.exists():
            stamp = datetime.now().strftime("%Y%m%d-%H%M%S")
            backup = release_dir / f"installed-backup-{stamp}-{old.name}"
        shutil.move(str(old), str(backup))
        build_log(f"已备份旧版：{backup.name}")
    destination = mods_dir / artifact.name
    shutil.copy2(artifact, destination)
    if sha256(destination) != sha256(artifact):
        raise PanelError("安装后的 JAR 哈希校验失败")
    return destination


def run_build(request: dict[str, Any]) -> None:
    previous_version: str | None = None
    try:
        with BUILD_LOCK:
            BUILD.phase = "saving"
        save_source_values(request["values"])
        local = load_local_settings()
        mods_dir = str(request.get("modsDir") or local["modsDir"])
        save_local_settings({"modsDir": mods_dir})
        if request.get("syncWorld"):
            build_log("同步测试世界中的材质与效果覆盖值……")
            sync_world(str(request.get("worldPath", "")), request["values"])

        previous_version = parse_version()
        new_version = bump_version(previous_version, str(request.get("increment", "patch")))
        release = PROJECT_ROOT / "releases" / f"swordflight-{new_version}.jar"
        if release.exists():
            raise PanelError(f"版本归档已存在：{release.name}")
        set_version(new_version)
        with BUILD_LOCK:
            BUILD.phase = "building"
            BUILD.version = new_version
        build_log(f"版本 {previous_version} → {new_version}")
        build_log("执行 Gradle 完整构建……")

        environment = os.environ.copy()
        java_home = Path("C:/Program Files/Microsoft/jdk-17.0.12.7-hotspot")
        if java_home.is_dir():
            environment["JAVA_HOME"] = str(java_home)
        command = [str(PROJECT_ROOT / "gradlew.bat"), "build"]
        creation_flags = getattr(subprocess, "CREATE_NO_WINDOW", 0)
        process = subprocess.Popen(command, cwd=PROJECT_ROOT, env=environment,
                                   stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                                   text=True, encoding="utf-8", errors="replace",
                                   creationflags=creation_flags)
        assert process.stdout is not None
        for line in process.stdout:
            build_log(line)
        exit_code = process.wait()
        if exit_code != 0:
            raise PanelError(f"Gradle 构建失败，退出码 {exit_code}")
        if not release.is_file():
            raise PanelError(f"构建成功但未找到标准归档 {release.name}")

        artifact_hash = sha256(release)
        build_log(f"归档完成：{release.name}")
        build_log(f"SHA-256：{artifact_hash}")
        installed_path: Path | None = None
        if request.get("install", True):
            with BUILD_LOCK:
                BUILD.phase = "installing"
            installed_path = install_artifact(release, mods_dir)
            build_log(f"已安装到：{installed_path}")

        with BUILD_LOCK:
            BUILD.running = False
            BUILD.phase = "complete"
            BUILD.success = True
            BUILD.artifact = str(release.resolve())
            BUILD.finished_at = time.time()
    except Exception as exc:  # Keep the UI alive and return a readable build failure.
        if previous_version is not None and BUILD.phase == "building":
            try:
                set_version(previous_version)
                build_log(f"构建失败，版本号已恢复为 {previous_version}")
            except Exception as rollback_error:
                build_log(f"版本号恢复失败：{rollback_error}")
        with BUILD_LOCK:
            BUILD.running = False
            BUILD.phase = "failed"
            BUILD.success = False
            BUILD.error = str(exc)
            BUILD.logs.append(f"错误：{exc}")
            BUILD.finished_at = time.time()


def begin_build(request: dict[str, Any]) -> None:
    with BUILD_LOCK:
        if BUILD.running:
            raise PanelError("已有构建任务正在运行")
        BUILD.running = True
        BUILD.phase = "queued"
        BUILD.logs = []
        BUILD.success = None
        BUILD.version = None
        BUILD.artifact = None
        BUILD.error = None
        BUILD.started_at = time.time()
        BUILD.finished_at = None
    threading.Thread(target=run_build, args=(request,), daemon=True, name="swordflight-build").start()


class ControlPanelHandler(BaseHTTPRequestHandler):
    server_version = "SwordflightControlPanel/1.0"

    @property
    def token(self) -> str:
        return self.server.control_token  # type: ignore[attr-defined]

    def log_message(self, format_string: str, *args: Any) -> None:
        print(f"[{self.log_date_time_string()}] {format_string % args}")

    def authorized(self) -> bool:
        query = parse_qs(urlparse(self.path).query)
        supplied = self.headers.get("X-Swordflight-Token") or (query.get("token") or [""])[0]
        return secrets.compare_digest(supplied, self.token)

    def send_json(self, value: Any, status: int = 200) -> None:
        encoded = json.dumps(value, ensure_ascii=False).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(encoded)))
        self.send_header("Cache-Control", "no-store")
        self.end_headers()
        self.wfile.write(encoded)

    def read_json(self) -> dict[str, Any]:
        try:
            length = int(self.headers.get("Content-Length", "0"))
        except ValueError as exc:
            raise PanelError("无效的请求长度") from exc
        if length <= 0 or length > MAX_REQUEST_BYTES:
            raise PanelError("请求为空或过大")
        try:
            value = json.loads(self.rfile.read(length).decode("utf-8"))
        except (UnicodeDecodeError, json.JSONDecodeError) as exc:
            raise PanelError("请求 JSON 无效") from exc
        if not isinstance(value, dict):
            raise PanelError("请求 JSON 必须是对象")
        return value

    def do_GET(self) -> None:
        parsed = urlparse(self.path)
        if parsed.path == "/":
            template = read_text(STATIC_DIR / "index.html")
            encoded = template.replace("__CONTROL_TOKEN__", self.token).encode("utf-8")
            self.send_response(200)
            self.send_header("Content-Type", "text/html; charset=utf-8")
            self.send_header("Content-Length", str(len(encoded)))
            self.send_header("Cache-Control", "no-store")
            self.end_headers()
            self.wfile.write(encoded)
            return
        if parsed.path in {"/app.css", "/app.js"}:
            path = STATIC_DIR / parsed.path[1:]
            encoded = path.read_bytes()
            content_type = "text/css; charset=utf-8" if path.suffix == ".css" else "text/javascript; charset=utf-8"
            self.send_response(200)
            self.send_header("Content-Type", content_type)
            self.send_header("Content-Length", str(len(encoded)))
            self.send_header("Cache-Control", "no-store")
            self.end_headers()
            self.wfile.write(encoded)
            return
        if not self.authorized():
            self.send_json({"error": "未授权"}, HTTPStatus.FORBIDDEN)
            return
        try:
            if parsed.path == "/api/state":
                self.send_json(source_state())
            elif parsed.path == "/api/build/status":
                with BUILD_LOCK:
                    self.send_json(BUILD.snapshot())
            else:
                self.send_json({"error": "接口不存在"}, HTTPStatus.NOT_FOUND)
        except PanelError as exc:
            self.send_json({"error": str(exc)}, HTTPStatus.BAD_REQUEST)

    def do_POST(self) -> None:
        if not self.authorized():
            self.send_json({"error": "未授权"}, HTTPStatus.FORBIDDEN)
            return
        try:
            request = self.read_json()
            path = urlparse(self.path).path
            if path == "/api/save":
                state = save_source_values(request.get("values", request))
                if isinstance(request.get("modsDir"), str):
                    save_local_settings({"modsDir": request["modsDir"]})
                self.send_json({"ok": True, "state": state})
            elif path == "/api/build":
                begin_build(request)
                self.send_json({"ok": True}, HTTPStatus.ACCEPTED)
            elif path == "/api/sync-world":
                sync_world(str(request.get("worldPath", "")), request.get("values", {}))
                self.send_json({"ok": True})
            else:
                self.send_json({"error": "接口不存在"}, HTTPStatus.NOT_FOUND)
        except PanelError as exc:
            self.send_json({"error": str(exc)}, HTTPStatus.BAD_REQUEST)
        except Exception as exc:
            self.send_json({"error": f"内部错误：{exc}"}, HTTPStatus.INTERNAL_SERVER_ERROR)


def main() -> None:
    parser = argparse.ArgumentParser(description="SwordFlight 本地可视化开发控制台")
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=0, help="0 表示自动选择空闲端口")
    parser.add_argument("--no-browser", action="store_true")
    args = parser.parse_args()
    if args.host not in {"127.0.0.1", "localhost", "::1"}:
        raise SystemExit("为安全起见，开发控制台只允许绑定本机回环地址")
    token = secrets.token_urlsafe(24)
    server = ThreadingHTTPServer((args.host, args.port), ControlPanelHandler)
    server.control_token = token  # type: ignore[attr-defined]
    host, port = server.server_address[:2]
    url = f"http://127.0.0.1:{port}/?token={token}"
    print("SwordFlight 可视化开发控制台")
    print(f"项目：{PROJECT_ROOT}")
    print(f"地址：{url}")
    print("按 Ctrl+C 关闭。")
    if not args.no_browser:
        threading.Timer(0.5, lambda: webbrowser.open(url)).start()
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        server.server_close()


if __name__ == "__main__":
    main()
