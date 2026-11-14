package com.swipesort.app

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

@OptIn(ExperimentalMaterial3Api::class)
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

    Scaffold(
        topBar = { TopAppBar(title = { Text("SwipeSort", fontWeight = FontWeight.Bold) }) }
    ) { pad ->
        if (!vm.hasPermission) {
            Column(
                Modifier.fillMaxSize().padding(pad).padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("SwipeSort needs access to your photos and videos to sort them.")
                Spacer(Modifier.height(16.dp))
                Button(onClick = { permLauncher.launch(MediaRepo.readPermissions()) }) { Text("Allow access") }
            }
            return@Scaffold
        }

        val sourceAlbum = vm.albums.firstOrNull { it.bucketId == vm.sourceBucket }
        val sourceCount = vm.sourceItems().size

        LazyColumn(
            Modifier.fillMaxSize().padding(pad).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (vm.loading) item {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            item { SectionLabel("1. Album to sort") }
            item {
                PickerCard(
                    icon = Icons.Default.PhotoLibrary,
                    title = sourceAlbum?.name ?: "Choose an album",
                    subtitle = sourceAlbum?.let { "${it.count} items · ${it.videoCount} videos" } ?: "Tap to pick",
                    cover = sourceAlbum?.coverUri,
                    onClick = { picking = "source" }
                )
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MediaFilter.entries.forEach { f ->
                        FilterChip(
                            selected = vm.filter == f,
                            onClick = { vm.updateFilter(f) },
                            label = { Text(when (f) { MediaFilter.ALL -> "All"; MediaFilter.PHOTOS -> "Photos"; MediaFilter.VIDEOS -> "Videos" }) }
                        )
                    }
                }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Newest first", Modifier.weight(1f))
                    Switch(checked = vm.newestFirst, onCheckedChange = vm::updateNewestFirst)
                }
            }

            item { HorizontalDivider() }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionLabel("2. Where swipes send things", Modifier.weight(1f))
                    IconButton(onClick = vm::swapSides) { Icon(Icons.Default.SwapHoriz, "Swap left and right") }
                }
            }
            item {
                PickerCard(
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    title = vm.left?.name ?: "Not set",
                    subtitle = "Swipe LEFT moves to · ${vm.left?.relativePath ?: "tap to choose"}",
                    onClick = { picking = "left" }
                )
            }
            item {
                PickerCard(
                    icon = Icons.AutoMirrored.Filled.ArrowForward,
                    title = vm.right?.name ?: "Not set",
                    subtitle = "Swipe RIGHT moves to · ${vm.right?.relativePath ?: "tap to choose"}",
                    onClick = { picking = "right" }
                )
            }
            item {
                Text(
                    "Swipe UP = delete (goes to the phone's Trash, recoverable for 30 days)\nSwipe DOWN = skip / keep where it is",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (Build.VERSION.SDK_INT >= 31 && !vm.canManageMedia) item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Bolt, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Turn on fast mode", fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Without it, Android asks you to confirm each batch of moves/deletes. " +
                                "Allow \"Media management\" for SwipeSort to skip the pop-ups.",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(Modifier.height(8.dp))
                        TextButton(onClick = {
                            ctx.startActivity(
                                Intent(Settings.ACTION_REQUEST_MANAGE_MEDIA, Uri.parse("package:${ctx.packageName}"))
                            )
                        }) { Text("Open settings") }
                    }
                }
            }

            item {
                Button(
                    onClick = vm::startSession,
                    enabled = sourceCount > 0 && (vm.left != null || vm.right != null),
                    modifier = Modifier.fillMaxWidth().height(56.dp)
                ) { Text(if (sourceCount > 0) "Start sorting ($sourceCount)" else "Start sorting") }
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    when (picking) {
        "source" -> AlbumPickerDialog(
            title = "Album to sort",
            albums = vm.albums,
            onPick = { vm.updateSource(it.bucketId); picking = null },
            onDismiss = { picking = null }
        )
        "left", "right" -> TargetPickerDialog(
            title = if (picking == "left") "Swipe LEFT goes to…" else "Swipe RIGHT goes to…",
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

@Composable
private fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier.padding(top = 8.dp), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
}

@Composable
private fun PickerCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    cover: Uri? = null,
    onClick: () -> Unit,
) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (cover != null) {
                AsyncImage(
                    model = cover, contentDescription = null, contentScale = ContentScale.Crop,
                    modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp))
                )
            } else {
                Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) { Icon(icon, null) }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun AlbumPickerDialog(
    title: String,
    albums: List<Album>,
    onPick: (Album) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            LazyColumn(Modifier.heightIn(max = 480.dp)) {
                items(albums, key = { it.bucketId }) { a ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onPick(a) }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AsyncImage(
                            model = a.coverUri, contentDescription = null, contentScale = ContentScale.Crop,
                            modifier = Modifier.size(44.dp).clip(RoundedCornerShape(8.dp))
                        )
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(a.name, fontWeight = FontWeight.SemiBold)
                            Text("${a.count} items · ${a.videoCount} videos", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

@Composable
private fun TargetPickerDialog(
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
            title = { Text("New album") },
            text = {
                Column {
                    OutlinedTextField(
                        value = newName, onValueChange = { newName = it },
                        singleLine = true, label = { Text("Album name") }
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("It will be created in Pictures/ when the first item is moved there.",
                        style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                TextButton(onClick = { onCreate(newName)?.let { onPick(it) } }, enabled = newName.isNotBlank()) { Text("Create") }
            },
            dismissButton = { TextButton(onClick = { creating = false }) { Text("Cancel") } }
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            LazyColumn(Modifier.heightIn(max = 480.dp)) {
                item {
                    Row(
                        Modifier.fillMaxWidth().clickable { creating = true }.padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Add, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Text("Create new album", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                    }
                    HorizontalDivider()
                }
                items(targets, key = { it.relativePath }) { t ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onPick(t) }.padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Folder, null)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(t.name, fontWeight = FontWeight.SemiBold)
                            Text(t.relativePath, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        dismissButton = { TextButton(onClick = { onPick(null) }) { Text("Clear") } }
    )
}
