package com.mrafshun20.lasernesting.desktop

import com.mrafshun20.lasernesting.data.MachinePreset
import com.mrafshun20.lasernesting.data.Material
import com.mrafshun20.lasernesting.data.PartOutlineJson
import com.mrafshun20.lasernesting.data.PartShape
import com.mrafshun20.lasernesting.data.SheetPreset
import com.mrafshun20.lasernesting.dxf.DxfParser
import com.mrafshun20.lasernesting.dxf.toPartOutline
import com.mrafshun20.lasernesting.nesting.NestResult
import com.mrafshun20.lasernesting.nesting.NestingEngine
import com.mrafshun20.lasernesting.nesting.PartInput
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.File

/**
 * Desktop settings. Mirrors the Android SettingsEntity (same defaults).
 */
data class DesktopSettings(
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
    /** "system" (follow the OS), "en", or "fa". */
    val language: String = "system"
)

/** Bilingual UI strings (English / Persian) for the desktop app. */
object DesktopStrings {
    private val en = mapOf(
        "app_title" to "Laser Nesting",
        "app_subtitle" to "Bystronic sheet nesting",
        "tab_parts" to "Parts",
        "tab_setup" to "Setup",
        "tab_nest" to "Nest",
        "import_dxf" to "Import DXF",
        "no_parts" to "No parts yet — import a DXF file.",
        "save_project" to "Save project",
        "open_project" to "Open project",
        "language" to "Language",
        "lang_system" to "System default",
        "lang_en" to "English",
        "lang_fa" to "Persian",
        "machine" to "Machine",
        "sheet" to "Sheet",
        "nesting" to "Nesting",
        "cutting" to "Cutting",
        "sheet_width" to "Sheet width (mm)",
        "sheet_length" to "Sheet length (mm)",
        "sheet_thickness" to "Sheet thickness (mm)",
        "sheet_count" to "Number of sheets",
        "kerf" to "Kerf (mm)",
        "gap" to "Part-to-part gap (mm)",
        "margin" to "Sheet edge margin (mm)",
        "allow_rotation" to "Allow rotation",
        "cutting_speed" to "Cutting speed (mm/min)",
        "run_nest" to "Auto-nest parts",
        "nesting_running" to "Nesting…",
        "utilization" to "Material utilization",
        "sheets" to "Sheets",
        "cut_length" to "Cut length",
        "est_time" to "Est. time",
        "placed" to "Placed",
        "unplaced" to "Unplaced parts",
        "reason_invalid" to "Invalid size or quantity",
        "reason_no_area" to "No usable area — reduce the edge margin",
        "reason_no_space" to "No free space left on the available sheets",
        "reason_no_fit" to "Does not fit the sheet",
        "remnant" to "Remnant",
        "zoom_in" to "Zoom in",
        "zoom_out" to "Zoom out",
        "zoom_fit" to "Fit",
        "zoom_hint" to "Scroll to zoom • Drag to pan",
        "export_csv" to "Cut list (CSV)",
        "export_svg" to "Layout (SVG)",
        "name" to "Name",
        "width" to "Width (mm)",
        "height" to "Height (mm)",
        "diameter" to "Diameter (mm)",
        "quantity" to "Quantity",
        "thickness" to "Thickness (mm)",
        "priority" to "Priority",
        "save" to "Save",
        "cancel" to "Cancel",
        "delete" to "Delete",
        "edit" to "Edit",
        "edit_part" to "Edit part",
        "rectangle" to "Rectangle",
        "circle" to "Circle",
        "dxf_outline" to "DXF outline",
        "holes" to "holes",
        "dxf_imported" to "Imported %d part(s) from %s.",
        "dxf_no_parts" to "No closed parts were found in this DXF file.",
        "dxf_read_error" to "Could not read this DXF file.",
        "project_saved" to "Project saved.",
        "project_loaded" to "Project loaded.",
        "project_error" to "Could not open that project file."
    )
    private val fa = mapOf(
        "app_title" to "چیدمان لیزر",
        "app_subtitle" to "چیدمان ورق بایسترونیک",
        "tab_parts" to "قطعات",
        "tab_setup" to "تنظیمات",
        "tab_nest" to "چیدمان",
        "import_dxf" to "وارد کردن DXF",
        "no_parts" to "هنوز قطعه‌ای نیست — یک فایل DXF وارد کنید.",
        "save_project" to "ذخیره پروژه",
        "open_project" to "باز کردن پروژه",
        "language" to "زبان",
        "lang_system" to "پیش‌فرض سیستم",
        "lang_en" to "انگلیسی",
        "lang_fa" to "فارسی",
        "machine" to "دستگاه",
        "sheet" to "ورق",
        "nesting" to "چیدمان",
        "cutting" to "برش",
        "sheet_width" to "عرض ورق (میلی‌متر)",
        "sheet_length" to "طول ورق (میلی‌متر)",
        "sheet_thickness" to "ضخامت ورق (میلی‌متر)",
        "sheet_count" to "تعداد ورق",
        "kerf" to "کرف (میلی‌متر)",
        "gap" to "فاصله بین قطعات (میلی‌متر)",
        "margin" to "حاشیه لبه ورق (میلی‌متر)",
        "allow_rotation" to "اجازه چرخش",
        "cutting_speed" to "سرعت برش (میلی‌متر/دقیقه)",
        "run_nest" to "چیدمان خودکار قطعات",
        "nesting_running" to "در حال چیدمان…",
        "utilization" to "بهره‌وری مواد",
        "sheets" to "ورق‌ها",
        "cut_length" to "طول برش",
        "est_time" to "زمان تقریبی",
        "placed" to "چیده شده",
        "unplaced" to "قطعات چیده‌نشده",
        "reason_invalid" to "اندازه یا تعداد نامعتبر است",
        "reason_no_area" to "ناحیه قابل‌استفاده‌ای نیست — حاشیه لبه را کم کنید",
        "reason_no_space" to "در ورق‌های موجود جای خالی نمانده است",
        "reason_no_fit" to "در ورق جا نمی‌شود",
        "remnant" to "باقیمانده",
        "zoom_in" to "بزرگ‌نمایی",
        "zoom_out" to "کوچک‌نمایی",
        "zoom_fit" to "نمایش کامل",
        "zoom_hint" to "برای بزرگنمایی اسکرول کنید • برای جابه‌جایی بکشید",
        "export_csv" to "لیست برش (CSV)",
        "export_svg" to "نقشه چیدمان (SVG)",
        "name" to "نام",
        "width" to "عرض (میلی‌متر)",
        "height" to "ارتفاع (میلی‌متر)",
        "diameter" to "قطر (میلی‌متر)",
        "quantity" to "تعداد",
        "thickness" to "ضخامت (میلی‌متر)",
        "priority" to "اولویت",
        "save" to "ذخیره",
        "cancel" to "لغو",
        "delete" to "حذف",
        "edit" to "ویرایش",
        "edit_part" to "ویرایش قطعه",
        "rectangle" to "مستطیل",
        "circle" to "دایره",
        "dxf_outline" to "خط DXF",
        "holes" to "سوراخ",
        "dxf_imported" to "%d قطعه از %s وارد شد.",
        "dxf_no_parts" to "در این فایل DXF هیچ قطعه بسته‌ای پیدا نشد.",
        "dxf_read_error" to "خواندن این فایل DXF ممکن نشد.",
        "project_saved" to "پروژه ذخیره شد.",
        "project_loaded" to "پروژه باز شد.",
        "project_error" to "باز کردن این فایل پروژه ممکن نشد."
    )

