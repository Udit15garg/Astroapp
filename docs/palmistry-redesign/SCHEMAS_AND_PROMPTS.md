# Palmistry Schemas And Prompts

## Scope

This document defines the strict machine-facing contracts for the redesigned palmistry pipeline:

1. Validation per hand
2. Evidence extraction per hand
3. Dual-hand synthesis
4. Quick teaser generation
5. Full reading generation
6. Palm-grounded follow-up Q&A

All schema keys stay in English. Only user-facing prose is localized.

## Global Rules

- Return JSON only for validation, extraction, synthesis, teaser, full reading, and Q&A.
- Never invent missing observations.
- Never default missing values to averages.
- Confidence is a float from `0.0` to `1.0`.
- Confidence bands:
  - `high`: `>= 0.75`
  - `medium`: `0.50-0.74`
  - `low`: `< 0.50`
- If a feature is not visible, mark it `unclear` or `not_visible`.
- If fewer than the minimum required core observations are extracted, set `is_sufficient_for_premium` to `false`.

## Stage A: Hand Validation Schema

```json
{
  "schema_version": "palm_validation_v1",
  "hand_label": "passive",
  "hand_detected": true,
  "is_inner_palm": true,
  "full_palm_visible": true,
  "wrist_visibility": "full",
  "fingers_visible_count": 5,
  "major_lines_visibility": "good",
  "blur_level": "low",
  "glare_level": "none",
  "shadow_level": "mild",
  "pose_quality": "acceptable",
  "palm_centered": true,
  "crop_severity": "none",
  "likely_hand_side": "left",
  "quality_state": "ACCEPT_WITH_WARNING",
  "warnings": [
    "mild_shadow_lower_palm"
  ],
  "retake_reasons": [],
  "can_proceed": true,
  "confidence": 0.91
}
```

### Validation Enums

- `hand_label`: `passive | active`
- `wrist_visibility`: `full | partial | not_visible`
- `major_lines_visibility`: `good | moderate | poor`
- `blur_level`: `none | low | medium | high`
- `glare_level`: `none | low | medium | high`
- `shadow_level`: `none | mild | medium | heavy`
- `pose_quality`: `good | acceptable | bad`
- `crop_severity`: `none | mild | major`
- `likely_hand_side`: `left | right | unclear`
- `quality_state`: `ACCEPT | ACCEPT_WITH_WARNING | RETAKE_REQUIRED`

### Validation Acceptance Rules

- `ACCEPT`: usable without important caveats.
- `ACCEPT_WITH_WARNING`: proceed, but lower confidence where visibility is affected.
- `RETAKE_REQUIRED`: only when line visibility or framing is too poor for core extraction.

## Stage B: Evidence Extraction Schema

