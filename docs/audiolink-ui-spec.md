# AudioLink — UI Design Spec (for Google Stitch)

**App name:** AudioLink
**Developer:** Muhammad Rayyan Shehzad

Use this document as the design brief / prompt input for Google Stitch. It contains
a consolidated prompt at the top (paste this directly), followed by the full
screen-by-screen breakdown for reference or manual refinement inside Stitch.

---

## Consolidated Stitch Prompt (paste this)

```
Design a minimalist, monochrome, professional Android app called "AudioLink" by
developer Muhammad Rayyan Shehzad that lets a user route call audio: incoming
voice to one device (e.g. Bluetooth earbuds) and outgoing mic input to a
different device (e.g. external USB microphone). Style: flat, black/white/gray
only, no gradients, no shadows except subtle hairline borders, generous
whitespace, sentence case text, Material-style toggle switches, rounded cards
(12-16dp radius), system default sans-serif font. Feel: calm utility app, not
consumer/social — closer to a settings screen than a media app. Support both a
light theme (white/near-white surfaces, black text) and a dark theme (near-black
surfaces, off-white text) with an identical layout in both — only surface, text,
and border colors invert; no accent colors in either theme.

Screens needed:

1. Home screen ("Call routing")
   - Small top-right status pill: dot + "Routing active" (only visible when on)
   - Two tappable collapsed rows, each showing an icon, a small label ("Output ·
     hearing" / "Input · speaking"), the currently selected device name in bold,
     and a chevron-right — tapping opens a dedicated screen (do not show device
     lists on this screen)
   - Bottom: a single master toggle switch row labeled "Split routing"
   - A small theme toggle (sun/moon icon or "Light / Dark" switch) tucked in the
     top-left or as a minor row near the bottom — not a headline feature, just a
     quiet control
   - A third collapsed row, same style as the two above: icon "apps", label
     "Applies to", value either "All apps" or "[N] apps selected" — tapping
     opens Screen 4

2. Output device screen
   - Back arrow + "Call routing" label at top
   - Title "Output device"
   - Vertical list of selectable rows (radio-style using toggle switches), one
     per device: e.g. "AirPods Pro", "Phone speaker", "Wired headphones" — active
     one shows a filled switch and a small "active" tag
   - Below the list, a card containing a "Test tone" control: circular play/pause
     button + one line of helper text that updates to "Playing sample tone
     through [device name]…" while active

3. Input device screen
   - Back arrow + "Call routing" label at top
   - Title "Input device"
   - Same style selectable list: "AirPods Pro mic", "External USB mic", "Phone
     built-in mic"
   - Below the list, a card containing a live input level meter: a row of ~24
     thin vertical bars of varying height (simulating a mic spectrum), labeled
     "Live input level" with a small caption "via [selected mic]"
   - Below the meter, a muted one-line compatibility note that only appears for
     certain devices: "Input switching may not be fully supported on this
     device."

4. Apps screen ("Applies to")
   - Back arrow + "Call routing" label at top
   - Title "Applies to", one-line subtitle "Choose which apps split routing
     turns on for automatically"
   - A single row at the top: "All apps" label + small helper line "Applies
     system-wide to any app using call audio" + a toggle switch
   - Below it, a muted section label "Or select specific apps"
   - A vertical list of installed apps (e.g. OmeTV, Google Meet, WhatsApp,
     Zoom, Skype, Telegram), each row with the app name and a checkbox-style
     toggle switch for multi-select
   - When "All apps" is on, the list below is visually dimmed and disabled
     (not hidden) so the user still sees what would otherwise be selectable
   - When "All apps" is off, the list becomes active and the user can check
     any number of apps individually

5. System notification (reference only, not a designed screen)
   - Persistent Android notification shown whenever split routing is on:
     title "AudioLink", body "Split routing active — Bluetooth out / USB
     mic in", small monochrome status bar icon.

Keep every screen sparse — one primary action per screen, no decorative icons,
no color accents, no dark backgrounds behind status text. This is a small,
single-purpose utility app, not a dashboard.
```

---

## 1. Design System

| Element | Spec |
|---|---|
| Palette | Monochrome only — black, white, grayscale. No accent colors, no semantic color roles (no red/green/blue) |
| Background | Light theme: white / near-white surface, light gray for secondary surfaces. Dark theme: near-black surface, dark gray for secondary surfaces |
| Theming | Full light and dark theme support — identical layout in both, only surface/text/border values invert. Follows system theme by default, with a manual override toggle on the home screen |
| Borders | 0.5–1dp hairline gray (light: dark-on-light gray, dark: light-on-dark gray), used instead of shadows |
| Corner radius | 12–16dp on cards, pill/full-round on switches and status dots |
| Typography | System sans-serif; sentence case everywhere; no ALL CAPS, no title case |
| Type scale | Title ~22sp/medium, section title ~18sp/medium, body ~15sp/regular, caption/helper ~12–13sp/regular muted gray |
| Iconography | Outline-style icons only (headphones, microphone, chevron, arrow-left, play/pause) — no filled/colored icons |
| Controls | Google Material-style Switch (track + thumb) reused for both on/off toggles and single-select list rows |
| Motion | Minimal — only the live input meter animates; no transition flourishes, no bouncing, no confetti/celebration states |
| Tone | Calm utility/settings app, not a media or social app |

---

## 2. Screens

### Screen 1 — Home ("Call routing")
**Purpose:** Single landing screen. Shows current state at a glance, nothing more.

