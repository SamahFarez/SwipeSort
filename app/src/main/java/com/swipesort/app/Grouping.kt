package com.swipesort.app

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

enum class GroupBy(val label: String) {
    MONTH("Month"),
    YEAR("Year"),
    DAY("Day"),
    COUNTRY("Country"),
    CITY("Village / city"),
    COUNTRY_MONTH("Country + month"),
    ALBUM("Album"),
    NONE("No groups"),
}

enum class Kind(val label: String) {
    ALL("All"), PHOTOS("Photos"), VIDEOS("Videos"), REELS("Reels 9:16"), FAVORITES("★ Favourites")
}

data class Section(val key: String, val title: String, val subtitle: String?, val items: List<MediaItem>)

object Grouping {
    private val monthFmt = SimpleDateFormat("LLLL yyyy", Locale.getDefault())
    private val shortMonthFmt = SimpleDateFormat("LLL yy", Locale.getDefault())
    private val dayFmt = SimpleDateFormat("EEE d MMMM yyyy", Locale.getDefault())
    private val fullFmt = SimpleDateFormat("d MMMM yyyy · HH:mm", Locale.getDefault())

    private fun cap(s: String) = s.replaceFirstChar { it.titlecase(Locale.getDefault()) }

    fun monthTitle(ms: Long): String = cap(monthFmt.format(Date(ms)))
    fun shortMonth(ms: Long): String = cap(shortMonthFmt.format(Date(ms)))
    fun fullDate(ms: Long): String = fullFmt.format(Date(ms))

    private fun monthKey(ms: Long): String {
        val c = Calendar.getInstance().apply { timeInMillis = ms }
        return "%04d-%02d".format(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1)
    }

    private fun dayKey(ms: Long): String {
        val c = Calendar.getInstance().apply { timeInMillis = ms }
        return "%04d-%02d-%02d".format(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH))
    }

    private fun yearOf(ms: Long): Int = Calendar.getInstance().apply { timeInMillis = ms }.get(Calendar.YEAR)

    fun filter(items: List<MediaItem>, kind: Kind, favorites: Set<Long>): List<MediaItem> = when (kind) {
        Kind.ALL -> items
        Kind.PHOTOS -> items.filter { !it.isVideo }
        Kind.VIDEOS -> items.filter { it.isVideo }
        Kind.REELS -> items.filter { MediaRepo.isReelReady(it) }
        Kind.FAVORITES -> items.filter { it.id in favorites }
    }

    /** Free-text search across album name, place names, month and file name. */
    fun search(items: List<MediaItem>, query: String, places: Map<Long, Place>): List<MediaItem> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return items
        return items.filter { item ->
            val p = places[item.id]
            item.bucketName.lowercase().contains(q) ||
                item.name.lowercase().contains(q) ||
                (p?.country?.lowercase()?.contains(q) == true) ||
                (p?.region?.lowercase()?.contains(q) == true) ||
                (p?.city?.lowercase()?.contains(q) == true) ||
                monthTitle(item.dateTaken).lowercase().contains(q)
        }
    }

    fun group(items: List<MediaItem>, by: GroupBy, places: Map<Long, Place>): List<Section> {
        val sorted = items.sortedByDescending { it.dateTaken }
        return when (by) {
            GroupBy.NONE -> listOf(Section("all", "${sorted.size} items", null, sorted))
            GroupBy.MONTH -> sorted.groupBy { monthKey(it.dateTaken) }.map { (k, list) ->
                Section(k, monthTitle(list.first().dateTaken), countLabel(list), list)
            }
            GroupBy.DAY -> sorted.groupBy { dayKey(it.dateTaken) }.map { (k, list) ->
                Section(k, cap(dayFmt.format(Date(list.first().dateTaken))), placesSummary(list, places), list)
            }
            GroupBy.YEAR -> sorted.groupBy { yearOf(it.dateTaken) }.map { (y, list) ->
                Section("y$y", y.toString(), countLabel(list), list)
            }
            GroupBy.ALBUM -> sorted.groupBy { it.bucketId }.entries
                .sortedByDescending { it.value.size }
                .map { (b, list) -> Section("a$b", list.first().bucketName, countLabel(list), list) }
            GroupBy.COUNTRY -> byPlaceKey(sorted, places) { it.country }
                .map { (name, list) -> Section("c$name", name, citiesSummary(list, places), list) }
            GroupBy.CITY -> byPlaceKey(sorted, places) { p -> p.local?.let { c -> listOfNotNull(c, p.country).distinct().joinToString(", ") } }
                .map { (name, list) -> Section("v$name", name, countLabel(list), list) }
            GroupBy.COUNTRY_MONTH -> byPlaceKey(sorted, places) { it.country }.flatMap { (country, list) ->
                list.groupBy { monthKey(it.dateTaken) }.map { (mk, sub) ->
                    Section("cm$country|$mk", country, monthTitle(sub.first().dateTaken) + " · " + countLabel(sub), sub)
                }
            }
        }
    }

    /** Groups by a place field; biggest first, "Unknown place" last. */
    private fun byPlaceKey(
        items: List<MediaItem>,
        places: Map<Long, Place>,
        field: (Place) -> String?,
    ): List<Pair<String, List<MediaItem>>> {
        val groups = items.groupBy { places[it.id]?.let(field) ?: Place.UNKNOWN }
        return groups.entries
            .sortedWith(compareBy<Map.Entry<String, List<MediaItem>>> { it.key == Place.UNKNOWN }.thenByDescending { it.value.size })
            .map { it.key to it.value }
    }

    fun countLabel(list: List<MediaItem>): String {
        val v = list.count { it.isVideo }
        val p = list.size - v
        return listOfNotNull(
            if (p > 0) "$p photo${if (p > 1) "s" else ""}" else null,
            if (v > 0) "$v video${if (v > 1) "s" else ""}" else null
        ).joinToString(" · ")
    }

    private fun citiesSummary(list: List<MediaItem>, places: Map<Long, Place>): String {
        val cities = list.mapNotNull { places[it.id]?.local }.groupingBy { it }.eachCount()
            .entries.sortedByDescending { it.value }.take(3).map { it.key }
        return if (cities.isEmpty()) countLabel(list) else cities.joinToString(" · ") + " — " + countLabel(list)
    }

    private fun placesSummary(list: List<MediaItem>, places: Map<Long, Place>): String {
        val p = list.mapNotNull { places[it.id]?.local }.distinct().take(2)
        return if (p.isEmpty()) countLabel(list) else p.joinToString(" · ")
    }

    /** Grid index of each section's header (header + its items per section). */
    fun headerIndices(sections: List<Section>): List<Int> {
        var i = 0
        return sections.map { s -> val start = i; i += 1 + s.items.size; start }
    }
}
