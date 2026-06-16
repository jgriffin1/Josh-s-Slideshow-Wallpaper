package josh.griff.joshsslideshowwallpaper.util

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import java.io.InputStream
import kotlin.math.max

object WallpaperHelper {
    private const val TAG = "WallpaperHelper"

    /**
     * Sets the wallpaper from a URI with smart downsampling.
     */
    fun setWallpaperFromUri(context: Context, uriString: String): Boolean {
        return try {
            val uri = Uri.parse(uriString)
            val wallpaperManager = WallpaperManager.getInstance(context)
            
            // Get screen dimensions to avoid loading massive bitmaps
            val displayMetrics = context.resources.displayMetrics
            val reqWidth = displayMetrics.widthPixels
            val reqHeight = displayMetrics.heightPixels

            val bitmap = decodeSampledBitmapFromUri(context, uri, reqWidth, reqHeight)
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

    private fun decodeSampledBitmapFromUri(context: Context, uri: Uri, reqWidth: Int, reqHeight: Int): Bitmap? {
        return try {
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            
            // First decode with inJustDecodeBounds=true to check dimensions
            var inputStream = context.contentResolver.openInputStream(uri)
            BitmapFactory.decodeStream(inputStream, null, options)
            inputStream?.close()

            // Calculate inSampleSize
            options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight)

            // Decode bitmap with inSampleSize set
            options.inJustDecodeBounds = false
            inputStream = context.contentResolver.openInputStream(uri)
            val bitmap = BitmapFactory.decodeStream(inputStream, null, options)
            inputStream?.close()
            
            bitmap
        } catch (e: Exception) {
            Log.e(TAG, "Error decoding bitmap", e)
            null
        }
    }

    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val (height: Int, width: Int) = options.outHeight to options.outWidth
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {
            val halfHeight: Int = height / 2
            val halfWidth: Int = width / 2

            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }
}
