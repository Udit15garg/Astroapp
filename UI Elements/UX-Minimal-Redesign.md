# Astra UI – Minimal Midnight

This repo contains:
- Home screen with feature tiles + banner
- Auth gating modal (login on tile tap if logged out)
- Credits screen + persistent credits balance
- Palmistry:
  - start screen (new vs existing)
  - capture screen with minimal SVG overlay guide
  - hand list + per-hand chat history (1 credit per question)
- Tarot:
  - animated flip cards (3-card pick flow; 1 credit per draw)
  - reading result with “Show more”

## Run
```bash
npm install
npm run start
```

## Where to edit next
- `src/screens/PalmCaptureScreen.tsx`: replace mock camera with Expo Camera
- `src/services/*`: replace mock data with real inference/API calls
- `src/state/AppState.tsx`: plug in real auth, purchases, etc.
