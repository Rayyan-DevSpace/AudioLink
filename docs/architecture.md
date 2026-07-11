# AudioLink — In-Call Bluetooth/Mic Split-Routing App
### Full Architecture Document (Concept → Deployment)

**Developer:** Muhammad Rayyan Shehzad

---

## 0. Problem Statement

**Scenario:** On a random-video-chat app (e.g. OmeTV/Omegle-style apps), the phone is connected to:
- **Bluetooth earbuds** (for listening to the other person)
- **An external USB/wired microphone** (for speaking)

**Default Android behavior:** These apps use **call/communication audio** (`AudioManager.MODE_IN_COMMUNICATION`), not plain media playback. In this mode, Android treats Bluetooth as an **atomic SCO (Synchronous Connection-Oriented) link** — meaning it locks *both* the incoming voice (to the earbuds) *and* the outgoing voice (from the earbuds' own mic) to the Bluetooth device as a single pair. You cannot natively tell Android "use Bluetooth for output only, and a different device for input" — the OS assumes one Bluetooth headset = one input + one output.

**Goal:** Break this pairing so that:
| Direction | Desired Device |
|---|---|
| Incoming voice (other person → you) | Bluetooth earbuds |
| Outgoing voice (you → other person) | External USB/wired microphone |

This document restructures the whole solution into a single, deployable, end-to-end architecture.

---

## 1. Feasibility Reality Check (Read This First)

Before the architecture, it's important to separate what's **officially supported** from what requires **workarounds**, since this determines how robust the final app can be.

| Capability | Status on Stock Android | Notes |
|---|---|---|
| Route **call output** to Bluetooth while call is active | ✅ Native, supported | `setCommunicationDevice()` (Android 12+) or `startBluetoothSco()` (older) |
| Route **call input** to a USB/wired mic while Bluetooth output is active | ⚠️ Not natively exposed as a toggle | Android *can* select a non-Bluetooth input device (`AudioDeviceInfo.TYPE_USB_HEADSET` / `TYPE_WIRED_HEADSET`) via `setPreferredDevice()` on the **recording** side, independent of the output. Success depends on OEM audio HAL (hardware abstraction layer) and chipset — not 100% guaranteed on every phone. |
| Forcing this split **inside a third-party app's own call session** (e.g. inside OmeTV's WebRTC/VOIP pipeline) | ❌ Not possible from outside that app | Your app **cannot** reach into OmeTV's internal audio stream. You can only influence *system-level* audio routing, which OmeTV's SDK (usually WebRTC) then queries and uses. |
| A system-wide "audio router" that intercepts and re-injects call audio for *any* app | ⚠️ Only via **Accessibility Service + Virtual Audio Device workarounds**, or requires the target app to cooperate (rare) | This is the hard part — see §5. |

**Key implication:** Your app cannot "hack into" OmeTV. What it *can* do is manipulate the **shared system audio routing state** before/while OmeTV is running, so that when OmeTV's WebRTC engine asks Android "what's the current input/output device," Android answers with your forced configuration instead of the default Bluetooth-locks-both-ways behavior. This works because most VOIP SDKs (including WebRTC, which OmeTV very likely uses) call the same public `AudioManager`/`AudioDeviceInfo` APIs rather than bypassing them.

---

