package app.pwhs.blockads.ui.home.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.pwhs.blockads.R
import app.pwhs.blockads.data.network.NetworkSpeed
import app.pwhs.blockads.utils.formatDataSize

private val DownloadCyan = Color(0xFF00BCD4)
private val UploadPurple = Color(0xFFA855F7)

@Composable
fun NetworkSpeedSection(
    networkSpeed: NetworkSpeed,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        TrafficCard(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            icon = Icons.Default.ArrowDownward,
            label = stringResource(R.string.home_downloaded),
            value = formatDataSize(networkSpeed.totalDownloadedBytes / 1024),
            color = DownloadCyan,
            chartPoints = networkSpeed.downloadHistory,
            bobDirection = 1
        )
        TrafficCard(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            icon = Icons.Default.ArrowUpward,
            label = stringResource(R.string.home_uploaded),
            value = formatDataSize(networkSpeed.totalUploadedBytes / 1024),
            color = UploadPurple,
            chartPoints = networkSpeed.uploadHistory,
            bobDirection = -1
        )
    }
}
