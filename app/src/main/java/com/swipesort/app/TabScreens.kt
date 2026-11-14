package com.swipesort.app

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

// ------------------------------------------------------------------ Albums tab

@Composable
fun AlbumsTab() {
    val nav = LocalNav.current
    val albums = Library.albums
    val items = Library.items
    val favCount = Library.favorites.count { it in Library.byId }
    val videoCount = remember(items) { items.count { it.isVideo } }
    val reelCount = remember(items) { items.count { MediaRepo.isReelReady(it) } }

    Column(Modifier.fillMaxSize()) {
        ScreenTitle("Albums", "${albums.size} albums", onBack = null)
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(vertical = 6.dp)) {
                    item { SmartCard("Favourites", favCount, Icons.Default.Favorite, listOf(Palette.Pink, Palette.PinkDeep)) { nav.push(Route.SmartRoute(Kind.FAVORITES)) } }
                    item { SmartCard("Videos", videoCount, Icons.Default.Videocam, listOf(Palette.BlueDeep, Palette.Blue)) { nav.push(Route.SmartRoute(Kind.VIDEOS)) } }
                    item { SmartCard("Reels 9:16", reelCount, Icons.Default.Movie, listOf(Palette.Blue, Palette.Pink)) { nav.push(Route.SmartRoute(Kind.REELS)) } }
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                SmartAlbumsRow()
            }
            items(albums.size, key = { albums[it].bucketId }) { i ->
                val a = albums[i]
                AlbumCard(a) { nav.push(Route.AlbumRoute(a.bucketId)) }
            }
        }
    }
}

