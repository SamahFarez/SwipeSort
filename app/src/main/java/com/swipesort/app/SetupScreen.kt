package com.swipesort.app

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

@Composable
fun SetupScreen(vm: MainViewModel) {
    val ctx = LocalContext.current
    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { vm.onPermissionResult() }

    LaunchedEffect(Unit) {
        if (!vm.hasPermission) permLauncher.launch(MediaRepo.readPermissions())
    }

    var picking by remember { mutableStateOf<String?>(null) } // "source", "left", "right"

    if (!vm.hasPermission) {
        Column(
            Modifier.fillMaxSize().systemBarsPadding().padding(28.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            BrandTitle()
            Spacer(Modifier.height(24.dp))
            Text(
                "To sort your gallery, SwipeSort needs to see your photos and videos.",
                color = Palette.Ink, fontSize = 16.sp
            )
            Spacer(Modifier.height(24.dp))
            GradientButton("Allow access", { permLauncher.launch(MediaRepo.readPermissions()) }, Modifier.fillMaxWidth())
        }
        return
    }

    val sourceAlbum = vm.albums.firstOrNull { it.bucketId == vm.sourceBucket }
    val sourceCount = vm.sourceItems().size

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Column(Modifier.padding(top = 16.dp, bottom = 8.dp)) {
                BrandTitle()
                Text(
                    "Your gallery, sorted one swipe at a time.",
                    color = Palette.InkSoft, fontSize = 15.sp, fontStyle = FontStyle.Italic,
                    fontFamily = DisplayFont
                )
            }
        }

        if (vm.loading) item {
            Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Palette.PinkDeep)
            }
        }

        // ---- Album to sort ----
        item {
            GlassCard(Modifier.fillMaxWidth()) {
                SmallCapsLabel("Album to sort")
                Spacer(Modifier.height(10.dp))
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).clickable { picking = "source" },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Thumb(sourceAlbum?.coverUri, Icons.Default.PhotoLibrary, Brush.linearGradient(listOf(Palette.BlueSoft, Palette.PinkSoft)), 60)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            sourceAlbum?.name ?: "Choose an album",
                            fontFamily = DisplayFont, fontSize = 22.sp, color = Palette.Ink,
                            maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            sourceAlbum?.let { "${it.count} items · ${it.videoCount} videos" } ?: "Tap to pick",
                            fontSize = 13.sp, color = Palette.InkSoft
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MediaFilter.entries.forEach { f ->
                        PillToggle(
                            text = when (f) { MediaFilter.ALL -> "All"; MediaFilter.PHOTOS -> "Photos"; MediaFilter.VIDEOS -> "Videos" },
                            selected = vm.filter == f,
                            onClick = { vm.updateFilter(f) }
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Newest first", Modifier.weight(1f), color = Palette.Ink, fontSize = 15.sp)
                    Switch(
                        checked = vm.newestFirst, onCheckedChange = vm::updateNewestFirst,
                        colors = SwitchDefaults.colors(
                            checkedTrackColor = Palette.Pink, checkedThumbColor = Color.White,
                            uncheckedTrackColor = Palette.BlueSoft, uncheckedThumbColor = Palette.Blue,
                            uncheckedBorderColor = Color.Transparent
                        )
                    )
                }
            }
        }

        // ---- Destinations ----
        item {
            GlassCard(Modifier.fillMaxWidth()) {
                SmallCapsLabel("Your swipes")
                Spacer(Modifier.height(10.dp))
                DestinationRow(
                    arrowIcon = Icons.AutoMirrored.Filled.ArrowBack,
                    direction = "Swipe left",
                    target = vm.left,
                    tint = Palette.BlueDeep,
                    soft = Palette.BlueSoft,
                    onClick = { picking = "left" }
                )
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    HorizontalDivider(color = Palette.Lilac)
                    GlassCircleButton(onClick = vm::swapSides, size = 36.dp) {
                        Icon(Icons.Default.SwapVert, "Swap left and right", tint = Palette.PinkDeep, modifier = Modifier.size(20.dp))
                    }
                }
                DestinationRow(
                    arrowIcon = Icons.AutoMirrored.Filled.ArrowForward,
                    direction = "Swipe right",
                    target = vm.right,
                    tint = Palette.PinkDeep,
                    soft = Palette.PinkSoft,
                    onClick = { picking = "right" }
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LegendChip("↑  delete", Palette.Rose)
                    LegendChip("↓  skip", Palette.InkSoft)
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "Deleted items go to your phone's Trash for 30 days.",
                    fontSize = 12.sp, color = Palette.InkSoft
                )
            }
        }

        // ---- Fast mode ----
        if (Build.VERSION.SDK_INT >= 31 && !vm.canManageMedia) item {
            GlassCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(34.dp).clip(CircleShape).background(BrandGradient),
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Default.AutoAwesome, null, tint = Color.White, modifier = Modifier.size(18.dp)) }
                    Spacer(Modifier.width(12.dp))
                    Text("Turn on fast mode", fontWeight = FontWeight.SemiBold, color = Palette.Ink, fontSize = 16.sp)
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "Allow \"Media management\" for SwipeSort and Android stops asking you to confirm every batch of moves.",
                    fontSize = 13.sp, color = Palette.InkSoft
                )
                Spacer(Modifier.height(4.dp))
                TextButton(onClick = {
                    ctx.startActivity(Intent(Settings.ACTION_REQUEST_MANAGE_MEDIA, Uri.parse("package:${ctx.packageName}")))
                }) { Text("Open settings", color = Palette.PinkDeep, fontWeight = FontWeight.SemiBold) }
            }
        }

        item {
            Spacer(Modifier.height(4.dp))
            GradientButton(
                text = if (sourceCount > 0) "Start sorting  ·  $sourceCount" else "Start sorting",
                onClick = vm::startSession,
                enabled = sourceCount > 0 && (vm.left != null || vm.right != null),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(28.dp))
        }
    }

    when (picking) {
        "source" -> AlbumPickerDialog(
            albums = vm.albums,
            onPick = { vm.updateSource(it.bucketId); picking = null },
            onDismiss = { picking = null }
        )
        "left", "right" -> TargetPickerDialog(
            title = if (picking == "left") "Swipe left sends to…" else "Swipe right sends to…",
            targets = vm.allTargets(),
            onPick = { t ->
                if (picking == "left") vm.updateLeft(t) else vm.updateRight(t)
                picking = null
            },
            onCreate = { name -> vm.createAlbum(name) },
            onDismiss = { picking = null }
        )
    }
}

