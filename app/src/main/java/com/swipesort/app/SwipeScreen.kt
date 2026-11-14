package com.swipesort.app

import android.app.Activity
import android.media.MediaPlayer
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.VideoView
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import kotlin.math.abs

private val LeftColor = Color(0xFF4FC3F7)
private val RightColor = Color(0xFF81C784)
private val DeleteColor = Color(0xFFE57373)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeScreen(vm: MainViewModel) {
    val snackbar = remember { SnackbarHostState() }
    var confirmExit by remember { mutableStateOf(false) }

    // ---- writing changes to storage: one system request per stage ----
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { res ->
        vm.onStageResult(res.resultCode == Activity.RESULT_OK)
    }
    LaunchedEffect(vm.commitStage) {
        if (vm.commitStage != CommitStage.IDLE) {
            val pi = runCatching { vm.requestForStage() }.getOrNull()
            if (pi == null) vm.onStageResult(true)
            else launcher.launch(IntentSenderRequest.Builder(pi.intentSender).build())
        }
    }
    LaunchedEffect(vm.message) {
        vm.message?.let { snackbar.showSnackbar(it); vm.message = null }
    }

    BackHandler {
        if (vm.pendingCount > 0) confirmExit = true else vm.leaveSession()
    }

    val item = vm.current
    val done = item == null
    val busy = vm.commitStage != CommitStage.IDLE

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = { if (vm.pendingCount > 0) confirmExit = true else vm.leaveSession() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                title = {
                    Text(
                        if (done) "Done" else "${vm.index + 1} / ${vm.queue.size}",
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    if (item?.isVideo == true) {
                        IconButton(onClick = vm::toggleMute) {
                            Icon(
                                if (vm.muted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                                "Mute"
                            )
                        }
                    }
                    if (vm.pendingCount > 0) {
                        TextButton(onClick = { vm.startCommit() }, enabled = !busy) {
                            Icon(Icons.Default.Check, null)
                            Text(" Apply ${vm.pendingCount}")
                        }
                    }
                }
            )
        },
        bottomBar = {
            if (!done) ActionBar(vm, enabled = !busy)
        }
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad).padding(12.dp)) {
            if (done) {
                DoneView(vm)
            } else {
                // Card underneath (next item preview)
                vm.next?.let { nxt ->
                    Box(
                        Modifier.fillMaxSize().scale(0.94f).clip(RoundedCornerShape(24.dp)).background(Color.Black)
                    ) {
                        AsyncImage(nxt.uri, null, Modifier.fillMaxSize().alpha(0.5f), contentScale = ContentScale.Fit)
                    }
                }
                SwipeCard(
                    item = item!!,
                    muted = vm.muted,
                    leftName = vm.left?.name,
                    rightName = vm.right?.name,
                    enabled = !busy,
                    onSwiped = vm::onSwipe,
                )
            }
        }
    }

    if (confirmExit) {
        AlertDialog(
            onDismissRequest = { confirmExit = false },
            title = { Text("Apply ${vm.pendingCount} changes?") },
            text = { Text("You have swipes that haven't been saved to your gallery yet.") },
            confirmButton = {
                TextButton(onClick = { confirmExit = false; vm.startCommit(exit = true) }) { Text("Apply & exit") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { confirmExit = false; vm.leaveSession() }) { Text("Discard") }
                    TextButton(onClick = { confirmExit = false }) { Text("Cancel") }
                }
            }
        )
    }
}

/** Triggers the same fly-away animation as a swipe when a button is tapped. */
private class ButtonTrigger { var fire by mutableStateOf<ActionType?>(null) }
private val trigger = ButtonTrigger()

@Composable
private fun ActionBar(vm: MainViewModel, enabled: Boolean) {
    Row(
        Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        SideButton("← ${vm.left?.name ?: "—"}", LeftColor, enabled && vm.left != null) { trigger.fire = ActionType.LEFT }
        FilledTonalIconButton(onClick = vm::undo, enabled = enabled && vm.history.isNotEmpty()) {
            Icon(Icons.AutoMirrored.Filled.Undo, "Undo")
        }
        FilledTonalIconButton(onClick = { trigger.fire = ActionType.SKIP }, enabled = enabled) {
            Icon(Icons.Default.KeyboardArrowDown, "Skip")
        }
        FilledTonalIconButton(onClick = { trigger.fire = ActionType.DELETE }, enabled = enabled) {
            Icon(Icons.Default.Delete, "Delete", tint = DeleteColor)
        }
        SideButton("${vm.right?.name ?: "—"} →", RightColor, enabled && vm.right != null) { trigger.fire = ActionType.RIGHT }
    }
}

@Composable
private fun SideButton(text: String, color: Color, enabled: Boolean, onClick: () -> Unit) {
    FilledTonalButton(onClick = onClick, enabled = enabled, modifier = Modifier.widthIn(max = 120.dp)) {
        Text(text, color = if (enabled) color else Color.Unspecified, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 13.sp)
    }
}

