package com.swipesort.app

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The whole photo/video library, shared by every screen. */
object Library {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var refreshJob: Job? = null
    private var indexJob: Job? = null
    private var db: PlaceDb? = null

    var items by mutableStateOf<List<MediaItem>>(emptyList())
        private set
    var byId by mutableStateOf<Map<Long, MediaItem>>(emptyMap())
        private set
    var albums by mutableStateOf<List<Album>>(emptyList())
        private set
    var loading by mutableStateOf(false)
        private set
    var loadedOnce by mutableStateOf(false)
        private set

    /** item id -> where it was taken (only items that have a location). */
    var places by mutableStateOf<Map<Long, Place>>(emptyMap())
        private set
    var indexing by mutableStateOf(false)
        private set
    var indexDone by mutableIntStateOf(0)
        private set
    var indexTotal by mutableIntStateOf(0)
        private set
    /** Places that have GPS but couldn't be named yet (usually: no internet). */
    var unnamedSpots by mutableIntStateOf(0)
        private set

    var favorites by mutableStateOf<Set<Long>>(emptySet())
        private set

    fun refresh(ctx: Context) {
        val app = ctx.applicationContext
        if (favorites.isEmpty()) loadFavorites(app)
        refreshJob?.cancel()
        refreshJob = scope.launch {
            loading = !loadedOnce
            val list = withContext(Dispatchers.IO) { runCatching { MediaRepo.loadAll(app) }.getOrDefault(emptyList()) }
            items = list
            byId = list.associateBy { it.id }
            albums = MediaRepo.albumsFrom(list)
            loading = false
            loadedOnce = true
            indexPlaces(app)
        }
    }

    fun placeOf(item: MediaItem): Place? = places[item.id]

    // ---------- favourites (stored in the app) ----------

    private fun loadFavorites(ctx: Context) {
        val prefs = ctx.getSharedPreferences("swipesort", Context.MODE_PRIVATE)
        favorites = prefs.getStringSet("favorites", emptySet())!!.mapNotNull { it.toLongOrNull() }.toSet()
    }

    fun toggleFavorite(ctx: Context, id: Long) {
        favorites = if (id in favorites) favorites - id else favorites + id
        ctx.getSharedPreferences("swipesort", Context.MODE_PRIVATE).edit()
            .putStringSet("favorites", favorites.map { it.toString() }.toSet()).apply()
    }

    // ---------- places ----------

    private fun indexPlaces(ctx: Context) {
        indexJob?.cancel()
        val snapshot = items
        indexJob = scope.launch(Dispatchers.IO) {
            val database = db ?: PlaceDb(ctx).also { db = it }
            val canRead = MediaRepo.hasLocationPermission(ctx)
            val locs = HashMap(database.allLocs())
            val geo = HashMap(database.allGeo())

            fun publish() {
                val map = HashMap<Long, Place>()
                var unnamed = 0
                val unnamedKeys = HashSet<String>()
                for (item in snapshot) {
                    val p = locs[item.id]?.point ?: continue
                    val k = PlaceReader.key(p)
                    val place = geo[k]
                    if (place != null) map[item.id] = place
                    else if (unnamedKeys.add(k)) unnamed++
                }
                scope.launch { places = map; unnamedSpots = unnamed }
            }

            publish()

            // 1) read GPS from files we haven't looked at yet
            val todo = if (canRead) snapshot.filter { locs[it.id]?.modified != it.dateModified } else emptyList()
            withContext(Dispatchers.Main) { indexing = todo.isNotEmpty(); indexDone = 0; indexTotal = todo.size }
            val batch = ArrayList<Triple<Long, Long, GeoPoint?>>()
            todo.forEachIndexed { i, item ->
                if (!isActive) return@launch
                val p = PlaceReader.readPoint(ctx, item, true)
                locs[item.id] = PlaceDb.Loc(item.dateModified, p)
                batch += Triple(item.id, item.dateModified, p)
                if (batch.size >= 100 || i == todo.lastIndex) {
                    database.putLocs(batch); batch.clear()
                    val done = i + 1
                    withContext(Dispatchers.Main) { indexDone = done }
                }
            }

            // 2) name the spots (country / region / village)
            val keys = locs.values.mapNotNull { it.point }.associateBy { PlaceReader.key(it) }
            val missing = keys.filterKeys { it !in geo }
            if (missing.isNotEmpty()) withContext(Dispatchers.Main) { indexing = true; indexDone = 0; indexTotal = missing.size }
            var n = 0
            var failuresInARow = 0
            for ((k, point) in missing) {
                if (!isActive) return@launch
                val place = PlaceReader.reverseGeocode(ctx, point)
                if (place != null && place.country != null) {
                    geo[k] = place
                    database.putGeo(k, place)
                    failuresInARow = 0
                } else {
                    failuresInARow++
                    if (failuresInARow >= 8) break // probably offline; try again next time
                }
                n++
                if (n % 10 == 0) { publish(); val d = n; withContext(Dispatchers.Main) { indexDone = d } }
            }
            publish()
            withContext(Dispatchers.Main) { indexing = false }
        }
    }
}