## 2. High-Level Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                        USER'S ANDROID DEVICE                     │
│                                                                   │
│  ┌───────────────┐        ┌────────────────────────────────┐    │
│  │ Bluetooth      │──SCO──▶│                                 │    │
│  │ Earbuds        │◀───────│      ANDROID AUDIO SUBSYSTEM     │    │
│  │ (OUTPUT ONLY)  │        │      (AudioFlinger / AudioPolicy)│    │
│  └───────────────┘        │                                 │    │
│                             │   ┌─────────────────────────┐   │    │
│  ┌───────────────┐        │   │  AudioLink App             │   │    │
│  │ External USB/  │──PCM──▶│   │  (Foreground Service)    │   │    │
│  │ Wired Mic      │        │   │  - Device listener        │   │    │
│  │ (INPUT ONLY)   │        │   │  - Routing policy enforcer│   │    │
│  └───────────────┘        │   │  - Communication Device   │   │    │
│                             │   │    selector API calls     │   │    │
│                             │   └─────────────────────────┘   │    │
│                             │              │                    │    │
│                             │              ▼                    │    │
│                             │   ┌─────────────────────────┐   │    │
│                             │   │   OmeTV App (WebRTC)      │   │    │
│                             │   │   MODE_IN_COMMUNICATION   │   │    │
│                             │   │   reads current input/    │   │    │
│                             │   │   output device from OS   │   │    │
│                             │   └─────────────────────────┘   │    │
│                             └────────────────────────────────┘    │
└─────────────────────────────────────────────────────────────────┘
```

**Core idea:** Your app doesn't inject itself into OmeTV's call. It continuously **enforces a routing policy at the OS level** (output → Bluetooth SCO, input → USB/wired mic) the moment `MODE_IN_COMMUNICATION` is detected, and keeps re-asserting it if the OS or OmeTV tries to reset it back to "Bluetooth handles both."

---

## 3. Component Breakdown

### 3.1 Device Discovery Layer
Responsible for enumerating and labeling every audio device the phone can see.

- Uses `AudioManager.getDevices(GET_DEVICES_INPUTS | GET_DEVICES_OUTPUTS)`
- Listens for hot-plug/connect events:
  - `BroadcastReceiver` for `BluetoothDevice.ACTION_ACL_CONNECTED` / `ACTION_ACL_DISCONNECTED`
  - `AudioDeviceCallback.onAudioDevicesAdded()` / `onAudioDevicesRemoved()` (preferred modern approach — fires for USB, wired, Bluetooth uniformly)
  - `UsbManager` broadcast receiver for raw USB attach/detach
- Outputs a clean list to the UI: `[Name, Type, Direction, isConnected]`

### 3.2 Routing Policy Engine (Core Feature)
This is the actual "split" logic.

**Output side (Incoming voice → Earbuds):**
```kotlin
val outputDevices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
val btDevice = outputDevices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO }

