package josh.griff.joshsslideshowwallpaper.worker

import android.content.Context
import android.util.Log
import androidx.work.*
import josh.griff.joshsslideshowwallpaper.data.DataStoreManager
import josh.griff.joshsslideshowwallpaper.util.WallpaperHelper
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

class WallpaperWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val dataStoreManager = DataStoreManager(applicationContext)
        
        // Check if slideshow is still enabled
        val isEnabled = dataStoreManager.isSlideshowEnabled.first()
        if (!isEnabled) {
            return Result.success()
        }

        val uris = dataStoreManager.imageUris.first()
        if (uris.isNotEmpty()) {
            var currentIndex = dataStoreManager.currentIndex.first()
            val isRandom = dataStoreManager.isRandom.first()

            // Try up to 3 times to find a valid URI
            var success = false
            repeat(3) {
                if (success) return@repeat
                
                val nextIndex = if (isRandom) {
                    (uris.indices).random()
                } else {
                    (currentIndex + 1) % uris.size
                }

                if (WallpaperHelper.setWallpaperFromUri(applicationContext, uris[nextIndex])) {
                    dataStoreManager.updateIndex(nextIndex)
                    success = true
                } else {
                    Log.w("WallpaperWorker", "Failed to set wallpaper for URI: ${uris[nextIndex]}")
                    currentIndex = nextIndex 
                }
            }
        }

        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "WallpaperSlideshowWork"

        /**
         * Starts the wallpaper slideshow using PeriodicWorkRequest.
         * Note: WorkManager enforces a minimum interval of 15 minutes.
         */
        fun startWork(context: Context, intervalMinutes: Int) {
            val constraints = Constraints.Builder()
                .setRequiresBatteryNotLow(true)
                .build()

            val workRequest = PeriodicWorkRequestBuilder<WallpaperWorker>(
                intervalMinutes.coerceAtLeast(15).toLong(), TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                workRequest
            )
        }

        fun stopWork(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
