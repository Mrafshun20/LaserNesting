package com.mrafshun20.lasernesting.desktop

import com.mrafshun20.lasernesting.data.MachinePreset
import com.mrafshun20.lasernesting.data.Material
import com.mrafshun20.lasernesting.data.PartOutlineJson
import com.mrafshun20.lasernesting.data.PartShape
import com.mrafshun20.lasernesting.data.SheetPreset
import com.mrafshun20.lasernesting.dxf.DxfParser
import com.mrafshun20.lasernesting.dxf.toPartOutline
import com.mrafshun20.lasernesting.nesting.NestOptions
import com.mrafshun20.lasernesting.nesting.NestQuality
import com.mrafshun20.lasernesting.nesting.NestResult
import com.mrafshun20.lasernesting.nesting.NestingEngine
import com.mrafshun20.lasernesting.nesting.PartInput
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.File
import java.util.Locale
import java.util.Properties
import kotlin.concurrent.thread

/**
 * Desktop settings. Persian is the default language.
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
    /** Take the cutting speed from the material table instead of [cuttingSpeedMmPerMin]. */
    val autoSpeed: Boolean = true,
    /** Pierce time in seconds; 0 = from the material table. */
    val pierceSeconds: Double = 0.0,
    /** Sheet price per kg; 0 = no cost figure. */
    val pricePerKg: Double = 0.0,
    /** FAST, BALANCED or HIGH. */
    val quality: String = "BALANCED",
    val trueShape: Boolean = true,
    /** "fa" (default), "en", or "system". */
    val language: String = "fa",
    val darkTheme: Boolean = true
)

