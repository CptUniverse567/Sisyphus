# Sisyphus v1.1 — Multiple Alarms — Feature Specification

> **NO ESCAPE. WALK.**

This is the feature/phase prompt for the **v1.1 Multiple Alarms** phase. It is an ADDITION to
`MASTER_PROMPT.md`, which remains the authoritative description of the pre-existing v1.0 system.
Preserve all existing Master Prompt requirements unless this specification explicitly changes a
requirement. Read `MASTER_PROMPT.md` and `TESTING.md` before implementing.

This document is the actual specification used for this implementation, not a summary afterward.

---

## 1. Goal

Replace the single-alarm model with **multiple independent alarms**. Each alarm has its own
schedule, enabled state, step requirement, and sound. The app can hold several alarms that fire
independently.

## 2. Requirement — multiple independent alarms

- The app supports more than one alarm simultaneously (e.g. a "workday morning" alarm and a
  "weekend" alarm).
- Each alarm is fully independent in configuration and behavior.
- Alarm list is persisted and survives process death and reboot.

## 3. Requirement — alarm identity

- Every alarm has a stable, unique **alarm ID** (used for scheduling, editing, deletion,
  persistence, and routing the broadcast/notification to the correct alarm).

## 4. Requirement — enabled / disabled state

- Each alarm has an **enabled/disabled** toggle.
- A disabled alarm is not scheduled and does not fire.
- Re-enabling a disabled alarm schedules it for its next occurrence.
- Enabled state is persisted per alarm.

## 5. Requirement — repeat modes (schedule types)

Each alarm has exactly one of the following repeat modes:

- **Once** — fires a single time on the configured date/time; after firing it does not repeat and
  becomes a one-shot occurrence.
- **Daily** — fires every day at the configured time.
- **Weekdays** — fires Mon–Fri at the configured time.
- **Weekends** — fires Sat–Sun at the configured time.
- **Custom weekday schedule** — fires on a user-selected subset of the seven weekdays (e.g. Mon +
  Wed + Fri) at the configured time.

Repeat mode is persisted per alarm.

## 6. Requirement — next-occurrence calculation

- For each enabled alarm, the **next occurrence** is calculated from the current time, the
  configured time-of-day, and the repeat mode.
- Next occurrence respects the repeat mode's day constraints (e.g. a Weekdays alarm set on Sunday
  rolls to Monday).
- A **Once** alarm uses its configured date/time.
- Calculation uses the injectable `Clock` (same as v1.0) for deterministic testing.

## 7. Requirement — missed-occurrence behavior

- If an alarm's scheduled occurrence passes while the app is off/dead (e.g. reboot, doze, process
  death), the occurrence is treated as **missed**.
- On recovery (reboot / app reopen / service resume), each enabled alarm that has a missed
  occurrence is handled per its repeat mode:
  - **Daily / Weekdays / Weekends / Custom**: reschedule to the next future occurrence (do not
    fire late for a missed window).
  - **Once**: a past one-shot is not rescheduled (it simply no longer fires).
- Missed-occurrence handling must not double-fire an alarm that already fired.

## 8. Requirement — reboot recovery

- On `BOOT_COMPLETED`, all enabled alarms are restored and rescheduled for their next occurrence.
- Any alarm that is currently firing / mid-challenge at reboot continues correctly (consistent with
  v1.0 challenge recovery).

## 9. Requirement — alarm editing / rescheduling

- An existing alarm can be edited (time, repeat mode, step requirement, sound, enabled state).
- Editing reschedules the alarm immediately based on the new configuration (recomputes next
  occurrence).
- Editing a currently-armed alarm must not corrupt an in-progress challenge for that alarm.

## 10. Requirement — alarm deletion

- An alarm can be deleted.
- Deletion cancels its pending schedule, removes its persisted configuration, and removes any
  in-progress challenge state for that alarm.
- Deleting one alarm does not affect other alarms.

## 11. Requirement — independent step requirements

- Each alarm carries its **own step requirement** (value in `1..10000`, with the v1.0 preset
  model). Changing one alarm's requirement does not affect others.

## 12. Requirement — independent sounds

- Each alarm carries its **own sound selection** (Bundled / SystemRingtone / CustomFile, per v1.0
  `SoundSelection`). The sound resolver fallback (never silent) applies per alarm.

## 13. Requirement — fresh challenge per occurrence

- Every firing of an alarm produces a **fresh challenge** for that occurrence.
- No inheritance of challenge progress between occurrences: completing one firing does not carry
  steps, baseline, or completion into the next firing of the same (or any) alarm.

## 14. Requirement — challenge routing by alarm ID

- An active challenge is associated with a specific **alarm ID**.
- Sensor events / progress / completion apply only to the active alarm's challenge.
- When one alarm's challenge completes, other alarms remain unaffected.

---

## 15. Architecture guidance (do not violate)

- Keep `:core` pure Kotlin/JVM with **no Android dependency**. All Android specifics (AlarmManager,
  PendingIntent request codes, broadcast routing, notification per alarm) stay behind the platform
  interfaces in `core/.../platform/Platform.kt`.
- Extend the platform interfaces (e.g. `AlarmScheduler.schedule` must accept a per-alarm tag/ID;
  `AlarmManagerScheduler` uses a unique request code/PendingIntent per alarm ID; broadcast carries
  the alarm ID; `AlarmReceiver` routes to the correct alarm's challenge).
- The state machine / engine must own per-alarm state; the Activity remains a view of
  `ChallengeViewState` snapshots and is never the source of truth.
- Persistence must store the full alarm list (IDs, enabled, repeat mode, time, step requirement,
  sound) plus the currently-active challenge keyed by alarm ID, surviving process death and reboot.
- Deterministic testing: next-occurrence calculation, missed-occurrence, and rescheduling must be
  testable with the injectable `Clock`.

## 16. Testing requirements

- Add/adapt tests per `TESTING.md` phase mapping. Cover at minimum:
  - Multiple alarms coexist and are independent.
  - Per-alarm enabled/disabled scheduling.
  - Each repeat mode's next-occurrence calculation (Once / Daily / Weekdays / Weekends / Custom).
  - Missed-occurrence behavior on recovery for each repeat mode.
  - Reboot recovery for multiple alarms.
  - Editing reschedules; deletion cancels/cleans only that alarm.
  - Independent step requirements and independent sounds per alarm.
  - Fresh challenge per occurrence; no cross-occurrence or cross-alarm progress inheritance.
  - Challenge routing by alarm ID; completion affects only the active alarm.
- Keep all existing v1.0 tests green (regression) unless a test is explicitly superseded by a
  stated requirement change.
- Run: `./gradlew build`, `./gradlew ktlintCheck`, `./gradlew :core:test`,
  `./gradlew :app:testDebugUnitTest`.

## 17. Definition of done

- Multiple independent alarms implemented with IDs, enabled/disabled, all repeat modes,
  next-occurrence calculation, missed-occurrence behavior, reboot recovery, edit/reschedule,
  deletion, independent steps, independent sounds, fresh challenge per occurrence.
- All new tests pass; all existing v1.0 tests still pass; ktlint clean; build green.
- `MASTER_PROMPT.md` updated to describe the new multi-alarm architecture and recurrence behavior
  (multiple independent alarms, alarm IDs, enabled/disabled, Once/Daily/Weekdays/Weekends/Custom
  schedules, next-occurrence calculation, missed-occurrence behavior, reboot recovery, alarm
  editing/rescheduling, alarm deletion, independent step requirements, independent sounds, fresh
  challenge per occurrence, no inheritance of challenge progress between occurrences).
- README / TESTING.md updated to reflect the new feature set where applicable.
