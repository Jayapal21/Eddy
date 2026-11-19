package app.eddy.browser.browser

import app.eddy.browser.R
import app.eddy.browser.util.str
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import app.eddy.browser.privacy.SiteFeature
import app.eddy.browser.ui.components.CheckRow
import app.eddy.browser.ui.sakuga.SakugaAlertDialog
import app.eddy.browser.ui.sakuga.SakugaOutlinedTextField
import app.eddy.browser.ui.sakuga.SakugaTextButton

/** Shows the oldest pending prompt; later ones appear as earlier ones are answered. */
@Composable
fun PromptHost(vm: BrowserViewModel) {
    when (val p = vm.prompts.firstOrNull()) {
        is Prompt.Permission -> PermissionDialog(p, vm)
        is Prompt.HttpAuth -> AuthDialog(p, vm)
        is Prompt.ExternalApp -> SakugaAlertDialog(
            onDismissRequest = { vm.answerExternal(p, false) },
            title = { Text(str(R.string.open_in_another_app_2)) },
            text = { Text(str(R.string.page_wants_to_open_app, p.intent.dataString ?: p.intent.`package` ?: str(R.string.unknown))) },
            confirmButton = { SakugaTextButton(onClick = { vm.answerExternal(p, true) }, burstOnClick = true) { Text(str(R.string.open)) } },
            dismissButton = { SakugaTextButton(onClick = { vm.answerExternal(p, false) }) { Text(str(R.string.stay_here)) } },
        )
        is Prompt.RiskyDownload -> SakugaAlertDialog(
            onDismissRequest = { vm.answerRiskyDownload(p, false) },
            title = { Text(str(R.string.download_this_file)) },
            text = {
                Text(
                    str(R.string.file_from_host_can_run_code, p.fileName, p.sourceHost) +
                        (if (p.insecure) str(R.string.it_is_also_being_sent_over_an_unencrypte) else "") +
                        str(R.string.download_it_only_if_you_trust_this_site),
                )
            },
            confirmButton = { SakugaTextButton(onClick = { vm.answerRiskyDownload(p, true) }, burstOnClick = true) { Text(str(R.string.download)) } },
            dismissButton = { SakugaTextButton(onClick = { vm.answerRiskyDownload(p, false) }) { Text(str(R.string.cancel)) } },
        )
        is Prompt.Paste -> SakugaAlertDialog(
            onDismissRequest = { vm.answerPaste(p, false) },
            title = { Text(str(R.string.paste_from_clipboard)) },
            text = { Text(str(R.string.host_wants_to_read_clipboard, p.host)) },
            confirmButton = { SakugaTextButton(onClick = { vm.answerPaste(p, true) }, burstOnClick = true) { Text(str(R.string.paste)) } },
            dismissButton = { SakugaTextButton(onClick = { vm.answerPaste(p, false) }) { Text(str(R.string.block)) } },
        )
        null -> Unit
    }
}

@Composable
private fun PermissionDialog(p: Prompt.Permission, vm: BrowserViewModel) {
    var rememberChoice by remember(p) { mutableStateOf(true) }
    val what = p.features.joinToString(str(R.string.and_separator)) {
        when (it) {
            SiteFeature.CAMERA -> str(R.string.feature_camera)
            SiteFeature.MICROPHONE -> str(R.string.feature_microphone)
            SiteFeature.LOCATION -> str(R.string.feature_location)
            else -> it.name.lowercase()
        }
    }
    SakugaAlertDialog(
        onDismissRequest = { vm.answerPermission(p, false, false) },
        title = { Text(str(R.string.allow_to_use_your, p.host, what)) },
        text = { if (!p.incognito) CheckRow(str(R.string.remember_my_choice_for_this_site), rememberChoice) { rememberChoice = it } },
        confirmButton = { SakugaTextButton(onClick = { vm.answerPermission(p, true, rememberChoice) }, burstOnClick = true) { Text(str(R.string.allow)) } },
        dismissButton = { SakugaTextButton(onClick = { vm.answerPermission(p, false, rememberChoice) }) { Text(str(R.string.block)) } },
    )
}

@Composable
private fun AuthDialog(p: Prompt.HttpAuth, vm: BrowserViewModel) {
    var user by remember(p) { mutableStateOf("") }
    var pass by remember(p) { mutableStateOf("") }
    SakugaAlertDialog(
        onDismissRequest = { vm.answerAuth(p, null, null) },
        title = { Text(str(R.string.sign_in_to, p.host)) },
        text = {
            Column {
                if (p.realm.isNotBlank()) Text(str(R.string.realm, p.realm))
                Spacer(Modifier.height(12.dp))
                SakugaOutlinedTextField(user, { user = it }, Modifier.fillMaxWidth(), label = { Text(str(R.string.username)) }, singleLine = true)
                Spacer(Modifier.height(8.dp))
                SakugaOutlinedTextField(pass, { pass = it }, Modifier.fillMaxWidth(), label = { Text(str(R.string.password)) }, singleLine = true, visualTransformation = PasswordVisualTransformation())
            }
        },
        confirmButton = { SakugaTextButton(onClick = { vm.answerAuth(p, user, pass) }, burstOnClick = true) { Text(str(R.string.sign_in)) } },
        dismissButton = { SakugaTextButton(onClick = { vm.answerAuth(p, null, null) }) { Text(str(R.string.cancel)) } },
    )
}
