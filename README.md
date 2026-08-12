# Sisyphus

> **NO ESCAPE. WALK.**

Sisyphus is a minimalist Android alarm app that requires you to physically walk a configurable number of steps before the alarm can be silenced.

It is deliberately simple: when the alarm rings, there is no snooze and no conventional stop button. The only way forward is to walk.

## Features

* Single alarm configuration
* Native Android time picker
* Configurable step requirement
* Step presets:

  * **Pebble** — 100 steps
  * **Stone** — 300 steps
  * **Boulder** — 500 steps
  * **Mountain** — 1,000 steps
  * **Sisyphus** — 2,000 steps
* Custom step requirements
* Custom alarm sounds
* Bundled default alarm sound
* System alarm/ringtone selection
* Real Android step-sensor tracking
* Persistent challenge progress
* Full-screen alarm presentation
* Alarm continues through normal Activity lifecycle interruptions
* Back-button protection during an active challenge
* Alarm stops immediately when the required steps reach zero
* Offline-first
* No account
* No backend
* No cloud synchronization

## Philosophy

Sisyphus is based on a simple principle:

> **Make waking up require physical effort.**

There are no points, streaks, achievements, social systems, or unnecessary gamification.

You set the burden.

The alarm rings.

You walk.

The stone eventually falls.

## Screens

### Setup

Configure the alarm time, step requirement, and alarm sound.

### Armed

The alarm is scheduled and waiting.

### Alarm

> **THE STONE AWAITS.**

The alarm demands movement.

### Challenge

> **WALK.**

The remaining step count is the primary focus.

### Completion

> **THE STONE HAS FALLEN.**

Once the required steps reach zero, the alarm stops immediately.

## Technical Stack

* **Kotlin**
* **Jetpack Compose**
* **Android SDK**
* **Gradle**
* **AlarmManager**
* **Foreground Service**
* **Full-screen intents**
* **Android SensorManager**
* `TYPE_STEP_COUNTER` / `TYPE_STEP_DETECTOR`
* Persistent local storage
* Offline-first architecture

The project is organized into:

```text
:core
:app
```

The core module contains platform-independent challenge logic and state management, while the Android application module handles sensors, alarms, services, persistence, permissions, and Compose UI.

## Challenge State

The application uses an explicit state machine:

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

Challenge progress is persisted so Activity recreation, lock/unlock, and process lifecycle events do not reset the user's progress.

## Step Tracking

Sisyphus uses Android's hardware step sensors rather than attempting to estimate steps from arbitrary UI interaction.

For cumulative step sensors, challenge progress is calculated relative to a challenge baseline rather than treating the device's lifetime step count as challenge progress.

The implementation also guards against duplicate readings, sensor resets, and unexpectedly large sensor deltas.

## Alarm Reliability

The alarm uses Android's alarm infrastructure and foreground execution mechanisms to support reliable triggering while the application UI is not active.

The implementation includes handling for:

* Exact alarm scheduling
* Alarm receivers
* Foreground service execution
* Full-screen alarm presentation
* Alarm notification channels
* Alarm audio
* Custom audio URIs
* Reboot recovery
* Normal Android lifecycle interruptions

Actual behavior can vary by Android version and device manufacturer.

## Testing

Sisyphus was developed with a testing-first approach.

The completed validation included:

* **196 unit/Robolectric tests**
* **3/3 DeviceGate instrumentation tests**
* **6/6 UI instrumentation tests**
* **0 skipped tests during final physical-device instrumentation**
* **0 failures**
* `ktlint` clean
* Successful Gradle build
* Manual physical-device walkthrough
* Real step-sensor validation
* Alarm audio validation
* Full-screen alarm validation
* Lock/unlock persistence validation
* Process-death recovery validation
* Doze alarm validation
* Immediate alarm termination validation

Final physical-device validation was performed on a **Samsung Galaxy S25 running Android API 36**.

The project also encountered and resolved device-specific issues during testing, including Samsung's native time-picker UI differences and active-challenge Back-button dismissal.

## Requirements

Sisyphus requires an Android device with compatible alarm and step-sensor capabilities.

Some Android permissions and special access may be required depending on the device and Android version, including notification, activity recognition, exact alarm, and full-screen intent access.

OEM battery-management policies may affect alarm behavior on some devices.

## Building

Clone the repository and open it in Android Studio.

Build the debug APK with:

```bash
./gradlew assembleDebug
```

On Windows:

```cmd
gradlew.bat assembleDebug
```

The debug APK is generated at:

```text
app/build/outputs/apk/debug/app-debug.apk
```

For a release build:

```bash
./gradlew assembleRelease
```

The release APK is generated at:

```text
app/build/outputs/apk/release/app-release.apk
```

## Project Status

**Sisyphus v1.0.0**

The MVP core functionality and physical-device validation are complete.

The current release focuses on doing one thing well:

> **When the alarm rings, get up and walk.**

## License

License information has not yet been defined.
