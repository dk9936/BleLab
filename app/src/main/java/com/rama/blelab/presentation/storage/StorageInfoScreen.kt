package com.rama.blelab.presentation.storage

import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.provider.Settings
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StorageInfoScreen(
    viewModel: StorageInfoViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsState()
    var selectedCategory by remember { mutableStateOf<StorageCategory?>(null) }
    var hasPermissions by remember {
        mutableStateOf(
            StorageInfoViewModel.requiredPermissions().all { permission ->
                ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
            }
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        hasPermissions = results.values.all { it }
        viewModel.refresh()
    }

    LaunchedEffect(Unit) {
        viewModel.refresh()
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = selectedCategory?.type?.label() ?: "Storage Info",
                        color = Color(0xFF1976D2),
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (selectedCategory != null) {
                            selectedCategory = null
                        } else {
                            onBack()
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::refresh) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                }
            )
        }
    ) { padding ->
        selectedCategory?.let { category ->
            StorageFilesGrid(
                category = category,
                modifier = Modifier.padding(padding)
            )
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(Color.White)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                StorageOverviewCard(state = state)
            }

            if (!state.hasAllFilesAccess) {
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFFFF8E1)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = "Allow All Files Access to fully calculate PDFs, Excel, Word, archives, APKs, and other non-media files.",
                                color = Color(0xFF5D4037),
                                fontSize = 13.sp
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            if (!hasPermissions || !state.hasPermissions) {
                                Button(
                                    onClick = { permissionLauncher.launch(StorageInfoViewModel.requiredPermissions()) },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1976D2))
                                ) {
                                    Text("Allow Media Access")
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                            Button(
                                onClick = {
                                    context.startActivity(
                                        Intent(
                                            Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                                            Uri.parse("package:${context.packageName}")
                                        )
                                    )
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00796B))
                            ) {
                                Text("Allow All Files Access")
                            }
                        }
                    }
                }
            }

            item {
                Text(
                    text = "CATEGORIES",
                    color = Color.Gray,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            if (state.isLoading) {
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFF8F9FA)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            Text(
                                text = "Scanning storage",
                                modifier = Modifier.padding(start = 10.dp),
                                color = Color.Gray
                            )
                        }
                    }
                }
            } else if (state.categories.isEmpty()) {
                item {
                    EmptyStorageState()
                }
            } else {
                items(state.categories, key = { it.type }) { category ->
                    StorageCategoryRow(
                        category = category,
                        onClick = { selectedCategory = category }
                    )
                }
            }

            item {
                state.lastUpdatedMillis?.let { updated ->
                    Text(
                        text = "Last update ${updated.toTimeText()}",
                        color = Color.Gray,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun StorageOverviewCard(state: StorageInfoState) {
    val usedFraction = if (state.totalBytes > 0L) {
        state.usedBytes.toFloat() / state.totalBytes.toFloat()
    } else {
        0f
    }.coerceIn(0f, 1f)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = Color(0xFFE3F2FD)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Storage, contentDescription = null, tint = Color(0xFF1976D2))
                Text(
                    text = "Device Storage",
                    modifier = Modifier.padding(start = 10.dp),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF111827)
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "${formatBytes(state.usedBytes)} used of ${formatBytes(state.totalBytes)}",
                color = Color(0xFF111827),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { usedFraction },
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xFF1976D2),
                trackColor = Color.White
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "${formatBytes(state.availableBytes)} available",
                color = Color.Gray,
                fontSize = 13.sp
            )
        }
    }
}

@Composable
private fun StorageCategoryRow(
    category: StorageCategory,
    onClick: () -> Unit
) {
    val color = category.type.color()
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = Color(0xFFF8F9FA)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = category.type.icon(),
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(32.dp)
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp)
            ) {
                Text(
                    text = category.type.label(),
                    color = Color(0xFF111827),
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
                Text(
                    text = "${category.count} items",
                    color = Color.Gray,
                    fontSize = 12.sp
                )
            }
            Text(
                text = formatBytes(category.bytes),
                color = color,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
        }
    }
}

@Composable
private fun StorageFilesGrid(
    category: StorageCategory,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.White)
            .padding(16.dp)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            color = category.type.color().copy(alpha = 0.12f)
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = category.type.icon(),
                    contentDescription = null,
                    tint = category.type.color(),
                    modifier = Modifier.size(32.dp)
                )
                Column(modifier = Modifier.padding(start = 12.dp)) {
                    Text(
                        text = category.type.label(),
                        color = Color(0xFF111827),
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                    Text(
                        text = "${category.count} files - ${formatBytes(category.bytes)}",
                        color = Color.Gray,
                        fontSize = 13.sp
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        if (category.files.isEmpty()) {
            EmptyStorageState()
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 150.dp),
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(category.files, key = { it.uri.toString() }) { file ->
                    StorageFileCard(
                        file = file,
                        icon = category.type.icon(),
                        color = category.type.color()
                    )
                }
            }
        }
    }
}

