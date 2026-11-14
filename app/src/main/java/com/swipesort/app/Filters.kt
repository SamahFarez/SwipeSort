package com.swipesort.app

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar
import kotlin.math.max
import kotlin.math.min

enum class Shape(val label: String) {
    PORTRAIT("Portrait"), LANDSCAPE("Landscape"), SQUARE("Square"), REELS("Reels 9:16"), PANORAMA("Panorama")
}

enum class Source(val label: String) {
    CAMERA("Camera"), SCREENSHOTS("Screenshots"), WHATSAPP("WhatsApp"), INSTAGRAM("Instagram"),
    SNAPCHAT("Snapchat"), DOWNLOADS("Downloads"), EDITS("Edited / CapCut"), OTHER("Other apps")
}

enum class TimeOfDay(val label: String) {
    MORNING("🌅 Morning"), AFTERNOON("☀️ Afternoon"), EVENING("🌇 Evening"), NIGHT("🌙 Night")
}

enum class Length(val label: String, val minMs: Long, val maxMs: Long) {
    SHORT("< 15 s", 0, 15_000), REEL("15–60 s", 15_000, 60_000), MEDIUM("1–3 min", 60_000, 180_000), LONG("> 3 min", 180_000, Long.MAX_VALUE)
}

enum class LocFilter(val label: String) { ANY("Any"), WITH("📍 Has location"), WITHOUT("No location") }

/** Everything you can filter on — all read from the photo/video metadata. */
data class Filters(
    val years: Set<Int> = emptySet(),
    val months: Set<Int> = emptySet(),          // 1..12
    val countries: Set<String> = emptySet(),
    val cities: Set<String> = emptySet(),
    val albums: Set<String> = emptySet(),
    val shapes: Set<Shape> = emptySet(),
    val sources: Set<Source> = emptySet(),
    val times: Set<TimeOfDay> = emptySet(),
    val lengths: Set<Length> = emptySet(),
    val cameras: Set<String> = emptySet(),
    val location: LocFilter = LocFilter.ANY,
) {
    val count: Int
        get() = listOf(years, months, countries, cities, albums, shapes, sources, times, lengths, cameras)
            .count { it.isNotEmpty() } + if (location != LocFilter.ANY) 1 else 0

    fun isEmpty() = count == 0
}

object Meta {
    private val monthNames = arrayOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
    fun monthName(m: Int) = monthNames[(m - 1).coerceIn(0, 11)]

    fun year(item: MediaItem): Int = Calendar.getInstance().apply { timeInMillis = item.dateTaken }.get(Calendar.YEAR)
    fun month(item: MediaItem): Int = Calendar.getInstance().apply { timeInMillis = item.dateTaken }.get(Calendar.MONTH) + 1

    fun timeOfDay(item: MediaItem): TimeOfDay {
        val h = Calendar.getInstance().apply { timeInMillis = item.dateTaken }.get(Calendar.HOUR_OF_DAY)
        return when (h) {
            in 5..11 -> TimeOfDay.MORNING
            in 12..16 -> TimeOfDay.AFTERNOON
            in 17..20 -> TimeOfDay.EVENING
            else -> TimeOfDay.NIGHT
        }
    }

    fun source(item: MediaItem): Source {
        val p = (item.relativePath + "/" + item.bucketName).lowercase()
        return when {
            "screenshot" in p || "screen record" in p || "screenrecord" in p -> Source.SCREENSHOTS
            "whatsapp" in p -> Source.WHATSAPP
            "instagram" in p -> Source.INSTAGRAM
            "snapchat" in p -> Source.SNAPCHAT
            "capcut" in p || "edits" in p || "inshot" in p || "vn" == item.bucketName.lowercase() -> Source.EDITS
            p.startsWith("download") -> Source.DOWNLOADS
            p.startsWith("dcim/camera") || item.bucketName.equals("Camera", true) -> Source.CAMERA
            else -> Source.OTHER
        }
    }

    fun shapes(item: MediaItem): Set<Shape> {
        if (item.width <= 0 || item.height <= 0) return emptySet()
        val r = max(item.width, item.height).toFloat() / min(item.width, item.height)
        val out = mutableSetOf<Shape>()
        when {
            r < 1.05f -> out += Shape.SQUARE
            item.height > item.width -> out += Shape.PORTRAIT
            else -> out += Shape.LANDSCAPE
        }
        if (MediaRepo.isReelReady(item)) out += Shape.REELS
        if (r >= 2.2f) out += Shape.PANORAMA
        return out
    }

    fun length(item: MediaItem): Length? =
        if (!item.isVideo) null else Length.entries.firstOrNull { item.durationMs >= it.minMs && item.durationMs < it.maxMs }

