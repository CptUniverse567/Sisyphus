# Sisyphus — MASTER PROMPT

> **NO ESCAPE. WALK.**

This document is the project's persistent source of truth. A future OpenCode session must read
this file completely before working on the project and treat it as authoritative for product
philosophy, architecture, engineering constraints, workflow, testing philosophy, OpenCode
operating rules, phase structure, agent behavior, and established decisions.

Do NOT rely on conversation history as the sole source of project requirements. The repository
must remain capable of reconstructing the project's current engineering/product context.

---

## 1. Product identity and philosophy

Sisyphus is a minimalist Android alarm app that **requires physical walking to silence the
alarm**. There is no snooze and no conventional stop button. The only way to silence the alarm is
to walk a configurable number of steps. Since v1.1 it supports **multiple independent alarms**,
each with its own schedule, enabled state, step requirement, and sound.

Core principle:

> **Make waking up require physical effort.**

No points, streaks, achievements, social systems, or unnecessary gamification. You set the burden.
The alarm rings. You walk. The stone eventually falls.

The app is **offline-first**: no account, no backend, no cloud synchronization.

## 2. Current validated architecture

Two-module Gradle project:

```text
:core   — pure Kotlin/JVM library, NO Android dependency (fully unit-testable, deterministic)
:app    — Android application module: sensors, alarms, services, persistence, permissions, Compose UI
```

The `:core` module is platform-independent. All Android specifics live behind interfaces defined in
`org.sisyphus.core.platform` and implemented by adapters in `:app`.

| Layer | Core files | Android adapter (`:app`) |
|---|---|---|
| State machine | `challenge/ChallengeStateMachine.kt` | — |
| Step accounting | `steps/StepAccountant.kt`, `steps/StepGuard.kt` | `platform/SensorManagerStepSensor.kt` |
| Alarm timing | `alarm/AlarmTimeCalculator.kt`, `alarm/AlarmScheduleManager.kt` | `platform/AlarmManagerScheduler.kt` |
| Multi-alarm model | `alarm/Alarm.kt`, `alarm/AlarmSpec.kt`, `alarm/AlarmRecurrenceCalculator.kt` | — |
| Persistence | `persistence/ChallengeRepository.kt`, `persistence/SettingsRepository.kt`, `persistence/AlarmRepository.kt` | `platform/SharedPrefsKeyValueStore.kt` |
| Sound | `sound/SoundResolver.kt` | `platform/MediaPlayerAlarmPlayer.kt`, `platform/AndroidSoundAvailability.kt` |
| Permissions/readiness | `permissions/ReadinessChecker.kt` | `platform/Support.kt` |
| Orchestrator | `engine/SisyphusEngine.kt` | `AppGraph` wiring (`SisyphusApp.kt`) |
| UI policy | `ui/ChallengeUi.kt` | `ui/MainActivity.kt`, `ui/SisyphusScreens.kt` |
| Runtime | `Clock`, `StepSensor`, `AlarmScheduler`, `AlarmPlayer`, `NotificationCenter` | `SystemClock`, foreground `SisyphusService` |

**The Activity is never the source of truth.** `SisyphusEngine` owns all state, persists every
mutation, and the UI renders `ChallengeViewState` snapshots.

### Platform interfaces (`core/.../platform/Platform.kt`)

- `Clock` — `currentTimeMillis()`, `zone()` (injectable time for deterministic tests).
- `StepSensor` — `status()`, `currentReading()`.
- `AlarmScheduler` — `schedule(fireAt, tag)`, `cancel(tag)`, `pendingFireAtMillis()`.
- `AlarmPlayer` — `start(ResolvedSound)`, `stop()`.
- `NotificationCenter` — `show(title, body)`, `dismiss()`.
- `KeyValueStore` — typed get/put/remove/clear.

## 3. Core challenge / state machine

Explicit state machine (`ChallengeState`):

```text
IDLE
  ↓
ARMED
  ↓
RINGING
  ↓
CHALLENGE_ACTIVE
  ↓
COMPLETED
  ↓
IDLE
```

Legal transitions (enforced in `ChallengeStateMachine`; invalid transitions throw):
- `arm(IDLE, steps, fireAt)` → `ARMED`
- `cancel(ARMED|RINGING)` → `IDLE`
- `alarmFired(ARMED)` → `RINGING`
- `startChallenge(RINGING, baseline)` → `CHALLENGE_ACTIVE`
- `progress(CHALLENGE_ACTIVE, additional)` → stays `CHALLENGE_ACTIVE` or → `COMPLETED`
- `complete(CHALLENGE_ACTIVE|COMPLETED)` → `COMPLETED`
- `acknowledge(COMPLETED)` → `IDLE`

