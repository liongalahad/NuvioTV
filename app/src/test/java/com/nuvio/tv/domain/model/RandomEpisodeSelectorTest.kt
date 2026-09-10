package com.nuvio.tv.domain.model

import android.content.Context
import com.nuvio.tv.data.local.RandomEpisodeSettings
import com.nuvio.tv.ui.screens.detail.MetaDetailsUiState
import com.nuvio.tv.ui.screens.detail.randomEpisodeDetailState
import com.nuvio.tv.ui.screens.home.ContinueWatchingItem
import com.nuvio.tv.ui.screens.home.NextUpInfo
import com.nuvio.tv.ui.screens.home.continueWatchingItemKey
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class RandomEpisodeSelectorTest {
    private val selector = RandomEpisodeSelector()
    private val videos = (1..8).map { video(it) }
    private fun video(e: Int, s: Int = 1) = Video("show:$s:$e", "Episode $e", "2020-01-01", null,
        season = s, episode = e, overview = null)
    private fun draw(selection: RandomEpisodeSelector.Selection = RandomEpisodeSelector.Selection(),
        pool: List<Video> = videos, unwatched: Boolean = false, watched: Set<Pair<Int, Int>> = emptySet(),
        current: Pair<Int, Int>? = null, show: String = "show", profile: Int = 1) =
        selector.select(profile, show, pool, unwatched, watched, selection, current)

    @Test fun filtersSpecialsInvalidUnavailableAndFutureEpisodes() {
        val pool = listOf(video(1), video(2, 0), video(0), video(3).copy(id = " "),
            video(4).copy(available = false), video(5).copy(released = "2999-01-01"),
            video(6).copy(released = null), video(7).copy(released = "unknown"), video(1))
        assertEquals(listOf(1, 6, 7), RandomEpisodeSelector.eligible(pool, LocalDate.of(2026, 1, 1)).map { it.episode })
    }

    @Test fun allUsesEveryEpisodeBeforeRepeatingAndAvoidsCycleBoundaryRepeat() {
        var previous: String? = null
        repeat(5) {
            val cycle = (1..8).map { draw()!!.id }
            assertEquals(8, cycle.toSet().size)
            assertNotEquals(previous, cycle.first())
            previous = cycle.last()
        }
    }

    @Test fun visitSurvivesRecompositionAndLateCatalogueExpansion() {
        val visit = RandomEpisodeSelector.Selection()
        val first = draw(visit, videos.take(3))
        repeat(30) { assertEquals(first, draw(visit)) }
        assertNotEquals(first, draw())
    }

    @Test fun removesASelectionThatBecomesUnavailable() {
        val visit = RandomEpisodeSelector.Selection()
        val first = draw(visit)!!
        assertNotEquals(first, draw(visit, videos.filterNot { it.id == first.id }))
    }

    @Test fun unwatchedExcludesCompletedAndStopsWhenExhausted() {
        val completed = videos.dropLast(1).map { it.season!! to it.episode!! }.toSet()
        assertEquals(videos.last(), draw(unwatched = true, watched = completed))
        assertNull(draw(unwatched = true, watched = completed + (1 to 8)))
        assertNull(draw(unwatched = true, watched = completed, current = 1 to 8))
    }

    @Test fun explicitEpisodeIsExcludedFromContinuationAndAllDoesNotExcludeWatched() {
        val completed = videos.map { it.season!! to it.episode!! }.toSet()
        repeat(20) { assertNotEquals(1, draw(watched = completed, current = 1 to 1)!!.episode) }
        assertNull(draw(pool = listOf(video(1)), current = 1 to 1))
    }

    @Test fun poolsShowsAndProfilesHaveIndependentBags() {
        val cycles = List(4) { mutableSetOf<String>() }
        repeat(8) {
            cycles[0].add(draw()!!.id)
            cycles[1].add(draw(show = "other")!!.id)
            cycles[2].add(draw(profile = 2)!!.id)
            cycles[3].add(draw(unwatched = true)!!.id)
        }
        cycles.forEach { assertEquals(videos.map { it.id }.toSet(), it) }
    }

    @Test fun browsingDoesNotConsumeThePlaybackBag() {
        val played = mutableSetOf<String>()
        repeat(8) {
            repeat(3) { draw(RandomEpisodeSelector.Selection("home")); draw(RandomEpisodeSelector.Selection("detail")) }
            played.add(draw()!!.id)
        }
        assertEquals(8, played.size)
    }

    private val context = mockk<Context>(relaxed = true)
    private val meta = mockk<Meta> {
        every { id } returns "show"
        every { imdbId } returns null
        every { apiType } returns "series"
        every { videos } returns this@RandomEpisodeSelectorTest.videos
    }
    private val native = NextToWatch(null, false, videos.first().id, 1, 1, "Play S1 E1")
    private val state = MetaDetailsUiState(meta = meta, nextToWatch = native)
    private val enabled = RandomEpisodeSettings(enabledShows = setOf("show"))
    private fun detail(settings: RandomEpisodeSettings = enabled, input: MetaDetailsUiState = state,
        resume: List<WatchProgress> = emptyList(), visit: RandomEpisodeSelector.Selection = RandomEpisodeSelector.Selection()) =
        randomEpisodeDetailState(input, settings, resume, selector, visit, context)
    private fun progress(e: Int, percent: Float = 50f) = WatchProgress("show", "series", "Show", null,
        null, null, video(e).id, 1, e, "Episode $e", 0, 0, e.toLong(), progressPercent = percent)

    @Test fun globalOffRetainsChoicesAndRestoresNativePlay() {
        val off = enabled.copy(enabled = false, unwatchedShows = setOf("show"))
        val result = detail(off)
        assertEquals(native, result.nextToWatch)
        assertFalse(result.randomEpisodeAvailable)
        assertFalse(result.randomEpisodeEnabled)
        assertTrue(result.randomEpisodeUnwatchedOnly)
        assertEquals(setOf("show"), off.enabledShows)
    }

    @Test fun showOffRestoresNativePlayAndDoesNotEnableOtherShowsOrMovies() {
        assertEquals(native, detail(enabled.copy(enabledShows = setOf("other"))).nextToWatch)
        assertFalse(enabled.isEnabled("show", "movie"))
        assertTrue(enabled.isEnabled("show", "tv"))
    }

    @Test fun continueWatchingWinsOverStaleNativeResumeForLabelAndRoute() {
        val stale = state.copy(nextToWatch = native.copy(isResume = true, nextEpisode = 2))
        val result = detail(input = stale, resume = listOf(progress(4)))
        assertEquals(4, result.nextToWatch?.nextEpisode)
        assertEquals(video(4).id, result.nextToWatch?.nextVideoId)
        assertTrue(result.nextToWatch!!.isResume)
        assertEquals(progress(4), result.nextToWatch?.watchProgress)
    }

    @Test fun removedOrCompletedResumeCannotBeResurrected() {
        val stale = state.copy(nextToWatch = native.copy(isResume = true))
        assertFalse(detail(input = stale).nextToWatch!!.isResume)
        assertFalse(detail(input = stale, resume = listOf(progress(1, 90f))).nextToWatch!!.isResume)
        assertTrue(detail(input = stale, resume = listOf(progress(1, 89.9f))).nextToWatch!!.isResume)
    }

    @Test fun exhaustedPoolStopsPlayButPartialEpisodeRemainsResumable() {
        val complete = state.copy(watchedEpisodes = videos.map { it.season!! to it.episode!! }.toSet())
        val settings = enabled.copy(unwatchedShows = setOf("show"))
        val empty = detail(settings, complete)
        assertTrue(empty.randomEpisodePoolEmpty)
        assertNull(empty.nextToWatch?.nextVideoId)
        val resume = detail(settings, complete, listOf(progress(2)))
        assertFalse(resume.randomEpisodePoolEmpty)
        assertEquals(2, resume.nextToWatch?.nextEpisode)
    }

    @Test fun randomHomeCardKeepsItsFocusIdentityWhenTheEpisodeChanges() {
        val info = NextUpInfo("show", "series", "Show", null, null, null, "show:1:1",
            1, 1, "First", thumbnail = null, lastWatched = 0, sortTimestamp = 0)
        val first = ContinueWatchingItem.NextUp(info, randomPlayback = true)
        val next = first.copy(info = info.copy(videoId = "show:1:2", episode = 2))
        assertEquals(continueWatchingItemKey(first), continueWatchingItemKey(next))
        assertNotEquals(continueWatchingItemKey(first.copy(randomPlayback = false)),
            continueWatchingItemKey(next.copy(randomPlayback = false)))
    }
}
