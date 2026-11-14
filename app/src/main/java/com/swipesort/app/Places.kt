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
class PlaceDb(ctx: Context) : SQLiteOpenHelper(ctx, "places.db", null, 1) {

    data class Loc(val modified: Long, val point: GeoPoint?)

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE loc(id INTEGER PRIMARY KEY, modified INTEGER, lat REAL, lng REAL, has INTEGER)")
        db.execSQL("CREATE TABLE geo(k TEXT PRIMARY KEY, country TEXT, region TEXT, city TEXT)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS loc")
        db.execSQL("DROP TABLE IF EXISTS geo")
        onCreate(db)
    }

    fun allLocs(): Map<Long, Loc> {
        val out = HashMap<Long, Loc>()
        readableDatabase.rawQuery("SELECT id, modified, lat, lng, has FROM loc", null).use { c ->
            while (c.moveToNext()) {
                val p = if (c.getInt(4) == 1) GeoPoint(c.getDouble(2), c.getDouble(3)) else null
                out[c.getLong(0)] = Loc(c.getLong(1), p)
            }
        }
        return out
    }

    fun putLocs(rows: List<Triple<Long, Long, GeoPoint?>>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            rows.forEach { (id, modified, p) ->
                val v = ContentValues().apply {
                    put("id", id); put("modified", modified)
                    put("lat", p?.lat ?: 0.0); put("lng", p?.lng ?: 0.0); put("has", if (p != null) 1 else 0)
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

    /** Reads the GPS position stored inside the photo/video, or null. */
    fun readPoint(ctx: Context, item: MediaItem, canReadLocation: Boolean): GeoPoint? {
        if (!canReadLocation) return null
        val uri = try { MediaStore.setRequireOriginal(item.uri) } catch (e: Exception) { item.uri }
        val point: GeoPoint? = try {
            if (!item.isVideo) {
                ctx.contentResolver.openInputStream(uri)?.use { stream ->
                    ExifInterface(stream).latLong?.let { GeoPoint(it[0], it[1]) }
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
        return point?.takeIf { !(it.lat == 0.0 && it.lng == 0.0) }
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
