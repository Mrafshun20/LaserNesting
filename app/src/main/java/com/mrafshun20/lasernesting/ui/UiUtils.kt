package com.mrafshun20.lasernesting.ui

/** Formats a millimetre value without a trailing ".0". */
fun formatMm(value: Double): String {
    return if (value == value.toLong().toDouble()) {
        value.toLong().toString()
    } else {
        "%.1f".format(value)
    }
}

/** Formats a duration in minutes as "12 min" or "1 h 25 min". */
fun formatMinutes(minutes: Double): String {
    if (minutes <= 0) return "0 min"
    val totalSeconds = (minutes * 60).toInt()
    val hours = totalSeconds / 3600
    val mins = (totalSeconds % 3600) / 60
    return if (hours > 0) {
        "$hours h $mins min"
    } else {
        "$mins min"
    }
}
