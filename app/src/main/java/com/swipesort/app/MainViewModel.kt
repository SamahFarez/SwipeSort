package com.swipesort.app

import android.app.Application
import android.app.PendingIntent
import android.content.Context
import android.provider.MediaStore
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class Screen { SETUP, SWIPE }
enum class MediaFilter { ALL, PHOTOS, VIDEOS }
enum class ActionType { LEFT, RIGHT, DELETE, SKIP }
enum class CommitStage { IDLE, MOVES, TRASH }

data class SwipeAction(val item: MediaItem, val type: ActionType, val targetPath: String?)

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = app.getSharedPreferences("swipesort", Context.MODE_PRIVATE)
    private val ctx: Context get() = getApplication()

    // ---------- setup state ----------
    var screen by mutableStateOf(Screen.SETUP)
    var loading by mutableStateOf(false)
    var hasPermission by mutableStateOf(MediaRepo.hasReadPermission(app))
    var canManageMedia by mutableStateOf(MediaRepo.canManageMedia(app))
    var allItems by mutableStateOf<List<MediaItem>>(emptyList())
    var albums by mutableStateOf<List<Album>>(emptyList())
    var customAlbums by mutableStateOf(loadCustomAlbums())

    var sourceBucket by mutableStateOf(
        prefs.getLong("source", Long.MIN_VALUE).takeIf { it != Long.MIN_VALUE }
    )
    var left by mutableStateOf(loadTarget("left"))
    var right by mutableStateOf(loadTarget("right"))
    var filter by mutableStateOf(
        runCatching { MediaFilter.valueOf(prefs.getString("filter", "ALL")!!) }.getOrDefault(MediaFilter.ALL)
    )
    var newestFirst by mutableStateOf(prefs.getBoolean("newest", true))
    var muted by mutableStateOf(prefs.getBoolean("muted", false))

    // ---------- swipe session state ----------
    var queue by mutableStateOf<List<MediaItem>>(emptyList())
    var index by mutableIntStateOf(0)
    /** Swipes not yet written to the phone's storage (can still be undone). */
    val history = mutableStateListOf<SwipeAction>()
    var commitStage by mutableStateOf(CommitStage.IDLE)
    var exitAfterCommit by mutableStateOf(false)
    var message by mutableStateOf<String?>(null)
    var doneLeft by mutableIntStateOf(0)
    var doneRight by mutableIntStateOf(0)
    var doneDeleted by mutableIntStateOf(0)

    val current: MediaItem? get() = queue.getOrNull(index)
    val next: MediaItem? get() = queue.getOrNull(index + 1)
    val pendingCount: Int get() = history.count { it.type != ActionType.SKIP }

    private val autoApplyEvery = 25

    init {
        if (hasPermission) refresh()
    }

    fun onPermissionResult() {
        hasPermission = MediaRepo.hasReadPermission(ctx)
        canManageMedia = MediaRepo.canManageMedia(ctx)
        if (hasPermission) refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            loading = albums.isEmpty()
            val items = withContext(Dispatchers.IO) { MediaRepo.loadAll(ctx) }
            allItems = items
            albums = MediaRepo.albumsFrom(items)
            loading = false
        }
    }

    // ---------- album targets ----------

    /** Every album you can send to: existing folders + albums you created that are still empty. */
    fun allTargets(): List<Target> {
        val existing = albums.map { Target(it.name, it.relativePath) }
        val paths = existing.map { it.relativePath }.toSet()
        return (existing + customAlbums.filter { it.relativePath !in paths }).distinctBy { it.relativePath }
    }

    fun createAlbum(name: String): Target? {
        val clean = name.trim().replace(Regex("[/\\\\:*?\"<>|]"), "")
        if (clean.isEmpty()) return null
        allTargets().firstOrNull { it.name.equals(clean, ignoreCase = true) }?.let { return it }
        val t = Target(clean, MediaRepo.newAlbumPath(clean))
        customAlbums = customAlbums + t
        saveCustomAlbums()
        return t
    }

    fun updateLeft(t: Target?) { left = t; saveTarget("left", t) }
    fun updateRight(t: Target?) { right = t; saveTarget("right", t) }
    fun swapSides() { val l = left; updateLeft(right); updateRight(l) }
    fun updateSource(bucket: Long) { sourceBucket = bucket; prefs.edit().putLong("source", bucket).apply() }
    fun updateFilter(f: MediaFilter) { filter = f; prefs.edit().putString("filter", f.name).apply() }
    fun updateNewestFirst(v: Boolean) { newestFirst = v; prefs.edit().putBoolean("newest", v).apply() }
    fun toggleMute() { muted = !muted; prefs.edit().putBoolean("muted", muted).apply() }

    fun sourceItems(): List<MediaItem> {
        val b = sourceBucket ?: return emptyList()
        val list = allItems.filter { it.bucketId == b }.filter {
            when (filter) {
                MediaFilter.ALL -> true
                MediaFilter.PHOTOS -> !it.isVideo
                MediaFilter.VIDEOS -> it.isVideo
            }
        }
        return if (newestFirst) list.sortedByDescending { it.dateAdded } else list.sortedBy { it.dateAdded }
    }

    // ---------- session ----------

    fun startSession() {
        queue = sourceItems()
        index = 0
        history.clear()
        doneLeft = 0; doneRight = 0; doneDeleted = 0
        exitAfterCommit = false
        screen = Screen.SWIPE
    }

    fun leaveSession() {
        history.clear()
        screen = Screen.SETUP
        refresh()
    }

    fun onSwipe(type: ActionType) {
        val item = current ?: return
        val target = when (type) {
            ActionType.LEFT -> left
            ActionType.RIGHT -> right
            else -> null
        }
        if ((type == ActionType.LEFT || type == ActionType.RIGHT) && target == null) return
        val path = target?.let { MediaRepo.pathFor(item, it) }
        // Already in that album -> treat as skip
        val finalType = if (path != null && path == item.relativePath) ActionType.SKIP else type
        history += SwipeAction(item, finalType, path)
        index++
        if (pendingCount >= autoApplyEvery || (index >= queue.size && pendingCount > 0)) startCommit()
    }

    fun undo() {
        if (commitStage != CommitStage.IDLE) return
        val last = history.removeLastOrNull() ?: return
        if (index > 0 && queue.getOrNull(index - 1)?.id == last.item.id) {
            index--
        } else {
            queue = queue.toMutableList().apply { add(index, last.item) }
        }
    }

    // ---------- writing changes (moves + trash) ----------

    fun startCommit(exit: Boolean = false) {
        exitAfterCommit = exit
        if (pendingCount == 0) {
            history.clear()
            if (exit) leaveSession()
            return
        }
        if (commitStage == CommitStage.IDLE) commitStage = CommitStage.MOVES
    }

    private fun pendingMoves() = history.filter {
        (it.type == ActionType.LEFT || it.type == ActionType.RIGHT) && it.targetPath != null
    }

    private fun pendingDeletes() = history.filter { it.type == ActionType.DELETE }

    /** System request (pop-up, or silent in fast mode) for the current stage, or null if nothing to do. */
    fun requestForStage(): PendingIntent? {
        val resolver = ctx.contentResolver
        return when (commitStage) {
            CommitStage.MOVES -> pendingMoves().takeIf { it.isNotEmpty() }
                ?.let { list -> MediaStore.createWriteRequest(resolver, list.map { it.item.uri }) }
            CommitStage.TRASH -> pendingDeletes().takeIf { it.isNotEmpty() }
                ?.let { list -> MediaStore.createTrashRequest(resolver, list.map { it.item.uri }, true) }
            CommitStage.IDLE -> null
        }
    }

    fun onStageResult(granted: Boolean) {
        when (commitStage) {
            CommitStage.MOVES -> {
                val moves = pendingMoves()
                if (!granted) {
                    if (moves.isNotEmpty()) message = "Moves not allowed — they're still pending"
                    commitStage = CommitStage.TRASH
                    return
                }
                viewModelScope.launch {
                    val failed = withContext(Dispatchers.IO) {
                        moves.filterNot { MediaRepo.move(ctx, it.item, it.targetPath!!) }
                    }
                    val ok = moves - failed.toSet()
                    doneLeft += ok.count { it.type == ActionType.LEFT }
                    doneRight += ok.count { it.type == ActionType.RIGHT }
                    history.removeAll(ok.toSet())
                    if (failed.isNotEmpty()) {
                        message = "${failed.size} file(s) couldn't be moved (name clash or read-only)"
                        history.removeAll(failed.toSet())
                    }
                    commitStage = CommitStage.TRASH
                }
            }
            CommitStage.TRASH -> {
                val dels = pendingDeletes()
                if (granted) {
                    doneDeleted += dels.size
                    history.removeAll(dels.toSet())
                } else if (dels.isNotEmpty()) {
                    message = "Delete not allowed — still pending"
                }
                finishCommit()
            }
            CommitStage.IDLE -> Unit
        }
    }

    private fun finishCommit() {
        commitStage = CommitStage.IDLE
        history.removeAll { it.type == ActionType.SKIP }
        if (exitAfterCommit) {
            exitAfterCommit = false
            leaveSession()
        } else {
            refresh()
        }
    }

    // ---------- prefs ----------

    private fun loadTarget(key: String): Target? {
        val name = prefs.getString("${key}_name", null) ?: return null
        val path = prefs.getString("${key}_path", null) ?: return null
        return Target(name, path)
    }

    private fun saveTarget(key: String, t: Target?) {
        prefs.edit().apply {
            if (t == null) { remove("${key}_name"); remove("${key}_path") }
            else { putString("${key}_name", t.name); putString("${key}_path", t.relativePath) }
        }.apply()
    }

    private fun loadCustomAlbums(): List<Target> =
        prefs.getStringSet("custom_albums", emptySet())!!.mapNotNull { entry ->
            val parts = entry.split("|", limit = 2)
            if (parts.size == 2) Target(parts[0], parts[1]) else null
        }.sortedBy { it.name.lowercase() }

    private fun saveCustomAlbums() {
        prefs.edit().putStringSet("custom_albums", customAlbums.map { "${it.name}|${it.relativePath}" }.toSet()).apply()
    }
}
