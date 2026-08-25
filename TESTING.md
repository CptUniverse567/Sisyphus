# Sisyphus — Test Strategy and Pre-Device Gate

> **Goal:** the phone validates Android/device-specific behavior, not basic application bugs.

Everything below that can run without a physical device runs automatically in this repository.

## Release candidate — Sisyphus v1.1.0

**Sisyphus v1.1.0** — multiple independent alarms, release candidate (2026-08).

- Unit tests: **213/213 PASS** (190 core JVM + 23 app Robolectric)
- ktlint: **PASS**
- Gradle build: **PASS**
- Instrumentation (emulator): **UiFlow 6/6, DeviceGate 3/3** (sensor-dependent tests skipped on
  emulator via `assumeTrue`; the two setup-flow tests exercise the new alarm editor)
- Physical-device gate: **pending for v1.1** (prior v1.0 gate: Samsung Galaxy S25 / API 36)

> v1.0 release candidate — 173/173 unit tests (150 core + 23 app), 9/9 instrumentation on device.

## How to run everything

```powershell
.\gradlew.bat build                        # compile + ktlint + all unit tests
.\gradlew.bat test                         # unit tests only
.\gradlew.bat ktlintCheck                  # static analysis
.\gradlew.bat ktlintFormat                 # auto-fix formatting
.\gradlew.bat :core:test                   # core JVM tests (JUnit 5)
.\gradlew.bat :app:testDebugUnitTest       # app Robolectric tests (JUnit 4)
.\gradlew.bat :app:assembleDebug           # debug APK
.\gradlew.bat :app:assembleDebugAndroidTest  # instrumentation APK (compile check)
```

Current status: **213 unit tests (190 core JVM + 23 app Robolectric), 0 failures, ktlint clean, build green.**

> **Phase 10 — physical-device gate: PASSED (accepted 2026-08-11).**
> Device: **Samsung Galaxy S25 (SM-S721B), API 36**, real step sensor.
>
> | Suite | Result |
> |---|---|
> | DeviceGate instrumentation | **3/3 passed, 0 ignored** |
> | UiFlow instrumentation | **6/6 passed, 0 ignored** |
> | Manual walk-through | **12/12 passed** |
> | Unit tests | **196 passed** |
> | ktlint / build | **clean** |
>
> Two real-device issues were discovered during validation and fixed; the final physical-device
> test suite passed afterward:
> - **Samsung native time-picker test compatibility.** The phone is 24-hour format, so its picker
>   exposes no `android:id/am_pm_spinner`; the original test helper assumed a 12-hour AM/PM spinner
>   and crashed with a null-object click. The helper is now mode-aware: it types the 24-hour hour
>   and only selects AM/PM when the spinner exists. (Also verified the native picker has no
>   text fields for hour/minute, per the no-text-fields test.)
> - **Back-button dismissal during the active challenge.** Pressing back while the alarm was
>   ringing / the challenge was active finished the activity (dismissing the full-screen alarm).
>   Added a `BackHandler` during `RINGING`/`CHALLENGE_ACTIVE` so back no longer dismisses the
>   alarm; the `backButton_doesNotDismissTheChallenge` test now passes on device.
>
> Earlier device-driven fix (also in the final green state): added `ACTIVITY_RECOGNITION` to the
> manifest and permission flow. Without it Android hides the step-counter sensor (Android 10+), so
> the sensor was silently unavailable — the emulator never caught this because it has no sensor
> and the DeviceGate `assumeTrue` skipped silently.

> Note: AGP resolves Robolectric to 4.16.1 regardless of the declared `4.14`, and it requires a
> `MediaInfo` provider before `setDataSource` succeeds. Tests use the 4.16.1 API
> (`ShadowMediaPlayer.setCreateListener`, `isReallyPlaying`, `ShadowSensor`, ...). uiautomator 2.3.0
> dropped `UiObject2.exists()` — instrumentation tests use `device.wait(Until.hasObject(...))`.

## Architecture (what the tests are wired to)

The core is a pure Kotlin/JVM library with **no Android dependency**. Android specifics are
behind interfaces, so the whole test suite is deterministic and repeatable. The `:app` module
provides the real Android adapters and the Compose UI.

