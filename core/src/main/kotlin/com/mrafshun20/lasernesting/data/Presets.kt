package com.mrafshun20.lasernesting.data

/**
 * Bystronic machine presets. These define the machine's maximum working
 * area (the bed). The sheet being nested is usually smaller than the bed.
 */
enum class MachinePreset(
    val displayName: String,
    val maxWidthMm: Double,
    val maxLengthMm: Double
) {
    BYSTAR_FIBER_3015("ByStar Fiber 3015", 3000.0, 1500.0),
    BYSTAR_FIBER_4020("ByStar Fiber 4020", 4000.0, 2000.0),
    BYSMART_3015("BySmart 3015", 3000.0, 1500.0),
    BYSMART_4020("BySmart 4020", 4000.0, 2000.0)
}

/**
 * Sheet-size presets. The sheets actually nested on are usually
 * 2000 x 1000 mm or 2500 x 1250 mm — smaller than the full machine bed.
 * Default is 2000 x 1000 mm.
 */
enum class SheetPreset(
    val displayName: String,
    val widthMm: Double?,
    val lengthMm: Double?
) {
    SHEET_2000x1000("2000 x 1000 mm", 2000.0, 1000.0),
    SHEET_2500x1250("2500 x 1250 mm", 2500.0, 1250.0),
    CUSTOM("Custom", null, null)
}
