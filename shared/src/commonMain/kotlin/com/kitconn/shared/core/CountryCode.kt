package com.kitconn.shared.core

object CountryCode {
    /** Двухбуквенный код страны по названию из API. */
    fun code(country: String): String? = when (country.trim().lowercase()) {
        "netherlands", "нидерланды", "nl" -> "NL"
        "italy", "италия", "it" -> "IT"
        "poland", "польша", "pl" -> "PL"
        "germany", "германия", "de" -> "DE"
        "usa", "united states", "сша", "us" -> "US"
        "finland", "финляндия", "fi" -> "FI"
        "sweden", "швеция", "se" -> "SE"
        "turkey", "турция", "tr" -> "TR"
        "russia", "россия", "ru" -> "RU"
        "france", "франция", "fr" -> "FR"
        "uk", "united kingdom", "великобритания", "gb" -> "GB"
        else -> if (country.length == 2) country.uppercase() else null
    }

    fun flagEmoji(country: String): String {
        val code = code(country)?.takeIf { it.length == 2 } ?: return "🌐"
        return code.map { charToRegionalIndicator(it) }.joinToString("")
    }

    private fun charToRegionalIndicator(c: Char): String {
        val cp = 0x1F1E6 + (c.code - 'A'.code)
        // Суррогатная пара вручную: Char.toChars доступен не во всех целях common-кода
        val v = cp - 0x10000
        return charArrayOf((0xD800 + (v shr 10)).toChar(), (0xDC00 + (v and 0x3FF)).toChar()).concatToString()
    }
}
