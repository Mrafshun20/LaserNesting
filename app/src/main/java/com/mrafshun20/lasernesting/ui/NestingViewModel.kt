package com.mrafshun20.lasernesting.ui

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mrafshun20.lasernesting.data.AppDatabase
import com.mrafshun20.lasernesting.data.Material
import com.mrafshun20.lasernesting.data.NestingRepository
import com.mrafshun20.lasernesting.data.PartEntity
import com.mrafshun20.lasernesting.data.PartOutlineJson
import com.mrafshun20.lasernesting.data.PartShape
import com.mrafshun20.lasernesting.data.SettingsEntity
import com.mrafshun20.lasernesting.dxf.DxfParser
import com.mrafshun20.lasernesting.dxf.toPartOutline
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

    /** Result of the last DXF import: how many parts landed, plus warnings. */
    var dxfImportMessage by mutableStateOf<String?>(null)
        private set

    fun upsertPart(part: PartEntity) {
        viewModelScope.launch(Dispatchers.IO) { repository.upsertPart(part) }
    }

    fun deletePart(part: PartEntity) {
        viewModelScope.launch(Dispatchers.IO) { repository.deletePart(part) }
    }

    fun updateSettings(transform: (SettingsEntity) -> SettingsEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            val current = repository.getSettings()
            repository.updateSettings(transform(current))
        }
    }

    /**
     * Applies the app language. "system" follows the device, "en" is
     * English, "fa" is Persian. Persisted in settings and applied via
     * AppCompat's per-app locale, which survives restarts.
     */
    fun setLanguage(language: String) {
        val code = when (language) {
            "en", "fa" -> language
            else -> "system"
        }
        updateSettings { it.copy(language = code) }
        val locales = when (code) {
            "en" -> LocaleListCompat.forLanguageTags("en")
            "fa" -> LocaleListCompat.forLanguageTags("fa")
            else -> LocaleListCompat.getEmptyLocaleList()
        }
        AppCompatDelegate.setApplicationLocales(locales)
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
            if (result == null) {
                dxfImportMessage = "Could not read this DXF file."
                return@launch
            }
            if (result.parts.isEmpty()) {
                val warning = result.warnings.firstOrNull()
                dxfImportMessage = warning
                    ?: "No closed parts were found in this DXF file."
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
            dxfImportMessage = if (result.warnings.isEmpty()) {
                "Imported $imported part(s) from $fileName."
            } else {
                "Imported $imported part(s) from $fileName. ${result.warnings.first()}"
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

            val error: String? = when {
                currentParts.isEmpty() ->
                    "Add at least one part first."
                s.sheetWidthMm <= 0 || s.sheetLengthMm <= 0 ->
                    "Sheet size must be greater than zero."
                s.sheetCount < 1 ->
                    "Sheet quantity must be at least 1."
                s.sheetWidthMm > machine.maxWidthMm || s.sheetLengthMm > machine.maxLengthMm ->
                    "Sheet (${formatMm(s.sheetWidthMm)} x ${formatMm(s.sheetLengthMm)} mm) is " +
                        "larger than the ${machine.displayName} working area " +
                        "(${formatMm(machine.maxWidthMm)} x ${formatMm(machine.maxLengthMm)} mm). " +
                        "Pick a smaller sheet or another machine."
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
                    cuttingSpeedMmPerMin = s.cuttingSpeedMmPerMin
                )
            }
            nestResult = result
            isNesting = false
        }
    }

    fun clearResult() {
        nestResult = null
        nestError = null
    }
}