| Layer | Core files | Android adapter (`:app`) |
|---|---|---|
| State machine | `challenge/ChallengeStateMachine.kt` | — |
| Step accounting | `steps/StepAccountant.kt`, `steps/StepGuard.kt` | `SensorManagerStepSensor` (`platform/`) |
| Alarm timing | `alarm/AlarmTimeCalculator.kt`, `alarm/AlarmScheduleManager.kt` | `AlarmManagerScheduler` (`platform/`) |
| Persistence | `persistence/ChallengeRepository.kt`, `persistence/SettingsRepository.kt` | `SharedPrefsKeyValueStore` (`platform/`) |
| Sound | `sound/SoundResolver.kt` | `MediaPlayerAlarmPlayer`, `AndroidSoundAvailability` (`platform/`) |
| Permissions/readiness | `permissions/ReadinessChecker.kt` | `SensorSupport`, `NotificationSupport`, `ExactAlarmSupport` (`platform/`) |
| Orchestrator | `engine/SisyphusEngine.kt` | `AppGraph` wiring (`SisyphusApp.kt`) |
| UI policy | `ui/ChallengeUi.kt` | `MainActivity`, `SisyphusScreens.kt` (Compose) |
| Runtime | `Clock`, `StepSensor`, `AlarmScheduler`, `AlarmPlayer`, `NotificationCenter` | `SystemClock`, foreground `SisyphusService` |

The Activity is **never** the source of truth: `SisyphusEngine` owns the challenge, persists every
mutation, and the UI renders `ChallengeViewState` snapshots.

## Phase mapping — where each requirement is tested

### Phase 1 — Unit tests
| Requirement | Test file |
|---|---|
| 500 required → 500 remaining initially | `ChallengeCalculationTest` |
| 500 → 499 after one valid step | `ChallengeCalculationTest` |
| 500 → 0 when reached | `ChallengeCalculationTest` |
| Progress never negative / excess steps | `ChallengeCalculationTest` |
| Presets, custom requirements, min/max, invalid values | `ChallengeCalculationTest` |
| Completion idempotent | `ChallengeCalculationTest` |
| Cumulative progression, baseline, duplicates | `StepAccountingTest` |
| Sensor reset, reboot, lower-than-baseline, unavailable | `StepAccountingTest` |
| Large/unexpected jumps (never grants thousands) | `StepAccountingTest` |
| Persisted progress + new reading | `StepAccountingTest` |
| Resume after reopening app: baseline persists, no free steps, duplicates ignored | `SensorResumeRegressionTest` |
| Completion is final: sensor events after completion are ignored, alarm never restarts | `SensorResumeRegressionTest` |
| Every valid state transition | `StateMachineTest` |
| Every invalid transition (complete inactive, edit armed, snooze/stop active, restart completed, duplicate active) | `StateMachineTest` |
| Lifecycle restore preserves active challenge | `StateMachineTest`, `LifecycleTest` |

### Phase 2 — Persistence
| Scenario | Test |
|---|---|
| Process death, activity recreation, config change, backgrounding/reopen, service recreation | `PersistenceTest` |
| 183/500 → recovery shows 317 remaining (not 500, not 0) | `PersistenceTest` |
| Reboot simulation | `PersistenceTest`, `AlarmScheduleTest` |
| Corrupted / out-of-range persisted state | `PersistenceTest` |

### Phase 3 — Alarm scheduling (fake clocks, no waiting)
| Scenario | Test |
|---|---|
| Creation, cancellation, replacement, duplicate prevention | `AlarmScheduleTest` |
| Trigger-time calculation, past-time, midnight, TZ change | `AlarmScheduleTest` |
| Reboot recovery (RESCHEDULED vs DUE) | `AlarmScheduleTest` |
| Alarm fires while Activity not open | `AlarmScheduleTest` |
| `AlarmManagerScheduler` maps intents / exact-alarm flags | `AlarmManagerSchedulerTest` (Robolectric) |

### Phase 4 — Service/lifecycle
| Scenario | Test |
|---|---|
| Activity destroyed/recreated/backgrounded/reopened | `LifecycleTest` |
| Notification interaction | `LifecycleTest` |
| Service restart, process death during alarm / mid-challenge | `LifecycleTest`, `PersistenceTest` |
| Persisted state authoritative; Activity is a view | `LifecycleTest` |

