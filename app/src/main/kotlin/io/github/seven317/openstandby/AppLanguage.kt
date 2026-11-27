package io.github.seven317.openstandby

import androidx.annotation.StringRes
import androidx.core.os.LocaleListCompat
import java.util.Locale

internal enum class AppLanguage(val tag: String, @param:StringRes val label: Int) {
    System("", R.string.language_system),
    English("en", R.string.language_english),
    TraditionalChinese("zh-TW", R.string.language_traditional_chinese),
    ;

    companion object {
        fun fromLocales(locales: LocaleListCompat): AppLanguage {
            val locale = locales[0] ?: return System
            return entries.firstOrNull {
                it != System && LocaleListCompat.matchesLanguageAndScript(locale, Locale.forLanguageTag(it.tag))
            } ?: System
        }
    }
}
