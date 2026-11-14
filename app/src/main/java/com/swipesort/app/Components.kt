package com.swipesort.app

import android.content.Context
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
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
import kotlinx.coroutines.launch

// ------------------------------------------------------------------ navigation

enum class HomeTab(val label: String) { PHOTOS("Photos"), ALBUMS("Albums"), PLACES("Places"), ORGANIZE("Organize") }

sealed interface Route {
    data class AlbumRoute(val bucketId: Long) : Route
    data class CountryRoute(val country: String) : Route
    data class SmartRoute(val kind: Kind) : Route
    data class FilterRoute(val title: String, val filters: Filters, val saved: Boolean) : Route
    data class ViewerRoute(val ids: List<Long>, val start: Int) : Route
}

class NavState {
    var tab by mutableStateOf(HomeTab.PHOTOS)
    val stack = mutableStateListOf<Route>()
    fun push(r: Route) { stack.add(r) }
    fun pop() { if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex) }
    fun openViewer(list: List<MediaItem>, item: MediaItem) {
        push(Route.ViewerRoute(list.map { it.id }, list.indexOfFirst { it.id == item.id }.coerceAtLeast(0)))
    }
}

val LocalNav = staticCompositionLocalOf<NavState> { error("No nav") }
val LocalVm = staticCompositionLocalOf<MainViewModel> { error("No vm") }

// ------------------------------------------------------------------ small pieces

@Composable
fun ScreenTitle(title: String, subtitle: String?, onBack: (() -> Unit)?, actions: @Composable RowScope.() -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        if (onBack != null) {
            GlassCircleButton(onClick = onBack, size = 42.dp) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Palette.Ink, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, fontFamily = DisplayFont, fontSize = if (onBack == null) 34.sp else 26.sp, color = Palette.Ink,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) Text(subtitle, fontSize = 13.sp, color = Palette.InkSoft, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        actions()
    }
}

@Composable
fun SoftIconButton(icon: ImageVector, desc: String, tint: Color = Palette.Ink, onClick: () -> Unit) {
    GlassCircleButton(onClick = onClick, size = 40.dp) {
        Icon(icon, desc, tint = tint, modifier = Modifier.size(20.dp))
    }
}

