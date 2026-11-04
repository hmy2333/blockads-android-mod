package app.pwhs.blockads.ui.proxyvpn.component

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.pwhs.blockads.R
import app.pwhs.blockads.data.entities.WireGuardProfile

@Composable
fun WireGuardRenameDialog(
    target: WireGuardProfile,
    onDismiss: () -> Unit,
    onRename: (id: String, newName: String) -> Unit
) {
    var text by remember(target.id) { mutableStateOf(target.name) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.wireguard_rename_dialog_title)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onRename(target.id, text.trim())
                    onDismiss()
                },
                enabled = text.isNotBlank() && text != target.name
            ) { Text(stringResource(R.string.wireguard_action_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.wireguard_action_cancel))
            }
        }
    )
}

@Composable
fun WireGuardDeleteDialog(
    target: WireGuardProfile,
    onDismiss: () -> Unit,
    onDelete: (id: String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.wireguard_delete_dialog_title, target.name)) },
        text = { Text(stringResource(R.string.wireguard_delete_dialog_text)) },
        confirmButton = {
            TextButton(onClick = {
                onDelete(target.id)
                onDismiss()
            }) {
                Text(stringResource(R.string.wireguard_action_delete), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.wireguard_action_cancel))
            }
        }
    )
}
