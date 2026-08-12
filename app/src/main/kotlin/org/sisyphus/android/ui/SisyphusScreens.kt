package org.sisyphus.android.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.sisyphus.android.platform.ExactAlarmSupport
import org.sisyphus.android.platform.FullScreenIntentSupport
import org.sisyphus.android.platform.NotificationSupport
import org.sisyphus.android.platform.PermissionFlow
import org.sisyphus.android.platform.SensorSupport
import org.sisyphus.core.challenge.ChallengeState
import org.sisyphus.core.challenge.StepPreset
import org.sisyphus.core.engine.ChallengeViewState
import org.sisyphus.core.permissions.ReadinessChecker
import org.sisyphus.core.permissions.ReadinessRequirement
import org.sisyphus.core.settings.SoundSelection

@Composable
fun sisyphusUi(
    ui: ChallengeViewModel,
    state: ChallengeViewState,
) {
    BackHandler(enabled = state.state == ChallengeState.RINGING || state.state == ChallengeState.CHALLENGE_ACTIVE) {
    }
    Scaffold(modifier = Modifier.testTag("root")) { innerPadding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Crossfade(targetState = state.state, label = "stateTransition") { currentState ->
                when (currentState) {
                    ChallengeState.IDLE -> setupScreen(ui)
                    ChallengeState.ARMED -> armedScreen(ui, state)
                    ChallengeState.RINGING -> alarmScreen(ui)
                    ChallengeState.CHALLENGE_ACTIVE -> challengeScreen(state)
                    ChallengeState.COMPLETED -> completedScreen(ui, state)
                }
            }
        }
    }
}

@Composable
private fun sectionHeader(title: String) {
    Text(
        title,
        style =
            MaterialTheme.typography.titleSmall.copy(
                letterSpacing = 4.sp,
                fontWeight = FontWeight.Bold,
            ),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun setupScreen(ui: ChallengeViewModel) {
    val context = LocalContext.current
    var steps by rememberSaveable { mutableStateOf(StepPreset.LIGHT.steps.toString()) }
    var hour by rememberSaveable { mutableStateOf(ui.settings.alarmHour) }
    var minute by rememberSaveable { mutableStateOf(ui.settings.alarmMinute) }
    var soundLabel by remember { mutableStateOf(SoundOption.BUNDLED) }
    var customUri by rememberSaveable { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    val notificationPermissionLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission(),
        ) { _ -> ui.refresh() }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !NotificationSupport.areEnabled(context) &&
            !PermissionFlow.notificationsAlreadyRequested(context)
        ) {
            PermissionFlow.markNotificationsRequested(context)
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val customPicker =
        rememberLauncherForActivityResult(
            ActivityResultContracts.OpenDocument(),
        ) { uri ->
            if (uri != null) {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
                customUri = uri.toString()
                soundLabel = SoundOption.CUSTOM
                ui.selectSound(SoundSelection.CustomFile(uri.toString()))
            }
        }

    val ringtonePicker =
        rememberLauncherForActivityResult(
            ActivityResultContracts.StartActivityForResult(),
        ) { result ->
            val uri =
                result.data?.getParcelableExtra<android.net.Uri>(
                    android.media.RingtoneManager.EXTRA_RINGTONE_PICKED_URI,
                )
            if (uri != null) {
                soundLabel = SoundOption.SYSTEM
                ui.selectSound(SoundSelection.SystemRingtone(uri.toString()))
            }
        }

    Column(
        modifier =
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(24.dp)
                .testTag("setupScreen"),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "SISYPHUS",
            style =
                MaterialTheme.typography.headlineLarge.copy(
                    letterSpacing = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                ),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "NO ESCAPE. WALK.",
            style = MaterialTheme.typography.bodyMedium.copy(letterSpacing = 4.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))

        permissionSetupCard(ui)

        Spacer(Modifier.height(24.dp))
        sectionHeader("THE STONE")
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StepPreset.entries.filter { it != StepPreset.CUSTOM }.forEach { preset ->
                FilterChip(
                    selected = steps == preset.steps.toString(),
                    onClick = { steps = preset.steps.toString() },
                    label = { Text("${preset.title} ${preset.steps}") },
                    modifier = Modifier.testTag("preset_${preset.steps}"),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = steps,
            onValueChange = { steps = it.filter { c -> c.isDigit() }.take(5) },
            label = { Text("CUSTOM STEPS") },
            modifier = Modifier.testTag("stepsField"),
        )

        Spacer(Modifier.height(24.dp))
        sectionHeader("THE HOUR")
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = {
                android.app.TimePickerDialog(
                    context,
                    { _, selectedHour, selectedMinute ->
                        hour = selectedHour
                        minute = selectedMinute
                    },
                    hour,
                    minute,
                    android.text.format.DateFormat.is24HourFormat(context),
                ).show()
            },
            modifier = Modifier.testTag("timeField"),
        ) {
            Text(formatTime(hour, minute), modifier = Modifier.testTag("timeFieldLabel"))
        }

        Spacer(Modifier.height(24.dp))
        sectionHeader("THE SOUND")
        Spacer(Modifier.height(8.dp))
        SoundOption.entries.forEach { option ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = soundLabel == option,
                            onClick = {
                                soundLabel = option
                                when (option) {
                                    SoundOption.BUNDLED -> ui.selectSound(SoundSelection.Bundled)
                                    SoundOption.SYSTEM ->
                                        ringtonePicker.launch(
                                            Intent(
                                                android.media.RingtoneManager.ACTION_RINGTONE_PICKER,
                                            ).apply {
                                                putExtra(
                                                    android.media.RingtoneManager.EXTRA_RINGTONE_TYPE,
                                                    android.media.RingtoneManager.TYPE_ALARM,
                                                )
                                            },
                                        )
                                    SoundOption.CUSTOM -> customPicker.launch(arrayOf("audio/*"))
                                }
                            },
                        ),
            ) {
                RadioButton(selected = soundLabel == option, onClick = null)
                Text(option.label)
            }
        }
        if (soundLabel == SoundOption.CUSTOM && customUri == null) {
            Text(
                "Pick an audio file",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.testTag("pickAudioHint"),
            )
        }

        if (error != null) {
            Text(
                error.orEmpty(),
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.testTag("setupError"),
            )
        }

        Spacer(Modifier.height(24.dp))
        Button(
            onClick = {
                val stepsValue = steps.toIntOrNull()
                error =
                    when {
                        stepsValue == null -> "Enter a step requirement."
                        stepsValue < 1 || stepsValue > 10000 -> "Steps must be between 1 and 10000."
                        else -> null
                    }
                if (error == null) {
                    runCatching {
                        ui.configureAlarm(stepsValue!!, hour, minute)
                    }.onFailure {
                        error = it.message ?: "Could not arm the alarm."
                    }
                }
            },
            modifier = Modifier.testTag("saveAlarm"),
        ) {
            Text("ARM ALARM")
        }
    }
}