    fun isPersian(language: String): Boolean {
        return when (language) {
            "fa" -> true
            "en" -> false
            else -> java.util.Locale.getDefault().language == "fa"
        }
    }

    fun get(key: String, language: String): String {
        val table = if (isPersian(language)) fa else en
        return table[key] ?: en[key] ?: key
    }
}

/**
 * Desktop state: parts, settings, nesting result, messages.
 * Parts are held as core [PartInput] values (with their true DXF outline);
 * the project file persists them as JSON in the user's home directory.
 */
class DesktopState {
    private val _parts = MutableStateFlow<List<PartInput>>(emptyList())
    val parts: StateFlow<List<PartInput>> = _parts.asStateFlow()

    private val _settings = MutableStateFlow(DesktopSettings())
    val settings: StateFlow<DesktopSettings> = _settings.asStateFlow()

    private val _nestResult = MutableStateFlow<NestResult?>(null)
    val nestResult: StateFlow<NestResult?> = _nestResult.asStateFlow()

    private val _nestError = MutableStateFlow<String?>(null)
    val nestError: StateFlow<String?> = _nestError.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private var nextId = 1L

    fun t(key: String): String =
        DesktopStrings.get(key, _settings.value.language)

    fun updateSettings(transform: (DesktopSettings) -> DesktopSettings) {
        _settings.update(transform)
        _nestResult.value = null
    }

    fun upsertPart(part: PartInput) {
        _parts.update { list ->
            val existing = list.indexOfFirst { it.id == part.id && part.id != 0L }
            if (existing >= 0) {
                list.toMutableList().also { it[existing] = part }
            } else {
                val withId = if (part.id == 0L) part.copy(id = nextId++) else part
                if (part.id != 0L) nextId = maxOf(nextId, part.id + 1)
                list + withId
            }
        }
        _nestResult.value = null
    }

