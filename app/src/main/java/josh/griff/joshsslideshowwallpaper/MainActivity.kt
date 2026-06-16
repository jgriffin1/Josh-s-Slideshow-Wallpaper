package josh.griff.joshsslideshowwallpaper

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import josh.griff.joshsslideshowwallpaper.ui.MainViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            JoshsTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    WallpaperApp()
                }
            }
        }
    }
}

@Composable
fun JoshsTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> darkColorScheme(
            primary = Color(0xFFD0BCFF),
            secondary = Color(0xFFCCC2DC),
            tertiary = Color(0xFFEFB8C8)
        )
        else -> lightColorScheme(
            primary = Color(0xFF6750A4),
            secondary = Color(0xFF625B71),
            tertiary = Color(0xFF7D5260)
        )
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography(),
        content = content
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun WallpaperApp(viewModel: MainViewModel = viewModel()) {
    val imageUris by viewModel.imageUris.collectAsState()
    val currentIndex by viewModel.currentIndex.collectAsState()
    val isSlideshowEnabled by viewModel.isSlideshowEnabled.collectAsState()
    val intervalMinutes by viewModel.intervalMinutes.collectAsState()
    val isRandom by viewModel.isRandom.collectAsState()

    var selectedUris by remember { mutableStateOf(setOf<String>()) }
    var initialSelectionDuringDrag by remember { mutableStateOf(setOf<String>()) }
    val isSelectionMode = selectedUris.isNotEmpty()
    
    var previewIndex by remember { mutableStateOf<Int?>(null) }
    
    // Fancy Success Toast State
    val isUpdating by viewModel.isUpdatingWallpaper.collectAsState()
    var showSuccessToast by remember { mutableStateOf(false) }

    LaunchedEffect(isUpdating) {
        if (isUpdating) {
            showSuccessToast = true
        } else if (showSuccessToast) {
            delay(2000)
            showSuccessToast = false
        }
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(),
        onResult = { uris ->
            if (uris.isNotEmpty()) {
                viewModel.onImagesSelected(uris)
            }
        }
    )

    val gridState = rememberLazyGridState()
    val coroutineScope = rememberCoroutineScope()
    val showScrollToTop by remember {
        derivedStateOf { gridState.firstVisibleItemIndex > 5 }
    }

    BackHandler(enabled = isSelectionMode || previewIndex != null) {
        if (previewIndex != null) {
            previewIndex = null
        } else {
            selectedUris = emptySet()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                TopAppBar(
                    title = {
                        if (isSelectionMode) {
                            Text("${selectedUris.size} selected")
                        } else {
                            Text("JoshSlides", fontWeight = FontWeight.Bold)
                        }
                    },
                    navigationIcon = {
                        if (isSelectionMode) {
                            IconButton(onClick = { selectedUris = emptySet() }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear selection")
                            }
                        }
                    },
                    actions = {
                        if (isSelectionMode) {
                            IconButton(onClick = {
                                viewModel.removeImages(selectedUris.toList())
                                selectedUris = emptySet()
                            }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete selected")
                            }
                        } else if (imageUris.isNotEmpty()) {
                            var showMenu by remember { mutableStateOf(false) }
                            Box {
                                IconButton(onClick = { showMenu = true }) {
                                    Icon(Icons.Default.MoreVert, contentDescription = "More")
                                }
                                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                                    DropdownMenuItem(
                                        text = { Text("Remove all photos") },
                                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                                        onClick = {
                                            viewModel.removeAllImages()
                                            showMenu = false
                                        }
                                    )
                                }
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = if (isSelectionMode) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent
                    )
                )
            }
        ) { innerPadding ->
            val gridUris = imageUris // Local copy for pointerInput capture
            var dragStartIndex by remember { mutableStateOf<Int?>(null) }

            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Fixed(3),
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                MaterialTheme.colorScheme.surface,
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.1f)
                            )
                        )
                    )
                    .pointerInput(gridUris) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { offset ->
                                gridState.layoutInfo.visibleItemsInfo
                                    .find { item ->
                                        offset.y.toInt() in item.offset.y..(item.offset.y + item.size.height) &&
                                                offset.x.toInt() in item.offset.x..(item.offset.x + item.size.width)
                                    }
                                    ?.let { 
                                        if (it.index >= 1 && (it.index - 1) < gridUris.size) {
                                            dragStartIndex = it.index - 1
                                            initialSelectionDuringDrag = selectedUris
                                            selectedUris = selectedUris + gridUris[dragStartIndex!!]
                                        }
                                    }
                            },
                            onDrag = { change, _ ->
                                dragStartIndex?.let { start ->
                                    gridState.layoutInfo.visibleItemsInfo
                                        .find { item ->
                                            change.position.y.toInt() in item.offset.y..(item.offset.y + item.size.height) &&
                                                    change.position.x.toInt() in item.offset.x..(item.offset.x + item.size.width)
                                        }
                                        ?.let { endItem ->
                                            val end = endItem.index - 1
                                            if (end >= 0 && end < gridUris.size) {
                                                val range = min(start, end)..max(start, end)
                                                val urisInRange = range.map { gridUris[it] }.toSet()
                                                selectedUris = initialSelectionDuringDrag + urisInRange
                                            }
                                        }
                                }
                            },
                            onDragEnd = { dragStartIndex = null },
                            onDragCancel = { dragStartIndex = null }
                        )
                    },
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Settings and Hero Section
                item(span = { GridItemSpan(3) }) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Spacer(modifier = Modifier.height(8.dp))

                        // Main Action Buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            LargeActionButton(
                                text = "Pick Photos",
                                icon = Icons.Default.Add,
                                onClick = {
                                    launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                },
                                modifier = Modifier.weight(1f),
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )

                            LargeActionButton(
                                text = "Next One",
                                icon = Icons.Default.Refresh,
                                onClick = { 
                                    showSuccessToast = true
                                    viewModel.nextWallpaper()
                                },
                                modifier = Modifier.weight(1f),
                                enabled = imageUris.isNotEmpty() && !isSelectionMode,
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Slideshow Settings Card
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(28.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)
                            )
                        ) {
                            Column(modifier = Modifier.padding(24.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                        modifier = Modifier.size(40.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                Icons.Default.PlayArrow,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            "Automatic Cycle",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                        var showHelp by remember { mutableStateOf(false) }
                                        IconButton(onClick = { showHelp = true }, modifier = Modifier.size(32.dp)) {
                                            Icon(
                                                Icons.Default.Info, 
                                                contentDescription = "Help", 
                                                modifier = Modifier.size(18.dp),
                                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                                            )
                                        }
                                        if (showHelp) {
                                            AlertDialog(
                                                onDismissRequest = { showHelp = false },
                                                title = { Text("Automatic Cycle") },
                                                text = { Text("This is the main magic switch! Turn it on to have your wallpaper automatically cycle through your gallery.") },
                                                confirmButton = {
                                                    TextButton(onClick = { showHelp = false }) { Text("Got it") }
                                                }
                                            )
                                        }
                                    }
                                    Switch(
                                        checked = isSlideshowEnabled,
                                        onCheckedChange = { viewModel.toggleSlideshow(it) },
                                        enabled = imageUris.isNotEmpty()
                                    )
                                }

                                Spacer(modifier = Modifier.height(20.dp))

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        "Shuffle photos",
                                        style = MaterialTheme.typography.bodyLarge,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Checkbox(
                                        checked = isRandom,
                                        onCheckedChange = { viewModel.toggleRandom(it) }
                                    )
                                }

                                HorizontalDivider(
                                    modifier = Modifier.padding(vertical = 16.dp),
                                    color = MaterialTheme.colorScheme.outlineVariant
                                )

                                // Interval Selector
                                var expanded by remember { mutableStateOf(false) }
                                val intervals = listOf(15, 30, 60, 360, 1440)
                                val labels = listOf("15 mins", "30 mins", "1 hour", "6 hours", "Daily")
                                val currentLabel = labels[intervals.indexOf(intervalMinutes).let { if (it == -1) 2 else it }]

                                Box(modifier = Modifier.fillMaxWidth()) {
                                    Button(
                                        onClick = { expanded = true },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(16.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    ) {
                                        Text("Update interval: $currentLabel")
                                    }
                                    DropdownMenu(
                                        expanded = expanded,
                                        onDismissRequest = { expanded = false },
                                        modifier = Modifier
                                            .fillMaxWidth(0.8f)
                                            .background(MaterialTheme.colorScheme.surface)
                                    ) {
                                        intervals.forEachIndexed { index, min ->
                                            DropdownMenuItem(
                                                text = { Text(labels[index]) },
                                                onClick = {
                                                    viewModel.setInterval(min)
                                                    expanded = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Current Displayed Photo Section
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(28.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp)
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    "Current Wallpaper",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                
                                if (imageUris.isNotEmpty() && currentIndex in imageUris.indices) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(200.dp)
                                            .clip(RoundedCornerShape(20.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant)
                                            .clickable { previewIndex = currentIndex }
                                    ) {
                                        AsyncImage(
                                            model = imageUris[currentIndex],
                                            contentDescription = "Current wallpaper",
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop
                                        )
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(
                                                    Brush.verticalGradient(
                                                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.3f))
                                                    )
                                                )
                                        )
                                        Icon(
                                            Icons.Default.Search,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp)
                                        )
                                    }
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(120.dp)
                                            .clip(RoundedCornerShape(20.dp))
                                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.05f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            "Add photos to get started! ✨",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(32.dp))

                        // Gallery Header
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.Bottom,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "Your Gallery",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                text = "${imageUris.size} photos",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }

                // Gallery Items
                itemsIndexed(imageUris) { index, uri ->
                    val isCurrent = index == currentIndex
                    val isSelected = selectedUris.contains(uri)
                    
                    Box(
                        modifier = Modifier
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                else if (isCurrent) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                                else Color.Transparent
                            )
                            .combinedClickable(
                                onClick = {
                                    if (isSelectionMode) {
                                        if (isSelected) selectedUris -= uri else selectedUris += uri
                                    } else {
                                        previewIndex = index
                                    }
                                }
                            )
                            .padding(if (isSelected || isCurrent) 4.dp else 0.dp)
                            .clip(RoundedCornerShape(12.dp))
                    ) {
                        AsyncImage(
                            model = uri,
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentScale = ContentScale.Crop
                        )
                        
                        if (isSelected) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        } else if (isCurrent) {
                            Surface(
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(4.dp)
                                    .size(20.dp),
                                shape = CircleShape
                            ) {
                                Icon(
                                    Icons.Default.Refresh,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.padding(4.dp)
                                )
                            }
                        }
                    }
                }
                
                if (imageUris.isEmpty()) {
                    item(span = { GridItemSpan(3) }) {
                        EmptyState()
                    }
                }
            }
        }

        // Return to Top FAB
        AnimatedVisibility(
            visible = showScrollToTop,
            enter = fadeIn() + slideInVertically(initialOffsetY = { 100 }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { 100 }),
            modifier = Modifier.align(Alignment.BottomEnd).padding(24.dp)
        ) {
            FloatingActionButton(
                onClick = {
                    coroutineScope.launch {
                        gridState.animateScrollToItem(0)
                    }
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = CircleShape,
                elevation = FloatingActionButtonDefaults.elevation(8.dp)
            ) {
                Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Scroll to top")
            }
        }

        // Fancy Success Toast Overlay
        AnimatedVisibility(
            visible = showSuccessToast,
            enter = fadeIn() + slideInVertically(initialOffsetY = { -100 }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { -100 }),
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 80.dp)
        ) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                shape = RoundedCornerShape(24.dp),
                shadowElevation = 8.dp,
                modifier = Modifier.padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (isUpdating) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 3.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            "Updating Wallpaper...",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                    } else {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            "Success!",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Photo Preview Overlay
        if (previewIndex != null && imageUris.isNotEmpty()) {
            val pagerState = rememberPagerState(
                initialPage = previewIndex ?: 0,
                pageCount = { imageUris.size }
            )

            // Re-sync pager if previewIndex changes while open (e.g. from hero)
            LaunchedEffect(previewIndex) {
                previewIndex?.let { pagerState.scrollToPage(it) }
            }

            Dialog(
                onDismissRequest = { previewIndex = null },
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black)
                ) {
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize(),
                        pageSpacing = 16.dp,
                        contentPadding = PaddingValues(horizontal = 0.dp)
                    ) { page ->
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            AsyncImage(
                                model = imageUris[page],
                                contentDescription = "Full preview",
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clickable { previewIndex = null },
                                contentScale = ContentScale.Fit
                            )
                        }
                    }

                    // Top Controls
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Photo counter
                        Surface(
                            color = Color.Black.copy(alpha = 0.4f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = "${pagerState.currentPage + 1} / ${imageUris.size}",
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                style = MaterialTheme.typography.labelLarge
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            // Delete button
                            IconButton(
                                onClick = {
                                    val uriToDelete = imageUris[pagerState.currentPage]
                                    viewModel.removeImages(listOf(uriToDelete))
                                    if (imageUris.size <= 1) {
                                        previewIndex = null
                                    }
                                },
                                modifier = Modifier.background(Color.Black.copy(alpha = 0.4f), CircleShape)
                            ) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Delete photo",
                                    tint = Color.White
                                )
                            }

                            // Close button
                            IconButton(
                                onClick = { previewIndex = null },
                                modifier = Modifier.background(Color.Black.copy(alpha = 0.4f), CircleShape)
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Close preview",
                                    tint = Color.White
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EmptyState() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(vertical = 64.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.05f),
                modifier = Modifier.size(120.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("📸", fontSize = 48.sp)
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                "No photos selected",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Pick some photos to start your slideshow!",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun LargeActionButton(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    containerColor: Color,
    contentColor: Color
) {
    FilledTonalButton(
        onClick = onClick,
        modifier = modifier.height(72.dp),
        enabled = enabled,
        shape = RoundedCornerShape(20.dp),
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = containerColor,
            contentColor = contentColor
        )
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.height(4.dp))
            Text(text, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        }
    }
}

@Preview(showBackground = true)
@Composable
fun WallpaperAppPreview() {
    JoshsTheme {
        WallpaperApp()
    }
}
