# Palmistry Module v2 Technical PRD

## Objective

Redesign palmistry so it:

- reduces unnecessary scan rejection
- improves reading quality and specificity
- supports dual-hand interpretation
- supports up to 2 optional detail photos
- replaces score-based output with evidence-first narrative reading
- increases curiosity, follow-up questions, and paid unlocks

## Product Summary

The new experience replaces rigid single-image palmistry with:

- 2 required full-hand images
- up to 2 optional detail images
- validation based on line visibility, not flash usage
- evidence extraction before interpretation
- passive vs active hand comparison
- teaser, full reading, and image-grounded Q&A

## Interpretation Model

- passive hand = inherited nature / baseline tendencies / what life gave you
- active hand = current direction / developed traits / what you are making of life

Rules:

- use handedness to determine active vs passive
- do not use gender in scan logic
- traditional framing may appear in copy, not in capture logic

## Capture Model

### Required

- passive full inner palm
- active full inner palm

### Optional

- detail image A
- detail image B

Default v1 use:

- side detail
- center detail

## Upload UI

Show 4 upload boxes:

1. `Passive hand`
   - `Full inner palm. Shows inherited tendencies.`
2. `Active hand`
   - `Full inner palm. Shows present direction and self-made changes.`
3. `Side detail`
   - `Capture the thumb-side or outer edge clearly for mount and side-line detail.`
4. `Center detail`
   - `Capture the middle of the palm clearly for major line intersections.`

## Validation Philosophy

Do not reject because:

- flash was off
- flash is unavailable
- battery is low
- image is slightly imperfect

Reject only when:

- palm lines are unreadable
- core palm regions are obscured
- blur, glare, or crop severity prevents extraction

## Validation States

- `ACCEPT`
- `ACCEPT_WITH_GUIDANCE`
- `RETAKE_REQUIRED`

## Validation Architecture

### Layer 1: Local CV checks

Soft checks only:

- brightness
- blur
- glare
- shadow
- crop severity
- palm occupancy
- center focus quality

### Layer 2: AI validation

Return structured JSON on palm readability.

### Layer 3: Evidence sufficiency

After extraction, verify enough evidence exists for teaser or premium reading.

## Evidence-First Pipeline

1. collect passive and active full-hand images
2. validate both
3. request targeted detail shots if needed
4. extract evidence only
5. synthesize passive vs active contrast
6. generate teaser, full reading, and Q&A

## Evidence Requirements

Extract visible features only:

- hand shape
- finger length pattern
- thumb openness
- life line
- head line
- heart line
- fate line
- sun line
- mercury line
- major mounts
- special marks
- confidence values
- image quality issues

Rules:

- no invented values
- no fake defaults
- if unclear, say unclear

## Output Modes

### Mode A: Quick Reveal

- opening verdict
- what life gave you
- what you are becoming
- 3 visible signs
- 1 contrast
- 1 locked curiosity hook

### Mode B: Full Life Reading

- nature
- destiny vs effort
- love and attachment
- career and money
- health and vitality
- family / marriage / children
- timing and turning points
- rare signs
- final guidance

### Mode C: Ask About My Palm

Palm-specific Q&A must include:

- both full-hand images
- detail images if available
- evidence JSON
- synthesis JSON
- prior reading summary
- user question

## Result UI

Remove:

- score bars
- 7 fixed categories
- generic category tiles

Add:

- top insight card
- destiny vs self-made contrast card
- observed signs card
- deep-dive tabs
- optional evidence drawer

## Rejection Minimization Rules

- proceed whenever core evidence is sufficient
- if one slot is weak, do not fail the whole session automatically
- request targeted retake only for the weak slot
- continue with warnings where possible
- prefer additional detail images before whole-session rejection

## Data Model

`PalmSession` should include:

- `session_id`
- `user_id`
- `created_at`
- `handedness`
- `locale`
- `passive_full_image_uri`
- `active_full_image_uri`
- `detail_image_a_uri`
- `detail_image_b_uri`
- `passive_validation_json`
- `active_validation_json`
- `detail_validations_json`
- `passive_evidence_json`
- `active_evidence_json`
- `synthesis_json`
- `teaser_text`
- `full_reading_text`
- `followup_history`
- `confidence_summary`

## Prompting

Use separate prompts for:

- validation
- evidence extraction
- dual-hand synthesis
- teaser generation
- full reading
- follow-up Q&A

Do not use a single mega prompt.

## Non-Negotiable Rules

Must do:

- no fake defaults
- no fabricated missing categories
- no mandatory flash
- no score-based output
- no non-image-grounded palm Q&A
- no gender-based upload logic
- targeted retake guidance only

Must not do:

- reject because flash is unavailable
- reject because battery is low
- reject because one image is imperfect when other evidence is sufficient
- invent special signs when confidence is low

## Suggested v1 Scope

Ship first:

- handedness selection
- 4-box upload UI
- no-flash validation logic
- 3-state acceptance system
- targeted retake guidance
- evidence extraction per hand
- dual-hand synthesis
- teaser output
- full reading output
- image-grounded follow-up Q&A

## Success Metrics

- scan completion rate
- rejection rate
- retake-to-success rate
- free-to-paid unlock rate
- average number of follow-up questions
- perceived accuracy rating
- time spent in reading
- repeat session rate

## Product Positioning

The module should feel like:

`A dual-hand palmistry reading that studies what life gave you, what you changed, and what signs in your hand still point toward your path ahead.`
