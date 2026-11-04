package app.pwhs.blockads.ui.proxyvpn.component

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.unit.dp
import app.pwhs.blockads.R
import app.pwhs.blockads.data.entities.ProxyProfile

@Composable
fun ProxyNodeRenameDialog(
    profile: ProxyProfile,
    onDismiss: () -> Unit,
    onRename: (String, String) -> Unit
) {
    var newName by remember(profile.id) { mutableStateOf(profile.name.ifEmpty { profile.tag }) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.wireguard_rename_dialog_title)) },
        text = {
            OutlinedTextField(
                value = newName,
                onValueChange = { newName = it },
                label = { Text(stringResource(R.string.wireguard_rename_dialog_title)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    val trimmed = newName.trim()
                    if (trimmed.isNotEmpty()) {
                        onRename(profile.id, trimmed)
                    }
                    onDismiss()
                },
                enabled = newName.isNotBlank()
            ) {
                Text(stringResource(R.string.wireguard_action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.wireguard_action_cancel))
            }
        }
    )
}

@Composable
fun ProxyNodeDeleteDialog(
    profile: ProxyProfile,
    onDismiss: () -> Unit,
    onDelete: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.shadowsocks_delete_confirm_title)) },
        text = {
            Text(stringResource(R.string.shadowsocks_delete_confirm_desc, profile.displayName))
        },
        confirmButton = {
            Button(
                onClick = {
                    onDelete(profile.id)
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text(stringResource(R.string.wireguard_action_delete))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.wireguard_action_cancel))
            }
        }
    )
}