btDevice?.let {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        audioManager.setCommunicationDevice(it)
    } else {
        audioManager.startBluetoothSco()
        audioManager.isBluetoothScoOn = true
    }
}
```

**Input side (Your voice → External Mic, bypassing Bluetooth mic):**
```kotlin
val inputDevices = audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)
val externalMic = inputDevices.firstOrNull {
    it.type == AudioDeviceInfo.TYPE_USB_HEADSET || it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET
}
// Note: setPreferredDevice() is called on the AudioRecord instance that the calling
// app (or your monitoring pipe) creates — not directly on AudioManager.
```

**Why input routing is trickier:** Unlike output, Android does not expose a single global "preferred call input device" switch to third-party apps for *another app's* ongoing `AudioRecord` session. Two real-world strategies exist:

| Strategy | How it works | Reliability |
|---|---|---|
| **A. System routing bias** | Force the Bluetooth SCO mic path *off* (`isBluetoothScoOn = false` for the mic path while keeping SCO on for playback isn't cleanly separable pre-Android 12) so the OS falls back to the next-priority input, which — if the external mic is plugged in — becomes the wired/USB mic automatically. | Medium — depends on OEM audio HAL priority order. Works well on many stock Android/Pixel-like devices; inconsistent on heavily customized OEM skins. |
| **B. `setPreferredDevice()` on Android 12+ Communication Device API** | `setCommunicationDevice(AudioDeviceInfo)` primarily targets **output**, but pairing it with explicit `AudioDeviceInfo.TYPE_USB_HEADSET`/`TYPE_WIRED_HEADSET` as the *input* preference (where OEM supports `AudioManager.setPreferredDevice` scoped per `AudioRecord`) gives more deterministic results — but your app must own or intercept the `AudioRecord`, which it doesn't for OmeTV's internal one. | Higher precision but **only works fully for audio recorded by your own app**, not silently for OmeTV's internal call stream. |

**Realistic conclusion:** True bulletproof "OmeTV's call literally reads from the USB mic" split-routing across all Android OEMs cannot be 100% guaranteed by any third-party app, because Android intentionally isolates each app's audio session for privacy/security. What you *can* build — and what most successful apps in this space actually do — is:

1. Force output to Bluetooth (`highly reliable`, native API).
2. Force the system input route away from the Bluetooth SCO mic in favor of the external mic (`reliable on most modern devices`, native API + hot-plug priority).
3. Continuously re-assert this policy via a foreground service, since some OEMs/apps try to "helpfully" reset it back to Bluetooth-for-both a few seconds into a call.

This gives you the split behavior in the vast majority of real-world cases without needing to build a full virtual-audio-driver (which would require root or a custom HAL — out of scope for a normal published app).

### 3.3 Foreground Service (Persistence Layer)
- Runs as a `ForegroundService` with `mediaPlayback`/`microphone` service type (Android 14+ requires declaring the specific foreground service type).
- Holds a persistent notification ("Split Routing Active: Bluetooth Out / USB Mic In").
- Re-applies the routing policy every time it detects:
  - `AudioManager.ACTION_HEADSET_PLUG`
  - Audio mode change to `MODE_IN_COMMUNICATION`
  - Bluetooth SCO state change broadcasts (`ACTION_SCO_AUDIO_STATE_UPDATED`)
- This "re-assert loop" is what defeats Android/OEM attempts to silently revert routing mid-call.

### 3.4 UI Layer — Screen-Based Routing Controller
Final UI design (see `audiolink-ui-spec.md` for the full Stitch-ready spec) uses **sequential screens instead of a side-by-side matrix**, to keep the interface minimal and monochrome:

```
Home ("Call routing")
 ├─ Row: Output · hearing → [Output device screen] → back → Home
 ├─ Row: Input · speaking → [Input device screen]  → back → Home
 ├─ Row: Applies to       → [Apps screen]          → back → Home
 └─ Master switch: Split routing (on/off, no navigation)