/** Bilingual UI strings (Persian default / English) for the desktop app. */
object DesktopStrings {
    private val en = mapOf(
        "app_title" to "ibebels",
        "app_subtitle" to "Bystronic sheet nesting",
        "nav_parts" to "Parts",
        "nav_setup" to "Setup",
        "nav_nest" to "Nesting",
        "new_project" to "New project",
        "open_project" to "Open",
        "save_project" to "Save",
        "import_dxf" to "Import DXF",
        "add_part" to "Add simple part",
        "empty_title" to "No parts yet",
        "empty_body" to "Import your customers' DXF files. Every closed outline becomes a part, together with its holes and slots.",
        "stat_types" to "Part types",
        "stat_pieces" to "Pieces",
        "stat_area" to "Total area",
        "holes" to "holes",
        "cutouts" to "cutouts",
        "priority_short" to "Priority",
        "rot_free" to "Rotation free",
        "rot_locked" to "Rotation locked",
        "qty" to "Qty",
        "edit" to "Edit",
        "duplicate" to "Duplicate",
        "delete" to "Delete",
        "copy_suffix" to "(copy)",
        "edit_part" to "Edit part",
        "new_part" to "New part",
        "shape" to "Shape",
        "name" to "Name",
        "width" to "Width (mm)",
        "height" to "Height (mm)",
        "diameter" to "Diameter (mm)",
        "quantity" to "Quantity",
        "material" to "Material",
        "thickness" to "Thickness (mm)",
        "priority" to "Priority (higher nests first)",
        "allow_rotation" to "Allow rotation",
        "rotations" to "Allowed angles",
        "save" to "Save",
        "cancel" to "Cancel",
        "rectangle" to "Rectangle",
        "circle" to "Circle",
        "dxf_outline" to "DXF outline",
        "sec_machine" to "Machine",
        "sec_sheet" to "Sheet",
        "sec_nesting" to "Nesting parameters",
        "sec_cutting" to "Cutting and cost",
        "sec_app" to "Language and appearance",
        "bed" to "Max working area",
        "sheet_width" to "Sheet width (mm)",
        "sheet_length" to "Sheet length (mm)",
        "sheet_thickness" to "Sheet thickness (mm)",
        "sheet_count" to "Sheets available",
        "kerf" to "Kerf (mm)",
        "gap" to "Part-to-part gap (mm)",
        "margin" to "Sheet edge margin (mm)",
        "true_shape" to "True-shape nesting (parts fill gaps, slots and holes of other parts)",
        "quality" to "Nesting quality",
        "q_fast" to "Fast",
        "q_balanced" to "Balanced",
        "q_high" to "Best yield",
        "auto_speed" to "Cutting speed from the material table",
        "cutting_speed" to "Cutting speed (mm/min)",
        "speed_hint" to "Table values are typical for a ~6 kW fiber laser — set your own speed for exact times.",
        "pierce_time" to "Pierce time (s, 0 = automatic)",
        "price_kg" to "Sheet price per kg (0 = off)",
        "language" to "Language",
        "lang_fa" to "فارسی",
        "lang_en" to "English",
        "theme" to "Theme",
        "theme_dark" to "Dark",
        "theme_light" to "Light",
        "run_nest" to "Start nesting",
        "nesting_running" to "Nesting…",
        "nest_hint" to "Press “Start nesting” to arrange the parts on the sheet.",
        "kpi_util" to "Material yield",
        "kpi_sheets" to "Sheets used",
        "kpi_parts" to "Parts placed",
        "kpi_cut" to "Cut length",
        "kpi_time" to "Cutting time",
        "kpi_pierces" to "Pierces",
        "kpi_rapid" to "Rapid travel",
        "kpi_weight" to "Parts weight",
        "kpi_scrap" to "Scrap",
        "kpi_cost" to "Material cost",
        "waste" to "Waste",
        "sheet_tab" to "Sheet",
        "remnant" to "Remnant",
        "show_path" to "Cut path",
        "show_labels" to "Labels",
        "zoom_in" to "Zoom in",
        "zoom_out" to "Zoom out",
        "zoom_fit" to "Fit",
        "zoom_hint" to "Scroll to zoom • drag to pan",
        "export" to "Export",
        "export_dxf" to "DXF for Bystronic (this sheet)",
        "export_dxf_all" to "DXF of all sheets…",
        "export_report" to "Job report (HTML)",
        "export_csv" to "Cut list (CSV)",
        "export_svg" to "Layout (SVG)",
        "unplaced" to "Parts not placed",
        "reason_invalid" to "Invalid size or quantity",
        "reason_no_area" to "No usable area — reduce the edge margin",
        "reason_no_space" to "No free space left on the available sheets",
        "reason_no_fit" to "Does not fit the sheet",
        "dxf_imported" to "%d part(s) imported from %s.",
        "dxf_no_parts" to "No closed parts were found in this DXF file.",
        "dxf_read_error" to "Could not read this DXF file.",
        "project_saved" to "Project saved.",
        "project_loaded" to "Project loaded.",
        "project_error" to "Could not open that project file.",
        "project_new" to "New project started.",
        "export_done" to "Saved: %s",
        "export_error" to "Could not write the file.",
        "nest_need_parts" to "Add at least one part first.",
        "nest_bad_sheet" to "Sheet size must be greater than zero.",
        "nest_too_big" to "The sheet is larger than the %s working area.",
        "nest_failed" to "Nesting failed.",
        "mm" to "mm",
        "min_unit" to "min",
        "h_unit" to "h",
        "m_unit" to "m",
        "kg_unit" to "kg",
        "strategy" to "Strategy"
    )

