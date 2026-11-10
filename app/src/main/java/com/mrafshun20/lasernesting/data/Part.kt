package com.mrafshun20.lasernesting.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter

/** Shape of a part. Circles are stored with width == height == diameter. */
enum class PartShape {
    RECTANGLE,
    CIRCLE
}

/** Sheet/part material. */
enum class Material(val displayName: String) {
    MILD_STEEL("Mild Steel"),
    STAINLESS_STEEL("Stainless Steel"),
    ALUMINIUM("Aluminium")
}

/**
 * One part to cut. Dimensions are in millimetres.
 * For a circle, [widthMm] and [heightMm] both hold the diameter.
 */
@Entity(tableName = "parts")
data class PartEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val shape: PartShape,
    val widthMm: Double,
    val heightMm: Double,
    val quantity: Int,
    val material: Material,
    val thicknessMm: Double,
    val allowRotation: Boolean
)

/** Room type converters for the enums used by the entities. */
class Converters {
    @TypeConverter
    fun partShapeToString(shape: PartShape): String = shape.name

    @TypeConverter
    fun stringToPartShape(value: String): PartShape = PartShape.valueOf(value)

    @TypeConverter
    fun materialToString(material: Material): String = material.name

    @TypeConverter
    fun stringToMaterial(value: String): Material = Material.valueOf(value)

    @TypeConverter
    fun machinePresetToString(preset: MachinePreset): String = preset.name

    @TypeConverter
    fun stringToMachinePreset(value: String): MachinePreset = MachinePreset.valueOf(value)

    @TypeConverter
    fun sheetPresetToString(preset: SheetPreset): String = preset.name

    @TypeConverter
    fun stringToSheetPreset(value: String): SheetPreset = SheetPreset.valueOf(value)
}