    fun matches(item: MediaItem, f: Filters, places: Map<Long, Place>, cameras: Map<Long, String>): Boolean {
        if (f.years.isNotEmpty() && year(item) !in f.years) return false
        if (f.months.isNotEmpty() && month(item) !in f.months) return false
        val place = places[item.id]
        if (f.countries.isNotEmpty() && (place?.country ?: Place.UNKNOWN) !in f.countries) return false
        if (f.cities.isNotEmpty() && (place?.local ?: "") !in f.cities) return false
        if (f.albums.isNotEmpty() && item.bucketName !in f.albums) return false
        if (f.shapes.isNotEmpty() && shapes(item).none { it in f.shapes }) return false
        if (f.sources.isNotEmpty() && source(item) !in f.sources) return false
        if (f.times.isNotEmpty() && timeOfDay(item) !in f.times) return false
        if (f.lengths.isNotEmpty() && (length(item) ?: return false) !in f.lengths) return false
        if (f.cameras.isNotEmpty() && (cameras[item.id] ?: "") !in f.cameras) return false
        when (f.location) {
            LocFilter.WITH -> if (place == null) return false
            LocFilter.WITHOUT -> if (place != null) return false
            LocFilter.ANY -> Unit
        }
        return true
    }

    fun apply(items: List<MediaItem>, f: Filters, places: Map<Long, Place>, cameras: Map<Long, String>): List<MediaItem> =
        if (f.isEmpty()) items else items.filter { matches(it, f, places, cameras) }

    /** Small removable chips describing the active filters. */
    fun activeChips(f: Filters): List<Pair<String, Filters>> {
        val out = mutableListOf<Pair<String, Filters>>()
        f.years.sorted().forEach { y -> out += "$y" to f.copy(years = f.years - y) }
        f.months.sorted().forEach { m -> out += monthName(m) to f.copy(months = f.months - m) }
        f.countries.forEach { c -> out += "📍 $c" to f.copy(countries = f.countries - c) }
        f.cities.forEach { c -> out += c to f.copy(cities = f.cities - c) }
        f.albums.forEach { a -> out += "📁 $a" to f.copy(albums = f.albums - a) }
        f.shapes.forEach { s -> out += s.label to f.copy(shapes = f.shapes - s) }
        f.sources.forEach { s -> out += s.label to f.copy(sources = f.sources - s) }
        f.times.forEach { t -> out += t.label to f.copy(times = f.times - t) }
        f.lengths.forEach { l -> out += l.label to f.copy(lengths = f.lengths - l) }
        f.cameras.forEach { c -> out += "📷 $c" to f.copy(cameras = f.cameras - c) }
        if (f.location != LocFilter.ANY) out += f.location.label to f.copy(location = LocFilter.ANY)
        return out
    }

    // ---------- JSON (for saved smart albums) ----------

    fun toJson(f: Filters): JSONObject = JSONObject().apply {
        put("years", JSONArray(f.years.toList()))
        put("months", JSONArray(f.months.toList()))
        put("countries", JSONArray(f.countries.toList()))
        put("cities", JSONArray(f.cities.toList()))
        put("albums", JSONArray(f.albums.toList()))
        put("shapes", JSONArray(f.shapes.map { it.name }))
        put("sources", JSONArray(f.sources.map { it.name }))
        put("times", JSONArray(f.times.map { it.name }))
        put("lengths", JSONArray(f.lengths.map { it.name }))
        put("cameras", JSONArray(f.cameras.toList()))
        put("location", f.location.name)
    }

    private fun strings(o: JSONObject, key: String): List<String> {
        val a = o.optJSONArray(key) ?: return emptyList()
        return (0 until a.length()).map { a.get(it).toString() }
    }

    fun fromJson(o: JSONObject): Filters = Filters(
        years = strings(o, "years").mapNotNull { it.toIntOrNull() }.toSet(),
        months = strings(o, "months").mapNotNull { it.toIntOrNull() }.toSet(),
        countries = strings(o, "countries").toSet(),
        cities = strings(o, "cities").toSet(),
        albums = strings(o, "albums").toSet(),
        shapes = strings(o, "shapes").mapNotNull { n -> Shape.entries.firstOrNull { it.name == n } }.toSet(),
        sources = strings(o, "sources").mapNotNull { n -> Source.entries.firstOrNull { it.name == n } }.toSet(),
        times = strings(o, "times").mapNotNull { n -> TimeOfDay.entries.firstOrNull { it.name == n } }.toSet(),
        lengths = strings(o, "lengths").mapNotNull { n -> Length.entries.firstOrNull { it.name == n } }.toSet(),
        cameras = strings(o, "cameras").toSet(),
        location = LocFilter.entries.firstOrNull { it.name == o.optString("location") } ?: LocFilter.ANY,
    )
}

