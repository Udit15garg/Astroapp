#!/usr/bin/env python3
import argparse
import base64
import json
import re
import time
import urllib.error
import urllib.request
from pathlib import Path


ENDPOINT = "https://api.openai.com/v1/chat/completions"

VALIDATION_SYSTEM = """You are a strict palm image gatekeeper for palmistry.
Accept ONLY when the image clearly shows the inner palm (front side), open hand, fingers naturally extended, major palm lines visible, and good focus/light.
Reject if any of these occur: back of hand, claw/curled fingers, fist, side angle, multiple hands, heavy shadow, blur, tilt, cut-off palm, non-hand object.

Output EXACTLY 3 lines:
DECISION: VALID or INVALID
REASON: short concrete reason
INSTRUCTION: specific retake instruction with angle/portion guidance"""

ANALYSIS_SYSTEM = """You are an expert palmist giving precise, non-vague readings from a palm image.
Provide readings for exactly these 7 categories based on what you observe in the palm lines and features:
HEALTH, MARRIAGE, EDUCATION, BRAIN, CHILDREN, CAREER, LUCK

If the palm lines are not clearly readable, output EXACTLY this 3-line format:
STATUS: REUPLOAD
REASON: short concrete reason
INSTRUCTION: specific retake instruction (angle, distance, or portion)

If readable, output EXACTLY:
STATUS: OK
then EXACTLY 7 lines, each in this format:
CATEGORY:SCORE:One-sentence interpretation based on the palm lines."""


def load_api_key(local_properties: Path) -> str:
    text = local_properties.read_text(encoding="utf-8")
    m = re.search(r"^OPENAI_API_KEY=(.+)$", text, flags=re.M)
    if not m:
        raise RuntimeError("OPENAI_API_KEY not found in local.properties")
    return m.group(1).strip()


def call_vision(api_key: str, model: str, system: str, user: str, image_b64: str, detail: str, max_comp_tokens: int):
    body = {
        "model": model,
        "messages": [
            {"role": "system", "content": system},
            {
                "role": "user",
                "content": [
                    {"type": "text", "text": user},
                    {"type": "image_url", "image_url": {"url": f"data:image/jpeg;base64,{image_b64}", "detail": detail}},
                ],
            },
        ],
    }
    if model.startswith("gpt-5"):
        body["max_completion_tokens"] = max_comp_tokens
        body["reasoning_effort"] = "low"
    else:
        body["max_tokens"] = max_comp_tokens

    req = urllib.request.Request(
        ENDPOINT,
        data=json.dumps(body).encode("utf-8"),
        headers={"Authorization": f"Bearer {api_key}", "Content-Type": "application/json"},
    )

    started = time.time()
    try:
        with urllib.request.urlopen(req, timeout=90) as resp:
            payload = json.loads(resp.read().decode("utf-8", "ignore"))
            elapsed_ms = round((time.time() - started) * 1000)
            choice = payload.get("choices", [{}])[0]
            message = choice.get("message", {})
            content = message.get("content", "")
            if isinstance(content, list):
                content = "\n".join(
                    part.get("text", "") if isinstance(part, dict) else str(part)
                    for part in content
                )
            return {
                "ok": True,
                "elapsed_ms": elapsed_ms,
                "finish_reason": choice.get("finish_reason"),
                "content": (content or "").strip(),
                "refusal": (message.get("refusal") or "").strip(),
                "error": "",
            }
    except urllib.error.HTTPError as e:
        elapsed_ms = round((time.time() - started) * 1000)
        return {
            "ok": False,
            "elapsed_ms": elapsed_ms,
            "finish_reason": "http",
            "content": "",
            "refusal": "",
            "error": e.read().decode("utf-8", "ignore"),
        }


def preview(text: str, limit: int = 260) -> str:
    return text.replace("\n", " | ")[:limit]


def run_case(api_key: str, image_path: Path):
    b64 = base64.b64encode(image_path.read_bytes()).decode("utf-8")

    print(f"\n=== CASE: {image_path} ===")

    v = call_vision(
        api_key=api_key,
        model="gpt-4o-mini",
        system=VALIDATION_SYSTEM,
        user="Classify this image for palm reading readiness.",
        image_b64=b64,
        detail="low",
        max_comp_tokens=600,
    )
    print(f"[Validation] ok={v['ok']} ms={v['elapsed_ms']} finish={v['finish_reason']}")
    if v["ok"]:
        out = v["content"] or v["refusal"]
        print(f"[Validation] preview={preview(out)}")
    else:
        print(f"[Validation] error={preview(v['error'])}")

    a = call_vision(
        api_key=api_key,
        model="gpt-5",
        system=ANALYSIS_SYSTEM,
        user="Please analyze this palm and provide the 7-line reading.",
        image_b64=b64,
        detail="high",
        max_comp_tokens=1400,
    )
    print(f"[Analysis] ok={a['ok']} ms={a['elapsed_ms']} finish={a['finish_reason']}")
    if a["ok"]:
        out = a["content"] or a["refusal"]
        print(f"[Analysis] preview={preview(out)}")
    else:
        print(f"[Analysis] error={preview(a['error'])}")


def main():
    parser = argparse.ArgumentParser(description="Smoke test palmistry OpenAI vision requests.")
    parser.add_argument(
        "--image",
        action="append",
        default=[],
        help="Image path(s) to test. Can be specified multiple times.",
    )
    parser.add_argument(
        "--local-properties",
        default="local.properties",
        help="Path to local.properties",
    )
    args = parser.parse_args()

    key = load_api_key(Path(args.local_properties))
    if not args.image:
        defaults = [
            "/tmp/palm_wiki.jpg",
            "app/build/intermediates/packaged_res/debug/drawable/the_star.jpg",
        ]
        images = [Path(p) for p in defaults if Path(p).exists()]
    else:
        images = [Path(p) for p in args.image]

    if not images:
        raise RuntimeError("No test images found. Pass one or more --image paths.")

    for image in images:
        if not image.exists():
            print(f"SKIP missing: {image}")
            continue
        run_case(key, image)


if __name__ == "__main__":
    main()
