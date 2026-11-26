package io.github.seven317.openstandby.domain.lyrics

import com.github.houbb.opencc4j.util.ZhConverterUtil
import java.lang.Character.UnicodeScript

internal fun Lyrics.toTraditionalChinese(): Lyrics = when (this) {
    is Lyrics.Timed -> copy(lines = lines.map { line ->
        val text = line.text.toTraditionalLyricText()
        if (text == line.text) line else line.copy(text = text)
    })
    is Lyrics.Plain -> copy(text = text.toTraditionalLyricText())
    else -> this
}

private val lyricTextLines = Regex("[^\\r\\n]+")

private fun String.toTraditionalLyricText(): String =
    lyricTextLines.replace(this) { convertChineseLine(it.value) }

private fun convertChineseLine(line: String): String {
    var hasHan = false
    var index = 0
    while (index < line.length) {
        val codePoint = line.codePointAt(index)
        when (UnicodeScript.of(codePoint)) {
            UnicodeScript.HAN -> hasHan = true
            // Han characters also appear in Japanese and Korean lyrics.
            UnicodeScript.HIRAGANA, UnicodeScript.KATAKANA, UnicodeScript.HANGUL -> return line
            else -> Unit
        }
        index += Character.charCount(codePoint)
    }
    return if (hasHan) ZhConverterUtil.toTraditional(line) else line
}
