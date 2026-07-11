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

## CI builds with Codemagic

A `codemagic.yaml` is included at the project root, following Codemagic's
native Android quickstart. As-is, it builds a **debug APK** on every run with
no extra setup — no keystore, no Google Play account needed. To use it:

1. Push this project (with `codemagic.yaml` at the root) to a Git repository.
2. In the Codemagic UI, add the app and connect that repository.
3. Click **Check for configuration file** — it should find `codemagic.yaml`
   and list the `audiolink-android` workflow.
4. Start a build.

```
git add codemagic.yaml
git commit -m "Add first workflow"
git push
```

Two things are commented out in the file until you're ready for them:
- **Release signing** (`android_signing`) — needs a keystore uploaded under
  *Team settings → codemagic.yaml settings → Code signing identities* first.
- **Google Play publishing** — needs a service account JSON added as a secret
  variable group called `google_play` (steps in Codemagic's Google Play
  publishing docs). Once both are set up, switch the build script from
  `assembleDebug` to the commented `bundleRelease` block and uncomment the
  `google_play` publishing section.



- 4-screen Compose UI: Home, Output device, Input device, Applies to (apps)
- Monochrome light/dark theme, manual override + system-follow
- Core routing engine (`AudioRoutingManager`): output lock via
  `setCommunicationDevice()` / legacy SCO, input bias via device priority —
  every call returns a `RoutingResult` (Success/DeviceUnavailable/
  PermissionDenied/Failed) instead of throwing
- Foreground service with a 1.5s re-assert loop, persistent notification, and:
  - **Device-disconnect handling**: an `AudioDeviceCallback` detects when the
    routed Bluetooth/USB/wired device disappears mid-call, updates the
    notification ("Output device disconnected — reconnect to resume"), and
    resumes automatically once it reconnects
  - **Permission-aware status**: if `RECORD_AUDIO`/`BLUETOOTH_CONNECT` isn't
    granted, the notification says so instead of the service silently failing
  - **Crash-safe loop**: each enforcement pass is wrapped so one bad cycle
    (unexpected exception, OEM quirk) doesn't kill the service — it just
    retries on the next tick
  - **Battery optimization prompt**: `MainActivity` asks the user to exempt
    AudioLink from battery optimization when routing is turned on (declines
    gracefully if the OEM blocks the intent)
- Test tone player (`ToneGenerator` on the voice-call stream)
- Live input spectrum meter (`AudioRecord` preview) — now exposes a
  `SpectrumState` (Levels/PermissionDenied/DeviceUnavailable/Error) so the
  Input screen shows a real message and a "Grant microphone access" button
  instead of dead bars when something's wrong
- App-scope engine: "All apps" vs specific-app multi-select, using
  `UsageStatsManager` to detect the foreground app — the Apps screen now
  shows a **"Usage access needed"** banner with an "Open settings" button
  whenever the user picks specific apps without having granted that access
  yet (state re-checked in `onResume()` so it clears when they come back)

## What you'll need to finish/tune yourself

- **Bluetooth device names**: some OEMs restrict `productName` without
  `BLUETOOTH_CONNECT` fully granted at runtime — verify the permission flow on
  your target devices.
- **Gradle wrapper jar**: see step 3 above.
- **App icon**: placeholder monochrome vector icon included — swap for a real
  one whenever you're ready.
- **Real device testing matrix**: this is the one architecture flagged
  repeatedly — input-bias reliability varies by OEM audio HAL. Test on your
  actual Samsung/Xiaomi/etc. devices before trusting the split in production.
- **First build hasn't been compiled/run** — this was written without access
  to a JDK/Android SDK in this environment, so treat the first Gradle sync
  and build as a debugging pass, not a formality.

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
