# Changelog

All notable changes are recorded here. New entries go on top.

The format loosely follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/);
versions follow [Semantic Versioning](https://semver.org/).

## [Unreleased]

## [1.7.0] — 2026-06-14

### Added
- 5 Premium interactive accelerometer-driven simulation modes:
  - **Magnetic Ferrofluid Mode (MAGNETIC)** featuring spiky fluid peaks, time-based sine wave micro-vibrations, and draggable virtual magnet attraction center.
  - **Aurora Flow Field Mode (AURORA)** featuring glowing color sweeps in a mathematical flow field, gravity-based sediment settling, and touch vortex swirl force.
  - **Rain & Ripples Mode (RAIN)** featuring sliding rain condensation drops on glass, collision raindrop merging, and radial wipe deflection on touch.
  - **Accretion Black Hole Mode (BLACKHOLE)** featuring orbiting star particles spiraling into a central gravitational singularity that grows stronger over time.
  - **Tesla Plasma Globe Mode (ELECTRIC)** featuring electric discharge arcs calculated by midpoint displacement recursion, convective anti-gravity rising, and touch-focused arcs with haptic micro-pulses.

## [1.6.0] — 2026-06-13

### Added
- Premium **Campfire-to-Embers (FIRE) Mode** featuring crossed firewood logs, progressive flame decay, breathing red-hot charcoal, and crackling spark particles.
- Accelerometer-driven **Interactive Tilt Physics** for fire flames and floating embers.

### Changed
- Upgraded the Gradle wrapper and project build configuration to **Gradle 9.5.1** to fix Windows Kotlin DSL script compilation conflicts.
- Converted calibration averages to Float inside `MainActivity.kt` to resolve Kotlin compilation type mismatches.

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
