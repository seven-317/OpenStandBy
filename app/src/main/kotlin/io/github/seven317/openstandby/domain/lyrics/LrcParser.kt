package io.github.seven317.openstandby.domain.lyrics

internal object LrcParser {
    private val timestamp = Regex("""\[(\d{1,4}):([0-5]\d)(?:[.:](\d{1,3}))?\]""")
    private val offset = Regex("""\[offset:\s*([+-]?\d+)\s*\]""", RegexOption.IGNORE_CASE)
    private val wordTimestamp = Regex("""<\d{1,4}:[0-5]\d(?:[.:]\d{1,3})?>""")

    fun parse(lrc: String): List<LyricLine> {
        val lines = lrc.lineSequence().map { it.trim().removePrefix("\uFEFF") }
        val offsetMs = lines.firstNotNullOfOrNull { line ->
            offset.matchEntire(line)?.groupValues?.get(1)?.toIntOrNull()
        } ?: 0
        val timedLines = sortedMapOf<Long, MutableList<String>>()

        for (line in lines) {
            val times = mutableListOf<Long>()
            var cursor = 0
            while (true) {
                val tag = timestamp.matchAt(line, cursor) ?: break
                val (_, minutes, seconds, fraction) = tag.groupValues
                val time = minutes.toLong() * 60_000 + seconds.toLong() * 1_000 + fraction.padEnd(3, '0').toLong()
                // Positive LRC offsets advance the lyrics relative to playback.
                times.add((time - offsetMs).coerceAtLeast(0))
                cursor = tag.range.last + 1
                while (cursor < line.length && line[cursor].isWhitespace()) cursor++
            }
            if (times.isEmpty()) continue

            val text = line.substring(cursor).replace(wordTimestamp, "").trim()
            for (time in times) timedLines.getOrPut(time, ::mutableListOf).add(text)
        }

        return timedLines.map { (time, texts) ->
            LyricLine(time, texts.filter(String::isNotEmpty).distinct().joinToString("\n"))
        }
    }
}