### Phase 5 — UI flows (presenter level + Compose)
| Flow | Test |
|---|---|
| Setup: open → alarm → requirement → sound → save | `UiFlowTest.setup flow...` |
| Alarm: activate → screen → remaining → progress → zero → stop → completed | `UiFlowTest.alarm flow...` |
| Escape: back doesn't dismiss, no stop button, no snooze, reopen restores, recreation doesn't reset | `UiFlowTest` |
| Sound picker/state; SharedPreferences store behavior | `AndroidSoundAvailabilityTest`, `SharedPrefsKeyValueStoreTest` (Robolectric) |
| Compose instrumentation (device) | `UiFlowInstrumentationTest` in `:app` androidTest |

### Phase 6 — Sound
| Scenario | Test |
|---|---|
| Bundled, system ringtone, custom file, persisted URI | `SoundTest` |
| Missing/deleted/unplayable custom file → bundled fallback (never silent) | `SoundTest` |
| Sound starts on fire, stops immediately at completion | `SoundTest` |
| Reboot after custom selection | `SoundTest` |

### Phase 7 — Permissions/readiness
| Scenario | Test |
|---|---|
| Grant, deny, later-grant, revoke, notifications off, no sensor, exact-alarm access | `ReadinessTest` |
| Explanatory missing messages instead of silent failure | `ReadinessTest`, `PreDeviceFailureInjectionTest` |

### Phase 9 — Pre-device failure injection
Every listed scenario is exercised in `PreDeviceFailureInjectionTest`: process death during alarm /
after 250 / after 500, activity recreation during alarm, service restart, sensor unavailable / reset /
duplicate events, corrupted / missing persisted state, missing custom sound, permission denial /
revocation, reboot recovery, alarm cancellation while inactive, attempted duplicate creation,
completion under simultaneous lifecycle events. Each asserts recovery to a valid state.

### Phase 12 — v1.1 Multiple alarms
| Requirement | Test |
|---|---|
| Next-occurrence calculation for every repeat mode | `AlarmRecurrenceCalculatorTest` |
| Multiple alarms coexist independently | `MultiAlarmEngineTest` |
| Enabled/disabled scheduling per alarm | `MultiAlarmEngineTest` |
| Per-alarm scheduling tags / unique request codes | `MultiAlarmEngineTest`, `AlarmManagerSchedulerTest` |
| Editing reschedules; deletion cleans only that alarm | `MultiAlarmEngineTest` |
| Independent step requirements and sounds | `MultiAlarmEngineTest` |
| Fresh challenge per occurrence, no cross-occurrence progress | `MultiAlarmEngineTest` |
| Challenge routing by alarm ID; skip on second firing | `MultiAlarmEngineTest` |
| Missed occurrences skipped, never fired late | `MultiAlarmEngineTest` |
| Reboot recovery reschedules all enabled alarms | `MultiAlarmEngineTest` |
| Alarm list + challenge survive process death | `MultiAlarmEngineTest`, `AlarmRepositoryTest` |
| AlarmRepository persistence round-trip | `AlarmRepositoryTest` |
| Legacy single-alarm API preserved | `MultiAlarmEngineTest`, `EngineAlarmSchedulingTest`, `UiFlowTest` |

## Android module status

The `:app` module is fully implemented and compiles to a debug APK:

- `AndroidManifest.xml` with exported components, intent filters, `FOREGROUND_SERVICE` (alarm type),
  `SCHEDULE_EXACT_ALARM`, `POST_NOTIFICATIONS`, step-sensor feature flag, `RECEIVE_BOOT_COMPLETED`.
- Notification channels + foreground-service notification (`AppNotificationCenter`).
- `AlarmManager` exact-alarm + `setAlarmClock` + boot restore via `SisyphusEngine.restoreAlarmOnBoot()`.
- `takePersistableUriPermission` for the custom sound URI.
- MediaPlayer lifecycle and missing-sound fallback.
- R8 rules + release build config (see `proguard-rules.pro`).

## Final status