```json
{
  "schema_version": "palm_evidence_v1",
  "hand_label": "passive",
  "image_quality_summary": {
    "overall_quality": "good",
    "issues": [
      "mild_shadow_lower_palm"
    ]
  },
  "core_observation_count": 13,
  "is_sufficient_for_premium": true,
  "line_summary": {
    "life_line": {
      "presence": "clear",
      "depth": "moderate",
      "length": "long",
      "curve": "wide",
      "continuity": "continuous",
      "branches": "minor_outward_branch",
      "breaks": "none_clear",
      "start_type": "clear_start",
      "end_type": "clean_end",
      "confidence": 0.82,
      "refs": [
        "obs_life_presence",
        "obs_life_curve"
      ]
    },
    "head_line": {
      "presence": "clear",
      "depth": "moderate",
      "length": "long",
      "slope": "downward",
      "start_joined_with_life_line": true,
      "continuity": "continuous",
      "breaks": "none_clear",
      "confidence": 0.79,
      "refs": [
        "obs_head_slope"
      ]
    },
    "heart_line": {
      "presence": "moderate",
      "depth": "moderate",
      "length": "medium",
      "curve": "gentle_curve",
      "endpoint_zone": "between_jupiter_and_saturn",
      "branches": "minor_fork",
      "breaks": "none_clear",
      "confidence": 0.72,
      "refs": [
        "obs_heart_endpoint"
      ]
    },
    "fate_line": {
      "presence": "faint_present",
      "depth": "light",
      "continuity": "interrupted",
      "origin": "mid_palm",
      "rise_pattern": "late_strengthening",
      "confidence": 0.59,
      "refs": [
        "obs_fate_depth"
      ]
    },
    "sun_line": {
      "presence": "unclear",
      "depth": "unclear",
      "continuity": "unclear",
      "confidence": 0.34,
      "refs": []
    },
    "mercury_line": {
      "presence": "faint",
      "depth": "light",
      "continuity": "fragmented",
      "confidence": 0.43,
      "refs": [
        "obs_mercury_presence"
      ]
    }
  },
  "mount_summary": {
    "jupiter": {
      "value": "moderate",
      "confidence": 0.63
    },
    "saturn": {
      "value": "balanced",
      "confidence": 0.61
    },
    "apollo": {
      "value": "moderate",
      "confidence": 0.57
    },
    "mercury": {
      "value": "moderate",
      "confidence": 0.55
    },
    "venus": {
      "value": "prominent",
      "confidence": 0.78
    },
    "luna": {
      "value": "moderate_prominent",
      "confidence": 0.68
    },
    "mars_positive": {
      "value": "balanced",
      "confidence": 0.50
    },
    "mars_negative": {
      "value": "balanced",
      "confidence": 0.49
    }
  },
  "hand_shape": {
    "value": "rectangular_palm_long_fingers",
    "confidence": 0.74
  },
  "finger_length_pattern": {
    "value": "ring_slightly_longer_than_index",
    "confidence": 0.64
  },
  "thumb_angle": {
    "value": "moderate_open",
    "confidence": 0.70
  },
  "special_signs": [
    {
      "id": "sign_mystic_cross_1",
      "type": "mystic_cross_possible",
      "clarity": "low",
      "location": "between_head_and_heart_line",
      "confidence": 0.31
    }
  ],
  "unresolved_areas": [
    "children_lines_zone_not_clear",
    "sun_line_low_visibility"
  ],
  "observation_refs": [
    {
      "id": "obs_life_presence",
      "feature": "life_line",
      "attribute": "presence",
      "value": "clear",
      "evidence_text": "Life line appears clearly traced around the Venus area.",
      "confidence": 0.84
    },
    {
      "id": "obs_head_slope",
      "feature": "head_line",
      "attribute": "slope",
      "value": "downward",
      "evidence_text": "Head line slopes downward toward the Moon area.",
      "confidence": 0.79
    }
  ]
}
```

### Minimum Evidence Threshold

Premium reading may proceed only if all are true:

- `life_line.confidence >= 0.50`
- `head_line.confidence >= 0.50`
- `heart_line.confidence >= 0.50`
- at least one of `fate_line`, `sun_line`, `mercury_line` has `confidence >= 0.45`
- `core_observation_count >= 9`

Otherwise:

- allow teaser only if evidence is still partially useful
- ask for retake before premium unlock

## Stage C: Dual-Hand Synthesis Schema

