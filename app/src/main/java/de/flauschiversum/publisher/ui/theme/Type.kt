package de.flauschiversum.publisher.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import de.flauschiversum.publisher.R

/** Lobster für Name und Titel (wie im Logo des Blogs), Lora für Beitragstext. */
val Lobster = FontFamily(Font(R.font.lobster))
val Lora = FontFamily(
    Font(R.font.lora_regular, FontWeight.Normal),
    Font(R.font.lora_bold, FontWeight.Bold),
    Font(R.font.lora_italic, FontWeight.Normal, FontStyle.Italic),
    Font(R.font.lora_bold_italic, FontWeight.Bold, FontStyle.Italic),
)

private val base = Typography()

val AppTypography = base.copy(
    headlineLarge = base.headlineLarge.copy(fontFamily = Lobster),
    headlineMedium = base.headlineMedium.copy(fontFamily = Lobster),
    headlineSmall = base.headlineSmall.copy(fontFamily = Lobster),
    titleLarge = base.titleLarge.copy(fontFamily = Lobster),
)

/** Textstil für den Beitragstext im Editor und in der Vorschau; [scale] ist die gewählte Größe. */
fun editorTextStyle(scale: Float): TextStyle = TextStyle(
    fontFamily = Lora,
    fontSize = (17f * scale).sp,
    lineHeight = (26f * scale).sp,
)