/** A saved filter = a smart album (like Google Photos' automatic albums). */
data class Preset(val name: String, val filters: Filters)

object Presets {
    var list by mutableStateOf<List<Preset>>(emptyList())
        private set
    private var loaded = false

    /** Built-in smart albums. */
    val builtIn = listOf(
        Preset("Screenshots", Filters(sources = setOf(Source.SCREENSHOTS))),
        Preset("WhatsApp", Filters(sources = setOf(Source.WHATSAPP))),
        Preset("Panoramas", Filters(shapes = setOf(Shape.PANORAMA))),
        Preset("Night shots", Filters(times = setOf(TimeOfDay.NIGHT), sources = setOf(Source.CAMERA))),
        Preset("Short clips", Filters(lengths = setOf(Length.SHORT, Length.REEL))),
        Preset("Long videos", Filters(lengths = setOf(Length.LONG))),
    )

    fun load(ctx: Context) {
        if (loaded) return
        loaded = true
        val raw = ctx.getSharedPreferences("swipesort", Context.MODE_PRIVATE).getString("presets", null) ?: return
        list = runCatching {
            val a = JSONArray(raw)
            (0 until a.length()).map { i ->
                val o = a.getJSONObject(i)
                Preset(o.getString("name"), Meta.fromJson(o.getJSONObject("filters")))
            }
        }.getOrDefault(emptyList())
    }

    fun save(ctx: Context, preset: Preset) {
        list = list.filterNot { it.name == preset.name } + preset
        persist(ctx)
    }

    fun delete(ctx: Context, name: String) {
        list = list.filterNot { it.name == name }
        persist(ctx)
    }

    private fun persist(ctx: Context) {
        val a = JSONArray()
        list.forEach { p -> a.put(JSONObject().put("name", p.name).put("filters", Meta.toJson(p.filters))) }
        ctx.getSharedPreferences("swipesort", Context.MODE_PRIVATE).edit().putString("presets", a.toString()).apply()
    }
}

// ------------------------------------------------------------------ folder maker plan

enum class FolderPattern(val label: String, val example: String) {
    ONE("One folder", "Name"),
    COUNTRY("By country", "Name / Algeria"),
    COUNTRY_CITY("Country › village", "Name / Algeria / Béjaïa"),
    YEAR_MONTH("Year › month", "Name / 2026 / 03 March"),
    MONTH("By month", "Name / 2026-03 March"),
    COUNTRY_MONTH("Country › month", "Name / Algeria / 2026-03 March"),
    ALBUM_COUNTRY("Album › country", "Name / Moments / Algeria"),
}

object FolderPlan {
    private val fullMonths = arrayOf("January", "February", "March", "April", "May", "June", "July",
        "August", "September", "October", "November", "December")

    fun clean(s: String): String = s.replace(Regex("[/\\\\:*?\"<>|]"), " ").trim().ifEmpty { "Untitled" }

    /** Target folder (relative path) for one item. */
    fun pathFor(item: MediaItem, pattern: FolderPattern, root: String, places: Map<Long, Place>): String {
        val p = places[item.id]
        val country = p?.country ?: Place.UNKNOWN
        val city = p?.local ?: Place.UNKNOWN
        val y = Meta.year(item)
        val m = Meta.month(item)
        val monthLabel = "%d-%02d %s".format(y, m, fullMonths[m - 1])
        val parts = mutableListOf<String>()
        if (root.isNotBlank()) parts += root
        when (pattern) {
            FolderPattern.ONE -> Unit
            FolderPattern.COUNTRY -> parts += country
            FolderPattern.COUNTRY_CITY -> { parts += country; parts += city }
            FolderPattern.YEAR_MONTH -> { parts += "$y"; parts += "%02d %s".format(m, fullMonths[m - 1]) }
            FolderPattern.MONTH -> parts += monthLabel
            FolderPattern.COUNTRY_MONTH -> { parts += country; parts += monthLabel }
            FolderPattern.ALBUM_COUNTRY -> { parts += item.bucketName; parts += country }
        }
        if (parts.isEmpty()) parts += "Sorted"
        return "Pictures/" + parts.joinToString("/") { clean(it) } + "/"
    }

    fun plan(items: List<MediaItem>, pattern: FolderPattern, root: String, places: Map<Long, Place>): List<Pair<MediaItem, String>> =
        items.map { it to pathFor(it, pattern, root, places) }
}
