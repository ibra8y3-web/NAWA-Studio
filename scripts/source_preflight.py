#!/usr/bin/env python3
"""Source-only checks for NAWA Studio. Does not claim to compile or test Android."""
from pathlib import Path
import json
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
REQUIRED = [
    "settings.gradle.kts", "build.gradle.kts", "app/build.gradle.kts",
    "app/src/main/AndroidManifest.xml",
    "app/src/main/java/com/nawa/studio/MainActivity.kt",
    "app/src/main/java/com/nawa/studio/core/NawaParser.kt",
    "app/src/main/java/com/nawa/studio/core/ProjectRepository.kt",
    "app/src/main/java/com/nawa/studio/core/NawaSourceEditor.kt",
    "app/src/main/java/com/nawa/studio/core/NawaAndroidProjectGenerator.kt",
    "app/src/test/java/com/nawa/studio/core/NawaParserTest.kt",
    "app/src/test/java/com/nawa/studio/core/NawaAndroidProjectGeneratorTest.kt",
    ".github/workflows/android.yml",
]
errors = []
for relative in REQUIRED:
    path = ROOT / relative
    if not path.is_file() or path.stat().st_size == 0:
        errors.append(f"MISSING: {relative}")

json_count = 0
xml_count = 0
for path in ROOT.rglob("*"):
    if not path.is_file() or any(part in {".git", "build"} for part in path.parts):
        continue
    try:
        if path.suffix.lower() == ".json":
            json.loads(path.read_text(encoding="utf-8"))
            json_count += 1
        elif path.suffix.lower() == ".xml":
            ET.parse(path)
            xml_count += 1
    except Exception as exc:
        errors.append(f"INVALID {path.relative_to(ROOT)}: {exc}")

workflow = ROOT / ".github/workflows/android.yml"
if workflow.exists():
    content = workflow.read_text(encoding="utf-8")
    for needle in ("testDebugUnitTest assembleDebug", "app-debug.apk", "apksigner", "upload-artifact"):
        if needle not in content:
            errors.append(f"WORKFLOW MISSING EXPECTED STEP: {needle}")

if errors:
    print("NAWA source preflight: FAILED")
    print("\n".join(f"- {error}" for error in errors))
    sys.exit(1)
print("NAWA source preflight: PASSED")
print(f"Required source files: {len(REQUIRED)}")
print(f"Valid JSON files: {json_count}")
print(f"Valid XML files: {xml_count}")
print("Scope: source structure + JSON/XML syntax + CI workflow markers only.")
print("Not checked: Android Gradle compilation, APK generation, installation, or device runtime.")