@Composable
fun Chip(text: String, selected: Boolean, onClick: () -> Unit, leading: ImageVector? = null) {
    val shape = RoundedCornerShape(50)
    Row(
        Modifier.clip(shape)
            .background(if (selected) BrandGradientDeep else Brush.linearGradient(listOf(Color.White.copy(alpha = 0.85f), Color.White.copy(alpha = 0.85f))))
            .border(1.dp, if (selected) Color.Transparent else Color.White, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (leading != null) {
            Icon(leading, null, tint = if (selected) Color.White else Palette.PinkDeep, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(text, color = if (selected) Color.White else Palette.Ink, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}

// ------------------------------------------------------------------ grid

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MediaTile(
    item: MediaItem,
    selected: Boolean,
    selecting: Boolean,
    favorite: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val scale by animateFloatAsState(if (selected) 0.86f else 1f, label = "sel")
    Box(
        Modifier.aspectRatio(1f)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        Box(
            Modifier.fillMaxSize().scale(scale).clip(RoundedCornerShape(if (selected) 16.dp else 8.dp))
                .background(Brush.linearGradient(listOf(Palette.BlueSoft, Palette.PinkSoft)))
        ) {
            AsyncImage(item.uri, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            if (item.isVideo) {
                Row(
                    Modifier.align(Alignment.BottomEnd).padding(5.dp).clip(RoundedCornerShape(50))
                        .background(Color.Black.copy(alpha = 0.45f)).padding(horizontal = 6.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.PlayArrow, null, tint = Color.White, modifier = Modifier.size(12.dp))
                    Text(MediaRepo.formatDuration(item.durationMs), color = Color.White, fontSize = 10.sp)
                }
            }
            if (favorite) {
                Icon(Icons.Default.Favorite, null, tint = Color.White,
                    modifier = Modifier.align(Alignment.BottomStart).padding(5.dp).size(14.dp))
            }
        }
        if (selecting) {
            Box(
                Modifier.align(Alignment.TopStart).padding(6.dp).size(22.dp).clip(CircleShape)
                    .background(if (selected) BrandGradientDeep else Brush.linearGradient(listOf(Color.White.copy(alpha = 0.6f), Color.White.copy(alpha = 0.6f))))
                    .border(1.5.dp, Color.White, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (selected) Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(14.dp))
            }
        }
    }
}

@Composable
fun SectionHeader(s: Section, onSelectAll: (() -> Unit)?) {
    Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 18.dp, bottom = 8.dp), verticalAlignment = Alignment.Bottom) {
        Column(Modifier.weight(1f)) {
            Text(s.title, fontFamily = DisplayFont, fontSize = 21.sp, color = Palette.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (s.subtitle != null) Text(s.subtitle, fontSize = 12.sp, color = Palette.InkSoft, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (onSelectAll != null) {
            Text("Select", color = Palette.PinkDeep, fontSize = 13.sp, fontWeight = FontWeight.Medium,
                modifier = Modifier.clip(RoundedCornerShape(50)).clickable(onClick = onSelectAll).padding(horizontal = 10.dp, vertical = 4.dp))
        }
    }
}

@Composable
fun MediaGrid(
    sections: List<Section>,
    state: LazyGridState,
    columns: Int,
    selection: SnapshotStateList<Long>,
    onOpen: (MediaItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val favorites = Library.favorites
    val selecting = selection.isNotEmpty()
    fun toggle(id: Long) { if (id in selection) selection.remove(id) else selection.add(id) }
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        state = state,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 32.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        sections.forEach { s ->
            item(key = "h:" + s.key, span = { GridItemSpan(maxLineSpan) }, contentType = "header") {
                SectionHeader(s, onSelectAll = if (sections.size > 1) ({
                    val ids = s.items.map { it.id }
                    if (selection.containsAll(ids)) selection.removeAll(ids.toSet())
                    else ids.forEach { if (it !in selection) selection.add(it) }
                    Unit
                }) else null)
            }
            items(s.items.size, key = { i -> s.items[i].id }, contentType = { "media" }) { i ->
                val m = s.items[i]
                MediaTile(
                    item = m,
                    selected = m.id in selection,
                    selecting = selecting,
                    favorite = m.id in favorites,
                    onClick = { if (selecting) toggle(m.id) else onOpen(m) },
                    onLongClick = { toggle(m.id) }
                )
            }
        }
    }
}

// ------------------------------------------------------------------ selection actions

@Composable
fun SelectionBar(selected: List<MediaItem>, onClear: () -> Unit) {
    val ctx = LocalContext.current
    val actions = LocalMediaActions.current
    val vm = LocalVm.current
    var moving by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var folderMaker by remember { mutableStateOf(false) }

    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp)
            .clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.92f))
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onClear) { Icon(Icons.Default.Close, "Cancel", tint = Palette.Ink) }
        Text("${selected.size} selected", Modifier.weight(1f), color = Palette.Ink, fontWeight = FontWeight.SemiBold)
        IconButton(onClick = { MediaRepo.share(ctx, selected, instagram = true) }) {
            Icon(Icons.AutoMirrored.Filled.Send, "Instagram", tint = Palette.PinkDeep)
        }
        IconButton(onClick = { MediaRepo.share(ctx, selected, instagram = false) }) {
            Icon(Icons.Default.Share, "Share", tint = Palette.Ink)
        }
        IconButton(onClick = { selected.forEach { Library.toggleFavorite(ctx, it.id) } }) {
            Icon(Icons.Default.FavoriteBorder, "Favourite", tint = Palette.Ink)
        }
        IconButton(onClick = { folderMaker = true }) { Icon(Icons.Default.CreateNewFolder, "Make folders", tint = Palette.BlueDeep) }
        IconButton(onClick = { moving = true }) { Icon(Icons.Default.DriveFileMove, "Move", tint = Palette.BlueDeep) }
        IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Default.DeleteOutline, "Delete", tint = Palette.Rose) }
    }

    if (moving) {
        TargetPickerDialog(
            title = "Move ${selected.size} to…",
            targets = vm.allTargets(),
            onPick = { t -> moving = false; if (t != null) actions.moveTo(selected, t, onClear) },
            onCreate = { vm.createAlbum(it) },
            onDismiss = { moving = false }
        )
    }
    if (confirmDelete) {
        ConfirmDeleteDialog(selected.size, onConfirm = { confirmDelete = false; actions.trash(selected, onClear) }, onDismiss = { confirmDelete = false })
    }
    if (folderMaker) {
        FolderMakerSheet(selected, defaultRoot = "", onDismiss = { folderMaker = false }, onFinished = onClear)
    }
}

