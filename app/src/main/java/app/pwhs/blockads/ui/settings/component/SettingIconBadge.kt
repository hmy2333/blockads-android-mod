package app.pwhs.blockads.ui.settings.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Modern squircle icon badge inspired by NordVPN and ExpressVPN design.
 * Features a subtle pastel background tint (alpha 0.12f) with a high-contrast monoline icon.
 */
@Composable
fun SettingIconBadge(
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    painter: Painter? = null,
    emoji: String? = null,
    tint: Color = Color(0xFF2563EB),
    contentDescription: String? = null
) {
    Box(
        modifier = modifier
            .size(36.dp)
            .background(
                color = tint.copy(alpha = 0.12f),
                shape = RoundedCornerShape(10.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        if (!emoji.isNullOrEmpty()) {
            Text(
                text = emoji,
                fontSize = 18.sp,
                textAlign = TextAlign.Center
            )
        } else if (painter != null) {
            Icon(
                painter = painter,
                contentDescription = contentDescription,
                tint = tint,
                modifier = Modifier.size(20.dp)
            )
        } else if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = tint,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
