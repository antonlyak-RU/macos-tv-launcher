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

data class CursorConfig(
    val color: Color = Color(0xFF00E5FF),
    val strokeWidthDp: Float = 2.5f,
    val animationMode: Int = 1,
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

    var iconBaseSize by remember { mutableStateOf(prefs.getInt("icon_size", 44)) }
    var iconCornerRadius by remember { mutableStateOf(prefs.getInt("icon_corner", 14)) }
    var iconVisualStyle by remember { mutableStateOf(prefs.getInt("icon_style", 0)) }
    var accentThemeIndex by remember { mutableStateOf(prefs.getInt("accent_idx", 0)) }

    var cursorColorIndex by remember { mutableStateOf(prefs.getInt("cur_color", 1)) }
    var cursorWidthIndex by remember { mutableStateOf(prefs.getInt("cur_width", 1)) }
    var cursorAnimMode by remember { mutableStateOf(prefs.getInt("cur_anim", 3)) }
    var cursorSpeedIndex by remember { mutableStateOf(prefs.getInt("cur_speed", 1)) }

    var dockAlpha by remember { mutableStateOf(prefs.getFloat("dock_alpha", 0.18f)) }
    var dockColorIndex by remember { mutableStateOf(prefs.getInt("dock_color", 0)) }
    var dockHeightDp by remember { mutableStateOf(prefs.getInt("dock_height", 76)) }
    var dockCornerRadius by remember { mutableStateOf(prefs.getInt("dock_corner", 24)) }
    var dockPositionIndex by remember { mutableStateOf(prefs.getInt("dock_pos", 0)) }

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
        Color(0xFF0A84FF),
        Color(0xFF30D158),
        Color(0xFFBF5AF2),
        Color(0xFFFF9F0A),
        Color(0xFFFF453A)
    )
    val accentColor = accentColors.getOrElse(accentThemeIndex) { accentColors[0] }

    val cursorColors = listOf(
        Color.White,
        Color(0xFF00E5FF),
        Color(0xFF30D158),
        Color(0xFFBF5AF2),
        Color(0xFFFFD60A),
        Color(0xFFFF375F)
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
                            editDockMode = false
                            showHdmiDialog = false
                            showLauncherSettings = false
                            showWallpaperDialog = !showWallpaperDialog
                        }
                    )
                }

                item {
                    TopBarButton(
                        text = "📺 HDMI",
                        isActive = showHdmiDialog,
                        accentColor = accentColor,
                        cursorConfig = cursorConfig,
                        onFocused = { focusedAppName = "Переключение входов телевизора" },
                        onClick = {
                            showLaunchpad = false
                            editDockMode = false
                            showWallpaperDialog = false
                            showLauncherSettings = false
                            tvSources = loadTvInputs(context)
                            showHdmiDialog = !showHdmiDialog
                        }
                    )
                }

                item {
                    TopBarButton(
                        text = "📶 Wi-Fi",
                        isActive = false,
                        accentColor = accentColor,
                        cursorConfig = cursorConfig,
                        onFocused = { focusedAppName = "Настройки Wi-Fi" },
                        onClick = { openWifiSettings(context) }
                    )
                }

                item {
                    TopBarButton(
                        text = "ᛒ Bluetooth",
                        isActive = false,
                        accentColor = accentColor,
                        cursorConfig = cursorConfig,
                        onFocused = { focusedAppName = "Настройки Bluetooth" },
                        onClick = { openBluetoothSettings(context) }
                    )
                }

                item {
                    TopBarButton(
                        text = "⚙️ Настройки ТВ",
                        isActive = false,
                        accentColor = accentColor,
                        cursorConfig = cursorConfig,
                        onFocused = { focusedAppName = "Системные настройки Android TV" },
                        onClick = { openSystemSettings(context) }
                    )
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(currentDate, color = Color.White.copy(alpha = 0.9f), fontSize = 13.sp)
                Spacer(modifier = Modifier.width(10.dp))
                Text(currentTime, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        }

        val isDesktopClean = !showLaunchpad && !showWallpaperDialog && !showHdmiDialog && !showLauncherSettings
        if (isDesktopClean || moveClockMode) {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(x = clockOffsetX.dp, y = clockOffsetY.dp)
                    .then(
                        if (moveClockMode) {
                            Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color.Black.copy(alpha = 0.55f))
                                .border(2.dp, accentColor, RoundedCornerShape(16.dp))
                                .padding(horizontal = 24.dp, vertical = 12.dp)
                        } else Modifier
                    ),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = currentTime,
                    color = Color.White.copy(alpha = 0.94f),
                    fontSize = clockFontSize.sp,
                    fontWeight = FontWeight.Light
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (moveClockMode) {
                        "Стрелки пульта: переместить часы  •  ОК / Назад: сохранить"
                    } else {
                        focusedAppName
                    },
                    color = if (moveClockMode) accentColor else Color.White,
                    fontSize = if (moveClockMode) 15.sp else 20.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        if (isDesktopClean && (showWeatherWidget || showCurrencyWidget || showSysInfoWidget)) {
            Column(
                modifier = Modifier
                    .align(if (widgetsSideLeft) Alignment.TopStart else Alignment.TopEnd)
                    .padding(top = 58.dp, start = 24.dp, end = 24.dp)
                    .width(250.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (showWeatherWidget) {
                    DesktopWidgetCard(title = "📍 ${weatherData.city}", accentColor = accentColor) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = weatherData.icon,
                                    fontSize = 34.sp,
                                    modifier = Modifier.padding(end = 10.dp)
                                )
                                Column {
                                    Text(
                                        text = weatherData.temp,
                                        color = Color.White,
                                        fontSize = 22.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = weatherData.conditionText,
                                        color = Color.White.copy(alpha = 0.85f),
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "💨 Ветер: ${weatherData.windSpeed}",
                                color = Color.White.copy(alpha = 0.9f),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = weatherData.sourceName,
                                color = Color.White.copy(alpha = 0.5f),
                                fontSize = 10.sp
                            )
                        }
                    }
                }

                if (showCurrencyWidget) {
                    DesktopWidgetCard(title = "💶 Курсы валют (Евро)", accentColor = accentColor) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("EUR / RUB", color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp)
                                Text("${currencyData.eurRub} ₽", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("EUR / USD", color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp)
                                Text("${currencyData.eurUsd} $", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                if (showSysInfoWidget) {
                    DesktopWidgetCard(title = "🖥️ Система ТВ", accentColor = accentColor) {
                        Text(
                            text = "ОЗУ: ${sysMetrics.usedRamMb} / ${sysMetrics.totalRamMb} МБ (${sysMetrics.ramPercent}%)",
                            color = Color.White,
                            fontSize = 12.sp
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = "CPU: ~${sysMetrics.cpuPercent}%  •  Память: ${sysMetrics.freeStorageGb} ГБ своб.",
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }

        AnimatedVisibility(visible = showLaunchpad, enter = fadeIn(), exit = fadeOut()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.8f))
                    .padding(top = 56.dp, bottom = 105.dp, start = 48.dp, end = 48.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (editDockMode) {
                            "Выберите до 6 приложений для нижнего бара (${pinnedPackages.size} из 6)"
                        } else {
                            "Все приложения"
                        },
                        color = if (editDockMode) accentColor else Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )

                    TopBarButton(
                        text = if (editDockMode) "✓ Готово" else "➕ Выбрать для Dock",
                        isActive = editDockMode,
                        accentColor = accentColor,
                        cursorConfig = cursorConfig,
                        onFocused = {},
                        onClick = { editDockMode = !editDockMode }
                    )
                }

                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 110.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(apps, key = { it.packageName }) { app ->
                        val isPinned = pinnedPackages.contains(app.packageName)
                        LaunchpadItem(
                            app = app,
                            isPinned = isPinned,
                            editMode = editDockMode,
                            iconCornerRadius = iconCornerRadius,
                            cursorConfig = cursorConfig,
                            onFocused = { focusedAppName = app.label },
                            onClick = {
                                if (editDockMode) {
                                    if (isPinned) {
                                        savePinnedApps(pinnedPackages - app.packageName)
                                    } else if (pinnedPackages.size < 6) {
                                        savePinnedApps(pinnedPackages + app.packageName)
                                    }
                                } else {
                                    showLaunchpad = false
                                    launchApp(context, app.packageName)
                                }
                            }
                        )
                    }
                }
            }
        }

        AnimatedVisibility(visible = showLauncherSettings && !moveClockMode, enter = fadeIn(), exit = fadeOut()) {
            Box(
                modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.72f)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .width(680.dp)
                        .heightIn(max = 480.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color(0xFF1E1E24))
                        .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(24.dp))
                        .padding(22.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val tabs = listOf("🎨 Иконки", "✨ Курсор", "💎 Нижний бар", "🕒 Часы и Видео", "📊 Виджеты")
                        items(tabs.size) { idx ->
                            TopBarButton(
                                text = tabs[idx],
                                isActive = settingsTab == idx,
                                accentColor = accentColor,
                                cursorConfig = cursorConfig,
                                onFocused = {},
                                onClick = { settingsTab = idx }
                            )
                        }
                    }

                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth().weight(1f, fill = false)
                    ) {
                        when (settingsTab) {
                            0 -> {
                                item {
                                    val sizeLabel = when (iconBaseSize) {
                                        36 -> "Маленький (36 dp)"
                                        44 -> "Средний (44 dp)"
                                        else -> "Крупный (52 dp)"
                                    }
                                    DialogButton("Размер иконок в баре: $sizeLabel", accentColor, cursorConfig) {
                                        iconBaseSize = when (iconBaseSize) {
                                            36 -> 44
                                            44 -> 52
                                            else -> 36
                                        }
                                        prefs.edit().putInt("icon_size", iconBaseSize).apply()
                                    }
                                }
                                item {
                                    val cornerLabel = when (iconCornerRadius) {
                                        6 -> "Квадратные (6 dp)"
                                        14 -> "macOS Squircle (14 dp)"
                                        22 -> "Сильно скругленные (22 dp)"
                                        else -> "Круглые (50 dp)"
                                    }
                                    DialogButton("Скругление краев иконок: $cornerLabel", accentColor, cursorConfig) {
                                        iconCornerRadius = when (iconCornerRadius) {
                                            6 -> 14
                                            14 -> 22
                                            22 -> 50
                                            else -> 6
                                        }
                                        prefs.edit().putInt("icon_corner", iconCornerRadius).apply()
                                    }
                                }
                                item {
                                    val styleName = when (iconVisualStyle) {
                                        0 -> "Классический"
                                        1 -> "Стеклянная подложка"
                                        else -> "Неоновый контур"
                                    }
                                    DialogButton("Внешний вид иконок: $styleName", accentColor, cursorConfig) {
                                        iconVisualStyle = (iconVisualStyle + 1) % 3
                                        prefs.edit().putInt("icon_style", iconVisualStyle).apply()
                                    }
                                }
                                item {
                                    val themeName = listOf("Синий macOS", "Изумрудный", "Пурпурный", "Золотой", "Красный")[accentThemeIndex]
                                    DialogButton("Цветовая гамма акцента: $themeName", accentColor, cursorConfig) {
                                        accentThemeIndex = (accentThemeIndex + 1) % accentColors.size
                                        prefs.edit().putInt("accent_idx", accentThemeIndex).apply()
                                    }
                                }
                            }

                            1 -> {
                                item {
                                    val animNames = listOf(
                                        "Выключена (Статичный контур)",
                                        "Сияние (Мягкая неоновая пульсация)",
                                        "Переливание (Плавная смена оттенков)",
                                        "Круговорот (Вращающийся градиент вокруг значка)"
                                    )
                                    DialogButton("Анимация курсора: ${animNames[cursorAnimMode]}", accentColor, cursorConfig) {
                                        cursorAnimMode = (cursorAnimMode + 1) % 4
                                        prefs.edit().putInt("cur_anim", cursorAnimMode).apply()
                                    }
                                }
                                item {
                                    val cNames = listOf("Белый", "Голубой неон", "Изумрудный", "Пурпурный", "Золотой", "Алый")
                                    DialogButton("Основной цвет курсора: ${cNames[cursorColorIndex]}", accentColor, cursorConfig) {
                                        cursorColorIndex = (cursorColorIndex + 1) % cursorColors.size
                                        prefs.edit().putInt("cur_color", cursorColorIndex).apply()
                                    }
                                }
                                item {
                                    val wNames = listOf("Тонкий (1.5 dp)", "Средний (2.5 dp)", "Крупный (3.8 dp)", "Массивный (5.0 dp)")
                                    DialogButton("Толщина курсора: ${wNames[cursorWidthIndex]}", accentColor, cursorConfig) {
                                        cursorWidthIndex = (cursorWidthIndex + 1) % cursorWidths.size
                                        prefs.edit().putInt("cur_width", cursorWidthIndex).apply()
                                    }
                                }
                                item {
                                    val sNames = listOf("Медленная", "Средняя", "Быстрая")
                                    DialogButton("Скорость анимации курсора: ${sNames[cursorSpeedIndex]}", accentColor, cursorConfig) {
                                        cursorSpeedIndex = (cursorSpeedIndex + 1) % cursorSpeeds.size
                                        prefs.edit().putInt("cur_speed", cursorSpeedIndex).apply()
                                    }
                                }
                            }

                            2 -> {
                                item {
                                    val pct = (dockAlpha * 100).toInt()
                                    DialogButton("Непрозрачность стекла Dock: $pct%", accentColor, cursorConfig) {
                                        dockAlpha = when {
                                            dockAlpha < 0.2f -> 0.35f
                                            dockAlpha < 0.4f -> 0.55f
                                            dockAlpha < 0.6f -> 0.78f
                                            else -> 0.14f
                                        }
                                        prefs.edit().putFloat("dock_alpha", dockAlpha).apply()
                                    }
                                }
                                item {
                                    val cName = listOf("Светлое стекло", "Темный графит", "Глубокий синий", "Пурпурный")[dockColorIndex]
                                    DialogButton("Цвет нижнего бара: $cName", accentColor, cursorConfig) {
                                        dockColorIndex = (dockColorIndex + 1) % dockBaseColors.size
                                        prefs.edit().putInt("dock_color", dockColorIndex).apply()
                                    }
                                }
                                item {
                                    val hName = when (dockHeightDp) {
                                        68 -> "Компактный (68 dp)"
                                        76 -> "Стандартный (76 dp)"
                                        else -> "Высокий (86 dp)"
                                    }
                                    DialogButton("Размер (высота) бара: $hName", accentColor, cursorConfig) {
                                        dockHeightDp = when (dockHeightDp) {
                                            68 -> 76
                                            76 -> 86
                                            else -> 68
                                        }
                                        prefs.edit().putInt("dock_height", dockHeightDp).apply()
                                    }
                                }
                                item {
                                    DialogButton("Скругление углов бара: ${dockCornerRadius} dp", accentColor, cursorConfig) {
                                        dockCornerRadius = when (dockCornerRadius) {
                                            12 -> 24
                                            24 -> 36
                                            else -> 12
                                        }
                                        prefs.edit().putInt("dock_corner", dockCornerRadius).apply()
                                    }
                                }
                                item {
                                    val posName = when (dockPositionIndex) {
                                        0 -> "Снизу по центру"
                                        1 -> "Снизу слева"
                                        else -> "Снизу справа"
                                    }
                                    DialogButton("Положение нижнего бара: $posName", accentColor, cursorConfig) {
                                        dockPositionIndex = (dockPositionIndex + 1) % 3
                                        prefs.edit().putInt("dock_pos", dockPositionIndex).apply()
                                    }
                                }
                            }

                            3 -> {
                                item {
                                    DialogButton("Размер времени на экране: ${clockFontSize} sp", accentColor, cursorConfig) {
                                        clockFontSize = when (clockFontSize) {
                                            52 -> 72
                                            72 -> 94
                                            94 -> 112
                                            else -> 52
                                        }
                                        prefs.edit().putInt("clock_size", clockFontSize).apply()
                                    }
                                }
                                item {
                                    DialogButton("✥ Переместить часы стрелками пульта по экрану", accentColor, cursorConfig) {
                                        moveClockMode = true
                                    }
                                }
                                item {
                                    DialogButton("Сбросить положение часов по центру", accentColor, cursorConfig) {
                                        clockOffsetX = 0
                                        clockOffsetY = -30
                                        prefs.edit().putInt("clock_x", 0).putInt("clock_y", -30).apply()
                                    }
                                }
                                item {
                                    DialogButton("🎬 Выбрать видеообои (.mp4) через проводник...", accentColor, cursorConfig) {
                                        showLauncherSettings = false
                                        try { videoPicker.launch("video/*") } catch (_: Exception) {}
                                    }
                                }
                                item {
                                    DialogButton("🎬 Загрузить видео из /sdcard/Download/wallpaper.mp4", accentColor, cursorConfig) {
                                        loadVideoFromDownloads(context)?.let { path ->
                                            videoWallpaperPath = path
                                            wallpaperPreset = -2
                                            prefs.edit().putInt("preset", -2).apply()
                                        }
                                        showLauncherSettings = false
                                    }
                                }
                            }

                            4 -> {
                                item {
                                    DialogButton(
                                        text = "Погода (г. Орёл, Россия): ${if (showWeatherWidget) "Включена ✓" else "Выключена"}",
                                        accentColor = accentColor,
                                        cursorConfig = cursorConfig
                                    ) {
                                        showWeatherWidget = !showWeatherWidget
                                        prefs.edit().putBoolean("w_weather", showWeatherWidget).apply()
                                    }
                                }
                                item {
                                    DialogButton(
                                        text = "Курсы EUR/RUB и EUR/USD: ${if (showCurrencyWidget) "Включены ✓" else "Выключены"}",
                                        accentColor = accentColor,
                                        cursorConfig = cursorConfig
                                    ) {
                                        showCurrencyWidget = !showCurrencyWidget
                                        prefs.edit().putBoolean("w_currency", showCurrencyWidget).apply()
                                    }
                                }
                                item {
                                    DialogButton(
                                        text = "Системный монитор (ОЗУ, CPU, Память): ${if (showSysInfoWidget) "Включен ✓" else "Выключен"}",
                                        accentColor = accentColor,
                                        cursorConfig = cursorConfig
                                    ) {
                                        showSysInfoWidget = !showSysInfoWidget
                                        prefs.edit().putBoolean("w_sysinfo", showSysInfoWidget).apply()
                                    }
                                }
                                item {
                                    DialogButton(
                                        text = "Расположение виджетов: ${if (widgetsSideLeft) "Слева" else "Справа"}",
                                        accentColor = accentColor,
                                        cursorConfig = cursorConfig
                                    ) {
                                        widgetsSideLeft = !widgetsSideLeft
                                        prefs.edit().putBoolean("w_side_left", widgetsSideLeft).apply()
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        AnimatedVisibility(visible = showHdmiDialog, enter = fadeIn(), exit = fadeOut()) {
            Box(
                modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.65f)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .width(480.dp)
                        .heightIn(max = 440.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color(0xFF252529))
                        .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(24.dp))
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("Входы телевизора (HDMI / AV)", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))

                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f, fill = false)
                    ) {
                        if (tvSources.isNotEmpty()) {
                            items(tvSources) { source ->
                                val statusText = if (source.isConnected) "  •  Активен" else ""
                                DialogButton("🔌 ${source.label}$statusText", accentColor, cursorConfig) {
                                    showHdmiDialog = false
                                    switchToTvInput(context, source.id)
                                }
                            }
                        } else {
                            items(listOf(1, 2, 3, 4)) { port ->
                                DialogButton("🔌 Включить HDMI $port", accentColor, cursorConfig) {
                                    showHdmiDialog = false
                                    openFallbackHdmiPort(context, port)
                                }
                            }
                        }
                        item {
                            DialogButton("📺 Открыть системное меню входов ТВ", accentColor, cursorConfig) {
                                showHdmiDialog = false
                                openSystemInputsMenu(context)
                            }
                        }
                    }
                }
            }
        }

        AnimatedVisibility(visible = showWallpaperDialog, enter = fadeIn(), exit = fadeOut()) {
            Box(
                modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.65f)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .width(480.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color(0xFF252529))
                        .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(24.dp))
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("Обои рабочего стола", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))

                    DialogButton("Тема: macOS Ventura", accentColor, cursorConfig) {
                        wallpaperPreset = 0
                        prefs.edit().putInt("preset", 0).apply()
                        showWallpaperDialog = false
                    }
                    DialogButton("Тема: macOS Sonoma", accentColor, cursorConfig) {
                        wallpaperPreset = 1
                        prefs.edit().putInt("preset", 1).apply()
                        showWallpaperDialog = false
                    }
                    DialogButton("Тема: macOS Monterey", accentColor, cursorConfig) {
                        wallpaperPreset = 2
                        prefs.edit().putInt("preset", 2).apply()
                        showWallpaperDialog = false
                    }
                    DialogButton("🖼️ Выбрать фото через проводник...", accentColor, cursorConfig) {
                        showWallpaperDialog = false
                        try { imagePicker.launch("image/*") } catch (_: Exception) {}
                    }
                    DialogButton("🖼️ Загрузить фото из /sdcard/Download/wallpaper.jpg", accentColor, cursorConfig) {
                        loadFromDownloadsOptimized(context)?.let { bmp ->
                            customWallpaper = bmp
                            wallpaperPreset = -1
                            prefs.edit().putInt("preset", -1).apply()
                        }
                        showWallpaperDialog = false
                    }
                    DialogButton("🎬 Выбрать видеообои (.mp4) через проводник...", accentColor, cursorConfig) {
                        showWallpaperDialog = false
                        try { videoPicker.launch("video/*") } catch (_: Exception) {}
                    }
                    DialogButton("🎬 Загрузить видео из /sdcard/Download/wallpaper.mp4", accentColor, cursorConfig) {
                        loadVideoFromDownloads(context)?.let { path ->
                            videoWallpaperPath = path
                            wallpaperPreset = -2
                            prefs.edit().putInt("preset", -2).apply()
                        }
                        showWallpaperDialog = false
                    }
                }
            }
        }

        val dockAlign = when (dockPositionIndex) {
            1 -> Alignment.BottomStart
            2 -> Alignment.BottomEnd
            else -> Alignment.BottomCenter
        }
        val maxZoomedIcon = (iconBaseSize + 12).coerceAtMost(dockHeightDp - 14)

        Box(
            modifier = Modifier
                .align(dockAlign)
                .padding(bottom = 20.dp, start = 28.dp, end = 28.dp)
                .height(dockHeightDp.dp)
                .clip(RoundedCornerShape(dockCornerRadius.dp))
                .background(dockBaseColor.copy(alpha = dockAlpha))
                .border(1.dp, Color.White.copy(alpha = 0.32f), RoundedCornerShape(dockCornerRadius.dp))
                .padding(horizontal = 18.dp),
            contentAlignment = Alignment.Center
        ) {
            if (dockApps.isEmpty()) {
                TopBarButton(
                    text = "➕ Добавить приложения в Dock",
                    isActive = false,
                    accentColor = accentColor,
                    cursorConfig = cursorConfig,
                    onFocused = { focusedAppName = "Настроить Dock" },
                    onClick = {
                        editDockMode = true
                        showLaunchpad = true
                    }
                )
            } else {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    items(dockApps, key = { it.packageName }) { app ->
                        DockIcon(
                            app = app,
                            baseSizeDp = iconBaseSize,
                            zoomedSizeDp = maxZoomedIcon,
                            cornerRadiusDp = iconCornerRadius,
                            visualStyle = iconVisualStyle,
                            accentColor = accentColor,
                            cursorConfig = cursorConfig,
                            onFocused = { focusedAppName = app.label },
                            onClick = { launchApp(context, app.packageName) }
                        )
                    }
                }
            }
        }
    }
}