- Top-right status pill (conditional): small filled dot + "Routing active"
- Page title: "Call routing"
- Row 1 (tap target → Screen 2): icon (headphones), label "Output · hearing", value "AirPods Pro", chevron
- Row 2 (tap target → Screen 3): icon (microphone), label "Input · speaking", value "External USB mic", chevron
- Row 3 (tap target → Screen 4): icon (apps grid), label "Applies to", value "All apps" or "[N] apps selected", chevron
- Bottom row: label "Split routing" + master toggle switch
- Minor row or icon: theme toggle (Light / Dark / System) — placed below the master switch, visually secondary to it

**Explicitly excluded from this screen:** full device lists, settings, ads, onboarding tips, illustrations.

### Screen 2 — Output device
**Purpose:** Pick which device receives the other person's voice; verify with a test tone.

- Back navigation: arrow + "Call routing" text label
- Title: "Output device"
- Selectable list (radio behavior via switch UI):
  - AirPods Pro *(active state example)*
  - Phone speaker
  - Wired headphones
- Test tone card:
  - Circular play/pause button
  - Helper text: idle → "Play a sample tone to confirm it's coming through your selected device"; playing → "Playing sample tone through [device]…"

### Screen 3 — Input device
**Purpose:** Pick which device sends the user's voice; verify with a live level meter.

- Back navigation: arrow + "Call routing" text label
- Title: "Input device"
- Selectable list (radio behavior via switch UI):
  - AirPods Pro mic
  - External USB mic *(active state example)*
  - Phone built-in mic
- Live input meter card:
  - Header: "Live input level" + right-aligned caption "via [selected mic]"
  - ~24 animated vertical bars representing live amplitude
  - Conditional muted note: "Input switching may not be fully supported on this device." (shown only for devices flagged as lower-reliability — see architecture doc §5/§10)

### Screen 4 — Apps ("Applies to")
**Purpose:** Scope split routing to specific apps, or leave it system-wide.

- Back navigation: arrow + "Call routing" text label
- Title: "Applies to", subtitle: "Choose which apps split routing turns on for automatically."
- "All apps" row: label + one-line helper "Applies system-wide to any app using call audio" + toggle switch (default: on)
- Section label: "Or select specific apps" (dims when "All apps" is on)
- App list, each row = app name + toggle switch, multi-select:
  - OmeTV *(example, checked)*
  - Google Meet *(example, checked)*
  - WhatsApp
  - Zoom
  - Skype
  - Telegram
- Behavior: when "All apps" is on, the list stays visible but dimmed and non-interactive (so the user can see what's available without it being a dead end); switching "All apps" off re-enables the list for individual multi-select
- Home screen row 3 reflects the result: "All apps" or "[N] apps selected"

**Implementation note (not a design concern, but relevant for engineering handoff):** Android's audio routing is a shared system-level state, not something scoped per third-party app by the OS itself. In practice, "apps selected here" should be read as *"automatically apply split routing whenever one of these apps is in the foreground and a call/communication-mode audio session starts,"* rather than the OS enforcing per-app isolation. The foreground service (see architecture doc §3.3) watches for the selected apps coming to the foreground and the audio mode switching to `MODE_IN_COMMUNICATION`, then applies the same routing policy described in §3.2. "All apps" simply removes that foreground-app check and applies routing to any communication-mode session, regardless of which app triggered it.

### Reference — System notification (not a Stitch screen, context only)
Android requires a persistent notification whenever the foreground microphone
service is active. Include this only as a note for engineering handoff, not as
a screen to design in Stitch:
- Title: "AudioLink"
- Body: "Split routing active — Bluetooth out / USB mic in"
- Status bar: small monochrome icon, visible for the duration routing is on, disappears immediately when turned off
- Notification respects the device's current light/dark notification style automatically — no custom theming needed here, Android handles it

---

## 3. Navigation Flow

```
Home
 ├─ tap Output row ──▶ Output device screen ──▶ back ──▶ Home
 ├─ tap Input row ───▶ Input device screen ───▶ back ──▶ Home
 ├─ tap Applies to row ──▶ Apps screen ──▶ back ──▶ Home
 └─ toggle Split routing switch ──▶ (no navigation, in-place state change,
                                       triggers status pill + system notification)
```

No tab bar, no bottom navigation, no hamburger menu — four screens total, reached only by the three rows and back arrow.

---

## 4. Notes for Stitch Iteration

- If Stitch generates any color accents, ask it to convert to grayscale-only.
- If Stitch adds a bottom nav bar or extra tabs, ask it to remove — this is a 4-screen flat flow, not a multi-tab app.
- If Stitch's device list uses checkmarks instead of switches, you can request the Material Switch style explicitly, since that was the specific ask.
- Keep the test-tone and input-meter cards visually secondary (smaller, muted) to the device list itself — the list is the primary action, the meter/tone are verification aids.
- On the Apps screen, make sure Stitch dims (not hides) the app list when "All apps" is on — hiding it removes context, dimming shows the option is simply inactive.
- Ask Stitch explicitly for both light and dark theme variants of each screen if it only generates one by default — some tools default to light-only unless prompted.
- Confirm the app name "AudioLink" and developer credit "Muhammad Rayyan Shehzad" appear correctly if you ask Stitch to generate a splash/about screen — otherwise these are just metadata for your Play Store listing, not on-screen UI text.
