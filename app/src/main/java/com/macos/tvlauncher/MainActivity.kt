package com.macos.tvlauncher

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

data class AppItem(
    val label: String,
    val packageName: String,
    val icon: Bitmap
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MacOsDesktop(context = this)
        }
    }
}

@Composable
fun MacOsDesktop(context: Context) {
    val prefs = remember { context.getSharedPreferences("macos_prefs", Context.MODE_PRIVATE) }
    val apps = remember { loadInstalledApps(context) }

    var focusedAppName by remember { mutableStateOf("Finder") }
    var currentTime by remember { mutableStateOf("") }
    var currentDate by remember { mutableStateOf("") }

    var showLaunchpad by remember { mutableStateOf(false) }
    var showWallpaperDialog by remember { mutableStateOf(false) }

    var wallpaperPreset by remember { mutableStateOf(prefs.getInt("preset", 0)) }
    var customWallpaper by remember { mutableStateOf(loadSavedWallpaper(context)) }

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            saveWallpaperFromUri(context, it)?.let { bmp ->
                customWallpaper = bmp
                wallpaperPreset = -1
                prefs.edit().putInt("preset", -1).apply()
            }
        }
    }

    BackHandler(enabled = true) {
        when {
            showWallpaperDialog -> showWallpaperDialog = false
            showLaunchpad -> showLaunchpad = false
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            val now = Date()
            currentTime = SimpleDateFormat("HH:mm", Locale.getDefault()).format(now)
            currentDate = SimpleDateFormat("EEE, d MMM", Locale("ru")).format(now)
            delay(1000)
        }
    }

    val presetGradients = listOf(
        listOf(Color(0xFF1A103C), Color(0xFF2B1B54), Color(0xFF8A2387), Color(0xFFE94057)),
        listOf(Color(0xFF0F2027), Color(0xFF203A43), Color(0xFF2C5364), Color(0xFF00B4DB)),
        listOf(Color(0xFF20002C), Color(0xFF6F00FF), Color(0xFFCBB4D4))
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown &&
                    event.key == Key.DirectionUp &&
                    !showLaunchpad && !showWallpaperDialog
                ) {
                    showLaunchpad = true
                    true
                } else {
                    false
                }
            }
    ) {
        if (wallpaperPreset == -1 && customWallpaper != null) {
            Image(
                bitmap = customWallpaper!!.asImageBitmap(),
                contentDescription = "Custom Wallpaper",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            val colors = presetGradients.getOrElse(wallpaperPreset) { presetGradients[0] }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Brush.linearGradient(colors = colors))
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(38.dp)
                .background(Color.Black.copy(alpha = 0.4f))
                .padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.width(20.dp))
                Text(focusedAppName, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.width(20.dp))
                Text("Launchpad (▲)", color = Color.White.copy(alpha = 0.75f), fontSize = 13.sp)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(currentDate, color = Color.White.copy(alpha = 0.9f), fontSize = 13.sp)
                Spacer(modifier = Modifier.width(14.dp))
                Text(currentTime, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        }

        if (!showLaunchpad && !showWallpaperDialog) {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(y = (-40).dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = currentTime,
                    color = Color.White.copy(alpha = 0.92f),
                    fontSize = 76.sp,
                    fontWeight = FontWeight.Light
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = focusedAppName,
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        AnimatedVisibility(
            visible = showLaunchpad,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.72f))
                    .padding(top = 56.dp, bottom = 120.dp, start = 48.dp, end = 48.dp)
            ) {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 115.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(apps) { app ->
                        LaunchpadItem(
                            app = app,
                            onFocused = { focusedAppName = app.label },
                            onClick = {
                                showLaunchpad = false
                                launchApp(context, app.packageName)
                            }
                        )
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = showWallpaperDialog,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.65f)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .width(460.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color(0xFF252529))
                        .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(24.dp))
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Обои рабочего стола",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))

                    DialogButton("Тема: macOS Ventura") {
                        wallpaperPreset = 0
                        prefs.edit().putInt("preset", 0).apply()
                        showWallpaperDialog = false
                    }
                    DialogButton("Тема: macOS Sonoma") {
                        wallpaperPreset = 1
                        prefs.edit().putInt("preset", 1).apply()
                        showWallpaperDialog = false
                    }
                    DialogButton("Тема: macOS Monterey") {
                        wallpaperPreset = 2
                        prefs.edit().putInt("preset", 2).apply()
                        showWallpaperDialog = false
                    }
                    DialogButton("Выбрать картинку через проводник...") {
                        showWallpaperDialog = false
                        try {
                            imagePicker.launch("image/*")
                        } catch (_: Exception) {}
                    }
                    DialogButton("Загрузить из /sdcard/Download/wallpaper.jpg") {
                        loadFromDownloads(context)?.let { bmp ->
                            customWallpaper = bmp
                            wallpaperPreset = -1
                            prefs.edit().putInt("preset", -1).apply()
                        }
                        showWallpaperDialog = false
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 24.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(Color.White.copy(alpha = 0.16f))
                .border(1.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(28.dp))
                .padding(horizontal = 22.dp, vertical = 12.dp)
        ) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                item {
                    SystemDockButton(
                        symbol = "🚀",
                        label = "Launchpad",
                        onFocused = { focusedAppName = "Launchpad" },
                        onClick = {
                            showWallpaperDialog = false
                            showLaunchpad = !showLaunchpad
                        }
                    )
                }

                item {
                    SystemDockButton(
                        symbol = "🖼️",
                        label = "Обои",
                        onFocused = { focusedAppName = "Обои рабочего стола" },
                        onClick = {
                            showLaunchpad = false
                            showWallpaperDialog = !showWallpaperDialog
                        }
                    )
                }

                items(apps.take(10)) { app ->
                    DockIcon(
                        app = app,
                        onFocused = { focusedAppName = app.label },
                        onClick = { launchApp(context, app.packageName) }
                    )
                }
            }
        }
    }
}

