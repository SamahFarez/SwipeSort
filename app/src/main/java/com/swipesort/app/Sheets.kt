package com.swipesort.app

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private fun <T> Set<T>.toggle(x: T): Set<T> = if (x in this) this - x else this + x

private fun <K> topCounts(keys: List<K>, limit: Int): List<Pair<K, Int>> =
    keys.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.take(limit).map { it.key to it.value }

// ------------------------------------------------------------------ filters

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FilterSheet(
    base: List<MediaItem>,
    current: Filters,
    onApply: (Filters) -> Unit,
    onDismiss: () -> Unit,
) {
    val ctx = LocalContext.current
    val places = Library.places
    val cameras = Library.cameras
    var f by remember { mutableStateOf(current) }
    var naming by remember { mutableStateOf(false) }
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val resultCount = remember(f, base, places, cameras) { Meta.apply(base, f, places, cameras).size }
    val years = remember(base) { base.map { Meta.year(it) }.distinct().sortedDescending() }
    val countries = remember(base, places) { topCounts(base.map { places[it.id]?.country ?: Place.UNKNOWN }, 40) }
    val cities = remember(base, places, f.countries) {
        topCounts(base.mapNotNull { item ->
            val p = places[item.id] ?: return@mapNotNull null
            if (f.countries.isNotEmpty() && (p.country ?: Place.UNKNOWN) !in f.countries) null else p.local
        }, 40)
    }
    val albums = remember(base) { topCounts(base.map { it.bucketName }, 40) }
    val cams = remember(base, cameras) { topCounts(base.mapNotNull { cameras[it.id] }, 20) }
    val sources = remember(base) { topCounts(base.map { Meta.source(it) }, 20) }
    val hasVideos = remember(base) { base.any { it.isVideo } }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, containerColor = Color(0xFFFFFBFE)) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.92f)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Filters", fontFamily = DisplayFont, fontSize = 28.sp, color = Palette.Ink, modifier = Modifier.weight(1f))
                if (!f.isEmpty()) TextButton(onClick = { f = Filters() }) { Text("Clear all", color = Palette.Rose) }
            }
            LazyColumn(
                Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                item {
                    FilterSection("When · year") {
                        years.forEach { y -> Chip("$y", y in f.years, { f = f.copy(years = f.years.toggle(y)) }) }
                    }
                }
                item {
                    FilterSection("When · month (any year)") {
                        (1..12).forEach { m -> Chip(Meta.monthName(m), m in f.months, { f = f.copy(months = f.months.toggle(m)) }) }
                    }
                }
                item {
                    FilterSection("Time of day") {
                        TimeOfDay.entries.forEach { t -> Chip(t.label, t in f.times, { f = f.copy(times = f.times.toggle(t)) }) }
                    }
                }
                if (countries.isNotEmpty()) item {
                    FilterSection("Country") {
                        countries.forEach { (c, n) -> Chip("$c · $n", c in f.countries, { f = f.copy(countries = f.countries.toggle(c), cities = emptySet()) }) }
                    }
                }
                if (cities.isNotEmpty()) item {
                    FilterSection(if (f.countries.isEmpty()) "Village / city" else "Village / city in ${f.countries.joinToString()}") {
                        cities.forEach { (c, n) -> Chip("$c · $n", c in f.cities, { f = f.copy(cities = f.cities.toggle(c)) }) }
                    }
                }
                item {
                    FilterSection("Location info") {
                        LocFilter.entries.forEach { l -> Chip(l.label, f.location == l, { f = f.copy(location = l) }) }
                    }
                }
                if (albums.size > 1) item {
                    FilterSection("Album") {
                        albums.forEach { (a, n) -> Chip("$a · $n", a in f.albums, { f = f.copy(albums = f.albums.toggle(a)) }) }
                    }
                }
                item {
                    FilterSection("Shape") {
                        Shape.entries.forEach { s -> Chip(s.label, s in f.shapes, { f = f.copy(shapes = f.shapes.toggle(s)) }) }
                    }
                }
                if (hasVideos) item {
                    FilterSection("Video length") {
                        Length.entries.forEach { l -> Chip(l.label, l in f.lengths, { f = f.copy(lengths = f.lengths.toggle(l)) }) }
                    }
                }
                if (sources.size > 1) item {
                    FilterSection("Came from") {
                        sources.forEach { (s, n) -> Chip("${s.label} · $n", s in f.sources, { f = f.copy(sources = f.sources.toggle(s)) }) }
                    }
                }
                if (cams.isNotEmpty()) item {
                    FilterSection("Camera / phone") {
                        cams.forEach { (c, n) -> Chip("$c · $n", c in f.cameras, { f = f.copy(cameras = f.cameras.toggle(c)) }) }
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = { naming = true }, enabled = !f.isEmpty()) {
                    Icon(Icons.Default.BookmarkAdd, null, tint = if (f.isEmpty()) Palette.InkSoft else Palette.PinkDeep)
                    Spacer(Modifier.width(6.dp))
                    Text("Save as smart album", color = if (f.isEmpty()) Palette.InkSoft else Palette.PinkDeep)
                }
                Spacer(Modifier.weight(1f))
                GradientButton("Show $resultCount", { onApply(f); onDismiss() }, height = 50.dp)
            }
        }
    }

    if (naming) {
        NameDialog(
            title = "Name this smart album",
            hint = "e.g. Algeria summer reels",
            onDone = { name -> naming = false; Presets.save(ctx, Preset(name, f)); onApply(f); onDismiss() },
            onDismiss = { naming = false }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterSection(title: String, content: @Composable () -> Unit) {
    Column {
        SmallCapsLabel(title)
        Spacer(Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            content()
        }
    }
}

@Composable
fun NameDialog(title: String, hint: String, initial: String = "", onDone: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        shape = RoundedCornerShape(30.dp),
        title = { Text(title, fontFamily = DisplayFont, color = Palette.Ink) },
        text = {
            OutlinedTextField(
                value = name, onValueChange = { name = it }, singleLine = true,
                placeholder = { Text(hint) }, shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Palette.Pink, cursorColor = Palette.PinkDeep)
            )
        },
        confirmButton = {
            TextButton(onClick = { onDone(name.trim()) }, enabled = name.isNotBlank()) {
                Text("Save", color = Palette.PinkDeep, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = Palette.InkSoft) } }
    )
}

