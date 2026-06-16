package josh.griff.joshsslideshowwallpaper.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import josh.griff.joshsslideshowwallpaper.data.DataStoreManager
import josh.griff.joshsslideshowwallpaper.util.WallpaperHelper
import josh.griff.joshsslideshowwallpaper.worker.WallpaperWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val dataStoreManager = DataStoreManager(application)

    val imageUris = dataStoreManager.imageUris.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val currentIndex = dataStoreManager.currentIndex.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    val intervalMinutes = dataStoreManager.intervalMinutes.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 60)
    val isRandom = dataStoreManager.isRandom.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val isSlideshowEnabled = dataStoreManager.isSlideshowEnabled.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val _isUpdatingWallpaper = MutableStateFlow(false)
    val isUpdatingWallpaper = _isUpdatingWallpaper.asStateFlow()

    fun onImagesSelected(uris: List<Uri>) {
        viewModelScope.launch {
            val context = getApplication<Application>()
            val newUriStrings = uris.map { uri ->
                try {
                    context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                } catch (e: Exception) {
                    // Fail gracefully if permission can't be taken
                }
                uri.toString()
            }
            val currentUris = imageUris.value.toMutableList()
            currentUris.addAll(newUriStrings)
            dataStoreManager.saveImageUris(currentUris.distinct())
        }
    }

    fun removeImages(urisToRemove: List<String>) {
        viewModelScope.launch {
            val currentUris = imageUris.value.toMutableList()
            currentUris.removeAll(urisToRemove)
            dataStoreManager.saveImageUris(currentUris)
            
            // Adjust current index if needed
            val currentIndexVal = currentIndex.value
            if (currentIndexVal >= currentUris.size && currentUris.isNotEmpty()) {
                dataStoreManager.updateIndex(0)
            }
        }
    }

    fun removeAllImages() {
        viewModelScope.launch {
            dataStoreManager.saveImageUris(emptyList())
            dataStoreManager.updateIndex(0)
            toggleSlideshow(false)
        }
    }

    fun nextWallpaper() {
        val uris = imageUris.value
        if (uris.isEmpty()) return

        _isUpdatingWallpaper.value = true
        viewModelScope.launch {
            val index = currentIndex.value
            val random = isRandom.value
            
            val nextIndex = if (random) {
                (uris.indices).random()
            } else {
                (index + 1) % uris.size
            }

            val success = withContext(Dispatchers.IO) {
                WallpaperHelper.setWallpaperFromUri(getApplication(), uris[nextIndex])
            }

            if (success) {
                dataStoreManager.updateIndex(nextIndex)
            }
            
            // Add a tiny delay so the "Updating" state is actually perceptible
            delay(500)
            _isUpdatingWallpaper.value = false
        }
    }

    fun setWallpaperAtIndex(index: Int) {
        val uris = imageUris.value
        if (index !in uris.indices) return

        _isUpdatingWallpaper.value = true
        viewModelScope.launch {
            val success = withContext(Dispatchers.IO) {
                WallpaperHelper.setWallpaperFromUri(getApplication(), uris[index])
            }

            if (success) {
                dataStoreManager.updateIndex(index)
            }

            delay(500)
            _isUpdatingWallpaper.value = false
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
