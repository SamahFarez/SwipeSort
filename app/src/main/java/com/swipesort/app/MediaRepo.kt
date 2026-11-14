package com.swipesort.app

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.ContextCompat

data class MediaItem(
    val id: Long,
    val uri: Uri,
    val isVideo: Boolean,
    val name: String,
    val relativePath: String,
    val bucketId: Long,
    val bucketName: String,
    val dateAdded: Long,
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

    fun hasReadPermission(ctx: Context): Boolean = readPermissions().all {
        ContextCompat.checkSelfPermission(ctx, it) == PackageManager.PERMISSION_GRANTED
    }

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
            val iId = c.getColumnIndexOrThrow(projection[0])
            val iType = c.getColumnIndexOrThrow(projection[1])
            val iBucket = c.getColumnIndexOrThrow(projection[2])
            val iBucketName = c.getColumnIndexOrThrow(projection[3])
            val iPath = c.getColumnIndexOrThrow(projection[4])
            val iName = c.getColumnIndexOrThrow(projection[5])
            val iDate = c.getColumnIndexOrThrow(projection[6])
            val iVol = c.getColumnIndexOrThrow(projection[7])
            while (c.moveToNext()) {
                val id = c.getLong(iId)
                val isVideo = c.getInt(iType) == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO
                val vol = c.getString(iVol) ?: MediaStore.VOLUME_EXTERNAL_PRIMARY
                val base = if (isVideo) MediaStore.Video.Media.getContentUri(vol)
                else MediaStore.Images.Media.getContentUri(vol)
                out += MediaItem(
                    id = id,
                    uri = ContentUris.withAppendedId(base, id),
                    isVideo = isVideo,
                    name = c.getString(iName) ?: "",
                    relativePath = c.getString(iPath) ?: "",
                    bucketId = c.getLong(iBucket),
                    bucketName = c.getString(iBucketName) ?: "(no name)",
                    dateAdded = c.getLong(iDate),
                )
            }
        }
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
}
