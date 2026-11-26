package io.github.seven317.openstandby.domain.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class TraditionalLyricsTest {
    @Test
    fun convertsTimedTextWithoutChangingTimestampsOrOriginal() {
        val original = Lyrics.Timed(listOf(
            LyricLine(1250, "听见远方的声音"),
            LyricLine(2875, "等待出发，整理头发"),
            LyricLine(4000, ""),
        ))

        assertEquals(
            Lyrics.Timed(listOf(
                LyricLine(1250, "聽見遠方的聲音"),
                LyricLine(2875, "等待出發，整理頭髮"),
                LyricLine(4000, ""),
            )),
            original.toTraditionalChinese(),
        )
        assertEquals("听见远方的声音", original.lines.first().text)
    }

    @Test
    fun convertsPlainChineseAndPreservesOtherLinesAndLineBreaks() {
        val original = Lyrics.Plain("云朵☁️\r\n\n  English  \n日本の音楽\n한국 漢字\n")
        val expected = Lyrics.Plain("雲朵☁️\r\n\n  English  \n日本の音楽\n한국 漢字\n")

        assertEquals(expected, original.toTraditionalChinese())
        assertSame(Lyrics.NotFound, Lyrics.NotFound.toTraditionalChinese())
    }
}
