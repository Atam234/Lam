package com.example.argus.logger

/**
 * Mirrors start.py / logger_light.py LCID keyboard layouts and Latin <-> Cyrillic character translation.
 */
object KeyboardLayoutTranslator {

    private const val LATIN_SEQ =
        "`QWERTYUIOP[]ASDFGHJKL;'ZXCVBNM,./" +
            "qwertyuiop[]asdfghjkl;'zxcvbnm,./" +
            "~`{[}]:;\"'|<,>.?/@#$^&"

    private const val CYRILLIC_SEQ =
        "ёЙЦУКЕНГШЩЗХЪФЫВАПРОЛДЖЭЯЧСМИТЬБЮ." +
            "йцукенгшщзхъфывапролджэячсмитьбю." +
            "ЁёХхЪъЖжЭэ/БбЮю,.\"№;:?"

    private val latinToCyrillic: Map<Char, Char> =
        LATIN_SEQ.zip(CYRILLIC_SEQ).toMap()

    private val cyrillicToLatin: Map<Char, Char> =
        CYRILLIC_SEQ.zip(LATIN_SEQ).toMap()

    val cyrillicLayouts: Set<String> = setOf(
        "Russian",
        "Russian - Moldava",
        "Azeri (Cyrillic)",
        "Belarusian",
        "Kazakh",
        "Kyrgyz (Cyrillic)",
        "Mongolian (Cyrillic)",
        "Tajik",
        "Tatar",
        "Serbian (Cyrillic)",
        "Ukrainian",
        "Uzbek (Cyrillic)"
    )

    val supportedLayouts: List<Pair<String, String>> = listOf(
        "0x409" to "English - United States",
        "0x809" to "English - United Kingdom",
        "0x419" to "Russian",
        "0x819" to "Russian - Moldava",
        "0x422" to "Ukrainian",
        "0x423" to "Belarusian",
        "0x043f" to "Kazakh",
        "0x440" to "Kyrgyz (Cyrillic)",
        "0x450" to "Mongolian (Cyrillic)",
        "0x428" to "Tajik",
        "0x444" to "Tatar",
        "0x0c1a" to "Serbian (Cyrillic)",
        "0x843" to "Uzbek (Cyrillic)",
        "0x082c" to "Azeri (Cyrillic)",
        "0x407" to "German - Germany",
        "0x040c" to "French - France",
        "0x0c0a" to "Spanish - Spain (Modern Sort)",
        "0x415" to "Polish",
        "0x041f" to "Turkish"
    )

    fun updateUpperCase(capsLockOn: Boolean, shiftOn: Boolean): Boolean {
        return (capsLockOn && !shiftOn) || (!capsLockOn && shiftOn)
    }

    fun translateCharacter(
        ch: Char,
        initialLanguage: String,
        currentLanguage: String,
        upperCase: Boolean
    ): Char {
        var mapped = ch
        if ("English" in currentLanguage && "English" !in initialLanguage) {
            cyrillicToLatin[mapped]?.let { mapped = it }
        } else if (currentLanguage in cyrillicLayouts && initialLanguage !in cyrillicLayouts) {
            latinToCyrillic[mapped]?.let { mapped = it }
        }
        return if (upperCase) mapped.uppercaseChar() else mapped.lowercaseChar()
    }
}
