package com.mrafshun20.lasernesting.dxf

/**
 * Standalone JVM self-test for [DxfParser]. Not used by the app.
 *
 * Run with kotlinc (no Android SDK / Gradle needed):
 *
 *   kotlinc DxfModel.kt DxfParser.kt DxfParserTest.kt -include-runtime -d dxf-test.jar
 *   java -jar dxf-test.jar [optional path to a .dxf file]
 *
 * With no argument it runs the built-in synthetic checks only. With a file
 * argument it also parses that file and prints every part found.
 *
 * The sample file (1_6_lzph.dxf) is expected to yield 19 parts and 28
 * circle holes under the corrected rule (21 outermost loops, of which the
 * two tiny 4x20 mm specks are ignored as details); the check below
 * verifies that when the file is given.
 */
fun main(args: Array<String>) {
    runSyntheticChecks()

    val path = args.firstOrNull()
    if (path == null) {
        println("No DXF file given; synthetic checks only. Pass a .dxf path to parse a real file.")
        return
    }
    val result = DxfParser.parseFile(java.io.File(path))
    println("Parsed $path")
    println("Parts: ${result.parts.size}, total holes: ${result.parts.sumOf { it.holes.size }}")
    for (part in result.parts) {
        println(
            "- ${part.name}: ${part.widthMm} x ${part.heightMm} mm, " +
                "outline ${part.outline.size} pts, holes ${part.holes.size}, layer ${part.layer}"
        )
        for (hole in part.holes) {
            println("    hole r=${hole.radius} at (${hole.center.x}, ${hole.center.y})")
        }
    }
    println("Warnings (${result.warnings.size}):")
    for (warning in result.warnings) println("  ! $warning")

    if (path.endsWith("1_6_lzph.dxf")) {
        check(result.parts.size == 19) { "Expected 19 parts, got ${result.parts.size}" }
        check(result.parts.sumOf { it.holes.size } == 28) {
            "Expected 28 holes, got ${result.parts.sumOf { it.holes.size }}"
        }
        check(result.parts.all { it.widthMm > 0.0 && it.heightMm > 0.0 }) {
            "Every part must have a positive size"
        }
        check(result.parts.all { it.outline.all { p -> p.x >= 0.0 && p.y >= 0.0 } }) {
            "Every outline must be normalised to min corner (0, 0)"
        }
        println("Sample-file checks: PASS (19 parts, 28 holes)")
    }
}

/** DXF text for one axis-aligned rectangle as 4 LINE entities. */
private fun rectDxf(x0: Double, y0: Double, x1: Double, y1: Double): String {
    fun line(ax: Double, ay: Double, bx: Double, by: Double): String = """
      0
    LINE
      8
    BY_12
     10
    $ax
     20
    $ay
     11
    $bx
     21
    $by
    """.trimIndent()
    return listOf(
        line(x0, y0, x1, y0),
        line(x1, y0, x1, y1),
        line(x1, y1, x0, y1),
        line(x0, y1, x0, y0)
    ).joinToString("\n")
}

/** DXF text for one CIRCLE entity. */
private fun circleDxf(cx: Double, cy: Double, r: Double): String = """
      0
    CIRCLE
     10
    $cx
     20
    $cy
     40
    $r
""".trimIndent()

