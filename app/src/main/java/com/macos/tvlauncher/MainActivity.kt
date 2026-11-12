package com.macos.tvlauncher

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.drawable.Drawable
import android.media.MediaPlayer
import android.media.tv.TvContract
import android.media.tv.TvInputInfo
import android.media.tv.TvInputManager
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.Process
import android.os.StatFs
import android.os.SystemClock
import android.provider.Settings
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColor
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*

data class AppItem(
    val label: String,
    val packageName: String,
    val icon: ImageBitmap
)

data class TvSourceItem(
    val id: String,
    val label: String,
    val isConnected: Boolean
)

data class WeatherData(
    val city: String = "Орёл, Россия",
    val temp: String = "--°C",
    val windSpeed: String = "-- м/с",
    val icon: String = "⛅",
    val conditionText: String = "Загрузка...",
    val sourceName: String = "Яндекс / Gismeteo"
)

data class CurrencyData(
    val eurRub: String = "--.--",
    val eurUsd: String = "--.--"
)

data class SystemMetrics(
    val usedRamMb: Long = 0,
    val totalRamMb: Long = 0,
    val ramPercent: Int = 0,
    val cpuPercent: Int = 0,
    val freeStorageGb: String = "0.0",
    val totalStorageGb: String = "0.0"
)

