package app.pwhs.blockads.ui.settings.component

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import app.pwhs.blockads.R
import app.pwhs.blockads.ui.theme.BlockadsTheme
import app.pwhs.blockads.ui.theme.DangerRed

@Composable
fun ConfirmResetSettingsDialog(
    modifier: Modifier = Modifier,
    onReset: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_reset)) },
        text = { Text(stringResource(R.string.settings_reset_confirm_message)) },
        confirmButton = {
            TextButton(onClick = onReset) {
                Text(
                    stringResource(R.string.settings_reset),
                    color = DangerRed
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.settings_cancel))
            }
        }
    )
}

@Preview(name = "Light Mode")
@Composable
private fun ConfirmResetSettingsDialogPreview() {
    BlockadsTheme {
        ConfirmResetSettingsDialog(
            onReset = {},
            onDismiss = {}
        )
    }
}