@Composable
private fun StorageFileCard(
    file: StorageFileItem,
    icon: ImageVector,
    color: Color
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = Color(0xFFF8F9FA)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            FilePreview(
                file = file,
                icon = icon,
                color = color
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = file.name,
                color = Color(0xFF111827),
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                maxLines = 2
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = formatBytes(file.sizeBytes),
                color = color,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
            file.modifiedMillis?.let { modified ->
                Text(
                    text = modified.toDateText(),
                    color = Color.Gray,
                    fontSize = 11.sp
                )
            }
        }
    }
}

@Composable
private fun FilePreview(
    file: StorageFileItem,
    icon: ImageVector,
    color: Color
) {
    val context = LocalContext.current
    var thumbnail by remember(file.uri) { mutableStateOf<Bitmap?>(null) }
    val canRenderThumbnail = file.mimeType?.startsWith("image/") == true ||
        file.mimeType?.startsWith("video/") == true

    LaunchedEffect(file.uri, canRenderThumbnail) {
        thumbnail = if (canRenderThumbnail) {
            withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.loadThumbnail(file.uri, Size(360, 360), null)
                }.getOrNull()
            }
        } else {
            null
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f),
        shape = RoundedCornerShape(8.dp),
        color = color.copy(alpha = 0.12f)
    ) {
        val bitmap = thumbnail
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = file.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(38.dp)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = file.extensionLabel(),
                    color = color,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun EmptyStorageState() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = Color(0xFFF8F9FA)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(Icons.Default.Folder, contentDescription = null, tint = Color.Gray)
            Spacer(modifier = Modifier.height(8.dp))
            Text("No category details available", color = Color.Gray)
        }
    }
}

private fun StorageCategoryType.label(): String {
    return when (this) {
        StorageCategoryType.IMAGES -> "Images"
        StorageCategoryType.VIDEOS -> "Videos"
        StorageCategoryType.AUDIO -> "Audio"
        StorageCategoryType.DOWNLOADS -> "Downloads"
        StorageCategoryType.PDF -> "PDF"
        StorageCategoryType.WORD -> "Word"
        StorageCategoryType.EXCEL -> "Excel"
        StorageCategoryType.POWERPOINT -> "PowerPoint"
        StorageCategoryType.TEXT -> "Text / CSV"
        StorageCategoryType.ARCHIVES -> "Archives"
        StorageCategoryType.APK -> "APK"
        StorageCategoryType.OTHER_FILES -> "Other Files"
    }
}

private fun StorageCategoryType.icon(): ImageVector {
    return when (this) {
        StorageCategoryType.IMAGES -> Icons.Default.Image
        StorageCategoryType.VIDEOS -> Icons.Default.VideoFile
        StorageCategoryType.AUDIO -> Icons.Default.AudioFile
        StorageCategoryType.DOWNLOADS -> Icons.Default.Download
        StorageCategoryType.PDF -> Icons.Default.Description
        StorageCategoryType.WORD -> Icons.Default.Description
        StorageCategoryType.EXCEL -> Icons.Default.InsertDriveFile
        StorageCategoryType.POWERPOINT -> Icons.Default.InsertDriveFile
        StorageCategoryType.TEXT -> Icons.Default.Description
        StorageCategoryType.ARCHIVES -> Icons.Default.Folder
        StorageCategoryType.APK -> Icons.Default.InsertDriveFile
        StorageCategoryType.OTHER_FILES -> Icons.Default.Folder
    }
}

private fun StorageCategoryType.color(): Color {
    return when (this) {
        StorageCategoryType.IMAGES -> Color(0xFF1976D2)
        StorageCategoryType.VIDEOS -> Color(0xFFD32F2F)
        StorageCategoryType.AUDIO -> Color(0xFF7B1FA2)
        StorageCategoryType.DOWNLOADS -> Color(0xFF00796B)
        StorageCategoryType.PDF -> Color(0xFFC62828)
        StorageCategoryType.WORD -> Color(0xFF1565C0)
        StorageCategoryType.EXCEL -> Color(0xFF2E7D32)
        StorageCategoryType.POWERPOINT -> Color(0xFFE65100)
        StorageCategoryType.TEXT -> Color(0xFF455A64)
        StorageCategoryType.ARCHIVES -> Color(0xFF6D4C41)
        StorageCategoryType.APK -> Color(0xFF00838F)
        StorageCategoryType.OTHER_FILES -> Color(0xFFF57C00)
    }
}

private fun formatBytes(bytes: Long): String {
    val units = listOf("B", "KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var unitIndex = 0
    while (value >= 1024 && unitIndex < units.lastIndex) {
        value /= 1024
        unitIndex++
    }
    return if (unitIndex == 0) {
        "${value.toLong()} ${units[unitIndex]}"
    } else {
        String.format(Locale.US, "%.2f %s", value, units[unitIndex])
    }
}

private fun Long.toTimeText(): String {
    return SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(this))
}

private fun Long.toDateText(): String {
    return SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(this))
}

private fun StorageFileItem.extensionLabel(): String {
    val extension = name.substringAfterLast('.', missingDelimiterValue = "")
    return when {
        extension.isNotBlank() && extension.length <= 8 -> extension.uppercase(Locale.getDefault())
        !mimeType.isNullOrBlank() -> mimeType.substringAfterLast('/').uppercase(Locale.getDefault()).take(8)
        else -> "FILE"
    }
}
