package io.github.seven317.openstandby.domain.lyrics

import org.junit.Assert.assertEquals
import org.junit.Test

class LrcParserTest {
    @Test
    fun parsesFractionsAndSortsRepeatedTimestamps() {
        val lyrics = """
            [ti:Example]
            [00:03.12][00:01.2]First line
            [00:02.345]<00:02.345>Second line
            [00:03.12]Translation
            [00:04]
        """.trimIndent()

        assertEquals(
            listOf(
                LyricLine(1_200, "First line"),
                LyricLine(2_345, "Second line"),
                LyricLine(3_120, "First line\nTranslation"),
                LyricLine(4_000, ""),
            ),
            LrcParser.parse(lyrics),
        )
    }

    @Test
    fun appliesOffsetToAllLinesEvenWhenItFollowsTheLyrics() {
        assertEquals(
            listOf(LyricLine(0, "Opening"), LyricLine(1_750, "Next line")),
            LrcParser.parse("[00:00.1]Opening\n[00:02]Next line\n[offset:+250]"),
        )
        assertEquals(listOf(LyricLine(1_250, "Opening")), LrcParser.parse("[offset:-250]\n[00:01]Opening"))
    }

    @Test
    fun expandedChorusKeepsAnIndependentPlaybackPositionForEachOccurrence() {
        val lines = LrcParser.parse(
            "\uFEFF  [00:08.10] [00:42.50]再唱一次\n" +
                "[00:11.00][00:46.70]下一句\n" +
                "[00:42.50]再唱一次\n" +
                "[00:42.50]Sing it again",
        )

        assertEquals(
            listOf(
                LyricLine(8_100, "再唱一次"),
                LyricLine(11_000, "下一句"),
                LyricLine(42_500, "再唱一次\nSing it again"),
                LyricLine(46_700, "下一句"),
            ),
            lines,
        )
        assertEquals(2, lines.activeIndexAt(42_500))
        assertEquals(0, lines.activeIndexAt(8_100))
    }

    @Test
    fun selectsBoundariesAndHandlesBackwardSeek() {
        val lines = LrcParser.parse("[00:01.02]First\n[00:03.015]Second\n[00:06]")

        assertEquals(-1, lines.activeIndexAt(1_019))
        assertEquals(0, lines.activeIndexAt(1_020))
        assertEquals(1, lines.activeIndexAt(5_999))
        assertEquals(2, lines.activeIndexAt(6_000))
        assertEquals(0, lines.activeIndexAt(2_000))
        assertEquals(-1, emptyList<LyricLine>().activeIndexAt(1_000))
    }
}
