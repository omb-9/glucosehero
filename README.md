<div align="center">

# GlucoseHero

**A local-first, no-account glucose, insulin, and meal logger for Android.**

![Kotlin](https://img.shields.io/badge/Kotlin-2.4.10-7F52FF?logo=kotlin&logoColor=white)
![Min SDK](https://img.shields.io/badge/min%20SDK-26-3DDC84?logo=android&logoColor=white)
![Target SDK](https://img.shields.io/badge/target%20SDK-37-3DDC84?logo=android&logoColor=white)
![License](https://img.shields.io/badge/license-TBD-lightgrey)

</div>

---

## What it is

GlucoseHero is a personal logging tool for people who track their glucose, insulin, carbs, and exercise. You log a reading or a meal, and the app keeps a searchable timeline, draws charts, and surfaces simple on-device patterns — no account, no cloud, no vendor lock-in. Your data stays on the device unless you explicitly share or export it.

## Privacy

This project is written for people who read the code before trusting a health app. Every line below is traceable to the source.

- **No account.** There is no sign-in, registration, or account system anywhere in the app. All data lives in a local Room database and Android DataStore.
- **No telemetry.** There are no analytics, crash-reporting, or advertising SDKs in the dependency graph ([`gradle/libs.versions.toml`](gradle/libs.versions.toml)).
- **Local-first.** Glucose, entries, foods, supplies, chat history, and settings are stored on-device in Room and DataStore. There is no backend server owned by this project.
- **What leaves the device is opt-in.** Two features can make a network request, both disabled or gated by default where it matters:
  - **Barcode lookup** — enabled by default, contacts Open Food Facts. Only the barcode number is sent; no log data leaves the device. It can be turned off in Settings → Meal Logging ([`MealLoggingSection.kt`](app/src/main/java/com/omb9/glucosehero/ui/settings/MealLoggingSection.kt), [`NetworkModule.kt`](app/src/main/java/com/omb9/glucosehero/di/NetworkModule.kt)).
  - **Hero AI** — disabled by default and requires you to supply your own API key. When enabled, your log data is sent to *your* chosen provider (see below).
- **No cleartext off-device.** Plain-HTTP requests are only permitted to loopback / RFC1918 local-network addresses; anything else is refused ([`CleartextGuardInterceptor.kt`](app/src/main/java/com/omb9/glucosehero/data/remote/CleartextGuardInterceptor.kt)).
- **Your API key is encrypted at rest** with a hardware-backed Android KeyStore key before it is persisted ([`KeystoreManager.kt`](app/src/main/java/com/omb9/glucosehero/data/security/KeystoreManager.kt)).

## Features

### Logging

- Manual glucose readings (mg/dL or mmol/L), basal and bolus insulin, carbs, protein, fat, and free-text notes.
- Meal context tags (fasting, before/after meal, bedtime) and hashtag extraction.
- Search and single-day filtering over a large, paged log.

### Stats & insights

- 24h / 7d / 14d / 30d / 90d charts (Vico), time-in-range, rolling averages, and estimated A1c (eA1c) from the ADAG formula.
- On-device pattern recognition runs nightly and surfaces insight cards.
- Food impact cards that correlate tags with post-meal glucose outcomes.

### Health Connect (read-only import)

- Imports **glucose, nutrition, and exercise** from Health Connect into the local log ([`HealthConnectRepository.kt`](app/src/main/java/com/omb9/glucosehero/data/health/HealthConnectRepository.kt)).
- Read-only: the app reads from Health Connect; it does **not** write back to it. Import is opt-in per category and idempotent.

### Barcode / food lookup

- EAN/UPC barcode scanning via Google Play services code scanner (no camera permission needed).
- Optional Open Food Facts lookup (toggleable). Only the barcode number is transmitted.

### Hero AI assistant (bring your own key)

- An opt-in chat assistant ("Hero") grounded in your logged data.
- **Not included by default** — it requires your own API key from one of: Google Gemini, OpenAI, OpenRouter, or any self-hosted OpenAI-compatible endpoint (e.g. Ollama). There is no bundled or free API key ([`AiModels.kt`](app/src/main/java/com/omb9/glucosehero/domain/model/AiModels.kt), [`AiAssistantSection.kt`](app/src/main/java/com/omb9/glucosehero/ui/settings/AiAssistantSection.kt)).
- API key is encrypted on-device and only decrypted in memory at request time.

### Data ownership

- Manual JSON backup/restore (merge or replace), one-file-per-month Markdown export, and shareable 90-day PDF/CSV clinical reports.
- Optional daily automatic backups to a folder you choose.

### Other

- Home-screen widget (Glance) showing your most recent reading.
- Post-meal reminders (+2h), silent by default.
- On-device crisis detection: matching journal text is never sent to the AI and surfaces local crisis support options ([`CrisisDetector.kt`](app/src/main/java/com/omb9/glucosehero/util/CrisisDetector.kt)).

> Screenshots: `TODO(owner)` — add up-to-date captures of Log, Stats, and Settings.

## Install

### Google Play

`TODO(owner)` — add the Play Store listing link once published.

### Direct APK

1. Download the signed APK from the [GitHub Releases](https://github.com/omb-9/glucosehero/releases) page.
2. **Verify the APK before installing.** Confirm the signing certificate fingerprint matches the one below — this is what protects you from a repackaged build shipped under this app's name.

```
TODO(owner): add the SHA-256 fingerprint of the release signing certificate here.
```

3. **Obtainium** — add `https://github.com/omb-9/glucosehero` and point it at GitHub Releases to receive updates directly from this repository.

## Build from source

Requirements:

- **JDK 17** — the project targets Java 17 (`sourceCompatibility`/`targetCompatibility` and `jvmTarget = 17` in [`app/build.gradle.kts`](app/build.gradle.kts)).
- **Android SDK** with `compileSdk 37`. Point `sdk.dir` in `local.properties` at your SDK (see below).
- **No API keys are required to build.** Open Food Facts is keyless (it only asks for a `User-Agent`), and Hero AI uses your own key entered at runtime. There is no Places or GoodRx integration in the code.

Steps:

```bash
# 1. Configure local.properties with your SDK path:
#    sdk.dir=C\:\\path\\to\\Android\\Sdk
#    (there is no local.properties.example yet — TODO(owner))

# 2. Build the debug APK
./gradlew assembleDebug
```

> [!NOTE]
> `local.properties` currently holds only the SDK path. `TODO(owner)` — add a `local.properties.example` documenting any future keys (e.g. an Open Food Facts contact, or API keys if integrations are added).

## Data ownership

This is the section that matters most to the F-Droid / Obtainium / self-hosting audience.

**Where your data lives.** Everything is stored on-device in a Room SQLite database (`GlucoseHeroDatabase`) and a Preferences DataStore.

**What is *not* backed up to Google cloud backup.** Cloud backup is enabled, but two stores are explicitly excluded ([`backup_rules.xml`](app/src/main/res/xml/backup_rules.xml), [`data_extraction_rules.xml`](app/src/main/res/xml/data_extraction_rules.xml)):

- the Room database (`domain="database"`)
- the DataStore (`domain="file" path="datastore/"`)

In other words, your raw log data and settings are **not** silently copied to Google's cloud backup. Export is something you do yourself.

**Export formats.** From Settings, you can:

- **JSON** — a full, self-contained backup of every user table plus profile and settings (minus the KeyStore-wrapped API key). Restore supports *merge* or *replace*.
- **Markdown** — one human-readable file per month with per-month glucose statistics.
- **PDF / CSV** — a shareable 90-day clinical report (entries, glucose, insulin, carbs, exercise, eA1c).

**Import.** JSON backups can be restored (merge or replace), with a pre-import snapshot written locally so a bad restore is reversible.

**Automatic backup.** An opt-in daily backup runs while the device is charging and idle, into a folder you select.

**Health Connect.** Import is one-way: the app reads Health Connect records into your local log and never writes back. You can remove imported data at any time without touching manual entries.

## Contributing

`TODO(owner)` — add contribution guidelines, code style, and issue/PR templates if desired.

## Disclaimer

GlucoseHero is a logging tool, not a medical device. Hero's answers are informational — always confirm treatment decisions with your care team.

This wording matches the in-app disclaimer ([`SettingsScreen.kt`](app/src/main/java/com/omb9/glucosehero/ui/settings/SettingsScreen.kt)). The app does not diagnose, treat, or advise on any medical condition.

## License

`TODO(owner)` — no `LICENSE` file is present in the repository, so the project is currently unlicensed (all rights reserved by default). Add an explicit open-source license before publishing.

## Attributions

- **[Open Food Facts](https://world.openfoodfacts.org/)** — barcode product data is retrieved from Open Food Facts, which is made available under the [Open Database License (ODbL)](https://opendatacommons.org/licenses/odbl/). This attribution is a condition of using that data.
- **[Health Connect](https://developer.android.com/health-connect)** by Google — health-data import.
- **[Vico](https://github.com/patrykandpatrick/vico)** — charting.
- **[Jetpack Compose](https://developer.android.com/jetpack/compose)**, **[Room](https://developer.android.com/jetpack/androidx/releases/room)**, **[DataStore](https://developer.android.com/jetpack/androidx/releases/datastore)**, and the rest of the AndroidX stack.
