package com.mrafshun20.lasernesting.ui

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mrafshun20.lasernesting.R
import com.mrafshun20.lasernesting.data.AppDatabase
import com.mrafshun20.lasernesting.data.Material
import com.mrafshun20.lasernesting.data.NestingRepository
import com.mrafshun20.lasernesting.data.PartEntity
import com.mrafshun20.lasernesting.data.PartOutlineJson
import com.mrafshun20.lasernesting.data.PartShape
import com.mrafshun20.lasernesting.data.SettingsEntity
import com.mrafshun20.lasernesting.dxf.DxfParser
import com.mrafshun20.lasernesting.dxf.toPartOutline
import com.mrafshun20.lasernesting.nesting.NestOptions
import com.mrafshun20.lasernesting.nesting.NestResult
import com.mrafshun20.lasernesting.nesting.NestingEngine
import com.mrafshun20.lasernesting.nesting.PartInput
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class NestingViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = NestingRepository(AppDatabase.getInstance(application))

    val parts: StateFlow<List<PartEntity>> = repository.observeParts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val settings: StateFlow<SettingsEntity> = repository.observeSettings()
        .map { it ?: SettingsEntity() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsEntity())

    var nestResult by mutableStateOf<NestResult?>(null)
        private set

    var nestError by mutableStateOf<String?>(null)
        private set

    var isNesting by mutableStateOf(false)
        private set

    init {
        // Persian is the default language. On every launch the stored
        // language is applied as the per-app locale, so the app always
        // opens in the picked language — a fresh install is Persian even
        // on an English device. Installs that stored the old "system"
        // default are migrated to Persian once.
        viewModelScope.launch {
            val current = withContext(Dispatchers.IO) { repository.getSettings() }
            val code = if (current.language == "system") "fa" else current.language
            if (current.language == "system") {
                withContext(Dispatchers.IO) {
                    repository.updateSettings(current.copy(language = "fa"))
                }
            }
            applyAppLocale(code)
        }
    }

    /** Applies a language code ("fa" or "en") as the per-app locale. */
    private fun applyAppLocale(code: String) {
        val locales = when (code) {
            "en" -> LocaleListCompat.forLanguageTags("en")
            else -> LocaleListCompat.forLanguageTags("fa")
        }
        AppCompatDelegate.setApplicationLocales(locales)
    }

    /**
     * Clears the cached nest result (and error) so the Results screen
     * re-nests with fresh inputs instead of showing a stale layout.
     */
    fun clearResult() {
        nestResult = null
        nestError = null
    }

    /** Result of the last DXF import: how many parts landed, plus warnings. */
    var dxfImportMessage by mutableStateOf<String?>(null)
        private set

    fun upsertPart(part: PartEntity) {
        clearResult()
        viewModelScope.launch(Dispatchers.IO) { repository.upsertPart(part) }
    }

    /** Changes a part's quantity right from its list row (minimum 1). */
    fun setPartQuantity(part: PartEntity, quantity: Int) {
        val clamped = quantity.coerceAtLeast(1)
        if (clamped == part.quantity) return
        upsertPart(part.copy(quantity = clamped))
    }

    /** Duplicates a part under a new id, with a localised "(copy)" name. */
    fun duplicatePart(part: PartEntity) {
        val copyName = getApplication<Application>().getString(
            R.string.part_copy_name, part.name
        )
        upsertPart(part.copy(id = 0, name = copyName))
    }

    fun deletePart(part: PartEntity) {
        clearResult()
        viewModelScope.launch(Dispatchers.IO) { repository.deletePart(part) }
    }

    fun updateSettings(
        invalidateNest: Boolean = true,
        transform: (SettingsEntity) -> SettingsEntity
    ) {
        if (invalidateNest) clearResult()
        viewModelScope.launch(Dispatchers.IO) {
            val current = repository.getSettings()
            repository.updateSettings(transform(current))
        }
    }

    /**
     * Applies the app language. Only "fa" (Persian, the default) and
     * "en" (English) exist — anything else falls back to Persian.
     * Persisted in settings and applied via AppCompat's per-app locale,
     * which survives restarts.
     */
    fun setLanguage(language: String) {
        val code = if (language == "en") "en" else "fa"
        updateSettings(invalidateNest = false) { it.copy(language = code) }
        applyAppLocale(code)
    }

    /**
     * Imports parts from DXF text. Each closed outline in the file becomes
     * one part (name, bounding size, true outline + holes). The caller
     * supplies the file name for display. Parts land in the library with
     * quantity 1 — the user sets the wanted quantity per part afterwards.
     * On failure [dxfImportMessage] explains it; on success it reports
     * how many parts were imported (plus any parser warnings).
     */
    fun importDxf(dxfText: String, fileName: String) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { DxfParser.parse(dxfText) }.getOrNull()
            }
            val app = getApplication<Application>()
            if (result == null) {
                dxfImportMessage = app.getString(R.string.dxf_read_error)
                return@launch
            }
            if (result.parts.isEmpty()) {
                val warning = result.warnings.firstOrNull()
                dxfImportMessage = warning
                    ?: app.getString(R.string.dxf_no_parts)
                return@launch
            }
            val entities = result.parts.map { dxfPart ->
                PartEntity(
                    name = dxfPart.name,
                    shape = PartShape.DXF,
                    widthMm = dxfPart.widthMm,
                    heightMm = dxfPart.heightMm,
                    quantity = 1,
                    material = Material.MILD_STEEL,
                    thicknessMm = 3.0,
                    allowRotation = true,
                    allowedRotationsCsv = "0,90,180,270",
                    priority = 0,
                    outlineJson = PartOutlineJson.encode(dxfPart.toPartOutline()),
                    sourceFile = fileName
                )
            }
            val imported = withContext(Dispatchers.IO) {
                repository.insertParts(entities).size
            }
            clearResult()
            dxfImportMessage = if (result.warnings.isEmpty()) {
                app.getString(R.string.dxf_imported, imported, fileName)
            } else {
                app.getString(R.string.dxf_imported, imported, fileName) +
                    " ${result.warnings.first()}"
            }
        }
    }

    fun clearDxfImportMessage() {
        dxfImportMessage = null
    }

    fun runNesting() {
        viewModelScope.launch {
            isNesting = true
            nestError = null
            val currentParts = withContext(Dispatchers.IO) { repository.getParts() }
            val s = withContext(Dispatchers.IO) { repository.getSettings() }
            val machine = s.machinePreset
            val app = getApplication<Application>()
            val error: String? = when {
                currentParts.isEmpty() ->
                    app.getString(R.string.nest_no_parts)
                s.sheetWidthMm <= 0 || s.sheetLengthMm <= 0 ->
                    app.getString(R.string.nest_sheet_zero)
                s.sheetCount < 1 ->
                    app.getString(R.string.nest_sheet_count_min)
                s.sheetWidthMm > machine.maxWidthMm || s.sheetLengthMm > machine.maxLengthMm ->
                    app.getString(
                        R.string.nest_sheet_too_big,
                        formatMm(s.sheetWidthMm),
                        formatMm(s.sheetLengthMm),
                        machine.displayName,
                        formatMm(machine.maxWidthMm),
                        formatMm(machine.maxLengthMm)
                    )
                else -> null
            }

            if (error != null) {
                nestError = error
                nestResult = null
                isNesting = false
                return@launch
            }

            val inputs = currentParts.map { part ->
                PartInput(
                    id = part.id,
                    name = part.name,
                    shape = part.shape,
                    widthMm = part.widthMm,
                    heightMm = part.heightMm,
                    quantity = part.quantity,
                    material = part.material,
                    thicknessMm = part.thicknessMm,
                    allowedRotations = if (part.allowRotation) {
                        part.allowedRotations
                    } else {
                        setOf(0)
                    },
                    priority = part.priority,
                    outline = part.outline
                )
            }
            val result = withContext(Dispatchers.Default) {
                NestingEngine.nest(
                    parts = inputs,
                    sheetWidthMm = s.sheetWidthMm,
                    sheetLengthMm = s.sheetLengthMm,
                    sheetCount = s.sheetCount,
                    kerfMm = s.kerfMm,
                    gapMm = s.gapMm,
                    marginMm = s.marginMm,
                    allowRotationGlobal = s.allowRotation,
                    cuttingSpeedMmPerMin = s.cuttingSpeedMmPerMin,
                    options = NestOptions(
                        pricePerKg = s.pricePerKg,
                        machineRatePerMinute = s.machineRatePerMinute,
                        pierceCost = s.pierceCost
                    )
                )
            }
            nestResult = result
            isNesting = false
        }
    }
}
