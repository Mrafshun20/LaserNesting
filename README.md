# Laser Nesting

A native Android app (plus a Windows desktop app) for sheet-metal nesting
— arrange parts on a sheet and cut them on a Bystronic laser, the way
IGEMS AutoNest does it. Parts library, sheet setup, automatic nesting, a
clear sheet drawing, utilization and waste reporting, and cut-list /
layout export.

**Version 2** adds: bilingual Persian/English, DXF import of real customer
parts, rotation angles 0/90/180/270, per-part priority, remnant reporting,
and a Windows build that shares the same core.

## What it does

- **Parts library** — the primary way to add parts is **DXF import**: pick
  a `.dxf` file from a customer and every closed outline in it becomes a
  part (true outline + circular holes, normalized to the origin). Set the
  quantity you want for each part. Manual rectangle/circle entry stays as
  a secondary "add simple part" option. Parts are saved on the phone (Room
  database) and each part keeps a stable colour used on the nest drawing
  and in exports.
- **Bilingual** — Persian and English, with a language selector in Setup.
  Persian uses the bundled **Vazirmatn** font; English uses the system
  font. The default follows the device; the selector overrides it.
- **Rotation & priority** — each part allows rotation angles **0 / 90 /
  180 / 270** (pick which ones), and a **priority** (higher priority
  nests first). The global rotation switch in Setup still applies.
- **Machine setup** — Bystronic presets: ByStar Fiber 3015 and 4020,
  BySmart 3015 and 4020. A preset defines the machine's maximum working
  area (3015: 3000 x 1500 mm, 4020: 4000 x 2000 mm). The app warns you
  if the sheet is larger than the machine bed.
- **Sheet setup** — sheet-size presets for the sheets actually nested on:
  **2000 x 1000 mm (default)** and **2500 x 1250 mm**, plus Custom.
  Sheet material (mild steel, stainless steel, aluminium), thickness and
  the number of sheets available.
- **Auto-nest** — a MaxRects bin-packing engine (pure Kotlin, no external
  dependency) packs parts highest-priority first, then largest-first,
  across as many sheets as you have, with kerf + part gap as spacing and
  a sheet edge margin. Each part is packed by its bounding box; DXF parts
  keep their true outline for area, cut length and drawing.
- **Results** — per-sheet and overall material utilization with progress
  bars, waste in % and m², sheets used, total cut length, a rough cutting
  time (cut length / cutting speed), parts placed vs requested, any
  unplaced parts clearly flagged with the reason, and the **largest
  leftover rectangle per sheet as a reusable remnant** (size shown, and
  drawn dashed-green on the sheet).
- **Sheet drawing** — each sheet drawn to scale with a 500 mm grid and the
  edge-margin boundary, parts colour-coded. DXF parts are drawn as their
  **true outline with holes**; rectangles and circles keep their shape.
  Labels are a single line — part name plus dimensions, never the material
  name — measured to fit inside the part; a part too small to hold its
  label legibly gets no label. Switch between sheets with the sheet cards.
- **Export** — cut list as CSV (part, sheet, X, Y, width, height,
  rotation in degrees, shape) and the nest layout as an SVG file (true
  DXF outlines + holes, single-line labels, remnant outline), both shared
  through the Android share sheet. No storage permission needed.

## Design

A dark professional theme for the workshop — deep charcoal surfaces with
an IGEMS-style amber accent, clean Material 3 cards, clear hierarchy and
consistent spacing and typography. The sheet itself is drawn light, like a
real sheet of metal on the bed, so the nest reads at a glance on the phone
next to the machine. Tab switches are animated; every screen is meant to
feel finished, not like a prototype.

## How the nesting works

Parts are rasterised at every allowed rotation (0/90/180/270) into a bit
mask already inflated by half of (kerf + gap), and placed on a bit-grid of
the sheet at the bottom-left-most free position. Because the mask follows
the real outline, parts nest into the concave gaps, slots and **holes of
other parts** — that is where the yield gain over plain bounding-box
packing comes from. Several strategies compete (MaxRects baseline plus
true-shape passes in different sort orders), then **seeded randomised
restarts** run while a job still has unplaced parts or could save a sheet.
The winner is chosen by: fewest unplaced parts, then fewest sheets, then
the largest reusable remnant. Priority groups are never reordered.
Results are validated against overlaps, spacing and sheet bounds by the
self-test in `core/src/test`.

Utilization uses true part area (outline minus holes and cutouts). The cut
plan cuts inner contours first and the outer contour last, parts visited
nearest-neighbour from the machine's home corner; pierces, rapid travel,
cutting time (material table or your own speed), weight, scrap and cost
are all derived from that plan. The largest free rectangle per sheet
(at least 50 x 50 mm) is reported as a remnant.

**DXF import.** The parser reads the file's `$INSUNITS` and converts to
millimetres (mm is assumed when missing), resolves block INSERTs (nested
up to depth 3), traces LINE/ARC chains into closed outlines (arcs sampled
to ≤ 5° steps), collects CIRCLE entities inside an outline as holes, and
treats a CIRCLE outside every outline as its own round part. Each
outermost closed loop is one part; inner loops are cutouts of the part
that contains them, not separate parts. Outlines are normalized so the
bounding-box min corner is at (0, 0). The parser never throws on malformed
input — problems are reported as warnings and the bad geometry is skipped.

## Build

### Android Studio