`Challenge` data: `state`, `requiredSteps`, `completedSteps`, `sensorBaseline`, `alarmFireTimeMillis`.
Invariants: `completedSteps >= 0`, `completedSteps <= requiredSteps`.

## 4. Step accounting rules

- Step requirement range: `1..10000` (`StepRequirement`). Default 500.
- Presets (`StepPreset`): PEBBLE 500, BOULDER 1000, MOUNTAIN 2000, plus CUSTOM. (README also lists
  Stone 300 / Sisyphus 2000; the authoritative enum is PEBBLE/BOULDER/MOUNTAIN/CUSTOM.)
- Uses real hardware step sensors (`TYPE_STEP_COUNTER` / `TYPE_STEP_DETECTOR`).
- Progress is computed **relative to a challenge baseline** (`StepAccountant`), never from the
  device's lifetime step count directly.
- `StepGuard.maxSingleDelta` = 300: reject duplicate readings (delta <= 0), sensor resets, and
  unexpectedly large deltas (> 300). Such events reset the baseline without granting steps.
- Challenge must start from a sensor that is `AVAILABLE` with a valid reading.
- On completion, subsequent sensor events are ignored and the alarm never restarts.
- Sensor baseline is persisted so lock/unlock, recreation, and process death do not reset or grant
  free steps.

## 5. Alarm architecture

- Scheduling uses Android `AlarmManager` via `AlarmManagerScheduler`: `setAlarmClock` (exact,
  >= LOLLIPOP), falls back to `setAndAllowWhileIdle` when exact-alarm access is unavailable.
- `AlarmTimeCalculator.nextTriggerMillis(hour, minute)` computes the next future trigger (rolls to
  next day if the time has passed).
- `AlarmScheduleManager` wraps scheduling/cancellation with a fixed tag `sisyphus_alarm`, and
  `restoreOrTrigger()` handles reboot recovery (RESCHEDULED vs DUE).
- On fire, a `BroadcastReceiver` starts the foreground `SisyphusService` which calls
  `engine.onAlarmFired()`, plays the sound, shows a notification, and presents full-screen.
- Reboot recovery: `BootReceiver` calls `engine.restoreAlarmOnBoot()`.
- The alarm continues through normal Activity lifecycle interruptions and process death.

### Multi-alarm scheduling (v1.1)

- Each enabled `Alarm` is scheduled independently under its **alarm ID as the scheduler tag**.
  `AlarmManagerScheduler` derives a distinct PendingIntent request code per tag and stores the
  pending fire time per tag. The broadcast carries `alarmId`; `AlarmReceiver` forwards it to the
  service, which routes to `engine.onAlarmFired(alarmId)`.
- The single active challenge is associated with an alarm ID. Only one occurrence is walked at a
  time; a second alarm firing while one is active is skipped (treated as missed).
- Legacy single-alarm API (`configureAlarm`, no-arg `onAlarmFired`, `cancelAlarm`) is preserved for
  backward compatibility and operates on the "primary" alarm (id `sisyphus_alarm`, Daily).

### Recurrence model (v1.1)

- `RepeatMode`: `ONCE`, `DAILY`, `WEEKDAYS`, `WEEKENDS`, `CUSTOM` (user-selected subset of the 7
  weekdays via `customDays`). `ONCE` carries a `onceDate`.
- `AlarmRecurrenceCalculator.nextOccurrenceMillis(alarm)` computes the next strictly-future
  occurrence from the injectable `Clock` for every repeat mode.
- **Missed occurrences are skipped, never fired late**: recurring alarms reschedule to the next
  valid occurrence; a past `ONCE` alarm is not rescheduled.
- Reboot (`restoreAlarmOnBoot`) reschedules every enabled alarm to its next valid occurrence and
  recovers an in-flight/armed challenge (RESCHEDULED vs DUE).

## 6. Persistence requirements

- `ChallengeRepository` persists the active challenge (all state, steps, baseline, fire time,
  alarm ID). IDLE clears all challenge keys.
- `SettingsRepository` persists app settings: required steps, alarm hour/minute, sound selection,
  notifications enabled (legacy/primary alarm flow).
- `AlarmRepository` persists the full list of independent alarms: an index of IDs plus per-alarm
  fields (time, repeat mode, custom days, enabled, steps, sound, once date). Survives process death
  and reboot.
