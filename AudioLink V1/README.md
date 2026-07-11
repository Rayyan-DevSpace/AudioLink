# AudioLink

In-call Bluetooth/mic split-routing app. Built by Muhammad Rayyan Shehzad.

Companion documents (also in this project as reference):
- `docs/architecture.md` — full system architecture
- `docs/audiolink-ui-spec.md` — UI design spec

## Opening in Android Studio

1. Unzip this project.
2. Open Android Studio → **File → Open** → select the unzipped `AudioLink` folder.
3. Android Studio will prompt to sync Gradle. The Gradle wrapper jar is **not**
   included in this download (binary files can't be generated here) — when
   prompted, let Android Studio regenerate it, or run:
   ```
   gradle wrapper --gradle-version 8.7
   ```
   once, from a terminal in the project root, if you have a system-wide Gradle
   install. After that, `./gradlew` will work normally.
4. Let Gradle sync finish (first sync downloads dependencies — needs internet).
5. Run on a physical device (recommended — Bluetooth/USB audio routing can't be
   meaningfully tested on the emulator).

## What's implemented

- 4-screen Compose UI: Home, Output device, Input device, Applies to (apps)
- Monochrome light/dark theme, manual override + system-follow
- Core routing engine (`AudioRoutingManager`): output lock via
  `setCommunicationDevice()` / legacy SCO, input bias via device priority
- Foreground service with a 1.5s re-assert loop and persistent notification
- Test tone player (`ToneGenerator` on the voice-call stream)
- Live input spectrum meter (`AudioRecord` preview, RMS → bar levels)
- App-scope engine: "All apps" vs specific-app multi-select, using
  `UsageStatsManager` to detect the foreground app

## What you'll need to finish/tune yourself

- **Usage access grant flow**: `ForegroundAppWatcher.hasUsageAccess()` is
  implemented, but the Settings deep-link UI to request it
  (`Settings.ACTION_USAGE_ACCESS_SETTINGS`) isn't wired into a screen yet —
  add a prompt/banner on the Apps screen when `allApps == false` and access
  isn't granted.
- **Bluetooth device names**: some OEMs restrict `productName` without
  `BLUETOOTH_CONNECT` fully granted at runtime — verify the permission flow on
  your target devices.
- **Gradle wrapper jar**: see step 3 above.
- **App icon**: placeholder monochrome vector icon included — swap for a real
  one whenever you're ready.
- **Real device testing matrix**: this is the one architecture flagged
  repeatedly — input-bias reliability varies by OEM audio HAL. Test on your
  actual Samsung/Xiaomi/etc. devices before trusting the split in production.

## Package structure

```
app/src/main/java/com/rayyanshehzad/audiolink/
├── MainActivity.kt        # screen state + wiring
├── AudioLinkApp.kt         # Application, holds RoutingRepository
├── ui/
│   ├── home/                # Home screen
│   ├── output/               # Output device screen + test tone
│   ├── input/                 # Input device screen + spectrum meter
│   ├── apps/                   # Apps scope screen
│   ├── common/                  # Shared row/switch composables
│   └── theme/                    # Light/dark MaterialTheme
├── data/RoutingRepository.kt   # DataStore-backed state
├── audio/                        # AudioRoutingManager, TestTonePlayer, InputSpectrumReader
├── appscope/ForegroundAppWatcher.kt
└── service/RoutingForegroundService.kt
```
