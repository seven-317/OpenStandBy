package io.github.seven317.openstandby.domain.playback

internal data class PlaybackTimeline(
    val positionMs: Long,
    val updatedAtMs: Long,
    val speed: Float,
) {
    val isMoving: Boolean
        get() = updatedAtMs > 0 && speed.isFinite() && speed != 0f

    fun positionAt(nowMs: Long, durationMs: Long?): Long {
        val advance = if (isMoving) (nowMs - updatedAtMs).coerceAtLeast(0) * speed.toDouble() else 0.0
        return (positionMs + advance).toLong().coerceIn(0, durationMs ?: Long.MAX_VALUE)
    }
}
