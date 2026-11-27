package io.github.seven317.openstandby.domain.lyrics

internal object YrcParser {
    private val lineTimestamp = Regex("""\[(\d+),\d+\](.*)""")
    private val wordTimestamp = Regex("""\(\d+,\d+,\d+\)""")

    fun parse(yrc: String): List<LyricLine> = yrc.lineSequence()
        .mapNotNull { line ->
            val match = lineTimestamp.matchEntire(line.trimStart().removePrefix("\uFEFF").trimStart())
                ?: return@mapNotNull null
            val (start, words) = match.destructured
            val time = start.toLongOrNull() ?: return@mapNotNull null
            LyricLine(time, wordTimestamp.replace(words, "").trim())
        }
        .groupBy(LyricLine::timestampMs, LyricLine::text)
        .toLyricLines()
}