@Composable
fun SystemDockButton(symbol: String, label: String, onFocused: () -> Unit, onClick: () -> Unit) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.35f else 1.0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "sysDockScale"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .scale(scale)
            .onFocusChanged {
                isFocused = it.isFocused
                if (it.isFocused) onFocused()
            }
            .focusable()
            .clickable { onClick() }
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(60.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color.White.copy(alpha = 0.22f))
                .border(
                    width = if (isFocused) 2.dp else 0.dp,
                    color = if (isFocused) Color.White else Color.Transparent,
                    shape = RoundedCornerShape(16.dp)
                )
        ) {
            Text(text = symbol, fontSize = 28.sp)
        }
        Spacer(modifier = Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .size(5.dp)
                .clip(CircleShape)
                .background(if (isFocused) Color.White else Color.Transparent)
        )
    }
}

@Composable
fun DockIcon(app: AppItem, onFocused: () -> Unit, onClick: () -> Unit) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.35f else 1.0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "dockMagnification"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .scale(scale)
            .onFocusChanged { state ->
                isFocused = state.isFocused
                if (state.isFocused) onFocused()
            }
            .focusable()
            .clickable { onClick() }
    ) {
        Image(
            bitmap = app.icon.asImageBitmap(),
            contentDescription = app.label,
            modifier = Modifier
                .size(60.dp)
                .clip(RoundedCornerShape(16.dp))
                .border(
                    width = if (isFocused) 2.dp else 0.dp,
                    color = if (isFocused) Color.White else Color.Transparent,
                    shape = RoundedCornerShape(16.dp)
                )
        )
        Spacer(modifier = Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .size(5.dp)
                .clip(CircleShape)
                .background(if (isFocused) Color.White else Color.Transparent)
        )
    }
}

@Composable
fun LaunchpadItem(app: AppItem, onFocused: () -> Unit, onClick: () -> Unit) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.18f else 1.0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "launchpadScale"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .scale(scale)
            .onFocusChanged {
                isFocused = it.isFocused
                if (it.isFocused) onFocused()
            }
            .focusable()
            .clickable { onClick() }
            .padding(8.dp)
    ) {
        Image(
            bitmap = app.icon.asImageBitmap(),
            contentDescription = app.label,
            modifier = Modifier
                .size(72.dp)
                .clip(RoundedCornerShape(18.dp))
                .border(
                    width = if (isFocused) 2.dp else 0.dp,
                    color = if (isFocused) Color.White else Color.Transparent,
                    shape = RoundedCornerShape(18.dp)
                )
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = app.label,
            color = Color.White,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun DialogButton(text: String, onClick: () -> Unit) {
    var isFocused by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (isFocused) Color(0xFF0A84FF) else Color.White.copy(alpha = 0.1f))
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .clickable { onClick() }
            .padding(vertical = 12.dp, horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text = text, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

fun saveWallpaperFromUri(context: Context, uri: Uri): Bitmap? {
    return try {
        val stream = context.contentResolver.openInputStream(uri)
        val bitmap = BitmapFactory.decodeStream(stream)
        stream?.close()
        if (bitmap != null) {
            val file = File(context.filesDir, "custom_wallpaper.jpg")
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
            }
        }
        bitmap
    } catch (e: Exception) {
        null
    }
}

fun loadFromDownloads(context: Context): Bitmap? {
    return try {
        val dlFile = File("/sdcard/Download/wallpaper.jpg")
        if (dlFile.exists()) {
            val bitmap = BitmapFactory.decodeFile(dlFile.absolutePath)
            if (bitmap != null) {
                val dest = File(context.filesDir, "custom_wallpaper.jpg")
                FileOutputStream(dest).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
                }
            }
            bitmap
        } else null
    } catch (e: Exception) {
        null
    }
}

fun loadSavedWallpaper(context: Context): Bitmap? {
    val file = File(context.filesDir, "custom_wallpaper.jpg")
    return if (file.exists()) BitmapFactory.decodeFile(file.absolutePath) else null
}

fun loadInstalledApps(context: Context): List<AppItem> {
    val pm = context.packageManager
    val result = mutableMapOf<String, AppItem>()
    val categories = listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)

    for (category in categories) {
        val intent = Intent(Intent.ACTION_MAIN, null).apply { addCategory(category) }
        val resolvedList = pm.queryIntentActivities(intent, 0)
        for (info in resolvedList) {
            val pkg = info.activityInfo.packageName
            if (pkg != context.packageName && !result.containsKey(pkg)) {
                val label = info.loadLabel(pm).toString()
                val drawable = info.activityInfo.loadIcon(pm)
                result[pkg] = AppItem(label, pkg, drawableToBitmap(drawable))
            }
        }
    }
    return result.values.sortedBy { it.label.lowercase() }
}

fun drawableToBitmap(drawable: Drawable): Bitmap {
    val width = drawable.intrinsicWidth.coerceAtLeast(96)
    val height = drawable.intrinsicHeight.coerceAtLeast(96)
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    drawable.setBounds(0, 0, canvas.width, canvas.height)
    drawable.draw(canvas)
    return bitmap
}

fun launchApp(context: Context, packageName: String) {
    val pm = context.packageManager
    val intent = pm.getLeanbackLaunchIntentForPackage(packageName)
        ?: pm.getLaunchIntentForPackage(packageName)
    intent?.let {
        it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(it)
    }
}