```

Each device screen shows a single vertical selectable list (Material-style switch per row) rather than two lists shown simultaneously — the user picks output, saves/returns, then separately picks input. This keeps each screen to one decision instead of two competing ones.

### 3.5 Test Tone Verification (Output Screen)
Lets the user confirm *which physical device* is actually receiving the routed output, rather than trusting the label alone.

- A short built-in sample tone (bundled asset, e.g. 2–3 second chime/beep, mono, 44.1kHz) is played through `AudioTrack` using the **currently selected communication device** (`setCommunicationDevice()` still applied), not through default media routing — otherwise the test wouldn't reflect the real call-routing path.
- UI: single play/pause control + a label that updates live to `"Playing sample tone through [device name]…"`.
- Implementation note: because `MODE_IN_COMMUNICATION` streams and `MODE_NORMAL` media streams can route differently on some OEMs, the tone should be played while the service **forces the audio mode to `MODE_IN_COMMUNICATION`** for the duration of the test, so the result reflects the actual call-time routing rather than media-mode routing.

### 3.6 Live Input Spectrum Meter (Input Screen)
Gives the user real-time visual confirmation that the *selected* microphone is the one actually capturing audio.

- A lightweight `AudioRecord` instance is opened **only while the Input screen is visible** (not during an actual call — this is a preview/verification instance, separate from the call's own audio session), targeting the currently selected input device via `AudioDeviceInfo`/`setPreferredDevice()` where supported.
- Amplitude is read in short buffers (e.g. every 100–150ms), converted to a simple RMS/peak level, and rendered as a bar-style meter (~20–24 bars) in the UI.
- The meter's caption updates to `"via [selected mic]"` whenever the user changes the selected input, so it's unambiguous which device is being visualized.
- This `AudioRecord` instance is torn down when the user leaves the screen, to avoid holding the microphone open unnecessarily and to avoid conflicting with OmeTV's own mic session during an actual call.

### 3.7 App Scope Engine ("Applies to")
Lets the user restrict automatic split routing to specific apps, or leave it global.

- **"All apps" (default on):** the Foreground Service (§3.3) applies the routing policy to *any* app session that enters `MODE_IN_COMMUNICATION` — no foreground-app check performed.
- **Specific apps selected:** the service additionally checks which app is currently in the foreground before applying/re-asserting routing. This requires:
  - `UsageStatsManager` (with `PACKAGE_USAGE_STATS` special access, granted manually by the user via Settings — this is a restricted permission, not a runtime dialog) **or**
  - `ActivityManager.getRunningAppProcesses()` (limited/deprecated on modern Android — `UsageStatsManager` is the more reliable path) **or**
  - a lighter-weight approach: an `AccessibilityService` that reports window/foreground-app changes (heavier permission ask, higher Play Store scrutiny — only use if `UsageStatsManager` proves insufficient in testing)
- App list for the multi-select UI is built from `PackageManager.getInstalledApplications()`, filtered to user-facing apps (exclude system packages) and ideally biased toward apps with `CATEGORY_VIDEO`/call-capable intents where feasible, though a full unfiltered list is a safe fallback.
- **Important scoping caveat (carried from §1):** this is *app-triggered automation*, not OS-level per-app audio isolation — Android does not offer the latter to third-party apps. "3 apps selected" means "auto-apply this routing policy when one of these 3 apps is foregrounded and a communication-mode session starts," not "only these 3 apps are physically capable of receiving the routed audio."

### 3.8 Theming Layer (Light / Dark)
- Built with Jetpack Compose `MaterialTheme`, driven by a single monochrome color scheme swapped between light and dark token sets (no accent colors in either — see UI spec §1).
- Defaults to `isSystemInDarkTheme()`; user can override via a toggle on the Home screen, persisted in `DataStore` alongside the last-selected devices and app-scope list.
- All four screens share one theme source — no per-screen theme logic needed.

### 3.9 Permissions Required (Updated)
| Permission | Purpose |
|---|---|
| `BLUETOOTH_CONNECT` | Read paired device names, connect state (Android 12+) |
| `RECORD_AUDIO` | Enumerate/verify mic input, monitor levels, run the input spectrum preview |
| `MODIFY_AUDIO_SETTINGS` | Change audio mode & routing |
| `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_MICROPHONE` | Keep routing alive while a selected app is in a call (Android 14+ split types) |
| `POST_NOTIFICATIONS` | Show persistent service notification (Android 13+) |
| `PACKAGE_USAGE_STATS` (special access, user-granted via Settings) | Only needed if the user selects specific apps in §3.7 instead of "All apps" — used to detect the current foreground app |
| `QUERY_ALL_PACKAGES` (or a scoped intent query, preferred where possible) | Populate the app list on the "Applies to" screen from installed apps |

---

## 4. End-to-End Data/Control Flow (Call Session Walkthrough)

1. User opens **AudioLink**, sees Bluetooth earbuds + USB mic listed on the Output and Input screens respectively.
2. User picks the output device on the Output screen and taps the test tone control to confirm it plays through the earbuds; returns to Home.
3. User picks the input device on the Input screen and watches the live spectrum meter react while speaking, to confirm it's reading from the external mic; returns to Home.
4. User optionally sets "Applies to" — leaves it on "All apps," or selects specific apps (e.g. OmeTV, Google Meet).
5. User flips the master "Split routing" switch on. App starts its **Foreground Service**, registers `AudioDeviceCallback` and Bluetooth/headset broadcast receivers, and (if specific apps were selected) begins foreground-app polling via `UsageStatsManager`.
6. Service sets `audioManager.mode = MODE_IN_COMMUNICATION` preemptively (or waits and detects when the target app sets it).
7. Service calls `setCommunicationDevice(bluetoothScoDevice)` → **output locked to earbuds**.
8. Service biases input selection toward `TYPE_USB_HEADSET` (via SCO-off-for-input trick or `setPreferredDevice` where supported) → **input biased to external mic**.
9. User opens OmeTV (or another selected app), starts a call. Its WebRTC layer queries `AudioManager` for current devices — receives the routing AudioLink already established.
10. Service keeps listening; if the OS or the calling app tries to reassert "Bluetooth for both," the service immediately re-applies the split (loop with debounce, e.g. every 1–2 seconds or event-driven).
11. Status bar shows the persistent "AudioLink — Split routing active" notification for the entire duration; disappears the instant the master switch is turned off.
12. Call ends → service can optionally auto-revert to default routing, or stay armed for the next call.

---

## 5. Major Engineering Challenges & Mitigations

| # | Challenge | Mitigation |
|---|---|---|
| 1 | Android couples Bluetooth SCO input+output as one unit | Use `setCommunicationDevice()` for output + input-priority biasing (§3.2) instead of trying to force a full manual split, which isn't publicly exposed |
| 2 | OEM audio HALs (Samsung, Xiaomi, Oppo, etc.) behave inconsistently | Test matrix across major OEMs; provide a fallback UI warning "Split routing not fully supported on this device" using a capability probe at first launch |
| 3 | Third-party app (OmeTV) may reset routing on its own call-start logic | Persistent foreground service that re-applies routing on every relevant broadcast, with a short polling fallback |
| 4 | Android 13/14 background restrictions on foreground services | Declare correct `foregroundServiceType` (`microphone`, `connectedDevice`), request `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` respectfully with user consent |
| 5 | Latency/echo if you build a custom audio pipe instead of relying on system routing | Prefer **pure system-routing approach** (no custom audio pipe) as default; only use Oboe/AAudio low-latency pipe as an *advanced fallback* for unsupported devices, since custom pipes add latency and battery cost |
| 6 | Privacy/Play Store policy scrutiny on `RECORD_AUDIO` + accessibility-adjacent behavior | Be explicit in the Play Store listing and in-app disclosures that the app does not record or transmit audio itself — it only changes routing preferences |
| 7 | `PACKAGE_USAGE_STATS`/foreground-app detection for the app-scope feature adds another restricted-permission ask | Keep "All apps" as the default so most users never need to grant it; only request it when the user explicitly opts into per-app scoping on the Apps screen |

---

## 6. Tech Stack

| Layer | Choice | Reasoning |
|---|---|---|
| Language | **Kotlin** | First-class support for modern `AudioManager`/`AudioDeviceCallback` APIs |
| UI toolkit | **Jetpack Compose** | Native support for `MaterialTheme` light/dark switching and simple state-driven screen navigation (Home → Output/Input/Apps) |
| Min SDK | **API 26 (Android 8)**, target **API 34+** | Communication Device API needs Android 12 (API 31) for full precision; app should gracefully degrade below that using legacy SCO calls |
| Architecture pattern | **MVVM** with a single `RoutingRepository` as source of truth | Keeps UI reactive to live device/state changes |
| Background execution | `ForegroundService` + `WorkManager` (for periodic re-assert fallback) | Reliable persistence within Android's background execution limits |
| Foreground-app detection | `UsageStatsManager` | Powers the optional per-app scoping in §3.7; only initialized if the user opts out of "All apps" |
| Optional low-latency layer | **Oboe (C++/NDK)** — only if a custom audio pipe fallback is built for unsupported OEMs | Google's recommended low-latency audio library |
| Local storage | `DataStore` (Preferences) | Save last-used routing configuration, theme preference, and app-scope selection |
| Dependency injection | Hilt | Standard for services + ViewModels |

---

## 7. Development Roadmap (Phased)

**Phase 1 — Device Awareness (Week 1–2)**
- Build UI that lists all connected input/output devices with correct names/types using `AudioManager.getDevices()`.
- Add hot-plug detection (`AudioDeviceCallback`).

**Phase 2 — Media-Mode Routing Proof of Concept (Week 3)**
- Implement basic output switching in `MODE_NORMAL` (e.g., force YouTube audio to a specific output).
- Validate `AudioDeviceInfo` type detection accuracy across at least 3 test devices.

**Phase 3 — Communication-Mode Split (Week 4–6)**
- Implement `MODE_IN_COMMUNICATION` handling.
- Implement output lock to Bluetooth SCO via `setCommunicationDevice()`.
- Implement input bias toward external mic.
- Build the Foreground Service + re-assert loop.

**Phase 4 — Verification Features (Week 7)**
- Build the test tone player on the Output screen (§3.5).
- Build the live input spectrum meter on the Input screen (§3.6).
- Build light/dark theming across all screens (§3.8).

**Phase 5 — App Scope & Real-World Integration Testing (Week 8–9)**
- Implement the "Applies to" screen and `UsageStatsManager`-based foreground detection (§3.7).
- Test specifically with OmeTV (and 1–2 other VOIP apps: Google Meet, WhatsApp call) to confirm the routing is actually respected by their WebRTC layers, both in "All apps" and per-app scoped modes.
- Build a device-compatibility matrix (Pixel, Samsung, Xiaomi, OnePlus).

**Phase 6 — Polish, Permissions, Battery (Week 10)**
- Runtime permission flows, battery optimization exemption prompts, notification channel setup.
- Graceful fallback messaging for unsupported OEMs.

**Phase 7 — QA, Play Store Prep, Deployment (Week 11–12)**
- See §9 below.

---

## 8. Testing Strategy

| Test Type | What to Verify |
|---|---|
| Unit tests | Device classification logic, routing-decision logic (pure functions, mockable `AudioManager`) |
| Instrumented tests | Foreground service lifecycle, permission flows, broadcast receiver registration/unregistration |
| Manual device matrix | At minimum: 1 stock Android (Pixel), 1 Samsung (One UI), 1 Xiaomi (MIUI/HyperOS), 1 OnePlus/Oppo (ColorOS) |
| Real-call testing | OmeTV, Google Meet, WhatsApp — confirm actual audio path with a second physical device listening in |
| Battery/behavior testing | Foreground service running for 30+ min continuous call; check for OS killing the service |
| Regression testing | Bluetooth disconnect mid-call, USB mic unplug mid-call — app should recover gracefully, not crash |
| Feature testing — test tone | Confirm the tone genuinely plays through the currently selected output device, not the default media output |
| Feature testing — spectrum meter | Confirm bars respond to actual mic input and correctly reflect the selected device when switching between mics |
| Feature testing — app scope | Confirm routing only auto-applies for selected apps when specific apps are chosen, and applies universally when "All apps" is on |
| Feature testing — theming | Confirm all four screens render correctly in both light and dark mode, including system-theme-follow behavior |

---

## 9. Deployment Plan

### 9.1 Build & Signing
- Use Android App Bundle (`.aab`) format for Play Store.
- Generate an upload keystore (`keytool -genkey -v -keystore upload-keystore.jks -keyalg RSA -keysize 2048 -validity 10000 -alias upload`), store securely (never commit to git).
- Configure Play App Signing (Google manages the final signing key; you keep the upload key).

### 9.2 Play Store Listing Requirements
- **Data Safety form**: Must disclose microphone access, explain that no audio is recorded/stored/transmitted — only routing preference is changed.
- **Permissions justification**: Google increasingly requires a short video or explanation for `RECORD_AUDIO` and background microphone use — prepare this in advance to avoid rejection.
- **Privacy Policy URL**: Mandatory given microphone + Bluetooth permissions.

### 9.3 CI/CD Pipeline (suggested)
```
GitHub repo
   │
   ▼
