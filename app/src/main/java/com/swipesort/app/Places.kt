package com.swipesort.app

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.location.Address
import android.location.Geocoder
import android.media.MediaMetadataRetriever
import android.os.Build
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.coroutines.resume

/** Where a photo/video was taken. */
data class Place(val country: String?, val region: String?, val city: String?) {
    /** Village / town, or region if unknown. */
    val local: String? get() = city ?: region
    val label: String get() = listOfNotNull(local, country).distinct().joinToString(", ").ifEmpty { UNKNOWN }

    companion object { const val UNKNOWN = "Unknown place" }
}

data class GeoPoint(val lat: Double, val lng: Double)

/** Small SQLite cache so GPS reading & place names only happen once per file. */
private const val LOC_TABLE =
    "CREATE TABLE loc(id INTEGER PRIMARY KEY, modified INTEGER, lat REAL, lng REAL, has INTEGER, camera TEXT)"

class PlaceDb(ctx: Context) : SQLiteOpenHelper(ctx, "places.db", null, 2) {

    data class Loc(val modified: Long, val point: GeoPoint?, val camera: String?)

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(LOC_TABLE)
        db.execSQL("CREATE TABLE geo(k TEXT PRIMARY KEY, country TEXT, region TEXT, city TEXT)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // v2 adds the camera model: re-read files, keep the place names we already looked up
        db.execSQL("DROP TABLE IF EXISTS loc")
        db.execSQL(LOC_TABLE)
    }

    fun allLocs(): Map<Long, Loc> {
        val out = HashMap<Long, Loc>()
        readableDatabase.rawQuery("SELECT id, modified, lat, lng, has, camera FROM loc", null).use { c ->
            while (c.moveToNext()) {
                val p = if (c.getInt(4) == 1) GeoPoint(c.getDouble(2), c.getDouble(3)) else null
                out[c.getLong(0)] = Loc(c.getLong(1), p, if (c.isNull(5)) null else c.getString(5))
            }
        }
        return out
    }

    fun putLocs(rows: List<Pair<Long, Loc>>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            rows.forEach { (id, loc) ->
                val p = loc.point
                val v = ContentValues().apply {
                    put("id", id); put("modified", loc.modified)
                    put("lat", p?.lat ?: 0.0); put("lng", p?.lng ?: 0.0); put("has", if (p != null) 1 else 0)
                    put("camera", loc.camera)
                }
                db.insertWithOnConflict("loc", null, v, SQLiteDatabase.CONFLICT_REPLACE)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun allGeo(): Map<String, Place> {
        val out = HashMap<String, Place>()
        readableDatabase.rawQuery("SELECT k, country, region, city FROM geo", null).use { c ->
            while (c.moveToNext()) out[c.getString(0)] = Place(c.getString(1), c.getString(2), c.getString(3))
        }
        return out
    }

    fun putGeo(key: String, place: Place) {
        val v = ContentValues().apply {
            put("k", key); put("country", place.country); put("region", place.region); put("city", place.city)
        }
        writableDatabase.insertWithOnConflict("geo", null, v, SQLiteDatabase.CONFLICT_REPLACE)
    }
}

object PlaceReader {

    /** ~1 km grid key, so nearby photos share one place lookup. */
    fun key(p: GeoPoint): String = "%.2f,%.2f".format(Locale.US, p.lat, p.lng)

    /** Reads the GPS position (and camera model) stored inside the photo/video. */
    fun readMeta(ctx: Context, item: MediaItem): Pair<GeoPoint?, String?> {
        val uri = try { MediaStore.setRequireOriginal(item.uri) } catch (e: Exception) { item.uri }
        var camera: String? = null
        val point: GeoPoint? = try {
            if (!item.isVideo) {
                ctx.contentResolver.openInputStream(uri)?.use { stream ->
                    val exif = ExifInterface(stream)
                    camera = cameraName(exif.getAttribute(ExifInterface.TAG_MAKE), exif.getAttribute(ExifInterface.TAG_MODEL))
                    exif.latLong?.let { GeoPoint(it[0], it[1]) }
                }
            } else {
                val r = MediaMetadataRetriever()
                try {
                    r.setDataSource(ctx, uri)
                    parseIso6709(r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_LOCATION))
                } finally {
                    try { r.release() } catch (_: Exception) {}
                }
            }
        } catch (e: Exception) {
            null
        }
        return point?.takeIf { !(it.lat == 0.0 && it.lng == 0.0) } to camera
    }

    private fun cameraName(make: String?, model: String?): String? {
        val mk = make?.trim().orEmpty()
        val md = model?.trim().orEmpty()
        if (md.isEmpty()) return mk.ifEmpty { null }
        return if (mk.isEmpty() || md.lowercase().startsWith(mk.lowercase())) md
        else mk.replaceFirstChar { it.uppercase() } + " " + md
    }

    /** Video location looks like "+36.7538+003.0588/" */
    private fun parseIso6709(s: String?): GeoPoint? {
        if (s.isNullOrBlank()) return null
        val m = Regex("([+-]\\d+(?:\\.\\d+)?)([+-]\\d+(?:\\.\\d+)?)").find(s) ?: return null
        return GeoPoint(m.groupValues[1].toDouble(), m.groupValues[2].toDouble())
    }

    /** Turns coordinates into country / region / village names (needs internet on most phones). */
    suspend fun reverseGeocode(ctx: Context, p: GeoPoint): Place? {
        if (!Geocoder.isPresent()) return null
        val geocoder = Geocoder(ctx, Locale.getDefault())
        val address: Address? = try {
            if (Build.VERSION.SDK_INT >= 33) {
                withTimeoutOrNull(8000) {
                    suspendCancellableCoroutine<Address?> { cont ->
                        geocoder.getFromLocation(p.lat, p.lng, 1, object : Geocoder.GeocodeListener {
                            override fun onGeocode(addresses: MutableList<Address>) {
                                if (cont.isActive) cont.resume(addresses.firstOrNull())
                            }
                            override fun onError(errorMessage: String?) {
                                if (cont.isActive) cont.resume(null)
                            }
                        })
                    }
                }
            } else {
                @Suppress("DEPRECATION")
                geocoder.getFromLocation(p.lat, p.lng, 1)?.firstOrNull()
            }
        } catch (e: Exception) {
            null
        }
        address ?: return null
        val city = address.locality ?: address.subLocality ?: address.subAdminArea
        return Place(address.countryName, address.adminArea, city)
    }
}