Open the project folder in Android Studio (Hedgehog or newer), let Gradle
sync, then Run on a device or emulator (min SDK 26).

### Command line — Android

```bash
gradle assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

A GitHub Actions workflow (`.github/workflows/build-apk.yml`) builds the
same debug APK on every push to `main` and uploads it as an artifact
(`laser-nesting-debug-apk`).

Requirements: JDK 17, Android SDK with API 34, Gradle 8.7. The Gradle
wrapper jar is not committed in this repo, so CI installs Gradle 8.7
itself (setup-gradle) and Android Studio downloads it on sync.

### Command line — Windows (desktop)

The desktop app shares the pure-Kotlin core (`:core`) with Android and
mirrors the same flows: parts with DXF import, setup, nest, results,
export. It is built with Compose Multiplatform (Compose Desktop).

```bash
gradle :desktop:createDistributable
```

This produces a runnable distribution (a folder with the app and its
runtime) under:

```
desktop/build/compose/binaries/main/app/LaserNesting/
```

Run it with `LaserNesting.exe` inside that folder. For a Windows
installer instead:

```bash
gradle :desktop:packageExe
```

which writes `LaserNesting-2.0.0.exe` under
`desktop/build/compose/binaries/main/exe/`.

A GitHub Actions workflow (`.github/workflows/build-windows.yml`) builds
both on every push to `main` (runner `windows-latest`) and uploads them
as artifacts: `laser-nesting-windows` (the runnable distribution, zipped)
and `laser-nesting-windows-installer` (the `.exe` installer).

Requirements: JDK 17, Gradle 8.7. No Android SDK is needed for the
desktop build.

## Project layout

```
core/                            Shared pure-Kotlin core (no Android dep.)
  src/main/kotlin/com/mrafshun20/lasernesting/
    data/
      PartModels.kt              Point, CircleHole, PartShape, Material, PartOutline
      PartOutlineJson.kt         JSON codec for a DXF outline (storage)
      Presets.kt                 MachinePreset, SheetPreset
    nesting/
      MaxRects.kt                MaxRects bin packing (Best Short Side Fit)
      OutlineGeometry.kt         Outline rotation, area, perimeter
      NestingEngine.kt           Multi-sheet nesting, priority, remnant
    dxf/
      DxfModel.kt                DxfPoint, DxfCircleHole, DxfPart, DxfParseResult
      DxfParser.kt               Pure-Kotlin DXF parser (AC1027, mm)
      DxfParserTest.kt           Standalone self-test (run with kotlinc)
    export/
      ExportCore.kt              Cut-list CSV + nest-layout SVG strings
app/                             Android app
  src/main/java/com/mrafshun20/lasernesting/
    MainActivity.kt              Bottom navigation (Parts / Setup / Nest)
    data/                        Room entities, DAOs, database, repository
      Part.kt                    PartEntity (outline, priority, rotations)
      Settings.kt                SettingsEntity (incl. language)
      AppDatabase.kt             Room database (v2)
      NestingRepository.kt       Parts + settings access
    export/
      Export.kt                  Share sheet (delegates strings to ExportCore)
    ui/
      NestingViewModel.kt        Parts + settings state, DXF import, nesting
      PartsScreen.kt             Parts library + DXF import entry point
      SetupScreen.kt             Language / machine / sheet / nesting / cutting
      ResultsScreen.kt           Utilization, sheet drawing, remnant, export
      UiUtils.kt                 Number / time formatting
      components/Fields.kt       Decimal and integer input fields
      theme/                     Dark workshop Material 3 theme + Vazirmatn
  src/main/res/
    font/                        Vazirmatn (Regular / Medium / Bold)
    values/strings.xml           English strings
    values-fa/strings.xml        Persian strings
desktop/                         Windows app (Compose Desktop)
  src/main/kotlin/com/mrafshun20/lasernesting/desktop/
    Main.kt                      Desktop entry point (window)
    DesktopState.kt              Parts / settings / nesting state, strings
    DesktopUi.kt                 Parts / setup / nest screens + sheet canvas
    Json.kt                      Minimal JSON (project save / load)
```

Package: `com.mrafshun20.lasernesting`. Min SDK 26, target SDK 34.
Kotlin + Jetpack Compose + Material 3 + Room (Android) / Compose Desktop
(Windows). The shared core is plain Kotlin/JVM.

## Current limits

- **Raster resolution.** True-shape nesting works on a 1–3 mm grid, so each
  edge can carry up to one cell of extra clearance. Quality "Best yield"
  uses a finer grid and a longer time budget.
- **No lead-ins / micro-joints / common-line cutting yet.** The DXF export
  contains the cut order on separate layers (`CUT_INNER`, `CUT_OUTER`);
  technology (lead-ins, tabs) is added in BySoft.
- **DXF import** reads LINE / ARC / CIRCLE / LWPOLYLINE / POLYLINE / INSERT;
  splines and ellipses are skipped with a warning.
- **Cutting-time figures** from the material table are typical values for
  a ~6 kW fiber laser; enter your own speed for exact estimates.

## Quality checks

```bash
kotlinc core/src/main/kotlin core/src/test/kotlin -include-runtime -d nest-test.jar
java -cp nest-test.jar com.mrafshun20.lasernesting.nesting.NestingSelfTestKt
java -cp nest-test.jar com.mrafshun20.lasernesting.dxf.DxfParserTestKt
```

The same checks run in CI (`.github/workflows/core-tests.yml`).
