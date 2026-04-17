#!/usr/bin/env python3
"""
Standalone test for the palmistry pipeline.
Mirrors exactly what the Android app sends to gpt-4o.

Usage:
    python3 test_palm.py <left.jpeg> <right.jpeg>
    python3 test_palm.py          # looks for Left.jpeg + Right.jpeg in current dir
"""

import sys
import base64
import json
import os
import urllib.request
import urllib.error
from pathlib import Path

# ── Config ────────────────────────────────────────────────────────────────────

API_KEY = "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"
MODEL   = "gpt-4o"
URL     = "https://api.openai.com/v1/chat/completions"

SYSTEM_PROMPT = """
You are a master palmist delivering a comprehensive palm reading directly from hand photos.

The user has sent photos of both hands:
- First image: LEFT hand (inherited traits, natural potential, destiny)
- Second image: RIGHT hand (developed path, present reality, effort)
- Additional images (if any): close-up details of the same hands

Analyze EVERYTHING visible: hand shape, finger lengths and proportions, thumb angle, all major and minor lines, all eight mounts, and any special formations or symbols.

Write user-facing narrative text in English. Keep all JSON keys and enum values in English.

Tone: warm, direct, honest, grounded. Like a trusted palmist who sees both gifts and challenges.
- No generic filler.
- No scores or numbers.
- Mention weaknesses honestly when visible.
- If a feature is unclear in the photo, note it briefly and move on.
- Each module: 4-5 tight sentences.
- Output JSON only. No markdown.

Return exactly:
{
  "opening_read": {
    "title": "Your Palm Reading",
    "body": "<3-sentence overall impression combining both hands>"
  },
  "modules": [
    { "key": "palm_shape",    "title": "Palm Shape & Hand Type",      "summary": "..." },
    { "key": "fingers",       "title": "Your Fingers",                "summary": "..." },
    { "key": "major_lines",   "title": "Life, Head & Heart Lines",    "summary": "..." },
    { "key": "secondary_lines","title": "Fate, Sun & Other Lines",    "summary": "..." },
    { "key": "mounts",        "title": "The Mounts",                  "summary": "..." },
    { "key": "symbols",       "title": "Symbols & Special Marks",     "summary": "..." },
    { "key": "love_marriage", "title": "Love & Marriage",             "summary": "..." },
    { "key": "career_money",  "title": "Career & Money",              "summary": "..." },
    { "key": "health",        "title": "Health",                      "summary": "..." },
    { "key": "luck",          "title": "Luck & Timing",               "summary": "..." },
    { "key": "left_vs_right", "title": "Left vs Right — Nature vs Path", "summary": "..." }
  ],
  "followup_prompts": [
    "Ask about love & marriage in detail",
    "Ask about career path",
    "Ask about money & finances",
    "Ask about health signs",
    "Ask about special symbols",
    "Ask about timing & turning points"
  ]
}
""".strip()

USER_MESSAGE = "Here are my palm photos. The first is my left hand, the second is my right hand. Please give me a complete comprehensive palm reading covering all aspects."

# ── Helpers ───────────────────────────────────────────────────────────────────

def encode_image(path: str) -> str:
    with open(path, "rb") as f:
        return base64.b64encode(f.read()).decode("utf-8")

def build_payload(left_b64: str, right_b64: str) -> dict:
    def img_block(b64):
        return {
            "type": "image_url",
            "image_url": {"url": f"data:image/jpeg;base64,{b64}", "detail": "high"}
        }
    return {
        "model": MODEL,
        "max_tokens": 1600,
        "temperature": 0.3,
        "messages": [
            {"role": "system", "content": SYSTEM_PROMPT},
            {"role": "user", "content": [
                {"type": "text", "text": USER_MESSAGE},
                img_block(left_b64),
                img_block(right_b64),
            ]}
        ]
    }

def call_api(payload: dict) -> dict:
    data = json.dumps(payload).encode("utf-8")
    req = urllib.request.Request(
        URL,
        data=data,
        headers={
            "Content-Type": "application/json",
            "Authorization": f"Bearer {API_KEY}",
        },
        method="POST"
    )
    with urllib.request.urlopen(req, timeout=60) as resp:
        return json.loads(resp.read().decode("utf-8"))

def pretty_print(result: dict):
    print("\n" + "═" * 60)
    opening = result.get("opening_read", {})
    print(f"  {opening.get('title', 'Palm Reading').upper()}")
    print("═" * 60)
    print(opening.get("body", ""))
    print()
    for mod in result.get("modules", []):
        print(f"── {mod['title']} ──")
        print(mod["summary"])
        print()
    prompts = result.get("followup_prompts", [])
    if prompts:
        print("── Follow-up Prompts ──")
        for p in prompts:
            print(f"  • {p}")
    print("═" * 60)

# ── Main ──────────────────────────────────────────────────────────────────────

def main():
    script_dir = Path(__file__).parent

    if len(sys.argv) == 3:
        left_path, right_path = sys.argv[1], sys.argv[2]
    else:
        left_path  = str(script_dir / "Left.jpeg")
        right_path = str(script_dir / "Right.jpeg")

    for p in (left_path, right_path):
        if not os.path.exists(p):
            print(f"ERROR: File not found: {p}")
            print("Usage: python3 test_palm.py <left.jpeg> <right.jpeg>")
            sys.exit(1)

    print(f"Left  : {left_path}")
    print(f"Right : {right_path}")
    print(f"Model : {MODEL}")
    print("Sending to OpenAI... (may take 10-20s)")

    left_b64  = encode_image(left_path)
    right_b64 = encode_image(right_path)

    payload = build_payload(left_b64, right_b64)

    try:
        response = call_api(payload)
    except urllib.error.HTTPError as e:
        body = e.read().decode("utf-8")
        print(f"HTTP {e.code}: {body}")
        sys.exit(1)

    usage = response.get("usage", {})
    print(f"Tokens: prompt={usage.get('prompt_tokens','?')}  completion={usage.get('completion_tokens','?')}")

    raw = response["choices"][0]["message"]["content"]

    # strip markdown fences if present
    clean = raw.strip()
    if clean.startswith("```"):
        clean = clean.split("```")[1]
        if clean.startswith("json"):
            clean = clean[4:]
        clean = clean.strip()

    try:
        parsed = json.loads(clean)
        pretty_print(parsed)

        # Validate structure matches what the app expects
        print("\n── Validation ──")
        modules = parsed.get("modules", [])
        expected_keys = {"palm_shape","fingers","major_lines","secondary_lines","mounts",
                         "symbols","love_marriage","career_money","health","luck","left_vs_right"}
        found_keys = {m["key"] for m in modules}
        missing = expected_keys - found_keys
        extra   = found_keys - expected_keys
        print(f"Modules found   : {len(modules)}/11")
        if missing: print(f"MISSING keys    : {missing}")
        if extra:   print(f"Extra keys      : {extra}")
        print(f"Opening read    : {'OK' if parsed.get('opening_read') else 'MISSING'}")
        print(f"Followup prompts: {len(parsed.get('followup_prompts', []))}")
        print("Parser result   : PASS" if not missing else "Parser result   : FAIL")

    except json.JSONDecodeError as e:
        print(f"\nJSON parse FAILED: {e}")
        print("Raw response:")
        print(raw[:2000])

if __name__ == "__main__":
    main()
