package com.mrafshun20.lasernesting.desktop

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Shapes
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.platform.Font
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mrafshun20.lasernesting.util.Fmt

/** iBebels palette: graphite and steel from the logo, laser orange as the accent. */
object Ib {
    val Bg = Color(0xFF090C11)
    val Bg2 = Color(0xFF0E131B)
    val Card = Color(0xFF131A24)
    val CardHi = Color(0xFF1A2330)
    val Line = Color(0xFF263143)
    val Text = Color(0xFFEAF0FA)
    val TextDim = Color(0xFF93A0B8)
    val Orange = Color(0xFFFF7A1A)
    val Amber = Color(0xFFFFB02E)
    val Sky = Color(0xFF3FB8FF)
    val Green = Color(0xFF34D399)
    val Red = Color(0xFFFF5D6C)
    val Steel = Color(0xFFB9C4D6)

    val Laser = Brush.linearGradient(listOf(Color(0xFFFF6A00), Color(0xFFFFA62B)))
    val Sheet = Brush.linearGradient(listOf(Color(0xFF1B2433), Color(0xFF141B27)))

    val CardShape = RoundedCornerShape(22.dp)
    val ControlShape = RoundedCornerShape(14.dp)
    val PillShape = RoundedCornerShape(50)
}

private val IbColors: ColorScheme = darkColorScheme(
    primary = Ib.Orange,
    onPrimary = Color(0xFF1A0D00),
    primaryContainer = Color(0xFF3B1F08),
    onPrimaryContainer = Color(0xFFFFD7B5),
    secondary = Ib.Sky,
    onSecondary = Color(0xFF00212F),
    background = Ib.Bg,
    onBackground = Ib.Text,
    surface = Ib.Card,
    onSurface = Ib.Text,
    surfaceVariant = Ib.CardHi,
    onSurfaceVariant = Ib.TextDim,
    outline = Ib.Line,
    outlineVariant = Ib.Line,
    error = Ib.Red,
    errorContainer = Color(0xFF4A1620),
    onErrorContainer = Color(0xFFFFD6DB)
)

private val IbShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(30.dp)
)

/** Vazirmatn is bundled in resources/fonts: Persian and Latin in one clean face. */
private val Vazir = FontFamily(
    Font(resource = "fonts/vazirmatn_regular.ttf", weight = FontWeight.Normal),
    Font(resource = "fonts/vazirmatn_medium.ttf", weight = FontWeight.Medium),
    Font(resource = "fonts/vazirmatn_bold.ttf", weight = FontWeight.Bold)
)

private fun TextStyle.vazir(weight: FontWeight? = null): TextStyle =
    copy(fontFamily = Vazir, fontWeight = weight ?: fontWeight)

private fun ibTypography(): Typography {
    val d = Typography()
    return Typography(
        displayLarge = d.displayLarge.vazir(),
        displayMedium = d.displayMedium.vazir(),
        displaySmall = d.displaySmall.vazir(),
        headlineLarge = d.headlineLarge.vazir(FontWeight.Bold),
        headlineMedium = d.headlineMedium.vazir(FontWeight.Bold),
        headlineSmall = d.headlineSmall.vazir(FontWeight.Bold),
        titleLarge = d.titleLarge.vazir(FontWeight.Bold),
        titleMedium = d.titleMedium.vazir(FontWeight.Medium),
        titleSmall = d.titleSmall.vazir(FontWeight.Medium),
        bodyLarge = d.bodyLarge.vazir(),
        bodyMedium = d.bodyMedium.vazir(),
        bodySmall = d.bodySmall.vazir(),
        labelLarge = d.labelLarge.vazir(FontWeight.Medium),
        labelMedium = d.labelMedium.vazir(FontWeight.Medium),
        labelSmall = d.labelSmall.vazir()
    )
}

/**
 * App theme. The Surface paints the window background: without it Compose
 * Desktop leaves the window white, which is what made the old UI glare.
 */
@Composable
fun IBebelsTheme(persian: Boolean, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = IbColors, typography = ibTypography(), shapes = IbShapes) {
        CompositionLocalProvider(
            LocalLayoutDirection provides if (persian) LayoutDirection.Rtl else LayoutDirection.Ltr
        ) {
            Surface(modifier = Modifier.fillMaxSize(), color = Ib.Bg) {
                content()
            }
        }
    }
}

// ----------------------------------------------------------------------
// Reusable pieces
// ----------------------------------------------------------------------

/** Rounded dark card with a hairline border. */
@Composable
fun SoftCard(
    modifier: Modifier = Modifier,
    padding: Dp = 18.dp,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .clip(Ib.CardShape)
            .background(Ib.Card)
            .border(BorderStroke(1.dp, Ib.Line), Ib.CardShape)
            .padding(padding)
    ) { content() }
}

