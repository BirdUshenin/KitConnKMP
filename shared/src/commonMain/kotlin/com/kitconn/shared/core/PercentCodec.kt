package com.kitconn.shared.core

/**
 * Декодирование %XX в UTF-8 без java.net и NSString, чтобы работало одинаково на всех платформах.
 * [plusAsSpace] повторяет поведение Android `Uri.getQueryParameter`.
 */
internal fun percentDecode(s: String, plusAsSpace: Boolean = false): String {
    if ('%' !in s && !(plusAsSpace && '+' in s)) return s
    val bytes = ArrayList<Byte>(s.length)
    val literal = StringBuilder()
    fun flushLiteral() {
        if (literal.isNotEmpty()) {
            literal.toString().encodeToByteArray().forEach { bytes.add(it) }
            literal.clear()
        }
    }
    var i = 0
    while (i < s.length) {
        val c = s[i]
        val hi = if (c == '%' && i + 2 < s.length) hex(s[i + 1]) else -1
        val lo = if (hi >= 0) hex(s[i + 2]) else -1
        when {
            c == '%' && hi >= 0 && lo >= 0 -> {
                flushLiteral()
                bytes.add((hi * 16 + lo).toByte())
                i += 3
            }
            c == '+' && plusAsSpace -> { literal.append(' '); i++ }
            else -> { literal.append(c); i++ }
        }
    }
    flushLiteral()
    return bytes.toByteArray().decodeToString()
}

private fun hex(c: Char): Int = when (c) {
    in '0'..'9' -> c - '0'
    in 'a'..'f' -> c - 'a' + 10
    in 'A'..'F' -> c - 'A' + 10
    else -> -1
}