@OptIn(ExperimentalTextApi::class)
@Composable
fun BrandTitle() {
    Text(
        "SwipeSort",
        style = TextStyle(
            brush = BrandGradientDeep,
            fontFamily = DisplayFont,
            fontSize = 42.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = (-0.5).sp
        )
    )
}

@Composable
private fun Thumb(uri: Uri?, icon: ImageVector, bg: Brush, sizeDp: Int) {
    Box(
        Modifier.size(sizeDp.dp).clip(RoundedCornerShape(18.dp)).background(bg),
        contentAlignment = Alignment.Center
    ) {
        if (uri != null) AsyncImage(uri, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else Icon(icon, null, tint = Palette.PinkDeep)
    }
}

@Composable
private fun DestinationRow(
    arrowIcon: ImageVector,
    direction: String,
    target: Target?,
    tint: Color,
    soft: Color,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).clickable(onClick = onClick).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(46.dp).clip(CircleShape).background(soft), contentAlignment = Alignment.Center) {
            Icon(arrowIcon, null, tint = tint)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(direction.uppercase(), fontSize = 11.sp, letterSpacing = 1.5.sp, color = tint, fontWeight = FontWeight.SemiBold)
            Text(
                target?.name ?: "Choose an album",
                fontFamily = DisplayFont, fontSize = 20.sp,
                color = if (target != null) Palette.Ink else Palette.InkSoft,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
        Text("Change", fontSize = 13.sp, color = Palette.InkSoft)
    }
}

