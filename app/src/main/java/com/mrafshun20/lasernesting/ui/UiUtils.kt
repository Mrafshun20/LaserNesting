package com.mrafshun20.lasernesting.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.mrafshun20.lasernesting.R
import com.mrafshun20.lasernesting.data.Material
import com.mrafshun20.lasernesting.data.SheetPreset
import com.mrafshun20.lasernesting.nesting.UnplacedReason
import java.util.Locale

/**
 * Formats a millimetre value without a trailing ".0". Always uses Latin
 * digits and "." as the decimal separator, whatever the device locale is,
 * so the result always parses back with [parseDouble].
 */
fun formatMm(value: Double): String {
    return if (value == value.toLong().toDouble()) {
        value.toLong().toString()
    } else {
        String.format(Locale.US, "%.1f", value)
    }
}

/**
 * Converts Persian (۰-۹) and Arabic-Indic (٠-٩) digits to Latin digits,
 * and Persian/Arabic decimal separators (٫ /) to ".". Thousands separators
 * (٬) and spaces are dropped. The result is safe for [String.toDoubleOrNull]
 * and [String.toIntOrNull].
 */
fun normalizeDigits(text: String): String {
    val out = StringBuilder(text.length)
    for (ch in text) {
        when (ch) {
            in '۰'..'۹' -> out.append('0' + (ch - '۰'))
            in '٠'..'٩' -> out.append('0' + (ch - '٠'))
            '٫', '/', ',' -> out.append('.')
            '٬', ' ', ' ' -> Unit
            else -> out.append(ch)
        }
    }
    return out.toString()
}

/** Parses a decimal the user typed, accepting Persian/Arabic digits. */
fun parseDouble(text: String): Double? = normalizeDigits(text).toDoubleOrNull()

/** Parses an integer the user typed, accepting Persian/Arabic digits. */
fun parseInt(text: String): Int? {
    val normalized = normalizeDigits(text)
    return normalized.toIntOrNull()
        ?: normalized.toDoubleOrNull()?.takeIf { it == it.toInt().toDouble() }?.toInt()
}

/** Formats a duration in minutes as "12 min" or "1 h 25 min". */
fun formatMinutes(minutes: Double, hourUnit: String = "h", minUnit: String = "min"): String {
    if (minutes <= 0) return "0 $minUnit"
    val totalSeconds = (minutes * 60).toInt()
    val hours = totalSeconds / 3600
    val mins = (totalSeconds % 3600) / 60
    return if (hours > 0) {
        "$hours $hourUnit $mins $minUnit"
    } else {
        "$mins $minUnit"
    }
}

/** Localised material name (English in en locale, Persian in fa locale). */
@Composable
fun Material.localizedName(): String = when (this) {
    Material.MILD_STEEL -> stringResource(R.string.material_mild_steel)
    Material.STAINLESS_STEEL -> stringResource(R.string.material_stainless_steel)
    Material.ALUMINIUM -> stringResource(R.string.material_aluminium)
}

/**
 * Localised sheet-preset name. Sizes are dimensions (not translated); only
 * the "Custom" preset has a translated name.
 */
@Composable
fun SheetPreset.localizedName(): String = when (this) {
    SheetPreset.CUSTOM -> stringResource(R.string.sheet_preset_custom)
    else -> displayName
}

/** Localised reason why a part could not be placed. */
@Composable
fun UnplacedReason.localized(): String = when (this) {
    UnplacedReason.INVALID -> stringResource(R.string.nest_unplaced_invalid)
    UnplacedReason.NO_USABLE_AREA -> stringResource(R.string.nest_unplaced_no_area)
    UnplacedReason.NO_FREE_SPACE -> stringResource(R.string.nest_unplaced_no_space)
    UnplacedReason.DOES_NOT_FIT -> stringResource(R.string.nest_unplaced_no_fit)
}
