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
     * App language: "fa" (Persian) or "en" (English). Persian is the
     * default; "system" (follow the device) is only a legacy stored
     * value and is migrated to Persian on load.
     */
    val language: String = "fa",
    /** Sheet price per kg; 0 = no material cost figure. */
    val pricePerKg: Double = 0.0,
    /** Laser machine rate per cutting minute; 0 = no time cost figure. */
    val machineRatePerMinute: Double = 0.0,
    /** Cost of one pierce; 0 = no pierce cost figure. */
    val pierceCost: Double = 0.0
)
