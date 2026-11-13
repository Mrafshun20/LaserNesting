package com.mrafshun20.lasernesting.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * All machine / sheet / nesting settings, stored as a single row (id = 1).
 * Dimensions are in millimetres, speed in mm/min.
 */
@Entity(tableName = "settings")
data class SettingsEntity(
    @PrimaryKey val id: Long = 1,
    val machinePreset: MachinePreset = MachinePreset.BYSTAR_FIBER_3015,
    val sheetPreset: SheetPreset = SheetPreset.SHEET_2000x1000,
    val sheetWidthMm: Double = 2000.0,
    val sheetLengthMm: Double = 1000.0,
    val sheetMaterial: Material = Material.MILD_STEEL,
    val sheetThicknessMm: Double = 3.0,
    val sheetCount: Int = 1,
    val kerfMm: Double = 0.2,
    val gapMm: Double = 3.0,
    val marginMm: Double = 10.0,
    val allowRotation: Boolean = true,
    val cuttingSpeedMmPerMin: Double = 6000.0,
    /**
     * App language: "fa" (Persian, the default), "en" (English) or
     * "system" (follow the device).
     */
    val language: String = "fa",
    /** Nesting engine: AUTO, TRUE_SHAPE or FAST_BOX (NestMode name). */
    val nestMode: String = "AUTO",
    /** Raster cell for true-shape nesting in mm; 0 = automatic. */
    val precisionMm: Double = 0.0,
    /** Extra rotation step for DXF parts in degrees; 0 = off. */
    val rotationStepDeg: Int = 0,
    /** Time to pierce one contour, in seconds (cut-time estimate). */
    val pierceTimeSec: Double = 0.5
)
