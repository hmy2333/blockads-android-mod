package app.pwhs.blockads.ui.browser.util

/**
 * Helper generating JavaScript code for handling Picture-in-Picture on web videos.
 */
object BrowserPipHelper {
    fun getPipToggleScript(isInPipMode: Boolean): String {
        return if (isInPipMode) {
            """
            (function() {
                if (window.__blockads_set_pip) {
                    window.__blockads_set_pip(true);
                }
                var style = document.getElementById('__blockads_pip_style');
                if (!style) {
                    style = document.createElement('style');
                    style.id = '__blockads_pip_style';
                    style.textContent = `
                        html.__blockads_pip_active,
                        html.__blockads_pip_active body {
                            overflow: hidden !important;
                            background: #000 !important;
                            margin: 0 !important;
                            padding: 0 !important;
                        }
                        html.__blockads_pip_active body #player-container-id,
                        html.__blockads_pip_active body #player,
                        html.__blockads_pip_active body #movie_player,
                        html.__blockads_pip_active body div[class*="html5-video-player"] {
                            position: fixed !important;
                            top: 0 !important;
                            left: 0 !important;
                            width: 100vw !important;
                            height: 100vh !important;
                            max-width: 100vw !important;
                            max-height: 100vh !important;
                            transform: none !important;
                            z-index: 2147483647 !important;
                            background: #000 !important;
                            margin: 0 !important;
                            padding: 0 !important;
                        }
                        html.__blockads_pip_active body video {
                            position: fixed !important;
                            top: 0 !important;
                            left: 0 !important;
                            width: 100vw !important;
                            height: 100vh !important;
                            max-width: 100vw !important;
                            max-height: 100vh !important;
                            object-fit: contain !important;
                            z-index: 2147483647 !important;
                            background: #000 !important;
                            margin: 0 !important;
                            padding: 0 !important;
                        }
                        html:not(.__blockads_pip_active) body #movie_player video,
                        html:not(.__blockads_pip_active) body div.html5-video-player video {
                            top: 0px !important;
                        }
                    `;
                    (document.head || document.documentElement).appendChild(style);
                }
                document.documentElement.classList.add('__blockads_pip_active');
                var v = document.querySelector('video');
                if (v && v.paused) {
                    v.play().catch(function(){});
                }
            })();
            """.trimIndent()
        } else {
            """
            (function() {
                if (window.__blockads_set_pip) {
                    window.__blockads_set_pip(false);
                }
                document.documentElement.classList.remove('__blockads_pip_active');
                var v = document.querySelector('video');
                if (v) {
                    v.style.top = '0px';
                    v.style.left = '0px';
                }
                var mp = document.querySelector('#movie_player');
                if (mp && typeof mp.wakeUp === 'function') {
                    mp.wakeUp();
                }
                window.dispatchEvent(new Event('resize'));
            })();
            """.trimIndent()
        }
    }
}
