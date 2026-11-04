package app.pwhs.blockads.data.network

import android.net.TrafficStats
import android.os.SystemClock
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

data class NetworkSpeed(
    val downloadBps: Long = 0L,
    val uploadBps: Long = 0L,
    val totalDownloadedBytes: Long = 0L,
    val totalUploadedBytes: Long = 0L,
    val downloadHistory: List<Float> = emptyList(),
    val uploadHistory: List<Float> = emptyList()
)

object NetworkSpeedMonitor {
    private const val HISTORY_SIZE = 10

    fun observeNetworkSpeed(intervalMs: Long = 1000L): Flow<NetworkSpeed> = flow {
        val startRx = TrafficStats.getTotalRxBytes()
        val startTx = TrafficStats.getTotalTxBytes()
        var lastRx = startRx
        var lastTx = startTx
        var lastTime = SystemClock.elapsedRealtime()

        val rxHistory = ArrayDeque<Long>(HISTORY_SIZE)
        val txHistory = ArrayDeque<Long>(HISTORY_SIZE)

        repeat(HISTORY_SIZE) {
            rxHistory.add(0L)
            txHistory.add(0L)
        }

        emit(NetworkSpeed())

        while (true) {
            delay(intervalMs)
            val now = SystemClock.elapsedRealtime()
            val currentRx = TrafficStats.getTotalRxBytes()
            val currentTx = TrafficStats.getTotalTxBytes()

            val dt = ((now - lastTime).coerceAtLeast(1)) / 1000f

            val rxDiff = if (lastRx != TrafficStats.UNSUPPORTED.toLong() && currentRx >= lastRx) {
                currentRx - lastRx
            } else 0L

            val txDiff = if (lastTx != TrafficStats.UNSUPPORTED.toLong() && currentTx >= lastTx) {
                currentTx - lastTx
            } else 0L

            val rxSpeed = (rxDiff / dt).toLong()
            val txSpeed = (txDiff / dt).toLong()

            val downloaded = if (startRx != TrafficStats.UNSUPPORTED.toLong() && currentRx >= startRx) {
                currentRx - startRx
            } else 0L

            val uploaded = if (startTx != TrafficStats.UNSUPPORTED.toLong() && currentTx >= startTx) {
                currentTx - startTx
            } else 0L

            lastRx = currentRx
            lastTx = currentTx
            lastTime = now

            if (rxHistory.size >= HISTORY_SIZE) rxHistory.removeFirst()
            rxHistory.add(rxSpeed)

            if (txHistory.size >= HISTORY_SIZE) txHistory.removeFirst()
            txHistory.add(txSpeed)

            val maxRx = rxHistory.maxOrNull()?.coerceAtLeast(1L) ?: 1L
            val maxTx = txHistory.maxOrNull()?.coerceAtLeast(1L) ?: 1L

            val normRx = if (maxRx <= 1024L) {
                emptyList()
            } else {
                rxHistory.map {
                    if (it == 0L) 0.15f
                    else (it.toFloat() / maxRx).coerceIn(0.18f, 0.85f)
                }
            }

            val normTx = if (maxTx <= 1024L) {
                emptyList()
            } else {
                txHistory.map {
                    if (it == 0L) 0.15f
                    else (it.toFloat() / maxTx).coerceIn(0.18f, 0.85f)
                }
            }

            emit(
                NetworkSpeed(
                    downloadBps = rxSpeed,
                    uploadBps = txSpeed,
                    totalDownloadedBytes = downloaded,
                    totalUploadedBytes = uploaded,
                    downloadHistory = normRx,
                    uploadHistory = normTx
                )
            )
        }
    }
}