@Composable
private fun permissionSetupCard(ui: ChallengeViewModel) {
    val context = LocalContext.current
    val report =
        ReadinessChecker().check(
            notificationsAllowed = NotificationSupport.areEnabled(context),
            sensorAvailable = SensorSupport.isAvailable(context),
            hasAlarmSound = true,
            exactAlarmsAllowed = ExactAlarmSupport.canSchedule(context),
            fullScreenAlarmAllowed = FullScreenIntentSupport.canUse(context),
        )
    if (report.isReady) return

    val notificationLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission(),
        ) { _ -> ui.refresh() }

    val activityRecognitionLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission(),
        ) { _ -> ui.refresh() }

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .testTag("permissionCard"),
        horizontalAlignment = Alignment.Start,
    ) {
        Text("Setup required before the alarm can work", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        report.missingRequirements.forEach { requirement ->
            permissionRequirementRow(
                requirement = requirement,
                message = report.messageFor(requirement),
                onRequestNotification = {
                    notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                },
                onRequestActivityRecognition = {
                    activityRecognitionLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION)
                },
                onOpenSettings = { context.openSettings(requirement) },
            )
            Spacer(Modifier.height(8.dp))
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun permissionRequirementRow(
    requirement: ReadinessRequirement,
    message: String,
    onRequestNotification: () -> Unit,
    onRequestActivityRecognition: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .testTag("permissionRow_${requirement.name.lowercase()}"),
    ) {
        Text(
            message,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            when (requirement) {
                ReadinessRequirement.NOTIFICATIONS -> {
                    Button(onClick = onRequestNotification) {
                        Text("Allow notifications")
                    }
                    TextButton(onClick = onOpenSettings) {
                        Text("Notification settings")
                    }
                }
                ReadinessRequirement.EXACT_ALARMS,
                ReadinessRequirement.FULL_SCREEN_INTENT,
                -> {
                    Button(onClick = onOpenSettings) {
                        Text("Open settings")
                    }
                }
                ReadinessRequirement.STEP_SENSOR -> {
                    Button(onClick = onRequestActivityRecognition) {
                        Text("Allow step tracking")
                    }
                }
                ReadinessRequirement.ALARM_SOUND -> Unit
            }
        }
    }
}

private fun android.content.Context.openSettings(requirement: ReadinessRequirement) {
    val action =
        when (requirement) {
            ReadinessRequirement.NOTIFICATIONS ->
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
            ReadinessRequirement.EXACT_ALARMS ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$packageName"))
                } else {
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
                }
            ReadinessRequirement.FULL_SCREEN_INTENT ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:$packageName"))
                } else {
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
                }
            ReadinessRequirement.STEP_SENSOR,
            ReadinessRequirement.ALARM_SOUND,
            -> return
        }
    startActivity(action.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

@Composable
private fun armedScreen(
    ui: ChallengeViewModel,
    state: ChallengeViewState,
) {
    Column(
        modifier =
            Modifier
                .padding(24.dp)
                .testTag("armedScreen"),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "ALARM ARMED",
            style =
                MaterialTheme.typography.headlineMedium.copy(
                    letterSpacing = 4.sp,
                    fontWeight = FontWeight.Bold,
                ),
        )
        Spacer(Modifier.height(12.dp))
        Text("The stone is waiting for ${state.requiredSteps} steps.", style = MaterialTheme.typography.bodyLarge)
        Text(
            "Fires at ${formatTime(ui.settings.alarmHour, ui.settings.alarmMinute)}",
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = { ui.cancelAlarm() },
            modifier = Modifier.testTag("cancelAlarm"),
        ) {
            Text("CANCEL ALARM")
        }
    }
}