    fun deletePart(part: PartInput) {
        _parts.update { it.filterNot { p -> p.id == part.id } }
        _nestResult.value = null
    }

    /** Imports parts from a DXF file on disk. */
    fun importDxf(file: File) {
        val result = runCatching { DxfParser.parseFile(file) }.getOrElse {
            _message.value = t("dxf_read_error")
            return
        }
        if (result.parts.isEmpty()) {
            _message.value = result.warnings.firstOrNull() ?: t("dxf_no_parts")
            return
        }
        val inputs = result.parts.map { dxfPart ->
            PartInput(
                id = nextId++,
                name = dxfPart.name,
                shape = PartShape.DXF,
                widthMm = dxfPart.widthMm,
                heightMm = dxfPart.heightMm,
                quantity = 1,
                material = Material.MILD_STEEL,
                thicknessMm = 3.0,
                allowedRotations = setOf(0, 90, 180, 270),
                priority = 0,
                outline = dxfPart.toPartOutline()
            )
        }
        _parts.update { it + inputs }
        _nestResult.value = null
        _message.value = if (result.warnings.isEmpty()) {
            t("dxf_imported").format(inputs.size, file.name)
        } else {
            t("dxf_imported").format(inputs.size, file.name) + " " + result.warnings.first()
        }
    }

    fun clearMessage() {
        _message.value = null
    }

    fun runNesting() {
        val s = _settings.value
        val currentParts = _parts.value
        if (currentParts.isEmpty()) {
            _nestError.value = "Add at least one part first."
            _nestResult.value = null
            return
        }
        if (s.sheetWidthMm <= 0 || s.sheetLengthMm <= 0) {
            _nestError.value = "Sheet size must be greater than zero."
            _nestResult.value = null
            return
        }
        if (s.sheetWidthMm > s.machinePreset.maxWidthMm ||
            s.sheetLengthMm > s.machinePreset.maxLengthMm
        ) {
            _nestError.value = "Sheet is larger than the " +
                "${s.machinePreset.displayName} working area."
            _nestResult.value = null
            return
        }
        _nestError.value = null
        _nestResult.value = NestingEngine.nest(
            parts = currentParts,
            sheetWidthMm = s.sheetWidthMm,
            sheetLengthMm = s.sheetLengthMm,
            sheetCount = s.sheetCount,
            kerfMm = s.kerfMm,
            gapMm = s.gapMm,
            marginMm = s.marginMm,
            allowRotationGlobal = s.allowRotation,
            cuttingSpeedMmPerMin = s.cuttingSpeedMmPerMin
        )
    }

    // ---- Project file (JSON) ----

    fun defaultProjectFile(): File {
        val home = System.getProperty("user.home") ?: "."
        return File(home, "LaserNesting-project.json")
    }

    fun saveProject(file: File): Boolean {
        return runCatching {
            val s = _settings.value
            val partsJson = JsonArray(
                _parts.value.map { part ->
                    Json.obj(
                        "id" to Json.num(part.id),
                        "name" to Json.str(part.name),
                        "shape" to Json.str(part.shape.name),
                        "widthMm" to Json.num(part.widthMm),
                        "heightMm" to Json.num(part.heightMm),
                        "quantity" to Json.num(part.quantity),
                        "material" to Json.str(part.material.name),
                        "thicknessMm" to Json.num(part.thicknessMm),
                        "allowRotation" to Json.bool(
                            part.allowedRotations != setOf(0)
                        ),
                        "allowedRotations" to Json.str(
                            part.allowedRotations.sorted().joinToString(",")
                        ),
                        "priority" to Json.num(part.priority),
                        "outlineJson" to Json.str(
                            PartOutlineJson.encode(part.outline) ?: ""
                        )
                    )
                }
            )
            val root = Json.obj(
                "version" to Json.num(2),
                "settings" to Json.obj(
                    "machinePreset" to Json.str(s.machinePreset.name),
                    "sheetPreset" to Json.str(s.sheetPreset.name),
                    "sheetWidthMm" to Json.num(s.sheetWidthMm),
                    "sheetLengthMm" to Json.num(s.sheetLengthMm),
                    "sheetMaterial" to Json.str(s.sheetMaterial.name),
                    "sheetThicknessMm" to Json.num(s.sheetThicknessMm),
                    "sheetCount" to Json.num(s.sheetCount),
                    "kerfMm" to Json.num(s.kerfMm),
                    "gapMm" to Json.num(s.gapMm),
                    "marginMm" to Json.num(s.marginMm),
                    "allowRotation" to Json.bool(s.allowRotation),
                    "cuttingSpeedMmPerMin" to Json.num(s.cuttingSpeedMmPerMin),
                    "language" to Json.str(s.language)
                ),
                "parts" to partsJson
            )
            file.writeText(Json.stringify(root))
            _message.value = t("project_saved")
            true
        }.getOrElse {
            _message.value = t("project_error")
            false
        }
    }