- A v1.0 install's single alarm is migrated into the alarm list as the primary Daily alarm.
- Corrupted / out-of-range / missing persisted state is handled gracefully (returns null / defaults).
- Every engine mutation is persisted immediately.

## 7. Anti-dismissal behavior

- No snooze. No conventional stop button. The only way forward is to walk.
- Back-button is blocked during `RINGING` / `CHALLENGE_ACTIVE` (BackHandler) so the alarm cannot be
  dismissed.
- Alarm stops **immediately** when required steps reach zero.
- Full-screen alarm presentation.

## 8. Sound

- `SoundSelection`: `Bundled`, `SystemRingtone(uri)`, `CustomFile(uri)`.
- `SoundResolver` falls back to Bundled when a custom/system sound is missing, deleted, or
  unplayable — **never silent**.
- Sound starts on fire and stops immediately at completion.

## 9. Permission requirements

Readiness is checked via `ReadinessChecker` producing a `ReadinessReport` with explicit
explanatory messages (never silent failure). Requirements:
- `NOTIFICATIONS`
- `EXACT_ALARMS`
- `FULL_SCREEN_INTENT`
- `STEP_SENSOR`
- `ALARM_SOUND`

Android permissions/manifest: `POST_NOTIFICATIONS`, `ACTIVITY_RECOGNITION`, `SCHEDULE_EXACT_ALARM`,
`FOREGROUND_SERVICE` (alarm type), `RECEIVE_BOOT_COMPLETED`, step-sensor feature flag,
`takePersistableUriPermission` for custom sound URIs.

## 10. UI/UX principles

- Screens: **Alarm list** ("THE STONES" — each alarm shows time, repeat label, steps, sound, an
  enabled switch, and edit/delete), **Alarm editor** ("NEW STONE"/"THE STONE" — time via native
  picker, repeat mode, custom days, once date, step presets, sound), **Alarm**
  ("THE STONE AWAITS."), **Challenge** ("WALK." — remaining step count is the primary focus),
  **Completion** ("THE STONE HAS FALLEN.").
- Jetpack Compose. Minimalist. The remaining step count is the primary focus during a challenge.
- UI renders `ChallengeViewState` + alarm-list snapshots; never holds source-of-truth state.
- **Native Android time picker** is used for alarm time selection (no custom picker). The UI test
  helper is **mode-aware**: it must handle both 24-hour and 12-hour (AM/PM) formats because devices
  like the Samsung S25 (24-hour) expose no `android:id/am_pm_spinner`. The picker has no text fields
  for hour/minute. A native `DatePickerDialog` is used for ONCE dates.
- **Phase 11 UI implementation** (Compose): `MainActivity` + `SisyphusScreens` render the Compose
  UI via a `ChallengeViewModel` that collects engine `ChallengeViewState` and alarm-list snapshots.
  The Activity refreshes and resumes/ stops step tracking on resume/pause but is never the source of
  truth.

## 11. Testing philosophy and requirements

**Testing-first.** Everything that can run without a physical device must run automatically and
deterministically. The phone validates Android/device-specific behavior, not basic application bugs.

- `:core` uses pure JVM tests (JUnit 5) with fake clocks, fake sensors, in-memory stores,
  recording platform, and an `EngineHarness` test util.
- `:app` uses Robolectric (JUnit 4) for Android adapters.
- `:app` instrumentation (`androidTest`): `DeviceGateInstrumentationTest` and
  `UiFlowInstrumentationTest` for physical-device validation (skipped via `assumeTrue` when no
  step sensor, e.g. emulator).
- Phase mapping lives in `TESTING.md` — every requirement is wired to a test file.
- Device-bug loop: reproduce → identify underlying logic → add an automated regression test →
  fix → re-run relevant suite → only then repeat the device test. Never patch device symptoms
  without a regression test when behavior is testable automatically.

Run commands (see `TESTING.md`):

```bash
./gradlew build                        # compile + ktlint + all unit tests
./gradlew test                         # unit tests only
./gradlew ktlintCheck                  # static analysis
./gradlew ktlintFormat                 # auto-fix formatting
./gradlew :core:test                   # core JVM tests (JUnit 5)
./gradlew :app:testDebugUnitTest       # app Robolectric tests (JUnit 4)
./gradlew :app:assembleDebug           # debug APK
```

Current v1.1 validated status: **213 unit tests (190 core JVM + 23 app Robolectric), 0 failures,
ktlint clean, build green.** v1.0 physical-device gate passed on **Samsung Galaxy S25 (SM-S721B),
API 36**; v1.1 was validated on the Linux CI/emulator (unit + Robolectric + build) and awaits the
physical-device gate. Application ID `org.sisyphus.android`, versionCode 1, versionName 1.0.0.