@Composable
private fun alarmScreen(ui: ChallengeViewModel) {
    val pulse =
        rememberInfiniteTransition(label = "alarmPulse").animateFloat(
            initialValue = 1f,
            targetValue = 1.06f,
            animationSpec =
                infiniteRepeatable(
                    animation = tween(durationMillis = 600, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse,
                ),
            label = "alarmPulseScale",
        )
    Column(
        modifier =
            Modifier
                .padding(24.dp)
                .testTag("alarmScreen"),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "THE STONE AWAITS.",
            style = MaterialTheme.typography.displaySmall,
            textAlign = TextAlign.Center,
            modifier =
                Modifier.graphicsLayer {
                    scaleX = pulse.value
                    scaleY = pulse.value
                },
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "WALK.",
            style = MaterialTheme.typography.headlineMedium.copy(letterSpacing = 6.sp),
        )
        if (ui.challengeError != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                ui.challengeError.orEmpty(),
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.testTag("startError"),
            )
        }
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = { ui.startChallenge() },
            modifier = Modifier.testTag("startChallenge"),
        ) {
            Text("START WALKING")
        }
    }
}

@Composable
private fun challengeScreen(state: ChallengeViewState) {
    val counterScale = remember { Animatable(1f) }
    LaunchedEffect(state.remainingSteps) {
        counterScale.snapTo(1.06f)
        counterScale.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing),
        )
    }
    Column(
        modifier =
            Modifier
                .padding(24.dp)
                .testTag("challengeScreen"),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "WALK.",
            style = MaterialTheme.typography.titleMedium.copy(letterSpacing = 6.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            state.remainingSteps.toString(),
            style =
                MaterialTheme.typography.displayLarge.copy(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                ),
            modifier =
                Modifier
                    .graphicsLayer {
                        scaleX = counterScale.value
                        scaleY = counterScale.value
                    }
                    .testTag("remainingSteps"),
        )
        Text(
            "STEPS REMAINING",
            style = MaterialTheme.typography.bodyMedium.copy(letterSpacing = 3.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        LinearProgressIndicator(
            progress = {
                if (state.requiredSteps == 0) 0f else state.completedSteps.toFloat() / state.requiredSteps
            },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .testTag("progressBar"),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "${state.completedSteps} of ${state.requiredSteps}",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.testTag("progressLabel"),
        )
        if (state.remainingSteps <= 200) {
            Spacer(Modifier.height(16.dp))
            Text(
                "THE SUMMIT IS CLOSE.",
                style = MaterialTheme.typography.titleMedium.copy(letterSpacing = 3.sp),
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun completedScreen(
    ui: ChallengeViewModel,
    state: ChallengeViewState,
) {
    Column(
        modifier =
            Modifier
                .padding(24.dp)
                .testTag("completedScreen"),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.testTag("completionIndicator"),
            progress = { 1f },
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "THE STONE HAS FALLEN.",
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text("${state.requiredSteps} steps. Done.", style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = { ui.acknowledge() },
            modifier = Modifier.testTag("acknowledge"),
        ) {
            Text("DONE")
        }
    }
}

private enum class SoundOption(val label: String) {
    BUNDLED("Bundled sound"),
    SYSTEM("System ringtone"),
    CUSTOM("Custom audio file"),
}

private fun formatTime(
    hour: Int,
    minute: Int,
): String {
    val period = if (hour < 12) "AM" else "PM"
    val displayHour = if (hour % 12 == 0) 12 else hour % 12
    return String.format("%d:%02d %s", displayHour, minute, period)
}
