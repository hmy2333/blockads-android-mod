package app.pwhs.blockads.worker

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import app.pwhs.blockads.data.dao.ConfigDao
import app.pwhs.blockads.data.datastore.AppPreferences
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.flow.first
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import timber.log.Timber

class ConfigUpdateWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params), KoinComponent {

    private val configDao: ConfigDao by inject()
    private val httpClient: HttpClient by inject()
    private val appPreferences: AppPreferences by inject()

    companion object {
        const val WORK_NAME = "config_update_work"
    }

    override suspend fun doWork(): Result {
        return try {
            val wifiOnly = appPreferences.autoUpdateWifiOnly.first()
            if (wifiOnly) {
                val cm = applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                val network = cm.activeNetwork
                val capabilities = cm.getNetworkCapabilities(network)
                val isUnmeteredOrWifi = capabilities != null && (
                        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) ||
                                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
                        )

                if (!isUnmeteredOrWifi) {
                    Timber.d("Config update skipped: not on unmetered/Wi-Fi")
                    return Result.retry()
                }
            }

            val remoteConfigs = configDao.getAutoUpdateConfigs()
            if (remoteConfigs.isEmpty()) {
                Timber.d("No remote configs set for auto-update")
                return Result.success()
            }

            var updatedCount = 0
            for (cfg in remoteConfigs) {
                val url = cfg.remoteUrl ?: continue
                try {
                    val content = httpClient.get(url).bodyAsText()
                    if (content.isNotBlank() && content != cfg.content) {
                        configDao.updateContent(cfg.id, content, System.currentTimeMillis())
                        updatedCount++
                        Timber.d("Config updated: %s", cfg.name)
                    }
                } catch (e: Exception) {
                    Timber.w(e, "Failed to update remote config: %s (%s)", cfg.name, url)
                }
            }

            Timber.i("Config update completed: %d of %d configs updated", updatedCount, remoteConfigs.size)
            Result.success()
        } catch (e: Exception) {
            Timber.e(e, "Config update worker failed")
            Result.retry()
        }
    }
}
