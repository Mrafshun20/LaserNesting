package com.mrafshun20.lasernesting.util

import java.util.Locale

/**
 * Locale-proof number formatting and parsing.
 *
 * Java's String.format uses the default locale, which on a Persian device
 * produces Persian digits and a "٫" separator. That breaks CSV/SVG/DXF
 * files and round-tripping text fields. Everything that is written to a
 * file or parsed back goes through here (always '.' and ASCII digits).
 */
object Fmt {

    /** Formats with [decimals] places using '.' and ASCII digits. */
    fun f(value: Double, decimals: Int = 1): String =
        String.format(Locale.US, "%.${decimals}f", value)

    /** Millimetres without a trailing ".0" (e.g. 250 or 12.5). */
    fun mm(value: Double): String =
        if (value == value.toLong().toDouble()) value.toLong().toString()
        else String.format(Locale.US, "%.1f", value)

    /** Like [mm] but with up to two decimals, for files. */
    fun num(value: Double): String =
        if (value == value.toLong().toDouble()) value.toLong().toString()
        else String.format(Locale.US, "%.2f", value)

    /** Converts Persian/Arabic-Indic digits and separators to ASCII. */
    fun normalizeDigits(text: String): String {
        val sb = StringBuilder(text.length)
        for (ch in text) {
            sb.append(
                when (ch) {
                    in '\u06F0'..'\u06F9' -> '0' + (ch - '\u06F0')
                    in '\u0660'..'\u0669' -> '0' + (ch - '\u0660')
                    '\u066B', '\u066C', '\u060C', ',' -> '.'
                    '\u2212' -> '-'
                    else -> ch
                }
            )
        }
        return sb.toString().trim()
    }

    /** Parses a user-typed number in any digit script; null when invalid. */
    fun parseDouble(text: String): Double? = normalizeDigits(text).toDoubleOrNull()

    fun parseInt(text: String): Int? {
        val n = normalizeDigits(text)
        return n.toIntOrNull() ?: n.toDoubleOrNull()?.toInt()
    }
}