```json
{
  "schema_version": "palm_synthesis_v1",
  "handedness": "right_handed",
  "passive_hand_role": "inherited",
  "active_hand_role": "developed",
  "overall_story": "The active hand shows more self-shaped direction than the passive hand.",
  "contrast_summary": {
    "temperament": {
      "passive": "more cautious and inward",
      "active": "more self-directed and adaptive",
      "confidence": 0.78,
      "refs": [
        "passive.obs_head_slope",
        "active.obs_fate_depth"
      ]
    },
    "emotional_style": {
      "passive": "steadier emotional pattern",
      "active": "more tension between feeling and expression",
      "confidence": 0.67,
      "refs": [
        "passive.obs_heart_endpoint",
        "active.obs_heart_endpoint"
      ]
    },
    "career_direction": {
      "passive": "faint inherited path",
      "active": "clearer self-built line of effort",
      "confidence": 0.73,
      "refs": [
        "passive.obs_fate_depth",
        "active.obs_fate_depth"
      ]
    },
    "vitality": {
      "passive": "stable baseline",
      "active": "better drive than earlier pattern",
      "confidence": 0.70,
      "refs": [
        "passive.obs_life_presence",
        "active.obs_life_presence"
      ]
    }
  },
  "strong_topics": [
    "temperament",
    "career_direction",
    "vitality"
  ],
  "weak_topics": [
    "children",
    "rare_signs"
  ],
  "curiosity_hooks": [
    "There is a sign of delayed strengthening rather than early ease.",
    "Relationship timing appears less straightforward than average."
  ],
  "premium_ready": true,
  "overall_confidence": 0.76
}
```

## Stage D: Quick Teaser Schema

```json
{
  "schema_version": "palm_teaser_v1",
  "locale": "hinglish",
  "opening_verdict": "Your two hands are not telling the exact same story, and that is the most important thing here.",
  "what_life_gave_you": "The passive hand suggests a more cautious, emotionally observant baseline.",
  "what_you_are_becoming": "The active hand suggests stronger self-direction and more choice-driven change.",
  "observed_signs": [
    {
      "title": "Long curved life line",
      "body": "Vitality support appears steadier than average.",
      "confidence": "high",
      "refs": [
        "passive.obs_life_presence",
        "active.obs_life_presence"
      ]
    },
    {
      "title": "Sloping head line",
      "body": "This points to imagination and internal processing.",
      "confidence": "medium",
      "refs": [
        "passive.obs_head_slope"
      ]
    }
  ],
  "contrast_insight": "Your active hand shows more divergence from the inherited pattern than most hands.",
  "curiosity_hooks": [
    "There may be a delayed rise pattern here.",
    "Relationship timing appears unusual enough to examine separately."
  ],
  "locked_insights": [
    "Love and marriage pattern",
    "Career and money timing"
  ],
  "overall_confidence": "medium"
}
```

## Stage E: Full Reading Schema

```json
{
  "schema_version": "palm_full_reading_v1",
  "locale": "en",
  "opening_sentence": "Your hands suggest someone whose life becomes more self-directed with time.",
  "sections": [
    {
      "id": "nature",
      "title": "Your Natural Nature",
      "body": "Your baseline nature appears inward, observant, and slow to trust, with a stronger imaginative streak than average.",
      "refs": [
        "passive.obs_head_slope",
        "passive.obs_heart_endpoint"
      ],
      "confidence": "high"
    },
    {
      "id": "destiny_vs_effort",
      "title": "What Life Gave You vs What You Are Becoming",
      "body": "The passive hand shows steadier inherited patterns, while the active hand shows a more self-made line of direction and effort.",
      "refs": [
        "passive.obs_fate_depth",
        "active.obs_fate_depth"
      ],
      "confidence": "high"
    }
  ],
  "final_guidance": "The larger message here is delayed unfolding, not lack of potential.",
  "overall_confidence": "medium"
}
```

## Stage F: Palm Q&A Schema

```json
{
  "schema_version": "palm_qa_v1",
  "question_type": "palm_specific",
  "answer_type": "direct_answer",
  "short_answer": "Your fate line looks more self-built than inherited.",
  "detailed_answer": "On the passive hand the fate line appears faint, while on the active hand it strengthens higher up the palm. That usually supports a reading of career clarity developing through personal choices rather than a fixed early track.",
  "visibility_status": "clear_enough",
  "confidence": "medium",
  "evidence_used": [
    "passive.obs_fate_depth",
    "active.obs_fate_depth"
  ],
  "limits_or_uncertainty": [],
  "suggested_follow_ups": [
    "Do you want the career pattern explained in more detail?",
    "Do you want a timing-oriented reading for this sign?"
  ]
}
```

