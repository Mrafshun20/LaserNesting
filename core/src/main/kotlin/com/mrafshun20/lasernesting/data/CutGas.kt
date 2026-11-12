package com.mrafshun20.lasernesting.data

/** Cutting assist gas for a fibre laser. */
enum class CutGasKind(val formula: String) {
    OXYGEN("O2"),
    NITROGEN("N2")
}

/**
 * Cutting assist gas, by material and thickness. Standard fibre-laser
 * practice: mild steel cuts with oxygen, stainless steel and aluminium
 * with nitrogen. The gas is what decides the per-metre cutting rate, so
 * it is shown next to the rate the user enters.
 */
object CutGas {
    fun kindFor(material: Material, thicknessMm: Double): CutGasKind =
        when (material) {
            Material.MILD_STEEL -> CutGasKind.OXYGEN
            Material.STAINLESS_STEEL, Material.ALUMINIUM -> CutGasKind.NITROGEN
        }

    /** Short label, Persian (the shop language): gas name + formula. */
    fun forMaterial(material: Material, thicknessMm: Double): String =
        when (kindFor(material, thicknessMm)) {
            CutGasKind.OXYGEN -> "اکسیژن (O2)"
            CutGasKind.NITROGEN -> "نیتروژن (N2)"
        }
}
