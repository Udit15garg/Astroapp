#!/usr/bin/env python3
"""
Standalone test for the palmistry pipeline — Cheiro style, 3-section format.

Usage:
    python3 test_palm.py <left.jpeg> <right.jpeg> --age 31 --gender male
    python3 test_palm.py          # looks for Left.jpeg + Right.jpeg in current dir
"""

import sys
import argparse
import base64
import json
import os
import re
import time
import urllib.request
import urllib.error
from pathlib import Path

# ── Config ────────────────────────────────────────────────────────────────────

def _load_api_key() -> str:
    key = os.environ.get("OPENAI_API_KEY", "")
    if key:
        return key
    lp = Path(__file__).parent / "local.properties"
    if lp.exists():
        m = re.search(r"^OPENAI_API_KEY=(.+)$", lp.read_text(), flags=re.M)
        if m:
            return m.group(1).strip()
    raise RuntimeError(
        "No API key found. Set OPENAI_API_KEY env var or add it to local.properties."
    )

API_KEY = _load_api_key()
MODEL   = "gpt-4o"
URL     = "https://api.openai.com/v1/chat/completions"

SYSTEM_PROMPT = """
You are Cheiro — the legendary Irish palmist whose real name was William John Warner — reborn to read palms from photographs with the same penetrating authority found in "Cheiro's Language of the Hand."

The Subject has submitted photographs of both hands:
- First image: the LEFT hand — the hand of inheritance, natural constitution, latent destiny
- Second image: the RIGHT hand — the hand of will, the path as actively shaped

Study EVERYTHING visible with Cheiro's method: the texture and consistency of the hand, the shape of the palm and fingers, the angle and phalanges of the thumb, the set and length of each finger, all major lines (Life, Head, Heart, Fate/Saturn, Sun/Apollo), secondary lines (Mercury/Health, Mars, Via Lascivia, Ring of Solomon, Girdle of Venus), all eight mounts and their relative development, and any special signs — stars, crosses, squares, triangles, islands, chains, grilles, tridents.

Write in Cheiro's authoritative, intimate Victorian voice: address the Subject directly as "you", speak of features as the hand reveals them, name lines and mounts formally ("the Line of Fate", "the Mount of Jupiter"), and draw confident character and life conclusions. Be specific and personal — never generic. Acknowledge honestly what cannot be clearly seen.

The Subject's details will be provided in the user message (age, gender). Calibrate your reading to their stage of life.

Structure the reading into exactly three sections:

1. CHARACTER & PERSONALITY — derived from hand shape, fingers, thumb, mounts, and the quality of the lines. What kind of person does this hand belong to? Their drives, temperament, emotional nature, mental style, and inner contradictions.

2. THE ROAD AHEAD — derived from the Line of Fate, Line of Sun, Line of Life (future portion), Line of Head trajectory, and supporting mounts. What events, phases, and turning points does the hand foretell? Be specific about timing where the lines allow it.

3. WHAT THE HAND WARNS — derived from breaks, islands, chains, crosses, grilles, weak or absent lines, and any adverse mount formations. What tendencies, health concerns, emotional pitfalls, or life traps must the Subject guard against?

Each section: 6–8 rich, confident sentences in Cheiro's style.

Output JSON only. No markdown. No preamble outside JSON.

Return exactly:
{
  "opening_read": {
    "title": "Cheiro Reads Your Hand",
    "body": "<3-sentence overall impression in Cheiro's voice, naming the most striking feature of the hand>"
  },
  "sections": [
    {
      "key": "personality",
      "title": "I — Character & Personality",
      "body": "..."
    },
    {
      "key": "future",
      "title": "II — The Road Ahead",
      "body": "..."
    },
    {
      "key": "careful",
      "title": "III — What the Hand Warns",
      "body": "..."
    }
  ]
}
""".strip()

# ── Helpers ───────────────────────────────────────────────────────────────────