```text
BUILD: PASS                    assembleDebug + assembleDebugAndroidTest
UNIT TESTS: PASS               213/213 (190 core + 23 app)
LINT/STATIC ANALYSIS: PASS     ktlintCheck
CORE STATE MACHINE: VERIFIED   StateMachineTest + ChallengeCalculationTest
PERSISTENCE: VERIFIED          PersistenceTest + AlarmRepositoryTest
ALARM LOGIC: VERIFIED          AlarmScheduleTest + AlarmRecurrenceCalculatorTest
MULTI-ALARM: VERIFIED          MultiAlarmEngineTest
STEP ACCOUNTING: VERIFIED      StepAccountingTest + PreDeviceFailureInjectionTest
PHYSICAL DEVICE GATE: PENDING  v1.1 — prior v1.0 gate passed on Samsung Galaxy S25, API 36
```

## Phase 10 — Physical device gate (PASSED 2026-08-11)

Executed on a **Samsung Galaxy S25 (SM-S721B), API 36**, real step sensor.
All automated suites and the manual walk-through passed; details in the status block at the top
of this file. The commands below are kept as the reproducible record of how the gate was run.

### Build artifacts (already produced, signed)

```
app\build\outputs\apk\debug\app-debug.apk            # the app (debug-signed)
app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk   # the tests
```

> The release APK is signed and produced at `app\build\outputs\apk\release\app-release.apk` for
> manual sideloading. Use the debug APK for the device test gate.
> Required on the phone: a step sensor (`android.hardware.sensor.stepcounter` / `stepdetector`),
> Android 8.0+ (minSdk 26). Target SDK 35 requires the app to run in the foreground for the alarm;
> keep Sisyphus installed with notifications + exact-alarm granted (commands below).

### 1. Connect the phone and install

```powershell
adb devices                 # confirm the phone is listed (not emulator-5554)
adb install -r app\build\outputs\apk\debug\app-debug.apk
adb install -r app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk
```

### 2. Grant runtime permissions before testing

```powershell
adb shell pm grant org.sisyphus.android android.permission.POST_NOTIFICATIONS
adb shell pm grant org.sisyphus.android android.permission.ACTIVITY_RECOGNITION
adb shell pm grant org.sisyphus.android.test android.permission.POST_NOTIFICATIONS
adb shell pm grant org.sisyphus.android.test android.permission.ACTIVITY_RECOGNITION
# Give the app full-screen intent access in Settings -> Special app access -> Alarms & reminders
# (or: Settings -> Apps -> Sisyphus -> Notifications -> full-screen intent allowed)
```

### 3. Run the instrumentation suites

```powershell
adb shell am instrument -w -e class "org.sisyphus.android.DeviceGateInstrumentationTest" `
  org.sisyphus.android.test/androidx.test.runner.AndroidJUnitRunner

adb shell am instrument -w -e class "org.sisyphus.android.UiFlowInstrumentationTest" `
  org.sisyphus.android.test/androidx.test.runner.AndroidJUnitRunner
```

Expected on a phone with a step sensor: `DeviceGate` runs the real `realSensor_updatesProgress`,
`lockAndUnlock_preservesProgress`, and `alarmSurvivesProcessRestart` (on the emulator these are
silently skipped via `assumeTrue` because there is no step sensor). `UiFlow` runs all 6 flows.

### 4. Manual walk-through

Install the debug APK, run the suites above, then walk this list:

1. Lock the phone. 2. Close Sisyphus. 3. Leave idle. 4. Wait for the alarm.
5. Verify full-screen alarm presentation. 6. Verify alarm sound.
7. Pick up the phone. 8. Walk. 9. Verify real sensor progress.
10. Lock/unlock during the challenge. 11. Leave/reopen the app.
12. Complete the required steps. 13. Verify immediate alarm termination.
14. Reboot the phone and verify alarm recovery. 15. Test the custom alarm sound.
16. Test notification and permission behavior.

## Testing principle (device-bug loop)

1. Reproduce. 2. Identify underlying logic. 3. Add an automated regression test if possible.
4. Fix the implementation. 5. Re-run the relevant suite. 6. Only then repeat the device test.
Do not patch device symptoms without a regression test when the behavior is testable automatically.
