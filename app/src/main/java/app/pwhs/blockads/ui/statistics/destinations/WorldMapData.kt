package app.pwhs.blockads.ui.statistics.destinations

import android.content.Context
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import org.json.JSONArray
import timber.log.Timber

/**
 * Representation of a country on the 1000x500 World Map coordinate plane.
 */
data class CountryShape(
    val iso: String,
    val name: String,
    val path: Path,
    val centroid: Offset
)

/**
 * Loads and caches vector polygon paths for the world map from assets.
 */
object WorldMapData {

    private const val ASSET_NAME = "preset/world_map_polygons.json"

    @Volatile
    private var cachedShapes: List<CountryShape>? = null

    fun load(context: Context): List<CountryShape> {
        cachedShapes?.let { return it }

        return synchronized(this) {
            cachedShapes?.let { return it }

            try {
                val jsonString = context.assets.open(ASSET_NAME).bufferedReader().use { it.readText() }
                val array = JSONArray(jsonString)
                val list = ArrayList<CountryShape>(array.length())

                for (i in 0 until array.length()) {
                    val item = array.getJSONObject(i)
                    val iso = item.getString("iso")
                    val name = item.optString("name", iso)
                    val polys = item.getJSONArray("polys")

                    val composePath = Path()
                    var sumX = 0.0
                    var sumY = 0.0
                    var pointCount = 0

                    for (p in 0 until polys.length()) {
                        val ring = polys.getJSONArray(p)
                        if (ring.length() == 0) continue

                        for (k in 0 until ring.length()) {
                            val pt = ring.getJSONArray(k)
                            val x = pt.getDouble(0).toFloat()
                            val y = pt.getDouble(1).toFloat()

                            sumX += x
                            sumY += y
                            pointCount++

                            if (k == 0) {
                                composePath.moveTo(x, y)
                            } else {
                                composePath.lineTo(x, y)
                            }
                        }
                        composePath.close()
                    }

                    val centroid = if (pointCount > 0) {
                        Offset((sumX / pointCount).toFloat(), (sumY / pointCount).toFloat())
                    } else {
                        Offset(500f, 250f)
                    }

                    list.add(CountryShape(iso = iso, name = name, path = composePath, centroid = centroid))
                }

                cachedShapes = list
                Timber.d("Loaded ${list.size} country shapes for World Map")
                list
            } catch (e: Exception) {
                Timber.e(e, "Failed to load WorldMapData from assets")
                emptyList()
            }
        }
    }
}