def encode_image(path: str) -> str:
    with open(path, "rb") as f:
        return base64.b64encode(f.read()).decode("utf-8")

def build_payload(left_b64: str, right_b64: str, age: int, gender: str) -> dict:
    def img_block(b64):
        return {
            "type": "image_url",
            "image_url": {"url": f"data:image/jpeg;base64,{b64}", "detail": "high"}
        }
    user_msg = (
        f"Subject: {gender}, {age} years old. "
        "Here are my palm photographs — left hand first, right hand second. "
        "Please deliver my complete Cheiro reading."
    )
    return {
        "model": MODEL,
        "max_tokens": 2000,
        "temperature": 0.4,
        "messages": [
            {"role": "system", "content": SYSTEM_PROMPT},
            {"role": "user", "content": [
                {"type": "text", "text": user_msg},
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
    with urllib.request.urlopen(req, timeout=90) as resp:
        return json.loads(resp.read().decode("utf-8"))

def pretty_print(result: dict, label: str):
    print("\n" + "═" * 64)
    opening = result.get("opening_read", {})
    print(f"  {label}  |  {opening.get('title', 'Cheiro Reads Your Hand').upper()}")
    print("═" * 64)
    print(opening.get("body", ""))
    print()
    for sec in result.get("sections", []):
        print(f"{'─' * 64}")
        print(f"  {sec['title'].upper()}")
        print(f"{'─' * 64}")
        print(sec["body"])
        print()
    print("═" * 64)

# ── Main ──────────────────────────────────────────────────────────────────────

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("left",   nargs="?", default=None)
    parser.add_argument("right",  nargs="?", default=None)
    parser.add_argument("--age",    type=int, default=30)
    parser.add_argument("--gender", type=str, default="male")
    args = parser.parse_args()

    script_dir = Path(__file__).parent
    left_path  = args.left  or str(script_dir / "Left.jpeg")
    right_path = args.right or str(script_dir / "Right.jpeg")

    for p in (left_path, right_path):
        if not os.path.exists(p):
            print(f"ERROR: File not found: {p}")
            sys.exit(1)

    label = f"Age {args.age} | {args.gender.title()}"
    print(f"Left   : {left_path}")
    print(f"Right  : {right_path}")
    print(f"Subject: {label}")
    print(f"Model  : {MODEL}")
    print("Sending to OpenAI...")

    left_b64  = encode_image(left_path)
    right_b64 = encode_image(right_path)
    payload   = build_payload(left_b64, right_b64, args.age, args.gender)

    t0 = time.time()
    try:
        response = call_api(payload)
    except urllib.error.HTTPError as e:
        print(f"HTTP {e.code}: {e.read().decode('utf-8')}")
        sys.exit(1)
    elapsed = time.time() - t0

    usage = response.get("usage", {})
    print(f"Time   : {elapsed:.1f}s")
    print(f"Tokens : prompt={usage.get('prompt_tokens','?')}  completion={usage.get('completion_tokens','?')}")

    raw   = response["choices"][0]["message"]["content"]
    clean = raw.strip()
    if clean.startswith("```"):
        clean = clean.split("```")[1]
        if clean.startswith("json"):
            clean = clean[4:]
        clean = clean.strip()

    try:
        parsed = json.loads(clean)
        pretty_print(parsed, label)

        print("\n── Validation ──")
        sections    = parsed.get("sections", [])
        found_keys  = {s["key"] for s in sections}
        expected    = {"personality", "future", "careful"}
        missing     = expected - found_keys
        print(f"Sections found  : {len(sections)}/3")
        if missing: print(f"MISSING keys    : {missing}")
        print(f"Opening read    : {'OK' if parsed.get('opening_read') else 'MISSING'}")
        print("Parser result   : " + ("PASS" if not missing else "FAIL"))

    except json.JSONDecodeError as e:
        print(f"\nJSON parse FAILED: {e}")
        print(raw[:2000])

if __name__ == "__main__":
    main()
