package josh.griff.joshsslideshowwallpaper.util

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import java.io.InputStream

object WallpaperHelper {
    private const val TAG = "WallpaperHelper"

    /**
     * Sets the wallpaper from a URI. 
     * Includes a fallback mechanism to decode and set as bitmap if direct stream fails,
     * which can be more reliable on some devices.
     */
    fun setWallpaperFromUri(context: Context, uriString: String): Boolean {
        return try {
            val uri = Uri.parse(uriString)
            val wallpaperManager = WallpaperManager.getInstance(context)
            
            // Try setting as stream first (most efficient)
            val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
            if (inputStream != null) {
                try {
                    wallpaperManager.setStream(inputStream)
                    return true
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to set wallpaper via stream, trying bitmap", e)
                } finally {
                    inputStream.close()
                }
            }

            // Fallback: Decode to bitmap and set
            // This is sometimes needed if the stream is incompatible or for better scaling
            val bitmap = decodeSampledBitmapFromUri(context, uri)
            if (bitmap != null) {
                wallpaperManager.setBitmap(bitmap)
                return true
            }

            false
        } catch (e: Exception) {
            Log.e(TAG, "Error setting wallpaper from URI: $uriString", e)
            false
        }
    }

    private fun decodeSampledBitmapFromUri(context: Context, uri: Uri): Bitmap? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream.close()
            bitmap
        } catch (e: Exception) {
            Log.e(TAG, "Error decoding bitmap", e)
            null
        }
    }
}
