# Changelog

All notable changes are recorded here. New entries go on top.

The format loosely follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/);
versions follow [Semantic Versioning](https://semver.org/).

## [Unreleased]

## [1.5.0] — 2026-05-28

### Added
- Premium **Water Mode** featuring double-layer translucent wave rendering, rising wobbly gaseous bubbles, and gravity-accelerated splashes.
- **Spring-Damper Tilt Physics** for realistic, fluid sloshing dynamics driven by device accelerometer tilting.
- **Full-Screen Immersive Mode** utilizing AndroidX's `WindowInsetsControllerCompat` to completely hide status and navigation bars with transient swipe-to-reveal gestures.

### Changed
- Aligned physical tilting scaling and gravity-lean calculation limits for falling water and splash particles to achieve high-fidelity visual alignment.

## [1.4.0] — 2026-05-28

### Added
- **Gravity-aware screen rotation** that snaps visual content to four physical 90° orientations so sand/LEDs always fall downward toward gravity.
- **Proportional pile rescaling** across orientation changes to keep filling volume layout balanced.

### Improved
- Bottom **PAUSE / RESET** control pill readability: added dark semi-transparent backdrops and increased text alpha.

## [1.3.0] — 2026-05-28

### Added
- **LED Grid Mode** featuring a beautiful retro 16×32 pixel art sand/light simulation, toggleable via the top screen tabs.
- Accelerometer-driven **gyro response** causing in-flight sand columns to bend and piles to slump organically toward gravity.

### Improved
- Sand accumulation expanded to **100% full height coverage** on timer completion.
- Smoother grain rendering with 90 particle columns.

## [1.2.0] — 2026-05-27

### Added
- Soft three-note completion chime via `ToneGenerator` on the notification
  stream, alongside the existing vibration pulse.
- Persisted last H R / M I N / S E C via a `Preferences` DataStore
  (`timer_prefs`). Pickers are pre-seeded with the last started duration on
  every launch.
- `TimerPreferences` helper with clamped read/save and the `LastDuration`
  data class.

### Changed
- `androidx.datastore.preferences` dependency activated.

## [1.1.0] — 2026-05-27

### Added
- Quick presets (`1m / 3m / 5m / 10m / 25m / 1h`) on the setup screen.
- `Paused` timer state with `P A U S E` / `R E S U M E` controls and a
  dedicated paused screen showing remaining time + percent left.
- `KeepScreenOn()` composable keeps the display awake during the running phase
  and releases the flag the moment the timer leaves Running.

### Changed
- Bottom controls on the running screen now host both `P A U S E` and
  `R E S E T` instead of just reset.

## [1.0.0] — 2026-05-27

### Added
- Particle-physics hourglass timer.
- Setup screen with HR / MIN / SEC drag-or-tap pickers.
- Accelerometer-driven sand pile slumping.
- Touch-and-hold reveal of exact remaining time.
- Completion vibration pulse + endless breathing background pulse on the
  finished screen.
- GitHub Actions CI (`android-ci.yml`) and release pipeline (`release.yml`).
- Gradle wrapper checked in.
- `scripts/build_release.ps1` and `scripts/export-play-store-release.ps1` for
  one-command desktop export.
- Unit tests for the timer state machine and the particle system, plus a
  Roborazzi screenshot baseline of the setup screen.
