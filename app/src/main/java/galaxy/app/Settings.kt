package galaxy.app

import android.content.Context
import galaxy.core.Emotion
import galaxy.core.brain.BrainSettings
import galaxy.core.brain.Prediction

/** What the user chose in the menu, saved on the phone. */
data class UserSettings(
    val name: String = "",
    val nameAsked: Boolean = false,
    val nightStartHour: Int = 22,
    val nightEndHour: Int = 7,
    val soundOn: Boolean = true,
    /** Say real words; otherwise Galaxy answers with robot beeps. */
    val speakWords: Boolean = false,
    val voiceOn: Boolean = true,
    val cameraOn: Boolean = true,
    /** Chosen eye color (ARGB), or null for Galaxy's cyan. */
    val eyeColor: Long? = null,
    /** Magic trick: what Galaxy will "guess". */
    val prediction: Prediction? = null,
    /** The look Galaxy rests in when nothing special happens. */
    val defaultEmotion: Emotion = Emotion.CALM,
) {
    fun toBrain() = BrainSettings(
        name = name,
        nightStartHour = nightStartHour,
        nightEndHour = nightEndHour,
        defaultEmotion = defaultEmotion,
    )
}

class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("galaxy", Context.MODE_PRIVATE)

    fun load() = UserSettings(
        name = prefs.getString(NAME, "") ?: "",
        nameAsked = prefs.getBoolean(NAME_ASKED, false),
        nightStartHour = prefs.getInt(NIGHT_START, 22),
        nightEndHour = prefs.getInt(NIGHT_END, 7),
        soundOn = prefs.getBoolean(SOUND_ON, true),
        speakWords = prefs.getBoolean(SPEAK_WORDS, false),
        voiceOn = prefs.getBoolean(VOICE_ON, true),
        cameraOn = prefs.getBoolean(CAMERA_ON, true),
        eyeColor = prefs.getLong(EYE_COLOR, 0L).takeIf { it != 0L },
        prediction = Prediction.decode(prefs.getString(PREDICTION, null)),
        defaultEmotion = Emotion.entries.firstOrNull { it.name == prefs.getString(DEFAULT_EMOTION, null) }
            ?: Emotion.CALM,
    )

    fun save(settings: UserSettings) {
        prefs.edit()
            .putString(NAME, settings.name)
            .putBoolean(NAME_ASKED, settings.nameAsked)
            .putInt(NIGHT_START, settings.nightStartHour)
            .putInt(NIGHT_END, settings.nightEndHour)
            .putBoolean(SOUND_ON, settings.soundOn)
            .putBoolean(SPEAK_WORDS, settings.speakWords)
            .putBoolean(VOICE_ON, settings.voiceOn)
            .putBoolean(CAMERA_ON, settings.cameraOn)
            .putLong(EYE_COLOR, settings.eyeColor ?: 0L)
            .putString(PREDICTION, settings.prediction?.encode())
            .putString(DEFAULT_EMOTION, settings.defaultEmotion.name)
            .apply()
    }

    private companion object {
        const val NAME = "name"
        const val NAME_ASKED = "name_asked"
        const val NIGHT_START = "night_start"
        const val NIGHT_END = "night_end"
        const val SOUND_ON = "sound_on"
        const val SPEAK_WORDS = "speak_words"
        const val VOICE_ON = "voice_on"
        const val CAMERA_ON = "camera_on"
        const val EYE_COLOR = "eye_color"
        const val PREDICTION = "prediction"
        const val DEFAULT_EMOTION = "default_emotion"
    }
}