fun Modifier.macOsCursor(
    isFocused: Boolean,
    config: CursorConfig,
    cornerRadius: Dp
): Modifier = composed {
    if (!isFocused) return@composed this

    val strokeWidth = config.strokeWidthDp.dp

    when (config.animationMode) {
        0 -> this.border(strokeWidth, config.color, RoundedCornerShape(cornerRadius))
        1 -> {
            val infiniteTransition = rememberInfiniteTransition(label = "cursorGlow")
            val glowAlpha by infiniteTransition.animateFloat(
                initialValue = 0.35f,
                targetValue = 1.0f,
                animationSpec = infiniteRepeatable(
                    animation = tween(config.speedMs, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "glowAlpha"
            )
            this.drawWithContent {
                drawContent()
                val strokePx = strokeWidth.toPx()
                val radiusPx = cornerRadius.toPx()
                drawRoundRect(
                    color = config.color.copy(alpha = glowAlpha * 0.42f),
                    cornerRadius = CornerRadius(radiusPx, radiusPx),
                    style = Stroke(width = strokePx * 2.4f)
                )
                drawRoundRect(
                    color = config.color.copy(alpha = glowAlpha),
                    cornerRadius = CornerRadius(radiusPx, radiusPx),
                    style = Stroke(width = strokePx)
                )
            }
        }
        2 -> {
            val infiniteTransition = rememberInfiniteTransition(label = "cursorShimmer")
            val secondColor = Color(
                red = 1f - config.color.red * 0.5f,
                green = config.color.blue,
                blue = config.color.green,
                alpha = 1f
            )
            val animatedColor by infiniteTransition.animateColor(
                initialValue = config.color,
                targetValue = secondColor,
                animationSpec = infiniteRepeatable(
                    animation = tween(config.speedMs, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "shimmerColor"
            )
            this.drawWithContent {
                drawContent()
                val strokePx = strokeWidth.toPx()
                val radiusPx = cornerRadius.toPx()
                drawRoundRect(
                    color = animatedColor.copy(alpha = 0.35f),
                    cornerRadius = CornerRadius(radiusPx, radiusPx),
                    style = Stroke(width = strokePx * 2.0f)
                )
                drawRoundRect(
                    color = animatedColor,
                    cornerRadius = CornerRadius(radiusPx, radiusPx),
                    style = Stroke(width = strokePx)
                )
            }
        }
        else -> {
            val infiniteTransition = rememberInfiniteTransition(label = "cursorSpin")
            val angle by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = 360f,
                animationSpec = infiniteRepeatable(
                    animation = tween(config.speedMs, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = "spinAngle"
            )
            val sweepColors = remember(config.color) {
                listOf(
                    config.color,
                    Color(0xFF00E5FF),
                    Color(0xFFBF5AF2),
                    Color(0xFFFF2D55),
                    Color(0xFFFFD60A),
                    config.color
                )
            }
            val shaderMatrix = remember { Matrix() }

            this.drawWithContent {
                drawContent()
                val strokePx = strokeWidth.toPx()
                val radiusPx = cornerRadius.toPx()
                val center = Offset(size.width / 2f, size.height / 2f)

                val shader = SweepGradientShader(center, sweepColors, null)
                shaderMatrix.reset()
                shaderMatrix.postRotate(angle, center.x, center.y)
                shader.setLocalMatrix(shaderMatrix)
                val brush = ShaderBrush(shader)

                drawRoundRect(
                    brush = brush,
                    alpha = 0.38f,
                    cornerRadius = CornerRadius(radiusPx, radiusPx),
                    style = Stroke(width = strokePx * 2.2f)
                )
                drawRoundRect(
                    brush = brush,
                    cornerRadius = CornerRadius(radiusPx, radiusPx),
                    style = Stroke(width = strokePx)
                )
            }
        }
    }
}

@Composable
fun VideoWallpaperView(videoPath: String, isPaused: Boolean) {
    val mediaPlayerRef = remember { mutableStateOf<MediaPlayer?>(null) }

    DisposableEffect(isPaused) {
        val player = mediaPlayerRef.value
        if (player != null) {
            try {
                if (isPaused && player.isPlaying) player.pause()
                else if (!isPaused && !player.isPlaying) player.start()
            } catch (_: Exception) {}
        }
        onDispose {}
    }

    DisposableEffect(videoPath) {
        onDispose {
            try {
                mediaPlayerRef.value?.stop()
                mediaPlayerRef.value?.release()
            } catch (_: Exception) {}
            mediaPlayerRef.value = null
        }
    }

    AndroidView(
        factory = { ctx ->
            SurfaceView(ctx).apply {
                holder.addCallback(object : SurfaceHolder.Callback {
                    override fun surfaceCreated(holder: SurfaceHolder) {
                        try {
                            val mp = MediaPlayer().apply {
                                setDataSource(videoPath)
                                setSurface(holder.surface)
                                isLooping = true
                                setVolume(0f, 0f)
                                prepareAsync()
                                setOnPreparedListener { it.start() }
                            }
                            mediaPlayerRef.value = mp
                        } catch (_: Exception) {}
                    }
                    override fun surfaceChanged(h: SurfaceHolder, f: Int, w: Int, height: Int) {}
                    override fun surfaceDestroyed(h: SurfaceHolder) {
                        try {
                            mediaPlayerRef.value?.release()
                        } catch (_: Exception) {}
                        mediaPlayerRef.value = null
                    }
                })
            }
        },
        modifier = Modifier.fillMaxSize()
    )
}

@Composable
fun DesktopWidgetCard(
    title: String,
    accentColor: Color,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color.Black.copy(alpha = 0.46f))
            .border(1.dp, Color.White.copy(alpha = 0.22f), RoundedCornerShape(18.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Text(
            text = title,
            color = accentColor,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(5.dp))
        content()
    }
}

@Composable
fun TopBarButton(
    text: String,
    isActive: Boolean,
    accentColor: Color,
    cursorConfig: CursorConfig,
    onFocused: () -> Unit,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val bgColor = when {
        isFocused -> accentColor.copy(alpha = 0.55f)
        isActive -> Color.White.copy(alpha = 0.25f)
        else -> Color.Transparent
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .macOsCursor(isFocused = isFocused, config = cursorConfig, cornerRadius = 8.dp)
            .onFocusChanged {
                isFocused = it.isFocused
                if (it.isFocused) onFocused()
            }
            .focusable()
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = if (isFocused || isActive) FontWeight.Bold else FontWeight.Medium
        )
    }
}

@Composable
fun DockIcon(
    app: AppItem,
    baseSizeDp: Int,
    zoomedSizeDp: Int,
    cornerRadiusDp: Int,
    visualStyle: Int,
    accentColor: Color,
    cursorConfig: CursorConfig,
    onFocused: () -> Unit,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val iconSize by animateDpAsState(
        targetValue = if (isFocused) zoomedSizeDp.dp else baseSizeDp.dp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "dockIconSize"
    )
    val shape = RoundedCornerShape(cornerRadiusDp.dp)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .width((zoomedSizeDp + 10).dp)
            .fillMaxHeight()
            .onFocusChanged { state ->
                isFocused = state.isFocused
                if (state.isFocused) onFocused()
            }
            .focusable()
            .clickable { onClick() }
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(iconSize)
                .clip(shape)
                .background(
                    when (visualStyle) {
                        1 -> Color.White.copy(alpha = 0.2f)
                        2 -> accentColor.copy(alpha = 0.22f)
                        else -> Color.Transparent
                    }
                )
                .then(
                    if (!isFocused && visualStyle == 2) {
                        Modifier.border(1.dp, accentColor.copy(alpha = 0.6f), shape)
                    } else Modifier
                )
                .macOsCursor(
                    isFocused = isFocused,
                    config = cursorConfig,
                    cornerRadius = cornerRadiusDp.dp
                )
                .padding(if (visualStyle == 1) 4.dp else 0.dp)
        ) {
            Image(
                bitmap = app.icon,
                contentDescription = app.label,
                modifier = Modifier.fillMaxSize().clip(shape)
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .size(4.dp)
                .clip(CircleShape)
                .background(if (isFocused) cursorConfig.color else Color.Transparent)
        )
    }
}

@Composable
fun LaunchpadItem(
    app: AppItem,
    isPinned: Boolean,
    editMode: Boolean,
    iconCornerRadius: Int,
    cursorConfig: CursorConfig,
    onFocused: () -> Unit,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.14f else 1.0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "launchpadScale"
    )
    val shape = RoundedCornerShape(iconCornerRadius.dp)

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
        Box(contentAlignment = Alignment.TopEnd) {
            Image(
                bitmap = app.icon,
                contentDescription = app.label,
                modifier = Modifier
                    .size(68.dp)
                    .clip(shape)
                    .then(
                        if (!isFocused && editMode && isPinned) {
                            Modifier.border(2.dp, Color(0xFF30D158), shape)
                        } else Modifier
                    )
                    .macOsCursor(
                        isFocused = isFocused,
                        config = cursorConfig,
                        cornerRadius = iconCornerRadius.dp
                    )
            )

            if (isPinned) {
                Box(
                    modifier = Modifier
                        .offset(x = 4.dp, y = (-4).dp)
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF30D158))
                        .border(1.dp, Color.White, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text("✓", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

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
fun DialogButton(
    text: String,
    accentColor: Color,
    cursorConfig: CursorConfig,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (isFocused) accentColor.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.09f))
            .macOsCursor(isFocused = isFocused, config = cursorConfig, cornerRadius = 12.dp)
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .clickable { onClick() }
            .padding(vertical = 11.dp, horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text = text, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

fun fetchOrelWeather(): WeatherData? {
    fetchFromYandexOrel()?.let { return it }
    fetchFromGismeteoOrel()?.let { return it }
    return fetchFromOpenMeteoOrel()
}

fun fetchFromYandexOrel(): WeatherData? {
    return try {
        val conn = URL("https://yandex.ru/pogoda/ru/orel").openConnection() as HttpURLConnection
        conn.setRequestProperty(
            "User-Agent",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/124.0.0.0 Safari/537.36"
        )
        conn.connectTimeout = 4500
        conn.readTimeout = 4500
        val html = conn.inputStream.bufferedReader().use { it.readText() }

        val tempRegex = Regex("""fact__temp[^>]*>.*?([+\-−]?\d+)""", RegexOption.DOT_MATCHES_ALL)
        val windRegex = Regex("""wind-speed[^>]*>([\d,.]+)""")
        val condRegex = Regex("""link__condition[^>]*>([^<]+)<""")

        val tempVal = tempRegex.find(html)?.groupValues?.get(1)?.replace("−", "-") ?: return null
        val windVal = windRegex.find(html)?.groupValues?.get(1)?.replace(",", ".") ?: "3.0"
        val condVal = condRegex.find(html)?.groupValues?.get(1)?.trim() ?: "Облачно"

        val (icon, shortText) = mapRussianConditionToStyle(condVal)
        WeatherData(
            city = "Орёл, Россия",
            temp = "$tempVal°C",
            windSpeed = "$windVal м/с",
            icon = icon,
            conditionText = shortText,
            sourceName = "Яндекс Погода"
        )
    } catch (_: Exception) {
        null
    }
}

fun fetchFromGismeteoOrel(): WeatherData? {
    return try {
        val conn = URL("https://www.gismeteo.ru/weather-orel-4432/now/").openConnection() as HttpURLConnection
        conn.setRequestProperty(
            "User-Agent",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/124.0.0.0 Safari/537.36"
        )
        conn.connectTimeout = 4500
        conn.readTimeout = 4500
        val html = conn.inputStream.bufferedReader().use { it.readText() }

        val descRegex = Regex("""content="[^"]*в Орле\s+([^,]+),\s*([+\-−]?\d+)°C[^"]*Ветер\s*(\d+)\s*м/c""", RegexOption.IGNORE_CASE)
        val match = descRegex.find(html) ?: return null

        val condRaw = match.groupValues[1].trim()
        val tempVal = match.groupValues[2].replace("−", "-")
        val windVal = match.groupValues[3]

        val (icon, shortText) = mapRussianConditionToStyle(condRaw)
        WeatherData(
            city = "Орёл, Россия",
            temp = "$tempVal°C",
            windSpeed = "$windVal м/с",
            icon = icon,
            conditionText = shortText,
            sourceName = "Gismeteo"
        )
    } catch (_: Exception) {
        null
    }
}

fun fetchFromOpenMeteoOrel(): WeatherData? {
    return try {
        val url = "https://api.open-meteo.com/v1/forecast?latitude=52.9651&longitude=36.0785&current=temperature_2m,weather_code,wind_speed_10m&wind_speed_unit=ms"
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 4500
        conn.readTimeout = 4500
        val text = conn.inputStream.bufferedReader().use { it.readText() }
        val current = JSONObject(text).getJSONObject("current")

        val temp = Math.round(current.getDouble("temperature_2m")).toInt()
        val tempStr = if (temp > 0) "+$temp°C" else "$temp°C"
        val wind = String.format(Locale.US, "%.1f м/с", current.getDouble("wind_speed_10m"))
        val code = current.getInt("weather_code")

        val (icon, condText) = when (code) {
            0 -> "☀️" to "Ясно, солнце"
            1, 2 -> "🌤️" to "Малооблачно"
            3 -> "☁️" to "Пасмурно"
            45, 48 -> "🌫️" to "Туман"
            51, 53, 55, 61, 63, 65, 80, 81, 82 -> "🌧️" to "Дождь"
            66, 67 -> "🌨️" to "Дождь со снегом"
            71, 73, 75, 77, 85, 86 -> "❄️" to "Снег"
            95, 96, 99 -> "⛈️" to "Гроза"
            else -> "⛅" to "Облачно"
        }

        WeatherData(
            city = "Орёл, Россия",
            temp = tempStr,
            windSpeed = wind,
            icon = icon,
            conditionText = condText,
            sourceName = "Метеосводка Орёл"
        )
    } catch (_: Exception) {
        null
    }
}

fun mapRussianConditionToStyle(cond: String): Pair<String, String> {
    val lower = cond.lowercase(Locale("ru"))
    return when {
        "снег" in lower && "дожд" in lower -> "🌨️" to "Дождь со снегом"
        "снег" in lower || "метел" in lower -> "❄️" to "Снег"
        "гроз" in lower -> "⛈️" to "Гроза"
        "дожд" in lower || "ливень" in lower || "морос" in lower -> "🌧️" to "Дождь"
        "ясно" in lower || "солн" in lower -> "☀️" to "Солнечно"
        "малооблач" in lower || "проясн" in lower -> "🌤️" to "С прояснениями"
        else -> "☁️" to "Пасмурно"
    }
}

fun fetchEurRates(): CurrencyData? {
    return try {
        val conn = URL("https://www.cbr-xml-daily.ru/daily_json.js").openConnection() as HttpURLConnection
        conn.connectTimeout = 4500
        conn.readTimeout = 4500
        val text = conn.inputStream.bufferedReader().use { it.readText() }
        val valute = JSONObject(text).getJSONObject("Valute")

        val eurRub = valute.getJSONObject("EUR").getDouble("Value")
        val usdRub = valute.getJSONObject("USD").getDouble("Value").coerceAtLeast(0.0001)
        val eurUsd = eurRub / usdRub

        CurrencyData(
            eurRub = String.format(Locale.US, "%.2f", eurRub),
            eurUsd = String.format(Locale.US, "%.3f", eurUsd)
        )
    } catch (_: Exception) {
        null
    }
}

fun readSystemMetrics(context: Context, appCpuPercent: Int): SystemMetrics {
    val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    val memInfo = ActivityManager.MemoryInfo()
    am.getMemoryInfo(memInfo)

    val totalRam = (memInfo.totalMem / (1024 * 1024)).coerceAtLeast(1L)
    val availRam = memInfo.availMem / (1024 * 1024)
    val usedRam = (totalRam - availRam).coerceAtLeast(0L)
    val ramPct = ((usedRam * 100L) / totalRam).toInt()
    val cpuEst = (appCpuPercent + (ramPct / 5)).coerceIn(2, 99)

    val stat = StatFs(Environment.getDataDirectory().path)
    val freeGb = stat.availableBytes.toDouble() / (1024.0 * 1024.0 * 1024.0)
    val totalGb = stat.totalBytes.toDouble() / (1024.0 * 1024.0 * 1024.0)

    return SystemMetrics(
        usedRamMb = usedRam,
        totalRamMb = totalRam,
        ramPercent = ramPct,
        cpuPercent = cpuEst,
        freeStorageGb = String.format(Locale.US, "%.1f", freeGb),
        totalStorageGb = String.format(Locale.US, "%.1f", totalGb)
    )
}

fun openSystemSettings(context: Context) {
    try {
        context.startActivity(Intent(Settings.ACTION_SETTINGS).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
    } catch (_: Exception) {}
}

fun openWifiSettings(context: Context) {
    try {
        context.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
    } catch (_: Exception) { openSystemSettings(context) }
}

fun openBluetoothSettings(context: Context) {
    try {
        context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
    } catch (_: Exception) { openSystemSettings(context) }
}

fun loadTvInputs(context: Context): List<TvSourceItem> {
    return try {
        val manager = context.getSystemService(Context.TV_INPUT_SERVICE) as? TvInputManager ?: return emptyList()
        manager.tvInputList
            .filter { it.isPassthroughInput || it.type == TvInputInfo.TYPE_HDMI || it.type == TvInputInfo.TYPE_TUNER }
            .map { info ->
                val customLabel = info.loadCustomLabel(context)?.toString()
                val baseLabel = info.loadLabel(context)?.toString() ?: info.id
                val label = if (!customLabel.isNullOrBlank()) "$baseLabel ($customLabel)" else baseLabel
                val state = try { manager.getInputState(info.id) } catch (_: Exception) { TvInputManager.INPUT_STATE_CONNECTED }
                TvSourceItem(info.id, label, state == TvInputManager.INPUT_STATE_CONNECTED)
            }
    } catch (_: Exception) { emptyList() }
}

fun switchToTvInput(context: Context, inputId: String) {
    try {
        val uri = TvContract.buildChannelUriForPassthroughInput(inputId)
        context.startActivity(Intent(Intent.ACTION_VIEW, uri).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
    } catch (_: Exception) { openSystemInputsMenu(context) }
}

fun openFallbackHdmiPort(context: Context, portNumber: Int) {
    for (action in listOf("com.android.tv.action.VIEW_INPUT", "android.media.tv.action.SETUP_INPUTS")) {
        try {
            context.startActivity(Intent(action).apply {
                putExtra("input_port", portNumber)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
            return
        } catch (_: Exception) {}
    }
    openSystemInputsMenu(context)
}

fun openSystemInputsMenu(context: Context) {
    val intents = listOf(
        Intent("com.android.tv.action.VIEW_INPUT"),
        Intent("android.media.tv.action.SETUP_INPUTS"),
        Intent(Intent.ACTION_MAIN).setClassName("com.android.tv", "com.android.tv.MainActivity"),
        Intent(Intent.ACTION_MAIN).setClassName("com.google.android.tv", "com.android.tv.MainActivity")
    )
    for (intent in intents) {
        try {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            return
        } catch (_: Exception) {}
    }
    openSystemSettings(context)
}

fun saveWallpaperFromUriOptimized(context: Context, uri: Uri): ImageBitmap? {
    return try {
        val options = BitmapFactory.Options().apply {
            inSampleSize = 2
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        val bitmap = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        }
        if (bitmap != null) {
            val file = File(context.filesDir, "custom_wallpaper.jpg")
            FileOutputStream(file).use { out -> bitmap.compress(Bitmap.CompressFormat.JPEG, 88, out) }
            bitmap.asImageBitmap()
        } else null
    } catch (_: Exception) { null }
}

fun loadFromDownloadsOptimized(context: Context): ImageBitmap? {
    return try {
        val dlFile = File("/sdcard/Download/wallpaper.jpg")
        if (dlFile.exists()) {
            val options = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.RGB_565 }
            val bitmap = BitmapFactory.decodeFile(dlFile.absolutePath, options)
            if (bitmap != null) {
                val dest = File(context.filesDir, "custom_wallpaper.jpg")
                FileOutputStream(dest).use { out -> bitmap.compress(Bitmap.CompressFormat.JPEG, 88, out) }
                bitmap.asImageBitmap()
            } else null
        } else null
    } catch (_: Exception) { null }
}

fun loadSavedWallpaperOptimized(context: Context): ImageBitmap? {
    val file = File(context.filesDir, "custom_wallpaper.jpg")
    if (!file.exists()) return null
    val options = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.RGB_565 }
    return BitmapFactory.decodeFile(file.absolutePath, options)?.asImageBitmap()
}

fun saveVideoFromUri(context: Context, uri: Uri): String? {
    return try {
        val dest = File(context.filesDir, "custom_wallpaper.mp4")
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(dest).use { output -> input.copyTo(output) }
        }
        if (dest.exists()) dest.absolutePath else null
    } catch (_: Exception) { null }
}

fun loadVideoFromDownloads(context: Context): String? {
    return try {
        val dlVideo = File("/sdcard/Download/wallpaper.mp4")
        if (dlVideo.exists()) {
            val dest = File(context.filesDir, "custom_wallpaper.mp4")
            dlVideo.copyTo(dest, overwrite = true)
            dest.absolutePath
        } else null
    } catch (_: Exception) { null }
}

fun getSavedVideoPath(context: Context): String? {
    val file = File(context.filesDir, "custom_wallpaper.mp4")
    return if (file.exists()) file.absolutePath else null
}

fun loadInstalledAppsOptimized(context: Context): List<AppItem> {
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
                result[pkg] = AppItem(label, pkg, drawableToOptimizedImageBitmap(drawable))
            }
        }
    }
    return result.values.sortedBy { it.label.lowercase() }
}

fun drawableToOptimizedImageBitmap(drawable: Drawable): ImageBitmap {
    val targetSize = 72
    val bitmap = Bitmap.createBitmap(targetSize, targetSize, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    drawable.setBounds(0, 0, targetSize, targetSize)
    drawable.draw(canvas)
    return bitmap.asImageBitmap()
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