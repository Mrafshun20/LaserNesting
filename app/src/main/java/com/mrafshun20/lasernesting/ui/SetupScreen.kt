package com.mrafshun20.lasernesting.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
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
import androidx.compose.ui.unit.dp
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
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SectionCard(icon = Icons.Filled.PrecisionManufacturing, title = "Machine") {
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
                        label = { Text(preset.displayName) }
                    )
                }
            }
            Text(
                text = "Max working area: ${formatMm(machine.maxWidthMm)} x " +
                    "${formatMm(machine.maxLengthMm)} mm",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        SectionCard(icon = Icons.Filled.Layers, title = "Sheet") {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SheetPreset.entries.forEach { preset ->
                    FilterChip(
                        selected = settings.sheetPreset == preset,
                        onClick = {
                            viewModel.updateSettings { current ->
                                if (preset.widthMm != null && preset.lengthMm != null) {
                                    current.copy(
                                        sheetPreset = preset,
                                        sheetWidthMm = preset.widthMm,
                                        sheetLengthMm = preset.lengthMm
                                    )
                                } else {
                                    current.copy(sheetPreset = preset)
                                }
                            }
                        },
                        label = { Text(preset.displayName) }
                    )
                }
            }
            DecimalField(
                label = "Sheet width",
                value = settings.sheetWidthMm,
                suffix = "mm",
                onValueChange = { value ->
                    viewModel.updateSettings {
                        it.copy(sheetWidthMm = value, sheetPreset = SheetPreset.CUSTOM)
                    }
                }
            )
            DecimalField(
                label = "Sheet length",
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
                    shape = RoundedCornerShape(12.dp)
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
                            text = "Sheet is larger than the ${machine.displayName} " +
                                "working area (${formatMm(machine.maxWidthMm)} x " +
                                "${formatMm(machine.maxLengthMm)} mm).",
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
                        label = { Text(entry.displayName) }
                    )
                }
            }
            DecimalField(
                label = "Sheet thickness",
                value = settings.sheetThicknessMm,
                suffix = "mm",
                onValueChange = { value ->
                    viewModel.updateSettings { it.copy(sheetThicknessMm = value) }
                }
            )
            IntField(
                label = "Number of sheets",
                value = settings.sheetCount,
                onValueChange = { value ->
                    viewModel.updateSettings { it.copy(sheetCount = value) }
                }
            )
        }

        SectionCard(icon = Icons.Filled.Dashboard, title = "Nesting") {
            DecimalField(
                label = "Kerf (cut width)",
                value = settings.kerfMm,
                suffix = "mm",
                onValueChange = { value ->
                    viewModel.updateSettings { it.copy(kerfMm = value) }
                }
            )
            DecimalField(
                label = "Part-to-part gap",
                value = settings.gapMm,
                suffix = "mm",
                onValueChange = { value ->
                    viewModel.updateSettings { it.copy(gapMm = value) }
                }
            )
            DecimalField(
                label = "Sheet edge margin",
                value = settings.marginMm,
                suffix = "mm",
                onValueChange = { value ->
                    viewModel.updateSettings { it.copy(marginMm = value) }
                }
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Allow 90° rotation",
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

        SectionCard(icon = Icons.Filled.Speed, title = "Cutting") {
            DecimalField(
                label = "Cutting speed",
                value = settings.cuttingSpeedMmPerMin,
                suffix = "mm/min",
                onValueChange = { value ->
                    viewModel.updateSettings { it.copy(cuttingSpeedMmPerMin = value) }
                }
            )
            Text(
                text = "Cutting time is a rough estimate: total cut length divided by speed.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
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
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(title, style = MaterialTheme.typography.titleMedium)
            }
            content()
        }
    }
}
