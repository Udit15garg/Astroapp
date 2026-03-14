# Palmistry Implementation Plan

## Outcome

Replace the current single-image, seven-score palmistry flow with a dual-hand, evidence-first pipeline that:

- feels visually grounded
- explains what was actually seen
- uses passive vs active hand contrast
- supports teaser, full reading, and image-grounded Q&A
- avoids repetitive scorecard output

## Current To Target Mapping

| Current | Replace With |
|---|---|
| `ScanActivity` single-hand flow | dual-hand capture flow with handedness selection |
| strict pass/fail validation | `ACCEPT / ACCEPT_WITH_WARNING / RETAKE_REQUIRED` |
| one prompt for final reading | separate validation, extraction, synthesis, teaser, reading, Q&A prompts |
| `PalmReading(category, score, interpretation)` | evidence JSON + synthesis JSON + teaser/full reading JSON |
| score bars UI | narrative cards + evidence snippets + contrast panel |
| non-image-grounded palm Q&A | image-grounded Q&A with both hands every time |
| parser normalization with fake defaults | strict JSON parsing + retry + low-confidence fallback |

## Recommended Kotlin Refactor

### New Data Models

Create dedicated models under a new package such as `com.palmreader.astro.palmistry`.

- `PalmHandLabel`
  - `PASSIVE`
  - `ACTIVE`
- `PalmHandedness`
  - `RIGHT_HANDED`
  - `LEFT_HANDED`
  - `NOT_SURE`
- `PalmValidationResult`
- `PalmEvidence`
- `PalmSynthesis`
- `PalmTeaser`
- `PalmFullReading`
- `PalmQaAnswer`
- `PalmSession`

### New Service Layer

- `PalmistryCaptureRepository`
  - stores original and resized image URIs for both hands
- `PalmistryQualityGate`
  - local CV heuristics
  - returns structured local warnings instead of a single pass/fail
- `PalmistryVisionRepository`
  - calls validation, extraction, synthesis, teaser, full read, and Q&A stages
- `PalmistryJsonParser`
  - strict parse
  - schema version checks
  - required-key checks
  - retry signal generation
- `PalmistrySessionStore`
  - persists `PalmSession`
  - supports teaser unlock, paid full reading, and Q&A history

### Existing Files To Retire Or Shrink

- [ScanActivity.kt](/Users/uditgarg/Astroapp/app/src/main/java/com/palmreader/astro/ScanActivity.kt)
  - split into capture flow + orchestration
- [ResultActivity.kt](/Users/uditgarg/Astroapp/app/src/main/java/com/palmreader/astro/ResultActivity.kt)
  - replace scorecards with narrative result screen
- [PromptTemplates.kt](/Users/uditgarg/Astroapp/app/src/main/java/com/palmreader/astro/api/PromptTemplates.kt)
  - replace current palmistry prompt pair with the staged prompt family
- [PalmReading.kt](/Users/uditgarg/Astroapp/app/src/main/java/com/palmreader/astro/PalmReading.kt)
  - keep only for backwards compatibility during migration

## Suggested Screen Flow

### 1. Palmistry Welcome Screen

- headline
- two-hand explanation
- short note about inherited vs active hand
- CTA: `Start palm scan`

### 2. Handedness Screen

- options:
  - `Right-handed`
  - `Left-handed`
  - `Not sure`
- help text:
  - `This helps us map your passive and active hand correctly.`

### 3. Dual-Hand Capture Screen

Two sequential steps:

1. capture passive hand
2. capture active hand

Each step includes:

- hand outline guide
- small sample image
- live hints
- `Retake`
- `Use photo`

### 4. Analysis Loading Screen

Use staged loader copy:

1. `Reading major lines...`
2. `Comparing both hands...`
3. `Checking mounts and finger balance...`
4. `Looking for special signs and timing shifts...`
5. `Preparing your first insight...`

### 5. Teaser Result Screen

No score bars.

Top-to-bottom layout:

1. opening verdict card
2. `What life gave you` card
3. `What you are becoming` card
4. observed signs list
5. contrast insight card
6. locked insights
7. CTA row

CTA row:

- `Unlock full reading`
- `Ask about love`
- `Ask about career`
- `Ask my own question`

### 6. Full Reading Screen

Use accordions or tabs:

- Nature
- Destiny vs Effort
- Love
- Career
- Health
- Timing
- Rare Signs
- Final Guidance

### 7. Q&A Screen

- persistent chips for common topics
- free-text question input
- answer card
- optional evidence drawer

## Result Screen Wireframe

```text
+----------------------------------------------------+
| Opening verdict                                    |
| "Your two hands are not telling the same story..." |
+----------------------------------------------------+
| Contrast                                           |
| What life gave you                                 |
| What you are becoming                              |
+----------------------------------------------------+
| What stood out in your hands                       |
| - Long curved life line                            |
| - Sloping head line                                |
| - Emerging fate line                               |
| - Prominent Venus mount                            |
+----------------------------------------------------+
| Why we said this                                   |
| Based on active fate line, passive head line, etc. |
+----------------------------------------------------+
| Locked deeper insights                             |
| [Love and marriage] [Career and money]             |
+----------------------------------------------------+
| CTA                                                |
| [Unlock full reading] [Ask question]               |
+----------------------------------------------------+
```