/** Saved filters + built-in metadata albums (Screenshots, Night shots, Panoramas…). */
@Composable
private fun SmartAlbumsRow() {
    val nav = LocalNav.current
    val ctx = LocalContext.current
    val items = Library.items
    val places = Library.places
    val cameras = Library.cameras
    val saved = Presets.list
    var deleting by remember { mutableStateOf<String?>(null) }
    val all = remember(saved) { saved.map { it to true } + Presets.builtIn.map { it to false } }
    val counts = remember(all, items, places, cameras) { all.map { (p, _) -> Meta.apply(items, p.filters, places, cameras).size } }

    Column {
        SmallCapsLabel("Smart albums · from photo info", Modifier.padding(start = 2.dp, bottom = 8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(all.size) { i ->
                val (preset, isSaved) = all[i]
                val count = counts.getOrElse(i) { 0 }
                if (!isSaved && count == 0) return@items
                Row(
                    Modifier.clip(RoundedCornerShape(18.dp))
                        .background(if (isSaved) BrandGradientDeep else Brush.linearGradient(listOf(Color.White, Color.White)))
                        .combinedClickableCompat(
                            onClick = { nav.push(Route.FilterRoute(preset.name, preset.filters, isSaved)) },
                            onLongClick = { if (isSaved) deleting = preset.name }
                        )
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(if (isSaved) Icons.Default.AutoAwesome else Icons.Default.Bolt, null,
                        tint = if (isSaved) Color.White else Palette.PinkDeep, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(preset.name, color = if (isSaved) Color.White else Palette.Ink, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                    Spacer(Modifier.width(6.dp))
                    Text("$count", color = if (isSaved) Color.White.copy(alpha = 0.8f) else Palette.InkSoft, fontSize = 12.sp)
                }
            }
        }
    }

    deleting?.let { name ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            containerColor = Color.White,
            shape = RoundedCornerShape(30.dp),
            title = { Text("Delete \"$name\"?", fontFamily = DisplayFont, color = Palette.Ink) },
            text = { Text("Only the smart album is removed. Your photos stay.", color = Palette.InkSoft) },
            confirmButton = { TextButton(onClick = { Presets.delete(ctx, name); deleting = null }) { Text("Delete", color = Palette.Rose) } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel", color = Palette.InkSoft) } }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
private fun Modifier.combinedClickableCompat(onClick: () -> Unit, onLongClick: () -> Unit): Modifier =
    this.combinedClickable(onClick = onClick, onLongClick = onLongClick)

@Composable
fun FilterDetail(title: String, filters: Filters) {
    val nav = LocalNav.current
    BrowseScreen(
        title = title, subtitle = null, items = Library.items, defaultGroup = GroupBy.MONTH,
        onBack = { nav.pop() }, stateKey = "filter_$title", initialFilters = filters
    )
}

@Composable
private fun SmartCard(label: String, count: Int, icon: ImageVector, colors: List<Color>, onClick: () -> Unit) {
    Column(
        Modifier.width(140.dp).height(96.dp).clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(colors)).clickable(onClick = onClick).padding(14.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(22.dp))
        Column {
            Text(label, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            Text("$count", color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp)
        }
    }
}

@Composable
private fun AlbumCard(a: Album, onClick: () -> Unit) {
    Column(Modifier.clip(RoundedCornerShape(24.dp)).clickable(onClick = onClick)) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(1f)
                .shadow(8.dp, RoundedCornerShape(24.dp), ambientColor = Palette.Pink, spotColor = Palette.BlueDeep.copy(alpha = 0.3f))
                .clip(RoundedCornerShape(24.dp))
                .background(Brush.linearGradient(listOf(Palette.BlueSoft, Palette.PinkSoft)))
        ) {
            AsyncImage(a.coverUri, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
        Spacer(Modifier.height(8.dp))
        Text(a.name, fontFamily = DisplayFont, fontSize = 18.sp, color = Palette.Ink, maxLines = 1,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 4.dp))
        Text("${a.count}" + if (a.videoCount > 0) " · ${a.videoCount} videos" else "", fontSize = 12.sp,
            color = Palette.InkSoft, modifier = Modifier.padding(horizontal = 4.dp))
    }
}

@Composable
fun AlbumDetail(bucketId: Long) {
    val nav = LocalNav.current
    val vm = LocalVm.current
    val all = Library.items
    val items = remember(all, bucketId) { all.filter { it.bucketId == bucketId } }
    val name = items.firstOrNull()?.bucketName ?: Library.albums.firstOrNull { it.bucketId == bucketId }?.name ?: "Album"
    BrowseScreen(
        title = name, subtitle = null, items = items, defaultGroup = GroupBy.MONTH,
        onBack = { nav.pop() }, stateKey = "album_$bucketId",
        extraActions = {
            SoftIconButton(Icons.Default.Swipe, "Sort with swipes", tint = Palette.PinkDeep) {
                vm.updateSource(bucketId)
                nav.stack.clear()
                nav.tab = HomeTab.ORGANIZE
            }
        }
    )
}

@Composable
fun SmartDetail(kind: Kind) {
    val nav = LocalNav.current
    val all = Library.items
    val favs = Library.favorites
    val items = remember(all, favs, kind) { Grouping.filter(all, kind, favs) }
    BrowseScreen(
        title = kind.label.removePrefix("★ "), subtitle = null, items = items, defaultGroup = GroupBy.MONTH,
        onBack = { nav.pop() }, stateKey = "smart_${kind.name}"
    )
}

// ------------------------------------------------------------------ Places tab

data class CountryInfo(val name: String, val items: List<MediaItem>, val topPlaces: List<String>)

@Composable
fun PlacesTab() {
    val nav = LocalNav.current
    val ctx = LocalContext.current
    val items = Library.items
    val places = Library.places
    var hasLocation by remember { mutableStateOf(MediaRepo.hasLocationPermission(ctx)) }
    val locLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasLocation = granted
        if (granted) Library.refresh(ctx)
    }

    val countries = remember(items, places) {
        items.groupBy { places[it.id]?.country ?: Place.UNKNOWN }.map { (name, list) ->
            CountryInfo(
                name, list,
                list.mapNotNull { places[it.id]?.local }.groupingBy { it }.eachCount()
                    .entries.sortedByDescending { it.value }.take(3).map { it.key }
            )
        }.sortedWith(compareBy<CountryInfo> { it.name == Place.UNKNOWN }.thenByDescending { it.items.size })
    }
    val known = countries.filter { it.name != Place.UNKNOWN }

    Column(Modifier.fillMaxSize()) {
        ScreenTitle(
            "Places",
            if (known.isEmpty()) "Where your memories were made" else "${known.size} countr${if (known.size == 1) "y" else "ies"}",
            onBack = null
        )
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp, top = 4.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            if (!hasLocation) item {
                GlassCard(Modifier.fillMaxWidth()) {
                    Text("Allow location info", fontWeight = FontWeight.SemiBold, color = Palette.Ink, fontSize = 16.sp)
                    Spacer(Modifier.height(4.dp))
                    Text("SwipeSort reads where each photo and video was taken (stored inside the file) to sort them by country and village. Nothing leaves your phone except place-name lookups.",
                        fontSize = 13.sp, color = Palette.InkSoft)
                    Spacer(Modifier.height(10.dp))
                    GradientButton("Allow", { locLauncher.launch(Manifest.permission.ACCESS_MEDIA_LOCATION) }, Modifier.fillMaxWidth(), height = 46.dp)
                }
            }
            if (Library.indexing) item {
                GlassCard(Modifier.fillMaxWidth()) {
                    Text("Finding places…", fontFamily = DisplayFont, fontSize = 18.sp, color = Palette.Ink)
                    Spacer(Modifier.height(8.dp))
                    val total = Library.indexTotal.coerceAtLeast(1)
                    LinearProgressIndicator(
                        progress = { Library.indexDone.toFloat() / total },
                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(50)),
                        color = Palette.PinkDeep, trackColor = Palette.BlueSoft
                    )
                    Spacer(Modifier.height(6.dp))
                    Text("${Library.indexDone} / ${Library.indexTotal} · only slow the first time", fontSize = 12.sp, color = Palette.InkSoft)
                }
            } else if (Library.unnamedSpots > 0 && hasLocation) item {
                GlassCard(Modifier.fillMaxWidth()) {
                    Text("${Library.unnamedSpots} spots still need a name", fontWeight = FontWeight.SemiBold, color = Palette.Ink)
                    Text("Connect to the internet and tap retry to turn GPS positions into village & country names.",
                        fontSize = 13.sp, color = Palette.InkSoft)
                    TextButton(onClick = { Library.refresh(ctx) }) { Text("Retry", color = Palette.PinkDeep, fontWeight = FontWeight.SemiBold) }
                }
            }

            items(countries.size, key = { countries[it].name }) { i ->
                CountryCard(countries[i]) { nav.push(Route.CountryRoute(countries[i].name)) }
            }
        }
    }
}

@Composable
private fun CountryCard(c: CountryInfo, onClick: () -> Unit) {
    val unknown = c.name == Place.UNKNOWN
    Box(
        Modifier.fillMaxWidth().height(if (unknown) 110.dp else 180.dp)
            .shadow(10.dp, RoundedCornerShape(30.dp), ambientColor = Palette.Pink, spotColor = Palette.BlueDeep.copy(alpha = 0.3f))
            .clip(RoundedCornerShape(30.dp))
            .background(Brush.linearGradient(listOf(Palette.Blue, Palette.Pink)))
            .clickable(onClick = onClick)
    ) {
        if (!unknown) {
            AsyncImage(c.items.first().uri, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f)))))
        }
        Column(Modifier.align(Alignment.BottomStart).padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(if (unknown) Icons.Default.LocationOff else Icons.Default.LocationOn, null, tint = Color.White, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(c.name, fontFamily = DisplayFont, fontSize = 26.sp, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(
                (if (c.topPlaces.isNotEmpty()) c.topPlaces.joinToString(" · ") + "  —  " else "") + Grouping.countLabel(c.items),
                color = Color.White.copy(alpha = 0.9f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
        Box(
            Modifier.align(Alignment.TopEnd).padding(14.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.9f))
                .padding(horizontal = 10.dp, vertical = 4.dp)
        ) { Text("${c.items.size}", color = Palette.PinkDeep, fontWeight = FontWeight.SemiBold, fontSize = 12.sp) }
    }
}

@Composable
fun CountryDetail(country: String) {
    val nav = LocalNav.current
    val all = Library.items
    val places = Library.places
    val items = remember(all, places, country) { all.filter { (places[it.id]?.country ?: Place.UNKNOWN) == country } }
    BrowseScreen(
        title = country, subtitle = null, items = items,
        defaultGroup = if (country == Place.UNKNOWN) GroupBy.MONTH else GroupBy.CITY,
        onBack = { nav.pop() }, stateKey = "country_$country"
    )
}
