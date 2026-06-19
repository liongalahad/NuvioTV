package com.nuvio.tv.domain.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImdbRatingVisibilityTest {

    @Test
    fun `home visibility hides or shows all home imdb ratings`() {
        assertTrue(HomeImdbRatingsVisibility.SHOW_ALL.showRatings)
        assertFalse(HomeImdbRatingsVisibility.HIDE_ALL.showRatings)
    }

    @Test
    fun `detail visibility keeps title ratings while hiding episode ratings`() {
        assertTrue(DetailImdbRatingsVisibility.SHOW_ALL.showTitleRatings)
        assertTrue(DetailImdbRatingsVisibility.SHOW_ALL.showEpisodeRatings)

        assertTrue(DetailImdbRatingsVisibility.HIDE_UNWATCHED_EPISODES.showTitleRatings)
        assertTrue(DetailImdbRatingsVisibility.HIDE_UNWATCHED_EPISODES.showEpisodeRatings)

        assertTrue(DetailImdbRatingsVisibility.HIDE_EPISODES.showTitleRatings)
        assertFalse(DetailImdbRatingsVisibility.HIDE_EPISODES.showEpisodeRatings)

        assertFalse(DetailImdbRatingsVisibility.HIDE_ALL.showTitleRatings)
        assertFalse(DetailImdbRatingsVisibility.HIDE_ALL.showEpisodeRatings)
    }

    @Test
    fun `detail visibility can hide only unwatched episode ratings`() {
        assertTrue(DetailImdbRatingsVisibility.SHOW_ALL.showEpisodeRating(isWatched = false))
        assertTrue(DetailImdbRatingsVisibility.SHOW_ALL.showEpisodeRating(isWatched = true))

        assertFalse(DetailImdbRatingsVisibility.HIDE_UNWATCHED_EPISODES.showEpisodeRating(isWatched = false))
        assertTrue(DetailImdbRatingsVisibility.HIDE_UNWATCHED_EPISODES.showEpisodeRating(isWatched = true))

        assertFalse(DetailImdbRatingsVisibility.HIDE_EPISODES.showEpisodeRating(isWatched = false))
        assertFalse(DetailImdbRatingsVisibility.HIDE_EPISODES.showEpisodeRating(isWatched = true))

        assertFalse(DetailImdbRatingsVisibility.HIDE_ALL.showEpisodeRating(isWatched = false))
        assertFalse(DetailImdbRatingsVisibility.HIDE_ALL.showEpisodeRating(isWatched = true))
    }
}
