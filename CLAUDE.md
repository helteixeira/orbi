# Orby: Wear OS voice AI assistant

Handoff written on 2026 11 15 from a Claude chat session, so work can continue in Claude Code.

## What this is

A voice AI assistant for Wear OS watches with an Android phone companion app.
The user taps the watch, talks, and gets an answer on screen and read aloud.
All setup (AI service, key, assistant name, skin) happens in the phone app and is synced to the watch.

Owner: Helder (GitHub `helteixeira`). He speaks Portuguese (Portugal). Reply to him in Portuguese.
He asked that prose written for him never uses hyphens or em dashes.

## Where the code is

- This repo: `https://github.com/helteixeira/orby` (public). Earlier names: `wristmind`, `orbi`, `wrivo`.
- Old private repo `helteixeira/JarvisSmartwatch`: first version. Its history contains an old signing keystore, so it must stay private. Do not make it public. Do not delete it without asking.
- GitHub is the source of truth. Stale copies exist on the owner's PC in `Downloads\Orbi` and `Downloads\WristMind`.

## Current state

- Last green build: release `latest` with `OrbiPhone.apk` and `OrbiWatch.apk` (bilingual build, English plus Portuguese).
- After that, one commit made `tr()` in `core/.../I18n.kt` return English only. Check that the build is still green.
- Nothing has ever been tested on a real phone or watch by the developer side. Only CI compilation is verified.
- The owner installed the phone app and reported that the Test button for the API key fails. He did not give the error text or which service. Ask for it.
- The code, README, workflow and SVGs still say Orbi. The final name is Orby (see below).

## Structure

Gradle multi module project, Kotlin, Jetpack Compose. Code package is still `app.wristmind.*` (internal only).

- `core/` (Android library)
  - `AppConfig.kt`: `AppConfig` data class, `Fornecedores` (providers: anthropic, gemini, openai), `Skins`, `Idiomas`, `ConfigStore` (SharedPreferences as JSON).
  - `AiClient.kt`: HTTP client for the three providers with `HttpURLConnection` and `org.json`. `perguntar()` (chat with in memory history), `traduzir()`, `listarModelos()`. Also `Prompts`.
  - `I18n.kt`: `tr(en, pt)`, now returns `en`.
- `mobile/` (phone app, applicationId `io.github.helteixeira.orbi`)
  - `MainActivity.kt`: Material 3 dark UI with four tabs: Assistant, Look, Watch, Help.
  - `Componentes.kt`: shared composables, watch preview, QR code (zxing).
  - `Relogio.kt`: Wearable Data Layer sync (`/wristmind/config`), release URLs.
- `wear/` (watch app, same applicationId and same signature as the phone app, required by the Data Layer)
  - `MainActivity.kt`: animated ring HUD drawn on a Canvas, speech recognition through `RecognizerIntent`, TextToSpeech, tap to talk, long press toggles Chat and Translation.
  - `ConfigService.kt`: `WearableListenerService` that receives the config.
  - `res/values/strings.xml` English, `res/values-pt/strings.xml` Portuguese (should be deleted, the app is English only now).
- `docs/relogio.svg`, `docs/telemovel.svg`: layout illustrations used by the README. Not real screenshots.
- `.github/workflows/compilar.yml`: builds both apps on push to `main`, deletes the previous `latest` release, publishes the two APKs.

Identifiers are in Portuguese (for example `perguntar`, `fornecedor`, `chaveAtiva`). Keep or translate, but be consistent.

## Build and signing

- CI: Java 17, Gradle 8.9, AGP 8.5.2, Kotlin 2.0.20, compileSdk 34. Wear minSdk 30, phone minSdk 26.
- There is no Gradle wrapper jar in the repo. CI uses `gradle/actions/setup-gradle` with a fixed Gradle version. Adding a proper wrapper is a good first step.
- Signing: if repo secret `KEYSTORE_B64` exists, CI decodes it and signs with it (alias `wristmind`, passwords `wristmind`, set in the Gradle files). Otherwise it falls back to the debug key of the CI runner, which changes every build and forces an uninstall before each update.
- The secret has NOT been added yet. The keystore (base64) is on the owner's PC at `Downloads\WristMind_chave_assinatura.txt`. Ask him to add it as `KEYSTORE_B64` before he installs the next build.
- Never put an API key or the signing keystore in the repo.

## Decisions made by the owner

1. Name: **Orby**. Caution: `ORBY AI` is a registered US trademark for AI software (Orby AI, Inc.), and an app called Orbi (AI chatbot) exists. Fine for a personal GitHub project, risky for the Play Store. Use a neutral `applicationId` such as `io.github.helteixeira.wearassistant` so the visible name can change later without reinstalling.
2. Each user brings their own API key (Claude, Gemini or ChatGPT). No backend, no shared key. Sign in with an account is not possible for Claude or ChatGPT in third party apps. The Firebase AI Logic route (Gemini on the developer's project) was declined because he does not want to pay for other users.
3. The app is English only. Remove the Portuguese UI. The assistant still answers in the language chosen in setup.
4. The app must be as simple and friendly as possible: open it and be guided.
5. Target is generic Wear OS watches (Wear OS 3 or later). He wants to publish on the Play Store later.
6. He only has a phone and a watch for testing. No computer should be required to install on the watch.
7. Do not delete repositories unless he confirms.

## Planned work

1. Apply the name Orby: `app_name` in both `strings.xml`, default assistant name in `AppConfig`, titles in `mobile/MainActivity.kt`, URLs and APK names in `mobile/Relogio.kt` and in the workflow, README, repo description, SVG texts. Switch to the neutral applicationId at the same time.
2. English only cleanup: delete `wear/src/main/res/values-pt/`, optionally inline `tr()`.
3. Make the key test robust: before testing, call `listarModelos()` and if the configured model is not available, pick a sensible one automatically (Claude: latest haiku; Gemini: latest plain flash; OpenAI: `chat-latest` or a mini model). Hide the model field behind an Advanced toggle. Show the full provider error text. Default model IDs are guesses and may be wrong: `claude-haiku-5-5`, `gemini-3.8-flash`, `chat-latest`.
4. Simplify the phone app into a guided first run: pick a service, get a key, paste, test, send to watch.
5. Add example voice prompts to the Help tab, and possibly a hint on the watch idle screen.
6. In app watch install (an Install on watch button) so no computer is needed:
   - Discover the watch with `NsdManager` on `_adb-tls-pairing._tcp` and `_adb-tls-connect._tcp`, with manual IP and port fields as fallback.
   - The user enters only the 6 digit pairing code shown on the watch (Developer options, Wireless debugging, Pair new device).
   - Use `com.github.MuntashirAkon:libadb-android` (JitPack; add the JitPack repo in `settings.gradle.kts`), plus `org.conscrypt:conscrypt-android`, and BouncyCastle (`bcpkix-jdk18on`) to create the self signed certificate. Subclass `AbsAdbConnectionManager`, persist key and certificate in `filesDir`, call `pair(host, port, code)`, `connect(host, port)`, then `openStream('exec:cmd package install -S <size>')` and stream the APK downloaded from the release.
   - Exact library versions and method signatures were not verified. Read the library README and sample before coding.
   - Once the app is on the Play Store this feature is unnecessary, because Play installs the watch app.
7. Later ideas from the owner: always listening wake word (battery cost), meeting recording with summaries (needs speech to text and consent of participants), a Wear OS Tile for one tap access.

## Cautions

- Phone and watch APKs must come from the same build so the signatures match.
- The watch app is marked `standalone=true` but needs the phone for setup.
- Be honest with the owner about what was only compiled and what was really tested on a device.