GitHub Actions (on push to main / tag)
   ├─ ./gradlew test              (unit tests)
   ├─ ./gradlew connectedCheck    (instrumented tests, optional on emulator matrix)
   ├─ ./gradlew bundleRelease     (build signed .aab)
   └─ Upload to Play Console via Fastlane / Google Play Developer API (internal testing track)
```

### 9.4 Release Tracks
1. **Internal testing** → your own devices + close testers (validate the OEM matrix).
2. **Closed testing (beta)** → 20–50 real OmeTV-type users, collect device model + routing success feedback via in-app quick survey/log upload (opt-in only).
3. **Production rollout** → staged rollout (5% → 20% → 50% → 100%) so you can catch OEM-specific crash spikes early via Play Console vitals.

### 9.5 Post-Launch Monitoring
- Firebase Crashlytics for crash/ANR tracking.
- Custom analytics event (privacy-respecting, no audio content) logging: device model, Android version, whether split-routing succeeded or fell back — this becomes your real-world compatibility matrix over time.

---

## 10. Realistic Scope Statement (Important)

To set correct expectations for a supervisor/mentor or for your own planning:

- ✅ **Reliably achievable:** Output → Bluetooth earbuds during calls (native, well-supported API).
- ✅ **Achievable on most modern devices:** Input → external USB/wired mic bias during calls, via device-priority manipulation and Android 12+ Communication Device APIs.
- ⚠️ **Not guaranteed on 100% of devices:** Exact behavior depends on OEM audio HAL implementation; some older or heavily customized Android skins may not fully respect the input bias.
- ❌ **Out of scope without root/custom HAL:** A universal, driver-level "virtual audio cable" that guarantees the split on *every* device and *every* app regardless of how that app requests audio. That would require system-level (root) access or being a preinstalled OEM component — not achievable as a normal Play Store app.

This scope statement is the honest technical boundary and is worth including explicitly in any academic (FYP) or professional documentation, since it demonstrates depth of understanding rather than overpromising.

---

## 11. Suggested Repository Structure

```
audiolink/
├── app/
│   ├── src/main/java/com/rayyanshehzad/audiolink/
│   │   ├── ui/
│   │   │   ├── home/            # Home screen (3 rows + master switch + theme toggle)
│   │   │   ├── output/          # Output device screen + test tone control
│   │   │   ├── input/           # Input device screen + live spectrum meter
│   │   │   ├── apps/            # "Applies to" screen (all apps / multi-select)
│   │   │   └── theme/           # Compose MaterialTheme, light/dark color sets
│   │   ├── service/             # ForegroundService + routing re-assert logic
│   │   ├── data/                 # RoutingRepository, DataStore preferences (devices, theme, app scope)
│   │   ├── audio/                # AudioManager wrappers, device classification, test tone player, spectrum reader
│   │   ├── appscope/             # UsageStatsManager wrapper, installed-app listing
│   │   └── di/                   # Hilt modules
│   └── src/test/                 # Unit tests
├── docs/
│   ├── architecture.md           # This document
│   └── audiolink-ui-spec.md      # Stitch-ready UI design spec
├── .github/workflows/ci.yml      # CI/CD pipeline
└── README.md
```

---

## 12. Summary

The core of **AudioLink** is not a single trick but a **persistent, re-asserting system-level routing policy**: lock call output to Bluetooth via the public `setCommunicationDevice()`/SCO APIs, bias call input away from the Bluetooth mic toward the external USB/wired mic using device priority and (on Android 12+) preferred-device APIs, and keep a foreground service alive to defend that configuration for the duration of the call — since third-party call apps like OmeTV rely on the same shared `AudioManager` state rather than bypassing it. Around that core, the app adds three practical layers: a **test tone** and **live input spectrum meter** so the user can verify routing is actually correct rather than trusting labels alone, an **app scope** setting so routing can auto-apply either universally or only for chosen apps, and full **light/dark theming** on a minimal, four-screen, monochrome interface. This is achievable as a legitimate, publishable Android app using only public SDK APIs and standard permissions, with the one honest caveat that OEM-level consistency (not app-level capability) is the main variable affecting reliability across different phone brands.
