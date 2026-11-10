package com.mrafshun20.lasernesting.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mrafshun20.lasernesting.data.AppDatabase
import com.mrafshun20.lasernesting.data.NestingRepository
import com.mrafshun20.lasernesting.data.PartEntity
import com.mrafshun20.lasernesting.data.SettingsEntity
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
                    allowRotation = part.allowRotation
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
