package app.pwhs.blockads.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A single shimmering placeholder item mimicking the app list item structure.
 */
@Composable
fun AppItemSkeleton(
    brush: Brush,
    modifier: Modifier = Modifier,
    titleWidth: Dp = 130.dp,
    subtitleWidth: Dp = 90.dp
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 3.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // App icon skeleton
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .shimmer(brush, CircleShape)
            )

            Spacer(modifier = Modifier.width(12.dp))

            // App label and package name skeleton
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .width(titleWidth)
                        .height(16.dp)
                        .shimmer(brush, RoundedCornerShape(4.dp))
                )
                Spacer(modifier = Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .width(subtitleWidth)
                        .height(12.dp)
                        .shimmer(brush, RoundedCornerShape(4.dp))
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Action control skeleton (switch / button placeholder)
            Box(
                modifier = Modifier
                    .size(width = 44.dp, height = 24.dp)
                    .shimmer(brush, RoundedCornerShape(12.dp))
            )
        }
    }
}

/**
 * A list of skeleton items displaying a shimmering loading state.
 */
@Composable
fun AppListSkeleton(
    modifier: Modifier = Modifier,
    itemCount: Int = 8
) {
    val brush = rememberShimmerBrush()
    val titleWidths = listOf(140.dp, 110.dp, 160.dp, 125.dp, 150.dp, 105.dp, 135.dp, 120.dp)
    val subtitleWidths = listOf(100.dp, 80.dp, 120.dp, 90.dp, 110.dp, 75.dp, 95.dp, 85.dp)

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        userScrollEnabled = false
    ) {
        items(itemCount) { index ->
            val tWidth = titleWidths[index % titleWidths.size]
            val sWidth = subtitleWidths[index % subtitleWidths.size]
            AppItemSkeleton(
                brush = brush,
                titleWidth = tWidth,
                subtitleWidth = sWidth
            )
        }
    }
}
