package com.mrafshun20.lasernesting.data

/**
 * Cutting data per material and thickness: density, a typical effective
 * fiber-laser cutting speed and pierce time. The numbers are generic
 * ~6 kW fiber values (already reduced for corners and acceleration) used for
 * estimates only — the cutting speed in Setup can always be set by hand.
 */
object MaterialDb {

    /** Density in kg per cubic metre. */
    fun densityKgPerM3(material: Material): Double = when (material) {
        Material.MILD_STEEL -> 7850.0
        Material.STAINLESS_STEEL -> 7930.0
        Material.ALUMINIUM -> 2700.0
    }

    // (thickness mm, speed m/min) at the machine, before the effective factor.
    private val speedTable: Map<Material, List<Pair<Double, Double>>> = mapOf(
        Material.MILD_STEEL to listOf(
            1.0 to 40.0, 2.0 to 25.0, 3.0 to 14.0, 4.0 to 10.0, 5.0 to 7.0, 6.0 to 4.5,
            8.0 to 3.0, 10.0 to 2.2, 12.0 to 1.8, 15.0 to 1.2, 20.0 to 0.9
        ),
        Material.STAINLESS_STEEL to listOf(
            1.0 to 40.0, 2.0 to 22.0, 3.0 to 12.0, 4.0 to 7.5, 5.0 to 5.5, 6.0 to 4.2,
            8.0 to 2.5, 10.0 to 1.6, 12.0 to 1.1, 15.0 to 0.7, 20.0 to 0.35
        ),
        Material.ALUMINIUM to listOf(
            1.0 to 40.0, 2.0 to 24.0, 3.0 to 14.0, 4.0 to 9.0, 5.0 to 6.5, 6.0 to 5.0,
            8.0 to 3.0, 10.0 to 2.0, 12.0 to 1.4, 15.0 to 0.8, 20.0 to 0.4
        )
    )

    /** Share of the table speed that is reached on average over real contours. */
    private const val EFFECTIVE_FACTOR = 0.7

    /** Suggested average cutting speed in mm/min for [thicknessMm]. */
    fun suggestedSpeedMmPerMin(material: Material, thicknessMm: Double): Double {
        val table = speedTable.getValue(material)
        val t = thicknessMm.coerceIn(table.first().first, table.last().first)
        var speed = table.last().second
        for (i in 0 until table.size - 1) {
            val (t0, s0) = table[i]
            val (t1, s1) = table[i + 1]
            if (t in t0..t1) {
                val k = if (t1 > t0) (t - t0) / (t1 - t0) else 0.0
                // Interpolate on a log scale: speed falls roughly exponentially.
                speed = Math.exp(Math.log(s0) + k * (Math.log(s1) - Math.log(s0)))
                break
            }
        }
        return speed * 1000.0 * EFFECTIVE_FACTOR
    }

    /** Typical pierce time in seconds for one pierce at [thicknessMm]. */
    fun pierceSeconds(material: Material, thicknessMm: Double): Double {
        val points = listOf(
            1.0 to 0.2, 2.0 to 0.3, 3.0 to 0.5, 4.0 to 0.7, 6.0 to 1.2,
            8.0 to 2.0, 10.0 to 3.0, 12.0 to 4.0, 15.0 to 6.0, 20.0 to 10.0
        )
        val t = thicknessMm.coerceIn(points.first().first, points.last().first)
        var secs = points.last().second
        for (i in 0 until points.size - 1) {
            val (t0, p0) = points[i]
            val (t1, p1) = points[i + 1]
            if (t in t0..t1) {
                val k = if (t1 > t0) (t - t0) / (t1 - t0) else 0.0
                secs = p0 + k * (p1 - p0)
                break
            }
        }
        return if (material == Material.STAINLESS_STEEL) secs * 1.3 else secs
    }

    /** Weight in kg of [areaMm2] of sheet metal [thicknessMm] thick. */
    fun weightKg(material: Material, areaMm2: Double, thicknessMm: Double): Double =
        areaMm2 * thicknessMm * 1e-9 * densityKgPerM3(material)
}
