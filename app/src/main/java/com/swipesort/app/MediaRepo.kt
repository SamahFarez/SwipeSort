package com.swipesort.app

import android.Manifest
import android.content.ClipData
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

data class MediaItem(
    val id: Long,
    val uri: Uri,
    val isVideo: Boolean,
    val name: String,
    val relativePath: String,
    val bucketId: Long,
    val bucketName: String,
    val dateAdded: Long,      // seconds
    val dateTaken: Long,      // milliseconds (falls back to dateAdded)
    val dateModified: Long,   // seconds
    val durationMs: Long,
    val width: Int,
    val height: Int,
    val sizeBytes: Long,
)

data class Album(
    val bucketId: Long,
    val name: String,
    val relativePath: String,
    val count: Int,
    val videoCount: Int,
    val coverUri: Uri?,
)

/** A destination album = a folder on the phone. */
data class Target(val name: String, val relativePath: String)

object MediaRepo {

    fun readPermissions(): Array<String> =
        if (Build.VERSION.SDK_INT >= 33)
            arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
        else arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)

    /** Read access + permission to see where photos were taken. */
    fun allPermissions(): Array<String> = readPermissions() + Manifest.permission.ACCESS_MEDIA_LOCATION

    fun hasReadPermission(ctx: Context): Boolean = readPermissions().all {
        ContextCompat.checkSelfPermission(ctx, it) == PackageManager.PERMISSION_GRANTED
    }

    fun hasLocationPermission(ctx: Context): Boolean =
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_MEDIA_LOCATION) == PackageManager.PERMISSION_GRANTED

    /** "Fast mode": with MANAGE_MEDIA the system approves move/trash requests without a pop-up. */
    fun canManageMedia(ctx: Context): Boolean =
        Build.VERSION.SDK_INT >= 31 && MediaStore.canManageMedia(ctx)

    fun loadAll(ctx: Context): List<MediaItem> {
        val out = ArrayList<MediaItem>()
        val filesUri = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.MEDIA_TYPE,
            MediaStore.MediaColumns.BUCKET_ID,
            MediaStore.MediaColumns.BUCKET_DISPLAY_NAME,
            MediaStore.MediaColumns.RELATIVE_PATH,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.DATE_ADDED,
            MediaStore.MediaColumns.VOLUME_NAME,
            MediaStore.MediaColumns.DATE_TAKEN,
            MediaStore.MediaColumns.DATE_MODIFIED,
            MediaStore.MediaColumns.DURATION,
            MediaStore.MediaColumns.WIDTH,
            MediaStore.MediaColumns.HEIGHT,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.ORIENTATION,
        )
        val selection = "${MediaStore.Files.FileColumns.MEDIA_TYPE} IN (?, ?)"
        val args = arrayOf(
            MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString(),
            MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString(),
        )
        ctx.contentResolver.query(
            filesUri, projection, selection, args,
            "${MediaStore.MediaColumns.DATE_ADDED} DESC"
        )?.use { c ->
            val idx = projection.map { c.getColumnIndexOrThrow(it) }
            while (c.moveToNext()) {
                val id = c.getLong(idx[0])
                val isVideo = c.getInt(idx[1]) == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO
                val vol = c.getString(idx[7]) ?: MediaStore.VOLUME_EXTERNAL_PRIMARY
                val base = if (isVideo) MediaStore.Video.Media.getContentUri(vol)
                else MediaStore.Images.Media.getContentUri(vol)
                val added = c.getLong(idx[6])
                val taken = if (c.isNull(idx[8])) 0L else c.getLong(idx[8])
                val orientation = if (c.isNull(idx[14])) 0 else c.getInt(idx[14])
                var w = if (c.isNull(idx[11])) 0 else c.getInt(idx[11])
                var h = if (c.isNull(idx[12])) 0 else c.getInt(idx[12])
                if (orientation == 90 || orientation == 270) { val t = w; w = h; h = t }
                out += MediaItem(
                    id = id,
                    uri = ContentUris.withAppendedId(base, id),
                    isVideo = isVideo,
                    name = c.getString(idx[5]) ?: "",
                    relativePath = c.getString(idx[4]) ?: "",
                    bucketId = c.getLong(idx[2]),
                    bucketName = c.getString(idx[3]) ?: "(no name)",
                    dateAdded = added,
                    dateTaken = if (taken > 0) taken else added * 1000,
                    dateModified = if (c.isNull(idx[9])) 0L else c.getLong(idx[9]),
                    durationMs = if (c.isNull(idx[10])) 0L else c.getLong(idx[10]),
                    width = w,
                    height = h,
                    sizeBytes = if (c.isNull(idx[13])) 0L else c.getLong(idx[13]),
                )
            }
        }
        out.sortByDescending { it.dateTaken }
        return out
    }

    fun albumsFrom(items: List<MediaItem>): List<Album> =
        items.groupBy { it.bucketId }.map { (bucket, list) ->
            Album(
                bucketId = bucket,
                name = list.first().bucketName,
                relativePath = list.first().relativePath,
                count = list.size,
                videoCount = list.count { it.isVideo },
                coverUri = list.first().uri,
            )
        }.sortedByDescending { it.count }

    /** Vertical ~9:16, ready for Reels / Stories. */
    fun isReelReady(item: MediaItem): Boolean {
        if (item.width <= 0 || item.height <= 0) return false
        val ratio = item.height.toFloat() / item.width
        return ratio > 1.6f && ratio < 1.95f
    }

    fun aspectLabel(item: MediaItem): String {
        if (item.width <= 0 || item.height <= 0) return "—"
        val r = max(item.width, item.height).toFloat() / min(item.width, item.height)
        val portrait = item.height > item.width
        return when {
            abs(r - 1f) < 0.05f -> "1:1 · square post"
            abs(r - 16f / 9f) < 0.1f -> if (portrait) "9:16 · Reels / Story ready" else "16:9 · landscape"
            abs(r - 5f / 4f) < 0.06f -> if (portrait) "4:5 · ideal feed post" else "5:4"
            abs(r - 4f / 3f) < 0.06f -> if (portrait) "3:4 · feed post" else "4:3"
            else -> "%.2f:1".format(r)
        }
    }

    /**
     * Android only allows photos in DCIM/ or Pictures/, and videos in DCIM/, Movies/ or Pictures/.
     * If the target folder isn't allowed for this item, fall back to Pictures/<album>/.
     */
    fun pathFor(item: MediaItem, target: Target): String {
        val top = target.relativePath.substringBefore('/')
        val allowed = if (item.isVideo) setOf("DCIM", "Movies", "Pictures") else setOf("DCIM", "Pictures")
        return if (top in allowed) target.relativePath else "Pictures/${target.name}/"
    }

    /** Moves the file into another folder (= album). Needs write access granted first. */
    fun move(ctx: Context, item: MediaItem, relativePath: String): Boolean = try {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
        }
        ctx.contentResolver.update(item.uri, values, null, null) > 0
    } catch (e: Exception) {
        false
    }

    fun newAlbumPath(name: String): String = "Pictures/${name.trim()}/"

    /** Copies a file into another folder. The original stays where it is. No permission pop-up needed. */
    fun copy(ctx: Context, item: MediaItem, relativePath: String): Boolean {
        val resolver = ctx.contentResolver
        val collection = if (item.isVideo) MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        else MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, item.name)
            put(MediaStore.MediaColumns.MIME_TYPE, resolver.getType(item.uri) ?: if (item.isVideo) "video/mp4" else "image/jpeg")
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val newUri = try { resolver.insert(collection, values) } catch (e: Exception) { null } ?: return false
        return try {
            // read the original so the GPS location is kept in the copy
            val src = if (hasLocationPermission(ctx)) {
                try { MediaStore.setRequireOriginal(item.uri) } catch (e: Exception) { item.uri }
            } else item.uri
            resolver.openInputStream(src).use { input ->
                resolver.openOutputStream(newUri).use { output ->
                    if (input == null || output == null) error("Cannot open file")
                    input.copyTo(output)
                }
            }
            resolver.update(newUri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
            true
        } catch (e: Exception) {
            runCatching { resolver.delete(newUri, null, null) }
            false
        }
    }

    /** Share to Instagram directly if installed, otherwise the normal share sheet. */
    fun share(ctx: Context, items: List<MediaItem>, instagram: Boolean) {
        if (items.isEmpty()) return
        val uris = ArrayList(items.map { it.uri })
        val mime = when {
            items.all { it.isVideo } -> "video/*"
            items.none { it.isVideo } -> "image/*"
            else -> "*/*"
        }
        val intent = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris[0])
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
        }
        intent.type = mime
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        val clip = ClipData.newRawUri("", uris[0])
        uris.drop(1).forEach { clip.addItem(ClipData.Item(it)) }
        intent.clipData = clip

        if (instagram) {
            intent.setPackage("com.instagram.android")
            try {
                ctx.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return
            } catch (e: Exception) {
                intent.setPackage(null)
            }
        }
        ctx.startActivity(Intent.createChooser(intent, "Share").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun formatDuration(ms: Long): String {
        val s = ms / 1000
        return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, (s / 60) % 60, s % 60)
        else "%d:%02d".format(s / 60, s % 60)
    }

    fun formatSize(bytes: Long): String = when {
        bytes >= 1L shl 30 -> "%.1f GB".format(bytes / (1L shl 30).toDouble())
        bytes >= 1L shl 20 -> "%.1f MB".format(bytes / (1L shl 20).toDouble())
        else -> "%d KB".format(bytes / 1024)
    }
}
