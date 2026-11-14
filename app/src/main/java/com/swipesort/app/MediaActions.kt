package com.swipesort.app

import android.app.Activity
import android.content.Context
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Move / delete from anywhere in the gallery (asks Android for permission, then does it). */
class MediaActions(
    private val ctx: Context,
    private val scope: CoroutineScope,
) {
    lateinit var launcher: ActivityResultLauncher<IntentSenderRequest>
    private var pending: (suspend (Boolean) -> Unit)? = null
    var toast by mutableStateOf<String?>(null)

    fun moveTo(items: List<MediaItem>, target: Target, onDone: () -> Unit = {}) {
        val list = items.filter { MediaRepo.pathFor(it, target) != it.relativePath }
        if (list.isEmpty()) { toast = "Already in ${target.name}"; onDone(); return }
        launch(MediaStore.createWriteRequest(ctx.contentResolver, list.map { it.uri })) { ok ->
            if (ok) {
                val moved = withContext(Dispatchers.IO) {
                    list.count { MediaRepo.move(ctx, it, MediaRepo.pathFor(it, target)) }
                }
                toast = "Moved $moved to ${target.name}" +
                    if (moved < list.size) " (${list.size - moved} failed)" else ""
                Library.refresh(ctx)
                onDone()
            }
        }
    }

    fun trash(items: List<MediaItem>, onDone: () -> Unit = {}) {
        if (items.isEmpty()) return
        launch(MediaStore.createTrashRequest(ctx.contentResolver, items.map { it.uri }, true)) { ok ->
            if (ok) {
                toast = "${items.size} moved to Trash"
                Library.refresh(ctx)
                onDone()
            }
        }
    }

    /** (done, total) while a big folder job runs. */
    var progress by mutableStateOf<Pair<Int, Int>?>(null)

    /** Moves each item into its own folder (plan = item -> relative path). Asks once per 400 items. */
    fun moveToPaths(plan: List<Pair<MediaItem, String>>, onDone: () -> Unit = {}) {
        val todo = plan.filter { (item, path) -> item.relativePath != path }
        if (todo.isEmpty()) { toast = "Everything is already in place"; onDone(); return }
        val chunks = todo.chunked(400)
        fun runChunk(i: Int, movedSoFar: Int) {
            if (i >= chunks.size) {
                progress = null
                toast = "Moved $movedSoFar into folders" + if (movedSoFar < todo.size) " (${todo.size - movedSoFar} failed)" else ""
                Library.refresh(ctx)
                onDone()
                return
            }
            val chunk = chunks[i]
            launch(MediaStore.createWriteRequest(ctx.contentResolver, chunk.map { it.first.uri })) { ok ->
                if (!ok) {
                    progress = null
                    toast = if (movedSoFar > 0) "Stopped after $movedSoFar" else "Not allowed"
                    Library.refresh(ctx)
                } else {
                    progress = movedSoFar to todo.size
                    val n = withContext(Dispatchers.IO) { chunk.count { (item, path) -> MediaRepo.move(ctx, item, path) } }
                    progress = (movedSoFar + n) to todo.size
                    runChunk(i + 1, movedSoFar + n)
                }
            }
        }
        runChunk(0, 0)
    }

    /** Copies each item into its folder; originals stay. */
    fun copyToPaths(plan: List<Pair<MediaItem, String>>, onDone: () -> Unit = {}) {
        if (plan.isEmpty()) return
        scope.launch {
            progress = 0 to plan.size
            var copied = 0
            plan.forEachIndexed { i, (item, path) ->
                val ok = withContext(Dispatchers.IO) { MediaRepo.copy(ctx, item, path) }
                if (ok) copied++
                if (i % 3 == 0 || i == plan.lastIndex) progress = (i + 1) to plan.size
            }
            progress = null
            toast = "Copied $copied into folders" + if (copied < plan.size) " (${plan.size - copied} failed)" else ""
            Library.refresh(ctx)
            onDone()
        }
    }

    private fun launch(pi: android.app.PendingIntent, then: suspend (Boolean) -> Unit) {
        pending = then
        launcher.launch(IntentSenderRequest.Builder(pi.intentSender).build())
    }

    fun onResult(ok: Boolean) {
        val p = pending
        pending = null
        if (p != null) scope.launch { p(ok) }
    }
}

val LocalMediaActions = staticCompositionLocalOf<MediaActions> { error("No MediaActions") }

@Composable
fun rememberMediaActions(): MediaActions {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val actions = remember { MediaActions(ctx, scope) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
        actions.onResult(it.resultCode == Activity.RESULT_OK)
    }
    actions.launcher = launcher
    return actions
}
