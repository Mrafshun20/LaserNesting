package com.mrafshun20.lasernesting.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.PrecisionManufacturing
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mrafshun20.lasernesting.R
import com.mrafshun20.lasernesting.data.CutGas
import com.mrafshun20.lasernesting.data.MachinePreset
import com.mrafshun20.lasernesting.data.Material
import com.mrafshun20.lasernesting.data.SheetPreset
import com.mrafshun20.lasernesting.ui.components.DecimalField
import com.mrafshun20.lasernesting.ui.components.IntField

/**
 * Machine, sheet and nesting settings.
 *
 * The machine presets define the Bystronic bed's maximum working area.
 * The sheet presets are the sheet sizes actually nested on — usually
 * 2000 x 1000 mm or 2500 x 1250 mm — and the default is 2000 x 1000 mm.
 */
@Composable
fun SetupScreen(viewModel: NestingViewModel) {
    val settings by viewModel.settings.collectAsState()
    val machine = settings.machinePreset
    val sheetTooBig = settings.sheetWidthMm > machine.maxWidthMm ||
        settings.sheetLengthMm > machine.maxLengthMm

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SectionCard(
            icon = Icons.Filled.Language,
            title = stringResource(R.string.setup_language)
        ) {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Persian is the default; English is an explicit choice.
                // There is no "follow the device" option — the app always
                // opens in the language picked here.
                FilterChip(
                    selected = settings.language == "fa",
                    onClick = { viewModel.setLanguage("fa") },
                    label = { Text(stringResource(R.string.setup_language_fa)) }
                )
                FilterChip(
                    selected = settings.language == "en",
                    onClick = { viewModel.setLanguage("en") },
                    label = { Text(stringResource(R.string.setup_language_en)) }
                )
            }
        }

        SectionCard(
            icon = Icons.Filled.PrecisionManufacturing,
            title = stringResource(R.string.setup_machine)
        ) {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MachinePreset.entries.forEach { preset ->
                    FilterChip(
                        selected = machine == preset,
                        onClick = {
                            viewModel.updateSettings { it.copy(machinePreset = preset) }
                        },
                        label = { Text(preset.displayName, maxLines = 1) }
                    )
                }
            }
            Text(
                text = stringResource(
                    R.string.setup_max_area,
                    formatMm(machine.maxWidthMm),
                    formatMm(machine.maxLengthMm)
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        SectionCard(icon = Icons.Filled.Layers, title = stringResource(R.string.setup_sheet)) {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SheetPreset.entries.forEach { preset ->
                    FilterChip(
                        selected = settings.sheetPreset == preset,
                        onClick = {
                            viewModel.updateSettings { current ->
                                val w = preset.widthMm
                                val l = preset.lengthMm
                                if (w != null && l != null) {
                                    current.copy(
                                        sheetPreset = preset,
                                        sheetWidthMm = w,
                                        sheetLengthMm = l
                                    )
                                } else {
                                    current.copy(sheetPreset = preset)
                                }
                            }
                        },
                        label = { Text(preset.localizedName(), maxLines = 1) }
                    )
                }
            }
            DecimalField(
                label = stringResource(R.string.setup_sheet_width),
                value = settings.sheetWidthMm,
                suffix = "mm",
                onValueChange = { value ->
                    viewModel.updateSettings {
                        it.copy(sheetWidthMm = value, sheetPreset = SheetPreset.CUSTOM)
                    }
                }
            )
            DecimalField(
                label = stringResource(R.string.setup_sheet_length),
                value = settings.sheetLengthMm,
                suffix = "mm",
                onValueChange = { value ->
                    viewModel.updateSettings {
                        it.copy(sheetLengthMm = value, sheetPreset = SheetPreset.CUSTOM)
                    }
                }
            )
            if (sheetTooBig) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.error.copy(alpha = 0.4f)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.WarningAmber,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(
                                R.string.setup_sheet_too_big,
                                machine.displayName,
                                formatMm(machine.maxWidthMm),
                                formatMm(machine.maxLengthMm)
                            ),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Material.entries.forEach { entry ->
                    FilterChip(
                        selected = settings.sheetMaterial == entry,
                        onClick = {
                            viewModel.updateSettings { it.copy(sheetMaterial = entry) }
                        },
                        label = { Text(entry.localizedName(), maxLines = 1) }
                    )
                }
            }
            // The assist gas decides the per-metre cutting rate, so it
            // is shown right under the material choice.
            Text(
                text = stringResource(R.string.setup_cut_gas) + ": " +
                    CutGas.forMaterial(settings.sheetMaterial, settings.sheetThicknessMm),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            DecimalField(
                label = stringResource(R.string.setup_sheet_thickness),
                value = settings.sheetThicknessMm,
                suffix = "mm",
                onValueChange = { value ->
                    viewModel.updateSettings { it.copy(sheetThicknessMm = value) }
                }
            )
            IntField(
                label = stringResource(R.string.setup_sheet_count),
                value = settings.sheetCount,
                onValueChange = { value ->
                    viewModel.updateSettings { it.copy(sheetCount = value) }
                }
            )
        }

        SectionCard(icon = Icons.Filled.Dashboard, title = stringResource(R.string.setup_nesting)) {
            DecimalField(
                label = stringResource(R.string.setup_kerf),
                value = settings.kerfMm,
                suffix = "mm",
                onValueChange = { value ->
                    viewModel.updateSettings { it.copy(kerfMm = value) }
                }
            )
            DecimalField(
                label = stringResource(R.string.setup_gap),
                value = settings.gapMm,
                suffix = "mm",
                onValueChange = { value ->
                    viewModel.updateSettings { it.copy(gapMm = value) }
                }
            )
            DecimalField(
                label = stringResource(R.string.setup_margin),
                value = settings.marginMm,
                suffix = "mm",
                onValueChange = { value ->
                    viewModel.updateSettings { it.copy(marginMm = value) }
                }
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.setup_allow_rotation),
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = settings.allowRotation,
                    onCheckedChange = { checked ->
                        viewModel.updateSettings { it.copy(allowRotation = checked) }
                    }
                )
            }
        }

        SectionCard(icon = Icons.Filled.Speed, title = stringResource(R.string.setup_cutting)) {
            DecimalField(
                label = stringResource(R.string.setup_cutting_speed),
                value = settings.cuttingSpeedMmPerMin,
                suffix = "mm/min",
                onValueChange = { value ->
                    viewModel.updateSettings { it.copy(cuttingSpeedMmPerMin = value) }
                }
            )
            Text(
                text = stringResource(R.string.setup_cutting_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        SectionCard(icon = Icons.Filled.AttachMoney, title = stringResource(R.string.setup_pricing)) {
            // Cutting fee first: laser cutting is priced per metre of
            // cut. The sheet price per kg is only the material cost.
            DecimalField(
                label = stringResource(R.string.setup_cut_price_per_meter),
                value = settings.cutPricePerMeter,
                onValueChange = { value ->
                    viewModel.updateSettings { it.copy(cutPricePerMeter = value) }
                }
            )
            DecimalField(
                label = stringResource(R.string.setup_pierce_cost),
                value = settings.pierceCost,
                onValueChange = { value ->
                    viewModel.updateSettings { it.copy(pierceCost = value) }
                }
            )
            DecimalField(
                label = stringResource(R.string.setup_price_per_kg),
                value = settings.pricePerKg,
                onValueChange = { value ->
                    viewModel.updateSettings { it.copy(pricePerKg = value) }
                }
            )
        }
    }
}

@Composable
private fun SectionCard(
    icon: ImageVector,
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(
                            MaterialTheme.colorScheme.primaryContainer,
                            RoundedCornerShape(8.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text(title, style = MaterialTheme.typography.titleMedium)
            }
            content()
        }
    }
}