    fun loadProject(file: File): Boolean {
        return runCatching {
            val root = Json.parse(file.readText()) as? JsonObject
                ?: return@runCatching false
            val settingsObj = root.obj("settings")
            if (settingsObj != null) {
                _settings.value = DesktopSettings(
                    machinePreset = settingsObj.string("machinePreset")
                        ?.let { runCatching { MachinePreset.valueOf(it) }.getOrNull() }
                        ?: MachinePreset.BYSTAR_FIBER_3015,
                    sheetPreset = settingsObj.string("sheetPreset")
                        ?.let { runCatching { SheetPreset.valueOf(it) }.getOrNull() }
                        ?: SheetPreset.SHEET_2000x1000,
                    sheetWidthMm = settingsObj.double("sheetWidthMm") ?: 2000.0,
                    sheetLengthMm = settingsObj.double("sheetLengthMm") ?: 1000.0,
                    sheetMaterial = settingsObj.string("sheetMaterial")
                        ?.let { runCatching { Material.valueOf(it) }.getOrNull() }
                        ?: Material.MILD_STEEL,
                    sheetThicknessMm = settingsObj.double("sheetThicknessMm") ?: 3.0,
                    sheetCount = settingsObj.int("sheetCount") ?: 1,
                    kerfMm = settingsObj.double("kerfMm") ?: 0.2,
                    gapMm = settingsObj.double("gapMm") ?: 3.0,
                    marginMm = settingsObj.double("marginMm") ?: 10.0,
                    allowRotation = settingsObj.bool("allowRotation") ?: true,
                    cuttingSpeedMmPerMin = settingsObj.double("cuttingSpeedMmPerMin")
                        ?: 6000.0,
                    language = settingsObj.string("language") ?: "system"
                )
            }
            val loaded = mutableListOf<PartInput>()
            var maxId = 0L
            root.array("parts")?.items?.forEach { item ->
                val obj = item as? JsonObject ?: return@forEach
                val id = obj.double("id")?.toLong() ?: 0L
                maxId = maxOf(maxId, id)
                val shape = obj.string("shape")
                    ?.let { runCatching { PartShape.valueOf(it) }.getOrNull() }
                    ?: PartShape.RECTANGLE
                val material = obj.string("material")
                    ?.let { runCatching { Material.valueOf(it) }.getOrNull() }
                    ?: Material.MILD_STEEL
                val rotations = obj.string("allowedRotations")
                    ?.split(',')?.mapNotNull { it.trim().toIntOrNull() }
                    ?.filter { it in setOf(0, 90, 180, 270) }?.toSet()
                    ?.ifEmpty { setOf(0) } ?: setOf(0, 90, 180, 270)
                val outlineJson = obj.string("outlineJson")
                loaded.add(
                    PartInput(
                        id = id,
                        name = obj.string("name") ?: "Part",
                        shape = shape,
                        widthMm = obj.double("widthMm") ?: 0.0,
                        heightMm = obj.double("heightMm") ?: 0.0,
                        quantity = obj.int("quantity") ?: 1,
                        material = material,
                        thicknessMm = obj.double("thicknessMm") ?: 3.0,
                        allowedRotations = rotations,
                        priority = obj.int("priority") ?: 0,
                        outline = PartOutlineJson.decode(outlineJson)
                    )
                )
            }
            nextId = maxId + 1
            _parts.value = loaded
            _nestResult.value = null
            _message.value = t("project_loaded")
            true
        }.getOrElse {
            _message.value = t("project_error")
            false
        }
    }
}

/** Formats a millimetre value without a trailing ".0". */
fun formatMm(value: Double): String {
    return if (value == value.toLong().toDouble()) {
        value.toLong().toString()
    } else {
        "%.1f".format(value)
    }
}

/** Formats a duration in minutes as "12 min" or "1 h 25 min". */
fun formatMinutes(minutes: Double): String {
    if (minutes <= 0) return "0 min"
    val totalSeconds = (minutes * 60).toInt()
    val hours = totalSeconds / 3600
    val mins = (totalSeconds % 3600) / 60
    return if (hours > 0) "$hours h $mins min" else "$mins min"
}