// ------------------------------------------------------------------ folder maker

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FolderMakerSheet(items: List<MediaItem>, defaultRoot: String, onDismiss: () -> Unit, onFinished: () -> Unit = {}) {
    val actions = LocalMediaActions.current
    val places = Library.places
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val anyPlaces = remember(items, places) { items.any { places.containsKey(it.id) } }
    var pattern by remember { mutableStateOf(if (anyPlaces) FolderPattern.COUNTRY else FolderPattern.MONTH) }
    var root by remember { mutableStateOf(defaultRoot) }
    var copy by remember { mutableStateOf(true) }

    val plan = remember(items, pattern, root, places) { FolderPlan.plan(items, pattern, root.trim(), places) }
    val folders = remember(plan) {
        plan.groupBy { it.second }.map { (path, list) -> path to list.size }.sortedBy { it.first.lowercase() }
    }
    val totalSize = remember(items) { items.sumOf { it.sizeBytes } }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, containerColor = Color(0xFFFFFBFE)) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.92f)) {
            Column(Modifier.padding(horizontal = 20.dp)) {
                Text("Make folders", fontFamily = DisplayFont, fontSize = 28.sp, color = Palette.Ink)
                Text("From ${items.size} items · real folders you can open from Claude, Instagram, CapCut or any file picker",
                    fontSize = 13.sp, color = Palette.InkSoft)
            }
            LazyColumn(
                Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    Column {
                        SmallCapsLabel("Organise by (from the photo's date & GPS)")
                        Spacer(Modifier.height(8.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            FolderPattern.entries.forEach { p -> Chip(p.label, pattern == p, { pattern = p }) }
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(pattern.example.replace("Name", root.ifBlank { "Pictures" }), fontSize = 12.sp, color = Palette.InkSoft)
                    }
                }
                item {
                    Column {
                        SmallCapsLabel(if (pattern == FolderPattern.ONE) "Folder name" else "Main folder (optional)")
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = root, onValueChange = { root = it }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("e.g. Algeria trip, Reels, Moments") },
                            shape = RoundedCornerShape(16.dp),
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Palette.Pink, cursorColor = Palette.PinkDeep,
                                unfocusedContainerColor = Color.White, focusedContainerColor = Color.White)
                        )
                    }
                }
                item {
                    Column {
                        SmallCapsLabel("How")
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Chip("Copy", copy, { copy = true }, leading = Icons.Default.ContentCopy)
                            Chip("Move", !copy, { copy = false }, leading = Icons.Default.DriveFileMove)
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            if (copy) "Originals stay in their albums. Uses about ${MediaRepo.formatSize(totalSize)} more storage."
                            else "Items leave their current album (no extra storage). Android asks you to confirm.",
                            fontSize = 12.sp, color = Palette.InkSoft
                        )
                    }
                }
                item { SmallCapsLabel("${folders.size} folder${if (folders.size == 1) "" else "s"} will be created") }
                items(folders.size.coerceAtMost(60)) { i ->
                    val (path, n) = folders[i]
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color.White)
                            .border(1.dp, Palette.Lilac, RoundedCornerShape(14.dp)).padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Folder, null, tint = Palette.Blue, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(
                            path.removePrefix("Pictures/").trimEnd('/').replace("/", "  ›  "),
                            Modifier.weight(1f), color = Palette.Ink, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                        Text("$n", color = Palette.PinkDeep, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    }
                }
                if (folders.size > 60) item { Text("…and ${folders.size - 60} more", color = Palette.InkSoft, fontSize = 12.sp) }
            }
            GradientButton(
                text = if (copy) "Copy into ${folders.size} folder${if (folders.size == 1) "" else "s"}"
                else "Move into ${folders.size} folder${if (folders.size == 1) "" else "s"}",
                onClick = {
                    if (copy) actions.copyToPaths(plan, onFinished) else actions.moveToPaths(plan, onFinished)
                    onDismiss()
                },
                enabled = plan.isNotEmpty() && (pattern != FolderPattern.ONE || root.isNotBlank()),
                modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp)
            )
        }
    }
}

/** Shown while a big copy/move job runs. */
@Composable
fun ProgressDialog(done: Int, total: Int) {
    AlertDialog(
        onDismissRequest = {},
        containerColor = Color.White,
        shape = RoundedCornerShape(30.dp),
        title = { Text("Making your folders…", fontFamily = DisplayFont, color = Palette.Ink) },
        text = {
            Column {
                LinearProgressIndicator(
                    progress = { if (total == 0) 0f else done.toFloat() / total },
                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(50)),
                    color = Palette.PinkDeep, trackColor = Palette.BlueSoft
                )
                Spacer(Modifier.height(10.dp))
                Text("$done / $total", color = Palette.InkSoft)
            }
        },
        confirmButton = {}
    )
}
