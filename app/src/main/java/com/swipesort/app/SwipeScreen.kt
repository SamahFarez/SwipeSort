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
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
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

    Box(Modifier.fillMaxSize().systemBarsPadding()) {
        Column(Modifier.fillMaxSize()) {
            TopBar(vm, done, busy, onBack = { if (vm.pendingCount > 0) confirmExit = true else vm.leaveSession() })

            Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp)) {
                if (done) {
                    DoneView(vm)
                } else {
                    // Next card peeking underneath
                    vm.next?.let { nxt ->
                        Box(
                            Modifier.fillMaxSize().scale(0.93f).offset(y = 14.dp)
                                .clip(RoundedCornerShape(34.dp))
                                .background(Color.White.copy(alpha = 0.6f))
                                .padding(6.dp)
                                .clip(RoundedCornerShape(28.dp))
                                .background(Palette.MediaBg)
                        ) {
                            AsyncImage(nxt.uri, null, Modifier.fillMaxSize().alpha(0.45f), contentScale = ContentScale.Crop)
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

            if (!done) ActionBar(vm, enabled = !busy)
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = 90.dp)) { data ->
            Snackbar(data, containerColor = Palette.Ink, contentColor = Color.White, shape = RoundedCornerShape(18.dp))
        }
    }

    if (confirmExit) {
        AlertDialog(
            onDismissRequest = { confirmExit = false },
            containerColor = Color.White,
            shape = RoundedCornerShape(30.dp),
            title = { Text("Save ${vm.pendingCount} changes?", fontFamily = DisplayFont, color = Palette.Ink) },
            text = { Text("Some swipes haven't been applied to your gallery yet.", color = Palette.InkSoft) },
            confirmButton = {
                TextButton(onClick = { confirmExit = false; vm.startCommit(exit = true) }) {
                    Text("Save & exit", color = Palette.PinkDeep, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { confirmExit = false; vm.leaveSession() }) { Text("Discard", color = Palette.Rose) }
                    TextButton(onClick = { confirmExit = false }) { Text("Cancel", color = Palette.InkSoft) }
                }
            }
        )
    }
}

