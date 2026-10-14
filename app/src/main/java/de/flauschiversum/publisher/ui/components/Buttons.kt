package de.flauschiversum.publisher.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/** Schaltfläche nur mit Symbol; [label] ist die Beschriftung für TalkBack. Mindestgröße 48 dp. */
@Composable
fun AppIconButton(icon: ImageVector, label: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    IconButton(onClick = onClick, modifier = modifier.size(48.dp), enabled = enabled) { Icon(icon, contentDescription = label) }
}

@Composable
private fun Content(icon: ImageVector?, label: String) {
    if (icon != null) { Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp)); androidx.compose.foundation.layout.Spacer(Modifier.width(8.dp)) }
    Text(label)
}

/** Schaltflächen mit Symbol vor dem Text (das Symbol ist reine Verdeutlichung, der Text beschriftet). */
@Composable
fun AppTextButton(onClick: () -> Unit, label: String, icon: ImageVector? = null, modifier: Modifier = Modifier, enabled: Boolean = true) =
    TextButton(onClick = onClick, modifier = modifier, enabled = enabled) { Content(icon, label) }

@Composable
fun AppOutlinedButton(onClick: () -> Unit, label: String, icon: ImageVector? = null, modifier: Modifier = Modifier, enabled: Boolean = true) =
    OutlinedButton(onClick = onClick, modifier = modifier, enabled = enabled) { Content(icon, label) }

@Composable
fun AppButton(onClick: () -> Unit, label: String, icon: ImageVector? = null, modifier: Modifier = Modifier, enabled: Boolean = true) =
    Button(onClick = onClick, modifier = modifier, enabled = enabled) { Content(icon, label) }