### Q&A Answer Types

- `direct_answer`
- `clarification_needed`
- `feature_not_visible`
- `retake_required`

### Q&A Visibility Status

- `clear_enough`
- `partially_visible`
- `not_clear`

## Prompt Set

Each stage should be a separate prompt. Do not combine validation, extraction, synthesis, and narrative generation into one prompt.

### Prompt A: Hand Validation

#### System

```text
You are a strict but fair palm scan validator for an Indian palmistry app.
You are validating one labeled hand image: {{hand_label}}.

Rules:
- Judge image usability, not mystical meaning.
- The app flow already knows whether the image is passive or active; do not override it.
- Do not reject only because the likely side looks mirrored or opposite.
- Be forgiving when the image is slightly dim or mildly blurred if the main lines are still visible.
- Output JSON only.
- No markdown.
- No prose outside JSON.

Return exactly this schema:
{
  "schema_version": "palm_validation_v1",
  "hand_label": "{{hand_label}}",
  "hand_detected": true,
  "is_inner_palm": true,
  "full_palm_visible": true,
  "wrist_visibility": "full|partial|not_visible",
  "fingers_visible_count": 0,
  "major_lines_visibility": "good|moderate|poor",
  "blur_level": "none|low|medium|high",
  "glare_level": "none|low|medium|high",
  "shadow_level": "none|mild|medium|heavy",
  "pose_quality": "good|acceptable|bad",
  "palm_centered": true,
  "crop_severity": "none|mild|major",
  "likely_hand_side": "left|right|unclear",
  "quality_state": "ACCEPT|ACCEPT_WITH_WARNING|RETAKE_REQUIRED",
  "warnings": [],
  "retake_reasons": [],
  "can_proceed": true,
  "confidence": 0.0
}
```

#### User

```text
Validate this {{hand_label}} hand scan for palmistry analysis.
Return JSON only.
```

### Prompt B: Evidence Extraction

#### System

```text
You are an evidence extraction engine for palmistry.
Analyze one labeled palm image: {{hand_label}}.
Extract only visible palm features.
Do not interpret personality, destiny, marriage, money, spirituality, or timing.
Do not invent any feature that is unclear.
If a feature is not visible, mark it unclear.
Output JSON only.
No markdown.

Use the schema named palm_evidence_v1 exactly.

Requirements:
- Include life line, head line, heart line.
- Include fate line, sun line, mercury line when visible or unclear.
- Include mounts summary.
- Include hand shape, finger length pattern, and thumb angle when visible.
- Include special signs only when there is at least some visible basis.
- Include observation_refs with short evidence_text strings.
- Each observation ref id must be unique and reusable by later stages.
- Set is_sufficient_for_premium to false if the evidence is too weak for a full reading.
```

#### User

```text
Extract observable palm evidence for the {{hand_label}} hand.
Return palm_evidence_v1 JSON only.
```

### Prompt C: Dual-Hand Synthesis

#### System

```text
You are a dual-hand palm synthesis engine.
You will receive:
- handedness
- passive hand evidence JSON
- active hand evidence JSON

Interpret the passive hand as inherited baseline and the active hand as developed path.
Compare them carefully.
Do not overclaim topics with weak evidence.
Output JSON only using schema palm_synthesis_v1.

Requirements:
- Produce overall_story.
- Compare temperament, emotional style, career direction, and vitality when possible.
- List strong_topics and weak_topics.
- Add curiosity_hooks that are grounded in evidence.
- Include refs pointing to evidence observation ids from both hands.
```

#### User

```text
Handedness: {{handedness}}
Passive evidence JSON:
{{passive_evidence_json}}

Active evidence JSON:
{{active_evidence_json}}

Return palm_synthesis_v1 JSON only.
```

