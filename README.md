<div align="center">

# GlucoseHero

**A local-first, no-account glucose, insulin, and meal logger for Android.**

![Kotlin](https://img.shields.io/badge/Kotlin-2.4.10-7F52FF?logo=kotlin&logoColor=white)
![Min SDK](https://img.shields.io/badge/min%20SDK-26-3DDC84?logo=android&logoColor=white)
![Target SDK](https://img.shields.io/badge/target%20SDK-37-3DDC84?logo=android&logoColor=white)
![License](https://img.shields.io/badge/license-none-lightgrey)

</div>

---

## What it is

GlucoseHero is a personal logging tool for people who track glucose, insulin, carbs, protein, fat, exercise, mood, and notes. You log a reading or a meal and the app keeps a searchable timeline, draws charts, computes on-device patterns, and can pull in data from Health Connect. There is no account, no backend server owned by this project, and no cloud copy of your data unless you export it yourself.

## Privacy

This project is written for people who read the code before trusting a health app. Every line below is traceable to the source.

- **No account.** There is no sign-in, registration, or account system in the app. The only Google interaction is the optional Pro subscription, which goes through Google Play Billing (and therefore your Google account), not an app account.
- **No telemetry.** There are no analytics, crash-reporting, or advertising SDKs in the dependency graph ([`gradle/libs.versions.toml`](gradle/libs.versions.toml)).
- **Local-first.** Glucose readings, entries, foods, supplies, chat history, and settings are stored on-device in a Room database and an Android Preferences DataStore ([`DatabaseModule.kt`](app/src/main/java/com/omb9/glucosehero/di/DatabaseModule.kt), [`SettingsDataStore.kt`](app/src/main/java/com/omb9/glucosehero/data/local/datastore/SettingsDataStore.kt)).
- **Network access is opt-in and limited to these paths:**
  - **Barcode and food search → Open Food Facts.** Scanning a barcode sends only the barcode number; a food-name search sends only your search text. No log data leaves the device. Both are gated by a single toggle, on by default ([`MealLoggingSection.kt`](app/src/main/java/com/omb9/glucosehero/ui/settings/MealLoggingSection.kt), [`NetworkModule.kt`](app/src/main/java/com/omb9/glucosehero/di/NetworkModule.kt), [`LogViewModel.kt`](app/src/main/java/com/omb9/glucosehero/ui/log/LogViewModel.kt)).
  - **Hero AI → your own provider.** The assistant sends a context built from your logged data (recent entries including notes, plus aggregate stats) to a provider you configure. It only works once you supply your own API key; see below.
  - **Local webhook → a URL you configure.** Optionally POSTs a saved manual glucose entry as JSON to a URL you enter; empty by default ([`EntryRepositoryImpl.kt`](app/src/main/java/com/omb9/glucosehero/data/repository/EntryRepositoryImpl.kt)).
  - **Nightscout REST poller → a URL you configure.** Optional CGM ingest, off by default. GlucoseHero `GET`s `/api/v1/entries/sgv.json` with a millisecond time cursor plus either `?token=` or a SHA-1 `api-secret` header. No glucose, notes, or AI keys are uploaded. The poller uses a dedicated OkHttp client that does not carry the AI bearer interceptor ([`NightscoutCgmSource.kt`](app/src/main/java/com/omb9/glucosehero/data/cgm/NightscoutCgmSource.kt), [`NetworkModule.kt`](app/src/main/java/com/omb9/glucosehero/di/NetworkModule.kt)). The host must be acknowledged in Settings → Data Sources; public HTTP is refused unless the site is on your LAN ([`NightscoutEndpointGuard.kt`](app/src/main/java/com/omb9/glucosehero/data/cgm/nightscout/NightscoutEndpointGuard.kt), [`TrustedHosts.kt`](app/src/main/java/com/omb9/glucosehero/data/remote/TrustedHosts.kt), [`CleartextGuardInterceptor.kt`](app/src/main/java/com/omb9/glucosehero/data/remote/CleartextGuardInterceptor.kt)). The token or API secret is Keystore-encrypted at rest ([`KeystoreManager.kt`](app/src/main/java/com/omb9/glucosehero/data/security/KeystoreManager.kt)). API v1 only; a v3-only site will not sync.
- **xDrip+ / AndroidAPS local broadcasts.** Optional CGM ingest, off by default. GlucoseHero listens on this phone for xDrip+ `BgEstimate` and AAPS `NEW_SGV` broadcasts. No xDrip account, no Nightscout, and no network. Identify receiver is `com.omb9.glucosehero` ([`XdripBroadcastHandler.kt`](app/src/main/java/com/omb9/glucosehero/data/cgm/xdrip/XdripBroadcastHandler.kt)). Fill Identify receiver so xDrip+ can wake a stopped process; leaving it blank only works while GlucoseHero is already running (after reboot the opt-in receiver starts the process and implicit listening resumes). On Android 13 and below the sending app is not exposed, so any app can inject a reading while xDrip+ or AAPS is installed; the in-app xDrip settings screen discloses this. Enable it from Settings → Data Sources.
- **LibreLinkUp is not implemented.** This build does not collect Abbott credentials, talk to LibreLinkUp hosts, or offer a working Libre toggle.
- **No cleartext off-device.** Plain-HTTP requests are refused to any host that is not loopback, RFC1918, link-local, or `.local` ([`CleartextGuardInterceptor.kt`](app/src/main/java/com/omb9/glucosehero/data/remote/CleartextGuardInterceptor.kt)).
- **Your AI API key is encrypted at rest** with a hardware-backed Android KeyStore key (AES/GCM) and decrypted only in memory at request time ([`KeystoreManager.kt`](app/src/main/java/com/omb9/glucosehero/data/security/KeystoreManager.kt)).

## Features

### Logging

- Manual glucose readings (mg/dL or mmol/L), basal and bolus insulin, carbs, protein, fat, exercise, mood, and free-text notes.
- Meal context tags (fasting, before/after meal, bedtime) and hashtag extraction.
- Search and single-day filtering over a paged log.
- A recommended bolus ("Smart Bolus") and insulin-on-board (IOB) readout ([`BolusCalculator.kt`](app/src/main/java/com/omb9/glucosehero/util/BolusCalculator.kt), [`IobCalculator.kt`](app/src/main/java/com/omb9/glucosehero/util/IobCalculator.kt)).

### Stats & insights

- 24h / 7d / 14d / 30d / 90d charts (Vico), time-in-range, rolling averages, and estimated A1c (eA1c) from the ADAG formula ([`Ea1cCalculator.kt`](app/src/main/java/com/omb9/glucosehero/util/Ea1cCalculator.kt)).
- Nightly on-device pattern recognition that surfaces insight cards ([`PatternRecognitionWorker.kt`](app/src/main/java/com/omb9/glucosehero/work/PatternRecognitionWorker.kt)).
- Food / lifestyle / mood impact cards that correlate tags with post-meal glucose outcomes.

### Health Connect (import and write-back)

- Imports **glucose, nutrition, exercise, sleep, and cycle** records from Health Connect into the local log, opt-in per category ([`HealthConnectRepository.kt`](app/src/main/java/com/omb9/glucosehero/data/health/HealthConnectRepository.kt)).
- Two-way: manual entries are also **written back** to Health Connect for glucose, nutrition, and exercise, when the matching write permission is granted ([`EntryRepositoryImpl.kt`](app/src/main/java/com/omb9/glucosehero/data/repository/EntryRepositoryImpl.kt), [`HealthConnectSyncWorker.kt`](app/src/main/java/com/omb9/glucosehero/work/HealthConnectSyncWorker.kt)).

### CGM ingest (Data Sources)

- Settings → **Data Sources** is the hub for CGM paths: xDrip+ (local broadcast), Nightscout (HTTPS poll), and a pointer to Health Connect glucose import. Each CGM ingest toggle defaults **off**. LibreLinkUp is shown as not available.
- When two sources report the same reading within about 2.5 minutes, the kept row is xDrip+ first, then Nightscout, then LibreLinkUp (reserved), then Health Connect, then a file import ([`CgmSourcePriority.kt`](app/src/main/java/com/omb9/glucosehero/data/cgm/CgmSourcePriority.kt)).

### Barcode / food lookup

- EAN/UPC barcode scanning via the out-of-process Google Play services code scanner, restricted to EAN-13 / EAN-8 / UPC-A / UPC-E. (The app's CAMERA permission is used only for the optional meal-photo feature.)
- Optional Open Food Facts lookup and food-name search (toggleable). Only the barcode number or search text is transmitted.

### Hero AI assistant (bring your own key)

- A chat assistant grounded in your logged data, plus optional meal-photo analysis.
- **Not included out of the box.** It requires your own API key from one of: Google Gemini, OpenAI, OpenRouter, or any OpenAI-compatible endpoint (e.g. self-hosted Ollama) ([`AiModels.kt`](app/src/main/java/com/omb9/glucosehero/domain/model/AiModels.kt), [`AiSettingsScreen.kt`](app/src/main/java/com/omb9/glucosehero/ui/settings/AiSettingsScreen.kt)). The key is encrypted on-device and only decrypted in memory at request time.
- The code also contains a Pro subscription and free/pro daily-call quota scaffolding ([`BillingRepository.kt`](app/src/main/java/com/omb9/glucosehero/data/billing/BillingRepository.kt), [`AiQuota.kt`](app/src/main/java/com/omb9/glucosehero/util/AiQuota.kt)), but the managed API key that would back those tiers is not present in this build — so today the working path is your own key.

### Other

- Home-screen widget (Glance) showing your most recent reading ([`GlucoseHeroGlanceWidget.kt`](app/src/main/java/com/omb9/glucosehero/ui/glance/GlucoseHeroGlanceWidget.kt)).
- Post-meal reminders (+2h), enabled by default and delivered silently (low-importance, no sound/vibration) ([`PostMealReminderNotifier.kt`](app/src/main/java/com/omb9/glucosehero/work/PostMealReminderNotifier.kt)).
- On-device crisis detection: a note matching a fixed list of phrases shows a local crisis-support card and is excluded from tag analytics. The matching runs entirely on-device ([`CrisisDetector.kt`](app/src/main/java/com/omb9/glucosehero/util/CrisisDetector.kt)).

> Screenshots: `TODO(owner)`, add up-to-date captures of Log, Stats, and Settings.

## Install

### Google Play

`TODO(owner)`, add the Play Store listing link once published.

### Direct APK

1. Download the signed APK from the [GitHub Releases](https://github.com/omb-9/glucosehero/releases) page.
2. **Verify the APK before installing.** Confirm the signing certificate fingerprint matches the one below — this is what protects you from a repackaged build shipped under this app's name.

```
TODO(owner): add the SHA-256 fingerprint of the release signing certificate here.
```

3. **Obtainium**, add `https://github.com/omb-9/glucosehero` and point it at GitHub Releases to receive updates directly from this repository.

## Build from source

Requirements:

- **JDK 17** — the project targets Java 17 (`sourceCompatibility`/`targetCompatibility` and `jvmTarget = 17` in [`app/build.gradle.kts`](app/build.gradle.kts)).
- **Android SDK** with `compileSdk 37`. Point `sdk.dir` in `local.properties` at your SDK.
- **No API keys are required to build.** Open Food Facts is keyless (it only asks for a `User-Agent`), and Hero AI uses your own key entered at runtime. There is no Places or GoodRx integration in the code.

Steps:

```bash
# 1. Configure local.properties with your SDK path:
#    sdk.dir=C\:\\path\\to\\Android\\Sdk

# 2. Build the debug APK
./gradlew assembleDebug
```

> [!NOTE]
> `local.properties` currently holds only the SDK path, and there is no `local.properties.example`. `TODO(owner)`, add a `local.properties.example` documenting the SDK path (and any future keys).

## Data ownership

This is the section that matters most to the F-Droid / Obtainium / self-hosting audience.

**Where your data lives.** Everything is stored on-device in a Room SQLite database and a Preferences DataStore.

**What is *not* backed up.** Cloud backup is enabled, but two stores are explicitly excluded from both Google cloud backup and device-to-device transfer ([`backup_rules.xml`](app/src/main/res/xml/backup_rules.xml), [`data_extraction_rules.xml`](app/src/main/res/xml/data_extraction_rules.xml)):

- the Room database (`domain="database"`)
- the DataStore (`domain="file" path="datastore/"`)

In other words, your raw log data and settings are **not** silently copied to Google's cloud backup or device-transfer. Export is something you do yourself.

**Export formats.** From Settings → Data & Backup you can:

- **JSON** — a full, self-contained backup of every user table plus profile and settings (minus the KeyStore-wrapped API key). Restore supports *merge* or *replace* ([`BackupManager.kt`](app/src/main/java/com/omb9/glucosehero/data/export/BackupManager.kt)).
- **Markdown** — one human-readable file per month with per-month glucose statistics ([`MarkdownExporter.kt`](app/src/main/java/com/omb9/glucosehero/data/export/MarkdownExporter.kt)).
- **PDF / CSV** — a shareable 90-day clinical report, from the Stats screen ([`ExportManager.kt`](app/src/main/java/com/omb9/glucosehero/data/export/ExportManager.kt)).

**Import.** JSON backups can be restored (merge or replace) from the same screen.

**Automatic backup.** An opt-in daily backup runs while the device is charging and idle, into a folder you choose ([`AutoBackupWorker.kt`](app/src/main/java/com/omb9/glucosehero/work/AutoBackupWorker.kt)).

## Contributing

`TODO(owner)`, add contribution guidelines, code style, and issue/PR templates if desired.

## Disclaimer

GlucoseHero is a logging tool, not a medical device. Hero's answers are informational. Always confirm treatment decisions with your care team.

The app does not diagnose, treat, or advise on any medical condition. The wording above matches the in-app disclaimer in [`SettingsScreen.kt`](app/src/main/java/com/omb9/glucosehero/ui/settings/SettingsScreen.kt).

## License

No `LICENSE` file is present in this repository, so the project is currently unlicensed (all rights reserved by default). `TODO(owner)`, add an explicit open-source license before publishing.

## Attributions

- **[Open Food Facts](https://world.openfoodfacts.org/)** — barcode and food-name product data is retrieved from Open Food Facts, which is made available under the [Open Database License (ODbL)](https://opendatacommons.org/licenses/odbl/). This attribution is a condition of using that data (the app also shows "Nutrition data from Open Food Facts" in-app).
- **[Health Connect](https://developer.android.com/health-connect)** by Google — health-data import and write-back.
- **[Vico](https://github.com/patrykandpatrick/vico)** — charting.
- **[Jetpack Compose](https://developer.android.com/jetpack/compose)**, **[Room](https://developer.android.com/jetpack/androidx/releases/room)**, **[DataStore](https://developer.android.com/jetpack/androidx/releases/datastore)**, and the rest of the AndroidX stack.
