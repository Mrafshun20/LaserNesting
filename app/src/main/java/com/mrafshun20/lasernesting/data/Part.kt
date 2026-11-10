package com.mrafshun20.lasernesting.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter

/**
 * One part to cut. Dimensions are in millimetres.
 * For a circle, [widthMm] and [heightMm] both hold the diameter.
 * For a DXF part, [widthMm]/[heightMm] hold the bounding-box size and
 * [outlineJson] holds the true outline (with holes).
 */
@Entity(tableName = "parts")
data class PartEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val shape: PartShape,
    /** Bounding-box width in millimetres. */
    val widthMm: Double,
    /** Bounding-box height in millimetres. */
    val heightMm: Double,
    val quantity: Int,
    val material: Material,
    val thicknessMm: Double,
    /** Whether this part may be rotated when nesting (0/90/180/270). */
    val allowRotation: Boolean = true,
    /** Rotation angles this part may use, e.g. "0,90,180,270". */
    val allowedRotationsCsv: String = "0,90,180,270",
    /** Higher priority nests first. */
    val priority: Int = 0,
    /** True DXF outline as JSON (PartOutlineJson), null for rect/circle. */
    val outlineJson: String? = null,
    /** Source DXF file name, when this part came from a DXF import. */
    val sourceFile: String? = null
) {
    /** Allowed rotation angles as a set, from [allowedRotationsCsv]. */
    val allowedRotations: Set<Int>
        get() = allowedRotationsCsv.split(',')
            .mapNotNull { it.trim().toIntOrNull() }
            .filter { it in setOf(0, 90, 180, 270) }
            .toSet()
            .ifEmpty { setOf(0) }

    /** The true DXF outline, decoded from [outlineJson]. */
    val outline: PartOutline?
        get() = PartOutlineJson.decode(outlineJson)
}

/** Room type converters for the enums used by the entities. */
class Converters {
    @TypeConverter
    fun partShapeToString(shape: PartShape): String = shape.name

    @TypeConverter
    fun stringToPartShape(value: String): PartShape =
        runCatching { PartShape.valueOf(value) }.getOrDefault(PartShape.RECTANGLE)

    @TypeConverter
    fun materialToString(material: Material): String = material.name

    @TypeConverter
    fun stringToMaterial(value: String): Material =
        runCatching { Material.valueOf(value) }.getOrDefault(Material.MILD_STEEL)

    @TypeConverter
    fun machinePresetToString(preset: MachinePreset): String = preset.name

    @TypeConverter
    fun stringToMachinePreset(value: String): MachinePreset =
        runCatching { MachinePreset.valueOf(value) }
            .getOrDefault(MachinePreset.BYSTAR_FIBER_3015)

    @TypeConverter
    fun sheetPresetToString(preset: SheetPreset): String = preset.name

    @TypeConverter
    fun stringToSheetPreset(value: String): SheetPreset =
        runCatching { SheetPreset.valueOf(value) }
            .getOrDefault(SheetPreset.SHEET_2000x1000)
}