/** Primary call-to-action with the laser gradient. */
@Composable
fun LaserButton(
    text: String,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val bg = if (enabled) Modifier.background(Ib.Laser, Ib.PillShape)
    else Modifier.background(Ib.CardHi, Ib.PillShape)
    Row(
        modifier = modifier
            .clip(Ib.PillShape)
            .then(bg)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
    ) {
        if (icon != null) {
            Icon(icon, null, tint = if (enabled) Color(0xFF1A0D00) else Ib.TextDim, modifier = Modifier.size(20.dp))
        }
        Text(
            text,
            color = if (enabled) Color(0xFF1A0D00) else Ib.TextDim,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
        )
    }
}

/** Secondary pill button: outlined, dark. */
@Composable
fun GhostButton(
    text: String,
    icon: ImageVector? = null,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Row(
        modifier = modifier
            .clip(Ib.PillShape)
            .background(Ib.CardHi)
            .border(BorderStroke(1.dp, Ib.Line), Ib.PillShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
    ) {
        if (icon != null) Icon(icon, null, tint = Ib.Steel, modifier = Modifier.size(18.dp))
        Text(text, color = Ib.Text, style = MaterialTheme.typography.labelLarge)
    }
}

/** Round icon-only button. */
@Composable
fun RoundIconButton(
    icon: ImageVector,
    description: String,
    tint: Color = Ib.Steel,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(Ib.CardHi)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, description, tint = tint, modifier = Modifier.size(20.dp))
    }
}

/** Selectable pill (single choice). */
@Composable
fun Pill(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val shape = Ib.PillShape
    val base = if (selected) Modifier.background(Ib.Laser, shape)
    else Modifier.background(Ib.CardHi, shape).border(BorderStroke(1.dp, Ib.Line), shape)
    Box(
        modifier = modifier
            .clip(shape)
            .then(base)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            color = if (selected) Color(0xFF1A0D00) else Ib.Text,
            style = MaterialTheme.typography.labelLarge.copy(
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
            )
        )
    }
}

/** Section heading with a small orange tick. */
@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .width(4.dp)
                .height(18.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Ib.Laser)
        )
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.titleMedium, color = Ib.Text)
    }
}

/** Big-number tile used for results. */
@Composable
fun StatTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    accent: Color = Ib.Orange
) {
    Column(
        modifier = modifier
            .clip(Ib.CardShape)
            .background(Ib.Card)
            .border(BorderStroke(1.dp, Ib.Line), Ib.CardShape)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(value, style = MaterialTheme.typography.headlineSmall, color = accent)
        Text(label, style = MaterialTheme.typography.bodySmall, color = Ib.TextDim)
    }
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Ib.Orange,
    unfocusedBorderColor = Ib.Line,
    focusedContainerColor = Ib.Bg2,
    unfocusedContainerColor = Ib.Bg2,
    focusedLabelColor = Ib.Orange,
    unfocusedLabelColor = Ib.TextDim,
    cursorColor = Ib.Orange,
    focusedTextColor = Ib.Text,
    unfocusedTextColor = Ib.Text
)

/** Decimal field that accepts Persian/Arabic digits and reports a parsed value. */
@Composable
fun DecimalField(
    label: String,
    value: Double,
    modifier: Modifier = Modifier,
    onValue: (Double) -> Unit
) {
    var text by remember(value) { mutableStateOf(Fmt.mm(value)) }
    OutlinedTextField(
        value = text,
        onValueChange = { raw ->
            text = raw
            Fmt.parseDouble(raw)?.let(onValue)
        },
        label = { Text(label) },
        singleLine = true,
        shape = Ib.ControlShape,
        colors = fieldColors(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier
    )
}

@Composable
fun IntegerField(
    label: String,
    value: Int,
    modifier: Modifier = Modifier,
    onValue: (Int) -> Unit
) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    OutlinedTextField(
        value = text,
        onValueChange = { raw ->
            text = raw
            Fmt.parseInt(raw)?.let(onValue)
        },
        label = { Text(label) },
        singleLine = true,
        shape = Ib.ControlShape,
        colors = fieldColors(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier
    )
}

@Composable
fun TextInput(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    onValue: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValue,
        label = { Text(label) },
        singleLine = true,
        shape = Ib.ControlShape,
        colors = fieldColors(),
        modifier = modifier
    )
}

/** Labelled on/off row. */
@Composable
fun ToggleRow(
    label: String,
    checked: Boolean,
    modifier: Modifier = Modifier,
    onChange: (Boolean) -> Unit
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(Ib.ControlShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onChange(!checked) }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = Ib.Text, modifier = Modifier.weight(1f))
        Box(
            modifier = Modifier
                .width(46.dp)
                .height(26.dp)
                .clip(Ib.PillShape)
                .background(if (checked) Ib.Orange else Ib.Line),
            contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart
        ) {
            Box(
                Modifier
                    .padding(3.dp)
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(Color.White)
            )
        }
    }
}

/** Small tinted badge. */
@Composable
fun Badge(text: String, color: Color = Ib.Sky) {
    Text(
        text,
        color = color,
        style = MaterialTheme.typography.labelSmall,
        modifier = Modifier
            .clip(Ib.PillShape)
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    )
}

@Composable
fun RowScope.FillSpacer() {
    Spacer(Modifier.weight(1f))
}

@Composable
fun VGap(h: Int) {
    Spacer(Modifier.height(h.dp))
}
