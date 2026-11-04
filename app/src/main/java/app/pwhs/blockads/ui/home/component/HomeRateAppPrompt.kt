package app.pwhs.blockads.ui.home.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pwhs.blockads.BuildConfig
import app.pwhs.blockads.R
import app.pwhs.blockads.data.datastore.AppPreferences
import app.pwhs.blockads.ui.common.SupportErrorInfo
import app.pwhs.blockads.ui.component.ErrorSupportBottomSheet
import app.pwhs.blockads.ui.component.RateAppBottomSheet
import app.pwhs.blockads.utils.SupportIntentHelper
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

private const val MIN_BLOCKED_COUNT_FOR_RATING = 50
private const val RATE_PROMPT_COOLDOWN_MS = 3L * 24 * 60 * 60 * 1000 // 3 days

@Composable
fun HomeRateAppPrompt(
    blockedCount: Int,
    modifier: Modifier = Modifier,
    appPrefs: AppPreferences = koinInject()
) {
    if (!BuildConfig.IS_STORE) return

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val hasRatedApp by appPrefs.hasRatedApp.collectAsStateWithLifecycle(initialValue = true)
    val ratePromptLastDismissed by appPrefs.ratePromptLastDismissed.collectAsStateWithLifecycle(initialValue = 0L)

    var dismissedLocally by remember { mutableStateOf(false) }
    var showSupportSheet by remember { mutableStateOf(false) }

    val shouldShowRatePrompt = remember(hasRatedApp, blockedCount, ratePromptLastDismissed, dismissedLocally) {
        !hasRatedApp &&
                !dismissedLocally &&
                blockedCount >= MIN_BLOCKED_COUNT_FOR_RATING &&
                (System.currentTimeMillis() - ratePromptLastDismissed) > RATE_PROMPT_COOLDOWN_MS
    }

    if (shouldShowRatePrompt) {
        RateAppBottomSheet(
            blockedCount = blockedCount,
            onRate = {
                dismissedLocally = true
                coroutineScope.launch { appPrefs.setHasRatedApp(true) }
                SupportIntentHelper.openPlayStore(context)
            },
            onFeedback = {
                dismissedLocally = true
                coroutineScope.launch { appPrefs.setRatePromptLastDismissed(System.currentTimeMillis()) }
                showSupportSheet = true
            },
            onDismiss = {
                dismissedLocally = true
                coroutineScope.launch { appPrefs.setRatePromptLastDismissed(System.currentTimeMillis()) }
            },
            modifier = modifier
        )
    }

    if (showSupportSheet) {
        ErrorSupportBottomSheet(
            errorInfo = SupportErrorInfo(
                title = stringResource(R.string.support_bottom_sheet_title),
                message = stringResource(R.string.support_bottom_sheet_desc),
                errorType = "USER_FEEDBACK"
            ),
            onDismiss = { showSupportSheet = false }
        )
    }
}
