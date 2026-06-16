package josh.griff.joshsslideshowwallpaper.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import josh.griff.joshsslideshowwallpaper.data.DataStoreManager
import josh.griff.joshsslideshowwallpaper.util.WallpaperHelper
import josh.griff.joshsslideshowwallpaper.worker.WallpaperWorker
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val dataStoreManager = DataStoreManager(application)

    val imageUris = dataStoreManager.imageUris.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val currentIndex = dataStoreManager.currentIndex.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    val intervalMinutes = dataStoreManager.intervalMinutes.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 60)
    val isRandom = dataStoreManager.isRandom.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val isSlideshowEnabled = dataStoreManager.isSlideshowEnabled.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun onImagesSelected(uris: List<Uri>) {
        viewModelScope.launch {
            val context = getApplication<Application>()
            val uriStrings = uris.map { uri ->
                try {
                    context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                } catch (e: Exception) {
                    // Fail gracefully if permission can't be taken
                }
                uri.toString()
            }
            dataStoreManager.saveImageUris(uriStrings)
            dataStoreManager.updateIndex(0)
        }
    }

    fun nextWallpaper() {
        viewModelScope.launch {
            val uris = imageUris.value
            if (uris.isEmpty()) return@launch

            val index = currentIndex.value
            val random = isRandom.value
            
            val nextIndex = if (random) {
                (uris.indices).random()
            } else {
                (index + 1) % uris.size
            }

            val success = WallpaperHelper.setWallpaperFromUri(getApplication(), uris[nextIndex])
            if (success) {
                dataStoreManager.updateIndex(nextIndex)
            }
        }
    }

    fun toggleSlideshow(enabled: Boolean) {
        viewModelScope.launch {
            dataStoreManager.saveSlideshowEnabled(enabled)
            if (enabled) {
                WallpaperWorker.startWork(getApplication(), intervalMinutes.value)
            } else {
                WallpaperWorker.stopWork(getApplication())
            }
        }
    }

    fun setInterval(minutes: Int) {
        viewModelScope.launch {
            dataStoreManager.saveInterval(minutes)
            if (isSlideshowEnabled.value) {
                WallpaperWorker.startWork(getApplication(), minutes)
            }
        }
    }

    fun toggleRandom(random: Boolean) {
        viewModelScope.launch {
            dataStoreManager.saveIsRandom(random)
        }
    }
}