@Composable
private fun PillToggle(text: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Box(
        Modifier.clip(shape)
            .background(if (selected) BrandGradientDeep else Brush.linearGradient(listOf(Color.White, Color.White)))
            .border(1.dp, if (selected) Color.Transparent else Palette.Lilac, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 8.dp)
    ) {
        Text(text, color = if (selected) Color.White else Palette.Ink, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun LegendChip(text: String, color: Color) {
    Box(
        Modifier.clip(RoundedCornerShape(50)).background(color.copy(alpha = 0.12f))
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) { Text(text, color = color, fontSize = 13.sp, fontWeight = FontWeight.Medium) }
}

@Composable
private fun AlbumPickerDialog(
    albums: List<Album>,
    onPick: (Album) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        shape = RoundedCornerShape(30.dp),
        title = { Text("Album to sort", fontFamily = DisplayFont, color = Palette.Ink) },
        text = {
            LazyColumn(Modifier.heightIn(max = 480.dp)) {
                items(albums, key = { it.bucketId }) { a ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable { onPick(a) }.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Thumb(a.coverUri, Icons.Default.PhotoLibrary, Brush.linearGradient(listOf(Palette.BlueSoft, Palette.PinkSoft)), 48)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(a.name, fontWeight = FontWeight.SemiBold, color = Palette.Ink)
                            Text("${a.count} items · ${a.videoCount} videos", fontSize = 12.sp, color = Palette.InkSoft)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close", color = Palette.PinkDeep) } }
    )
}

@Composable
fun TargetPickerDialog(
    title: String,
    targets: List<Target>,
    onPick: (Target?) -> Unit,
    onCreate: (String) -> Target?,
    onDismiss: () -> Unit,
) {
    var creating by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }

    if (creating) {
        AlertDialog(
            onDismissRequest = { creating = false },
            containerColor = Color.White,
            shape = RoundedCornerShape(30.dp),
            title = { Text("New album", fontFamily = DisplayFont, color = Palette.Ink) },
            text = {
                Column {
                    OutlinedTextField(
                        value = newName, onValueChange = { newName = it },
                        singleLine = true, label = { Text("Album name") },
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Palette.Pink, focusedLabelColor = Palette.PinkDeep,
                            cursorColor = Palette.PinkDeep, unfocusedBorderColor = Palette.Lilac
                        )
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("Created in Pictures/ when the first item is moved there.", fontSize = 12.sp, color = Palette.InkSoft)
                }
            },
            confirmButton = {
                TextButton(onClick = { onCreate(newName)?.let { onPick(it) } }, enabled = newName.isNotBlank()) {
                    Text("Create", color = Palette.PinkDeep, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = { TextButton(onClick = { creating = false }) { Text("Cancel", color = Palette.InkSoft) } }
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        shape = RoundedCornerShape(30.dp),
        title = { Text(title, fontFamily = DisplayFont, color = Palette.Ink) },
        text = {
            LazyColumn(Modifier.heightIn(max = 480.dp)) {
                item {
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                            .background(Brush.linearGradient(listOf(Palette.BlueSoft, Palette.PinkSoft)))
                            .clickable { creating = true }.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Add, null, tint = Palette.PinkDeep)
                        Spacer(Modifier.width(12.dp))
                        Text("Create new album", color = Palette.Ink, fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(Modifier.height(8.dp))
                }
                items(targets, key = { it.relativePath }) { t ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable { onPick(t) }.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Folder, null, tint = Palette.Blue)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(t.name, fontWeight = FontWeight.SemiBold, color = Palette.Ink)
                            Text(t.relativePath, fontSize = 12.sp, color = Palette.InkSoft)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close", color = Palette.PinkDeep) } },
        dismissButton = { TextButton(onClick = { onPick(null) }) { Text("Clear", color = Palette.InkSoft) } }
    )
}