    private val fa = mapOf(
        "app_title" to "ibebels",
        "app_subtitle" to "چیدمان ورق برای بایسترونیک",
        "nav_parts" to "قطعات",
        "nav_setup" to "تنظیمات",
        "nav_nest" to "چیدمان",
        "new_project" to "پروژه جدید",
        "open_project" to "باز کردن",
        "save_project" to "ذخیره",
        "import_dxf" to "وارد کردن DXF",
        "add_part" to "افزودن قطعه ساده",
        "empty_title" to "هنوز قطعه‌ای اضافه نشده",
        "empty_body" to "فایل‌های DXF مشتری را وارد کنید. هر خط بسته یک قطعه می‌شود، همراه با سوراخ‌ها و شیارهایش.",
        "stat_types" to "نوع قطعه",
        "stat_pieces" to "تعداد کل",
        "stat_area" to "مساحت کل",
        "holes" to "سوراخ",
        "cutouts" to "برش داخلی",
        "priority_short" to "اولویت",
        "rot_free" to "چرخش آزاد",
        "rot_locked" to "چرخش محدود",
        "qty" to "تعداد",
        "edit" to "ویرایش",
        "duplicate" to "کپی",
        "delete" to "حذف",
        "copy_suffix" to "(کپی)",
        "edit_part" to "ویرایش قطعه",
        "new_part" to "قطعه جدید",
        "shape" to "شکل",
        "name" to "نام",
        "width" to "عرض (میلی‌متر)",
        "height" to "ارتفاع (میلی‌متر)",
        "diameter" to "قطر (میلی‌متر)",
        "quantity" to "تعداد",
        "material" to "جنس",
        "thickness" to "ضخامت (میلی‌متر)",
        "priority" to "اولویت (بالاتر زودتر چیده می‌شود)",
        "allow_rotation" to "اجازه چرخش",
        "rotations" to "زاویه‌های مجاز",
        "save" to "ذخیره",
        "cancel" to "انصراف",
        "rectangle" to "مستطیل",
        "circle" to "دایره",
        "dxf_outline" to "خط DXF",
        "sec_machine" to "دستگاه",
        "sec_sheet" to "ورق",
        "sec_nesting" to "پارامترهای چیدمان",
        "sec_cutting" to "برش و هزینه",
        "sec_app" to "زبان و ظاهر",
        "bed" to "حداکثر ناحیه کاری",
        "sheet_width" to "عرض ورق (میلی‌متر)",
        "sheet_length" to "طول ورق (میلی‌متر)",
        "sheet_thickness" to "ضخامت ورق (میلی‌متر)",
        "sheet_count" to "تعداد ورق موجود",
        "kerf" to "کرف (میلی‌متر)",
        "gap" to "فاصله بین قطعات (میلی‌متر)",
        "margin" to "حاشیه لبه ورق (میلی‌متر)",
        "true_shape" to "چیدمان شکل‌محور (قطعات در فضای خالی، شیار و سوراخ قطعات دیگر جا می‌گیرند)",
        "quality" to "کیفیت چیدمان",
        "q_fast" to "سریع",
        "q_balanced" to "متعادل",
        "q_high" to "بیشترین بازده",
        "auto_speed" to "سرعت برش از جدول مواد",
        "cutting_speed" to "سرعت برش (میلی‌متر/دقیقه)",
        "speed_hint" to "مقادیر جدول برای فایبر لیزر حدود ۶ کیلووات است؛ برای زمان دقیق، سرعت دستگاه خودتان را وارد کنید.",
        "pierce_time" to "زمان پیرس (ثانیه، ۰ = خودکار)",
        "price_kg" to "قیمت هر کیلو ورق (۰ = غیرفعال)",
        "language" to "زبان",
        "lang_fa" to "فارسی",
        "lang_en" to "English",
        "theme" to "پوسته",
        "theme_dark" to "تیره",
        "theme_light" to "روشن",
        "run_nest" to "شروع چیدمان",
        "nesting_running" to "در حال چیدمان…",
        "nest_hint" to "برای چیدن قطعات روی ورق، «شروع چیدمان» را بزنید.",
        "kpi_util" to "بازده مواد",
        "kpi_sheets" to "ورق مصرفی",
        "kpi_parts" to "قطعات چیده‌شده",
        "kpi_cut" to "طول برش",
        "kpi_time" to "زمان برش",
        "kpi_pierces" to "تعداد پیرس",
        "kpi_rapid" to "حرکت سریع",
        "kpi_weight" to "وزن قطعات",
        "kpi_scrap" to "ضایعات",
        "kpi_cost" to "هزینه مواد",
        "waste" to "ضایعات",
        "sheet_tab" to "ورق",
        "remnant" to "باقیمانده",
        "show_path" to "مسیر برش",
        "show_labels" to "برچسب‌ها",
        "zoom_in" to "بزرگ‌نمایی",
        "zoom_out" to "کوچک‌نمایی",
        "zoom_fit" to "نمایش کامل",
        "zoom_hint" to "اسکرول برای زوم • کشیدن برای جابه‌جایی",
        "export" to "خروجی",
        "export_dxf" to "DXF برای بایسترونیک (این ورق)",
        "export_dxf_all" to "DXF همه ورق‌ها…",
        "export_report" to "گزارش کار (HTML)",
        "export_csv" to "لیست برش (CSV)",
        "export_svg" to "نقشه چیدمان (SVG)",
        "unplaced" to "قطعات چیده‌نشده",
        "reason_invalid" to "اندازه یا تعداد نامعتبر است",
        "reason_no_area" to "ناحیه قابل‌استفاده‌ای نیست — حاشیه لبه را کم کنید",
        "reason_no_space" to "در ورق‌های موجود جای خالی نمانده است",
        "reason_no_fit" to "در ورق جا نمی‌شود",
        "dxf_imported" to "%d قطعه از %s وارد شد.",
        "dxf_no_parts" to "در این فایل DXF هیچ قطعه بسته‌ای پیدا نشد.",
        "dxf_read_error" to "خواندن این فایل DXF ممکن نشد.",
        "project_saved" to "پروژه ذخیره شد.",
        "project_loaded" to "پروژه باز شد.",
        "project_error" to "باز کردن این فایل پروژه ممکن نشد.",
        "project_new" to "پروژه جدید شروع شد.",
        "export_done" to "ذخیره شد: %s",
        "export_error" to "نوشتن فایل ممکن نشد.",
        "nest_need_parts" to "ابتدا حداقل یک قطعه اضافه کنید.",
        "nest_bad_sheet" to "اندازه ورق باید بزرگ‌تر از صفر باشد.",
        "nest_too_big" to "ورق از ناحیه کاری %s بزرگ‌تر است.",
        "nest_failed" to "چیدمان انجام نشد.",
        "mm" to "میلی‌متر",
        "min_unit" to "دقیقه",
        "h_unit" to "ساعت",
        "m_unit" to "متر",
        "kg_unit" to "کیلوگرم",
        "strategy" to "روش"
    )

