package org.jmouse.project.kitsu.tv

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import org.jmouse.project.kitsu.tv.ui.KitsuApp
import org.jmouse.project.kitsu.tv.ui.theme.AccentStore
import org.jmouse.project.kitsu.tv.ui.theme.KitsuTvTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        /*
          ⚠️ THE SCREEN STAYS ON WHILE KITSU IS IN FRONT, not only while something is playing.

          The player held the flag on its own view for the duration of playback, so the moment a film
          ended — or somebody stopped to read a series and think — the television was idle as far as
          the system was concerned, and the manufacturer's screensaver took the panel. This
          household's set answered that with a music channel, which reads as the application having
          done something rather than as the television doing what it was configured to.

          ⚠️ A WINDOW FLAG, so it is released the instant Kitsu goes to the background. It is not a
          wake lock and holds nothing when the application is not on screen: leaving Kitsu, or the
          set-top box sleeping, ends it with no code of ours running. The player's own
          `WAKE_MODE_NETWORK` is a different thing again and stays — it keeps the radio up mid-stream.
         */
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        /* ⚠️ BEFORE setContent, not inside it. Restoring the accent during composition would paint
           the first frame in the default and swap — the same flash the browser's pre-paint script
           exists to avoid, and far more visible on a television than on a phone. */
        AccentStore.restore(this)

        setContent {
            KitsuTvTheme {
                KitsuApp()
            }
        }
    }
}
