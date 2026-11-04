package app.pwhs.blockads.ui.browser.media

import android.webkit.WebView

/**
 * Handles communication between BrowserMediaCoordinator and the active WebView.
 */
object BrowserMediaBridge {

    fun attach(webView: WebView?) {
        val wv = webView ?: return
        BrowserMediaCoordinator.actionListener = object : BrowserMediaCoordinator.MediaActionListener {
            override fun onPlay() {
                wv.post {
                    wv.evaluateJavascript(
                        """
                        (function() {
                            if (window.__blockads_set_user_paused) {
                                window.__blockads_set_user_paused(false);
                            } else if (window.__blockads_trigger_user_action) {
                                window.__blockads_trigger_user_action();
                            }
                            var mp = document.querySelector('#movie_player');
                            if (mp && typeof mp.playVideo === 'function') {
                                try { mp.playVideo(); } catch(e) {}
                            }
                            var v = document.querySelector('video, audio');
                            if (v) {
                                try { v.play().catch(function(){}); } catch(e) {}
                                v.dispatchEvent(new Event('canplay'));
                                v.dispatchEvent(new Event('playing'));
                                v.dispatchEvent(new Event('timeupdate'));
                            }
                            if (typeof window.__blockads_report_video === 'function') {
                                setTimeout(window.__blockads_report_video, 150);
                            }
                        })();
                        """.trimIndent(),
                        null
                    )
                }
            }

            override fun onPause() {
                wv.post {
                    wv.evaluateJavascript(
                        """
                        (function() {
                            if (window.__blockads_set_user_paused) {
                                window.__blockads_set_user_paused(true);
                            } else if (window.__blockads_trigger_user_action) {
                                window.__blockads_trigger_user_action();
                            }
                            var mp = document.querySelector('#movie_player');
                            if (mp && typeof mp.pauseVideo === 'function') {
                                try { mp.pauseVideo(); } catch(e) {}
                            }
                            var v = document.querySelector('video, audio');
                            if (v) {
                                try { v.pause(); } catch(e) {}
                            }
                            if (typeof window.__blockads_report_video === 'function') {
                                setTimeout(window.__blockads_report_video, 150);
                            }
                        })();
                        """.trimIndent(),
                        null
                    )
                }
            }

            override fun onSkipNext() {
                wv.post {
                    wv.evaluateJavascript(
                        """
                        (function() {
                            if (window.__blockads_trigger_user_action) window.__blockads_trigger_user_action();
                            var mp = document.querySelector('#movie_player');
                            if (mp && typeof mp.nextVideo === 'function') {
                                try { mp.nextVideo(); } catch(e) {}
                            } else {
                                var v = document.querySelector('video, audio');
                                if (v) v.currentTime = Math.min(v.duration || 0, (v.currentTime || 0) + 10);
                            }
                            if (typeof window.__blockads_report_video === 'function') {
                                setTimeout(window.__blockads_report_video, 250);
                            }
                        })();
                        """.trimIndent(),
                        null
                    )
                }
            }

            override fun onSkipPrev() {
                wv.post {
                    wv.evaluateJavascript(
                        """
                        (function() {
                            if (window.__blockads_trigger_user_action) window.__blockads_trigger_user_action();
                            var mp = document.querySelector('#movie_player');
                            if (mp && typeof mp.previousVideo === 'function') {
                                try { mp.previousVideo(); } catch(e) {}
                            } else {
                                var v = document.querySelector('video, audio');
                                if (v) v.currentTime = Math.max(0, (v.currentTime || 0) - 10);
                            }
                            if (typeof window.__blockads_report_video === 'function') {
                                setTimeout(window.__blockads_report_video, 250);
                            }
                        })();
                        """.trimIndent(),
                        null
                    )
                }
            }

            override fun onSeekTo(positionMs: Long) {
                wv.post {
                    val sec = positionMs / 1000.0
                    wv.evaluateJavascript(
                        """
                        (function() {
                            var sec = $sec;
                            var mp = document.querySelector('#movie_player');
                            if (mp && typeof mp.seekTo === 'function') {
                                try { mp.seekTo(sec, true); } catch(e) {}
                            } else {
                                var v = document.querySelector('video, audio');
                                if (v) v.currentTime = sec;
                            }
                            if (typeof window.__blockads_report_video === 'function') {
                                setTimeout(window.__blockads_report_video, 200);
                            }
                        })();
                        """.trimIndent(),
                        null
                    )
                }
            }
        }
    }

    fun detach() {
        BrowserMediaCoordinator.actionListener = null
    }
}