private fun runSyntheticChecks() {
    // 1) A 100x50 rectangle from 4 LINEs, one r=5 circle hole inside it,
    //    and one standalone r=10 circle (a round part) next to it.
    val rect = """
      0
    SECTION
      2
    HEADER
      9
    ${'$'}INSUNITS
     70
         4
      0
    ENDSEC
      0
    SECTION
      2
    ENTITIES
      0
    LINE
      8
    BY_12
     10
    0.0
     20
    0.0
     11
    100.0
     21
    0.0
      0
    LINE
      8
    BY_12
     10
    100.0
     20
    0.0
     11
    100.0
     21
    50.0
      0
    LINE
      8
    BY_12
     10
    100.0
     20
    50.0
     11
    0.0
     21
    50.0
      0
    LINE
      8
    BY_12
     10
    0.0
     20
    50.0
     11
    0.0
     21
    0.0
      0
    CIRCLE
     10
    50.0
     20
    25.0
     40
    5.0
      0
    CIRCLE
     10
    200.0
     20
    100.0
     40
    10.0
      0
    ENDSEC
      0
    EOF
    """.trimIndent()
    val r1 = DxfParser.parse(rect)
    check(r1.parts.size == 2) { "Synthetic 1: expected 2 parts, got ${r1.parts.size}: ${r1.parts.map { it.name }}" }
    val rectPart = r1.parts.first { it.widthMm == 100.0 }
    check(rectPart.heightMm == 50.0) { "Synthetic 1: rectangle height wrong: ${rectPart.heightMm}" }
    check(rectPart.holes.size == 1 && rectPart.holes[0].radius == 5.0) {
        "Synthetic 1: expected one r=5 hole, got ${rectPart.holes}"
    }
    check(rectPart.holes[0].center == DxfPoint(50.0, 25.0)) {
        "Synthetic 1: hole centre wrong: ${rectPart.holes[0].center}"
    }
    check(rectPart.layer == "BY_12") { "Synthetic 1: layer wrong: ${rectPart.layer}" }
    val roundPart = r1.parts.first { it.widthMm == 20.0 }
    check(roundPart.heightMm == 20.0 && roundPart.holes.isEmpty()) {
        "Synthetic 1: round part wrong: ${roundPart.widthMm} x ${roundPart.heightMm}"
    }
    println("Synthetic check 1 (rectangle + hole + round part): PASS")

    // 2) Inches must be converted (x25.4) and warned about; an unsupported
    //    entity (TEXT) must be skipped with a warning, not crash the parse.
    val inch = """
      0
    SECTION
      2
    HEADER
      9
    ${'$'}INSUNITS
     70
         1
      0
    ENDSEC
      0
    SECTION
      2
    ENTITIES
      0
    LWPOLYLINE
     90
    4
     70
    1
     10
    0.0
     20
    0.0
     10
    2.0
     20
    0.0
     10
    2.0
     20
    1.0
     10
    0.0
     20
    1.0
      0
    TEXT
     10
    0.0
     20
    0.0
      1
    hello
      0
    ENDSEC
      0
    EOF
    """.trimIndent()
    val r2 = DxfParser.parse(inch)
    check(r2.parts.size == 1) { "Synthetic 2: expected 1 part, got ${r2.parts.size}" }
    check(r2.parts[0].widthMm == 50.8 && r2.parts[0].heightMm == 25.4) {
        "Synthetic 2: inch conversion wrong: ${r2.parts[0].widthMm} x ${r2.parts[0].heightMm}"
    }
    check(r2.warnings.any { it.contains("inch") && it.contains("25.4") }) {
        "Synthetic 2: expected inch warning, got ${r2.warnings}"
    }
    check(r2.warnings.any { it.contains("TEXT") }) {
        "Synthetic 2: expected TEXT skipped warning, got ${r2.warnings}"
    }
    println("Synthetic check 2 (inch units + skipped TEXT): PASS")

    // 3) INSERT of a named block, rotated 90 degrees, must resolve and the
    //    part must take the block name.
    val block = """
      0
    SECTION
      2
    BLOCKS
      0
    BLOCK
      2
    BRACKET
     10
    0.0
     20
    0.0
      0
    LWPOLYLINE
     90
    4
     70
    1
     10
    0.0
     20
    0.0
     10
    30.0
     20
    0.0
     10
    30.0
     20
    10.0
     10
    0.0
     20
    10.0
      0
    ENDBLK
      0
    ENDSEC
      0
    SECTION
      2
    ENTITIES
      0
    INSERT
      2
    BRACKET
     10
    500.0
     20
    500.0
     50
    90.0
      0
    ENDSEC
      0
    EOF
    """.trimIndent()
    val r3 = DxfParser.parse(block)
    check(r3.parts.size == 1) { "Synthetic 3: expected 1 part, got ${r3.parts.size}" }
    check(r3.parts[0].name == "BRACKET") { "Synthetic 3: expected block name, got ${r3.parts[0].name}" }
    check(r3.parts[0].widthMm == 10.0 && r3.parts[0].heightMm == 30.0) {
        "Synthetic 3: rotated size wrong: ${r3.parts[0].widthMm} x ${r3.parts[0].heightMm}"
    }
    check(r3.parts[0].outline.all { it.x >= 0.0 && it.y >= 0.0 }) {
        "Synthetic 3: outline not normalised"
    }
    println("Synthetic check 3 (INSERT of named block, rotated): PASS")

    // 4) Garbage input must not throw.
    val r4 = DxfParser.parse("this is not a dxf file\n0\n")
    check(r4.parts.isEmpty()) { "Synthetic 4: garbage should yield no parts" }
    check(r4.warnings.isNotEmpty()) { "Synthetic 4: garbage should yield a warning" }
    println("Synthetic check 4 (garbage input): PASS")

    // 5) One large outline plus small inner/outer detail loops: the large
    //    outline is ONE part (inner loop = cutout, tiny outer loops and
    //    tiny lone circles are ignored details), while a second large
    //    disjoint outline is still a part of its own.
    val large = rectDxf(0.0, 0.0, 200.0, 100.0)       // area 20000
    val other = rectDxf(300.0, 0.0, 400.0, 50.0)      // area 5000, disjoint
    val inner = rectDxf(50.0, 40.0, 70.0, 50.0)       // inside large: cutout
    val speck = rectDxf(500.0, 0.0, 504.0, 18.0)      // area 72: ignored
    val holeCircle = circleDxf(100.0, 50.0, 5.0)      // hole in large
    val speckCircle = circleDxf(600.0, 50.0, 2.0)     // area 12.6: ignored
    val dxf5 = """
      0
    SECTION
      2
    HEADER
      9
    ${'$'}INSUNITS
     70
         4
      0
    ENDSEC
      0
    SECTION
      2
    ENTITIES
    $large
    $other
    $inner
    $speck
    $holeCircle
    $speckCircle
      0
    ENDSEC
      0
    EOF
    """.trimIndent()
    val r5 = DxfParser.parse(dxf5)
    check(r5.parts.size == 2) { "Synthetic 5: expected 2 parts, got ${r5.parts.size}: ${r5.parts.map { it.name }}" }
    val big = r5.parts.first { it.widthMm == 200.0 }
    check(big.heightMm == 100.0) { "Synthetic 5: large part height wrong: ${big.heightMm}" }
    check(big.holes.size == 1 && big.holes[0].radius == 5.0) {
        "Synthetic 5: expected one r=5 hole in large part, got ${big.holes}"
    }
    val second = r5.parts.first { it.widthMm == 100.0 }
    check(second.heightMm == 50.0 && second.holes.isEmpty()) {
        "Synthetic 5: second part wrong: ${second.widthMm} x ${second.heightMm}"
    }
    check(r5.warnings.any { it.contains("tiny closed outline") }) {
        "Synthetic 5: expected tiny-outline warning, got ${r5.warnings}"
    }
    check(r5.warnings.any { it.contains("tiny circle") }) {
        "Synthetic 5: expected tiny-circle warning, got ${r5.warnings}"
    }
    println("Synthetic check 5 (large outline + details, multi-part kept): PASS")
}
