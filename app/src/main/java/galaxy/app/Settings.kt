package galaxy.app

import android.content.Context
import galaxy.core.brain.BrainSettings

/** What the user chose in the menu, saved on the phone. */
data class UserSettings(
    val name: String = "",
    val nameAsked: Boolean = false,
    val nightStartHour: Int = 22,
    val nightEndHour: Int = 7,
    val soundOn: Boolean = true,
    val voiceOn: Boolean = true,
    val cameraOn: Boolean = true,
) {
    fun toBrain() = BrainSettings(name = name, nightStartHour = nightStartHour, nightEndHour = nightEndHour)
}

class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("galaxy", Context.MODE_PRIVATE)

    fun load() = UserSettings(
        name = prefs.getString(NAME, "") ?: "",
        nameAsked = prefs.getBoolean(NAME_ASKED, false),
        nightStartHour = prefs.getInt(NIGHT_START, 22),
        nightEndHour = prefs.getInt(NIGHT_END, 7),
        soundOn = prefs.getBoolean(SOUND_ON, true),
        voiceOn = prefs.getBoolean(VOICE_ON, true),
        cameraOn = prefs.getBoolean(CAMERA_ON, true),
    )

    fun save(settings: UserSettings) {
        prefs.edit()
            .putString(NAME, settings.name)
            .putBoolean(NAME_ASKED, settings.nameAsked)
            .putInt(NIGHT_START, settings.nightStartHour)
            .putInt(NIGHT_END, settings.nightEndHour)
            .putBoolean(SOUND_ON, settings.soundOn)
            .putBoolean(VOICE_ON, settings.voiceOn)
            .putBoolean(CAMERA_ON, settings.cameraOn)
            .apply()
    }

    private companion object {
        const val NAME = "name"
        const val NAME_ASKED = "name_asked"
        const val NIGHT_START = "night_start"
        const val NIGHT_END = "night_end"
        const val SOUND_ON = "sound_on"
        const val VOICE_ON = "voice_on"
        const val CAMERA_ON = "camera_on"
    }
}