@Composable
fun ConfirmDeleteDialog(count: Int, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        shape = RoundedCornerShape(30.dp),
        title = { Text("Delete $count item${if (count > 1) "s" else ""}?", fontFamily = DisplayFont, color = Palette.Ink) },
        text = { Text("They go to your phone's Trash and can be restored for 30 days.", color = Palette.InkSoft) },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Delete", color = Palette.Rose, fontWeight = FontWeight.SemiBold) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = Palette.InkSoft) } }
    )
}

// ------------------------------------------------------------------ the reusable browse screen

/**
 * A grid of photos/videos that can be grouped (month, country, village, album…),
 * filtered (photos, videos, reels, favourites) and searched.
 * Used for the Photos tab, every album, every country and the smart collections.
 */
@Composable
fun BrowseScreen(
    title: String,
    subtitle: String?,
    items: List<MediaItem>,
    defaultGroup: GroupBy,
    onBack: (() -> Unit)?,
    stateKey: String,
    initialFilters: Filters = Filters(),
    extraActions: @Composable RowScope.() -> Unit = {},
) {
    val nav = LocalNav.current
    val scope = rememberCoroutineScope()
    val prefs = LocalContext.current.getSharedPreferences("swipesort", Context.MODE_PRIVATE)

    var group by remember(stateKey) {
        mutableStateOf(runCatching { GroupBy.valueOf(prefs.getString("group_$stateKey", null) ?: defaultGroup.name) }.getOrDefault(defaultGroup))
    }
    var kind by remember(stateKey) { mutableStateOf(Kind.ALL) }
    var filters by remember(stateKey) { mutableStateOf(initialFilters) }
    var filterSheet by remember { mutableStateOf(false) }
    var folderSheet by remember { mutableStateOf(false) }
    var query by remember(stateKey) { mutableStateOf("") }
    var searching by remember(stateKey) { mutableStateOf(false) }
    var columns by remember { mutableIntStateOf(prefs.getInt("columns", 4)) }
    var groupMenu by remember { mutableStateOf(false) }
    val selection = remember(stateKey) { mutableStateListOf<Long>() }
    val gridState = rememberLazyGridState()

    val places = Library.places
    val favorites = Library.favorites
    val cameras = Library.cameras
    val filtered = remember(items, kind, favorites, query, places, filters, cameras) {
        Grouping.search(Meta.apply(Grouping.filter(items, kind, favorites), filters, places, cameras), query, places)
    }
    val sections = remember(filtered, group, places) { Grouping.group(filtered, group, places) }
    val headerIdx = remember(sections) { Grouping.headerIndices(sections) }
    val flat = remember(sections) { sections.flatMap { it.items } }

    androidx.activity.compose.BackHandler(enabled = selection.isNotEmpty()) { selection.clear() }

    Column(Modifier.fillMaxSize()) {
        if (selection.isNotEmpty()) {
            val selectedItems = remember(selection.toList(), items) { val s = selection.toSet(); items.filter { it.id in s } }
            SelectionBar(selectedItems, onClear = { selection.clear() })
        } else {
            ScreenTitle(title, subtitle ?: Grouping.countLabel(items), onBack) {
                extraActions()
                Spacer(Modifier.width(6.dp))
                SoftIconButton(Icons.Default.CreateNewFolder, "Make folders", tint = Palette.BlueDeep) { folderSheet = true }
                Spacer(Modifier.width(6.dp))
                SoftIconButton(Icons.Default.Search, "Search", tint = if (searching) Palette.PinkDeep else Palette.Ink) {
                    searching = !searching; if (!searching) query = ""
                }
                Spacer(Modifier.width(6.dp))
                SoftIconButton(Icons.Default.GridView, "Grid size") {
                    columns = if (columns >= 5) 3 else columns + 1
                    prefs.edit().putInt("columns", columns).apply()
                }
            }
        }

        if (searching) {
            OutlinedTextField(
                value = query, onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                placeholder = { Text("Country, village, album, month…") },
                leadingIcon = { Icon(Icons.Default.Search, null, tint = Palette.PinkDeep) },
                singleLine = true,
                shape = RoundedCornerShape(50),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color.White, unfocusedContainerColor = Color.White.copy(alpha = 0.85f),
                    focusedBorderColor = Palette.Pink, unfocusedBorderColor = Color.White, cursorColor = Palette.PinkDeep
                )
            )
        }

        // group + filter chips
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Chip(
                    if (filters.isEmpty()) "Filters" else "Filters · ${filters.count}",
                    selected = !filters.isEmpty(), onClick = { filterSheet = true }, leading = Icons.Default.Tune
                )
            }
            item {
                Box {
                    Chip("Group: ${group.label}", selected = true, onClick = { groupMenu = true }, leading = Icons.Default.Layers)
                    DropdownMenu(expanded = groupMenu, onDismissRequest = { groupMenu = false }) {
                        GroupBy.entries.forEach { g ->
                            DropdownMenuItem(
                                text = { Text(g.label, color = if (g == group) Palette.PinkDeep else Palette.Ink,
                                    fontWeight = if (g == group) FontWeight.SemiBold else FontWeight.Normal) },
                                onClick = {
                                    group = g; groupMenu = false
                                    prefs.edit().putString("group_$stateKey", g.name).apply()
                                    scope.launch { gridState.scrollToItem(0) }
                                }
                            )
                        }
                    }
                }
            }
            items(Kind.entries.size) { i ->
                val k = Kind.entries[i]
                Chip(k.label, selected = kind == k, onClick = { kind = k })
            }
        }

        // active filters, tap to remove
        val active = remember(filters) { Meta.activeChips(filters) }
        if (active.isNotEmpty()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(active.size) { i ->
                    val (label, without) = active[i]
                    Row(
                        Modifier.clip(RoundedCornerShape(50)).background(Palette.PinkSoft)
                            .clickable { filters = without }.padding(start = 10.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(label, fontSize = 12.sp, color = Palette.Ink)
                        Spacer(Modifier.width(4.dp))
                        Icon(Icons.Default.Close, "Remove", tint = Palette.PinkDeep, modifier = Modifier.size(14.dp))
                    }
                }
            }
        }

        // jump bar: one chip per section (months, countries, villages…)
        if (sections.size > 1) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                itemsIndexed(sections, key = { _, s -> s.key }) { i, s ->
                    val label = if (group == GroupBy.MONTH) Grouping.shortMonth(s.items.first().dateTaken)
                    else if (group == GroupBy.COUNTRY_MONTH) "${s.title} · ${Grouping.shortMonth(s.items.first().dateTaken)}"
                    else s.title
                    Text(
                        label, fontSize = 12.sp, color = Palette.Ink,
                        modifier = Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.55f))
                            .clickable { scope.launch { gridState.scrollToItem(headerIdx[i]) } }
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }
        }

        if (Library.loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Palette.PinkDeep) }
        } else if (filtered.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text(
                    if (kind == Kind.FAVORITES) "No favourites yet.\nTap ♡ on a photo to add it."
                    else if (!filters.isEmpty()) "Nothing matches these filters."
                    else "Nothing here yet.",
                    color = Palette.InkSoft, fontFamily = DisplayFont, fontSize = 18.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        } else {
            MediaGrid(sections, gridState, columns, selection, onOpen = { nav.openViewer(flat, it) })
        }
    }

    if (filterSheet) {
        FilterSheet(
            base = remember(items, kind, favorites) { Grouping.filter(items, kind, favorites) },
            current = filters,
            onApply = { filters = it; scope.launch { gridState.scrollToItem(0) } },
            onDismiss = { filterSheet = false }
        )
    }
    if (folderSheet) {
        FolderMakerSheet(filtered, defaultRoot = if (onBack == null) "" else title, onDismiss = { folderSheet = false })
    }
}