## 12. OpenCode operating rules

- Read this `MASTER_PROMPT.md` completely before working.
- Follow the per-phase workflow (section 14). Never begin implementation before the feature/phase
  prompt exists and the audit is complete.
- Treat existing Master Prompt requirements as binding unless a feature specification explicitly
  changes them.
- Do not rely on conversation history as the only source of requirements.
- Only commit when explicitly asked.

## 13. Engineering constraints

- `:core` must remain **pure Kotlin/JVM with no Android dependency**. Android specifics stay behind
  platform interfaces.
- Everything testable without a device is tested automatically.
- Alarm reliability: exact scheduling, receivers, foreground service, full-screen presentation,
  notification channels, audio, custom URI, reboot recovery, lifecycle interruptions.
- Actual behavior can vary by Android version and device manufacturer; guard accordingly.

## 14. Development workflow for future OpenCode sessions

Every major Sisyphus development phase follows this sequence:

```text
MASTER_PROMPT.md
        ↓
Feature/Phase Prompt  (docs/prompts/<PHASE>.md)
        ↓
Implementation
        ↓
Tests
        ↓
Master Prompt update  (reflect the NEW system)
        ↓
Phase completion
```

Phase prompts are preserved under `docs/prompts/`. The saved prompt is the actual specification
used for the implementation, not merely a summary afterward.

## 15. Current project phase and status

- **Sisyphus v1.0.0** — MVP complete and physically validated (2026-08-11).
- Feature: single alarm, configurable steps, step presets, custom sounds, full-screen alarm,
  persistent challenge progress, back-button protection, immediate termination, reboot recovery.
- **Phase 10** (physical-device gate) and **Phase 11** (final UI implementation) both PASSED on the
  Samsung Galaxy S25 / API 36.
- **Sisyphus v1.1 — Multiple Alarms** — implemented (2026-08). Multiple independent alarms with
  IDs, enabled/disabled state, Once/Daily/Weekdays/Weekends/Custom schedules, next-occurrence
  calculation, missed-occurrence skipping, reboot recovery, edit/reschedule, deletion, independent
  step requirements and sounds, fresh challenge per occurrence, and no inheritance of challenge
  progress between occurrences.
- v1.1 validated by 213 unit/Robolectric tests (0 failures), ktlint clean, build green, and
  emulator instrumentation. Physical-device gate for v1.1 is pending (Samsung Galaxy S25 / API 36).

## 16. v1.1 multi-alarm requirements (authoritative)

- **Multiple independent alarms** coexist; the alarm list is persisted and survives process death
  and reboot.
- **Alarm IDs**: every alarm has a stable unique ID used for scheduling, editing, deletion,
  persistence, and broadcast/notification routing.
- **Enabled/disabled**: a disabled alarm is not scheduled and does not fire; re-enabling
  reschedules its next occurrence.
- **Repeat modes**: `ONCE`, `DAILY`, `WEEKDAYS`, `WEEKENDS`, `CUSTOM` (subset of the seven
  weekdays). Repeat mode is persisted per alarm.
- **Next-occurrence calculation**: computed from the injectable `Clock`, the time-of-day, and the
  repeat mode's day constraints.
- **Missed-occurrence behavior**: recurring alarms reschedule to the next future occurrence (never
  fired late); a past `ONCE` alarm is not rescheduled; no double-firing.
- **Reboot recovery**: all enabled alarms are restored and rescheduled for their next valid
  occurrence; in-flight/armed challenges recover consistently.
- **Editing/rescheduling**: editing recomputes and reschedules immediately; it does not corrupt an
  in-progress challenge.
- **Deletion**: cancels the pending schedule, removes persisted configuration, and clears any
  in-progress challenge for that alarm; other alarms are unaffected.
- **Independent steps**: each alarm owns its step requirement (`1..10000`, v1.0 preset model).
- **Independent sounds**: each alarm owns its sound selection; the never-silent resolver fallback
  applies per alarm.
- **Fresh challenge per occurrence**: every firing creates a fresh challenge with a fresh step
  baseline; progress never carries between occurrences or alarms.

## 17. Prompt history

Major implementation prompts are preserved in `docs/prompts/`. The v1.1 feature specification is
recorded in `docs/prompts/SISYPHUS_V1.1_MULTIPLE_ALARMS.md`.

---

_This Master Prompt is the authoritative description of Sisyphus. Keep it updated after each
phase so a future session can reconstruct the project's engineering/product context without
prior chat history._
