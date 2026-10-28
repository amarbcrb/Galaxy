package galaxy.core.face

/**
 * Smooths the face position seen by the camera, and says when someone shows up again
 * after being away for a while.
 */
class FacePresence(
    private val absenceMillis: Long = 20_000,
    private val lostMillis: Long = 1_500,
) {
    /** Where your face is, in the same -1..1 coordinates as [Look], or null if nobody is there. */
    var look: Look? = null
        private set

    private var lastSeen = Long.MIN_VALUE / 2

    /** Forgets the current face, e.g. when the camera is turned off. */
    fun forget() {
        look = null
    }

    /** @return true when a face appears after an absence. */
    fun onFrame(face: Look?, timeMillis: Long): Boolean {
        if (face == null) {
            if (timeMillis - lastSeen > lostMillis) look = null
            return false
        }
        val appeared = timeMillis - lastSeen > absenceMillis
        lastSeen = timeMillis
        look = look?.let { Look(lerp(it.x, face.x, 0.5f), lerp(it.y, face.y, 0.5f)) } ?: face
        return appeared
    }
}