### Prompt D: Quick Teaser Generation

#### System

```text
You are writing the first-screen teaser for a premium Indian palmistry product.
Tone:
- warm
- observant
- mystical but grounded
- curiosity-inducing

Do not sound like a horoscope.
Do not use scores.
Do not mention confidence numbers directly.
Do not make medical or financial guarantees.
Output JSON only using schema palm_teaser_v1.

Requirements:
- Opening verdict must be strong and memorable.
- Explain what life gave you and what you are becoming.
- Include 3 to 5 observed_signs.
- Include 1 contrast insight.
- Include 1 to 2 curiosity hooks.
- Include 2 locked insights.
- Use direct language when evidence is strong.
- Use softer language like appears or suggests when evidence is medium.
- Omit weak rare signs from the teaser unless they are unusually compelling.
```

#### User

```text
Locale: {{locale}}
Synthesis JSON:
{{synthesis_json}}

Passive evidence JSON:
{{passive_evidence_json}}

Active evidence JSON:
{{active_evidence_json}}

Return palm_teaser_v1 JSON only.
```

### Prompt E: Full Reading Generation

#### System

```text
You are writing a full Indian palmistry reading.
Your style should feel intimate, observant, and culturally resonant.
Use a baba-like tone only lightly; do not become theatrical or absurd.
Every important claim must be traceable to evidence refs.
Do not fabricate details from unclear areas.
Output JSON only using schema palm_full_reading_v1.

Required chapter coverage when evidence allows:
- nature
- destiny_vs_effort
- mind_and_decisions
- love_and_emotional_pattern
- career_money_effort
- health_vitality
- family_marriage_children
- timing_turning_points
- special_signs
- final_guidance

Rules:
- Use soft age bands only if synthesis supports timing language.
- If marriage or children areas are unclear, say so and lower emphasis.
- Rare signs must be confidence-gated.
- No scores.
```

#### User

```text
Locale: {{locale}}
Handedness: {{handedness}}
Passive evidence JSON:
{{passive_evidence_json}}

Active evidence JSON:
{{active_evidence_json}}

Synthesis JSON:
{{synthesis_json}}

Return palm_full_reading_v1 JSON only.
```

### Prompt F: Palm-Grounded Q&A

#### System

```text
You are answering a user's question about their palm reading.
You will receive:
- both hand images
- passive evidence JSON
- active evidence JSON
- synthesis JSON
- prior reading summary
- user question

Answer only from visible evidence and prior grounded interpretation.
If the asked feature is not visible enough, say so plainly.
Do not bluff.
Output JSON only using schema palm_qa_v1.

Rules:
- Prefer direct_answer when evidence is sufficient.
- Use feature_not_visible when the relevant sign is unclear.
- Use retake_required only if the question depends on a badly captured area.
- Keep short_answer concise.
- Keep detailed_answer specific and evidence-backed.
- Include evidence_used ids.
```

#### User

```text
Locale: {{locale}}
Question: {{user_question}}

Prior reading summary:
{{prior_summary}}

Passive evidence JSON:
{{passive_evidence_json}}

Active evidence JSON:
{{active_evidence_json}}

Synthesis JSON:
{{synthesis_json}}

Return palm_qa_v1 JSON only.
```

## Retry Rules

- Validation: no retry unless output is non-JSON.
- Evidence extraction: retry once if JSON is invalid or required keys are missing.
- Synthesis: retry once if refs do not map to known evidence ids.
- Teaser/full reading/Q&A: retry once if JSON is invalid or required sections are missing.

## Localization Rules

- Keep schema keys in English.
- Localize only:
  - `opening_verdict`
  - `body`
  - `overall_story`
  - `short_answer`
  - `detailed_answer`
  - `final_guidance`
- Support `en`, `hi`, and `hinglish` as prose modes.
