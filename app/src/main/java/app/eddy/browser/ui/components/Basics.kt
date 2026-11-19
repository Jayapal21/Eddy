package app.eddy.browser.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.eddy.browser.R
import app.eddy.browser.ui.sakuga.SakugaAlertDialog
import app.eddy.browser.ui.sakuga.SakugaCheckbox
import app.eddy.browser.ui.sakuga.SakugaEmptyState
import app.eddy.browser.ui.sakuga.SakugaIconButton
import app.eddy.browser.ui.sakuga.SakugaOutlinedTextField
import app.eddy.browser.ui.sakuga.SakugaRadioButton
import app.eddy.browser.ui.sakuga.SakugaTextButton
import app.eddy.browser.ui.sakuga.sakugaPressSquash
import app.eddy.browser.ui.sakuga.SakugaParticlePreset
import app.eddy.browser.ui.theme.Dimens
import app.eddy.browser.util.str
import androidx.compose.foundation.layout.Column

/** Icon button with a 48dp target, balanced optical size, and Sakuga press/hover/particle feedback. */
@Composable
fun EddyIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    iconSize: Dp = 22.dp,
    particlePreset: SakugaParticlePreset = SakugaParticlePreset.SUBTLE,
) {
    SakugaIconButton(
        icon = icon,
        contentDescription = contentDescription,
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        tint = tint,
        iconSize = iconSize,
        particlePreset = particlePreset,
    )
}

@Composable
fun TextInputDialog(
    title: String,
    initial: String,
    label: String,
    confirmLabel: String = str(R.string.save),
    keyboardType: KeyboardType = KeyboardType.Text,
    validate: (String) -> Boolean = { it.isNotBlank() },
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    SakugaAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            SakugaOutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(label) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (validate(text)) onConfirm(text.trim()) }),
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            SakugaTextButton(
                onClick = { onConfirm(text.trim()) },
                enabled = validate(text),
                burstOnClick = true,
                particlePreset = SakugaParticlePreset.SUCCESS,
            ) {
                Text(confirmLabel)
            }
        },
        dismissButton = { SakugaTextButton(onClick = onDismiss) { Text(str(R.string.cancel)) } },
    )
}

@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    SakugaAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            SakugaTextButton(onClick = onConfirm, burstOnClick = true) { Text(confirmLabel) }
        },
        dismissButton = { SakugaTextButton(onClick = onDismiss) { Text(str(R.string.cancel)) } },
    )
}

@Composable
fun <T> ChoiceDialog(
    title: String,
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onDismiss: () -> Unit,
    onSelect: (T) -> Unit,
) {
    SakugaAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                options.forEach { option ->
                    val source = remember { MutableInteractionSource() }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .sizeIn(minHeight = Dimens.touchTarget)
                            .sakugaPressSquash(source, pressX = 1.02f, pressY = 0.96f)
                            .selectable(
                                selected = option == selected,
                                interactionSource = source,
                                indication = androidx.compose.material3.ripple(bounded = true),
                                role = Role.RadioButton,
                            ) { onSelect(option); onDismiss() },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SakugaRadioButton(selected = option == selected, onClick = null)
                        Spacer(Modifier.width(12.dp))
                        Text(label(option), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { SakugaTextButton(onClick = onDismiss) { Text(str(R.string.cancel)) } },
    )
}

@Composable
fun CheckRow(text: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val source = remember { MutableInteractionSource() }
    Row(
        Modifier
            .fillMaxWidth()
            .sizeIn(minHeight = Dimens.touchTarget)
            .sakugaPressSquash(source, pressX = 1.02f, pressY = 0.96f)
            .clickable(
                interactionSource = source,
                indication = androidx.compose.material3.ripple(bounded = true),
                role = Role.Checkbox,
            ) { onChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SakugaCheckbox(checked = checked, onCheckedChange = null)
        Spacer(Modifier.width(12.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
fun EmptyState(icon: ImageVector, title: String, message: String, modifier: Modifier = Modifier) {
    SakugaEmptyState(icon = icon, title = title, message = message, modifier = modifier)
}