// Настройки кастомного курсора фокуса
data class CursorConfig(
    val color: Color = Color(0xFF00E5FF),
    val strokeWidthDp: Float = 2.5f,
    val animationMode: Int = 1, // 0: Выкл (Статичный), 1: Сияние, 2: Переливание, 3: Круговорот (RGB)
    val speedMs: Int = 1800
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
    val apps = remember { loadInstalledAppsOptimized(context) }

    // --- НАСТРОЙКИ ИКОНОК И ТЕМЫ ---
    var iconBaseSize by remember { mutableStateOf(prefs.getInt("icon_size", 44)) }
    var iconCornerRadius by remember { mutableStateOf(prefs.getInt("icon_corner", 14)) }
    var iconVisualStyle by remember { mutableStateOf(prefs.getInt("icon_style", 0)) }
    var accentThemeIndex by remember { mutableStateOf(prefs.getInt("accent_idx", 0)) }

    // --- НАСТРОЙКИ КУРСОРА ---
    var cursorColorIndex by remember { mutableStateOf(prefs.getInt("cur_color", 1)) }
    var cursorWidthIndex by remember { mutableStateOf(prefs.getInt("cur_width", 1)) } // 0: 1.5dp, 1: 2.5dp, 2: 3.5dp, 3: 5.0dp
    var cursorAnimMode by remember { mutableStateOf(prefs.getInt("cur_anim", 3)) }   // 0: Выкл, 1: Сияние, 2: Переливание, 3: Круговорот
    var cursorSpeedIndex by remember { mutableStateOf(prefs.getInt("cur_speed", 1)) } // 0: Медленно, 1: Средне, 2: Быстро

    // --- НАСТРОЙКИ НИЖНЕГО DOCK ---
    var dockAlpha by remember { mutableStateOf(prefs.getFloat("dock_alpha", 0.18f)) }
    var dockColorIndex by remember { mutableStateOf(prefs.getInt("dock_color", 0)) }
    var dockHeightDp by remember { mutableStateOf(prefs.getInt("dock_height", 76)) }
    var dockCornerRadius by remember { mutableStateOf(prefs.getInt("dock_corner", 24)) }
    var dockPositionIndex by remember { mutableStateOf(prefs.getInt("dock_pos", 0)) }

    // --- НАСТРОЙКИ ЧАСОВ И ВИДЖЕТОВ ---
    var clockFontSize by remember { mutableStateOf(prefs.getInt("clock_size", 72)) }
    var clockOffsetX by remember { mutableStateOf(prefs.getInt("clock_x", 0)) }
    var clockOffsetY by remember { mutableStateOf(prefs.getInt("clock_y", -30)) }
    var moveClockMode by remember { mutableStateOf(false) }

    var showWeatherWidget by remember { mutableStateOf(prefs.getBoolean("w_weather", true)) }
    var showCurrencyWidget by remember { mutableStateOf(prefs.getBoolean("w_currency", true)) }
    var showSysInfoWidget by remember { mutableStateOf(prefs.getBoolean("w_sysinfo", true)) }
    var widgetsSideLeft by remember { mutableStateOf(prefs.getBoolean("w_side_left", false)) }

    var wallpaperPreset by remember { mutableStateOf(prefs.getInt("preset", 0)) }
    var customWallpaper by remember { mutableStateOf(loadSavedWallpaperOptimized(context)) }
    var videoWallpaperPath by remember { mutableStateOf(getSavedVideoPath(context)) }

    val accentColors = listOf(
        Color(0xFF0A84FF), // Синий macOS
        Color(0xFF30D158), // Изумрудный
        Color(0xFFBF5AF2), // Пурпурный
        Color(0xFFFF9F0A), // Золотой
        Color(0xFFFF453A)  // Красный
    )
    val accentColor = accentColors.getOrElse(accentThemeIndex) { accentColors[0] }

    val cursorColors = listOf(
        Color.White,           // 0: Белый
        Color(0xFF00E5FF),     // 1: Голубой неон
        Color(0xFF30D158),     // 2: Изумрудный
        Color(0xFFBF5AF2),     // 3: Пурпурный
        Color(0xFFFFD60A),     // 4: Золотой
        Color(0xFFFF375F)      // 5: Алый
    )
    val cursorWidths = listOf(1.5f, 2.5f, 3.8f, 5.0f)
    val cursorSpeeds = listOf(2800, 1600, 850)

    val cursorConfig = remember(cursorColorIndex, cursorWidthIndex, cursorAnimMode, cursorSpeedIndex) {
        CursorConfig(
            color = cursorColors.getOrElse(cursorColorIndex) { Color.White },
            strokeWidthDp = cursorWidths.getOrElse(cursorWidthIndex) { 2.5f },
            animationMode = cursorAnimMode,
            speedMs = cursorSpeeds.getOrElse(cursorSpeedIndex) { 1600 }
        )
    }

    val dockBaseColors = listOf(
        Color.White,
        Color(0xFF141418),
        Color(0xFF0A2246),
        Color(0xFF2C123E)
    )
    val dockBaseColor = dockBaseColors.getOrElse(dockColorIndex) { Color.White }

    var pinnedPackages by remember {
        val saved = prefs.getString("pinned_dock_apps", null)
        val initialList = if (saved != null) {
            saved.split(",").filter { it.isNotBlank() }
        } else {
            apps.take(6).map { it.packageName }
        }
        mutableStateOf(initialList.take(6))
    }

    fun savePinnedApps(newList: List<String>) {
        pinnedPackages = newList.take(6)
        prefs.edit().putString("pinned_dock_apps", pinnedPackages.joinToString(",")).apply()
    }

    val dockApps = remember(apps, pinnedPackages) {
        pinnedPackages.mapNotNull { pkg -> apps.find { it.packageName == pkg } }.take(6)
    }

    var focusedAppName by remember { mutableStateOf("Рабочий стол") }
    var currentTime by remember { mutableStateOf("") }
    var currentDate by remember { mutableStateOf("") }

    var showLaunchpad by remember { mutableStateOf(false) }
    var editDockMode by remember { mutableStateOf(false) }
    var showWallpaperDialog by remember { mutableStateOf(false) }
    var showHdmiDialog by remember { mutableStateOf(false) }
    var showLauncherSettings by remember { mutableStateOf(false) }
    var settingsTab by remember { mutableStateOf(0) }
    var tvSources by remember { mutableStateOf<List<TvSourceItem>>(emptyList()) }

    var weatherData by remember { mutableStateOf(WeatherData()) }
    var currencyData by remember { mutableStateOf(CurrencyData()) }
    var sysMetrics by remember { mutableStateOf(SystemMetrics()) }

    val lifecycleOwner = LocalLifecycleOwner.current
    var isLauncherVisible by remember { mutableStateOf(true) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            isLauncherVisible = event.targetState.isAtLeast(Lifecycle.State.STARTED)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            saveWallpaperFromUriOptimized(context, it)?.let { bmp ->
                customWallpaper = bmp
                wallpaperPreset = -1
                prefs.edit().putInt("preset", -1).apply()
            }
        }
    }

    val videoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            saveVideoFromUri(context, it)?.let { path ->
                videoWallpaperPath = path
                wallpaperPreset = -2
                prefs.edit().putInt("preset", -2).apply()
            }
        }
    }

    BackHandler(enabled = true) {
        when {
            moveClockMode -> moveClockMode = false
            showLauncherSettings -> showLauncherSettings = false
            showHdmiDialog -> showHdmiDialog = false
            showWallpaperDialog -> showWallpaperDialog = false
            showLaunchpad -> {
                showLaunchpad = false
                editDockMode = false
            }
        }
    }

    // 1. Обновление часов и системной информации
    LaunchedEffect(isLauncherVisible, showSysInfoWidget) {
        var lastCpuTime = Process.getElapsedCpuTime()
        var lastRealTime = SystemClock.elapsedRealtime()
        val cores = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)

        while (isLauncherVisible) {
            val now = Date()
            currentTime = SimpleDateFormat("HH:mm", Locale.getDefault()).format(now)
            currentDate = SimpleDateFormat("EEE, d MMM", Locale("ru")).format(now)

            if (showSysInfoWidget) {
                val nowCpu = Process.getElapsedCpuTime()
                val nowReal = SystemClock.elapsedRealtime()
                val deltaReal = (nowReal - lastRealTime).coerceAtLeast(1L)
                val appCpu = (((nowCpu - lastCpuTime).toFloat() / (deltaReal * cores)) * 100f).toInt()
                lastCpuTime = nowCpu
                lastRealTime = nowReal

                sysMetrics = readSystemMetrics(context, appCpu)
            }
            delay(3000)
        }
    }

    // 2. Обновление погоды (г. Орёл) и валют (EUR/RUB, EUR/USD)
    LaunchedEffect(isLauncherVisible, showWeatherWidget, showCurrencyWidget) {
        while (isLauncherVisible) {
            withContext(Dispatchers.IO) {
                if (showWeatherWidget) {
                    fetchOrelWeather()?.let { weatherData = it }
                }
                if (showCurrencyWidget) {
                    fetchEurRates()?.let { currencyData = it }
                }
            }
            delay(15 * 60 * 1000L)
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
                if (moveClockMode && event.type == KeyEventType.KeyDown) {
                    when (event.key) {
                        Key.DirectionUp -> {
                            clockOffsetY -= 15
                            prefs.edit().putInt("clock_y", clockOffsetY).apply()
                            true
                        }
                        Key.DirectionDown -> {
                            clockOffsetY += 15
                            prefs.edit().putInt("clock_y", clockOffsetY).apply()
                            true
                        }
                        Key.DirectionLeft -> {
                            clockOffsetX -= 15
                            prefs.edit().putInt("clock_x", clockOffsetX).apply()
                            true
                        }
                        Key.DirectionRight -> {
                            clockOffsetX += 15
                            prefs.edit().putInt("clock_x", clockOffsetX).apply()
                            true
                        }
                        Key.Enter, Key.DirectionCenter, Key.Back -> {
                            moveClockMode = false
                            true
                        }
                        else -> false
                    }
                } else {
                    false
                }
            }
    ) {
        // --- 1. ФОН РАБОЧЕГО СТОЛА ---
        when {
            wallpaperPreset == -2 && videoWallpaperPath != null -> {
                VideoWallpaperView(
                    videoPath = videoWallpaperPath!!,
                    isPaused = !isLauncherVisible
                )
            }
            wallpaperPreset == -1 && customWallpaper != null -> {
                Image(
                    bitmap = customWallpaper!!,
                    contentDescription = "Custom Wallpaper",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            else -> {
                val colors = presetGradients.getOrElse(wallpaperPreset.coerceAtLeast(0)) { presetGradients[0] }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Brush.linearGradient(colors = colors))
                )
            }
        }

        // --- 2. ВЕРХНИЙ БАР ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .background(Color.Black.copy(alpha = 0.5f))
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            LazyRow(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    Text(
                        text = "",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(end = 4.dp)
                    )
                }

                item {
                    TopBarButton(
                        text = "🚀 Программы",
                        isActive = showLaunchpad && !editDockMode,
                        accentColor = accentColor,
                        cursorConfig = cursorConfig,
                        onFocused = { focusedAppName = "Все приложения (Launchpad)" },
                        onClick = {
                            showWallpaperDialog = false
                            showHdmiDialog = false
                            showLauncherSettings = false
                            if (showLaunchpad && !editDockMode) {
                                showLaunchpad = false
                            } else {
                                editDockMode = false
                                showLaunchpad = true
                            }
                        }
                    )
                }

                item {
                    TopBarButton(
                        text = "➕ Dock (${dockApps.size}/6)",
                        isActive = showLaunchpad && editDockMode,
                        accentColor = accentColor,
                        cursorConfig = cursorConfig,
                        onFocused = { focusedAppName = "Выбор приложений для нижнего бара" },
                        onClick = {
                            showWallpaperDialog = false
                            showHdmiDialog = false
                            showLauncherSettings = false
                            if (showLaunchpad && editDockMode) {
                                showLaunchpad = false
                                editDockMode = false
                            } else {
                                editDockMode = true
                                showLaunchpad = true
                            }
                        }
                    )
                }

                item {
                    TopBarButton(
                        text = "🎨 Лаунчер",
                        isActive = showLauncherSettings,
                        accentColor = accentColor,
                        cursorConfig = cursorConfig,
                        onFocused = { focusedAppName = "Настройки вида, курсора, Dock и виджетов" },
                        onClick = {
                            showLaunchpad = false
                            editDockMode = false
                            showWallpaperDialog = false
                            showHdmiDialog = false
                            showLauncherSettings = !showLauncherSettings
                        }
                    )
                }

                item {
                    TopBarButton(
                        text = "🖼️ Обои",
                        isActive = showWallpaperDialog,
                        accentColor = accentColor,
                        cursorConfig = cursorConfig,
                        onFocused = { focusedAppName = "Фото и видеообои рабочего стола" },
                        onClick = {
                            showLaunchpad = false
                          