    fun isPersian(language: String): Boolean {
        return when (language) {
            "fa" -> true
            "en" -> false
            else -> Locale.getDefault().language == "fa"
        }
    }

    fun get(key: String, language: String): String {
        val table = if (isPersian(language)) fa else en
        return table[key] ?: en[key] ?: key
    }
}

/** Turns Persian / Arabic digits and decimal separators into plain ASCII so "۱۲٫۵" parses. */
fun normalizeNumber(text: String): String {
    val sb = StringBuilder(text.length)
    for (ch in text.trim()) {
        sb.append(
            when (ch) {
                in '۰'..'۹' -> '0' + (ch - '۰')
                in '٠'..'٩' -> '0' + (ch - '٠')
                '٫', '٬', ',', '،' -> '.'
                else -> ch
            }
        )
    }
    return sb.toString()
}

/**
 * Desktop state: parts, settings, nesting result, messages.
 * Parts are held as core [PartInput] values (with their true DXF outline).
 * The session is autosaved to the user's home folder and restored on start,
 * so closing the window never loses work.
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

    private val _nesting = MutableStateFlow(false)
    val nesting: StateFlow<Boolean> = _nesting.asStateFlow()

    private var nextId = 1L

    private val appDir: File = File(System.getProperty("user.home") ?: ".", "LaserNesting")
    private val autosaveFile = File(appDir, "autosave.json")
    private val prefsFile = File(appDir, "preferences.properties")

    init {
        loadPrefs()
        runCatching { if (autosaveFile.exists()) readProject(autosaveFile, announce = false) }
    }

    fun t(key: String): String = DesktopStrings.get(key, _settings.value.language)

    fun isPersian(): Boolean = DesktopStrings.isPersian(_settings.value.language)

    fun updateSettings(transform: (DesktopSettings) -> DesktopSettings) {
        val before = _settings.value
        _settings.update(transform)
        val after = _settings.value
        if (before.language != after.language || before.darkTheme != after.darkTheme) savePrefs()
        _nestResult.value = null
        persist()
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
        persist()
    }

    fun duplicatePart(part: PartInput) {
        upsertPart(part.copy(id = 0L, name = part.name + " " + t("copy_suffix")))
    }

    fun setQuantity(part: PartInput, quantity: Int) {
        upsertPart(part.copy(quantity = quantity.coerceIn(1, 9999)))
    }

    fun deletePart(part: PartInput) {
        _parts.update { it.filterNot { p -> p.id == part.id } }
        _nestResult.value = null
        persist()
    }

    fun newProject() {
        _parts.value = emptyList()
        _nestResult.value = null
        _nestError.value = null
        nextId = 1L
        _message.value = t("project_new")
        persist()
    }

    /** Imports parts from one or more DXF files on disk. */
    fun importDxf(files: List<File>) {
        var total = 0
        var lastName = ""
        var lastWarning: String? = null
        var failed = false
        for (file in files) {
            val result = runCatching { DxfParser.parseFile(file) }.getOrNull()
            if (result == null) {
                failed = true
                continue
            }
            if (result.parts.isEmpty()) {
                lastWarning = result.warnings.firstOrNull() ?: t("dxf_no_parts")
                continue
            }
            val s = _settings.value
            val inputs = result.parts.map { dxfPart ->
                PartInput(
                    id = nextId++,
                    name = if (files.size > 1) "${file.nameWithoutExtension} · ${dxfPart.name}" else dxfPart.name,
                    shape = PartShape.DXF,
                    widthMm = dxfPart.widthMm,
                    heightMm = dxfPart.heightMm,
                    quantity = 1,
                    material = s.sheetMaterial,
                    thicknessMm = s.sheetThicknessMm,
                    allowedRotations = setOf(0, 90, 180, 270),
                    priority = 0,
                    outline = dxfPart.toPartOutline()
                )
            }
            _parts.update { it + inputs }
            total += inputs.size
            lastName = file.name
        }
        _nestResult.value = null
        _message.value = when {
            total > 0 -> t("dxf_imported").format(total, if (files.size > 1) "${files.size}" else lastName)
            failed -> t("dxf_read_error")
            else -> lastWarning ?: t("dxf_no_parts")
        }
        persist()
    }

    fun postMessage(text: String) {
        _message.value = text
    }

    fun clearMessage() {
        _message.value = null
    }

    fun runNesting() {
        if (_nesting.value) return
        val s = _settings.value
        val currentParts = _parts.value
        if (currentParts.isEmpty()) {
            _nestError.value = t("nest_need_parts")
            _nestResult.value = null
            return
        }
        if (s.sheetWidthMm <= 0 || s.sheetLengthMm <= 0) {
            _nestError.value = t("nest_bad_sheet")
            _nestResult.value = null
            return
        }
        if (s.sheetWidthMm > s.machinePreset.maxWidthMm ||
            s.sheetLengthMm > s.machinePreset.maxLengthMm
        ) {
            _nestError.value = t("nest_too_big").format(s.machinePreset.displayName)
            _nestResult.value = null
            return
        }
        _nestError.value = null
        _nesting.value = true
        val failedText = t("nest_failed")
        thread(isDaemon = true, name = "nesting") {
            try {
                val quality = runCatching { NestQuality.valueOf(s.quality) }
                    .getOrDefault(NestQuality.BALANCED)
                _nestResult.value = NestingEngine.nest(
                    parts = currentParts,
                    sheetWidthMm = s.sheetWidthMm,
                    sheetLengthMm = s.sheetLengthMm,
                    sheetCount = s.sheetCount,
                    kerfMm = s.kerfMm,
                    gapMm = s.gapMm,
                    marginMm = s.marginMm,
                    allowRotationGlobal = s.allowRotation,
                    cuttingSpeedMmPerMin = if (s.autoSpeed) 0.0 else s.cuttingSpeedMmPerMin,
                    options = NestOptions(
                        quality = quality,
                        trueShape = s.trueShape,
                        sheetMaterial = s.sheetMaterial,
                        sheetThicknessMm = s.sheetThicknessMm,
                        pricePerKg = s.pricePerKg,
                        pierceSeconds = s.pierceSeconds
                    )
                )
            } catch (e: Throwable) {
                _nestError.value = failedText + " " + (e.message ?: "")
                _nestResult.value = null
            } finally {
                _nesting.value = false
            }
        }
    }

    // ---- Formatting helpers (always plain Latin digits) ----

    fun minutesText(minutes: Double): String {
        if (minutes <= 0) return "0 " + t("min_unit")
        val totalSeconds = (minutes * 60).toInt()
        val hours = totalSeconds / 3600
        val mins = (totalSeconds % 3600) / 60
        val secs = totalSeconds % 60
        return when {
            hours > 0 -> "$hours ${t("h_unit")} $mins ${t("min_unit")}"
            mins > 0 -> "$mins ${t("min_unit")} $secs s"
            else -> "$secs s"
        }
    }

    // ---- Preferences (language, theme) ----

    private fun loadPrefs() {
        runCatching {
            if (!prefsFile.exists()) return
            val p = Properties()
            prefsFile.inputStream().use { p.load(it) }
            _settings.update { s ->
                s.copy(
                    language = p.getProperty("language") ?: s.language,
                    darkTheme = p.getProperty("dark")?.toBooleanStrictOrNull() ?: s.darkTheme
                )
            }
        }
    }

    private fun savePrefs() {
        runCatching {
            appDir.mkdirs()
            val p = Properties()
            p.setProperty("language", _settings.value.language)
            p.setProperty("dark", _settings.value.darkTheme.toString())
            prefsFile.outputStream().use { p.store(it, "Laser Nesting preferences") }
        }
    }

    // ---- Project file (JSON) ----

    private fun persist() {
        runCatching {
            appDir.mkdirs()
            autosaveFile.writeText(projectJson())
        }
    }

    fun saveProject(file: File): Boolean {
        return runCatching {
            file.writeText(projectJson())
            _message.value = t("project_saved")
            true
        }.getOrElse {
            _message.value = t("project_error")
            false
        }
    }

    fun loadProject(file: File): Boolean = readProject(file, announce = true)

    private fun projectJson(): String {
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
                    "allowRotation" to Json.bool(part.allowedRotations != setOf(0)),
                    "allowedRotations" to Json.str(part.allowedRotations.sorted().joinToString(",")),
                    "priority" to Json.num(part.priority),
                    "outlineJson" to Json.str(PartOutlineJson.encode(part.outline) ?: "")
                )
            }
        )
        val root = Json.obj(
            "version" to Json.num(3),
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
                "autoSpeed" to Json.bool(s.autoSpeed),
                "pierceSeconds" to Json.num(s.pierceSeconds),
                "pricePerKg" to Json.num(s.pricePerKg),
                "quality" to Json.str(s.quality),
                "trueShape" to Json.bool(s.trueShape),
                "language" to Json.str(s.language),
                "darkTheme" to Json.bool(s.darkTheme)
            ),
            "parts" to partsJson
        )
        return Json.stringify(root)
    }

    private fun readProject(file: File, announce: Boolean): Boolean {
        return runCatching {
            val root = Json.parse(file.readText()) as? JsonObject
                ?: return@runCatching false
            val keepLanguage = _settings.value.language
            val keepTheme = _settings.value.darkTheme
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
                    cuttingSpeedMmPerMin = settingsObj.double("cuttingSpeedMmPerMin") ?: 6000.0,
                    autoSpeed = settingsObj.bool("autoSpeed") ?: true,
                    pierceSeconds = settingsObj.double("pierceSeconds") ?: 0.0,
                    pricePerKg = settingsObj.double("pricePerKg") ?: 0.0,
                    quality = settingsObj.string("quality") ?: "BALANCED",
                    trueShape = settingsObj.bool("trueShape") ?: true,
                    // Language and theme are per-user preferences, not part of a project.
                    language = keepLanguage,
                    darkTheme = keepTheme
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
            if (announce) {
                _message.value = t("project_loaded")
                persist()
            }
            true
        }.getOrElse {
            if (announce) _message.value = t("project_error")
            false
        }
    }
}

/** Formats a millimetre value without a trailing ".0" (plain Latin digits). */
fun formatMm(value: Double): String {
    return if (value == value.toLong().toDouble()) {
        value.toLong().toString()
    } else {
        String.format(Locale.ROOT, "%.1f", value)
    }
}

/** Fixed-decimals number with plain Latin digits. */
fun fmt(value: Double, decimals: Int = 1): String =
    String.format(Locale.ROOT, "%.${decimals}f", value)
