package app.pwhs.blockads.ui.browser.media

import android.content.Context
import android.content.Intent
import android.os.Build

/**
 * Coordinates media playback states between WebView and the background BrowserMediaService.
 */
object BrowserMediaCoordinator {

    interface MediaActionListener {
        fun onPlay()
        fun onPause()
        fun onSkipNext()
        fun onSkipPrev()
        fun onSeekTo(positionMs: Long)
    }

    var actionListener: MediaActionListener? = null

    @Volatile
    var currentTitle: String = ""
        private set

    @Volatile
    var currentArtist: String = ""
        private set

    @Volatile
    var currentArtworkUrl: String = ""
        private set

    @Volatile
    var isPlaying: Boolean = false
        private set

    @Volatile
    var positionMs: Long = 0L
        private set

    @Volatile
    var durationMs: Long = 0L
        private set

    @Volatile
    private var isServiceActive: Boolean = false

    fun updateMediaState(
        context: Context,
        title: String,
        artist: String,
        artworkUrl: String = "",
        playing: Boolean,
        posMs: Long,
        durMs: Long
    ) {
        val titleChanged = title.isNotBlank() && title != currentTitle
        val artistChanged = artist.isNotBlank() && artist != currentArtist
        val artworkChanged = artworkUrl.isNotBlank() && artworkUrl != currentArtworkUrl
        val playingChanged = playing != isPlaying
        val posDiff = kotlin.math.abs(posMs - positionMs)
        val shouldUpdatePos = posDiff > 3000L

        if (title.isNotBlank()) currentTitle = title
        if (artist.isNotBlank()) currentArtist = artist
        if (artworkUrl.isNotBlank()) currentArtworkUrl = artworkUrl
        isPlaying = playing
        positionMs = posMs
        durationMs = durMs

        val shouldNotify = (playing && !isServiceActive) ||
                playingChanged ||
                titleChanged ||
                artistChanged ||
                artworkChanged ||
                shouldUpdatePos

        // Only start or update service when there is an active media track
        if (currentTitle.isNotBlank() && shouldNotify) {
            val intent = Intent(context, BrowserMediaService::class.java).apply {
                action = BrowserMediaService.ACTION_UPDATE_STATE
                putExtra(BrowserMediaService.EXTRA_TITLE, currentTitle)
                putExtra(BrowserMediaService.EXTRA_ARTIST, currentArtist)
                putExtra(BrowserMediaService.EXTRA_ARTWORK_URL, currentArtworkUrl)
                putExtra(BrowserMediaService.EXTRA_IS_PLAYING, isPlaying)
                putExtra(BrowserMediaService.EXTRA_POSITION_MS, positionMs)
                putExtra(BrowserMediaService.EXTRA_DURATION_MS, durationMs)
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
                isServiceActive = true
            } catch (_: Exception) {
                // Ignore background start restrictions if activity is finishing
            }
        }
    }

    fun stopMedia(context: Context) {
        isPlaying = false
        currentTitle = ""
        currentArtist = ""
        currentArtworkUrl = ""
        positionMs = 0L
        durationMs = 0L
        isServiceActive = false
        val intent = Intent(context, BrowserMediaService::class.java).apply {
            action = BrowserMediaService.ACTION_STOP
        }
        try {
            context.startService(intent)
        } catch (_: Exception) {}
    }
}