## Parser Rules

### Non-Negotiable

- parse JSON only
- reject non-JSON
- no regex-based category salvage for the new flow
- no fallback score assignment
- no fabricated category padding
- retry once on malformed model output
- persist raw model response for debug logs

### Parse Failure Handling

- validation invalid JSON:
  - retry once
  - if still invalid, show generic scan error
- extraction invalid JSON:
  - retry once
  - if still invalid, mark hand low-confidence and request retake
- synthesis invalid JSON:
  - retry once
  - if still invalid, allow teaser only from per-hand evidence if possible
- teaser/full reading/Q&A invalid JSON:
  - retry once
  - if still invalid, show service unavailable with credit restore where applicable

## Pipeline Pseudocode

```text
select_handedness()
capture(passive_hand)
capture(active_hand)

passive_validation = validate(passive_image)
active_validation = validate(active_image)

if either quality_state == RETAKE_REQUIRED:
  request_retake()

passive_evidence = extract(passive_image)
active_evidence = extract(active_image)

if extraction weak:
  allow teaser if enough evidence
  block premium and suggest retake

synthesis = compare(passive_evidence, active_evidence, handedness)
teaser = generate_teaser(passive_evidence, active_evidence, synthesis)

show_teaser()

if user_unlocks_full:
  full_reading = generate_full_reading(passive_evidence, active_evidence, synthesis)

if user_asks_question:
  answer = answer_with_images(passive_image, active_image, passive_evidence, active_evidence, synthesis, prior_summary, question)
```

## Suggested File-Level Changes

### Phase 1: Foundation

- add `palmistry/` package for new models and repositories
- add strict JSON schema-aware parser utilities
- add dual-hand session model
- add handedness selection state
- update image storage to keep both original and inference-sized copies

### Phase 2: Orchestration

- replace current single-photo `ScanActivity` path
- add dual capture state machine
- integrate per-hand validation and evidence extraction
- store raw JSON in session for debugging

### Phase 3: UI

- replace scorecard layout in [item_reading.xml](/Users/uditgarg/Astroapp/app/src/main/res/layout/item_reading.xml)
- replace `ResultActivity` render path
- add teaser screen components
- add evidence drawer
- add topic chips and palm-grounded Q&A

### Phase 4: Monetization

- teaser free
- full reading paid
- topic packs optional
- 3-question pack optional

## Monetization Copy

### Free Teaser Lock Copy

Headline:

`Your hands already revealed the first layer`

Body:

`You have the surface reading. Unlock the deeper story of what life gave you, what changed in your active hand, and where the strongest timing and relationship signs appear.`

Primary CTA:

`Unlock full palm reading`

Secondary CTA:

`Ask 3 palm questions`

### Full Reading Offer

Headline:

`Read both hands together`

Body:

`Get the complete dual-hand reading: temperament, destiny vs self-made changes, love, career, vitality, timing shifts, and rare signs where visible.`

Bullets:

- `Dual-hand comparison`
- `Evidence-backed insights`
- `Love, career, timing, and rare signs`
- `Save this palm session`

CTA:

`Unlock full reading`

### Topic Pack Copy

Love pack:

`See what your heart line, Venus area, and relationship zone suggest about attachment, emotional style, and marriage timing.`

Career pack:

`Go deeper into fate line strength, rise patterns, money signs, and whether your path looks inherited or self-built.`

Timing pack:

`Explore delayed rise, turning points, and the phases where your hand suggests change becomes stronger.`

Rare signs pack:

`Check for special symbols, protection marks, forks, crosses, triangles, and other unusual signs only where visibility is strong enough.`

### Question Pack Copy

Headline:

`Ask directly about your palm`

Body:

`Use your scanned hands to ask about love, career, money, foreign travel, timing, or a sign you noticed yourself.`

CTA:

`Ask 3 palm questions`

## Telemetry To Add

- handedness selected
- passive scan accepted / warning / retake
- active scan accepted / warning / retake
- extraction sufficient / insufficient
- synthesis success / failure
- teaser shown
- full reading unlocked
- question asked
- question answer type
- feature_not_visible rate
- retake_after_low_confidence rate

## Experiment Priorities

### Experiment 1

Compare:

- current scorecard result
- evidence-first teaser

Primary metrics:

- unlock rate
- first-session question rate
- session length

### Experiment 2

Compare:

- English
- Hinglish

Primary metric:

- completion and unlock rate for Indian users

### Experiment 3

Compare first-screen formats:

- destiny vs karma
- what stood out
- timeline hints

## Delivery Phases

### Milestone 1

- dual-hand capture
- handedness
- validation JSON
- evidence extraction JSON
- session storage

### Milestone 2

- synthesis JSON
- teaser generation
- teaser UI
- paywall hooks

### Milestone 3

- full reading generation
- full reading UI
- topic packs

### Milestone 4

- image-grounded Q&A
- evidence drawer
- Hinglish mode

## Immediate Build Recommendation

Ship the first usable redesign with:

1. dual-hand flow
2. handedness selection
3. strict JSON validation and extraction
4. synthesis object
5. teaser result UI
6. full reading unlock
7. image-grounded Q&A

Do not carry forward:

- numeric scores
- fake parser defaults
- single-hand-only product framing
- non-image-grounded palm Q&A
