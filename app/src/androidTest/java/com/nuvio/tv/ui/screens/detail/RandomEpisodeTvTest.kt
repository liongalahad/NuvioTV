package com.nuvio.tv.ui.screens.detail

import android.content.Context
import android.content.ContextWrapper
import android.os.SystemClock
import android.view.KeyEvent
import android.view.ViewConfiguration
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.*
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.nuvio.tv.core.profile.ProfileManager
import com.nuvio.tv.data.local.ProfileDataStore
import com.nuvio.tv.data.local.ProfileDataStoreFactory
import com.nuvio.tv.data.local.RandomEpisodeDataStore
import com.nuvio.tv.domain.model.*
import com.nuvio.tv.ui.theme.NuvioTheme
import com.squareup.moshi.Moshi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class RandomEpisodeTvTest {
    @get:Rule val compose = createComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val meta = Meta(id = "random-qa", type = ContentType.SERIES, name = "Random Episode QA",
        poster = null, posterShape = PosterShape.POSTER, background = null, logo = null,
        description = null, releaseInfo = null, imdbRating = null, genres = emptyList(), runtime = null,
        director = emptyList(), cast = emptyList(), videos = emptyList(), country = null,
        awards = null, language = null, links = emptyList())

    @Test fun dpadTogglePoolCommitAndBackCancellation() {
        var enabled by mutableStateOf(false)
        var unwatched by mutableStateOf(false)
        var plays = 0
        compose.setContent {
            NuvioTheme {
                HeroContentSection(meta, null, null, onPlayClick = { plays++ },
                    isInLibrary = false, onToggleLibrary = {}, onLibraryLongPress = {},
                    isMovieWatched = false, isMovieWatchedPending = false, onToggleMovieWatched = {},
                    randomEpisodeAvailable = true, randomEpisodeEnabled = enabled,
                    randomEpisodeUnwatchedOnly = unwatched, onToggleRandomEpisode = { enabled = !enabled },
                    onRandomEpisodePoolSelected = { unwatched = it; enabled = true })
            }
        }
        val toggle = compose.onNodeWithContentDescription("Enable random playback")
        val activity = compose.runOnIdle {
            ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).single()
        }
        toggle.performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER)
        compose.waitUntil { enabled }
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER)
        compose.waitUntil { !enabled }
        for (key in listOf(KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER)) {
            // Navigate while the opening key is still held; releasing it must not select anything.
            val downTime = SystemClock.uptimeMillis()
            fun sendHeldKey(action: Int, repeat: Int = 0) = instrumentation.sendKeySync(
                KeyEvent(downTime, SystemClock.uptimeMillis(), action, key, repeat, 0))
            sendHeldKey(KeyEvent.ACTION_DOWN)
            SystemClock.sleep(ViewConfiguration.getLongPressTimeout().toLong() + 50)
            sendHeldKey(KeyEvent.ACTION_DOWN, 1)
            compose.onNodeWithText("Choose episodes for shuffle playback.").assertIsDisplayed()
            sendHeldKey(KeyEvent.ACTION_DOWN, 2)
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN)
            compose.onNodeWithText("Unwatched only").assertIsFocused()
            sendHeldKey(KeyEvent.ACTION_UP)
            compose.onNodeWithText("Choose episodes for shuffle playback.").assertIsDisplayed()
            compose.onNodeWithText("Unwatched only").assertIsFocused()
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
            compose.waitForIdle()
            assertFalse(enabled)
            assertFalse(unwatched)
            toggle.assertIsFocused()
            compose.waitUntil { compose.runOnIdle { activity.hasWindowFocus() } }
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER)
            compose.waitUntil { enabled }
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER)
            compose.waitUntil { !enabled }
        }
        // Menu is the native remote alternative to holding OK.
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_MENU)
        compose.onNodeWithText("Unwatched only").assertIsDisplayed()
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN)
        compose.waitForIdle()
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER)
        compose.waitUntil { enabled && unwatched }
        assertEquals(0, plays)
        compose.onNodeWithContentDescription("Disable random playback").assertIsFocused()
        compose.waitUntil { compose.runOnIdle { activity.hasWindowFocus() } }
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_MENU)
        compose.onNodeWithText("Unwatched only").assertIsFocused()
    }

    @Test fun emptyPoolCannotPlayAndMovieHasNoShuffleControl() {
        var plays = 0
        compose.setContent {
            NuvioTheme {
                HeroContentSection(meta.copy(type = ContentType.MOVIE, rawType = "movie"), null,
                    NextToWatch(null, false, null, null, null, "No eligible episodes"),
                    onPlayClick = { plays++ }, isInLibrary = false, onToggleLibrary = {},
                    onLibraryLongPress = {}, isMovieWatched = false, isMovieWatchedPending = false,
                    onToggleMovieWatched = {}, randomEpisodePoolEmpty = true)
            }
        }
        compose.onNodeWithText("No eligible episodes").performClick()
        assertEquals(0, plays)
        compose.onNodeWithContentDescription("Enable random playback").assertDoesNotExist()
    }

    @Test fun androidPreferencesPersistIndependentChoicesWithoutGlobalReset() = runBlocking {
        val isolated = object : ContextWrapper(context) {
            private val directory = File(context.cacheDir, "random-episode-${System.nanoTime()}").apply { mkdirs() }
            override fun getApplicationContext(): Context = this
            override fun getFilesDir(): File = directory
        }
        val factory = ProfileDataStoreFactory(isolated)
        val profiles = ProfileManager(ProfileDataStore(context, Moshi.Builder().build()), factory, emptySet(), context)
        val store = RandomEpisodeDataStore(factory, profiles)
        val profile = profiles.activeProfileId.value
        assertTrue(store.settingsForProfile(profile).first().enabled)
        store.selectPool("a", true)
        store.toggleShow("b")
        store.setEnabled(false)
        val restored = RandomEpisodeDataStore(factory, profiles).settingsForProfile(profile).first()
        assertFalse(restored.enabled)
        assertEquals(setOf("a", "b"), restored.enabledShows)
        assertEquals(setOf("a"), restored.unwatchedShows)
        assertTrue(store.settingsForProfile(999).first().enabledShows.isEmpty())
        store.setEnabled(true)
        store.toggleShow("a")
        assertEquals(setOf("b"), store.settingsForProfile(profile).first().enabledShows)
        assertEquals(setOf("a"), store.settingsForProfile(profile).first().unwatchedShows)
    }
}
