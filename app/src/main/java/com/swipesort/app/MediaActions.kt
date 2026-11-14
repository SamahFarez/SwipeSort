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
