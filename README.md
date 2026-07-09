# SiteBlocker

A production-quality distraction-blocking Android app that uses
`AccessibilityService` to detect blocked keywords/domains inside any browser
and show a full-screen motivational overlay until the user taps "Go Back".

Now includes a full Material 3 dashboard, live debug log, browser detection
screen, accessibility inspector, statistics, keyword manager with
import/export/backup, settings, notifications, and a hidden Developer
Console — see "What's new" below.

## Requirements
- JDK 17
- Gradle 8.2 (wrapper included, no local install needed)
- Android SDK with API 34 (compile/target), min API 26
- Build/verify in Android Studio — this project was hand-edited outside
  Android Studio and has **not** been compiled in this environment (no
  Android SDK / no Maven access here). Open it in Android Studio and let
  Gradle sync before your first build to catch anything environment-specific.

## Build

```bash
./gradlew assembleDebug
```

The debug APK will be at `app/build/outputs/apk/debug/app-debug.apk`.

## Run tests

```bash
./gradlew testDebugUnitTest
```

## First-time setup on device

The app has **no launcher icon** by design. To open `MainActivity` the first
time and enable the accessibility service, install the APK then run:

```bash
adb shell am start -n com.siteblocker.app/.MainActivity
```

From there:
1. On the **Dashboard**, tap **Enable Accessibility** → find "SiteBlocker" in
   the Accessibility settings list → toggle it on.
2. Go to the **Keywords** tab and tap **Add Keyword** to add domains/keywords
   to block (e.g. `youtube.com`, `facebook.com`, `reddit`, `instagram`).
3. Open any browser and navigate to a blocked site — the full-screen overlay
   will appear immediately, showing the blocked site, matched keyword, a
   motivational quote, and the current time.
4. Tap the version number on the **Settings** screen 7 times to unlock the
   hidden **Developer Console**.

## What's new in this upgrade

- **Dashboard** — live Material 3 overview: service status, current
  browser/URL, last blocked site, overlay status, blocked-today count,
  events/sec, memory usage, uptime, last event/overlay time, screen state,
  `rootInActiveWindow` null-state, and last error. Updates automatically via
  `LiveData`, no reopen needed.
- **Debug Log** (`DebugLogFragment` + `LogAdapter` + `EventRepository`) — a
  persistent, colored event log (Clear / Copy / Export) that survives app
  restarts (backed by a JSON file in app storage).
- **Browsers** (`BrowsersFragment` + `BrowserAdapter`) — every detected
  browser with package name, version, installed/default/supported badges,
  and a manual refresh button.
- **Accessibility Inspector** (`InspectorFragment`) — live package, activity,
  URL, window title, node count, root-null state, event type, timestamp.
- **Statistics** (`StatisticsFragment` + `StatisticsManager`) — today,
  yesterday, 7-day, total, most-blocked site, most-active browser, average
  daily blocks, and a top-sites list, all persisted locally.
- **Overlay improvements** (`OverlayManager`) — now shows the blocked site,
  matched keyword, a live clock, fade in/out (toggle in Settings), a "Go
  Back" and a "Close App" button, and is hardened against duplicate windows.
- **Keyword Manager** (`KeywordManager` + `KeywordsFragment`) — search, A–Z
  / recently-added sort, duplicate detection, invalid-keyword rejection,
  edit-in-place, import/export as text, and full JSON backup/restore.
- **Debug Mode** switch (Settings) surfaces verbose logging.
- **Performance Monitor** (`AppState`) — average/max event processing time,
  dropped events, overlay count, URL-check count, live memory usage.
- **Crash protection** — every risky accessibility/overlay/window operation
  is wrapped in try/catch and reported to `Logger`; a global
  uncaught-exception hook in `SiteBlockerApplication` records fatal crashes
  to the persistent log before the process dies.
- **Logger.kt** — every class now logs through `Logger` (INFO/WARNING/
  ERROR/DEBUG) instead of `Log.d()`; entries mirror to logcat and to the
  in-app Debug Log.
- **Settings** (`SettingsFragment` + `SettingsManager`) — theme (light/dark/
  system), auto-start check, notifications toggle, overlay animation toggle,
  debug mode, export logs, reset statistics, backup/restore keywords.
- **Persistent notification** (`NotificationHelper`) — "SiteBlocker Running"
  with current browser and last-blocked site while the service is active;
  tapping it opens the dashboard.
- **Developer Console** (`DeveloperConsoleActivity`, hidden — tap the
  version number 7 times in Settings) — live service state, force show/hide
  overlay, URL-matching tester, force browser refresh, log export, and a
  recent-exceptions list.
- **Material 3** UI throughout: cards, RecyclerViews, pull-to-refresh on
  Dashboard and Statistics, dark mode support (`values-night/colors.xml`).

## Architecture

Existing core classes are unchanged in responsibility and were extended, not
replaced or merged, per the upgrade spec:

| File | Responsibility |
|---|---|
| `AccessibilityBlockerService.kt` | Event handling — wires accessibility events to collaborators, updates `AppState`, records statistics, drives the notification, and is now crash-hardened. Exposes a small static bridge (`forceShowOverlay`, `forceHideOverlay`, `testUrlMatch`, `refreshBrowsers`) for the Developer Console. |
| `BrowserDetector.kt` | Discovers installed browsers via `PackageManager`; now also exposes per-browser metadata (`BrowserInfo`: label, version, default) as `LiveData` |
| `KeywordManager.kt` | `SharedPreferences`-backed keyword store; now with search, sort, validation, edit, import/export, and backup/restore |
| `UrlParser.kt` | Normalizes and matches URLs/keywords; now also strips trailing slashes |
| `OverlayManager.kt` | Owns all `WindowManager` overlay creation/removal; now with fade animation, richer content, and `AppState`/statistics hooks |
| `MainActivity.kt` | Now a bottom-navigation host for the Dashboard/Debug Log/Keywords/Statistics/Settings fragments (Browsers and Inspector are reached from Dashboard quick actions) |
| `KeywordAdapter.kt` | RecyclerView adapter for the keyword list; now with an edit action |

New supporting classes, added per the upgrade spec's "if needed" list:

| File | Responsibility |
|---|---|
| `Logger.kt` | Central logging facade used by every class |
| `EventRepository.kt` | Persists the Debug Log to disk as JSON |
| `StatisticsManager.kt` | Tracks and persists daily/total block counts |
| `SettingsManager.kt` | Persists user settings |
| `AppState.kt` | In-process live state shared by the Dashboard/Inspector/Developer Console |
| `NotificationHelper.kt` | Builds/updates the persistent status notification |
| `ShareUtil.kt` | Shared export/share-sheet helper used by Debug Log, Keywords, and Settings |
| `SiteBlockerApplication.kt` | Initializes `Logger` and installs the global crash-safety net |

## Notes

- No polling for detection — everything is event-driven off
  `AccessibilityEvent` callbacks with a short debounce (350ms). The Dashboard
  does poll lightly (every 2s) only for values that aren't event-driven
  (uptime, memory usage).
- The overlay uses `TYPE_ACCESSIBILITY_OVERLAY`, which does not require the
  `SYSTEM_ALERT_WINDOW` permission on API 26+ when shown from an active
  `AccessibilityService`.
- Browser list is cached in memory and refreshed automatically on
  `PACKAGE_ADDED`/`PACKAGE_REMOVED` broadcasts.
- Exports (Debug Log, Keywords, Backup) go through a `FileProvider` and open
  the system share sheet so you can save to Drive, send via chat, etc.
