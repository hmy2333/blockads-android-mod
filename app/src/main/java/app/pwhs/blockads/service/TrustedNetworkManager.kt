package app.pwhs.blockads.service

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import app.pwhs.blockads.R
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import app.pwhs.blockads.data.datastore.AppPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber

/**
 * Trusted Wi-Fi networks (#197).
 *
 * Watches the connected Wi-Fi SSID and, when "Pause on trusted networks" is
 * enabled, auto-pauses BlockAds on a trusted SSID and auto-resumes when the
 * device leaves it. Registered once from [BlockAdsApplication] so it runs for
 * the whole process lifetime.
 *
 * Limitation: auto-resume only fires while the app process is alive. While
 * protection is ON the foreground service keeps it alive, so entering a
 * trusted network reliably pauses; resuming after the process was killed
 * happens on next app launch / boot. Documented in the UI.
 */
class TrustedNetworkManager(
    private val context: Context,
    private val appPrefs: AppPreferences,
) {
    private val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Mutex()
    private var registered = false

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = evaluate()
        override fun onLost(network: Network) = evaluate()
        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) = evaluate()
    }

    fun start() {
        if (registered) return
        try {
            val request = NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .build()
            cm.registerNetworkCallback(request, callback)
            registered = true
            Timber.d("TrustedNetworkManager started")
            evaluate()
        } catch (e: Exception) {
            Timber.e(e, "TrustedNetworkManager register failed")
        }

        // Safety net: Wi-Fi callbacks can fire before the SSID is readable, and
        // some ROMs then never send another one, leaving the feature stuck in
        // the state it first evaluated. A cheap periodic re-check fixes both
        // that and "permission granted later while staying on the same SSID".
        scope.launch {
            while (true) {
                delay(TRUSTED_POLL_INTERVAL_MS)
                evaluate()
            }
        }

        // The VPN being started/stopped is itself a state change that must be
        // re-evaluated: switching routing mode restarts the service without
        // touching Wi-Fi, and relying on network callbacks alone missed it.
        scope.launch {
            AdBlockVpnService.state.collect { evaluate() }
        }
        scope.launch {
            RootProxyService.state.collect { evaluate() }
        }

        // Re-evaluate when the settings change (toggle flipped or an SSID
        // added/removed) — otherwise enabling the feature while already
        // connected to a trusted network wouldn't pause until the next
        // network change.
        scope.launch {
            combine(
                appPrefs.pauseOnTrustedEnabled,
                appPrefs.trustedSsids
            ) { enabled, ssids -> enabled to ssids }
                .distinctUntilChanged()
                .collect { evaluate() }
        }
    }

    private fun evaluate() {
        scope.launch {
            lock.withLock {
                try {
                    // If the feature was turned off (or all SSIDs removed) while
                    // we had auto-paused, resume protection and clear the notice.
                    val featureOn = appPrefs.getPauseOnTrustedEnabledSnapshot()
                    val trusted = appPrefs.getTrustedSsidsSnapshot()
                    if (!featureOn || trusted.isEmpty()) {
                        if (appPrefs.getPausedByTrustedSnapshot() &&
                            !(AdBlockVpnService.isRunning || RootProxyService.isRunning)
                        ) {
                            appPrefs.setPausedByTrusted(false)
                            cancelPausedNotification()
                            ServiceController.requestStart(context)
                        }
                        return@withLock
                    }

                    val access = hasSsidAccess(context)
                    val ssid = currentSsid(context)
                    val onTrusted = ssid != null && ssid in trusted
                    val running = AdBlockVpnService.isRunning || RootProxyService.isRunning
                    val pausedByUs = appPrefs.getPausedByTrustedSnapshot()
                    // Logcat-visible in release builds too: without this the
                    // feature fails silently when the SSID cannot be read.
                    Log.i(
                        TAG,
                        "evaluate ssidAccess=$access ssid=$ssid trusted=$trusted " +
                            "onTrusted=$onTrusted running=$running pausedByUs=$pausedByUs"
                    )

                    when {
                        // Entered a trusted network while protected → pause.
                        onTrusted && running -> {
                            Timber.d("Trusted network '$ssid' — pausing BlockAds")
                            appPrefs.setPausedByTrusted(true, ssid)
                            ServiceController.requestStop(context)
                            showPausedNotification(ssid)
                        }
                        // Left the trusted network and we had paused → resume.
                        !onTrusted && pausedByUs && !running -> {
                            Timber.d("Left trusted network (now '$ssid') — resuming BlockAds")
                            appPrefs.setPausedByTrusted(false)
                            cancelPausedNotification()
                            ServiceController.requestStart(context)
                        }
                        // User manually started while on a trusted net, or any
                        // other state — clear the stale pause flag.
                        running && pausedByUs -> {
                            appPrefs.setPausedByTrusted(false)
                            cancelPausedNotification()
                        }
                    }
                } catch (e: Exception) {
                    Timber.e(e, "TrustedNetworkManager evaluate failed")
                }
            }
        }
    }

    private fun showPausedNotification(ssid: String) {
        try {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                nm.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_ID,
                        context.getString(R.string.trusted_networks_title),
                        NotificationManager.IMPORTANCE_LOW
                    ).apply { setShowBadge(false) }
                )
            }
            val tapIntent = PendingIntent.getActivity(
                context, 0,
                android.content.Intent(context, app.pwhs.blockads.MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            val text = if (ssid.isNotEmpty())
                context.getString(R.string.trusted_networks_paused_text, ssid)
            else context.getString(R.string.trusted_networks_paused_text_generic)
            val n = androidx.core.app.NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_shield_off)
                .setContentTitle(context.getString(R.string.trusted_networks_paused_title))
                .setContentText(text)
                .setStyle(androidx.core.app.NotificationCompat.BigTextStyle().bigText(text))
                .setOngoing(false)
                .setAutoCancel(false)
                .setContentIntent(tapIntent)
                .build()
            nm.notify(NOTIFICATION_ID, n)
        } catch (e: Exception) {
            Timber.w(e, "showPausedNotification failed")
        }
    }

    private fun cancelPausedNotification() {
        try {
            (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .cancel(NOTIFICATION_ID)
        } catch (_: Exception) {
        }
    }

    companion object {
        private const val CHANNEL_ID = "blockads_trusted_channel"
        private const val NOTIFICATION_ID = 30

        /**
         * Returns the connected Wi-Fi SSID (without surrounding quotes), or null
         * if not on Wi-Fi / unavailable / location permission missing.
         */
        /**
         * Whether the SSID can be read at all.
         *
         * Only ACCESS_FINE_LOCATION works here. NEARBY_WIFI_DEVICES declared
         * with neverForLocation makes the system strip the SSID (verified on
         * HyperOS), so it is deliberately not used.
         */
        fun hasSsidAccess(context: Context): Boolean =
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED

        const val TAG = "BlockAdsTrustedNet"

        /** How often the connected SSID is re-checked as a fallback. */
        private const val TRUSTED_POLL_INTERVAL_MS = 15_000L

        /** Connected to Wi-Fi, regardless of whether the SSID is readable. */
        fun isOnWifi(context: Context): Boolean = try {
            val cm = context.applicationContext
                .getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            val caps = cm?.getNetworkCapabilities(cm.activeNetwork)
            caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        } catch (e: Exception) {
            false
        }

        fun currentSsid(context: Context): String? {
            if (!hasSsidAccess(context)) {
                Log.i(TAG, "SSID unavailable: no NEARBY_WIFI_DEVICES / ACCESS_FINE_LOCATION grant")
                return null
            }
            // Prefer NetworkCapabilities.transportInfo: on Android 13+ it works
            // with NEARBY_WIFI_DEVICES alone, while the deprecated
            // WifiManager.connectionInfo path keeps returning "<unknown ssid>"
            // unless the location permission is also granted.
            try {
                // Walk every network instead of only the active one: while the
                // VPN is up, activeNetwork is the VPN, so the Wi-Fi transport
                // (and its WifiInfo) is invisible from there.
                val connectivity =
                    context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                for (network in connectivity?.allNetworks.orEmpty()) {
                    val caps = connectivity?.getNetworkCapabilities(network) ?: continue
                    if (!caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) continue
                    val info = caps.transportInfo as? WifiInfo
                    val ssid = info?.ssid?.trim('"')
                    if (!ssid.isNullOrEmpty() && ssid != "<unknown ssid>") {
                        return ssid
                    }
                }
            } catch (e: Exception) {
                Timber.w(e, "currentSsid via NetworkCapabilities failed")
            }

            return try {
                val wifi = context.applicationContext
                    .getSystemService(Context.WIFI_SERVICE) as? WifiManager ?: return null
                @Suppress("DEPRECATION")
                val raw = wifi.connectionInfo?.ssid ?: return null
                val ssid = raw.trim('"')
                if (ssid.isEmpty() || ssid == "<unknown ssid>") null else ssid
            } catch (e: Exception) {
                Timber.w(e, "currentSsid failed")
                null
            }
        }
    }
}
