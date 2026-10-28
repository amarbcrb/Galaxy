package galaxy.app

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import galaxy.core.Emotion
import galaxy.core.brain.Command

private val TIMER_PRESETS = listOf(1, 3, 5, 10, 15, 30)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalaxyMenu(
    settings: UserSettings,
    timerRemainingMillis: Long?,
    onStartTimer: (minutes: Int) -> Unit,
    onCancelTimer: () -> Unit,
    onSettingsChange: (UserSettings) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Section("Minuteur")
            if (timerRemainingMillis != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("En cours : ${formatCountdown(timerRemainingMillis)}")
                    Spacer(Modifier.width(16.dp))
                    OutlinedButton(onClick = onCancelTimer) { Text("Arrêter") }
                }
            } else {
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TIMER_PRESETS.forEach { minutes ->
                        FilledTonalButton(onClick = { onStartTimer(minutes) }) { Text("$minutes min") }
                    }
                }
                CustomTimer(onStartTimer)
            }

            HorizontalDivider()
            Section("Ton prénom")
            OutlinedTextField(
                value = settings.name,
                onValueChange = { onSettingsChange(settings.copy(name = it.take(30), nameAsked = true)) },
                singleLine = true,
                placeholder = { Text("Prénom") },
            )

            HorizontalDivider()
            Section("Mode nuit")
            Text(
                "Galaxy dort et l'écran devient très sombre de ${settings.nightStartHour}h à ${settings.nightEndHour}h.",
                style = MaterialTheme.typography.bodyMedium,
            )
            HourStepper("Début", settings.nightStartHour) { onSettingsChange(settings.copy(nightStartHour = it)) }
            HourStepper("Fin", settings.nightEndHour) { onSettingsChange(settings.copy(nightEndHour = it)) }

            HorizontalDivider()
            Toggle("Son et voix", settings.soundOn) { onSettingsChange(settings.copy(soundOn = it)) }
            Toggle("Commandes vocales (micro)", settings.voiceOn) { onSettingsChange(settings.copy(voiceOn = it)) }
            Toggle("Me suivre des yeux (caméra)", settings.cameraOn) { onSettingsChange(settings.copy(cameraOn = it)) }

            HorizontalDivider()
            Section("Comment jouer avec Galaxy")
            Text(
                "Caresse (va-et-vient) : il est content, puis amoureux\n" +
                    "Tap : il sursaute. Plein de taps : il se fâche\n" +
                    "Appui long : il rit\n" +
                    "Double-tap : il te donne l'heure\n" +
                    "Secouer : il a le tournis\n" +
                    "Poser à plat ou retourner : il s'endort\n" +
                    "Tourner vite le téléphone : sa tête tourne\n" +
                    "Glisser vers le haut : ce menu",
                style = MaterialTheme.typography.bodyMedium,
            )
            Section("Ce qu'il comprend à la voix")
            Text(
                "« Dors », « Réveille-toi », « Rigole », « Souris », « Fâche-toi », « Triste », " +
                    "« Je t'aime », « Surprise », « Tourne », « Tu as faim ? », « Quelle heure est-il ? », " +
                    "« Minuteur 5 minutes », « Stop »",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun Toggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

private val SECRET_COMMANDS = listOf(
    "Dormir" to Command.Sleep,
    "Réveiller" to Command.Wake,
    "Rire" to Command.Act(Emotion.LAUGHING),
    "Content" to Command.Act(Emotion.HAPPY),
    "Amoureux" to Command.Act(Emotion.LOVE),
    "Surpris" to Command.Act(Emotion.SURPRISED),
    "Curieux" to Command.Act(Emotion.CURIOUS),
    "Fâché" to Command.Act(Emotion.ANGRY),
    "Triste" to Command.Act(Emotion.SAD),
    "Tournis" to Command.Act(Emotion.DIZZY),
    "Faim" to Command.Act(Emotion.HUNGRY),
    "Fatigué" to Command.Act(Emotion.SLEEPY),
    "L'heure" to Command.TellTime,
)

/** Hidden menu (two-finger hold) to make Galaxy do things on demand. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecretMenu(onCommand: (Command) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Section("Commandes secrètes")
            SECRET_COMMANDS.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { (label, command) ->
                        FilledTonalButton(onClick = { onCommand(command) }, modifier = Modifier.weight(1f)) {
                            Text(label, maxLines = 1)
                        }
                    }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun Section(title: String) {
    Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
}

@Composable
private fun CustomTimer(onStart: (Int) -> Unit) {
    var text by remember { mutableStateOf("") }
    val minutes = text.toIntOrNull()?.takeIf { it in 1..600 }
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = text,
            onValueChange = { value -> text = value.filter { it.isDigit() }.take(3) },
            singleLine = true,
            label = { Text("Minutes") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.width(140.dp),
        )
        Spacer(Modifier.width(12.dp))
        Button(onClick = { minutes?.let(onStart) }, enabled = minutes != null) { Text("Lancer") }
    }
}

@Composable
private fun HourStepper(label: String, hour: Int, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.width(64.dp))
        OutlinedButton(onClick = { onChange((hour + 23) % 24) }) { Text("-") }
        Text("${hour}h", Modifier.width(56.dp).padding(horizontal = 8.dp), style = MaterialTheme.typography.titleMedium)
        OutlinedButton(onClick = { onChange((hour + 1) % 24) }) { Text("+") }
    }
}

/** Asked on first launch so Galaxy can greet the user by name. */
@Composable
fun NameDialog(onDone: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = {},
        title = { Text("Salut, moi c'est Galaxy !") },
        text = {
            Column {
                Text("Comment tu t'appelles ?")
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(30) },
                    singleLine = true,
                    placeholder = { Text("Prénom") },
                )
            }
        },
        confirmButton = {
            Button(onClick = { onDone(name.trim()) }, enabled = name.isNotBlank()) { Text("C'est parti") }
        },
        dismissButton = {
            TextButton(onClick = { onDone("") }) { Text("Plus tard") }
        },
    )
}