@Composable
private fun TopBar(vm: MainViewModel, done: Boolean, busy: Boolean, onBack: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            GlassCircleButton(onClick = onBack, size = 44.dp) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Palette.Ink, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (done) "All done" else "${vm.index + 1} of ${vm.queue.size}",
                    fontFamily = DisplayFont, fontSize = 22.sp, color = Palette.Ink
                )
                vm.queue.firstOrNull()?.let {
                    Text(it.bucketName, fontSize = 12.sp, color = Palette.InkSoft, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (vm.current?.isVideo == true) {
                GlassCircleButton(onClick = vm::toggleMute, size = 44.dp) {
                    Icon(
                        if (vm.muted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                        "Mute", tint = Palette.Ink, modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(Modifier.width(8.dp))
            }
            if (vm.pendingCount > 0) {
                GradientButton(
                    text = if (busy) "Saving…" else "Save ${vm.pendingCount}",
                    onClick = { vm.startCommit() },
                    enabled = !busy,
                    height = 40.dp
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        // progress
        val total = vm.queue.size.coerceAtLeast(1)
        val progress by animateFloatAsState((vm.index.toFloat() / total).coerceIn(0f, 1f), label = "progress")
        Box(Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.7f))) {
            Box(Modifier.fillMaxWidth(progress).fillMaxHeight().clip(RoundedCornerShape(50)).background(BrandGradientDeep))
        }
    }
}

/** Lets the bottom buttons trigger the same fly-away animation as a swipe. */
private class ButtonTrigger { var fire by mutableStateOf<ActionType?>(null) }
private val trigger = ButtonTrigger()

@Composable
private fun ActionBar(vm: MainViewModel, enabled: Boolean) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        GradientButton(
            text = "← ${vm.left?.name ?: "—"}",
            onClick = { trigger.fire = ActionType.LEFT },
            enabled = enabled && vm.left != null,
            brush = Brush.linearGradient(listOf(Palette.BlueDeep, Palette.Blue)),
            modifier = Modifier.weight(1f),
            height = 52.dp
        )
        GlassCircleButton(onClick = vm::undo, enabled = enabled && vm.history.isNotEmpty(), size = 46.dp) {
            Icon(Icons.AutoMirrored.Filled.Undo, "Undo", tint = Palette.InkSoft, modifier = Modifier.size(20.dp))
        }
        GlassCircleButton(onClick = { trigger.fire = ActionType.SKIP }, enabled = enabled, size = 46.dp) {
            Icon(Icons.Default.KeyboardArrowDown, "Skip", tint = Palette.InkSoft)
        }
        GlassCircleButton(onClick = { trigger.fire = ActionType.DELETE }, enabled = enabled, size = 46.dp) {
            Icon(Icons.Default.DeleteOutline, "Delete", tint = Palette.Rose, modifier = Modifier.size(22.dp))
        }
        GradientButton(
            text = "${vm.right?.name ?: "—"} →",
            onClick = { trigger.fire = ActionType.RIGHT },
            enabled = enabled && vm.right != null,
            brush = Brush.linearGradient(listOf(Palette.Pink, Palette.PinkDeep)),
            modifier = Modifier.weight(1f),
            height = 52.dp
        )
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
    val outer = RoundedCornerShape(34.dp)

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
            offset.animateTo(dest, tween(220, easing = FastOutLinearInEasing))
            currentOnSwiped(type)
        }

        // Button taps
        LaunchedEffect(trigger.fire, item.id) {
            val t = trigger.fire ?: return@LaunchedEffect
            trigger.fire = null
            if (enabled) flyAway(t)
        }

        val o = offset.value
        val horiz = abs(o.x) >= abs(o.y)
        val leftStrength = if (horiz && o.x < 0 && leftName != null) (-o.x / xThreshold).coerceIn(0f, 1f) else 0f
        val rightStrength = if (horiz && o.x > 0 && rightName != null) (o.x / xThreshold).coerceIn(0f, 1f) else 0f
        val deleteStrength = if (!horiz && o.y < 0) (-o.y / yThreshold).coerceIn(0f, 1f) else 0f
        val skipStrength = if (!horiz && o.y > 0) (o.y / yThreshold).coerceIn(0f, 1f) else 0f

        // The card: white frame around the media
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationX = o.x
                    translationY = o.y
                    rotationZ = (o.x / w) * 12f
                }
                .shadow(18.dp, outer, ambientColor = Palette.PinkDeep, spotColor = Palette.BlueDeep)
                .clip(outer)
                .background(Color.White)
                .padding(6.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(Palette.MediaBg)
        ) {
            key(item.id) { MediaView(item, muted) }

            // colour wash while dragging
            val wash = when {
                leftStrength > 0f -> Palette.BlueDeep.copy(alpha = 0.28f * leftStrength)
                rightStrength > 0f -> Palette.PinkDeep.copy(alpha = 0.28f * rightStrength)
                deleteStrength > 0f -> Palette.Rose.copy(alpha = 0.30f * deleteStrength)
                else -> Color.Transparent
            }
            Box(Modifier.matchParentSize().background(wash))

            // Info strip with soft fade
            Column(
                Modifier.align(Alignment.BottomStart).fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f))))
                    .padding(start = 18.dp, end = 18.dp, top = 28.dp, bottom = 16.dp)
            ) {
                Text(item.name, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.Medium, fontSize = 15.sp)
                Text(if (item.isVideo) "Video" else "Photo", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
            }

            Hint("${leftName ?: ""}", Palette.BlueDeep, leftStrength, Alignment.TopEnd)
            Hint("${rightName ?: ""}", Palette.PinkDeep, rightStrength, Alignment.TopStart)
            Hint("Delete", Palette.Rose, deleteStrength, Alignment.BottomCenter)
            Hint("Skip", Palette.InkSoft, skipStrength, Alignment.TopCenter)
        }

        // Transparent gesture layer on top (so the video view can't steal the touches)
        Box(
            Modifier.fillMaxSize().pointerInput(item.id, enabled) {
                if (!enabled) return@pointerInput
                detectDragGestures(
                    onDragEnd = {
                        scope.launch {
                            val type = classify(offset.value)
                            if (type == null) offset.animateTo(Offset.Zero, spring(dampingRatio = 0.6f, stiffness = 400f))
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
private fun BoxScope.Hint(text: String, color: Color, strength: Float, align: Alignment) {
    if (strength <= 0.05f) return
    Box(
        Modifier.align(align).padding(28.dp).alpha(strength).scale(0.8f + 0.2f * strength)
            .shadow(8.dp, RoundedCornerShape(50))
            .clip(RoundedCornerShape(50))
            .background(Color.White)
            .padding(horizontal = 20.dp, vertical = 10.dp)
    ) {
        Text(text, color = color, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, maxLines = 1,
            fontFamily = DisplayFont)
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
            Box(
                Modifier.align(Alignment.Center).size(64.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.85f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.PlayArrow, null, tint = if (failed) Palette.Rose else Palette.PinkDeep, modifier = Modifier.size(34.dp))
            }
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
        GlassCard(Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("✨", fontSize = 48.sp)
                Spacer(Modifier.height(8.dp))
                Text("Beautifully sorted", fontFamily = DisplayFont, fontSize = 28.sp, color = Palette.Ink)
                Spacer(Modifier.height(20.dp))
                vm.left?.let { StatRow(it.name, vm.doneLeft, Palette.BlueDeep, Palette.BlueSoft) }
                vm.right?.let { StatRow(it.name, vm.doneRight, Palette.PinkDeep, Palette.PinkSoft) }
                StatRow("Trash", vm.doneDeleted, Palette.Rose, Palette.Rose.copy(alpha = 0.12f))
                if (vm.pendingCount > 0) {
                    Spacer(Modifier.height(10.dp))
                    Text("${vm.pendingCount} change(s) not saved yet", color = Palette.InkSoft, fontSize = 13.sp)
                    Spacer(Modifier.height(10.dp))
                    GradientButton("Save now", { vm.startCommit() }, Modifier.fillMaxWidth())
                }
                Spacer(Modifier.height(14.dp))
                GradientButton(
                    "Back to albums", { vm.startCommit(exit = true) }, Modifier.fillMaxWidth(),
                    brush = Brush.linearGradient(listOf(Palette.Blue, Palette.Pink))
                )
            }
        }
    }
}

@Composable
private fun StatRow(name: String, count: Int, color: Color, soft: Color) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(16.dp)).background(soft)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(name, Modifier.weight(1f), color = Palette.Ink, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text("$count", color = color, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
    }
}
