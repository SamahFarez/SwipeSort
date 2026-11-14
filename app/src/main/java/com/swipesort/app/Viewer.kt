package com.swipesort.app

import android.media.MediaPlayer
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.VideoView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ViewerScreen(ids: List<Long>, start: Int, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val vm = LocalVm.current
    val actions = LocalMediaActions.current
    val byId = Library.byId
    val items = remember(ids, byId) { ids.mapNotNull { byId[it] } }

    if (items.isEmpty()) {
        LaunchedEffect(Unit) { onClose() }
        return
    }

    val pager = rememberPagerState(initialPage = start.coerceIn(0, items.lastIndex)) { items.size }
    var chrome by remember { mutableStateOf(true) }
    var zoomed by remember { mutableStateOf(false) }
    var showInfo by remember { mutableStateOf(false) }
    var moving by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    val current = items.getOrNull(pager.currentPage.coerceIn(0, items.lastIndex)) ?: items.first()
    val place = Library.places[current.id]

    Box(Modifier.fillMaxSize().background(Color(0xFF0E0C16))) {
        HorizontalPager(
            state = pager,
            userScrollEnabled = !zoomed,
            key = { items[it].id },
            modifier = Modifier.fillMaxSize()
        ) { page ->
            val item = items[page]
            if (item.isVideo) {
                VideoPage(item, playing = pager.settledPage == page, onTap = { chrome = !chrome })
            } else {
                ZoomableImage(item, onTap = { chrome = !chrome }, onZoomChanged = { zoomed = it })
            }
        }

        // ---- top bar ----
        AnimatedVisibility(chrome, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.TopCenter)) {
            Row(
                Modifier.fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.6f), Color.Transparent)))
                    .statusBarsPadding().padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White) }
                Column(Modifier.weight(1f)) {
                    Text(Grouping.fullDate(current.dateTaken), color = Color.White, fontFamily = DisplayFont, fontSize = 18.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(listOfNotNull(place?.label, current.bucketName).joinToString("  ·  "),
                        color = Color.White.copy(alpha = 0.75f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                IconButton(onClick = { showInfo = true }) { Icon(Icons.Default.Info, "Info", tint = Color.White) }
            }
        }

        // ---- bottom bar ----
        AnimatedVisibility(chrome, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.BottomCenter)) {
            Column(
                Modifier.fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.65f))))
                    .navigationBarsPadding().padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
                if (MediaRepo.isReelReady(current)) {
                    Text(
                        "✦ Reels-ready 9:16", color = Color.White, fontSize = 12.sp,
                        modifier = Modifier.align(Alignment.CenterHorizontally).clip(RoundedCornerShape(50))
                            .background(BrandGradientDeep).padding(horizontal = 12.dp, vertical = 4.dp)
                    )
                    Spacer(Modifier.height(10.dp))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    val fav = current.id in Library.favorites
                    ViewerAction(Icons.Default.Share, "Share") { MediaRepo.share(ctx, listOf(current), instagram = false) }
                    ViewerAction(Icons.AutoMirrored.Filled.Send, "Instagram", highlight = true) {
                        MediaRepo.share(ctx, listOf(current), instagram = true)
                    }
                    ViewerAction(if (fav) Icons.Default.Favorite else Icons.Default.FavoriteBorder, "Favourite",
                        tint = if (fav) Palette.Pink else Color.White) { Library.toggleFavorite(ctx, current.id) }
                    ViewerAction(Icons.Default.CreateNewFolder, "Move") { moving = true }
                    ViewerAction(Icons.Default.DeleteOutline, "Delete", tint = Palette.Rose) { confirmDelete = true }
                }
            }
        }
    }

    if (showInfo) InfoDialog(current, place) { showInfo = false }
    if (moving) {
        TargetPickerDialog(
            title = "Move to…",
            targets = vm.allTargets(),
            onPick = { t -> moving = false; if (t != null) actions.moveTo(listOf(current), t) },
            onCreate = { vm.createAlbum(it) },
            onDismiss = { moving = false }
        )
    }
    if (confirmDelete) {
        ConfirmDeleteDialog(1, onConfirm = { confirmDelete = false; actions.trash(listOf(current)) }, onDismiss = { confirmDelete = false })
    }
}