@Composable
private fun SwipeCard(
    item: MediaItem,
    muted: Boolean,
    leftName: String?,
    rightName: String?,
    enabled: Boolean,
    onSwiped: (ActionType) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val offset = remember(item.id) { Animatable(Offset.Zero, Offset.VectorConverter) }
    val currentOnSwiped by rememberUpdatedState(onSwiped)

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        val xThreshold = w * 0.28f
        val yThreshold = h * 0.20f

        fun classify(o: Offset): ActionType? = when {
            abs(o.y) > abs(o.x) && o.y < -yThreshold -> ActionType.DELETE
            abs(o.y) > abs(o.x) && o.y > yThreshold -> ActionType.SKIP
            o.x < -xThreshold && leftName != null -> ActionType.LEFT
            o.x > xThreshold && rightName != null -> ActionType.RIGHT
            else -> null
        }

        suspend fun flyAway(type: ActionType) {
            val o = offset.value
            val dest = when (type) {
                ActionType.LEFT -> Offset(-w * 1.6f, o.y)
                ActionType.RIGHT -> Offset(w * 1.6f, o.y)
                ActionType.DELETE -> Offset(o.x, -h * 1.6f)
                ActionType.SKIP -> Offset(o.x, h * 1.6f)
            }
            offset.animateTo(dest, tween(200))
            currentOnSwiped(type)
        }

        // Button taps
        LaunchedEffect(trigger.fire, item.id) {
            val t = trigger.fire ?: return@LaunchedEffect
            trigger.fire = null
            if (enabled) flyAway(t)
        }

        // The card itself
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationX = offset.value.x
                    translationY = offset.value.y
                    rotationZ = (offset.value.x / w) * 12f
                }
                .clip(RoundedCornerShape(24.dp))
                .background(Color.Black)
        ) {
            key(item.id) { MediaView(item, muted) }

            // Info strip
            Column(
                Modifier.align(Alignment.BottomStart).fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.45f)).padding(12.dp)
            ) {
                Text(item.name, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                Text("in ${item.bucketName}", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
            }

            // Swipe hint overlays
            val o = offset.value
            val horiz = abs(o.x) >= abs(o.y)
            if (horiz && o.x < 0 && leftName != null)
                Hint("← $leftName", LeftColor, (-o.x / xThreshold).coerceIn(0f, 1f), Alignment.TopEnd)
            if (horiz && o.x > 0 && rightName != null)
                Hint("$rightName →", RightColor, (o.x / xThreshold).coerceIn(0f, 1f), Alignment.TopStart)
            if (!horiz && o.y < 0)
                Hint("DELETE", DeleteColor, (-o.y / yThreshold).coerceIn(0f, 1f), Alignment.BottomCenter)
            if (!horiz && o.y > 0)
                Hint("SKIP", Color.White, (o.y / yThreshold).coerceIn(0f, 1f), Alignment.TopCenter)
        }

        // Transparent gesture layer on top (so the video view can't steal the touches)
        Box(
            Modifier.fillMaxSize().pointerInput(item.id, enabled) {
                if (!enabled) return@pointerInput
                detectDragGestures(
                    onDragEnd = {
                        scope.launch {
                            val type = classify(offset.value)
                            if (type == null) offset.animateTo(Offset.Zero, spring(dampingRatio = 0.6f))
                            else flyAway(type)
                        }
                    },
                    onDragCancel = { scope.launch { offset.animateTo(Offset.Zero) } },
                ) { change, drag ->
                    change.consume()
                    scope.launch { offset.snapTo(offset.value + drag) }
                }
            }
        )
    }
}

@Composable
private fun androidx.compose.foundation.layout.BoxScope.Hint(text: String, color: Color, strength: Float, align: Alignment) {
    if (strength <= 0.05f) return
    Box(
        Modifier.align(align).padding(24.dp).alpha(strength)
            .background(color.copy(alpha = 0.9f), RoundedCornerShape(12.dp))
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(text, color = Color.Black, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, maxLines = 1)
    }
}

@Composable
private fun MediaView(item: MediaItem, muted: Boolean) {
    if (!item.isVideo) {
        AsyncImage(
            model = item.uri, contentDescription = item.name,
            modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit
        )
        return
    }
    var player by remember(item.id) { mutableStateOf<MediaPlayer?>(null) }
    var failed by remember(item.id) { mutableStateOf(false) }
    val mutedNow by rememberUpdatedState(muted)

    Box(Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                FrameLayout(ctx).apply {
                    val vv = VideoView(ctx)
                    addView(vv, FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                        Gravity.CENTER
                    ))
                    vv.setOnPreparedListener { mp ->
                        mp.isLooping = true
                        val v = if (mutedNow) 0f else 1f
                        mp.setVolume(v, v)
                        player = mp
                        vv.start()
                    }
                    vv.setOnErrorListener { _, _, _ -> failed = true; true }
                    vv.setVideoURI(item.uri)
                }
            },
            onRelease = { frame ->
                (frame.getChildAt(0) as? VideoView)?.stopPlayback()
                player = null
            }
        )
        if (player == null) {
            // Thumbnail while the video loads (or if it can't play)
            AsyncImage(item.uri, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
            Icon(
                Icons.Default.PlayCircle, null,
                Modifier.align(Alignment.Center).size(64.dp).alpha(if (failed) 1f else 0.6f),
                tint = Color.White
            )
        }
    }
    LaunchedEffect(muted, player) {
        player?.let { runCatching { val v = if (muted) 0f else 1f; it.setVolume(v, v) } }
    }
}

@Composable
private fun DoneView(vm: MainViewModel) {
    Column(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("🎉", fontSize = 64.sp)
        Spacer(Modifier.height(12.dp))
        Text("Album sorted!", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))
        vm.left?.let { Text("${vm.doneLeft} → ${it.name}", color = LeftColor) }
        vm.right?.let { Text("${vm.doneRight} → ${it.name}", color = RightColor) }
        Text("${vm.doneDeleted} moved to Trash", color = DeleteColor)
        if (vm.pendingCount > 0) {
            Spacer(Modifier.height(8.dp))
            Text("${vm.pendingCount} change(s) still pending", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            Button(onClick = { vm.startCommit() }) { Text("Apply now") }
        }
        Spacer(Modifier.height(24.dp))
        Button(onClick = { vm.startCommit(exit = true) }) { Text("Back to albums") }
    }
}
