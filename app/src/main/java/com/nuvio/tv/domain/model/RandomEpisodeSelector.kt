package com.nuvio.tv.domain.model

import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/** Per-show shuffle, adapted from DeclanSC's feature and the Morphe port. */
@Singleton
class RandomEpisodeSelector @Inject constructor() {
    private data class Bag(val used: MutableSet<Pair<Int, Int>> = mutableSetOf(), var last: Pair<Int, Int>? = null)
    private val bags = object : LinkedHashMap<String, Bag>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Bag>?) = size > 128
    }

    /** Owned by a detail visit, Home visit, or the current player episode. */
    class Selection(internal val scope: String = "playback") {
        internal var key: String? = null
        internal var episode: Pair<Int, Int>? = null
        fun clear() { key = null; episode = null }
    }

    @Synchronized
    fun select(
        profileId: Int,
        contentId: String,
        videos: List<Video>,
        unwatchedOnly: Boolean,
        watched: Set<Pair<Int, Int>>,
        selection: Selection,
        current: Pair<Int, Int>? = null
    ): Video? {
        val key = "$profileId:${selection.scope}:$contentId:$unwatchedOnly"
        val eligible = eligible(videos).filter {
            val coordinate = it.season!! to it.episode!!
            coordinate != current && (!unwatchedOnly || coordinate !in watched)
        }.associateBy { it.season!! to it.episode!! }
        if (selection.key == key) eligible[selection.episode]?.let { return it }
        selection.clear()
        if (eligible.isEmpty()) return null
        val bag = bags.getOrPut(key) { Bag() }
        current?.let { bag.used.add(it) }
        var candidates = eligible.keys - bag.used
        if (candidates.isEmpty()) {
            bag.used.clear()
            current?.let { bag.used.add(it) }
            candidates = eligible.keys
        }
        bag.last?.let { if (candidates.size > 1) candidates = candidates - it }
        val chosen = candidates.random()
        bag.used.add(chosen)
        bag.last = chosen
        selection.key = key
        selection.episode = chosen
        return eligible.getValue(chosen)
    }

    companion object {
        fun eligible(videos: List<Video>, today: LocalDate = LocalDate.now()): List<Video> =
            videos.filter { video ->
                video.id.isNotBlank() && (video.season ?: 0) > 0 && (video.episode ?: 0) > 0 &&
                    video.available != false && hasAired(video.released, today)
            }.distinctBy { it.season to it.episode }

        // Match native date-only airing rules. Addons with no usable date remain playable.
        private fun hasAired(released: String?, today: LocalDate): Boolean =
            released?.trim()?.takeIf { it.isNotEmpty() }?.let {
                runCatching { !LocalDate.parse(it.take(10)).isAfter(today) }.getOrDefault(true)
            } ?: true
    }
}