@Composable
private fun ViewerAction(icon: ImageVector, label: String, tint: Color = Color.White, highlight: Boolean = false, onClick: () -> Unit) {
    Column(
        Modifier.clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier.size(44.dp).clip(CircleShape)
                .background(if (highlight) BrandGradientDeep else Brush.linearGradient(listOf(Color.White.copy(alpha = 0.14f), Color.White.copy(alpha = 0.14f)))),
            contentAlignment = Alignment.Center
        ) { Icon(icon, label, tint = tint, modifier = Modifier.size(22.dp)) }
        Spacer(Modifier.height(4.dp))
        Text(label, color = Color.White.copy(alpha = 0.85f), fontSize = 11.sp)
    }
}

@Composable
private fun ZoomableImage(item: MediaItem, onTap: () -> Unit, onZoomChanged: (Boolean) -> Unit) {
    var scale by remember(item.id) { mutableFloatStateOf(1f) }
    var offset by remember(item.id) { mutableStateOf(Offset.Zero) }

    Box(
        Modifier.fillMaxSize()
            .pointerInput(item.id) {
                detectTapGestures(
                    onTap = { onTap() },
                    onDoubleTap = {
                        if (scale > 1f) { scale = 1f; offset = Offset.Zero; onZoomChanged(false) }
                        else { scale = 2.5f; onZoomChanged(true) }
                    }
                )
            }
            .then(
                if (scale > 1f) Modifier.pointerInput(item.id, "zoom") {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(1f, 5f)
                        offset = if (scale <= 1f) Offset.Zero else offset + pan
                        onZoomChanged(scale > 1f)
                    }
                } else Modifier.pointerInput(item.id, "pinch") {
                    // pinch to start zooming (single-finger drags still go to the pager)
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            if (event.changes.size >= 2) { scale = 1.2f; onZoomChanged(true) }
                        }
                    }
                }
            )
    ) {
        AsyncImage(
            item.uri, item.name,
            Modifier.fillMaxSize().graphicsLayer {
                scaleX = scale; scaleY = scale
                translationX = offset.x; translationY = offset.y
            },
            contentScale = ContentScale.Fit
        )
    }
}

@Composable
private fun VideoPage(item: MediaItem, playing: Boolean, onTap: () -> Unit) {
    var player by remember(item.id) { mutableStateOf<MediaPlayer?>(null) }
    var paused by remember(item.id) { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        if (playing) {
            key(item.id) {
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
                            vv.setOnPreparedListener { mp -> mp.isLooping = true; player = mp; vv.start() }
                            vv.setVideoURI(item.uri)
                        }
                    },
                    update = { frame ->
                        val vv = frame.getChildAt(0) as? VideoView
                        if (vv != null && player != null) {
                            if (paused && vv.isPlaying) vv.pause() else if (!paused && !vv.isPlaying) vv.start()
                        }
                    },
                    onRelease = { frame ->
                        (frame.getChildAt(0) as? VideoView)?.stopPlayback()
                        player = null
                    }
                )
            }
        }
        if (!playing || player == null) {
            AsyncImage(item.uri, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
        }
        // tap layer
        Box(Modifier.fillMaxSize().pointerInput(item.id) {
            detectTapGestures(onTap = { onTap() }, onDoubleTap = { paused = !paused })
        })
        if (playing && player != null && paused) {
            Box(
                Modifier.align(Alignment.Center).size(70.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.85f))
                    .clickable { paused = false },
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Default.PlayArrow, "Play", tint = Palette.PinkDeep, modifier = Modifier.size(40.dp)) }
        }
    }
}

@Composable
private fun InfoDialog(item: MediaItem, place: Place?, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        shape = RoundedCornerShape(30.dp),
        title = { Text("Details", fontFamily = DisplayFont, color = Palette.Ink) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                InfoRow(Icons.Default.Event, Grouping.fullDate(item.dateTaken))
                InfoRow(Icons.Default.LocationOn, place?.let { listOfNotNull(it.city, it.region, it.country).distinct().joinToString(", ") } ?: "No location")
                InfoRow(Icons.Default.Folder, "${item.bucketName}  ·  ${item.relativePath}")
                InfoRow(Icons.Default.Image, item.name)
                InfoRow(Icons.Default.AspectRatio, "${item.width} × ${item.height}  ·  ${MediaRepo.aspectLabel(item)}")
                InfoRow(Icons.Default.Storage, MediaRepo.formatSize(item.sizeBytes) +
                    if (item.isVideo) "  ·  ${MediaRepo.formatDuration(item.durationMs)}" else "")
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close", color = Palette.PinkDeep) } }
    )
}

@Composable
private fun InfoRow(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(32.dp).clip(CircleShape).background(Palette.PinkSoft), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = Palette.PinkDeep, modifier = Modifier.size(16.dp))
        }
        Spacer(Modifier.width(12.dp))
        Text(text, color = Palette.Ink, fontSize = 14.sp)
    }
}
