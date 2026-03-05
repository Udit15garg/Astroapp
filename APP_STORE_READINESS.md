# AstroApp App Store Readiness

## 1) Production AI backend (required)
- Do not ship direct OpenAI key in app binaries.
- Configure a backend proxy and set in `local.properties`:
  - `OPENAI_PROXY_URL=https://your-domain.com/v1/chat/completions`
  - `OPENAI_PROXY_TOKEN=your_backend_token` (optional if proxy is already authenticated)
- Keep `ALLOW_DIRECT_OPENAI=false` for release.

## 2) Policy links (required)
- Host production policy pages and configure:
  - `PRIVACY_POLICY_URL=https://your-domain.com/privacy`
  - `TERMS_URL=https://your-domain.com/terms`
- App will open hosted links when configured, and fallback to local policy pages otherwise.

## 3) Release build checks
- Build:
  - `./gradlew :app:assembleRelease`
- Verify:
  - Login and logout
  - Delete account/data
  - Subscription screen opens from coin badge
  - Palmistry camera + strict rejection flow
  - AI error messaging when backend unavailable

## 4) Play Console checklist
- Privacy Policy URL filled in Play Console.
- Data safety form completed (account data, user content, diagnostics as applicable).
- Camera permission purpose clearly described.
- Content rating and app access instructions provided.
- Screenshots, feature graphic, and short/full descriptions uploaded.

## 5) Security and compliance notes
- This app now supports proxy-first AI architecture for production.
- Local DB migrations are additive and no longer destructive by default.
- Terms and Privacy actions are split correctly.
