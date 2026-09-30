package com.github.vermilion10.milea.ui.screens.poster

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripPosterDialog(
    content: PosterContent,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var style by remember { mutableStateOf(PosterStyle.MIDNIGHT) }
    var format by remember { mutableStateOf(PosterFormat.STORY) }
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var busy by remember { mutableStateOf(false) }

    LaunchedEffect(style, format, content) {
        bitmap = withContext(Dispatchers.Default) { TripPosterRenderer.render(content, style, format) }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Share trip") },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close")
                        }
                    }
                )
            },
            snackbarHost = { SnackbarHost(snackbar) },
            bottomBar = {
                Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
                    Column(
                        modifier = Modifier
                            .navigationBarsPadding()
                            .padding(vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            PosterStyle.entries.forEach { s ->
                                FilterChip(
                                    selected = style == s,
                                    onClick = { style = s },
                                    label = { Text(s.label) }
                                )
                            }
                        }
                        SingleChoiceSegmentedButtonRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                        ) {
                            PosterFormat.entries.forEachIndexed { i, f ->
                                SegmentedButton(
                                    selected = format == f,
                                    onClick = { format = f },
                                    shape = SegmentedButtonDefaults.itemShape(i, PosterFormat.entries.size)
                                ) { Text(f.label) }
                            }
                        }
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    val b = bitmap ?: return@OutlinedButton
                                    scope.launch {
                                        busy = true
                                        val ok = withContext(Dispatchers.IO) { saveToGallery(context, b) }
                                        busy = false
                                        snackbar.showSnackbar(if (ok) "Saved to Pictures/Milea" else "Couldn't save the image")
                                    }
                                },
                                enabled = bitmap != null && !busy,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Download, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Save")
                            }
                            Button(
                                onClick = {
                                    val b = bitmap ?: return@Button
                                    scope.launch {
                                        busy = true
                                        val file = withContext(Dispatchers.IO) { writeShareFile(context, b) }
                                        busy = false
                                        sharePoster(context, file)
                                    }
                                },
                                enabled = bitmap != null && !busy,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Share, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Share")
                            }
                        }
                    }
                }
            }
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                val b = bitmap
                if (b == null) {
                    CircularProgressIndicator()
                } else {
                    val shape = MaterialTheme.shapes.large
                    Box(
                        modifier = Modifier
                            .aspectRatio(b.width.toFloat() / b.height)
                            .shadow(8.dp, shape)
                            .clip(shape)
                    ) {
                        if (style == PosterStyle.STICKER) Checkerboard()
                        Image(
                            bitmap = b.asImageBitmap(),
                            contentDescription = "Trip poster preview",
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
    }
}

/** Shows transparency, like image editors do, so the sticker style reads correctly. */
@Composable
private fun Checkerboard() {
    val light = MaterialTheme.colorScheme.surfaceContainerHighest
    val dark = MaterialTheme.colorScheme.outline
    Canvas(
        Modifier
            .fillMaxSize()
            .background(light)
    ) {
        val cell = 16.dp.toPx()
        var row = 0
        var y = 0f
        while (y < size.height) {
            var x = if (row % 2 == 0) 0f else cell
            while (x < size.width) {
                drawRect(dark, Offset(x, y), Size(cell, cell))
                x += cell * 2
            }
            y += cell
            row++
        }
    }
}

private fun writeShareFile(context: Context, bitmap: Bitmap): File {
    val dir = File(context.cacheDir, "posters").apply { mkdirs() }
    dir.listFiles()?.forEach { it.delete() }
    val file = File(dir, "milea-trip-${System.currentTimeMillis()}.png")
    file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    return file
}

private fun sharePoster(context: Context, file: File) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Share trip"))
}

private fun saveToGallery(context: Context, bitmap: Bitmap): Boolean = runCatching {
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, "milea-trip-${System.currentTimeMillis()}.png")
        put(MediaStore.Images.Media.MIME_TYPE, "image/png")
        put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/Milea")
        put(MediaStore.Images.Media.IS_PENDING, 1)
    }
    val resolver = context.contentResolver
    val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return false
    resolver.openOutputStream(uri)?.use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    values.clear()
    values.put(MediaStore.Images.Media.IS_PENDING, 0)
    resolver.update(uri, values, null, null)
    true
}.getOrDefault(false)
