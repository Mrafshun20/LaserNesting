# Laser Nesting

A native Android app for sheet-metal nesting — arrange parts on a sheet and
cut them on a Bystronic laser, the way IGEMS AutoNest does it. Parts library,
sheet setup, automatic nesting, a clear sheet drawing, utilization and waste
reporting, and cut-list / layout export.

## What it does

- **Parts library** — add, edit and delete parts. Rectangles and circles,
  with name, size (mm), quantity, material, thickness and a per-part
  90° rotation toggle. Parts are saved on the phone (Room database) and
  each part keeps a stable colour used on the nest drawing and in exports.
- **Machine setup** — Bystronic presets: ByStar Fiber 3015 and 4020,
  BySmart 3015 and 4020. A preset defines the machine's maximum working
  area (3015: 3000 x 1500 mm, 4020: 4000 x 2000 mm). The app warns you
  if the sheet is larger than the machine bed.
- **Sheet setup** — sheet-size presets for the sheets actually nested on:
  **2000 x 1000 mm (default)** and **2500 x 1250 mm**, plus Custom.
  Sheet material (mild steel, stainless steel, aluminium), thickness and
  the number of sheets available.
- **Auto-nest** — a MaxRects bin-packing engine (pure Kotlin, no external
  dependency) packs parts largest-first across as many sheets as you have,
  with 90° rotation, kerf + part gap as spacing and a sheet edge margin.
- **Results** — per-sheet and overall material utilization with progress
  bars, waste in % and m², sheets used, total cut length, a rough cutting
  time (cut length / cutting speed), parts placed vs requested, and any
  unplaced parts clearly flagged with the reason.
- **Sheet drawing** — each sheet drawn to scale with a 500 mm grid and the
  edge-margin boundary, parts colour-coded and labelled with name and
  dimensions. Switch between sheets with the sheet cards.
- **Export** — cut list as CSV (part, sheet, X, Y, width, height, rotated,
  shape) and the nest layout as an SVG file, both shared through the
  Android share sheet. No storage permission needed.

## Design

A dark professional theme for the workshop — deep charcoal surfaces with
an IGEMS-style amber accent, clean Material 3 cards, clear hierarchy and
consistent spacing and typography. The sheet itself is drawn light, like a
real sheet of metal on the bed, so the nest reads at a glance on the phone
next to the machine. Tab switches are animated; every screen is meant to
feel finished, not like a prototype.

## How the nesting works

Each part is inflated by (kerf + gap) for packing so neighbouring cuts
never merge, and the usable area is the sheet minus the edge margin on all
four sides. Parts are packed largest-first with the MaxRects Best Short
Side Fit heuristic, trying the rotated orientation when rotation is
allowed (globally and per part). Packing continues across sheets until
every part is placed or the sheets run out. Utilization uses the true part
area (circle area for circles). Cut length is the sum of part perimeters
(circumference for circles); cutting time is cut length divided by the
cutting speed you set.

## Build

### Android Studio

Open the project folder in Android Studio (Hedgehog or newer), let Gradle
sync, then Run on a device or emulator (min SDK 26).

### Command line

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

## Project layout

```
app/src/main/java/com/mrafshun20/lasernesting/
  MainActivity.kt            Bottom navigation (Parts / Setup / Nest)
  data/                      Room entities, DAOs, database, repository
    Part.kt                  PartEntity, PartShape, Material, converters
    Settings.kt              MachinePreset, SheetPreset, SettingsEntity
    AppDatabase.kt           Room database
    NestingRepository.kt     Parts + settings access
  nesting/
    MaxRects.kt              MaxRects bin packing (Best Short Side Fit)
    NestingEngine.kt         Multi-sheet nesting, utilization, cut length
  export/
    Export.kt                Cut-list CSV, nest-layout SVG, share sheet
  ui/
    NestingViewModel.kt      Parts + settings state, runs the nesting
    PartsScreen.kt           Parts library
    SetupScreen.kt           Machine / sheet / nesting / cutting setup
    ResultsScreen.kt         Utilization, sheet drawing, export
    UiUtils.kt               Number / time formatting
    components/Fields.kt     Decimal and integer input fields
    theme/                   Dark workshop Material 3 theme
```

Package: `com.mrafshun20.lasernesting`. Min SDK 26, target SDK 34.
Kotlin + Jetpack Compose + Material 3 + Room.

## Current limits

- Rectangles and circles only. Circles are packed by their bounding
  square (their true area is used for utilization). True-shape nesting of
  arbitrary outlines is not in this version.
- No DXF/DWG import yet — parts are entered by size in the app.
- No common-line cutting (shared cut edges between neighbouring parts)
  and no remnant library yet.
- Cutting time is a rough estimate only (cut length / speed); it does not
  model pierce time, rapid moves or acceleration.

## Roadmap (IGEMS-style)

- DXF import with true-shape nesting.
- Common-line cutting and part priorities.
- Remnant library: save leftover sheet pieces and nest on them first.
- Material database with cutting parameters per material and thickness.
- Cut-path simulation and ordering before export.
