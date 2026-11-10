package com.mrafshun20.lasernesting.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType

/**
 * A decimal number field bound to an external value. It keeps its own text
 * while the user types and only re-syncs when the external value changes
 * to something the current text does not already parse to.
 */
@Composable
fun DecimalField(
    label: String,
    value: Double,
    onValueChange: (Double) -> Unit,
    modifier: Modifier = Modifier,
    suffix: String = ""
) {
    var text by remember { mutableStateOf(formatNumber(value)) }

    LaunchedEffect(value) {
        val parsed = text.toDoubleOrNull()
        if (parsed == null || parsed != value) {
            text = formatNumber(value)
        }
    }

    OutlinedTextField(
        value = text,
        onValueChange = { newText ->
            text = newText
            newText.toDoubleOrNull()?.let { onValueChange(it) }
        },
        label = { Text(label) },
        suffix = if (suffix.isNotEmpty()) {
            { Text(suffix) }
        } else {
            null
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
        modifier = modifier.fillMaxWidth()
    )
}

/** An integer field, same behaviour as [DecimalField]. */
@Composable
fun IntField(
    label: String,
    value: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    suffix: String = ""
) {
    var text by remember { mutableStateOf(value.toString()) }

    LaunchedEffect(value) {
        val parsed = text.toIntOrNull()
        if (parsed == null || parsed != value) {
            text = value.toString()
        }
    }

    OutlinedTextField(
        value = text,
        onValueChange = { newText ->
            text = newText
            newText.toIntOrNull()?.let { onValueChange(it) }
        },
        label = { Text(label) },
        suffix = if (suffix.isNotEmpty()) {
            { Text(suffix) }
        } else {
            null
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
        modifier = modifier.fillMaxWidth()
    )
}

private fun formatNumber(value: Double): String {
    return if (value == value.toLong().toDouble()) {
        value.toLong().toString()
    } else {
        value.toString()
    }
}
