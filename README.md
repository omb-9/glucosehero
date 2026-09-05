# GlucoseHero

Privacy-first, AI-enhanced glucose management for Android. Local-first by design: every entry lives in an on-device SQLite (Room) database, API keys are encrypted with the hardware-backed Android KeyStore, and the AI layer is strictly **bring-your-own-key** — no GlucoseHero servers, no accounts, no telemetry.

> **Not medical advice.** GlucoseHero is a logging and informational tool. It must not be used for dosing decisions. Always consult your healthcare provider.

---

## Quick start

1. **Requirements:** Android Studio Koala (2024.1.1) or newer, JDK 17, Android SDK 34.
2. Open the project root (`GlucoseHero/`) in Android Studio and let Gradle sync.
3. If the Gradle wrapper jar is missing (some unzip tools strip it), run from the project root:
   ```
   gradle wrapper --gradle-version 8.7
   ```
   …or let Android Studio regenerate it when prompted.
4. Run the `app` configuration on a device/emulator with API 26+.

## AI setup (bring your own key)

Open **Settings → Hero AI** in the app:

| Provider | Default model | Key source |
|---|---|---|
| Gemini (default) | `gemini-2.0-flash` | https://aistudio.google.com/apikey |
| OpenAI | `gpt-4o-mini` | https://platform.openai.com/api-keys |
| OpenRouter | `google/gemini-2.0-flash-001` | https://openrouter.ai/keys |
| Custom (self-hosted) | `llama3.1` | none required |

All providers speak the OpenAI-compatible `/chat/completions` protocol with SSE streaming.

**Custom / Ollama:** point the base URL at any OpenAI-compatible endpoint, e.g. `http://192.168.1.10:11434/v1/` for a LAN Ollama box (Tailscale IPs work too). No API key needed — the field can stay empty. Provider switches apply **immediately**; the OkHttp client is never rebuilt (a dynamic interceptor reads config from DataStore per request).

Keys are AES/GCM-encrypted via Android KeyStore before touching DataStore, and DataStore is excluded from Android backups (`data_extraction_rules.xml`), so ciphertext never leaves the device.

## Architecture map

```
app/src/main/java/com/omb9/glucosehero/
├── domain/          # Pure Kotlin: models + repository interfaces
│   ├── model/       # Entry (sealed EntryDetails JSON-polymorphism), UserSettings, Chat/Stats models
│   └── repository/  # EntryRepository, SettingsRepository, ChatRepository
├── data/
│   ├── local/       # Room (lean indexed Entry table + JSON details column), DataStore, DAOs
│   ├── security/    # KeystoreManager — AES/GCM via Android KeyStore
│   ├── remote/      # Retrofit AiApi, DynamicApiInterceptor, OkHttp SSE streaming client
│   └── repository/  # Impls; AI context assembled at SQLite level (GROUP BY / LIMIT queries)
├── di/              # Hilt modules (Database, Network, Repository)
├── work/            # PendingQueryWorker (offline AI queue), ConnectivityObserver, InsightNotifier
└── ui/              # Compose, Material 3, state-driven navigation (primitive IDs only)
    ├── log/         # 90-day history; @Immutable state + kotlinx immutable collections (Strong Skipping)
    ├── components/  # AddEntrySheet — sub-5s entry, auto-focus + auto-keyboard
    ├── stats/       # Vico chart, 7/14/30/90d, target-range shading; math on Dispatchers.Default
    ├── chat/        # Hero AI: SSE typewriter streaming, offline queue banner
    ├── settings/    # Theme/accent/units/target range/AI provider
    └── entrydetail/ # entry/{entryId} route → ViewModel re-fetches fresh from Room
```

**Key decisions**
- Glucose is stored canonically in **mg/dL**; mmol/L is a display-layer conversion (`Formatters`).
- Navigation passes only primitive IDs; destination ViewModels observe Room via `StateFlow` (`SavedStateHandle`).
- Polymorphic entry payloads (insulin/meal/activity/note) serialize to one JSON column via a `TypeConverter` + `kotlinx.serialization` sealed class — the core table stays lean and indexable.
- Offline Hero queries are persisted to Room and replayed by a Hilt `CoroutineWorker` (`NetworkType.CONNECTED` constraint); a notification deep-links back to the Hero tab when the answer lands.

## First-build checklist

Built file-by-file outside Android Studio, so expect a short shake-out pass rather than a guaranteed zero-warning build:

- **Gradle wrapper:** if `gradlew` is missing/unexecutable → `gradle wrapper --gradle-version 8.7` then `chmod +x gradlew`.
- **Version pins** (in `gradle/libs.versions.toml`): AGP 8.5.2 · Kotlin 2.0.20 · KSP 2.0.20-1.0.25 · Hilt 2.51.1 · Compose BOM 2024.06.00 · Room 2.6.1 · Vico **1.15.0**. If you bump Kotlin, bump KSP in lockstep.
- **Vico:** code targets the **1.x API** (`ChartEntryModelProducer`, `lineChart`, `ThresholdLine`, `AxisValuesOverrider`). Vico 2.x renamed most of this — don't upgrade casually.
- **First sync** downloads ~1–2 GB of dependencies; subsequent builds are fast.
- Min SDK 26 / Target SDK 34. Notification permission is requested at runtime on Android 13+.
