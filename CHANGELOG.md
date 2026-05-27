# Changelog

All notable changes are recorded here. New entries go on top.

The format loosely follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/);
versions follow [Semantic Versioning](https://semver.org/).

## [Unreleased]

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
