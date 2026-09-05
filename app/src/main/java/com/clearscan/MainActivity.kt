/*
 * ClearScan main activity and view model
 * Copyright (c) 2026 SuiYueMengHen (original code, MIT License)
 * Modifications Copyright (c) 2026 ant-cave <antmmmmm@126.com>
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * Based on ClearScan by SuiYueMengHen (MIT License).
 * ant-cave modifications:
 *  - OpenCV-accelerated document filters (adaptive threshold, unsharp mask, white balance)
 *  - Cloud translation via OpenAI-compatible chat APIs (DeepSeek, Kimi, Qwen, ...)
 *  - Local llama.cpp / Hy-MT2 inference removed in favor of the cloud engine
 *  - Follow-system language and light/dark theme
 */

package com.clearscan

import android.Manifest
import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.print.PrintAttributes
import android.print.PrintManager
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.annotation.VisibleForTesting
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Camera
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.exifinterface.media.ExifInterface
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Filter
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.FlashAuto
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.RotateLeft
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.key
import androidx.compose.runtime.setValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewModelScope
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.Dispatchers
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfByte
import org.opencv.core.Scalar
import org.opencv.core.Size as CvSize
import org.opencv.imgproc.Imgproc
import java.text.SimpleDateFormat
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import com.google.android.gms.tasks.Tasks
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

private val Teal = ComposeColor(0xFF0FA7A0)
private val TealDark = ComposeColor(0xFF07847F)
private val TextDark = ComposeColor(0xFF111827)
private val Muted = ComposeColor(0xFF737B8C)
private val Soft = ComposeColor(0xFFF4F6F8)

object AppLogger {
    private const val MAX_LOG_BYTES = 512 * 1024
    private var appContext: Context? = null
    private var previousHandler: Thread.UncaughtExceptionHandler? = null
    private var initialized = false

    fun init(context: Context) {
        appContext = context.applicationContext
        if (initialized) return
        initialized = true
        previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            e("Crash", "Uncaught exception on ${thread.name}", throwable)
            previousHandler?.uncaughtException(thread, throwable)
        }
        i("App", "Logger initialized")
    }

    fun file(context: Context? = appContext): File {
        val root = File((context ?: error("Logger context missing")).filesDir, "logs")
        root.mkdirs()
        return File(root, "clearscan.log")
    }

    fun read(): String = runCatching {
        val log = file()
        if (log.exists()) log.readText() else ""
    }.getOrDefault("")

    fun clear() {
        runCatching { file().writeText("") }
        i("Log", "Log cleared")
    }

    fun i(tag: String, message: String) = write("INFO", tag, message)
    fun w(tag: String, message: String) = write("WARN", tag, message)
    fun e(tag: String, message: String, throwable: Throwable? = null) {
        val detail = if (throwable == null) message else "$message\n${throwable.stackTraceToString()}"
        write("ERROR", tag, detail)
    }

    private fun write(level: String, tag: String, message: String) {
        val context = appContext ?: return
        runCatching {
            val log = file(context)
            rotateIfNeeded(log)
            val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())
            log.appendText("$time [$level] $tag: $message\n")
        }
    }

    private fun rotateIfNeeded(log: File) {
        if (!log.exists() || log.length() <= MAX_LOG_BYTES) return
        val text = log.readText()
        log.writeText(text.takeLast(MAX_LOG_BYTES / 2))
    }
}

private fun defaultCropPoints() = listOf(
    Offset(0.06f, 0.06f),
    Offset(0.94f, 0.06f),
    Offset(0.94f, 0.94f),
    Offset(0.06f, 0.94f),
)

private fun Offset.coerceCropPoint() = Offset(x.coerceIn(0f, 1f), y.coerceIn(0f, 1f))

/** Maps normalized crop points into the coordinate space of a 90°-rotated bitmap. */
private fun rotateCropPoints(points: List<Offset>, clockwise: Boolean): List<Offset> =
    points.map { if (clockwise) Offset(1f - it.y, it.x) else Offset(it.y, 1f - it.x) }.map { it.coerceCropPoint() }

private fun normalizeQuarters(quarters: Int): Int = ((quarters % 4) + 4) % 4

fun encodeCropPoints(points: List<Offset>): String = points.take(4).joinToString(";") { "${it.x},${it.y}" }

fun decodeCropPoints(value: String): List<Offset> = value.split(';').mapNotNull { pair ->
    val values = pair.split(',')
    if (values.size != 2) null else {
        val x = values[0].toFloatOrNull()
        val y = values[1].toFloatOrNull()
        if (x == null || y == null) null else Offset(x.coerceIn(0f, 1f), y.coerceIn(0f, 1f))
    }
}.takeIf { it.size == 4 } ?: defaultCropPoints()

private fun requiredTypesFor(tool: String?): Set<String> = when (tool) {
    "PDF to Image", "PDF Edit", "Merge PDF", "Split PDF", "Compress PDF" -> setOf("PDF")
    "Image to PDF", "Image Format Converter" -> setOf("JPG", "JPEG", "PNG", "WEBP", "BMP", "IMAGE")
    "Watermark", "Add Signature" -> setOf("PDF", "JPG", "JPEG", "PNG", "WEBP", "BMP", "IMAGE")
    else -> emptySet()
}

private fun minSelectionFor(tool: String?): Int = if (tool == "Merge PDF") 2 else 1

private fun maxSelectionFor(tool: String?): Int = if (tool == "Merge PDF") Int.MAX_VALUE else 1

private fun defaultToolOption(tool: String): String = when (tool) {
    "Split PDF" -> "All pages"
    "Compress PDF" -> "Medium"
    "Image Format Converter" -> "PNG"
    else -> "Standard"
}

private fun toolOptions(tool: String?): List<String> = when (tool) {
    "Split PDF" -> listOf("All pages", "First page")
    "Compress PDF" -> listOf("Low", "Medium", "High")
    "Image Format Converter" -> listOf("JPEG", "PNG", "WEBP", "BMP", "PDF")
    else -> emptyList()
}

private fun selectionHint(tool: String): String {
    val min = minSelectionFor(tool)
    val types = requiredTypesFor(tool).joinToString("/")
    return if (min > 1) "Select at least $min $types documents first." else "Select a $types document first."
}

private fun selectionHint(tool: String, settings: AppSettings): String {
    if (!isChineseUi(settings)) return selectionHint(tool)
    val min = minSelectionFor(tool)
    val types = requiredTypesFor(tool).joinToString("/")
    return if (min > 1) "请先选择至少 $min 个 $types 文件。" else "请先选择一个 $types 文件。"
}

private fun toolLabel(tool: String, settings: AppSettings): String = when (tool) {
    "Merge PDF" -> tr(settings, "Merge PDF", "合并 PDF")
    "Split PDF" -> tr(settings, "Split PDF", "拆分 PDF")
    "Compress PDF" -> tr(settings, "Compress PDF", "压缩 PDF")
    "PDF to Image" -> tr(settings, "PDF to Image", "PDF 转图片")
    "Image to PDF" -> tr(settings, "Image to PDF", "图片转 PDF")
    "Image Format Converter" -> tr(settings, "Image Format Converter", "图片格式转换")
    "PDF Edit" -> tr(settings, "PDF Edit", "PDF 编辑")
    "Watermark" -> tr(settings, "Watermark", "添加水印")
    "Add Signature" -> tr(settings, "Add Signature", "添加签名")
    "QR Code Scan" -> tr(settings, "QR Code Scan", "二维码扫描")
    "ID Card Scan" -> tr(settings, "ID Card Scan", "证件扫描")
    "Translate" -> tr(settings, "Translate", "翻译")
    else -> tool
}

fun isChineseUi(settings: AppSettings): Boolean {
    if (settings.language == "中文") return true
    if (settings.language == "Auto") return Locale.getDefault().language.startsWith("zh")
    return false
}

fun tr(settings: AppSettings, english: String, chinese: String): String {
    return if (isChineseUi(settings)) chinese else english
}

@Composable
fun isDarkTheme(settings: AppSettings): Boolean {
    return settings.theme == "Dark" || (settings.theme == "System" && isSystemInDarkTheme())
}

class MainActivity : ComponentActivity() {
    private val model: ClearScanViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppLogger.init(this)
        AppLogger.i("MainActivity", "onCreate")
        setContent {
            ClearScanTheme {
                ClearScanApp(model)
            }
        }
    }
}

/** Filter presets shown on the filter screen; also validates the persisted default filter. */
val DocumentFilters = listOf("Smart Gray", "Magic Color", "B&W", "Ink", "White Paper")

data class AppSettings(
    val language: String = "Auto",
    val theme: String = "System",
    val loggedIn: Boolean = false,
    val accountName: String = "Guest",
    val accountEmail: String = "",
    val passwordMap: Map<Long, String> = emptyMap(),
    val defaultSavePath: String = "Internal Storage",
    val defaultFilter: String = "B&W",
    val autoCheckUpdates: Boolean = true,
    val autoDownloadUpdates: Boolean = true,
    val wifiOnlyUpdates: Boolean = true,
    val cameraGrid: Boolean = false,
    val cameraEnhance: Boolean = true,
    // "High" (CAPTURE_MODE_MAXIMIZE_QUALITY) is the default: full-sensor stills.
    val cameraResolution: String = "High",
)

enum class Tab(val title: String, val icon: ImageVector) {
    Home("Home", Icons.Default.Home),
    Docs("Docs", Icons.Default.Description),
    Camera("Scan", Icons.Default.CameraAlt),
    Tools("Tools", Icons.Default.GridView),
    Me("Me", Icons.Default.AccountCircle),
}

/** Localized bottom-bar label; enum titles stay English as internal identifiers. */
fun tabLabel(settings: AppSettings, tab: Tab): String = when (tab) {
    Tab.Home -> tr(settings, "Home", "首页")
    Tab.Docs -> tr(settings, "Docs", "文档")
    Tab.Camera -> tr(settings, "Scan", "扫描")
    Tab.Tools -> tr(settings, "Tools", "工具")
    Tab.Me -> tr(settings, "Me", "我的")
}

enum class Screen {
    Shell, Camera, Crop, Edit, Filter, Adjust, Save, Detail, Share, ToolSelect, WatermarkEditor, SignatureEditor, Translate, Settings, Account, Help, About, Legal, AppLogs
}

enum class DocumentCaptureMode { Single, Multi }

@VisibleForTesting
fun shouldOpenCropAfterCapture(scanMode: ScanMode, captureMode: DocumentCaptureMode): Boolean =
    scanMode == ScanMode.Document && captureMode == DocumentCaptureMode.Single

data class TranslationState(
    val sourceLang: String = "Auto",
    val targetLang: String = "Chinese",
    val inputText: String = "",
    val outputText: String = "",
    val isTranslating: Boolean = false,
    val progress: Float = 0f,
    val error: String? = null,
    val cloudBaseUrl: String = "https://api.deepseek.com",
    val cloudModel: String = "deepseek-chat",
    val cloudApiKey: String = "",
)

data class UiState(
    val tab: Tab = Tab.Home,
    val screen: Screen = Screen.Shell,
    val documents: List<Document> = emptyList(),
    val folders: List<FolderEntity> = emptyList(),
    val currentFolderId: Long? = null,
    val query: String = "",
    val selected: Document? = null,
    val scanBitmap: Bitmap? = null,
    val processedBitmap: Bitmap? = null,
    val scanSourcePath: String? = null,
    val cropPoints: List<Offset> = defaultCropPoints(),
    val autoCropPoints: List<Offset> = emptyList(),
    val detectionStatus: DocumentDetectionStatus = DocumentDetectionStatus.Idle,
    val detectionConfidence: Float = 0f,
    val detectionProcessingMs: Long = 0L,
    val scanMode: ScanMode = ScanMode.Document,
    val documentCaptureMode: DocumentCaptureMode = DocumentCaptureMode.Single,
    val liveDocumentFrame: LiveDocumentFrame? = null,
    val scanSessionId: Long? = null,
    val draftPages: List<DraftScanPageEntity> = emptyList(),
    val currentDraftIndex: Int = 0,
    val codeResult: CodeScanResult? = null,
    val updateInfo: UpdateInfo? = null,
    val updateDownload: UpdateDownloadState = UpdateDownloadState(),
    val checkingUpdate: Boolean = false,
    val backStack: List<Screen> = emptyList(),
    val activeTool: String? = null,
    val selectedToolIds: Set<Long> = emptySet(),
    val toolOption: String = "Medium",
    val cropPreset: String = "Original",
    val scanRotationQuarters: Int = 0,
    val selectedFilter: String = "B&W",
    /** Bumped after each per-page edit so the edit pager reloads its page bitmaps. */
    val editVersion: Int = 0,
    val translationState: TranslationState = TranslationState(),
    val savedResultDetail: Boolean = false,
    val legalTitle: String = "",
    val logText: String = "",
    val captureMessage: String? = null,
    val settings: AppSettings = AppSettings(),
    val busy: Boolean = false,
)

class ClearScanViewModel(application: Application) : AndroidViewModel(application) {
    private val database = Room.databaseBuilder(application, ClearScanDatabase::class.java, "clearscan.db")
        .addMigrations(ClearScanDatabase.MIGRATION_1_2)
        .build()
    private val dao = database.documentDao()
    private val prefs = application.getSharedPreferences("clearscan-settings", Context.MODE_PRIVATE)
    private val settingsRepository = SettingsRepository(application)
    private val settingsFlow = MutableStateFlow(loadSettings())
    private val queryFlow = MutableStateFlow("")
    private val navFlow = MutableStateFlow(UiState())
    private var allDocuments: List<Document> = emptyList()
    private var detectionRequestId = 0L

    val ui: StateFlow<UiState> = combine(dao.observeDocuments(), dao.observeFolders(), settingsFlow, queryFlow, navFlow) { docs, folders, settings, query, nav ->
        allDocuments = docs
        val visibleDocs = docs.filter { document ->
            val matchesQuery = document.title.contains(query, ignoreCase = true)
            matchesQuery && (query.isNotBlank() || document.folderId == nav.currentFolderId)
        }
        nav.copy(
            documents = visibleDocs,
            folders = folders,
            query = query,
            settings = settings,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState())

    init {
        AppLogger.init(application)
        AppLogger.i("ViewModel", "ClearScanViewModel created")
        refreshTranslationModelState()
        viewModelScope.launch {
            settingsFlow.value = settingsRepository.load(settingsFlow.value)
            // New sessions start with the user's default filter preselected on the filter screen.
            navFlow.value = navFlow.value.copy(selectedFilter = settingsFlow.value.defaultFilter)
            val session = withContext(Dispatchers.IO) { dao.latestSession() }
            if (session != null) {
                val pages = withContext(Dispatchers.IO) { dao.draftPages(session.id) }
                if (pages.isNotEmpty()) {
                    navFlow.value = navFlow.value.copy(
                        scanSessionId = session.id,
                        scanMode = runCatching { ScanMode.valueOf(session.mode) }.getOrDefault(ScanMode.Document),
                        documentCaptureMode = if (session.mode == ScanMode.Document.name) DocumentCaptureMode.Multi else DocumentCaptureMode.Single,
                        draftPages = pages,
                        captureMessage = tr(settingsFlow.value, "An unfinished ${pages.size}-page scan was restored", "已恢复未完成的 ${pages.size} 页扫描"),
                    )
                }
            }
            seedIfEmpty()
        }
    }

    private fun go(screen: Screen, update: UiState.() -> UiState = { this }) {
        val current = navFlow.value
        navFlow.value = current.update().copy(screen = screen, backStack = current.backStack + current.screen)
    }

    private fun replace(screen: Screen, update: UiState.() -> UiState = { this }) {
        navFlow.value = navFlow.value.update().copy(screen = screen)
    }

    fun selectTab(tab: Tab) {
        AppLogger.i("Navigation", "Select tab ${tab.name}")
        navFlow.value = navFlow.value.copy(tab = tab, screen = Screen.Shell, backStack = emptyList(), activeTool = null, selectedToolIds = emptySet())
    }

    fun setQuery(query: String) {
        queryFlow.value = query
    }

    fun openFolder(folderId: Long?) {
        queryFlow.value = ""
        navFlow.value = navFlow.value.copy(currentFolderId = folderId)
    }

    fun createFolder(name: String) {
        val clean = name.trim().take(80)
        if (clean.isBlank()) return
        val state = navFlow.value
        if (state.folders.any { it.parentId == state.currentFolderId && it.name.equals(clean, true) }) {
            navFlow.value = state.copy(captureMessage = if (isChineseUi(state.settings)) "同级目录下已存在同名文件夹" else "A folder with this name already exists here")
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            val id = System.currentTimeMillis()
            dao.upsertFolder(FolderEntity(id, clean, state.currentFolderId, id))
            AppLogger.i("Folder", "Create folder id=$id parent=${state.currentFolderId}")
        }
    }

    fun renameFolder(folder: FolderEntity, name: String) {
        val clean = name.trim().take(80)
        if (clean.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) { dao.upsertFolder(folder.copy(name = clean)) }
    }

    fun deleteFolder(folder: FolderEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.moveFolderDocumentsToParent(folder.id, folder.parentId)
            dao.moveChildFoldersToParent(folder.id, folder.parentId)
            dao.deleteFolder(folder.id)
            if (navFlow.value.currentFolderId == folder.id) navFlow.value = navFlow.value.copy(currentFolderId = folder.parentId)
            AppLogger.i("Folder", "Delete folder id=${folder.id}; contents moved to parent=${folder.parentId}")
        }
    }

    fun moveDocument(document: Document, folderId: Long?) {
        viewModelScope.launch(Dispatchers.IO) { dao.moveDocument(document.id, folderId) }
    }

    fun openCamera() {
        AppLogger.i("Scan", "Open camera")
        startScanMode(ScanMode.Document)
    }

    fun openQrScanner() {
        startScanMode(ScanMode.QrCode)
    }

    fun startScanMode(mode: ScanMode) {
        AppLogger.i("Scan", "Open camera mode=${mode.name}")
        val sessionId = if (mode in listOf(ScanMode.Document, ScanMode.Book, ScanMode.IdCard)) System.currentTimeMillis() else null
        if (sessionId != null) {
            viewModelScope.launch(Dispatchers.IO) {
                dao.upsertSession(ScanSessionEntity(sessionId, mode.name, sessionId, sessionId))
            }
        }
        go(Screen.Camera) {
            copy(
                captureMessage = null,
                scanMode = mode,
                documentCaptureMode = DocumentCaptureMode.Single,
                liveDocumentFrame = null,
                scanSessionId = sessionId,
                draftPages = emptyList(),
                currentDraftIndex = 0,
                activeTool = null,
                selectedToolIds = emptySet(),
            )
        }
    }

    fun changeScanMode(mode: ScanMode) {
        val state = navFlow.value
        if (state.draftPages.isNotEmpty()) {
            navFlow.value = state.copy(captureMessage = if (isChineseUi(state.settings)) "请先完成或退出当前多页扫描" else "Finish or exit the current multi-page scan first")
            return
        }
        val previousSessionId = state.scanSessionId
        val sessionId = if (mode in listOf(ScanMode.Document, ScanMode.Book, ScanMode.IdCard)) System.currentTimeMillis() else null
        navFlow.value = state.copy(scanMode = mode, scanSessionId = sessionId, codeResult = null, liveDocumentFrame = null, captureMessage = null)
        viewModelScope.launch(Dispatchers.IO) {
            if (previousSessionId != null && previousSessionId != sessionId) dao.deleteSession(previousSessionId)
            if (sessionId != null) dao.upsertSession(ScanSessionEntity(sessionId, mode.name, sessionId, System.currentTimeMillis()))
        }
    }

    fun changeDocumentCaptureMode(mode: DocumentCaptureMode) {
        val state = navFlow.value
        if (state.scanMode != ScanMode.Document || state.draftPages.isNotEmpty()) return
        navFlow.value = state.copy(documentCaptureMode = mode, captureMessage = null)
    }

    fun discardScanAndChangeMode(mode: ScanMode) {
        val state = navFlow.value
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                state.scanSessionId?.let { sessionId ->
                    dao.deleteDraftPages(sessionId)
                    dao.deleteSession(sessionId)
                }
            }
            navFlow.value = navFlow.value.copy(draftPages = emptyList(), scanSessionId = null, currentDraftIndex = 0)
            changeScanMode(mode)
        }
    }

    fun onLiveDocumentFrame(frame: LiveDocumentFrame?) {
        val state = navFlow.value
        if (state.screen != Screen.Camera || state.scanMode !in listOf(ScanMode.Document, ScanMode.Book, ScanMode.IdCard)) return
        navFlow.value = state.copy(liveDocumentFrame = frame)
    }

    fun onCodeDetected(result: CodeScanResult) {
        if (navFlow.value.codeResult?.rawValue == result.rawValue) return
        AppLogger.i("CodeScan", "Detected mode=${navFlow.value.scanMode} format=${result.format} type=${result.valueType}")
        navFlow.value = navFlow.value.copy(codeResult = result, captureMessage = null)
    }

    fun clearCodeResult() {
        navFlow.value = navFlow.value.copy(codeResult = null)
    }

    fun back() {
        val state = navFlow.value
        AppLogger.i("Navigation", "Back from ${state.screen}")
        if (state.screen == Screen.Detail && state.savedResultDetail) {
            navFlow.value = state.copy(
                screen = Screen.Shell,
                tab = Tab.Docs,
                selected = null,
                backStack = emptyList(),
                savedResultDetail = false,
                activeTool = null,
                selectedToolIds = emptySet(),
            )
            return
        }
        val previous = state.backStack.lastOrNull()
        navFlow.value = if (previous != null) {
            state.copy(
                screen = previous,
                backStack = state.backStack.dropLast(1),
                activeTool = if (state.screen == Screen.ToolSelect) null else state.activeTool,
                selectedToolIds = if (state.screen == Screen.ToolSelect) emptySet() else state.selectedToolIds,
            )
        } else {
            when (state.screen) {
                Screen.Shell -> state
                else -> state.copy(screen = Screen.Shell, selected = null, activeTool = null, selectedToolIds = emptySet())
            }
        }
    }

    fun captureSample() {
        viewModelScope.launch {
            AppLogger.i("Scan", "Capture sample document")
            val bitmap = withContext(Dispatchers.Default) { ImageProcessor.sampleDocumentBitmap() }
            go(Screen.Crop) {
                copy(
                scanBitmap = bitmap,
                processedBitmap = bitmap,
                scanSourcePath = null,
                cropPoints = defaultCropPoints(),
                autoCropPoints = emptyList(),
                detectionStatus = DocumentDetectionStatus.Detecting,
                detectionConfidence = 0f,
                cropPreset = "Auto",
                captureMessage = tr(settingsFlow.value, "Capturing... Please hold steady", "正在拍摄，请保持稳定"),
                )
            }
            detectForCrop(bitmap)
        }
    }

    fun capturePhotoFile(file: File) {
        viewModelScope.launch {
            AppLogger.i("Camera", "CameraX photo file: ${file.absolutePath}, bytes=${file.length()}")
            val sourceFile = withContext(Dispatchers.IO) {
                val optimized = File(file.parentFile, "${file.nameWithoutExtension}-optimized.jpg")
                ImageProcessor.optimizeCapturedPhoto(file, optimized) ?: file
            }
            val bitmap = withContext(Dispatchers.IO) {
                ImageProcessor.decodeCameraBitmap(sourceFile.absolutePath, maxDimension = 2048)
            }
            if (bitmap == null) {
                AppLogger.w("Camera", "Photo decode failed")
                navFlow.value = navFlow.value.copy(captureMessage = tr(settingsFlow.value, "Photo capture failed. Please try again.", "照片拍摄失败，请重试"))
            } else if (navFlow.value.scanMode in listOf(ScanMode.QrCode, ScanMode.Barcode)) {
                val result = withContext(Dispatchers.Default) { ImageProcessor.scanQr(bitmap) }
                AppLogger.i("CodeScan", "${navFlow.value.scanMode} result: ${result ?: "none"}")
                replace(Screen.Camera) {
                    copy(
                    captureMessage = result ?: tr(settingsFlow.value, "No matching code found. Try again.", "未识别到对应编码，请重试"),
                    )
                }
            } else {
                AppLogger.i(
                    "Scan",
                    "Photo preview decoded ${bitmap.width}x${bitmap.height}, optimizedBytes=${sourceFile.length()}, opening crop",
                )
                appendCapturedBitmap(bitmap, sourceFile)
            }
        }
    }

    fun importBitmap(uri: Uri, context: Context) {
        viewModelScope.launch {
            val bitmap = withContext(Dispatchers.IO) {
                ImageProcessor.decodeUriBitmap(context, uri, maxDimension = 4096)
            }
            if (bitmap == null) {
                AppLogger.w("Scan", "Unable to decode imported image: $uri")
                navFlow.value = navFlow.value.copy(captureMessage = tr(settingsFlow.value, "Unable to open this image.", "无法打开此图片"))
                return@launch
            }
            appendCapturedBitmap(bitmap, null)
        }
    }

    fun importBitmaps(uris: List<Uri>, context: Context) {
        viewModelScope.launch {
            var captured = 0
            for (uri in uris) {
                val bitmap = withContext(Dispatchers.IO) {
                    ImageProcessor.decodeUriBitmap(context, uri, maxDimension = 4096)
                }
                if (bitmap != null) {
                    appendCapturedBitmap(bitmap, null)
                    captured++
                }
            }
            if (captured == 0) {
                navFlow.value = navFlow.value.copy(captureMessage = tr(settingsFlow.value, "Unable to open any images.", "无法打开任何图片"))
            } else {
                navFlow.value = navFlow.value.copy(captureMessage = tr(settingsFlow.value, "Imported $captured page(s)", "已导入 $captured 页"))
            }
        }
    }

    private suspend fun appendCapturedBitmap(bitmap: Bitmap, sourceFile: File?) {
        val state = navFlow.value
        val sessionId = state.scanSessionId ?: System.currentTimeMillis().also { id ->
            dao.upsertSession(ScanSessionEntity(id, state.scanMode.name, id, id))
            navFlow.value = navFlow.value.copy(scanSessionId = id)
        }
        if (state.draftPages.size >= 100 || (state.scanMode == ScanMode.IdCard && state.draftPages.size >= 2)) {
            navFlow.value = navFlow.value.copy(captureMessage = if (state.scanMode == ScanMode.IdCard) "ID front and back are already captured." else "A scan can contain up to 100 pages.")
            return
        }
        val pageBitmaps = listOf(bitmap)
        val remaining = (if (state.scanMode == ScanMode.IdCard) 2 else 100) - state.draftPages.size
        val sessionDir = File(getApplication<Application>().filesDir, "scan_sessions/$sessionId").apply { mkdirs() }
        val added = mutableListOf<DraftScanPageEntity>()
        pageBitmaps.take(remaining).forEachIndexed { splitIndex, pageBitmap ->
            val pageId = System.currentTimeMillis() + splitIndex
            val original = File(sessionDir, "$pageId-original.jpg")
            if (pageBitmaps.size == 1 && sourceFile != null && sourceFile.exists()) {
                sourceFile.copyTo(original, overwrite = true)
            } else {
                ImageProcessor.writeJpeg(pageBitmap, original, 88)
            }
            val thumbnail = File(sessionDir, "$pageId-thumb.jpg")
            ImageProcessor.writeJpeg(ImageProcessor.previewBitmap(pageBitmap, 640) ?: pageBitmap, thumbnail, 76)
            val detection = withContext(Dispatchers.Default) { DocumentEdgeDetector.detect(pageBitmap, detectionProfileFor(state.scanMode)) }
            val liveFallback = state.liveDocumentFrame?.takeIf { it.corners.size == 4 && it.confidence >= .42f }
            val corners = detection.corners.takeIf { detection.status == DocumentDetectionStatus.Detected && it.size == 4 }
                ?: liveFallback?.corners
                ?: defaultCropPoints()
            val confidence = if (detection.corners.size == 4) detection.confidence else liveFallback?.confidence ?: 0f
            val entity = DraftScanPageEntity(
                id = pageId,
                sessionId = sessionId,
                pageIndex = state.draftPages.size + added.size,
                originalPath = original.absolutePath,
                thumbnailPath = thumbnail.absolutePath,
                cropPoints = encodeCropPoints(corners),
                confidence = confidence,
                sourceType = state.scanMode.name,
            )
            dao.upsertDraftPage(entity)
            added += entity
        }
        val pages = state.draftPages + added
        dao.upsertSession(ScanSessionEntity(sessionId, state.scanMode.name, sessionId, System.currentTimeMillis(), "CAPTURING"))
        navFlow.value = navFlow.value.copy(
            scanSessionId = sessionId,
            draftPages = pages,
            captureMessage = if (isChineseUi(state.settings)) "已拍摄 ${pages.size} 页" else "${pages.size} page${if (pages.size == 1) "" else "s"} captured",
        )
        if (shouldOpenCropAfterCapture(state.scanMode, state.documentCaptureMode) && added.isNotEmpty()) {
            openDraftPage(added.first(), pages.indexOfFirst { it.id == added.first().id })
        }
    }

    fun finishScanSession() {
        val state = navFlow.value
        val first = state.draftPages.firstOrNull() ?: return
        viewModelScope.launch {
            state.scanSessionId?.let { dao.upsertSession(ScanSessionEntity(it, state.scanMode.name, it, System.currentTimeMillis(), "CROPPING")) }
            openDraftPage(first, 0)
        }
    }

    fun resumeScanSession() {
        if (navFlow.value.draftPages.isEmpty()) return
        go(Screen.Camera) { copy(captureMessage = null) }
    }

    fun selectDraftPage(index: Int) {
        navFlow.value.draftPages.getOrNull(index)?.let { openDraftPage(it, index) }
    }

    fun moveDraftPage(from: Int, to: Int) {
        val state = navFlow.value
        if (from !in state.draftPages.indices || to !in state.draftPages.indices || from == to) return
        val reordered = state.draftPages.toMutableList().apply { add(to, removeAt(from)) }.mapIndexed { index, page -> page.copy(pageIndex = index) }
        val selectedId = state.draftPages.getOrNull(state.currentDraftIndex)?.id
        val newIndex = reordered.indexOfFirst { it.id == selectedId }.coerceAtLeast(0)
        navFlow.value = state.copy(draftPages = reordered, currentDraftIndex = newIndex)
        viewModelScope.launch(Dispatchers.IO) { reordered.forEach { dao.upsertDraftPage(it) } }
    }

    fun deleteCurrentDraft(retake: Boolean = false) {
        val state = navFlow.value
        val page = state.draftPages.getOrNull(state.currentDraftIndex) ?: return
        viewModelScope.launch {
            dao.deleteDraftPage(page.id)
            val remaining = state.draftPages.filterNot { it.id == page.id }.mapIndexed { index, item -> item.copy(pageIndex = index) }
            withContext(Dispatchers.IO) { remaining.forEach { dao.upsertDraftPage(it) } }
            if (retake || remaining.isEmpty()) {
                replace(Screen.Camera) { copy(draftPages = remaining, currentDraftIndex = 0, scanBitmap = null, processedBitmap = null) }
            } else {
                navFlow.value = state.copy(draftPages = remaining, currentDraftIndex = state.currentDraftIndex.coerceAtMost(remaining.lastIndex))
                openDraftPage(remaining[navFlow.value.currentDraftIndex], navFlow.value.currentDraftIndex)
            }
        }
    }

    private fun openDraftPage(page: DraftScanPageEntity, index: Int) {
        val decoded = ImageProcessor.decodeCameraBitmap(page.originalPath, 2560) ?: return
        // Draft pages store their original capture unrotated; replay the saved rotation.
        val bitmap = if (page.rotation != 0) ImageProcessor.rotateQuarters(decoded, page.rotation) else decoded
        val corners = decodeCropPoints(page.cropPoints)
        val update: UiState.() -> UiState = {
            copy(
                scanBitmap = bitmap,
                processedBitmap = bitmap,
                scanSourcePath = page.originalPath,
                currentDraftIndex = index,
                cropPoints = corners,
                autoCropPoints = corners,
                detectionStatus = if (page.confidence >= .54f) DocumentDetectionStatus.Detected else DocumentDetectionStatus.LowConfidence,
                detectionConfidence = page.confidence,
                cropPreset = if (page.confidence >= .54f) "Auto" else "Original",
                scanRotationQuarters = page.rotation,
                captureMessage = null,
            )
        }
        if (navFlow.value.screen == Screen.Crop) replace(Screen.Crop, update) else go(Screen.Crop, update)
    }

    private suspend fun detectForCrop(bitmap: Bitmap) {
        val requestId = ++detectionRequestId
        navFlow.value = navFlow.value.copy(
            detectionStatus = DocumentDetectionStatus.Detecting,
            detectionConfidence = 0f,
            captureMessage = tr(settingsFlow.value, "Detecting document edges...", "正在识别文档边缘..."),
        )
        val result = withContext(Dispatchers.Default) { DocumentEdgeDetector.detect(bitmap, detectionProfileFor(navFlow.value.scanMode)) }
        val current = navFlow.value
        if (requestId != detectionRequestId || current.screen != Screen.Crop || current.scanBitmap !== bitmap) {
            AppLogger.i("ScanDetect", "Discard stale detection request=$requestId")
            return
        }
        val accepted = result.status == DocumentDetectionStatus.Detected && result.corners.size == 4
        AppLogger.i(
            "ScanDetect",
            "status=${result.status} confidence=${"%.3f".format(Locale.US, result.confidence)} " +
                "candidates=${result.candidateCount} processingMs=${result.processingMs} input=${bitmap.width}x${bitmap.height} " +
                "reason=${result.reason ?: "none"}",
        )
        navFlow.value = current.copy(
            cropPoints = if (accepted) result.corners else defaultCropPoints(),
            autoCropPoints = if (accepted) result.corners else emptyList(),
            cropPreset = if (accepted) "Auto" else "Original",
            detectionStatus = result.status,
            detectionConfidence = result.confidence,
            detectionProcessingMs = result.processingMs,
            captureMessage = if (accepted) tr(settingsFlow.value, "Document edges detected.", "已识别文档边缘") else tr(settingsFlow.value, "Document edges were unclear. Adjust the corners manually.", "文档边缘不清晰，请手动调整四角"),
        )
    }

    fun redetectDocument() {
        val bitmap = navFlow.value.processedBitmap ?: navFlow.value.scanBitmap ?: return
        viewModelScope.launch { detectForCrop(bitmap) }
    }

    fun toEdit() {
        go(Screen.Edit) { copy(processedBitmap = processedBitmap ?: scanBitmap) }
    }

    fun setCropPoint(index: Int, point: Offset) {
        val points = navFlow.value.cropPoints.toMutableList()
        if (index in points.indices) {
            detectionRequestId++
            points[index] = Offset(point.x.coerceIn(0f, 1f), point.y.coerceIn(0f, 1f))
            navFlow.value = navFlow.value.copy(cropPoints = points, cropPreset = "Custom", detectionStatus = DocumentDetectionStatus.Idle)
        }
    }

    fun setCropPoints(points: List<Offset>) {
        if (points.size >= 4) {
            detectionRequestId++
            navFlow.value = navFlow.value.copy(
                cropPoints = points.take(4).map { it.coerceCropPoint() },
                cropPreset = "Custom",
                detectionStatus = DocumentDetectionStatus.Idle,
            )
        }
    }

    fun setCropPreset(label: String) {
        if (label == "Auto") {
            val auto = navFlow.value.autoCropPoints
            if (auto.size == 4) {
                detectionRequestId++
                navFlow.value = navFlow.value.copy(cropPreset = "Auto", cropPoints = auto, detectionStatus = DocumentDetectionStatus.Detected)
            } else {
                redetectDocument()
            }
            return
        }
        detectionRequestId++
        navFlow.value = navFlow.value.copy(cropPreset = label, cropPoints = defaultCropPoints())
    }

    fun applyCropAndEdit() {
        val previewBitmap = navFlow.value.processedBitmap ?: navFlow.value.scanBitmap ?: return
        val sourcePath = navFlow.value.scanSourcePath
        val quarters = navFlow.value.scanRotationQuarters
        val selectedFilter = navFlow.value.selectedFilter
        val filterParams = FilterParams()
        val allPages = navFlow.value.draftPages.toList()
        if (allPages.isEmpty()) return
        viewModelScope.launch {
            navFlow.value = navFlow.value.copy(busy = true)
            val processedPages = mutableListOf<DraftScanPageEntity>()
            var hasError = false
            for (page in allPages) {
                val pagePoints = decodeCropPoints(page.cropPoints)
                val pageQuarters = page.rotation
                val result = runCatching {
                    withContext(Dispatchers.Default) {
                        val workingBitmap = sourcePath
                            ?.let { ImageProcessor.decodeCameraBitmap(it, maxDimension = 4096) }
                            ?.let { if (pageQuarters != 0) ImageProcessor.rotateQuarters(it, pageQuarters) else it }
                            ?: previewBitmap
                        val corrected = DocumentPerspectiveCorrector.crop(workingBitmap, pagePoints)
                        val enhanced = if (settingsFlow.value.cameraEnhance) ImageProcessor.enhanceDocument(corrected) else corrected
                        if (selectedFilter.isNotBlank() && selectedFilter != "None") {
                            ImageProcessor.filter(enhanced, selectedFilter, filterParams) ?: enhanced
                        } else enhanced
                    }
                }.onFailure { AppLogger.e("Scan", "Crop/filter failed for page ${page.id}", it) }.getOrNull()
                if (result == null) {
                    hasError = true
                    continue
                }
                val processedFile = File(page.originalPath).parentFile?.let { File(it, "${page.id}-processed.jpg") }
                    ?: File(getApplication<Application>().filesDir, "${page.id}-processed.jpg")
                withContext(Dispatchers.IO) { ImageProcessor.writeJpeg(result, processedFile, 90) }
                val updatedPage = page.copy(
                    processedPath = processedFile.absolutePath,
                    cropPoints = encodeCropPoints(pagePoints),
                    rotation = normalizeQuarters(pageQuarters),
                )
                dao.upsertDraftPage(updatedPage)
                processedPages += updatedPage
            }
            if (processedPages.isEmpty()) {
                navFlow.value = navFlow.value.copy(busy = false, captureMessage = tr(settingsFlow.value, "Unable to process photos. Please try again.", "无法处理照片，请重试"))
                return@launch
            }
            val state = navFlow.value
            val stack = if (state.backStack.lastOrNull() == Screen.Edit) state.backStack.dropLast(1) else state.backStack
            AppLogger.i("Scan", "Batch crop+filter completed for ${processedPages.size} pages")
            val currentDraft = state.draftPages.getOrNull(state.currentDraftIndex)
            val firstProcessed = processedPages.firstOrNull()
            navFlow.value = state.copy(
                screen = Screen.Edit,
                draftPages = processedPages,
                processedBitmap = firstProcessed?.let { ImageProcessor.readBitmap(it.processedPath, 1400) } ?: previewBitmap,
                scanBitmap = firstProcessed?.let { ImageProcessor.readBitmap(it.processedPath, 1400) } ?: previewBitmap,
                scanSourcePath = null,
                cropPoints = defaultCropPoints(),
                autoCropPoints = emptyList(),
                detectionStatus = DocumentDetectionStatus.Idle,
                detectionConfidence = 0f,
                scanRotationQuarters = 0,
                selectedFilter = selectedFilter,
                editVersion = state.editVersion + 1,
                busy = false,
                captureMessage = if (hasError) tr(settingsFlow.value, "Some pages failed to process", "部分页面处理失败") else null,
            )
        }
    }

    fun toFilter() {
        go(Screen.Filter)
    }

    fun toAdjust() {
        go(Screen.Adjust)
    }

    fun toCrop() {
        go(Screen.Crop)
    }

    fun toSave() {
        go(Screen.Save)
    }

    fun rotate(clockwise: Boolean = true) {
        val state = navFlow.value
        val bitmap = state.processedBitmap ?: state.scanBitmap ?: return
        if (state.screen != Screen.Crop) {
            navFlow.value = state.copy(processedBitmap = ImageProcessor.rotate(bitmap, clockwise))
            return
        }
        // On the crop screen the rotation must keep the overlay and the detection result
        // in sync with the rotated bitmap; scanBitmap is updated too because edge
        // detection uses it as an identity check for stale requests.
        val rotated = ImageProcessor.rotate(bitmap, clockwise)
        val points = rotateCropPoints(state.cropPoints, clockwise)
        val auto = rotateCropPoints(state.autoCropPoints, clockwise)
        if (state.scanSourcePath == null) {
            // Re-entered from the edit screen: the working bitmap is already the cropped
            // result, so only the in-memory preview and points need to follow.
            navFlow.value = state.copy(processedBitmap = rotated, scanBitmap = rotated, cropPoints = points, autoCropPoints = auto)
            return
        }
        val quarters = state.scanRotationQuarters + if (clockwise) 1 else -1
        navFlow.value = state.copy(
            processedBitmap = rotated,
            scanBitmap = rotated,
            cropPoints = points,
            autoCropPoints = auto,
            scanRotationQuarters = quarters,
        )
        // Track the rotation on the persisted draft so the full-resolution original is
        // rotated the same way when the crop is applied or the page is reopened.
        val currentDraft = state.draftPages.getOrNull(state.currentDraftIndex) ?: return
        viewModelScope.launch {
            val updated = currentDraft.copy(rotation = normalizeQuarters(quarters), cropPoints = encodeCropPoints(points))
            withContext(Dispatchers.IO) {
                runCatching {
                    ImageProcessor.readBitmap(currentDraft.thumbnailPath, 1280)
                        ?.let { ImageProcessor.rotate(it, clockwise) }
                        ?.let { ImageProcessor.writeJpeg(it, File(currentDraft.thumbnailPath), 82) }
                }
            }
            dao.upsertDraftPage(updated)
            navFlow.value = navFlow.value.copy(draftPages = navFlow.value.draftPages.map { if (it.id == updated.id) updated else it })
        }
    }

    /** Returns to the camera for another shot, replacing the current draft page. */
    fun retakePhoto() {
        if (navFlow.value.draftPages.isEmpty()) deleteScan() else deleteCurrentDraft(true)
    }

    fun deleteScan() {
        replace(Screen.Camera) { copy(scanBitmap = null, processedBitmap = null, scanSourcePath = null) }
    }

    fun applyFilter(filter: String, params: FilterParams = FilterParams()) {
        val base = navFlow.value.processedBitmap ?: navFlow.value.scanBitmap ?: return
        val state = navFlow.value
        val stack = if (state.backStack.lastOrNull() == Screen.Edit) state.backStack.dropLast(1) else state.backStack
        navFlow.value = state.copy(busy = true, selectedFilter = filter)
        viewModelScope.launch {
            val filtered = withContext(Dispatchers.Default) { ImageProcessor.filter(base, filter, params) }
            navFlow.value = navFlow.value.copy(screen = Screen.Edit, backStack = stack, processedBitmap = filtered, busy = false)
        }
    }

    fun applyAdjust(brightness: Float, contrast: Float, saturation: Float) {
        val base = navFlow.value.processedBitmap ?: navFlow.value.scanBitmap ?: return
        val state = navFlow.value
        val stack = if (state.backStack.lastOrNull() == Screen.Edit) state.backStack.dropLast(1) else state.backStack
        navFlow.value = state.copy(screen = Screen.Edit, backStack = stack, processedBitmap = ImageProcessor.adjust(base, brightness, contrast, saturation))
    }

    /** Applies a filter to one page of the edit pager and persists it as that page's processed image. */
    fun applyFilterToPage(index: Int, filter: String, params: FilterParams = FilterParams()) {
        val state = navFlow.value
        val page = state.draftPages.getOrNull(index)
        if (page == null) {
            applyFilter(filter, params)
            return
        }
        val sourcePath = page.processedPath.ifBlank { page.originalPath }
        viewModelScope.launch {
            navFlow.value = navFlow.value.copy(busy = true)
            val base = withContext(Dispatchers.IO) {
                val b = ImageProcessor.readBitmap(sourcePath, 2560)
                if (page.processedPath.isBlank() && b != null && page.rotation != 0) ImageProcessor.rotateQuarters(b, page.rotation) else b
            } ?: run { navFlow.value = navFlow.value.copy(busy = false); return@launch }
            val filtered = withContext(Dispatchers.Default) { ImageProcessor.filter(base, filter, params) }
                ?: run { navFlow.value = navFlow.value.copy(busy = false); return@launch }
            val processedFile = File(page.originalPath).parentFile?.let { File(it, "${page.id}-filtered.jpg") }
                ?: File(getApplication<Application>().filesDir, "${page.id}-filtered.jpg")
            withContext(Dispatchers.IO) { ImageProcessor.writeJpeg(filtered, processedFile, 88) }
            val updated = page.copy(processedPath = processedFile.absolutePath)
            dao.upsertDraftPage(updated)
            val current = navFlow.value
            navFlow.value = current.copy(
                draftPages = current.draftPages.map { if (it.id == updated.id) updated else it },
                processedBitmap = filtered,
                scanBitmap = filtered,
                selectedFilter = filter,
                editVersion = current.editVersion + 1,
                busy = false,
            )
        }
    }

    fun saveDocument(title: String, quality: String) {
        val bitmap = navFlow.value.processedBitmap ?: navFlow.value.scanBitmap ?: return
        val stateAtSave = navFlow.value
        AppLogger.i("Document", "Save document title=$title quality=$quality bitmap=${bitmap.width}x${bitmap.height}")
        viewModelScope.launch {
            navFlow.value = navFlow.value.copy(busy = true)
            val saved = withContext(Dispatchers.IO) {
                val id = System.currentTimeMillis()
                val files = saveDirectory()
                val sessionBitmaps = stateAtSave.draftPages.mapNotNull { page ->
                    // Storage is image-only: pages that never passed the crop/filter step fall
                    // back to the unrotated original, so replay the persisted rotation there.
                    ImageProcessor.readBitmap(page.processedPath.ifBlank { page.originalPath }, 4096)
                        ?.let { if (page.processedPath.isBlank() && page.rotation != 0) ImageProcessor.rotateQuarters(it, page.rotation) else it }
                }.toMutableList()
                val pageBitmaps = sessionBitmaps.ifEmpty { mutableListOf(bitmap) }
                val imageFile = File(files, "$id-page.jpg")
                ImageProcessor.writeJpeg(pageBitmaps.first(), imageFile, quality)
                val export = File(files, "$id.jpg").also { ImageProcessor.writeJpeg(pageBitmaps.first(), it, quality) }
                val doc = Document(
                    id = id,
                    title = title.ifBlank { "Untitled Scan" },
                    type = "JPG",
                    createdAt = id,
                    sizeBytes = export.length(),
                    pageCount = pageBitmaps.size,
                    thumbnailPath = imageFile.absolutePath,
                    exportPath = export.absolutePath,
                    folderId = stateAtSave.currentFolderId,
                    scanMode = stateAtSave.scanMode.name,
                )
                dao.upsert(doc)
                pageBitmaps.forEachIndexed { index, pageBitmap ->
                    val pageFile = File(files, "$id-page-$index.jpg")
                    ImageProcessor.writeJpeg(pageBitmap, pageFile, quality)
                    val draft = stateAtSave.draftPages.getOrNull(index)
                    dao.upsertPage(
                        ScanPage(
                            id = id + index,
                            documentId = id,
                            originalPath = draft?.originalPath ?: pageFile.absolutePath,
                            processedPath = pageFile.absolutePath,
                            cropPoints = draft?.cropPoints ?: "auto",
                            filter = "Auto",
                            brightness = 0f,
                            contrast = 1f,
                            saturation = 1f,
                            rotation = draft?.rotation ?: 0,
                            pageIndex = index,
                            sourceType = stateAtSave.scanMode.name,
                            originalWidth = pageBitmap.width,
                            originalHeight = pageBitmap.height,
                        )
                    )
                }
                stateAtSave.scanSessionId?.let { sessionId ->
                    dao.deleteDraftPages(sessionId)
                    dao.deleteSession(sessionId)
                }
                AppLogger.i("Document", "Saved document id=$id export=${export.absolutePath} size=${export.length()}")
                doc
            }
            navFlow.value = navFlow.value.copy(
                screen = Screen.Detail,
                backStack = emptyList(),
                busy = false,
                selected = saved,
                tab = Tab.Docs,
                savedResultDetail = true,
                draftPages = emptyList(),
                scanSessionId = null,
            )
        }
    }

    fun openDocument(document: Document) {
        AppLogger.i("Document", "Open document id=${document.id} title=${document.title} type=${document.type}")
        val bitmap = ImageProcessor.readBitmap(document.thumbnailPath, 3072)
        go(Screen.Detail) {
            copy(
            selected = document,
            tab = Tab.Docs,
            scanBitmap = bitmap,
            processedBitmap = bitmap,
            savedResultDetail = false,
            )
        }
    }

    suspend fun loadDocumentPages(document: Document): List<Bitmap> = withContext(Dispatchers.IO) {
        if (document.type == "PDF") {
            ImageProcessor.renderPdfPages(document.exportPath, maxPages = 100)
        } else {
            val stored = dao.pages(document.id).mapNotNull { page -> ImageProcessor.readBitmap(page.processedPath.ifBlank { page.originalPath }, 3072) }
            stored.ifEmpty { listOfNotNull(ImageProcessor.readBitmap(document.exportPath, 3072) ?: ImageProcessor.readBitmap(document.thumbnailPath, 3072)) }
        }
    }

    fun shareSelected() {
        AppLogger.i("Share", "Open share for document ${navFlow.value.selected?.id}")
        go(Screen.Share)
    }

    /** Exports a saved image-only document to a user-chosen PDF file (no PDF stored in the library). */
    fun exportDocumentAsPdf(document: Document, uri: android.net.Uri) {
        viewModelScope.launch {
            val pages = loadDocumentPages(document)
            if (pages.isEmpty()) return@launch
            withContext(Dispatchers.IO) {
                val app = getApplication<Application>()
                val tmp = File(app.cacheDir, "export-${System.currentTimeMillis()}.pdf")
                runCatching { ImageProcessor.writePdf(pages, tmp) }
                    .onSuccess {
                        runCatching {
                            app.contentResolver.openOutputStream(uri)?.use { out -> tmp.inputStream().use { it.copyTo(out) } }
                        }
                    }
                tmp.delete()
            }
        }
    }

    fun renameSelected(title: String) {
        val doc = navFlow.value.selected ?: return
        AppLogger.i("Document", "Rename document id=${doc.id} title=$title")
        viewModelScope.launch {
            dao.rename(doc.id, title)
            navFlow.value = navFlow.value.copy(selected = doc.copy(title = title))
        }
    }

    fun deleteSelected() {
        val doc = navFlow.value.selected ?: return
        AppLogger.i("Document", "Delete document id=${doc.id} title=${doc.title}")
        viewModelScope.launch {
            dao.deleteDocument(doc.id)
            replace(Screen.Shell) { copy(selected = null, tab = Tab.Docs, backStack = emptyList()) }
        }
    }

    fun openSettings() {
        AppLogger.i("Settings", "Open settings")
        go(Screen.Settings) { copy(tab = Tab.Me) }
    }

    fun openAccount() {
        go(Screen.Account)
    }

    fun openAbout() {
        go(Screen.About)
    }

    fun openHelp() {
        go(Screen.Help)
    }

    fun openLegal(title: String) {
        go(Screen.Legal) { copy(legalTitle = title) }
    }

    fun openLogs() {
        AppLogger.i("Log", "Open app logs")
        go(Screen.AppLogs) { copy(logText = AppLogger.read()) }
    }

    fun refreshLogs() {
        navFlow.value = navFlow.value.copy(logText = AppLogger.read())
    }

    fun clearLogs() {
        AppLogger.clear()
        navFlow.value = navFlow.value.copy(logText = AppLogger.read(), captureMessage = tr(settingsFlow.value, "Logs cleared", "日志已清空"))
    }

    fun updateSettings(settings: AppSettings) {
        AppLogger.i("Settings", "Update settings language=${settings.language} theme=${settings.theme} path=${settings.defaultSavePath}")
        settingsFlow.value = settings
        saveSettings(settings)
        GitHubUpdateRepository.schedule(getApplication(), settings.autoCheckUpdates, settings.wifiOnlyUpdates)
    }

    fun checkForUpdates(downloadAutomatically: Boolean = false) {
        viewModelScope.launch {
            navFlow.value = navFlow.value.copy(checkingUpdate = true, captureMessage = null)
            val result = runCatching { GitHubUpdateRepository(getApplication()).checkLatest() }
            val info = result.getOrNull()
            navFlow.value = navFlow.value.copy(
                checkingUpdate = false,
                updateInfo = info,
                captureMessage = when {
                    result.isFailure -> if (isChineseUi(settingsFlow.value)) "检查更新失败：${result.exceptionOrNull()?.message}" else "Update check failed: ${result.exceptionOrNull()?.message}"
                    info == null -> if (isChineseUi(settingsFlow.value)) "当前已是最新版本" else "ClearScan is up to date"
                    else -> if (isChineseUi(settingsFlow.value)) "发现新版本 ${info.version}" else "Version ${info.version} is available"
                },
            )
            if (info != null && (downloadAutomatically || settingsFlow.value.autoDownloadUpdates)) downloadUpdate(info)
        }
    }

    fun downloadUpdate(requestedInfo: UpdateInfo? = navFlow.value.updateInfo) {
        val info = requestedInfo ?: return
        viewModelScope.launch {
            navFlow.value = navFlow.value.copy(updateDownload = UpdateDownloadState("downloading", total = info.sizeBytes))
            val result = runCatching {
                GitHubUpdateRepository(getApplication()).download(info) { downloaded, total ->
                    navFlow.value = navFlow.value.copy(updateDownload = UpdateDownloadState("downloading", downloaded, total))
                }
            }
            val file = result.getOrNull()
            navFlow.value = navFlow.value.copy(
                updateDownload = if (file != null) UpdateDownloadState("ready", file.length(), file.length(), file.absolutePath) else UpdateDownloadState("error", error = result.exceptionOrNull()?.message),
                captureMessage = result.exceptionOrNull()?.message,
            )
        }
    }

    fun installDownloadedUpdate() {
        val file = navFlow.value.updateDownload.filePath?.let(::File) ?: return
        runCatching { GitHubUpdateRepository(getApplication()).install(file) }
            .onFailure { navFlow.value = navFlow.value.copy(captureMessage = it.message) }
    }

    fun login(name: String, email: String) {
        AppLogger.i("Account", "Login/update account name=${name.ifBlank { "ClearScan User" }} email=$email")
        settingsFlow.value = settingsFlow.value.copy(
            loggedIn = true,
            accountName = name.ifBlank { "ClearScan User" },
            accountEmail = email.ifBlank { "user@clearscan.local" },
        )
        saveSettings(settingsFlow.value)
        navFlow.value = navFlow.value.copy(captureMessage = tr(settingsFlow.value, "Signed in", "登录成功"))
    }

    fun logout() {
        AppLogger.i("Account", "Logout")
        settingsFlow.value = settingsFlow.value.copy(loggedIn = false, accountName = "Guest", accountEmail = "")
        saveSettings(settingsFlow.value)
        navFlow.value = navFlow.value.copy(screen = Screen.Shell, tab = Tab.Me, selected = null, backStack = emptyList(), captureMessage = tr(settingsFlow.value, "Logged out", "已退出登录"))
    }

    fun setDocumentPassword(documentId: Long, password: String) {
        AppLogger.i("Security", if (password.isBlank()) "Remove password for document $documentId" else "Set password for document $documentId")
        val current = settingsFlow.value.passwordMap
        settingsFlow.value = settingsFlow.value.copy(passwordMap = if (password.isBlank()) current - documentId else current + (documentId to password))
        saveSettings(settingsFlow.value)
        navFlow.value = navFlow.value.copy(captureMessage = if (password.isBlank()) tr(settingsFlow.value, "Password removed", "密码已移除") else tr(settingsFlow.value, "Password set", "密码已设置"))
    }

    fun runTool(name: String) {
        AppLogger.i("Tool", "Run tool entry $name")
        when (name) {
            "ID Card Scan" -> startScanMode(ScanMode.IdCard)
            "QR Code Scan" -> openQrScanner()
            "Barcode Scan" -> startScanMode(ScanMode.Barcode)
            "Translate" -> openTranslate()
            else -> beginTool(name)
        }
    }

    fun openTranslate() {
        AppLogger.i("Translate", "Open translate")
        refreshTranslationModelState()
        go(Screen.Translate)
    }

    fun setTranslationInput(text: String) {
        val state = navFlow.value.translationState
        navFlow.value = navFlow.value.copy(translationState = state.copy(inputText = text, error = null))
    }

    fun setTranslationLanguages(source: String? = null, target: String? = null) {
        val state = navFlow.value.translationState
        navFlow.value = navFlow.value.copy(translationState = state.copy(sourceLang = source ?: state.sourceLang, targetLang = target ?: state.targetLang))
    }

    fun swapTranslationLanguages() {
        val state = navFlow.value.translationState
        val nextTarget = if (state.sourceLang == "Auto") detectTranslationLanguage(state.inputText) else state.sourceLang
        navFlow.value = navFlow.value.copy(
            translationState = state.copy(
                sourceLang = state.targetLang,
                targetLang = nextTarget,
                inputText = state.outputText.ifBlank { state.inputText },
                outputText = "",
                error = null,
            )
        )
    }

    fun clearTranslation() {
        val state = navFlow.value.translationState
        navFlow.value = navFlow.value.copy(translationState = state.copy(inputText = "", outputText = "", error = null))
    }

    fun translateText() {
        val state = navFlow.value.translationState
        val chinese = isChineseUi(settingsFlow.value)
        if (state.inputText.isBlank()) {
            AppLogger.w("Translate", "Translate requested with blank input")
            navFlow.value = navFlow.value.copy(translationState = state.copy(error = if (chinese) "请输入要翻译的文本。" else "Enter text to translate."))
            return
        }
        if (state.cloudApiKey.isBlank() || state.cloudBaseUrl.isBlank() || state.cloudModel.isBlank()) {
            navFlow.value = navFlow.value.copy(
                translationState = state.copy(
                    error = if (chinese) "请先填写云端 API 的地址、密钥和模型名称。" else "Fill in the cloud API base URL, key and model first.",
                )
            )
            return
        }
        viewModelScope.launch {
            AppLogger.i("Translate", "Translate start source=${state.sourceLang} target=${state.targetLang} chars=${state.inputText.length}")
            navFlow.value = navFlow.value.copy(
                translationState = state.copy(
                    isTranslating = true,
                    progress = .2f,
                    outputText = "",
                    error = null,
                )
            )
            val translated = runCatching {
                translateWithCloudApi(state.inputText, state.sourceLang, state.targetLang, state.cloudBaseUrl.trim(), state.cloudApiKey.trim(), state.cloudModel.trim())
            }.onFailure { AppLogger.e("Translate", "Translation failed", it) }.getOrElse { error ->
                navFlow.value = navFlow.value.copy(
                    translationState = navFlow.value.translationState.copy(
                        isTranslating = false,
                        progress = 0f,
                        error = if (chinese) "翻译失败：${error.message ?: "运行错误"}" else "Translation failed: ${error.message ?: "runtime error"}",
                    )
                )
                return@launch
            }
            navFlow.value = navFlow.value.copy(
                translationState = navFlow.value.translationState.copy(
                    isTranslating = false,
                    progress = 1f,
                    outputText = translated,
                    error = null,
                )
            )
            AppLogger.i("Translate", "Translate complete outputChars=${translated.length}")
        }
    }

    fun updateTranslationCloudConfig(baseUrl: String, apiKey: String, model: String) {
        prefs.edit()
            .putString("translationCloudBaseUrl", baseUrl.trim())
            .putString("translationCloudApiKey", apiKey.trim())
            .putString("translationCloudModel", model.trim())
            .apply()
        navFlow.value = navFlow.value.copy(
            translationState = navFlow.value.translationState.copy(
                cloudBaseUrl = baseUrl,
                cloudApiKey = apiKey,
                cloudModel = model,
            )
        )
    }

    /**
     * Translates via any OpenAI-compatible chat completions endpoint
     * (OpenAI, DeepSeek, Kimi, Qwen, OpenRouter, Groq, Ollama, ...).
     * Long inputs are split into chunks so each request stays small.
     */
    private suspend fun translateWithCloudApi(
        input: String,
        sourceLang: String,
        targetLang: String,
        baseUrl: String,
        apiKey: String,
        model: String,
    ): String = withContext(Dispatchers.IO) {
        require(sourceLang != targetLang || sourceLang == "Auto") { "Source and target languages must be different." }
        val chunks = splitTranslationText(input, maxChars = 2000)
        check(chunks.isNotEmpty()) { "Text is empty." }
        val endpoint = baseUrl.trimEnd('/') + "/chat/completions"
        val completed = StringBuilder()
        chunks.forEachIndexed { index, chunk ->
            val output = requestCloudTranslation(endpoint, apiKey, model, chunk, sourceLang, targetLang)
            if (completed.isNotEmpty()) completed.append('\n')
            completed.append(output.trim())
            navFlow.value = navFlow.value.copy(
                translationState = navFlow.value.translationState.copy(
                    progress = .2f + .75f * ((index + 1).toFloat() / chunks.size.toFloat()),
                )
            )
        }
        completed.toString().trim()
    }

    private fun requestCloudTranslation(
        endpoint: String,
        apiKey: String,
        model: String,
        text: String,
        sourceLang: String,
        targetLang: String,
    ): String {
        val sourceLabel = if (sourceLang == "Auto") "auto-detected language" else sourceLang
        val messages = JSONArray().apply {
            put(
                JSONObject().apply {
                    put("role", "system")
                    put("content", "You are a professional document translator. Translate the user's text from $sourceLabel into $targetLang. Output ONLY the translation, with no explanations, quotes, or extra formatting. Preserve paragraph breaks.")
                }
            )
            put(
                JSONObject().apply {
                    put("role", "user")
                    put("content", text)
                }
            )
        }
        val body = JSONObject().apply {
            put("model", model)
            put("messages", messages)
            put("temperature", 0.1)
            put("stream", false)
        }
        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 20_000
            readTimeout = 120_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Authorization", "Bearer $apiKey")
        }
        try {
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val payload = stream?.bufferedReader()?.use { it.readText() } ?: ""
            if (code !in 200..299) {
                val detail = runCatching { JSONObject(payload).optJSONObject("error")?.optString("message") }.getOrNull()
                error("HTTP $code${if (!detail.isNullOrBlank()) ": $detail" else if (payload.isNotBlank()) ": ${payload.take(200)}" else ""}")
            }
            val content = JSONObject(payload)
                .optJSONArray("choices")
                ?.optJSONObject(0)
                ?.optJSONObject("message")
                ?.optString("content")
            check(!content.isNullOrBlank()) { "Empty response from the API." }
            return content
        } finally {
            connection.disconnect()
        }
    }

    private fun refreshTranslationModelState() {
        navFlow.value = navFlow.value.copy(
            translationState = navFlow.value.translationState.copy(
                cloudBaseUrl = prefs.getString("translationCloudBaseUrl", "https://api.deepseek.com") ?: "https://api.deepseek.com",
                cloudModel = prefs.getString("translationCloudModel", "deepseek-chat") ?: "deepseek-chat",
                cloudApiKey = prefs.getString("translationCloudApiKey", "") ?: "",
            )
        )
    }

    fun beginTool(name: String) {
        AppLogger.i("Tool", "Begin tool selection $name")
        go(Screen.ToolSelect) {
            copy(activeTool = name, selectedToolIds = emptySet(), toolOption = defaultToolOption(name), captureMessage = null)
        }
    }

    fun toggleToolDocument(documentId: Long) {
        val state = navFlow.value
        val maxSelection = maxSelectionFor(state.activeTool)
        val current = state.selectedToolIds
        val next = if (documentId in current) {
            current - documentId
        } else if (current.size < maxSelection) {
            current + documentId
        } else {
            setOf(documentId)
        }
        navFlow.value = state.copy(selectedToolIds = next)
    }

    fun setToolOption(option: String) {
        navFlow.value = navFlow.value.copy(toolOption = option)
    }

    fun executeActiveTool() {
        val state = navFlow.value
        val tool = state.activeTool ?: return
        val selectedIds = state.selectedToolIds
        AppLogger.i("Tool", "Execute tool=$tool selected=${selectedIds.joinToString()} option=${state.toolOption}")
        if (selectedIds.size < minSelectionFor(tool)) {
            AppLogger.w("Tool", "Not enough selection for $tool: ${selectedIds.size}")
            navFlow.value = state.copy(captureMessage = selectionHint(tool, settingsFlow.value))
            return
        }
        if (tool == "Watermark" || tool == "Add Signature") {
            val document = allDocuments.firstOrNull { it.id in selectedIds }
            if (document != null) {
                replace(if (tool == "Watermark") Screen.WatermarkEditor else Screen.SignatureEditor) {
                    copy(selected = document, busy = false, captureMessage = null)
                }
            }
            return
        }
        viewModelScope.launch {
            val selected = allDocuments.filter { it.id in selectedIds }
            if (selected.size < minSelectionFor(tool)) {
                AppLogger.w("Tool", "Selected documents missing for $tool")
                navFlow.value = navFlow.value.copy(captureMessage = selectionHint(tool, settingsFlow.value))
                return@launch
            }
            navFlow.value = navFlow.value.copy(busy = true)
            val result = withContext(Dispatchers.IO) {
                runToolOperation(tool, selected, navFlow.value.toolOption)
            }
            if (result != null) {
                AppLogger.i("Tool", "$tool complete outputId=${result.id} path=${result.exportPath}")
                replace(Screen.Detail) {
                    copy(
                        busy = false,
                        selected = result,
                        tab = Tab.Docs,
                        activeTool = null,
                        selectedToolIds = emptySet(),
                        scanBitmap = ImageProcessor.readBitmap(result.thumbnailPath),
                        processedBitmap = ImageProcessor.readBitmap(result.thumbnailPath),
                        captureMessage = "${toolLabel(tool, settingsFlow.value)} ${if (isChineseUi(settingsFlow.value)) "已完成" else "complete"}",
                    )
                }
            } else {
                AppLogger.w("Tool", "$tool failed")
                navFlow.value = navFlow.value.copy(busy = false, captureMessage = if (isChineseUi(settingsFlow.value)) "${toolLabel(tool, settingsFlow.value)} 失败，请选择有效文件。" else "$tool failed. Please choose a valid file.")
            }
        }
    }

    fun applyWatermark(document: Document, options: WatermarkOptions) {
        viewModelScope.launch {
            navFlow.value = navFlow.value.copy(busy = true)
            val result = withContext(Dispatchers.IO) {
                val pages = loadDocumentPages(document)
                val output = pages.mapIndexed { index, bitmap -> if (index == 0 || options.applyAllPages) OverlayRenderer.watermark(bitmap, options) else bitmap }
                if (output.isEmpty()) null else writeDocumentFiles("${document.title} - Watermark", document.type, output.first(), "High", output.size, if (document.type == "PDF") output else null)
            }
            finishOverlayResult(result, "Watermark")
        }
    }

    fun applySignature(document: Document, options: SignatureOptions) {
        viewModelScope.launch {
            navFlow.value = navFlow.value.copy(busy = true)
            val result = withContext(Dispatchers.IO) {
                val pages = loadDocumentPages(document)
                val output = pages.mapIndexed { index, bitmap -> if (index == 0 || options.applyAllPages) OverlayRenderer.signature(bitmap, options) else bitmap }
                if (output.isEmpty()) null else writeDocumentFiles("${document.title} - Signed", document.type, output.first(), "High", output.size, if (document.type == "PDF") output else null)
            }
            finishOverlayResult(result, "Signature")
        }
    }

    private fun finishOverlayResult(result: Document?, label: String) {
        if (result == null) {
            navFlow.value = navFlow.value.copy(busy = false, captureMessage = tr(settingsFlow.value, "$label failed", "操作失败"))
        } else {
            replace(Screen.Detail) { copy(busy = false, selected = result, tab = Tab.Docs, activeTool = null, selectedToolIds = emptySet()) }
        }
    }

    private suspend fun runToolOperation(tool: String, selected: List<Document>, option: String): Document? {
        val first = selected.firstOrNull() ?: return null
        return when (tool) {
            "PDF Edit" -> first
            "PDF to Image" -> {
                val bitmap = ImageProcessor.renderPdfFirstPage(first.exportPath) ?: return null
                writeDocumentFiles("Image from ${first.title}", "JPG", bitmap, "High")
            }
            "Image to PDF" -> {
                val bitmap = ImageProcessor.readBitmap(first.exportPath) ?: ImageProcessor.readBitmap(first.thumbnailPath) ?: return null
                writeDocumentFiles("${first.title} PDF", "PDF", bitmap, "High")
            }
            "Image Format Converter" -> {
                val bitmap = ImageProcessor.readBitmap(first.exportPath) ?: ImageProcessor.readBitmap(first.thumbnailPath) ?: return null
                val targetType = when (option.uppercase()) {
                    "JPG", "JPEG" -> "JPEG"
                    "WEBP" -> "WEBP"
                    "BMP" -> "BMP"
                    "PDF" -> "PDF"
                    else -> "PNG"
                }
                writeDocumentFiles("${first.title} - $targetType", targetType, bitmap, "High")
            }
            "Merge PDF" -> {
                val pages = selected.flatMap { doc -> ImageProcessor.documentPages(doc) }
                if (pages.isEmpty()) return null
                writeDocumentFiles("Merged PDF", "PDF", pages.first(), "Medium", pageCount = pages.size, pdfPages = pages)
            }
            "Split PDF" -> {
                val pages = ImageProcessor.renderPdfPages(first.exportPath, maxPages = if (option == "First page") 1 else Int.MAX_VALUE)
                if (pages.isEmpty()) return null
                writeDocumentFiles("${first.title} - Split", "PDF", pages.first(), "Medium", pageCount = pages.size, pdfPages = pages)
            }
            "Compress PDF" -> {
                val pages = ImageProcessor.renderPdfPages(first.exportPath).ifEmpty { return null }
                val quality = when (option) {
                    "Low" -> "Low"
                    "High" -> "High"
                    else -> "Medium"
                }
                val compressed = pages.map { ImageProcessor.downsampleForPdf(it, option) }
                writeDocumentFiles("${first.title} - Compressed", "PDF", compressed.first(), quality, pageCount = compressed.size, pdfPages = compressed)
            }
            "Watermark" -> {
                val pages = ImageProcessor.documentPages(first).ifEmpty { return null }
                val watermarked = pages.map { ImageProcessor.watermark(it, "ClearScan") }
                writeDocumentFiles("${first.title} - Watermark", first.type, watermarked.first(), "High", pageCount = watermarked.size, pdfPages = if (first.type == "PDF") watermarked else null)
            }
            "Add Signature" -> {
                val pages = ImageProcessor.documentPages(first).ifEmpty { return null }
                val signed = pages.mapIndexed { index, bitmap -> if (index == 0) ImageProcessor.addSignature(bitmap) else bitmap }
                writeDocumentFiles("${first.title} - Signed", first.type, signed.first(), "High", pageCount = signed.size, pdfPages = if (first.type == "PDF") signed else null)
            }
            else -> null
        }
    }

    private suspend fun writeDocumentFiles(
        title: String,
        type: String,
        bitmap: Bitmap,
        quality: String,
        pageCount: Int = 1,
        pdfPages: List<Bitmap>? = null,
    ): Document {
        val id = System.currentTimeMillis()
        val files = saveDirectory()
        val imageFile = File(files, "$id-page.jpg")
        ImageProcessor.writeJpeg(bitmap, imageFile, quality)
        val normalizedType = when (type.uppercase()) {
            "JPG" -> "JPEG"
            else -> type.uppercase()
        }
        val export = when (normalizedType) {
            "PDF" -> File(files, "$id.pdf").also { ImageProcessor.writePdf(pdfPages ?: listOf(bitmap), it) }
            "PNG" -> File(files, "$id.png").also { ImageProcessor.writePng(bitmap, it) }
            "WEBP" -> File(files, "$id.webp").also { ImageProcessor.writeWebp(bitmap, it) }
            "BMP" -> File(files, "$id.bmp").also { ImageProcessor.writeBmp(bitmap, it) }
            else -> File(files, "$id.jpg").also { ImageProcessor.writeJpeg(bitmap, it, quality) }
        }
        val doc = Document(id, title, normalizedType, id, export.length(), pageCount, imageFile.absolutePath, export.absolutePath)
        dao.upsert(doc)
        dao.upsertPage(ScanPage(id, id, imageFile.absolutePath, imageFile.absolutePath, "auto", "Auto", 0f, 1f, 1f, 0))
        return doc
    }

    private fun saveDirectory(): File {
        val app = getApplication<Application>()
        return if (settingsFlow.value.defaultSavePath == "Documents") {
            app.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: app.filesDir
        } else {
            app.filesDir
        }
    }

    private fun loadSettings(): AppSettings {
        val passwords = prefs.getString("passwords", "").orEmpty()
            .split("|")
            .mapNotNull { item ->
                val parts = item.split(":", limit = 2)
                parts.firstOrNull()?.toLongOrNull()?.let { id -> id to parts.getOrElse(1) { "" } }
            }
            .filter { it.second.isNotBlank() }
            .toMap()
        return AppSettings(
            language = prefs.getString("language", "Auto") ?: "Auto",
            theme = prefs.getString("theme", "System") ?: "System",
            loggedIn = prefs.getBoolean("loggedIn", false),
            accountName = prefs.getString("accountName", "Guest") ?: "Guest",
            accountEmail = prefs.getString("accountEmail", "") ?: "",
            passwordMap = passwords,
            defaultSavePath = prefs.getString("defaultSavePath", "Internal Storage") ?: "Internal Storage",
            // Sanitize: a stored default may name a filter that no longer exists.
            defaultFilter = (prefs.getString("defaultFilter", "B&W") ?: "B&W").takeIf { it in DocumentFilters } ?: "B&W",
            autoCheckUpdates = prefs.getBoolean("autoCheckUpdates", true),
            autoDownloadUpdates = prefs.getBoolean("autoDownloadUpdates", true),
            wifiOnlyUpdates = prefs.getBoolean("wifiOnlyUpdates", true),
            cameraGrid = prefs.getBoolean("cameraGrid", false),
            cameraEnhance = prefs.getBoolean("cameraEnhance", true),
            cameraResolution = prefs.getString("cameraResolution", "High") ?: "High",
        )
    }

    private fun saveSettings(settings: AppSettings) {
        prefs.edit()
            .putString("language", settings.language)
            .putString("theme", settings.theme)
            .putBoolean("loggedIn", settings.loggedIn)
            .putString("accountName", settings.accountName)
            .putString("accountEmail", settings.accountEmail)
            .putString("defaultSavePath", settings.defaultSavePath)
            .putString("defaultFilter", settings.defaultFilter)
            .putBoolean("autoCheckUpdates", settings.autoCheckUpdates)
            .putBoolean("autoDownloadUpdates", settings.autoDownloadUpdates)
            .putBoolean("wifiOnlyUpdates", settings.wifiOnlyUpdates)
            .putBoolean("cameraGrid", settings.cameraGrid)
            .putBoolean("cameraEnhance", settings.cameraEnhance)
            .putString("cameraResolution", settings.cameraResolution)
            .putString("passwords", settings.passwordMap.entries.joinToString("|") { "${it.key}:${it.value}" })
            .apply()
        viewModelScope.launch(Dispatchers.IO) { settingsRepository.save(settings) }
    }

    private suspend fun seedIfEmpty() {
        if (ui.value.documents.isNotEmpty()) return
        val context = getApplication<Application>()
        val names = listOf("Contract Agreement" to "PDF", "Lecture Notes" to "PDF", "Invoice_0528" to "PDF", "ID Card" to "JPG", "Book Summary" to "PDF", "Whiteboard Notes" to "JPG")
        names.forEachIndexed { index, pair ->
            val id = System.currentTimeMillis() - index * 86_400_000L
            val bitmap = ImageProcessor.sampleDocumentBitmap(pair.first)
            val thumb = File(context.filesDir, "$id-seed.jpg")
            ImageProcessor.writeJpeg(bitmap, thumb, "Medium")
            val export = File(context.filesDir, if (pair.second == "PDF") "$id-seed.pdf" else "$id-seed-out.jpg")
            if (pair.second == "PDF") ImageProcessor.writePdf(bitmap, export) else ImageProcessor.writeJpeg(bitmap, export, "Medium")
            dao.upsert(Document(id, pair.first, pair.second, id, export.length(), 1, thumb.absolutePath, export.absolutePath))
        }
    }
}

@Composable
fun ClearScanTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = androidx.compose.material3.lightColorScheme(
            primary = Teal,
            secondary = TealDark,
            background = ComposeColor.White,
            surface = ComposeColor.White,
            onSurface = TextDark,
        ),
        content = content,
    )
}

@Composable
fun ClearScanApp(model: ClearScanViewModel) {
    val state by model.ui.collectAsState()
    val context = LocalContext.current
    val density = LocalDensity.current
    BackHandler(enabled = true) {
        model.back()
    }
    LaunchedEffect(state.captureMessage) {
        state.captureMessage?.let { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
    }
    CompositionLocalProvider(
        LocalDensity provides Density(density = density.density * 0.88f, fontScale = density.fontScale * 0.9f)
    ) {
        val colors = if (isDarkTheme(state.settings)) {
            androidx.compose.material3.darkColorScheme(primary = Teal, secondary = TealDark, background = ComposeColor(0xFF111317), surface = ComposeColor(0xFF181B20), onSurface = ComposeColor(0xFFF4F6F8))
        } else {
            androidx.compose.material3.lightColorScheme(primary = Teal, secondary = TealDark, background = ComposeColor.White, surface = ComposeColor.White, onSurface = TextDark)
        }
        MaterialTheme(colorScheme = colors) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            EdgeSwipeBackBox(onBack = model::back) {
                AnimatedContent(targetState = state.screen, label = "screen") { screen ->
                    when (screen) {
                        Screen.Shell -> ShellScreen(state, model)
                        Screen.Camera -> CameraScreen(state, model)
                        Screen.Crop -> CropScreen(state, model)
                        Screen.Edit -> EditScreen(state, model)
                        Screen.Filter -> FilterScreen(state, model)
                        Screen.Adjust -> AdjustScreen(state, model)
                        Screen.Save -> SaveScreen(state, model)
                        Screen.Detail -> DetailScreen(state, model)
                        Screen.Share -> ShareScreen(state, model)
                        Screen.ToolSelect -> ToolSelectScreen(state, model)
                        Screen.WatermarkEditor -> WatermarkEditorScreen(state, model)
                        Screen.SignatureEditor -> SignatureEditorScreen(state, model)
                        Screen.Translate -> TranslateScreen(state, model)
                        Screen.Settings -> SettingsScreen(state, model)
                        Screen.Account -> AccountScreen(state, model)
                        Screen.Help -> HelpScreen(state, model)
                        Screen.About -> AboutScreen(state, model)
                        Screen.Legal -> LegalScreen(state, model)
                        Screen.AppLogs -> AppLogsScreen(state, model)
                    }
                }
            }
        }
        }
    }
}

@Composable
fun EdgeSwipeBackBox(onBack: () -> Unit, content: @Composable () -> Unit) {
    var edgeDrag by remember { mutableStateOf(false) }
    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { start -> edgeDrag = start.x < 56f },
                    onDragEnd = { edgeDrag = false },
                    onDragCancel = { edgeDrag = false },
                    onDrag = { change, drag ->
                        if (edgeDrag && drag.x > 78f && abs(drag.y) < 64f) {
                            change.consume()
                            edgeDrag = false
                            onBack()
                        }
                    },
                )
            }
    ) {
        content()
    }
}

@Composable
fun ShellScreen(state: UiState, model: ClearScanViewModel) {
    Scaffold(
        bottomBar = { BottomNav(state.settings, state.tab, model::selectTab, model::openCamera) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (state.tab) {
                Tab.Home -> HomeScreen(state, model)
                Tab.Docs -> DocsScreen(state, model)
                Tab.Camera -> CameraScreen(state, model)
                Tab.Tools -> ToolsScreen(state, model)
                Tab.Me -> MeScreen(state, model)
            }
        }
    }
}

@Composable
fun BottomNav(settings: AppSettings, current: Tab, onTab: (Tab) -> Unit, onCamera: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(92.dp)
            .background(MaterialTheme.colorScheme.surface)
            .navigationBarsPadding()
    ) {
        Row(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(76.dp)
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            listOf(Tab.Home, Tab.Docs).forEach { NavItem(settings, it, current == it) { onTab(it) } }
            Spacer(Modifier.width(72.dp))
            listOf(Tab.Tools, Tab.Me).forEach { NavItem(settings, it, current == it) { onTab(it) } }
        }
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .size(72.dp)
                .clip(CircleShape)
                .background(Brush.verticalGradient(listOf(Teal, TealDark)))
                .clickable { onCamera() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.CameraAlt, null, tint = ComposeColor.White, modifier = Modifier.size(36.dp))
        }
    }
}

@Composable
fun NavItem(settings: AppSettings, tab: Tab, selected: Boolean, onClick: () -> Unit) {
    val label = tabLabel(settings, tab)
    Column(
        Modifier.width(56.dp).clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(tab.icon, label, tint = if (selected) Teal else Muted, modifier = Modifier.size(27.dp))
        Text(label, color = if (selected) Teal else Muted, fontSize = 12.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
    }
}

@Composable
fun HomeScreen(state: UiState, model: ClearScanViewModel) {
    val settings = state.settings
    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 112.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("ClearScan", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
                IconButton(onClick = model::openSettings) { Icon(Icons.Default.Settings, null, modifier = Modifier.size(28.dp)) }
            }
        }
        item { HeroCard(settings) }
        if (state.draftPages.isNotEmpty()) item {
            OutlinedButton(model::resumeScanSession, Modifier.fillMaxWidth().height(48.dp)) {
                Text(tr(settings, "Resume ${state.draftPages.size}-page scan", "继续 ${state.draftPages.size} 页扫描"), fontWeight = FontWeight.Bold)
            }
        }
        item {
            Button(
                onClick = model::openCamera,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Teal),
                shape = RoundedCornerShape(12.dp),
            ) {
                Icon(Icons.Default.CameraAlt, null, Modifier.size(26.dp))
                Spacer(Modifier.width(12.dp))
                Text(tr(settings, "Scan Document", "扫描文档"), fontSize = 19.sp, fontWeight = FontWeight.Bold)
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                QuickAction(tr(settings, "Import Images", "导入图片"), Icons.Default.Image, Modifier.weight(1f)) { model.openCamera() }
                QuickAction(tr(settings, "ID Card Scan", "证件扫描"), Icons.Outlined.Badge, Modifier.weight(1f)) { model.startScanMode(ScanMode.IdCard) }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(tr(settings, "Recent", "最近文档"), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                TextButton(onClick = { model.selectTab(Tab.Docs) }) { Text(tr(settings, "View All  ›", "查看全部  ›"), color = Muted) }
            }
        }
        item {
            DocumentListCard(state.documents.take(3), model)
        }
    }
}

@Composable
fun HeroCard(settings: AppSettings) {
    val dark = isDarkTheme(settings)
    Card(
        Modifier.fillMaxWidth().height(150.dp),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = if (dark) ComposeColor(0xFF162321) else ComposeColor(0xFFE9FAF7)),
        elevation = CardDefaults.cardElevation(0.dp),
    ) {
        Row(Modifier.fillMaxSize().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(tr(settings, "Go Paperless,\nBe Productive.", "告别纸张，\n高效办公。"), Modifier.weight(1f), fontSize = 23.sp, lineHeight = 31.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onSurface)
            ScannerIllustration(Modifier.size(110.dp), dark)
        }
    }
}

@Composable
fun ScannerIllustration(modifier: Modifier = Modifier, dark: Boolean = false) {
    Canvas(modifier) {
        drawRoundRect(ComposeColor(0xFF23B7AE), topLeft = Offset(size.width * .35f, size.height * .05f), size = Size(size.width * .48f, size.height * .72f), cornerRadius = CornerRadius(10f, 10f))
        drawRoundRect(if (dark) ComposeColor(0xFF232A32) else ComposeColor.White, topLeft = Offset(size.width * .18f, size.height * .18f), size = Size(size.width * .48f, size.height * .64f), cornerRadius = CornerRadius(8f, 8f))
        repeat(5) { y ->
            drawRoundRect(if (dark) ComposeColor(0xFF6A7A89) else ComposeColor(0xFFB6C7D4), topLeft = Offset(size.width * .27f, size.height * (.30f + y * .09f)), size = Size(size.width * .26f, 5f), cornerRadius = CornerRadius(3f, 3f))
        }
        drawCircle(ComposeColor(0xFFFFB39F), radius = size.width * .13f, center = Offset(size.width * .16f, size.height * .64f))
        drawCircle(ComposeColor(0xFFFFB39F), radius = size.width * .13f, center = Offset(size.width * .82f, size.height * .65f))
    }
}

@Composable
fun QuickAction(label: String, icon: ImageVector, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Card(
        modifier.height(104.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(0.dp),
    ) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(icon, label, tint = Teal, modifier = Modifier.size(34.dp))
            Spacer(Modifier.height(10.dp))
            Text(label, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
fun DocumentListCard(documents: List<Document>, model: ClearScanViewModel) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(1.dp),
    ) {
        Column {
            documents.forEach { DocumentRow(it, model) }
            if (documents.isEmpty()) EmptyState("No scans yet", "Tap the camera button to create your first document.")
        }
    }
}

@Composable
fun DocumentRow(document: Document, model: ClearScanViewModel) {
    Row(
        Modifier.fillMaxWidth().clickable { model.openDocument(document) }.padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Thumbnail(document.thumbnailPath, Modifier.size(58.dp, 74.dp))
        Spacer(Modifier.width(18.dp))
        Column(Modifier.weight(1f)) {
            Text(document.title, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(8.dp))
            Text(formatDate(document.createdAt), color = Muted, fontSize = 15.sp)
        }
        Text(document.type, color = if (document.type == "PDF") ComposeColor(0xFFFF6258) else ComposeColor(0xFF36B36A), modifier = Modifier.border(1.dp, if (document.type == "PDF") ComposeColor(0xFFFF6258) else ComposeColor(0xFF36B36A), RoundedCornerShape(5.dp)).padding(horizontal = 8.dp, vertical = 4.dp), fontWeight = FontWeight.Bold)
    }
}

@Composable
fun Thumbnail(path: String, modifier: Modifier = Modifier) {
    // Keyed on lastModified so rewritten files (e.g. rotated crop thumbnails) reload.
    val bitmap = remember(path, File(path).lastModified()) { ImageProcessor.readBitmap(path, 256) }
    if (bitmap != null) {
        Image(bitmap.asImageBitmap(), null, modifier.clip(RoundedCornerShape(5.dp)).background(Soft), contentScale = ContentScale.Crop)
    } else {
        Box(modifier.clip(RoundedCornerShape(5.dp)).background(Soft), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Description, null, tint = Muted)
        }
    }
}

@Composable
fun DocsScreen(state: UiState, model: ClearScanViewModel) {
    var newFolderOpen by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("") }
    var folderAction by remember { mutableStateOf<FolderEntity?>(null) }
    var folderActionName by remember { mutableStateOf("") }
    val settings = state.settings
    val currentFolder = state.folders.firstOrNull { it.id == state.currentFolderId }
    val childFolders = state.folders.filter { it.parentId == state.currentFolderId }
    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 122.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(currentFolder?.name ?: tr(settings, "My Docs", "我的文档"), fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
                Row {
                    IconButton(onClick = { newFolderOpen = true }) { Icon(Icons.Default.CreateNewFolder, null) }
                    if (currentFolder != null) IconButton(onClick = { model.openFolder(currentFolder.parentId) }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) }
                }
            }
        }
        if (currentFolder != null) item {
            Text(
                tr(settings, "My Docs / ${folderBreadcrumb(state.folders, currentFolder.id)}", "我的文档 / ${folderBreadcrumb(state.folders, currentFolder.id)}"),
                color = Muted,
                fontSize = 13.sp,
            )
        }
        item {
            OutlinedTextField(
                value = state.query,
                onValueChange = model::setQuery,
                placeholder = { Text(tr(settings, "Search all documents", "搜索全部文档")) },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                singleLine = true,
            )
        }
        items(childFolders, key = { "folder-${it.id}" }) { folder ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { model.openFolder(folder.id) }.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.Folder, null, tint = Teal, modifier = Modifier.size(34.dp))
                Spacer(Modifier.width(14.dp))
                Text(folder.name, Modifier.weight(1f), fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                IconButton(onClick = { folderAction = folder; folderActionName = folder.name }) { Icon(Icons.Default.MoreVert, null, tint = Muted) }
            }
        }
        items(state.documents, key = { it.id }) { doc -> DocumentRow(doc, model) }
    }
    Box(Modifier.fillMaxSize().padding(bottom = 26.dp, end = 26.dp), contentAlignment = Alignment.BottomEnd) {
        Box(Modifier.size(72.dp).clip(CircleShape).background(Teal).clickable { model.openCamera() }, contentAlignment = Alignment.Center) {
            Icon(Icons.Default.CameraAlt, null, tint = ComposeColor.White, modifier = Modifier.size(34.dp))
        }
    }
    if (newFolderOpen) AlertDialog(
        onDismissRequest = { newFolderOpen = false },
        title = { Text(tr(settings, "New folder", "新建文件夹")) },
        text = { OutlinedTextField(newFolderName, { newFolderName = it }, singleLine = true, label = { Text(tr(settings, "Folder name", "文件夹名称")) }) },
        confirmButton = { TextButton(onClick = { model.createFolder(newFolderName); newFolderOpen = false; newFolderName = "" }) { Text(tr(settings, "Create", "创建")) } },
        dismissButton = { TextButton(onClick = { newFolderOpen = false }) { Text(tr(settings, "Cancel", "取消")) } },
    )
    folderAction?.let { folder ->
        AlertDialog(
            onDismissRequest = { folderAction = null },
            title = { Text(tr(settings, "Folder options", "文件夹选项")) },
            text = { OutlinedTextField(folderActionName, { folderActionName = it }, singleLine = true, label = { Text(tr(settings, "Folder name", "文件夹名称")) }) },
            confirmButton = { TextButton(onClick = { model.renameFolder(folder, folderActionName); folderAction = null }) { Text(tr(settings, "Rename", "重命名")) } },
            dismissButton = {
                Row {
                    TextButton(onClick = { model.deleteFolder(folder); folderAction = null }) { Text(tr(settings, "Delete", "删除"), color = ComposeColor(0xFFE53935)) }
                    TextButton(onClick = { folderAction = null }) { Text(tr(settings, "Cancel", "取消")) }
                }
            },
        )
    }
}

fun folderBreadcrumb(folders: List<FolderEntity>, folderId: Long?): String {
    val byId = folders.associateBy { it.id }
    val names = mutableListOf<String>()
    val visited = mutableSetOf<Long>()
    var current = folderId
    while (current != null && visited.add(current)) {
        val folder = byId[current] ?: break
        names += folder.name
        current = folder.parentId
    }
    return names.asReversed().joinToString(" / ")
}

fun canMoveFolder(folders: List<FolderEntity>, folderId: Long, targetParentId: Long?): Boolean {
    if (targetParentId == null) return true
    if (folderId == targetParentId) return false
    val byId = folders.associateBy { it.id }
    val visited = mutableSetOf<Long>()
    var current: Long? = targetParentId
    while (current != null && visited.add(current)) {
        if (current == folderId) return false
        current = byId[current]?.parentId
    }
    return true
}

@Composable
fun CameraScreen(state: UiState, model: ClearScanViewModel) {
    val context = LocalContext.current
    val settings = state.settings
    val imageCapture = remember(settings.cameraResolution) {
        // 4:3 matches the native sensor mode on most devices, which keeps the full
        // sensor width in play; capture mode decides between full-resolution stills
        // (High/MAXIMIZE_QUALITY) and low-latency preview-matched stills (Balanced).
        val resolutionSelector = ResolutionSelector.Builder()
            .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
            .build()
        ImageCapture.Builder()
            .setResolutionSelector(resolutionSelector)
            .setCaptureMode(if (settings.cameraResolution == "High") ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY else ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()
    }
    var boundCamera by remember { mutableStateOf<Camera?>(null) }
    var lensFacing by remember { mutableStateOf(CameraSelector.LENS_FACING_BACK) }
    var flashMode by remember { mutableStateOf(ImageCapture.FLASH_MODE_AUTO) }
    var settingsOpen by remember { mutableStateOf(false) }
    var pendingMode by remember { mutableStateOf<ScanMode?>(null) }
    val analyzer = remember(state.scanMode) {
        when (state.scanMode) {
            ScanMode.QrCode, ScanMode.Barcode -> BarcodeAnalyzer(state.scanMode, model::onCodeDetected)
            ScanMode.Document, ScanMode.Book, ScanMode.IdCard -> DocumentFrameAnalyzer(detectionProfileFor(state.scanMode), model::onLiveDocumentFrame)
        }
    }
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    DisposableEffect(analyzer) { onDispose { (analyzer as? BarcodeAnalyzer)?.close() } }
    DisposableEffect(Unit) { onDispose { analysisExecutor.shutdownNow() } }
    imageCapture.flashMode = flashMode
    var hasCameraPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris != null) model.importBitmaps(uris, context)
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasCameraPermission = granted
        if (!granted) Toast.makeText(context, tr(settings, "Camera permission is needed to scan.", "扫描需要相机权限"), Toast.LENGTH_SHORT).show()
    }
    Column(Modifier.fillMaxSize().background(ComposeColor.Black).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 14.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = model::back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = ComposeColor.White) }
            IconButton(
                enabled = boundCamera?.cameraInfo?.hasFlashUnit() == true,
                onClick = {
                    flashMode = when (flashMode) {
                        ImageCapture.FLASH_MODE_AUTO -> ImageCapture.FLASH_MODE_ON
                        ImageCapture.FLASH_MODE_ON -> ImageCapture.FLASH_MODE_OFF
                        else -> ImageCapture.FLASH_MODE_AUTO
                    }
                },
            ) {
                Icon(
                    when (flashMode) {
                        ImageCapture.FLASH_MODE_ON -> Icons.Default.FlashOn
                        ImageCapture.FLASH_MODE_OFF -> Icons.Default.FlashOff
                        else -> Icons.Default.FlashAuto
                    },
                    null,
                    tint = if (boundCamera?.cameraInfo?.hasFlashUnit() == true) ComposeColor.White else Muted,
                )
            }
            IconButton(onClick = { lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK }) {
                Icon(Icons.Default.Cameraswitch, null, tint = ComposeColor.White)
            }
            IconButton(onClick = { settingsOpen = true }) { Icon(Icons.Default.Settings, null, tint = ComposeColor.White) }
        }
        Box(Modifier.weight(1f).fillMaxWidth().background(ComposeColor(0xFF6E4E32)), contentAlignment = Alignment.Center) {
            if (hasCameraPermission) {
                key(lensFacing, state.scanMode) {
                    CameraPreview(
                        imageCapture = imageCapture,
                        lensFacing = lensFacing,
                        analyzer = analyzer,
                        analysisExecutor = analysisExecutor,
                        settings = settings,
                        onCameraBound = { boundCamera = it },
                        onCameraError = { if (lensFacing != CameraSelector.LENS_FACING_BACK) lensFacing = CameraSelector.LENS_FACING_BACK },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                if (state.settings.cameraGrid) Canvas(Modifier.fillMaxSize()) {
                    val color = ComposeColor.White.copy(alpha = .42f)
                    drawLine(color, Offset(size.width / 3f, 0f), Offset(size.width / 3f, size.height), 1.5f)
                    drawLine(color, Offset(size.width * 2f / 3f, 0f), Offset(size.width * 2f / 3f, size.height), 1.5f)
                    drawLine(color, Offset(0f, size.height / 3f), Offset(size.width, size.height / 3f), 1.5f)
                    drawLine(color, Offset(0f, size.height * 2f / 3f), Offset(size.width, size.height * 2f / 3f), 1.5f)
                }
                if (state.scanMode in listOf(ScanMode.Document, ScanMode.Book, ScanMode.IdCard)) {
                    LiveDocumentGuide(state.liveDocumentFrame, Modifier.fillMaxSize())
                }
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    DocumentOnTable()
                    Spacer(Modifier.height(16.dp))
                    Text(tr(settings, "Allow camera access to scan real documents", "允许相机权限后即可扫描真实文档"), color = ComposeColor.White, fontSize = 15.sp)
                }
            }
            if (state.captureMessage != null) {
                Text(state.captureMessage, color = ComposeColor.White, textAlign = TextAlign.Center, modifier = Modifier.align(Alignment.TopCenter).padding(top = 12.dp).clip(RoundedCornerShape(8.dp)).background(ComposeColor(0x99000000)).padding(horizontal = 14.dp, vertical = 9.dp), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
            state.codeResult?.let { result ->
                Card(
                    Modifier.align(Alignment.BottomCenter).padding(18.dp).fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = ComposeColor(0xEE15191E)),
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(if (state.scanMode == ScanMode.QrCode) tr(settings, "QR code found", "发现二维码") else tr(settings, "Barcode found", "发现条形码"), color = ComposeColor.White, fontWeight = FontWeight.Bold)
                        Text(result.rawValue, color = ComposeColor.White, maxLines = 3, overflow = TextOverflow.Ellipsis)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { copyText(context, result.rawValue); model.clearCodeResult() }) { Text(tr(settings, "Copy", "复制")) }
                            if (result.isWebUrl) TextButton(onClick = { openSafeUrl(context, result.rawValue, settings) }) { Text(tr(settings, "Open link", "打开链接")) }
                            if (state.scanMode == ScanMode.Barcode) TextButton(onClick = { searchBarcode(context, result.rawValue) }) { Text(tr(settings, "Search", "搜索")) }
                            TextButton(onClick = model::clearCodeResult) { Text(tr(settings, "Scan again", "继续扫描")) }
                        }
                    }
                }
            }
        }
        Column(Modifier.fillMaxWidth().background(ComposeColor.Black).navigationBarsPadding().padding(horizontal = 14.dp, vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                listOf(
                    ScanMode.IdCard to tr(settings, "ID", "证件"),
                    ScanMode.Document to tr(settings, "Document", "文档"),
                    ScanMode.Book to tr(settings, "Book", "书籍"),
                    ScanMode.QrCode to tr(settings, "QR", "二维码"),
                    ScanMode.Barcode to tr(settings, "Data", "条码"),
                ).forEach {
                    val selected = it.first == state.scanMode
                    Text(
                        it.second,
                        Modifier.clip(RoundedCornerShape(6.dp)).background(if (selected) Teal.copy(alpha = .18f) else ComposeColor.Transparent).clickable {
                            if (state.draftPages.isNotEmpty() && it.first != state.scanMode) pendingMode = it.first else model.changeScanMode(it.first)
                        }.padding(horizontal = 10.dp, vertical = 7.dp),
                        color = if (selected) Teal else ComposeColor.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            if (state.scanMode == ScanMode.Document) {
                Spacer(Modifier.height(7.dp))
                Row(Modifier.clip(RoundedCornerShape(7.dp)).background(ComposeColor(0xFF1C1C1C)).padding(3.dp)) {
                    listOf(
                        DocumentCaptureMode.Single to tr(settings, "Single page", "单页"),
                        DocumentCaptureMode.Multi to tr(settings, "Multiple pages", "多页"),
                    ).forEach { (mode, label) ->
                        val selected = mode == state.documentCaptureMode
                        Text(
                            label,
                            Modifier.clip(RoundedCornerShape(5.dp)).background(if (selected) Teal else ComposeColor.Transparent).clickable { model.changeDocumentCaptureMode(mode) }.padding(horizontal = 18.dp, vertical = 6.dp),
                            color = ComposeColor.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround, verticalAlignment = Alignment.CenterVertically) {
                CameraSmallButton(Icons.Default.PhotoLibrary) { pickImage.launch("image/*") }
                Box(Modifier.size(78.dp).clip(CircleShape).background(ComposeColor.White).border(5.dp, Teal, CircleShape).clickable {
                    if (hasCameraPermission) takeRealPhoto(context, imageCapture, model, settings) else permission.launch(Manifest.permission.CAMERA)
                }, contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.CameraAlt, null, tint = Teal, modifier = Modifier.size(34.dp))
                }
                CameraSmallButton(if (state.draftPages.isNotEmpty()) Icons.Default.Check else Icons.Default.DocumentScanner) {
                    if (state.draftPages.isNotEmpty()) model.finishScanSession()
                    else if (hasCameraPermission) takeRealPhoto(context, imageCapture, model, settings) else permission.launch(Manifest.permission.CAMERA)
                }
            }
            if (state.draftPages.isNotEmpty()) {
                Text(
                    tr(settings, "${state.draftPages.size} pages • tap the right button when finished", "已拍摄 ${state.draftPages.size} 页 • 完成后点击右侧按钮"),
                    color = ComposeColor.White,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
    if (settingsOpen) AlertDialog(
        onDismissRequest = { settingsOpen = false },
        title = { Text(tr(settings, "Camera settings", "相机设置")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                SettingToggle(tr(settings, "Composition grid", "构图网格"), settings.cameraGrid) { model.updateSettings(settings.copy(cameraGrid = it)) }
                SettingToggle(tr(settings, "Automatic enhancement", "自动增强"), settings.cameraEnhance) { model.updateSettings(settings.copy(cameraEnhance = it)) }
                Text(tr(settings, "Resolution: ${settings.cameraResolution}", "分辨率：${if (settings.cameraResolution == "Balanced") "均衡" else settings.cameraResolution}"))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Balanced", "High").forEach { option ->
                        OutlinedButton({ model.updateSettings(settings.copy(cameraResolution = option)) }, enabled = settings.cameraResolution != option) {
                            Text(if (option == "High") tr(settings, "High", "高画质") else tr(settings, "Balanced", "均衡"))
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { settingsOpen = false }) { Text(tr(settings, "Done", "完成")) } },
    )
    pendingMode?.let { mode ->
        AlertDialog(
            onDismissRequest = { pendingMode = null },
            title = { Text(tr(settings, "Switch scan mode?", "切换扫描模式？")) },
            text = { Text(tr(settings, "The current unfinished pages will be discarded.", "当前尚未完成的页面将被丢弃。")) },
            confirmButton = {
                TextButton(onClick = { pendingMode = null; model.discardScanAndChangeMode(mode) }) { Text(tr(settings, "Discard and switch", "丢弃并切换")) }
            },
            dismissButton = { TextButton(onClick = { pendingMode = null }) { Text(tr(settings, "Cancel", "取消")) } },
        )
    }
}

@Composable
fun CameraPreview(
    imageCapture: ImageCapture,
    lensFacing: Int,
    analyzer: ImageAnalysis.Analyzer?,
    analysisExecutor: Executor,
    settings: AppSettings,
    onCameraBound: (Camera) -> Unit,
    onCameraError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            val previewView = PreviewView(ctx).apply {
                scaleType = PreviewView.ScaleType.FIT_CENTER
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            }
            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
            cameraProviderFuture.addListener({
                runCatching {
                    val cameraProvider = cameraProviderFuture.get()
                    val preview = Preview.Builder().build()
                        .also {
                            it.surfaceProvider = previewView.surfaceProvider
                        }
                    cameraProvider.unbindAll()
                    val selector = CameraSelector.Builder().requireLensFacing(lensFacing).build()
                    val useCases = mutableListOf<androidx.camera.core.UseCase>(preview, imageCapture)
                    var analysis: ImageAnalysis? = null
                    if (analyzer != null) {
                        // 720p analysis: enough detail for the edge detector's 720-long-side
                        // pipeline without starving the preview or capture frame rate.
                        val analysisSelector = ResolutionSelector.Builder()
                            .setResolutionStrategy(ResolutionStrategy(android.util.Size(1280, 720), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER))
                            .build()
                        analysis = ImageAnalysis.Builder()
                            .setResolutionSelector(analysisSelector)
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()
                        analysis.setAnalyzer(analysisExecutor, analyzer)
                        useCases += analysis
                    }
                    val camera = runCatching {
                        cameraProvider.bindToLifecycle(lifecycleOwner, selector, *useCases.toTypedArray())
                    }.getOrElse { analysisError ->
                        AppLogger.w("Camera", "ImageAnalysis unavailable; falling back to capture-only: ${analysisError.message}")
                        analysis?.clearAnalyzer()
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(lifecycleOwner, selector, preview, imageCapture)
                    }
                    onCameraBound(camera)
                }.onFailure { error ->
                    AppLogger.e("Camera", "Unable to open camera", error)
                    onCameraError()
                    Toast.makeText(ctx, tr(settings, "Unable to open camera: ${error.message ?: "camera unavailable"}", "无法打开相机：${error.message ?: "相机不可用"}"), Toast.LENGTH_SHORT).show()
                }
            }, ContextCompat.getMainExecutor(ctx))
            previewView
        },
    )
}

@Composable
private fun LiveDocumentGuide(frame: LiveDocumentFrame?, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val fallback = listOf(
            Offset(size.width * .12f, size.height * .16f),
            Offset(size.width * .88f, size.height * .16f),
            Offset(size.width * .88f, size.height * .84f),
            Offset(size.width * .12f, size.height * .84f),
        )
        val points = if (frame?.corners?.size == 4) {
            val frameWidth = frame.imageAspectRatio
            val frameHeight = 1f
            val scale = minOf(size.width / frameWidth, size.height / frameHeight)
            val shownWidth = frameWidth * scale
            val shownHeight = frameHeight * scale
            val left = (size.width - shownWidth) / 2f
            val top = (size.height - shownHeight) / 2f
            frame.corners.map { Offset(left + it.x * shownWidth, top + it.y * shownHeight) }
        } else fallback
        val color = if (frame == null) ComposeColor.White.copy(alpha = .48f) else if (frame.stable) Teal else ComposeColor(0xFFFFC857)
        val path = Path().apply {
            moveTo(points[0].x, points[0].y)
            points.drop(1).forEach { lineTo(it.x, it.y) }
            close()
        }
        drawPath(path, color, style = Stroke(width = if (frame?.stable == true) 6f else 4f))
        points.forEach { point ->
            drawCircle(ComposeColor.White, radius = 8f, center = point)
            drawCircle(color, radius = 8f, center = point, style = Stroke(width = 4f))
        }
    }
}

fun takeRealPhoto(context: Context, imageCapture: ImageCapture, model: ClearScanViewModel, settings: AppSettings) {
    val outputFile = File(context.cacheDir, "clearscan-capture-${System.currentTimeMillis()}.jpg")
    outputFile.parentFile?.mkdirs()
    if (context is android.app.Activity) {
        @Suppress("DEPRECATION")
        imageCapture.targetRotation = context.windowManager.defaultDisplay.rotation
    }
    val output = ImageCapture.OutputFileOptions.Builder(outputFile).build()
    val executor: Executor = ContextCompat.getMainExecutor(context)
    runCatching {
        imageCapture.takePicture(
            output,
            executor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    model.capturePhotoFile(outputFile)
                }

            override fun onError(exception: ImageCaptureException) {
                AppLogger.e("Camera", "ImageCapture failed", exception)
                Toast.makeText(context, tr(settings, "Photo failed: ${exception.message}", "拍摄失败：${exception.message}"), Toast.LENGTH_SHORT).show()
            }
            },
        )
    }.onFailure { error ->
        AppLogger.e("Camera", "takePicture invocation failed", error)
        Toast.makeText(context, tr(settings, "Photo failed: ${error.message ?: "camera not ready"}", "拍摄失败：${error.message ?: "相机未就绪"}"), Toast.LENGTH_SHORT).show()
    }
}

@Composable
fun CameraSmallButton(icon: ImageVector, onClick: () -> Unit) {
    Box(Modifier.size(50.dp).clip(RoundedCornerShape(11.dp)).background(ComposeColor(0xFF171717)).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = ComposeColor.White, modifier = Modifier.size(27.dp))
    }
}

@Composable
fun SettingToggle(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

fun copyText(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("ClearScan", text))
}

fun openSafeUrl(context: Context, value: String, settings: AppSettings) {
    val uri = runCatching { Uri.parse(value) }.getOrNull() ?: return
    if (uri.scheme?.lowercase() !in setOf("http", "https")) return
    val intent = Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
        .onFailure { Toast.makeText(context, tr(settings, "No browser can open this link", "没有可打开此链接的浏览器"), Toast.LENGTH_SHORT).show() }
}

fun searchBarcode(context: Context, value: String) {
    val uri = Uri.parse("https://www.google.com/search?q=${Uri.encode(value)}")
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

@Composable
fun DocumentOnTable() {
    Box(Modifier.fillMaxWidth(.78f).aspectRatio(.72f).clip(RoundedCornerShape(3.dp)).background(ComposeColor(0xFFF8F8F8)).border(3.dp, Teal)) {
        Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("AGREEMENT", fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(24.dp))
            repeat(5) { index ->
                Text("${index + 1}. Terms of Agreement", Modifier.fillMaxWidth(), fontWeight = FontWeight.Bold, fontSize = 11.sp)
                Spacer(Modifier.height(6.dp))
                Box(Modifier.fillMaxWidth().height(8.dp).background(ComposeColor(0xFFE0E0E0)))
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

@Composable
fun CropScreen(state: UiState, model: ClearScanViewModel) {
    val settings = state.settings
    val dark = isDarkTheme(settings)
    Column(Modifier.fillMaxSize().background(if (dark) ComposeColor(0xFF111317) else MaterialTheme.colorScheme.background).statusBarsPadding()) {
        TopBar(tr(settings, "Crop", "裁剪"), onBack = model::back, action = tr(settings, "Next", "下一步"), onAction = model::applyCropAndEdit, dark = dark)
        Box(Modifier.weight(1f).fillMaxWidth().padding(10.dp).clipToBounds(), contentAlignment = Alignment.Center) {
            // No fillMaxWidth here: with loose constraints the aspectRatio modifier picks the
            // largest size that fits inside the box, so tall shots can no longer overflow
            // into the top bar or the bottom toolbar.
            CropEditor(
                bitmap = state.processedBitmap ?: state.scanBitmap,
                points = state.cropPoints,
                onPointsChange = model::setCropPoints,
            )
            when (state.detectionStatus) {
                DocumentDetectionStatus.Detecting -> Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = .94f),
                    shape = RoundedCornerShape(8.dp),
                    shadowElevation = 3.dp,
                ) {
                    Row(
                        Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Teal)
                        Text(tr(settings, "Detecting document edges...", "正在识别文档边缘..."), fontSize = 13.sp)
                    }
                }
                DocumentDetectionStatus.Detected -> Surface(
                    modifier = Modifier.align(Alignment.TopCenter),
                    color = Teal.copy(alpha = .94f),
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Text(
                        tr(settings, "Document aligned", "文档已智能对齐") + " ${(state.detectionConfidence * 100).roundToInt()}%",
                        Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        color = ComposeColor.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                DocumentDetectionStatus.LowConfidence, DocumentDetectionStatus.Failed -> Surface(
                    modifier = Modifier.align(Alignment.TopCenter).clickable { model.redetectDocument() },
                    color = MaterialTheme.colorScheme.surface.copy(alpha = .96f),
                    shape = RoundedCornerShape(8.dp),
                    shadowElevation = 2.dp,
                ) {
                    Row(
                        Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        Icon(Icons.Default.AutoAwesome, null, Modifier.size(16.dp), tint = Teal)
                        Text(tr(settings, "Edges unclear - tap to retry", "边缘不清晰，点击重新识别"), fontSize = 12.sp)
                    }
                }
                else -> Unit
            }
        }
        if (state.draftPages.isNotEmpty()) {
            Text(
                tr(settings, "Page ${state.currentDraftIndex + 1} of ${state.draftPages.size}", "第 ${state.currentDraftIndex + 1} 页，共 ${state.draftPages.size} 页"),
                Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                color = if (dark) ComposeColor.White else MaterialTheme.colorScheme.onSurface,
                fontSize = 13.sp,
            )
            LazyRow(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.draftPages.size, key = { state.draftPages[it].id }) { index ->
                    val page = state.draftPages[index]
                    var dragX by remember(page.id) { mutableFloatStateOf(0f) }
                    Box(
                        Modifier
                            .size(54.dp, 70.dp)
                            .border(if (index == state.currentDraftIndex) 3.dp else 1.dp, if (index == state.currentDraftIndex) Teal else Muted, RoundedCornerShape(5.dp))
                            .pointerInput(page.id, index) {
                                detectDragGestures(
                                    onDragStart = { dragX = 0f },
                                    onDragEnd = {
                                        when {
                                            dragX > 36f -> model.moveDraftPage(index, (index + 1).coerceAtMost(state.draftPages.lastIndex))
                                            dragX < -36f -> model.moveDraftPage(index, (index - 1).coerceAtLeast(0))
                                        }
                                        dragX = 0f
                                    },
                                    onDrag = { change, amount -> change.consume(); dragX += amount.x },
                                )
                            }
                            .clickable { model.selectDraftPage(index) },
                    ) {
                        Thumbnail(page.thumbnailPath, Modifier.fillMaxSize())
                        // Static blue crop border as a hint: no handles, not editable here. Tap the
                        // thumbnail to switch to the large image and edit there.
                        CropOutlineOverlay(
                            points = decodeCropPoints(page.cropPoints),
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
            TextButton(
                onClick = { model.deleteCurrentDraft(false) },
                modifier = Modifier.align(Alignment.CenterHorizontally),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
            ) { Text(tr(settings, "Delete page", "删除此页"), fontSize = 12.sp) }
        }
        // Compact toolbar: smart crop, select-all crop, retake, rotate left/right.
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CropToolButton(tr(settings, "Smart Crop", "智能裁切"), Icons.Default.AutoAwesome, dark = dark) { model.setCropPreset("Auto") }
            CropToolButton(tr(settings, "Select All", "全选裁切"), Icons.Default.CropFree, dark = dark) { model.setCropPreset("Original") }
            CropToolButton(tr(settings, "Retake", "再拍一张"), Icons.Default.AddAPhoto, dark = dark) { model.retakePhoto() }
            CropToolButton(tr(settings, "Rotate Left", "左转"), Icons.Default.RotateLeft, dark = dark) { model.rotate(clockwise = false) }
            CropToolButton(tr(settings, "Rotate Right", "右转"), Icons.Default.RotateRight, dark = dark) { model.rotate(clockwise = true) }
        }
    }
}

@Composable
fun CropToolButton(label: String, icon: ImageVector, dark: Boolean = false, onClick: () -> Unit) {
    val content = if (dark) ComposeColor.White else MaterialTheme.colorScheme.onSurface
    Column(
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(ComposeColor.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, label, tint = content, modifier = Modifier.size(20.dp))
        Spacer(Modifier.height(3.dp))
        Text(label, fontSize = 11.sp, color = content, maxLines = 1)
    }
}

/** Static blue quadrilateral hint of a page's crop region (no draggable handles). */
@Composable
fun CropOutlineOverlay(points: List<Offset>, modifier: Modifier = Modifier) {
    if (points.size < 4) return
    Canvas(modifier) {
        val outline = Path()
        points.take(4).forEachIndexed { i, p ->
            val x = p.x * size.width
            val y = p.y * size.height
            if (i == 0) outline.moveTo(x, y) else outline.lineTo(x, y)
        }
        outline.close()
        drawPath(outline, ComposeColor(0x221E88FF), style = Stroke(width = size.minDimension / 8f))
        drawPath(outline, ComposeColor(0xFF1E88FF), style = Stroke(width = size.minDimension / 24f))
    }
}

@Composable
fun CropEditor(
    bitmap: Bitmap?,
    points: List<Offset>,
    onPointsChange: (List<Offset>) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (bitmap == null) {
        ScanBitmap(null, modifier.aspectRatio(.72f))
        return
    }
    val aspect = bitmap.width.toFloat() / bitmap.height.toFloat()
    var localPoints by remember { mutableStateOf(points) }
    var canvasSize by remember { mutableStateOf(Size(1f, 1f)) }
    var selectedHandle by remember { mutableStateOf(-1) }
    var fineTuneDirection by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(points) {
        if (selectedHandle < 0 && points.size == 4 && localPoints.size == 4) {
            val start = localPoints
            Animatable(0f).animateTo(1f, animationSpec = tween(240)) {
                localPoints = start.zip(points) { from, to ->
                    Offset(
                        from.x + (to.x - from.x) * value,
                        from.y + (to.y - from.y) * value,
                    )
                }
            }
        }
    }
    val currentPoints by rememberUpdatedState(localPoints)
    Box(
        modifier
            .aspectRatio(aspect)
            .background(ComposeColor.White)
            .pointerInput(canvasSize) {
                detectDragGestures(
                    onDragStart = { start ->
                        val nearest = currentPoints
                            .mapIndexed { index, p ->
                                val handle = Offset(p.x * canvasSize.width, p.y * canvasSize.height)
                                index to hypot(start.x - handle.x, start.y - handle.y)
                            }
                            .minByOrNull { it.second }
                        selectedHandle = if (nearest != null && nearest.second < 180f) nearest.first else -1
                        fineTuneDirection = null
                    },
                    onDragEnd = {
                        val selected = selectedHandle
                        if (selected >= 0) onPointsChange(currentPoints)
                        selectedHandle = -1
                        fineTuneDirection = null
                    },
                    onDragCancel = {
                        localPoints = points
                        selectedHandle = -1
                        fineTuneDirection = null
                    },
                    onDrag = { change, drag ->
                        val selected = selectedHandle
                        if (selected >= 0) {
                            change.consume()
                            val current = currentPoints[selected]
                            val next = Offset(
                                current.x + drag.x / canvasSize.width.coerceAtLeast(1f),
                                current.y + drag.y / canvasSize.height.coerceAtLeast(1f),
                            )
                            localPoints = currentPoints.toMutableList().also { it[selected] = next.coerceCropPoint() }
                        }
                    },
                )
            }
    ) {
        Box(
            Modifier.fillMaxSize()
        ) {
            Image(bitmap.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
            Canvas(
                Modifier
                    .fillMaxSize()
                    .onSizeChanged { canvasSize = Size(it.width.toFloat(), it.height.toFloat()) }
            ) {
                val px = currentPoints.map { Offset(it.x * size.width, it.y * size.height) }
                val path = Path().apply {
                    moveTo(px[0].x, px[0].y)
                    lineTo(px[1].x, px[1].y)
                    lineTo(px[2].x, px[2].y)
                    lineTo(px[3].x, px[3].y)
                    close()
                }
                drawPath(path, Teal, style = Stroke(width = 5f))
                px.forEach {
                    drawCircle(ComposeColor.White, 25f, it)
                    drawCircle(Teal, 25f, it, style = Stroke(width = 5f))
                }
            }
            if (selectedHandle >= 0 && canvasSize.width > 0 && canvasSize.height > 0) {
                val handlePos = Offset(currentPoints[selectedHandle].x * canvasSize.width, currentPoints[selectedHandle].y * canvasSize.height)
                val zoomSize = 120f
                Box(
                    Modifier
                        .size(zoomSize, zoomSize)
                        .offset(
                            x = (handlePos.x - zoomSize / 2).coerceIn(0f, canvasSize.width - zoomSize),
                            y = (handlePos.y - zoomSize / 2).coerceIn(0f, canvasSize.height - zoomSize),
                        )
                        .clip(RoundedCornerShape(12.dp))
                        .background(ComposeColor(0xCC1A1A1A))
                        .border(2.dp, Teal, RoundedCornerShape(12.dp))
                ) {
                val zoomBitmap = rememberUpdatedState(
                    if (selectedHandle >= 0 && canvasSize.width > 0 && canvasSize.height > 0) {
                        val sx = (currentPoints[selectedHandle].x * bitmap.width * 0.5f).toInt().coerceIn(0, bitmap.width - 1)
                        val sy = (currentPoints[selectedHandle].y * bitmap.height * 0.5f).toInt().coerceIn(0, bitmap.height - 1)
                        val sw = minOf(60, bitmap.width - sx)
                        val sh = minOf(60, bitmap.height - sy)
                        if (sw > 0 && sh > 0) Bitmap.createBitmap(bitmap, sx, sy, sw, sh) else null
                    } else null
                ).value
                zoomBitmap?.let {
                    Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
                }
                    Canvas(Modifier.fillMaxSize()) {
                        val cx = size.width / 2f
                        val cy = size.height / 2f
                        drawCircle(ComposeColor.White, 3f, Offset(cx, cy))
                    }
                }
                Column(
                    Modifier
                        .offset(
                            x = (handlePos.x + 40f).coerceIn(0f, canvasSize.width - 70f),
                            y = (handlePos.y - 70f).coerceIn(0f, canvasSize.height - 70f),
                        )
                        .padding(4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    IconButton(onClick = {
                        fineTuneDirection = "up"
                        localPoints = localPoints.toMutableList().also { list ->
                            val idx = selectedHandle
                            list[idx] = Offset(list[idx].x, (list[idx].y - 0.01f).coerceIn(0f, 1f))
                        }
                        onPointsChange(localPoints)
                    }) {
                        Icon(Icons.Default.ArrowForwardIos, null, tint = ComposeColor.White, modifier = Modifier.size(24.dp).rotate(-90f))
                    }
                    Row(Modifier.padding(horizontal = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = {
                            fineTuneDirection = "left"
                            localPoints = localPoints.toMutableList().also { list ->
                                val idx = selectedHandle
                                list[idx] = Offset((list[idx].x - 0.01f).coerceIn(0f, 1f), list[idx].y)
                            }
                            onPointsChange(localPoints)
                        }) {
                            Icon(Icons.Default.ArrowForwardIos, null, tint = ComposeColor.White, modifier = Modifier.size(24.dp).rotate(-180f))
                        }
                        Icon(Icons.Default.Search, null, tint = Teal, modifier = Modifier.size(24.dp))
                        IconButton(onClick = {
                            fineTuneDirection = "right"
                            localPoints = localPoints.toMutableList().also { list ->
                                val idx = selectedHandle
                                list[idx] = Offset((list[idx].x + 0.01f).coerceIn(0f, 1f), list[idx].y)
                            }
                            onPointsChange(localPoints)
                        }) {
                            Icon(Icons.Default.ArrowForwardIos, null, tint = ComposeColor.White, modifier = Modifier.size(24.dp))
                        }
                    }
                    IconButton(onClick = {
                        fineTuneDirection = "down"
                        localPoints = localPoints.toMutableList().also { list ->
                            val idx = selectedHandle
                            list[idx] = Offset(list[idx].x, (list[idx].y + 0.01f).coerceIn(0f, 1f))
                        }
                        onPointsChange(localPoints)
                    }) {
                        Icon(Icons.Default.ArrowForwardIos, null, tint = ComposeColor.White, modifier = Modifier.size(24.dp).rotate(90f))
                    }
                }
            }
        }
    }
}

@Composable
fun EditScreen(state: UiState, model: ClearScanViewModel) {
    val settings = state.settings
    val draftPages = state.draftPages
    val pageCount = draftPages.size.coerceAtLeast(1)
    val initialPage = state.currentDraftIndex.coerceIn(0, pageCount - 1).coerceAtLeast(0)
    val pagerState = rememberPagerState(initialPage = initialPage) { pageCount }
    val currentPageIndex = pagerState.currentPage.coerceAtMost(pageCount - 1)
    val currentPage = draftPages.getOrNull(currentPageIndex)
    val filters = remember { DocumentFilters }
    // Current page's source image; filtered pages reload when the edit version bumps.
    val pageBitmap by produceState<Bitmap?>(initialValue = null, currentPage?.id, state.editVersion) {
        value = withContext(Dispatchers.IO) {
            if (currentPage == null) {
                if (draftPages.isEmpty()) state.processedBitmap ?: state.scanBitmap else null
            } else {
                val p = currentPage.processedPath.ifBlank { currentPage.originalPath }
                val b = ImageProcessor.readBitmap(p, 1400)
                if (b != null && currentPage.processedPath.isBlank() && currentPage.rotation != 0) ImageProcessor.rotateQuarters(b, currentPage.rotation) else b
            }
        }
    }
    val thumbSource = remember(pageBitmap) { ImageProcessor.previewBitmap(pageBitmap, 288) }
    var thumbPreviews by remember(thumbSource) { mutableStateOf<Map<String, Bitmap?>>(emptyMap()) }
    LaunchedEffect(thumbSource) {
        thumbPreviews = withContext(Dispatchers.Default) { filters.associateWith { filter -> ImageProcessor.filter(thumbSource, filter) } }
    }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding()) {
        TopBar(tr(settings, "Edit", "编辑"), onBack = model::back, action = tr(settings, "Next", "下一步"), onAction = model::toSave)
        if (draftPages.size > 1) {
            Text(
                tr(settings, "Page ${currentPageIndex + 1} of ${draftPages.size}", "第 ${currentPageIndex + 1} 页，共 ${draftPages.size} 页"),
                Modifier.fillMaxWidth().padding(top = 4.dp), textAlign = TextAlign.Center, color = Muted, fontSize = 13.sp,
            )
        }
        HorizontalPager(state = pagerState, modifier = Modifier.weight(1f).fillMaxWidth()) { index ->
            val page = draftPages.getOrNull(index)
            val fallback = if (draftPages.isEmpty()) state.processedBitmap ?: state.scanBitmap else null
            Box(Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
                val bitmap by produceState<Bitmap?>(initialValue = fallback, page?.id, state.editVersion) {
                    value = withContext(Dispatchers.IO) {
                        if (page == null) fallback
                        else {
                            val p = page.processedPath.ifBlank { page.originalPath }
                            val b = ImageProcessor.readBitmap(p, 1400)
                            if (b != null && page.processedPath.isBlank() && page.rotation != 0) ImageProcessor.rotateQuarters(b, page.rotation) else b
                        }
                    }
                }
                ScanBitmap(bitmap ?: fallback, Modifier.fillMaxWidth().aspectRatio(.72f))
            }
        }
        // Directly show every filter effect for the current page; tapping applies it to that page.
        LazyRow(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 10.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(filters) { filter ->
                val selected = filter == state.selectedFilter
                val chipAlpha by animateFloatAsState(if (selected) 1f else 0f, animationSpec = tween(180), label = "filter-chip")
                Column(Modifier.width(84.dp).clickable { model.applyFilterToPage(currentPageIndex, filter) }, horizontalAlignment = Alignment.CenterHorizontally) {
                    ScanBitmap(thumbPreviews[filter], Modifier.size(76.dp, 98.dp).clip(RoundedCornerShape(6.dp)))
                    Spacer(Modifier.height(6.dp))
                    Text(filterLabel(settings, filter), color = if (selected) ComposeColor.White else MaterialTheme.colorScheme.onSurface, modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(if (selected) Teal.copy(alpha = chipAlpha) else ComposeColor.Transparent).padding(horizontal = 10.dp, vertical = 4.dp), fontSize = 13.sp, maxLines = 1)
                }
            }
        }
    }
}

@Composable
fun EditTool(label: String, icon: ImageVector, onClick: () -> Unit) {
    Column(Modifier.clickable(onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, label, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(30.dp))
        Spacer(Modifier.height(7.dp))
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
fun FilterScreen(state: UiState, model: ClearScanViewModel) {
    val settings = state.settings
    val source = state.processedBitmap ?: state.scanBitmap
    val mainSource = remember(source) { ImageProcessor.previewBitmap(source, 1440) }
    val thumbSource = remember(source) { ImageProcessor.previewBitmap(source, 320) }
    // Only the ant-cave OpenCV-accelerated filters remain; the original author's filter
    // presets were removed. The smart filters (division normalization, the pipeline behind
    // classic scanner apps) lead the strip. The initial selection comes from the
    // user-configurable default filter setting.
    val filters = remember { DocumentFilters }
    // Filters that expose user-tunable parameters below the strip.
    val tunableFilters = remember { setOf("Smart Gray", "Magic Color", "B&W", "Ink", "White Paper") }
    var selectedFilter by remember { mutableStateOf(state.selectedFilter) }
    var filterParams by remember { mutableStateOf(FilterParams()) }
    var mainPreview by remember(mainSource) { mutableStateOf(mainSource) }
    var mainCache by remember(mainSource) { mutableStateOf(mapOf<String, Bitmap?>()) }
    var thumbPreviews by remember(thumbSource) { mutableStateOf<Map<String, Bitmap?>>(emptyMap()) }
    LaunchedEffect(thumbSource) {
        thumbPreviews = withContext(Dispatchers.Default) {
            filters.associateWith { filter -> ImageProcessor.filter(thumbSource, filter) }
        }
    }
    LaunchedEffect(mainSource, selectedFilter, filterParams) {
        val key = filterCacheKey(selectedFilter, filterParams)
        mainCache[key]?.let { mainPreview = it; return@LaunchedEffect }
        // Debounce slider drags before re-running the filter on the large preview.
        delay(60)
        val generated = withContext(Dispatchers.Default) { ImageProcessor.filter(mainSource, selectedFilter, filterParams) }
        mainPreview = generated
        mainCache = (mainCache + (key to generated)).entries.toList().takeLast(4).associate { it.key to it.value }
    }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding()) {
        TopBar(tr(settings, "Filter", "滤镜"), onBack = model::back, action = tr(settings, "Apply", "应用"), onAction = { model.applyFilter(selectedFilter, filterParams) })
        Box(Modifier.weight(1f).fillMaxWidth().padding(10.dp), contentAlignment = Alignment.Center) {
            ScanBitmap(mainPreview ?: state.processedBitmap ?: state.scanBitmap, Modifier.fillMaxWidth().aspectRatio(.72f))
        }
        LazyRow(Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, top = 10.dp, bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(filters) { filter ->
                val selected = filter == selectedFilter
                val chipAlpha by animateFloatAsState(if (selected) 1f else 0f, animationSpec = tween(180), label = "filter-chip")
                Column(Modifier.width(84.dp).clickable { selectedFilter = filter }, horizontalAlignment = Alignment.CenterHorizontally) {
                    ScanBitmap(thumbPreviews[filter], Modifier.size(76.dp, 98.dp))
                    Spacer(Modifier.height(6.dp))
                    Text(filterLabel(settings, filter), color = if (selected) ComposeColor.White else MaterialTheme.colorScheme.onSurface, modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(if (selected) Teal.copy(alpha = chipAlpha) else ComposeColor.Transparent).padding(horizontal = 10.dp, vertical = 4.dp), fontSize = 13.sp, maxLines = 1)
                }
            }
        }
        if (selectedFilter in tunableFilters) {
            when (selectedFilter) {
                "White Paper" -> FilterAdjustment(
                    label = tr(settings, "Paper Lift", "纸张提亮"),
                    valueText = "%.2f".format(filterParams.paperLift),
                    value = filterParams.paperLift,
                    range = .72f..1f,
                ) { filterParams = filterParams.copy(paperLift = it) }
                "Smart Gray", "Magic Color" -> {
                    FilterAdjustment(
                        label = tr(settings, "Enhance", "增强"),
                        valueText = "%.0f%%".format(filterParams.smartStrength * 100),
                        value = filterParams.smartStrength,
                        range = 0f..1.3f,
                    ) { filterParams = filterParams.copy(smartStrength = it) }
                    FilterAdjustment(
                        label = tr(settings, "Sharpen", "锐化"),
                        valueText = "%.1fx".format(filterParams.sharpenScale),
                        value = filterParams.sharpenScale,
                        range = 0f..1.6f,
                    ) { filterParams = filterParams.copy(sharpenScale = it) }
                }
                else -> {
                    FilterAdjustment(
                        label = tr(settings, "Threshold", "阈值"),
                        valueText = filterParams.threshold.toInt().toString(),
                        value = filterParams.threshold,
                        range = 2f..30f,
                    ) { filterParams = filterParams.copy(threshold = it) }
                    FilterAdjustment(
                        label = tr(settings, "Sharpen", "锐化"),
                        valueText = "%.1fx".format(filterParams.sharpenScale),
                        value = filterParams.sharpenScale,
                        range = 0f..1.6f,
                    ) { filterParams = filterParams.copy(sharpenScale = it) }
                    // Three discrete levels (off/standard/aggressive) mapped to median kernels 1/3/5.
                    val denoiseLabel = when (filterParams.denoise) {
                        1 -> tr(settings, "Off", "关")
                        5 -> tr(settings, "Strong", "强")
                        else -> tr(settings, "Standard", "标准")
                    }
                    FilterAdjustment(
                        label = tr(settings, "Denoise", "降噪"),
                        valueText = denoiseLabel,
                        value = when (filterParams.denoise) { 1 -> 0f; 5 -> 2f; else -> 1f },
                        range = 0f..2f,
                    ) { filterParams = filterParams.copy(denoise = when (it.roundToInt()) { 0 -> 1; 2 -> 5; else -> 3 }) }
                }
            }
            Spacer(Modifier.navigationBarsPadding())
        }
    }
}

/** Cache key that folds the current filter parameters into the preview cache entry. */
private fun filterCacheKey(filter: String, params: FilterParams): String =
    "$filter|${params.threshold}|${params.sharpenScale}|${params.paperLift}|${params.denoise}|${params.smartStrength}"

@Composable
private fun FilterAdjustment(label: String, valueText: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.width(88.dp), fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
        Slider(value = value, onValueChange = onChange, valueRange = range, modifier = Modifier.weight(1f))
        Text(valueText, Modifier.width(48.dp), fontSize = 12.sp, color = Muted, textAlign = TextAlign.End)
    }
}

private fun filterLabel(settings: AppSettings, filter: String): String = when (filter) {
    "Smart Gray" -> tr(settings, "Smart Gray", "智能灰度")
    "Magic Color" -> tr(settings, "Magic Color", "魔法彩色")
    "White Paper" -> tr(settings, "White Paper", "白纸")
    "B&W" -> tr(settings, "B&W", "黑白")
    "Ink" -> tr(settings, "Ink", "墨迹")
    else -> filter
}

/** Localized display label for persisted image-quality values (identifiers stay English). */
fun qualityLabel(settings: AppSettings, quality: String): String = when (quality) {
    "High" -> tr(settings, "High", "高")
    "Medium" -> tr(settings, "Medium", "中")
    "Low" -> tr(settings, "Low", "低")
    else -> quality
}

/** Localized display label for persisted save-path values. */
fun savePathLabel(settings: AppSettings, path: String): String = when (path) {
    "Internal Storage" -> tr(settings, "Internal Storage", "内部存储")
    "Documents" -> tr(settings, "Documents", "文档目录")
    else -> path
}

@Composable
fun AdjustScreen(state: UiState, model: ClearScanViewModel) {
    val settings = state.settings
    var brightness by remember { mutableFloatStateOf(0.1f) }
    var contrast by remember { mutableFloatStateOf(1.15f) }
    var saturation by remember { mutableFloatStateOf(1.2f) }
    val source = state.processedBitmap ?: state.scanBitmap
    val preview = remember(source) { ImageProcessor.previewBitmap(source, 900) }
    var adjustedPreview by remember(preview) { mutableStateOf(preview) }
    LaunchedEffect(preview, brightness, contrast, saturation) {
        delay(45)
        adjustedPreview = withContext(Dispatchers.Default) {
            ImageProcessor.adjust(preview, brightness, contrast, saturation)
        }
    }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding()) {
        TopBar(tr(settings, "Adjust", "调节"), onBack = model::back, action = tr(settings, "Apply", "应用"), onAction = { model.applyAdjust(brightness, contrast, saturation) })
        Box(Modifier.fillMaxWidth().height(430.dp).padding(14.dp), contentAlignment = Alignment.Center) {
            ScanBitmap(adjustedPreview, Modifier.fillMaxHeight().aspectRatio(.72f))
        }
        Adjustment(tr(settings, "Brightness", "亮度"), brightness, -0.4f..0.4f) { brightness = it }
        Adjustment(tr(settings, "Contrast", "对比度"), contrast, 0.5f..1.8f) { contrast = it }
        Adjustment(tr(settings, "Saturation", "饱和度"), saturation, 0f..2f) { saturation = it }
        Spacer(Modifier.weight(1f))
        Row(Modifier.fillMaxWidth().height(96.dp).navigationBarsPadding(), horizontalArrangement = Arrangement.SpaceAround, verticalAlignment = Alignment.CenterVertically) {
            EditTool(tr(settings, "Adjust", "调节"), Icons.Default.Brightness6) {}
            EditTool(tr(settings, "Crop", "裁剪"), Icons.Default.Crop, model::toCrop)
            EditTool(tr(settings, "Filter", "滤镜"), Icons.Default.Palette, model::toFilter)
        }
    }
}

@Composable
fun Adjustment(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.width(104.dp), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
        Slider(value = value, onValueChange = onChange, valueRange = range, modifier = Modifier.weight(1f))
    }
}

@Composable
fun SaveScreen(state: UiState, model: ClearScanViewModel) {
    var title by remember { mutableStateOf("Contract Agreement") }
    var quality by remember { mutableStateOf("High") }
    val settings = state.settings
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding()) {
        TopBar(tr(settings, "Save", "保存"), onBack = model::back)
        Box(Modifier.fillMaxWidth().height(280.dp), contentAlignment = Alignment.Center) {
            ScanBitmap(state.processedBitmap ?: state.scanBitmap, Modifier.width(150.dp).aspectRatio(.72f))
        }
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(tr(settings, "Title", "标题"), color = Muted, fontSize = 16.sp)
            OutlinedTextField(title, { title = it }, Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), singleLine = true)
            SelectField(tr(settings, "Image Quality", "图片质量"), quality, listOf("High", "Medium", "Low"), displayValue = { qualityLabel(settings, it) }) { quality = it }
            Button(onClick = { model.saveDocument(title, quality) }, modifier = Modifier.fillMaxWidth().height(52.dp), colors = ButtonDefaults.buttonColors(containerColor = Teal), shape = RoundedCornerShape(12.dp)) {
                Icon(Icons.Default.Save, null)
                Spacer(Modifier.width(12.dp))
                Text(if (state.busy) tr(settings, "Saving...", "保存中...") else tr(settings, "Save", "保存"), fontSize = 18.sp)
            }
            Text(
                tr(settings, "Saved as images only. You can export a PDF later from the document page.", "只会保存为图片。你可以在文档页随时导出 PDF。"),
                color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp),
            )
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(tr(settings, "Save to", "保存到"), color = Muted, fontSize = 17.sp)
                Text("${savePathLabel(settings, state.settings.defaultSavePath)}  ›", color = Muted, fontSize = 17.sp)
            }
        }
    }
}

@Composable
fun SelectField(
    label: String,
    value: String,
    options: List<String>,
    modifier: Modifier = Modifier,
    displayValue: (String) -> String = { it },
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Column(modifier) {
        Text(label, color = Muted, fontSize = 18.sp)
        Box {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(58.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .border(1.dp, if (MaterialTheme.colorScheme.background == ComposeColor.White) ComposeColor(0xFFE8EAEE) else ComposeColor(0xFF2A313A), RoundedCornerShape(12.dp))
                    .clickable { expanded = true }
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(displayValue(value), fontSize = 17.sp, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("⌄", color = Muted)
            }
            DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
                options.forEach { option -> DropdownMenuItem(text = { Text(displayValue(option)) }, onClick = { onSelect(option); expanded = false }) }
            }
        }
    }
}

@Composable
fun DetailScreen(state: UiState, model: ClearScanViewModel) {
    val doc = state.selected ?: return
    val context = LocalContext.current
    var renameOpen by remember { mutableStateOf(false) }
    var passwordOpen by remember { mutableStateOf(false) }
    var moveOpen by remember { mutableStateOf(false) }
    val exportPdf = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        if (uri != null) model.exportDocumentAsPdf(doc, uri)
    }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().height(82.dp).padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = model::back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(doc.title, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${doc.type}    ${formatDate(doc.createdAt)}  •  ${formatSize(doc.sizeBytes)}", color = Muted, fontSize = 13.sp)
            }
            IconButton(onClick = { renameOpen = true }) { Icon(Icons.Default.Edit, null) }
        }
        Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 24.dp, vertical = 14.dp), contentAlignment = Alignment.Center) {
            DocumentPreviewPages(doc, state.settings, model)
        }
        Row(Modifier.fillMaxWidth().height(104.dp).navigationBarsPadding(), horizontalArrangement = Arrangement.SpaceAround, verticalAlignment = Alignment.CenterVertically) {
            EditTool(tr(state.settings, "Export PDF", "导出PDF"), Icons.Default.PictureAsPdf) { exportPdf.launch("${doc.title}.pdf") }
            EditTool(tr(state.settings, "Share", "分享"), Icons.Default.Share, model::shareSelected)
            EditTool(tr(state.settings, "Edit", "编辑"), Icons.Default.Edit) { model.toEdit() }
            EditTool(tr(state.settings, "Print", "打印"), Icons.Default.Print) { printDocument(context, doc) }
            EditTool(tr(state.settings, "Delete", "删除"), Icons.Default.Delete, model::deleteSelected)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            TextButton(onClick = { passwordOpen = true }) { Text(tr(state.settings, "Password", "密码")) }
            TextButton(onClick = { moveOpen = true }) { Text(tr(state.settings, "Move to folder", "移动到文件夹")) }
        }
    }
    if (renameOpen) RenameDialog(state.settings, doc.title, onDismiss = { renameOpen = false }, onRename = { model.renameSelected(it); renameOpen = false })
    if (passwordOpen) PasswordDialog(
        settings = state.settings, hasPassword = state.settings.passwordMap.containsKey(doc.id),
        onDismiss = { passwordOpen = false },
        onSave = { model.setDocumentPassword(doc.id, it); passwordOpen = false },
    )
    if (moveOpen) AlertDialog(
        onDismissRequest = { moveOpen = false },
        title = { Text(tr(state.settings, "Move document", "移动文档")) },
        text = {
            LazyColumn(Modifier.height(300.dp)) {
                item { TextButton(onClick = { model.moveDocument(doc, null); moveOpen = false }) { Text(tr(state.settings, "My Docs (root)", "我的文档（根目录）")) } }
                items(state.folders, key = { it.id }) { folder ->
                    TextButton(onClick = { model.moveDocument(doc, folder.id); moveOpen = false }) { Text(folderBreadcrumb(state.folders, folder.id)) }
                }
            }
        },
        confirmButton = { TextButton(onClick = { moveOpen = false }) { Text(tr(state.settings, "Cancel", "取消")) } },
    )
}

@Composable
fun DocumentPreviewPages(document: Document, settings: AppSettings, model: ClearScanViewModel) {
    var pages by remember(document.id, document.exportPath, document.pageCount) { mutableStateOf<List<Bitmap>>(emptyList()) }
    var loaded by remember(document.id, document.exportPath, document.pageCount) { mutableStateOf(false) }
    LaunchedEffect(document.id, document.exportPath, document.pageCount) {
        loaded = false
        pages = model.loadDocumentPages(document)
        loaded = true
    }
    if (pages.isEmpty()) {
        if (loaded) {
            Thumbnail(document.thumbnailPath, Modifier.fillMaxWidth().aspectRatio(.72f))
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(tr(settings, "Loading preview...", "正在加载预览..."), color = Muted)
            }
        }
        return
    }
    LazyColumn(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        contentPadding = PaddingValues(bottom = 18.dp),
    ) {
        items(pages.size) { index ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                ScanBitmap(pages[index], Modifier.fillMaxWidth().aspectRatio(pages[index].width.toFloat() / pages[index].height.toFloat()))
                if (pages.size > 1) {
                    Spacer(Modifier.height(6.dp))
                    Text("${index + 1} / ${pages.size}", color = Muted, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
fun RenameDialog(settings: AppSettings, current: String, onDismiss: () -> Unit, onRename: (String) -> Unit) {
    var text by remember { mutableStateOf(current) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(tr(settings, "Rename document", "重命名文档")) }, text = { OutlinedTextField(text, { text = it }, singleLine = true) }, confirmButton = { TextButton(onClick = { onRename(text) }) { Text(tr(settings, "Save", "保存")) } }, dismissButton = { TextButton(onClick = onDismiss) { Text(tr(settings, "Cancel", "取消")) } })
}

@Composable
fun PasswordDialog(settings: AppSettings, hasPassword: Boolean, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var password by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (hasPassword) tr(settings, "Update password", "更新密码") else tr(settings, "Set document password", "设置文档密码")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(tr(settings, "This password is stored locally and protects this document inside ClearScan.", "密码仅保存在本机，用于保护 ClearScan 内的此文档。"), color = Muted, fontSize = 14.sp)
                OutlinedTextField(password, { password = it }, singleLine = true, placeholder = { Text(tr(settings, "Enter password", "输入密码")) })
            }
        },
        confirmButton = { TextButton(onClick = { onSave(password) }) { Text(tr(settings, "Save", "保存")) } },
        dismissButton = {
            Row {
                if (hasPassword) TextButton(onClick = { onSave("") }) { Text(tr(settings, "Remove", "移除")) }
                TextButton(onClick = onDismiss) { Text(tr(settings, "Cancel", "取消")) }
            }
        },
    )
}

@Composable
fun ShareScreen(state: UiState, model: ClearScanViewModel) {
    val doc = state.selected ?: return
    val context = LocalContext.current
    val settings = state.settings
    LazyColumn(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding(), contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        item { TopTitle(tr(settings, "Share", "分享"), model::back) }
        item {
            Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(1.dp)) {
                Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Thumbnail(doc.thumbnailPath, Modifier.size(96.dp, 126.dp))
                    Spacer(Modifier.width(24.dp))
                    Column {
                        Text("${doc.title}.${doc.type.lowercase()}", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(10.dp))
                        Text("${doc.type} Document • ${formatSize(doc.sizeBytes)}", color = Muted)
                        Text(formatDate(doc.createdAt), color = Muted)
                    }
                }
            }
        }
        item { Text(tr(settings, "Share to", "分享到"), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface) }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                ShareCard(tr(settings, "WeChat", "微信好友"), Icons.Default.Share) {
                    shareFile(context, doc, "com.tencent.mm", tr(settings, "Send to WeChat", "发送给微信好友"))
                }
                ShareCard(tr(settings, "QQ", "QQ 好友"), Icons.Default.Share) {
                    shareFile(context, doc, "com.tencent.mobileqq", tr(settings, "Send to QQ", "发送给 QQ 好友"))
                }
                ShareCard(tr(settings, "More", "更多"), Icons.Default.MoreVert) {
                    shareFile(context, doc, chooserTitle = tr(settings, "Share document", "分享文档"))
                }
            }
        }
        item { Text(tr(settings, "More options", "更多操作"), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface) }
        item {
            Column(Modifier.clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surface).border(1.dp, ComposeColor(0xFFE8EAEE), RoundedCornerShape(14.dp))) {
                OptionRow(tr(settings, "Save to Files", "保存到文件"), Icons.Default.Folder) { saveToGallery(context, doc) }
                OptionRow(tr(settings, "Print", "打印"), Icons.Default.Print) { printDocument(context, doc) }
                OptionRow(tr(settings, "Open with another app", "用其他应用打开"), Icons.Default.FileOpen) { shareFile(context, doc) }
            }
        }
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) { BottomNav(settings, Tab.Docs, model::selectTab, model::openCamera) }
}

@Composable
fun ShareCard(label: String, icon: ImageVector, onClick: () -> Unit) {
    Column(Modifier.width(96.dp).height(112.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surface).clickable(onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Box(Modifier.size(52.dp).clip(CircleShape).background(Teal), contentAlignment = Alignment.Center) { Icon(icon, null, tint = ComposeColor.White) }
        Spacer(Modifier.height(10.dp))
        Text(label, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
    }
}

@Composable
fun ToolSelectScreen(state: UiState, model: ClearScanViewModel) {
    val tool = state.activeTool ?: "Tool"
    val settings = state.settings
    val required = requiredTypesFor(tool)
    val options = toolOptions(tool)
    val candidates = state.documents.filter { doc -> required.isEmpty() || doc.type.uppercase() in required }
    val enoughSelection = state.selectedToolIds.size >= minSelectionFor(tool)
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding()) {
        TopBar(toolLabel(tool, settings), onBack = model::back, action = tr(settings, "Run", "执行"), onAction = model::executeActiveTool)
        Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 8.dp)) {
            Text(selectionHint(tool, settings), color = Muted, fontSize = 15.sp)
            if (options.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(options) { option ->
                        val selected = state.toolOption == option
                        Text(
                            option,
                            modifier = Modifier
                                .clip(RoundedCornerShape(18.dp))
                                .background(if (selected) Teal else Soft)
                                .clickable { model.setToolOption(option) }
                                .padding(horizontal = 16.dp, vertical = 9.dp),
                            color = if (selected) ComposeColor.White else TextDark,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(start = 22.dp, end = 22.dp, top = 10.dp, bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (candidates.isEmpty()) {
                item { EmptyState(tr(settings, "No matching documents", "没有匹配的文档"), tr(settings, "Create or import a ${required.joinToString("/")} document first.", "请先创建或导入 ${required.joinToString("/")} 文档。")) }
            } else {
                items(candidates) { document ->
                    SelectableDocumentRow(
                        document = document,
                        selected = document.id in state.selectedToolIds,
                        onClick = { model.toggleToolDocument(document.id) },
                    )
                }
            }
        }
        Button(
            onClick = model::executeActiveTool,
            enabled = enoughSelection && !state.busy,
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 22.dp, vertical = 14.dp)
                .height(58.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Teal),
            shape = RoundedCornerShape(10.dp),
        ) {
            Text(if (state.busy) tr(settings, "Processing...", "处理中...") else tr(settings, "Continue", "继续"), fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun SelectableDocumentRow(document: Document, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) ComposeColor(0xFFE8FAF8) else ComposeColor.White)
            .border(1.dp, if (selected) Teal else ComposeColor(0xFFE8EAEE), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Thumbnail(document.thumbnailPath, Modifier.size(54.dp, 68.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(document.title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(6.dp))
            Text("${document.type} • ${document.pageCount} page • ${formatSize(document.sizeBytes)}", color = Muted, fontSize = 13.sp)
        }
        Checkbox(checked = selected, onCheckedChange = null)
    }
}

@Composable
fun ToolsScreen(state: UiState, model: ClearScanViewModel) {
    val settings = state.settings
    LazyColumn(Modifier.fillMaxSize().statusBarsPadding(), contentPadding = PaddingValues(start = 22.dp, end = 22.dp, top = 24.dp, bottom = 122.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(tr(settings, "Tools", "工具"), fontSize = 36.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onSurface)
                Icon(Icons.Default.RotateRight, null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(34.dp))
            }
        }
        item { Text(tr(settings, "Popular Tools", "常用工具"), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface) }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    ToolCard("ID Card Scan", tr(settings, "ID Card Scan", "证件扫描"), tr(settings, "Scan ID cards quickly\nand accurately", "快速准确扫描\n身份证件"), Icons.Outlined.Badge, Modifier.weight(1f), model)
                    ToolCard("PDF to Image", tr(settings, "PDF to Image", "PDF 转图片"), tr(settings, "Convert PDF pages\ninto images", "将 PDF 页面\n转换为图片"), Icons.Default.PictureAsPdf, Modifier.weight(1f), model)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    ToolCard("Image to PDF", tr(settings, "Image to PDF", "图片转 PDF"), tr(settings, "Convert images\ninto PDF files", "将图片转换为\nPDF 文件"), Icons.Default.Image, Modifier.weight(1f), model)
                    ToolCard("PDF Edit", tr(settings, "PDF Edit", "PDF 编辑"), tr(settings, "Edit pages in\nPDF files", "编辑 PDF\n页面"), Icons.Default.Edit, Modifier.weight(1f), model)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    ToolCard("Translate", tr(settings, "Translate", "翻译"), tr(settings, "Cloud AI\nmulti-language MT", "云端 AI\n多语言翻译"), Icons.Default.Language, Modifier.weight(1f), model)
                    ToolCard("Image Format Converter", tr(settings, "Image Format\nConverter", "图片格式\n转换"), tr(settings, "JPEG, PNG, WebP,\nBMP and PDF", "支持 JPEG、PNG、\nWebP、BMP、PDF"), Icons.Default.PhotoLibrary, Modifier.weight(1f), model)
                }
            }
        }
        item { Text(tr(settings, "More Tools", "更多工具"), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface) }
        item {
            Column(Modifier.clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surface).border(1.dp, ComposeColor(0xFFE8EAEE), RoundedCornerShape(12.dp))) {
                listOf(
                    Triple("Merge PDF", tr(settings, "Merge PDF", "合并 PDF"), Icons.Default.ContentCopy),
                    Triple("Split PDF", tr(settings, "Split PDF", "拆分 PDF"), Icons.Default.FileOpen),
                    Triple("Compress PDF", tr(settings, "Compress PDF", "压缩 PDF"), Icons.Default.PictureAsPdf),
                    Triple("QR Code Scan", tr(settings, "QR Code Scan", "二维码扫描"), Icons.Default.QrCodeScanner),
                    Triple("Barcode Scan", tr(settings, "Barcode Scan", "条形码扫描"), Icons.Default.Search),
                    Triple("Watermark", tr(settings, "Watermark", "添加水印"), Icons.Default.WaterDrop),
                    Triple("Add Signature", tr(settings, "Add Signature", "添加签名"), Icons.Default.Edit),
                ).forEach { item -> OptionRow(item.second, item.third) { model.runTool(item.first) } }
            }
        }
    }
}

@Composable
fun ToolCard(toolName: String, title: String, subtitle: String, icon: ImageVector, modifier: Modifier, model: ClearScanViewModel) {
    Card(modifier.height(170.dp).clickable { model.runTool(toolName) }, shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(0.dp)) {
        Column(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Icon(icon, title, tint = Teal, modifier = Modifier.size(46.dp))
            Column {
                Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Text(subtitle, color = Muted, fontSize = 15.sp)
            }
        }
    }
}

@Composable
fun TranslateScreen(state: UiState, model: ClearScanViewModel) {
    val settings = state.settings
    val translation = state.translationState
    val languages = listOf(
        "Auto", "Chinese", "English", "Japanese", "Korean", "French", "German", "Spanish",
        "Portuguese", "Italian", "Russian", "Arabic", "Thai", "Vietnamese", "Indonesian",
        "Malay", "Turkish", "Polish", "Dutch", "Czech", "Ukrainian", "Hindi",
    )
    LazyColumn(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding(),
        contentPadding = PaddingValues(22.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { TopBar(tr(settings, "Translate", "翻译"), onBack = model::back) }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(12.dp), elevation = CardDefaults.cardElevation(0.dp)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(tr(settings, "Cloud API Settings", "云端 API 设置"), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                        OutlinedTextField(
                            value = translation.cloudBaseUrl,
                            onValueChange = { model.updateTranslationCloudConfig(it, translation.cloudApiKey, translation.cloudModel) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            label = { Text(tr(settings, "Base URL", "API 地址")) },
                            placeholder = { Text("https://api.deepseek.com") },
                            supportingText = { Text(tr(settings, "The app calls {base}/chat/completions", "实际请求 {base}/chat/completions"), color = Muted, fontSize = 11.sp) },
                        )
                        var showKey by remember { mutableStateOf(false) }
                        OutlinedTextField(
                            value = translation.cloudApiKey,
                            onValueChange = { model.updateTranslationCloudConfig(translation.cloudBaseUrl, it, translation.cloudModel) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            label = { Text(tr(settings, "API Key", "API 密钥")) },
                            visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { showKey = !showKey }) {
                                    Icon(
                                        if (showKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        tr(settings, "Toggle key visibility", "切换密钥可见性"),
                                        tint = Muted,
                                    )
                                }
                            },
                            supportingText = { Text(tr(settings, "Stored only on this device.", "仅保存在本机。"), color = Muted, fontSize = 11.sp) },
                        )
                        OutlinedTextField(
                            value = translation.cloudModel,
                            onValueChange = { model.updateTranslationCloudConfig(translation.cloudBaseUrl, translation.cloudApiKey, it) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            label = { Text(tr(settings, "Model", "模型名称")) },
                            placeholder = { Text("deepseek-chat") },
                        )
                    }
                }
            }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SelectField(tr(settings, "From", "源语言"), translation.sourceLang, languages, Modifier.weight(1f), displayValue = { translationLanguageLabel(it, settings) }) { model.setTranslationLanguages(source = it) }
                IconButton(onClick = model::swapTranslationLanguages, modifier = Modifier.size(48.dp).align(Alignment.Bottom)) {
                    Icon(Icons.Default.SwapHoriz, tr(settings, "Swap languages", "交换语言"), tint = Teal)
                }
                SelectField(tr(settings, "To", "目标语言"), translation.targetLang, languages.filter { it != "Auto" }, Modifier.weight(1f), displayValue = { translationLanguageLabel(it, settings) }) { model.setTranslationLanguages(target = it) }
            }
        }
        item {
            OutlinedTextField(
                value = translation.inputText,
                onValueChange = model::setTranslationInput,
                modifier = Modifier.fillMaxWidth().height(150.dp),
                label = { Text(tr(settings, "Text to translate", "待翻译文本")) },
                placeholder = { Text(tr(settings, "Enter text here", "在此输入文本")) },
            )
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = model::translateText, enabled = !translation.isTranslating, modifier = Modifier.weight(1f).height(54.dp), colors = ButtonDefaults.buttonColors(containerColor = Teal)) {
                    Text(if (translation.isTranslating) tr(settings, "Translating...", "翻译中...") else tr(settings, "Translate", "翻译"))
                }
                OutlinedButton(onClick = model::clearTranslation, modifier = Modifier.weight(1f).height(54.dp)) {
                    Text(tr(settings, "Clear", "清空"))
                }
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(12.dp), elevation = CardDefaults.cardElevation(0.dp)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(tr(settings, "Result", "结果"), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    Text(
                        when {
                            translation.isTranslating -> tr(settings, "Translating via cloud API...", "正在通过云端 API 翻译...")
                            translation.outputText.isNotBlank() -> translation.outputText
                            translation.error != null -> translation.error
                            else -> tr(settings, "Translation output will appear here.", "翻译结果会显示在这里。")
                        },
                        color = if (translation.error != null) ComposeColor(0xFFE53935) else MaterialTheme.colorScheme.onSurface,
                        lineHeight = 22.sp,
                    )
                }
            }
        }
    }
}

@Composable
fun MeScreen(state: UiState, model: ClearScanViewModel) {
    SettingsScreen(state, model, embedded = true)
}

@Composable
fun SettingsScreen(state: UiState, model: ClearScanViewModel, embedded: Boolean = false) {
    val settings = state.settings
    var activeDialog by remember { mutableStateOf<String?>(null) }
    LazyColumn(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = if (embedded) 24.dp else 12.dp, bottom = 122.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { TopTitle(tr(settings, "Settings", "设置"), if (embedded) null else model::back) }
        item { SettingRow(label = tr(settings, "My Account", "我的账号"), icon = Icons.Default.AccountCircle, value = if (settings.loggedIn) settings.accountName else tr(settings, "Sign in", "登录"), onClick = model::openAccount) }
        item { SettingRow(label = tr(settings, "Language", "语言"), icon = Icons.Default.Language, value = if (settings.language == "Auto") tr(settings, "Auto (system)", "跟随系统") else settings.language, onClick = { activeDialog = "language" }) }
        item { SettingRow(label = tr(settings, "Theme", "主题"), icon = Icons.Default.Brightness6, value = when (settings.theme) { "System" -> tr(settings, "Follow system", "跟随系统"); "Dark" -> tr(settings, "Dark", "夜间"); else -> tr(settings, "Light", "日间") }, onClick = { activeDialog = "theme" }) }
        item { SettingRow(label = tr(settings, "Default Save Path", "默认保存路径"), icon = Icons.Default.Folder, value = settings.defaultSavePath, onClick = { activeDialog = "path" }) }
        item { SettingRow(label = tr(settings, "Default Filter", "默认滤镜"), icon = Icons.Default.Filter, value = filterLabel(settings, settings.defaultFilter), onClick = { activeDialog = "filter" }) }
        item { SettingRow(label = tr(settings, "Password Lock", "文件密码锁"), icon = Icons.Default.Lock, value = tr(settings, "${settings.passwordMap.size} protected", "已保护 ${settings.passwordMap.size} 个文件"), onClick = { activeDialog = "password" }) }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(8.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(tr(settings, "Updates", "应用更新"), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text("v${BuildConfig.VERSION_NAME}", color = Muted, fontSize = 13.sp)
                    SettingToggle(tr(settings, "Automatically check for updates", "自动检查更新"), settings.autoCheckUpdates) { model.updateSettings(settings.copy(autoCheckUpdates = it)) }
                    SettingToggle(tr(settings, "Automatically download updates", "自动下载更新"), settings.autoDownloadUpdates) { model.updateSettings(settings.copy(autoDownloadUpdates = it)) }
                    SettingToggle(tr(settings, "Download on Wi-Fi only", "仅使用 Wi-Fi 下载"), settings.wifiOnlyUpdates) { model.updateSettings(settings.copy(wifiOnlyUpdates = it)) }
                    state.updateInfo?.let { Text(tr(settings, "Version ${it.version} is available", "发现版本 ${it.version}"), color = Teal, fontWeight = FontWeight.SemiBold) }
                    if (state.updateDownload.status == "downloading") {
                        val progress = if (state.updateDownload.total > 0) state.updateDownload.downloaded.toFloat() / state.updateDownload.total else 0f
                        androidx.compose.material3.LinearProgressIndicator(progress = { progress.coerceIn(0f, 1f) }, Modifier.fillMaxWidth())
                    }
                    state.updateDownload.error?.let { Text(it, color = ComposeColor(0xFFE53935), fontSize = 12.sp) }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton({ model.checkForUpdates(false) }, enabled = !state.checkingUpdate, modifier = Modifier.weight(1f)) { Text(tr(settings, "Check now", "立即检查")) }
                        when (state.updateDownload.status) {
                            "ready" -> Button(model::installDownloadedUpdate, Modifier.weight(1f)) { Text(tr(settings, "Install", "安装")) }
                            else -> state.updateInfo?.let { info -> Button({ model.downloadUpdate(info) }, Modifier.weight(1f)) { Text(tr(settings, "Download", "下载")) } }
                        }
                    }
                }
            }
        }
        item { SettingRow(label = tr(settings, "App Logs", "运行日志"), icon = Icons.Default.Description, value = tr(settings, "View", "查看"), onClick = model::openLogs) }
        item { SettingRow(label = tr(settings, "Help & Feedback", "帮助与反馈"), icon = Icons.Default.Info, onClick = model::openHelp) }
        item { SettingRow(label = tr(settings, "About ClearScan", "关于 ClearScan"), icon = Icons.Default.Info, value = "v${BuildConfig.VERSION_NAME}", onClick = model::openAbout) }
        item {
            OutlinedButton(onClick = model::logout, modifier = Modifier.fillMaxWidth().height(52.dp), colors = ButtonDefaults.outlinedButtonColors(contentColor = ComposeColor(0xFFE53935)), shape = RoundedCornerShape(9.dp)) {
                Icon(Icons.AutoMirrored.Filled.Logout, null)
                Spacer(Modifier.width(10.dp))
                Text(tr(settings, "Log Out", "退出登录"), fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
    if (activeDialog == "language") ChoiceDialog(settings,
        title = tr(settings, "Language", "语言"),
        options = listOf("Auto", "English", "中文"),
        selected = settings.language,
        label = { option -> if (option == "Auto") tr(settings, "Auto (follow system)", "跟随系统") else option },
        onDismiss = { activeDialog = null },
        onSelect = { model.updateSettings(settings.copy(language = it)); activeDialog = null },
    )
    if (activeDialog == "theme") ChoiceDialog(settings,
        title = tr(settings, "Theme", "主题"),
        options = listOf("System", "Light", "Dark"),
        selected = settings.theme,
        label = { option -> when (option) { "System" -> tr(settings, "Follow system", "跟随系统"); "Light" -> tr(settings, "Light", "日间"); else -> tr(settings, "Dark", "夜间") } },
        onDismiss = { activeDialog = null },
        onSelect = { model.updateSettings(settings.copy(theme = it)); activeDialog = null },
    )
    if (activeDialog == "path") ChoiceDialog(settings,
        title = tr(settings, "Default Save Path", "默认保存路径"),
        options = listOf("Internal Storage", "Documents"),
        selected = settings.defaultSavePath,
        onDismiss = { activeDialog = null },
        onSelect = { model.updateSettings(settings.copy(defaultSavePath = it)); activeDialog = null },
    )
    if (activeDialog == "filter") ChoiceDialog(settings,
        title = tr(settings, "Default Filter", "默认滤镜"),
        options = DocumentFilters,
        selected = settings.defaultFilter,
        label = { option -> filterLabel(settings, option) },
        onDismiss = { activeDialog = null },
        onSelect = { model.updateSettings(settings.copy(defaultFilter = it)); activeDialog = null },
    )
    if (activeDialog == "password") PasswordManagerDialog(state, model, onDismiss = { activeDialog = null })
}

@Composable
fun ChoiceDialog(settings: AppSettings, title: String, options: List<String>, selected: String, onDismiss: () -> Unit, onSelect: (String) -> Unit, label: (String) -> String = { it }) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, color = MaterialTheme.colorScheme.onSurface) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                options.forEach { option ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(if (option == selected) Soft.copy(alpha = if (MaterialTheme.colorScheme.background == ComposeColor.White) 1f else .16f) else ComposeColor.Transparent).clickable { onSelect(option) }.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(label(option), Modifier.weight(1f), fontSize = 17.sp, fontWeight = if (option == selected) FontWeight.Bold else FontWeight.Normal, color = MaterialTheme.colorScheme.onSurface)
                        if (option == selected) Icon(Icons.Default.Check, null, tint = Teal)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(tr(settings, "Close", "关闭")) } },
    )
}

@Composable
fun PasswordManagerDialog(state: UiState, model: ClearScanViewModel, onDismiss: () -> Unit) {
    var target by remember { mutableStateOf<Document?>(null) }
    var password by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr(state.settings, "Password Lock", "文件密码锁")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(tr(state.settings, "Choose a document, then set or remove its local password.", "选择一个文件，然后设置或移除本地密码。"), color = Muted, fontSize = 14.sp)
                state.documents.take(6).forEach { document ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(if (target?.id == document.id) Soft else ComposeColor.Transparent).clickable { target = document; password = state.settings.passwordMap[document.id].orEmpty() }.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(if (state.settings.passwordMap.containsKey(document.id)) Icons.Default.Lock else Icons.Default.Description, null, tint = Teal)
                        Spacer(Modifier.width(10.dp))
                        Text(document.title, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                OutlinedTextField(password, { password = it }, Modifier.fillMaxWidth(), enabled = target != null, singleLine = true, placeholder = { Text(tr(state.settings, "Password", "密码")) })
            }
        },
        confirmButton = {
            TextButton(onClick = {
                target?.let { model.setDocumentPassword(it.id, password) }
                onDismiss()
            }, enabled = target != null) { Text(tr(state.settings, "Save", "保存")) }
        },
        dismissButton = {
            Row {
                TextButton(onClick = {
                    target?.let { model.setDocumentPassword(it.id, "") }
                    onDismiss()
                }, enabled = target != null) { Text(tr(state.settings, "Remove", "移除")) }
                TextButton(onClick = onDismiss) { Text(tr(state.settings, "Cancel", "取消")) }
            }
        },
    )
}

@Composable
fun AccountScreen(state: UiState, model: ClearScanViewModel) {
    var name by remember { mutableStateOf(state.settings.accountName.takeIf { it != "Guest" } ?: "") }
    var email by remember { mutableStateOf(state.settings.accountEmail) }
    LazyColumn(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding(), contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item { TopTitle(tr(state.settings, "My Account", "我的账号"), model::back) }
        item {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Box(Modifier.size(96.dp).clip(CircleShape).background(ComposeColor(0xFFE8FAF8)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.AccountCircle, null, tint = Teal, modifier = Modifier.size(72.dp))
                }
            }
        }
        item { OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text(tr(state.settings, "Name", "姓名")) }, singleLine = true) }
        item { OutlinedTextField(email, { email = it }, Modifier.fillMaxWidth(), label = { Text(tr(state.settings, "Email", "邮箱")) }, singleLine = true) }
        item {
            Button(onClick = { model.login(name, email) }, modifier = Modifier.fillMaxWidth().height(58.dp), colors = ButtonDefaults.buttonColors(containerColor = Teal), shape = RoundedCornerShape(10.dp)) {
                Text(if (state.settings.loggedIn) tr(state.settings, "Update Account", "更新账号") else tr(state.settings, "Sign In", "登录"), fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        }
        if (state.settings.loggedIn) {
            item {
                OutlinedButton(onClick = model::logout, modifier = Modifier.fillMaxWidth().height(58.dp), colors = ButtonDefaults.outlinedButtonColors(contentColor = ComposeColor(0xFFE53935))) {
                    Text(tr(state.settings, "Log Out", "退出登录"))
                }
            }
        }
    }
}

@Composable
fun HelpScreen(state: UiState, model: ClearScanViewModel) {
    LazyColumn(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding(), contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { TopTitle(tr(state.settings, "Help", "帮助"), model::back) }
        item { HelpCard(tr(state.settings, "How do I scan multiple pages?", "如何扫描多页？"), tr(state.settings, "Keep taking photos, tap the right Done button, then confirm each page crop and save them together.", "连续拍摄照片，点击右侧完成按钮，再逐页确认裁剪并统一保存。")) }
        item { HelpCard(tr(state.settings, "How does smart alignment work?", "智能对齐如何工作？"), tr(state.settings, "ClearScan detects page edges locally. You can always drag any corner before continuing.", "ClearScan 会在本机识别页面边缘，你仍可在继续前拖动任意角点。")) }
        item { HelpCard(tr(state.settings, "How do PDF tools work?", "PDF 工具如何使用？"), tr(state.settings, "Select files first. Every operation saves a new document and keeps the original.", "先选择文件；所有操作都会生成新文档并保留原文件。")) }
        item { HelpCard(tr(state.settings, "Feedback", "问题反馈"), tr(state.settings, "Export App Logs and include your phone model, Android version, and reproduction steps.", "请导出运行日志，并附上手机型号、Android 版本和复现步骤。")) }
    }
}

@Composable
fun HelpCard(title: String, body: String) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(12.dp), elevation = CardDefaults.cardElevation(0.dp)) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            Text(body, color = Muted, fontSize = 15.sp, lineHeight = 22.sp)
        }
    }
}

@Composable
fun AboutScreen(state: UiState, model: ClearScanViewModel) {
    val settings = state.settings
    val context = LocalContext.current
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().padding(horizontal = 24.dp)) {
        TopTitle(tr(settings, "About", "关于"), model::back)
        Spacer(Modifier.height(80.dp))
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Box(Modifier.size(156.dp).clip(RoundedCornerShape(34.dp)).background(ComposeColor(0xFFE8FAF8)), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.DocumentScanner, null, tint = Teal, modifier = Modifier.size(92.dp))
            }
        }
        Spacer(Modifier.height(28.dp))
        Text("ClearScan", Modifier.fillMaxWidth(), textAlign = TextAlign.Center, fontSize = 40.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onSurface)
        Text(tr(settings, "Version ${BuildConfig.VERSION_NAME}", "版本 ${BuildConfig.VERSION_NAME}"), Modifier.fillMaxWidth(), textAlign = TextAlign.Center, color = Muted, fontSize = 18.sp)
        Spacer(Modifier.height(36.dp))
        Text(tr(settings, "Scan Everything, Clearly", "清晰扫描一切"), Modifier.fillMaxWidth(), textAlign = TextAlign.Center, color = TealDark, fontSize = 23.sp, fontWeight = FontWeight.Bold)
        Text(tr(settings, "ClearScan helps you scan, save and\nmanage your documents easily.", "ClearScan 帮你轻松扫描、保存\n和管理所有文档。"), Modifier.fillMaxWidth().padding(top = 18.dp), textAlign = TextAlign.Center, color = Muted, fontSize = 18.sp, lineHeight = 27.sp)
        Spacer(Modifier.height(58.dp))
        OptionRow(tr(settings, "Privacy Policy", "隐私政策"), Icons.Default.Lock) { model.openLegal("Privacy Policy") }
        OptionRow(tr(settings, "Terms of Use", "使用条款"), Icons.Default.Description) { model.openLegal("Terms of Use") }
        OptionRow(tr(settings, "Source Code (GitHub)", "源代码（GitHub）"), Icons.Default.Language) {
            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/ant-cave/ClearScan"))) }
        }
        OptionRow(tr(settings, "Open Source License", "开源许可证"), Icons.Default.Info) { model.openLegal("Open Source License") }
        Spacer(Modifier.weight(1f))
        Text(tr(settings, "© 2026 ClearScan · AGPL-3.0\nBased on ClearScan by SuiYueMengHen (MIT)", "© 2026 ClearScan · AGPL-3.0\n基于 SuiYueMengHen 的 ClearScan（MIT）"), Modifier.fillMaxWidth().padding(bottom = 34.dp), textAlign = TextAlign.Center, color = Muted, fontSize = 14.sp, lineHeight = 21.sp)
    }
}

@Composable
fun LegalScreen(state: UiState, model: ClearScanViewModel) {
    val title = state.legalTitle
    val privacy = title == "Privacy Policy"
    val license = title == "Open Source License"
    val settings = state.settings
    LazyColumn(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding(), contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { TopTitle(when { privacy -> tr(settings, "Privacy Policy", "隐私政策"); license -> tr(settings, "Open Source License", "开源许可证"); else -> tr(settings, "Terms of Use", "使用条款") }, model::back) }
        item {
            Text(
                when {
                    privacy -> tr(settings, "ClearScan stores scans, passwords, account profile data, and settings locally on this device. Files are not uploaded to a server in this clean local edition. Camera and media permissions are used only for scanning, importing, exporting, sharing, and printing documents. You can delete documents from the Docs page and clear local account state with Log Out. Cloud translation, if used, sends the text you submit to the third-party API you configure, and your API key stays on this device.", "ClearScan 会将扫描文件、密码、账号资料和设置保存在本设备本地。纯净本地版不会把文件上传到服务器。相机和媒体权限仅用于扫描、导入、导出、分享和打印文档。你可以在文档页删除文件，也可以通过退出登录清除本地账号状态。若使用云端翻译，所提交的文本将发送到你自行配置的第三方 API，API 密钥仅保存在本设备。")
                    license -> tr(settings, "ClearScan is free software: you can redistribute it and/or modify it under the terms of the GNU Affero General Public License v3.0 (AGPL-3.0-or-later) as published by the Free Software Foundation. This project is based on ClearScan by SuiYueMengHen, originally released under the MIT License; the upstream MIT notice is preserved in the LICENSE file. The full license text is available at https://www.gnu.org/licenses/agpl-3.0.html and in the source repository: https://github.com/ant-cave/ClearScan", "ClearScan 是自由软件：你可以依据自由软件基金会发布的 GNU Affero 通用公共许可证 v3.0（AGPL-3.0-or-later）条款重新分发或修改它。本项目基于 SuiYueMengHen 的 ClearScan（原始许可证为 MIT），上游 MIT 声明保留在 LICENSE 文件中。完整许可证文本见 https://www.gnu.org/licenses/agpl-3.0.html 及源代码仓库：https://github.com/ant-cave/ClearScan")
                    else -> tr(settings, "ClearScan is provided as a local document scanning tool. You are responsible for the content you scan, export, share, or print. PDF tools create new local files and do not modify originals unless you delete them. Password protection is local to this app and should not be treated as enterprise encryption. By using the app, you agree to use it lawfully and keep backups of important documents.", "ClearScan 是一个本地文档扫描工具。你需要对自己扫描、导出、分享或打印的内容负责。PDF 工具会生成新的本地文件，不会覆盖原文件，除非你主动删除。文件密码保护仅在本应用内本地生效，不应视为企业级加密。使用本应用即表示你同意合法使用，并自行备份重要文档。")
                },
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 16.sp,
                lineHeight = 25.sp,
            )
        }
    }
}

@Composable
fun AppLogsScreen(state: UiState, model: ClearScanViewModel) {
    val settings = state.settings
    val context = LocalContext.current
    val exportTxt = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri != null) runCatching { LogExporter.exportTxt(context, uri, state.logText) }
    }
    val exportDocx = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.wordprocessingml.document")) { uri ->
        if (uri != null) runCatching { LogExporter.exportDocx(context, uri, state.logText) }
    }
    LaunchedEffect(Unit) { model.refreshLogs() }
    LazyColumn(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding(),
        contentPadding = PaddingValues(22.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { TopBar(tr(settings, "App Logs", "运行日志"), onBack = model::back, action = tr(settings, "Refresh", "刷新"), onAction = model::refreshLogs) }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = { copyLogsToClipboard(context, state.logText) }, modifier = Modifier.weight(1f).height(48.dp)) {
                    Text(tr(settings, "Copy", "复制"))
                }
                OutlinedButton(onClick = { shareLogFile(context) }, modifier = Modifier.weight(1f).height(48.dp)) {
                    Text(tr(settings, "Share", "分享"))
                }
                OutlinedButton(onClick = model::clearLogs, modifier = Modifier.weight(1f).height(48.dp), colors = ButtonDefaults.outlinedButtonColors(contentColor = ComposeColor(0xFFE53935))) {
                    Text(tr(settings, "Clear", "清空"))
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton({ exportTxt.launch("ClearScan-log-${System.currentTimeMillis()}.txt") }, Modifier.weight(1f)) { Text(tr(settings, "Export TXT", "导出 TXT")) }
                OutlinedButton({ exportDocx.launch("ClearScan-log-${System.currentTimeMillis()}.docx") }, Modifier.weight(1f)) { Text(tr(settings, "Export Word", "导出 Word")) }
            }
        }
        item {
            Text(
                tr(settings, "Path: ${AppLogger.file(context).absolutePath}", "路径：${AppLogger.file(context).absolutePath}"),
                color = Muted,
                fontSize = 12.sp,
                lineHeight = 17.sp,
            )
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = if (isDarkTheme(settings)) ComposeColor(0xFF111820) else ComposeColor(0xFFF8FAFC)), shape = RoundedCornerShape(12.dp), elevation = CardDefaults.cardElevation(0.dp)) {
                Text(
                    state.logText.ifBlank { tr(settings, "No logs yet.", "暂无日志。") },
                    Modifier.fillMaxWidth().padding(14.dp),
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                )
            }
        }
    }
}

@Composable
fun TopBar(title: String, onBack: () -> Unit, action: String? = null, onAction: (() -> Unit)? = null, dark: Boolean = false) {
    Row(Modifier.fillMaxWidth().height(60.dp).padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = if (dark) ComposeColor.White else MaterialTheme.colorScheme.onSurface) }
        Text(title, Modifier.weight(1f), textAlign = TextAlign.Center, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = if (dark) ComposeColor.White else MaterialTheme.colorScheme.onSurface)
        TextButton(onClick = { onAction?.invoke() }, enabled = action != null) { Text(action ?: "", color = Teal, fontSize = 16.sp, fontWeight = FontWeight.Bold) }
    }
}

@Composable
fun TopTitle(title: String, onBack: (() -> Unit)?) {
    Row(Modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically) {
        if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = MaterialTheme.colorScheme.onSurface) } else Spacer(Modifier.width(8.dp))
        Text(title, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
fun OptionRow(label: String, icon: ImageVector, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(60.dp).clickable(onClick = onClick).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, label, tint = Teal, modifier = Modifier.size(26.dp))
        Spacer(Modifier.width(14.dp))
        Text(label, Modifier.weight(1f), fontSize = 18.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
        Icon(Icons.Default.ArrowForwardIos, null, tint = Muted, modifier = Modifier.size(18.dp))
    }
}

@Composable
fun SettingRow(label: String, icon: ImageVector, value: String? = null, onClick: () -> Unit = {}, trailing: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().height(60.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surface).border(1.dp, ComposeColor(0xFFECEFF2), RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, label, tint = Teal, modifier = Modifier.size(26.dp))
        Spacer(Modifier.width(14.dp))
        Text(label, Modifier.weight(1f), fontSize = 17.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
        if (trailing != null) trailing() else {
            if (value != null) Text(value, color = Muted, fontSize = 14.sp)
            Spacer(Modifier.width(8.dp))
            Icon(Icons.Default.ArrowForwardIos, null, tint = Muted, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
fun EmptyState(title: String, body: String) {
    Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Default.DocumentScanner, null, tint = Muted, modifier = Modifier.size(48.dp))
        Text(title, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurface)
        Text(body, color = Muted, textAlign = TextAlign.Center)
    }
}

@Composable
fun ScanBitmap(bitmap: Bitmap?, modifier: Modifier = Modifier) {
    if (bitmap != null) {
        val displayBitmap = remember(bitmap) { ImageProcessor.previewBitmap(bitmap, 3072) ?: bitmap }
        Image(displayBitmap.asImageBitmap(), null, modifier.clip(RoundedCornerShape(4.dp)).background(ComposeColor.White), contentScale = ContentScale.Fit)
    } else {
        Box(modifier.clip(RoundedCornerShape(4.dp)).background(ComposeColor.White), contentAlignment = Alignment.Center) {
            DocumentOnTable()
        }
    }
}

@VisibleForTesting
fun detectTranslationLanguage(input: String): String {
    return if (input.any { it.code in 0x3400..0x9FFF }) "Chinese" else "English"
}

private val translationLanguageChineseNames = mapOf(
    "Auto" to "自动检测",
    "Chinese" to "中文",
    "English" to "英语",
    "Japanese" to "日语",
    "Korean" to "韩语",
    "French" to "法语",
    "German" to "德语",
    "Spanish" to "西班牙语",
    "Portuguese" to "葡萄牙语",
    "Italian" to "意大利语",
    "Russian" to "俄语",
    "Arabic" to "阿拉伯语",
    "Thai" to "泰语",
    "Vietnamese" to "越南语",
    "Indonesian" to "印度尼西亚语",
    "Malay" to "马来语",
    "Turkish" to "土耳其语",
    "Polish" to "波兰语",
    "Dutch" to "荷兰语",
    "Czech" to "捷克语",
    "Ukrainian" to "乌克兰语",
    "Hindi" to "印地语",
)

fun translationLanguageLabel(language: String, settings: AppSettings): String {
    return if (settings.language == "中文") translationLanguageChineseNames[language] ?: language else language
}

@VisibleForTesting
fun splitTranslationText(input: String, maxChars: Int = 800): List<String> {
    val remaining = StringBuilder(input.trim())
    if (remaining.isEmpty()) return emptyList()
    val chunks = mutableListOf<String>()
    val boundaries = charArrayOf('\n', '。', '！', '？', '.', '!', '?', ';', '；', ' ')
    while (remaining.isNotEmpty()) {
        if (remaining.length <= maxChars) {
            remaining.toString().trim().takeIf { it.isNotEmpty() }?.let(chunks::add)
            break
        }
        val candidate = remaining.substring(0, maxChars)
        val boundary = candidate.lastIndexOfAny(boundaries)
        val end = if (boundary >= maxChars / 2) boundary + 1 else maxChars
        candidate.substring(0, end).trim().takeIf { it.isNotEmpty() }?.let(chunks::add)
        remaining.delete(0, end)
        while (remaining.isNotEmpty() && remaining.first().isWhitespace()) remaining.deleteCharAt(0)
    }
    return chunks
}

/** User-tunable parameters for the OpenCV-accelerated filters. Defaults match the original look. */
data class FilterParams(
    /** Adaptive-threshold bias for B&W / Ink: lower picks up fainter strokes, higher keeps them thin and clean (2..30). */
    val threshold: Float = 12f,
    /** Multiplier applied to each filter's sharpen amount (0..1.6). */
    val sharpenScale: Float = 1f,
    /** White Paper lift gamma: lower lifts shadows more (0.72..1.0). */
    val paperLift: Float = .88f,
    /** Median denoise kernel before B&W binarization: 1 = off, 3 = standard, 5 = aggressive (odd only). */
    val denoise: Int = 3,
    /** Strength of the division-normalization "smart" filters (Smart Gray / Magic Color): 0 = original, 1 = full normalization. */
    val smartStrength: Float = 1f,
)

object ImageProcessor {
    fun sampleDocumentBitmap(title: String = "AGREEMENT"): Bitmap {
        val bitmap = Bitmap.createBitmap(900, 1250, Bitmap.Config.ARGB_8888)
        val canvas = AndroidCanvas(bitmap)
        canvas.drawColor(Color.WHITE)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = Color.rgb(20, 24, 32)
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = 46f
        paint.isFakeBoldText = true
        canvas.drawText(title.uppercase(), 450f, 120f, paint)
        paint.textAlign = Paint.Align.LEFT
        paint.textSize = 27f
        repeat(5) { i ->
            val top = 210f + i * 150f
            paint.isFakeBoldText = true
            canvas.drawText("${i + 1}. Terms of Agreement", 110f, top, paint)
            paint.isFakeBoldText = false
            paint.textSize = 23f
            canvas.drawText("This agreement shall commence on the effective date and continue", 110f, top + 42f, paint)
            canvas.drawText("for a period of twelve months unless terminated earlier.", 110f, top + 78f, paint)
            paint.textSize = 27f
        }
        paint.strokeWidth = 3f
        canvas.drawLine(190f, 1030f, 530f, 1030f, paint)
        canvas.drawLine(145f, 1110f, 490f, 1110f, paint)
        paint.textSize = 25f
        canvas.drawText("Signature:", 110f, 1022f, paint)
        canvas.drawText("Date:", 110f, 1102f, paint)
        return bitmap
    }

    fun readBitmap(path: String): Bitmap? = runCatching { BitmapFactoryCompat.decode(path) }.getOrNull()

    fun readBitmap(path: String, maxDimension: Int): Bitmap? = runCatching {
        BitmapFactoryCompat.decodeSampled(path, maxDimension)
    }.onFailure { AppLogger.e("Image", "Failed to decode sampled bitmap: $path", it) }.getOrNull()

    fun renderPdfFirstPage(path: String): Bitmap? = renderPdfPages(path, maxPages = 1).firstOrNull()

    fun renderPdfPages(path: String, maxPages: Int = Int.MAX_VALUE): List<Bitmap> = runCatching {
        val file = File(path)
        if (!file.exists()) return@runCatching emptyList()
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            android.graphics.pdf.PdfRenderer(descriptor).use { renderer ->
                val pages = mutableListOf<Bitmap>()
                val count = minOf(renderer.pageCount, maxPages)
                for (index in 0 until count) {
                    renderer.openPage(index).use { page ->
                        val scale = 2
                        val bitmap = Bitmap.createBitmap(page.width * scale, page.height * scale, Bitmap.Config.ARGB_8888)
                        val canvas = AndroidCanvas(bitmap)
                        canvas.drawColor(Color.WHITE)
                        page.render(bitmap, null, null, android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        pages += bitmap
                    }
                }
                pages
            }
        }
    }.getOrDefault(emptyList())

    fun documentPages(document: Document): List<Bitmap> {
        return if (document.type == "PDF") {
            renderPdfPages(document.exportPath)
        } else {
            listOfNotNull(readBitmap(document.exportPath) ?: readBitmap(document.thumbnailPath))
        }
    }

    fun downsampleForPdf(bitmap: Bitmap, level: String): Bitmap {
        val maxWidth = when (level) {
            "Low" -> 820
            "High" -> 1600
            else -> 1200
        }
        if (bitmap.width <= maxWidth) return bitmap
        val ratio = maxWidth.toFloat() / bitmap.width.toFloat()
        val height = (bitmap.height * ratio).roundToInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, maxWidth, height, true)
    }

    fun decodeCameraBitmap(path: String, maxDimension: Int = 3072): Bitmap? = runCatching {
        val orientation = ExifInterface(path).getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL,
        )
        val bitmap = BitmapFactoryCompat.decodeSampled(path, maxDimension) ?: return@runCatching null
        applyExifOrientation(bitmap, orientation)
    }.onFailure { AppLogger.e("Image", "Failed to decode camera bitmap: $path", it) }.getOrNull()

    fun decodeUriBitmap(context: Context, uri: Uri, maxDimension: Int = 2048): Bitmap? = runCatching {
        val resolver = context.contentResolver
        val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { android.graphics.BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null
        val options = android.graphics.BitmapFactory.Options().apply {
            inSampleSize = bitmapSampleSize(bounds.outWidth, bounds.outHeight, maxDimension)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val orientation = resolver.openInputStream(uri)?.use {
            ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        } ?: ExifInterface.ORIENTATION_NORMAL
        val bitmap = resolver.openInputStream(uri)?.use {
            android.graphics.BitmapFactory.decodeStream(it, null, options)
        } ?: return@runCatching null
        applyExifOrientation(bitmap, orientation)
    }.onFailure { AppLogger.e("Image", "Failed to decode imported bitmap: $uri", it) }.getOrNull()

    fun optimizeCapturedPhoto(
        input: File,
        output: File,
        maxDimension: Int = 4096,
        targetBytes: Long = 8_000_000L,
    ): File? = runCatching {
        val decoded = decodeCameraBitmap(input.absolutePath, maxDimension) ?: return@runCatching null
        var encoded = decoded
        for (quality in listOf(92, 86, 80)) {
            writeJpeg(encoded, output, quality)
            if (output.length() <= targetBytes) break
        }
        if (output.length() > targetBytes && maxOf(decoded.width, decoded.height) > 3200) {
            encoded = previewBitmap(decoded, 3200) ?: decoded
            writeJpeg(encoded, output, 84)
        }
        if (!output.exists() || output.length() <= 0L) return@runCatching null
        val originalBytes = input.length()
        if (input.absolutePath != output.absolutePath) input.delete()
        AppLogger.i(
            "Camera",
            "Capture optimized ${decoded.width}x${decoded.height}, $originalBytes -> ${output.length()} bytes",
        )
        output
    }.onFailure { AppLogger.e("Camera", "Capture compression failed", it) }.getOrNull()

    private fun applyExifOrientation(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.setScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.setRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.setRotate(-90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(-90f)
            else -> return bitmap
        }
        val oriented = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (oriented !== bitmap) bitmap.recycle()
        return oriented
    }

    fun writeJpeg(bitmap: Bitmap, file: File, quality: String) {
        val percent = when (quality) { "Low" -> 55; "Medium" -> 74; else -> 88 }
        writeJpeg(bitmap, file, percent)
    }

    fun writeJpeg(bitmap: Bitmap, file: File, quality: Int) {
        file.parentFile?.mkdirs()
        FileOutputStream(file).use { out ->
            check(bitmap.compress(Bitmap.CompressFormat.JPEG, quality.coerceIn(40, 95), out)) { "JPEG encoding failed" }
        }
    }

    fun writePng(bitmap: Bitmap, file: File) {
        file.parentFile?.mkdirs()
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
    }

    @Suppress("DEPRECATION")
    fun writeWebp(bitmap: Bitmap, file: File, quality: Int = 88) {
        file.parentFile?.mkdirs()
        FileOutputStream(file).use { out ->
            val format = if (android.os.Build.VERSION.SDK_INT >= 30) Bitmap.CompressFormat.WEBP_LOSSY else Bitmap.CompressFormat.WEBP
            check(bitmap.compress(format, quality.coerceIn(40, 95), out)) { "WebP encoding failed" }
        }
    }

    fun writeBmp(bitmap: Bitmap, file: File) {
        file.parentFile?.mkdirs()
        val width = bitmap.width
        val height = bitmap.height
        val rowSize = ((width * 3 + 3) / 4) * 4
        val pixelBytes = rowSize * height
        FileOutputStream(file).buffered().use { out ->
            fun writeLe16(value: Int) {
                out.write(value and 0xFF)
                out.write((value ushr 8) and 0xFF)
            }
            fun writeLe32(value: Int) {
                out.write(value and 0xFF)
                out.write((value ushr 8) and 0xFF)
                out.write((value ushr 16) and 0xFF)
                out.write((value ushr 24) and 0xFF)
            }
            out.write('B'.code)
            out.write('M'.code)
            writeLe32(54 + pixelBytes)
            writeLe32(0)
            writeLe32(54)
            writeLe32(40)
            writeLe32(width)
            writeLe32(height)
            writeLe16(1)
            writeLe16(24)
            writeLe32(0)
            writeLe32(pixelBytes)
            writeLe32(2835)
            writeLe32(2835)
            writeLe32(0)
            writeLe32(0)
            val pixels = IntArray(width)
            val row = ByteArray(rowSize)
            for (y in height - 1 downTo 0) {
                bitmap.getPixels(pixels, 0, width, 0, y, width, 1)
                var offset = 0
                for (color in pixels) {
                    row[offset++] = Color.blue(color).toByte()
                    row[offset++] = Color.green(color).toByte()
                    row[offset++] = Color.red(color).toByte()
                }
                while (offset < rowSize) row[offset++] = 0
                out.write(row)
            }
        }
    }

    fun writePdf(bitmap: Bitmap, file: File) {
        writePdf(listOf(bitmap), file)
    }

    fun writePdf(bitmaps: List<Bitmap>, file: File) {
        file.parentFile?.mkdirs()
        val pdf = PdfDocument()
        bitmaps.forEachIndexed { index, bitmap ->
            val pageInfo = PdfDocument.PageInfo.Builder(595, 842, index + 1).create()
            val page = pdf.startPage(pageInfo)
            val rect = RectF(32f, 32f, 563f, 810f)
            page.canvas.drawColor(Color.WHITE)
            page.canvas.drawBitmap(bitmap, null, rect, Paint(Paint.ANTI_ALIAS_FLAG))
            pdf.finishPage(page)
        }
        FileOutputStream(file).use { pdf.writeTo(it) }
        pdf.close()
    }

    fun rotate(bitmap: Bitmap, clockwise: Boolean = true): Bitmap {
        val out = Bitmap.createBitmap(bitmap.height, bitmap.width, Bitmap.Config.ARGB_8888)
        val canvas = AndroidCanvas(out)
        canvas.rotate(if (clockwise) 90f else -90f, out.width / 2f, out.height / 2f)
        canvas.drawBitmap(bitmap, (out.width - bitmap.width) / 2f, (out.height - bitmap.height) / 2f, Paint(Paint.ANTI_ALIAS_FLAG))
        return out
    }

    /** Replays cumulative 90° rotations; negative and multi-turn values are normalized. */
    fun rotateQuarters(bitmap: Bitmap, quarters: Int): Bitmap {
        var out = bitmap
        repeat(((quarters % 4) + 4) % 4) { out = rotate(out, clockwise = true) }
        return out
    }

    fun perspectiveCrop(bitmap: Bitmap, normalizedPoints: List<Offset>): Bitmap {
        if (normalizedPoints.size < 4) return bitmap
        val src = normalizedPoints.map {
            Offset(
                it.x.coerceIn(0f, 1f) * bitmap.width,
                it.y.coerceIn(0f, 1f) * bitmap.height,
            )
        }
        val top = distance(src[0], src[1])
        val right = distance(src[1], src[2])
        val bottom = distance(src[2], src[3])
        val left = distance(src[3], src[0])
        val width = max(32f, max(top, bottom)).roundToInt()
        val height = max(32f, max(left, right)).roundToInt()
        val matrix = Matrix()
        val srcArray = floatArrayOf(
            src[0].x, src[0].y,
            src[1].x, src[1].y,
            src[2].x, src[2].y,
            src[3].x, src[3].y,
        )
        val dstArray = floatArrayOf(
            0f, 0f,
            width.toFloat(), 0f,
            width.toFloat(), height.toFloat(),
            0f, height.toFloat(),
        )
        matrix.setPolyToPoly(srcArray, 0, dstArray, 0, 4)
        val out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = AndroidCanvas(out)
        canvas.drawColor(Color.WHITE)
        canvas.drawBitmap(bitmap, matrix, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        return out
    }

    fun enhanceDocument(bitmap: Bitmap): Bitmap {
        val balanced = grayWorldWhiteBalance(bitmap)
        val adjusted = adjust(balanced, .025f, 1.14f, 1.0f) ?: balanced
        return sharpen(adjusted, amount = .72f)
    }

    /** Runs [block] with an RGBA [Mat] of [bitmap]; returns null when OpenCV is unavailable. */
    private fun <T> withOpenCvMat(bitmap: Bitmap, block: (Mat) -> T?): T? {
        if (!DocumentEdgeDetector.openCvAvailable || bitmap.width < 3 || bitmap.height < 3) return null
        val source = Mat()
        return try {
            Utils.bitmapToMat(bitmap, source)
            block(source)
        } catch (_: Throwable) {
            null
        } finally {
            source.release()
        }
    }

    /** Converts an RGBA [Mat] back to a bitmap; returns null on failure. */
    private fun matToBitmap(mat: Mat): Bitmap? = runCatching {
        Bitmap.createBitmap(mat.cols(), mat.rows(), Bitmap.Config.ARGB_8888).also { Utils.matToBitmap(mat, it) }
    }.getOrNull()

    private fun grayWorldWhiteBalance(bitmap: Bitmap): Bitmap {
        grayWorldWhiteBalanceOpenCv(bitmap)?.let { return it }
        return grayWorldWhiteBalanceFallback(bitmap)
    }

    /** Gray-world statistics via in-range mask + Core.mean, then the same clamped channel gains. */
    private fun grayWorldWhiteBalanceOpenCv(bitmap: Bitmap): Bitmap? = withOpenCvMat(bitmap) { source ->
        val channels = mutableListOf<Mat>()
        val grayStats = Mat()
        val mask = Mat()
        val maxPlane = Mat()
        val minPlane = Mat()
        val output = Mat()
        try {
            Core.split(source, channels)
            Core.max(channels[0], channels[1], maxPlane)
            Core.max(maxPlane, channels[2], maxPlane)
            Core.min(channels[0], channels[1], minPlane)
            Core.min(minPlane, channels[2], minPlane)
            Core.subtract(maxPlane, minPlane, grayStats)
            // Keep only near-gray pixels: channel spread < 80 and brightness > 48.
            Core.inRange(grayStats, Scalar(0.0), Scalar(80.0), mask)
            val bright = Mat()
            Core.inRange(maxPlane, Scalar(48.0), Scalar(255.0), bright)
            Core.bitwise_and(mask, bright, mask)
            bright.release()
            val samples = Core.countNonZero(mask)
            if (samples <= 0) return@withOpenCvMat null
            val averages = channels.map { plane -> Core.mean(plane, mask).`val`[0] }
            val gray = averages.average()
            fun gain(average: Double) = (gray / average).coerceIn(.82, 1.18)
            val transform = Mat.zeros(4, 4, CvType.CV_32FC1)
            transform.put(0, 0, gain(averages[0]))
            transform.put(1, 1, gain(averages[1]))
            transform.put(2, 2, gain(averages[2]))
            transform.put(3, 3, 1.0)
            Core.transform(source, output, transform)
            transform.release()
            matToBitmap(output)
        } finally {
            channels.forEach(Mat::release)
            listOf(grayStats, mask, maxPlane, minPlane, output).forEach(Mat::release)
        }
    }

    private fun grayWorldWhiteBalanceFallback(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val step = max(1, minOf(width, height) / 360)
        var rSum = 0L
        var gSum = 0L
        var bSum = 0L
        var count = 0L
        var y = 0
        while (y < height) {
            var x = 0
            while (x < width) {
                val color = bitmap.getPixel(x, y)
                val r = Color.red(color)
                val g = Color.green(color)
                val b = Color.blue(color)
                val maxChannel = maxOf(r, g, b)
                val minChannel = minOf(r, g, b)
                if (maxChannel - minChannel < 80 && maxChannel > 48) {
                    rSum += r
                    gSum += g
                    bSum += b
                    count++
                }
                x += step
            }
            y += step
        }
        if (count == 0L) return bitmap
        val rAvg = rSum.toFloat() / count
        val gAvg = gSum.toFloat() / count
        val bAvg = bSum.toFloat() / count
        val gray = (rAvg + gAvg + bAvg) / 3f
        val rScale = (gray / rAvg).coerceIn(.82f, 1.18f)
        val gScale = (gray / gAvg).coerceIn(.82f, 1.18f)
        val bScale = (gray / bAvg).coerceIn(.82f, 1.18f)
        val out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = AndroidCanvas(out)
        val matrix = ColorMatrix(floatArrayOf(
            rScale, 0f, 0f, 0f, 0f,
            0f, gScale, 0f, 0f, 0f,
            0f, 0f, bScale, 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
        ))
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { colorFilter = ColorMatrixColorFilter(matrix) }
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        return out
    }

    fun previewBitmap(bitmap: Bitmap?, maxLongSide: Int): Bitmap? {
        if (bitmap == null) return null
        val longSide = max(bitmap.width, bitmap.height)
        if (longSide <= maxLongSide) return bitmap
        val scale = maxLongSide.toFloat() / longSide.toFloat()
        return Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).roundToInt().coerceAtLeast(1), (bitmap.height * scale).roundToInt().coerceAtLeast(1), true)
    }

    fun sharpen(bitmap: Bitmap, amount: Float = 1f): Bitmap {
        sharpenOpenCv(bitmap, amount)?.let { return it }
        return sharpenFallback(bitmap, amount)
    }

    /** Unsharp mask: out = src + (src - blur) * amount, computed with native OpenCV. */
    private fun sharpenOpenCv(bitmap: Bitmap, amount: Float): Bitmap? = withOpenCvMat(bitmap) { source ->
        val blurred = Mat()
        val output = Mat()
        try {
            Imgproc.GaussianBlur(source, blurred, CvSize(3.0, 3.0), 0.0)
            Core.addWeighted(source, 1.0 + amount.toDouble(), blurred, -amount.toDouble(), 0.0, output)
            matToBitmap(output)
        } finally {
            blurred.release()
            output.release()
        }
    }

    private fun sharpenFallback(bitmap: Bitmap, amount: Float = 1f): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        if (width < 3 || height < 3) return bitmap
        val out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(width * height)
        val result = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        fun clamp(value: Int) = value.coerceIn(0, 255)
        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                val center = pixels[y * width + x]
                val left = pixels[y * width + x - 1]
                val right = pixels[y * width + x + 1]
                val top = pixels[(y - 1) * width + x]
                val bottom = pixels[(y + 1) * width + x]
                val blurR = (Color.red(left) + Color.red(right) + Color.red(top) + Color.red(bottom)) / 4
                val blurG = (Color.green(left) + Color.green(right) + Color.green(top) + Color.green(bottom)) / 4
                val blurB = (Color.blue(left) + Color.blue(right) + Color.blue(top) + Color.blue(bottom)) / 4
                val r = clamp((Color.red(center) + (Color.red(center) - blurR) * amount).roundToInt())
                val g = clamp((Color.green(center) + (Color.green(center) - blurG) * amount).roundToInt())
                val b = clamp((Color.blue(center) + (Color.blue(center) - blurB) * amount).roundToInt())
                result[y * width + x] = Color.rgb(r, g, b)
            }
        }
        for (x in 0 until width) {
            result[x] = pixels[x]
            result[(height - 1) * width + x] = pixels[(height - 1) * width + x]
        }
        for (y in 0 until height) {
            result[y * width] = pixels[y * width]
            result[y * width + width - 1] = pixels[y * width + width - 1]
        }
        out.setPixels(result, 0, width, 0, 0, width, height)
        return out
    }

    @VisibleForTesting
    fun perspectiveOutputSize(bitmapWidth: Int, bitmapHeight: Int, normalizedPoints: List<Offset>): Pair<Int, Int> {
        if (normalizedPoints.size < 4) return bitmapWidth to bitmapHeight
        val src = normalizedPoints.map { Offset(it.x * bitmapWidth, it.y * bitmapHeight) }
        val top = distance(src[0], src[1])
        val right = distance(src[1], src[2])
        val bottom = distance(src[2], src[3])
        val left = distance(src[3], src[0])
        return max(32f, max(top, bottom)).roundToInt() to max(32f, max(left, right)).roundToInt()
    }

    private fun distance(a: Offset, b: Offset): Float = hypot(a.x - b.x, a.y - b.y)

    fun filter(bitmap: Bitmap?, filter: String, params: FilterParams = FilterParams()): Bitmap? {
        if (bitmap == null) return null
        return when (filter) {
            "Smart Gray" -> smartEnhance(bitmap, params, color = false)
            "Magic Color" -> smartEnhance(bitmap, params, color = true)
            "B&W" -> blackAndWhite(bitmap, params)
            "Ink" -> sharpen(blackAndWhite(bitmap, params), .35f * params.sharpenScale)
            "White Paper" -> whitePaper(bitmap, params)
            // Unknown names (e.g. legacy pages saved with removed presets) fall back to the original.
            else -> bitmap
        }
    }

    /**
     * Division-normalization enhancement, the pipeline behind classic scanner apps: a
     * large-kernel Gaussian blur estimates the per-pixel background illumination, and
     * dividing by it flattens uneven lighting, yellow paper, and soft shadows to a clean
     * page while ink keeps its depth. Text is sparse and darker than its surroundings,
     * so the background estimate at ink pixels stays close to paper white — the division
     * therefore whitens paper but preserves strokes instead of thresholding them away.
     *
     * The grayscale variant additionally auto-locates black/white points from the
     * histogram (0.4% / 99.6% percentiles) and stretches contrast before sharpening.
     * The color variant normalizes each RGB channel independently (which also removes
     * color casts) and then boosts saturation so stamps and charts stay vivid.
     */
    private fun smartEnhance(bitmap: Bitmap, params: FilterParams, color: Boolean): Bitmap {
        smartEnhanceOpenCv(bitmap, params, color)?.let { return it }
        return smartEnhanceFallback(bitmap, params, color)
    }

    /** Kernel radius for the background estimate: scales with image resolution (approx. r = sqrt(w*h)/32). */
    private fun smartBackgroundRadius(width: Int, height: Int): Int {
        val radius = (sqrt(width.toDouble() * height.toDouble()) / 32.0).roundToInt().coerceIn(9, 151)
        return if (radius % 2 == 0) radius + 1 else radius
    }

    private fun smartEnhanceOpenCv(bitmap: Bitmap, params: FilterParams, color: Boolean): Bitmap? = withOpenCvMat(bitmap) { source ->
        val background = Mat()
        val white = Mat(source.size(), source.type(), Scalar(255.0, 255.0, 255.0, 255.0))
        val denominator = Mat()
        val normalized = Mat()
        val gray = Mat()
        val stretched = Mat()
        val lut = MatOfByte()
        val output = Mat()
        try {
            val radius = smartBackgroundRadius(source.cols(), source.rows())
            Imgproc.GaussianBlur(source, background, CvSize(radius.toDouble(), radius.toDouble()), 0.0)
            // Blend the background toward white by strength: 1 = full normalization, 0 = untouched.
            // denominator = bg * s + 255 * (1 - s); normalized = source * 255 / denominator.
            val strength = params.smartStrength.toDouble()
            Core.addWeighted(background, strength, white, 1.0 - strength, 0.0, denominator)
            Core.divide(source, denominator, normalized, 255.0)
            if (color) {
                // Saturation boost on top of per-channel normalization keeps stamps and charts vivid.
                val saturated = adjust(matToBitmap(normalized) ?: return@withOpenCvMat null, .02f, 1.03f, 1.22f)
                    ?: return@withOpenCvMat null
                return@withOpenCvMat sharpen(saturated, .35f * params.sharpenScale)
            }
            Imgproc.cvtColor(normalized, gray, Imgproc.COLOR_RGBA2GRAY)
            // Auto black/white points from the histogram: stretch between the 0.4% and 99.6% percentiles.
            val grayBytes = ByteArray(gray.total().toInt())
            gray.get(0, 0, grayBytes)
            val bins = IntArray(256)
            grayBytes.forEach { bins[it.toInt() and 0xFF]++ }
            val totalPixels = grayBytes.size.toDouble()
            var low = 0
            var high = 255
            var cumulative = 0.0
            val lowTarget = totalPixels * .004
            val highTarget = totalPixels * .996
            for (index in 0 until 256) {
                cumulative += bins[index]
                if (cumulative >= lowTarget) { low = index; break }
            }
            cumulative = 0.0
            for (index in 0 until 256) {
                cumulative += bins[index]
                if (cumulative >= highTarget) { high = index; break }
            }
            if (high - low < 24) { low = 0; high = 255 }
            val span = (high - low).coerceAtLeast(1)
            val table = ByteArray(256) { index ->
                (((index - low) * 255) / span).coerceIn(0, 255).toByte()
            }
            lut.fromArray(*table)
            Core.LUT(gray, lut, stretched)
            Imgproc.cvtColor(stretched, output, Imgproc.COLOR_GRAY2RGBA)
            matToBitmap(output)?.let { return@withOpenCvMat sharpen(it, .5f * params.sharpenScale) }
        } finally {
            listOf(background, white, denominator, normalized, gray, stretched, output).forEach(Mat::release)
            lut.release()
        }
        null
    }

    private fun smartEnhanceFallback(bitmap: Bitmap, params: FilterParams, color: Boolean): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        // Downscale + upscale approximates the large-kernel Gaussian background estimate.
        val factor = 16
        val small = Bitmap.createScaledBitmap(bitmap, (width / factor).coerceAtLeast(1), (height / factor).coerceAtLeast(1), true)
        val background = Bitmap.createScaledBitmap(small, width, height, true)
        val pixels = IntArray(width * height)
        val bgPixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        background.getPixels(bgPixels, 0, width, 0, 0, width, height)
        val strength = params.smartStrength
        fun divideChannel(value: Int, bgValue: Int): Int {
            val denominator = bgValue * strength + 255f * (1f - strength)
            return if (denominator < 1f) value else (value * 255f / denominator).roundToInt().coerceIn(0, 255)
        }
        val result = IntArray(width * height)
        val grayHistogram = IntArray(256)
        for (index in pixels.indices) {
            val pixel = pixels[index]
            val bg = bgPixels[index]
            val r = divideChannel(Color.red(pixel), Color.red(bg))
            val g = divideChannel(Color.green(pixel), Color.green(bg))
            val b = divideChannel(Color.blue(pixel), Color.blue(bg))
            result[index] = if (color) {
                Color.rgb(r, g, b)
            } else {
                val gray = (r * 0.299f + g * 0.587f + b * 0.114f).roundToInt().coerceIn(0, 255)
                grayHistogram[gray]++
                Color.rgb(gray, gray, gray)
            }
        }
        val normalized: Bitmap = if (color) {
            Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also {
                it.setPixels(result, 0, width, 0, 0, width, height)
            }
        } else {
            // Same auto black/white stretch as the OpenCV path, computed from the gray histogram.
            var low = 0
            var high = 255
            val total = result.size.toDouble()
            var cumulative = 0.0
            for (index in 0 until 256) {
                cumulative += grayHistogram[index]
                if (cumulative >= total * .004) { low = index; break }
            }
            cumulative = 0.0
            for (index in 0 until 256) {
                cumulative += grayHistogram[index]
                if (cumulative >= total * .996) { high = index; break }
            }
            if (high - low >= 24) {
                val span = high - low
                for (index in result.indices) {
                    val gray = (((Color.red(result[index]) - low) * 255) / span).coerceIn(0, 255)
                    result[index] = Color.rgb(gray, gray, gray)
                }
            }
            Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also {
                it.setPixels(result, 0, width, 0, 0, width, height)
            }
        }
        return if (color) {
            sharpen(adjust(normalized, .02f, 1.03f, 1.22f) ?: normalized, .35f * params.sharpenScale)
        } else {
            sharpen(normalized, .5f * params.sharpenScale)
        }
    }

    private fun whitePaper(bitmap: Bitmap, params: FilterParams): Bitmap {
        val balanced = grayWorldWhiteBalance(bitmap)
        whitePaperOpenCv(balanced, params.paperLift)?.let { return it }
        return whitePaperFallback(balanced, params.paperLift)
    }

    /** Levels lift ((c-14)/224)^gamma applied through a 256-entry native LUT. */
    private fun whitePaperOpenCv(bitmap: Bitmap, gamma: Float): Bitmap? = withOpenCvMat(bitmap) { source ->
        val table = MatOfByte()
        val output = Mat()
        try {
            val lut = ByteArray(256) { index ->
                val normalized = ((index - 14f) / 224f).coerceIn(0f, 1f)
                (255f * Math.pow(normalized.toDouble(), gamma.toDouble())).roundToInt().coerceIn(0, 255).toByte()
            }
            table.fromArray(*lut)
            Core.LUT(source, table, output)
            matToBitmap(output)
        } finally {
            table.release()
            output.release()
        }
    }

    private fun whitePaperFallback(bitmap: Bitmap, gamma: Float): Bitmap {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        fun lift(channel: Int): Int {
            val normalized = ((channel - 14f) / 224f).coerceIn(0f, 1f)
            return (255f * Math.pow(normalized.toDouble(), gamma.toDouble())).roundToInt().coerceIn(0, 255)
        }
        pixels.indices.forEach { index ->
            val color = pixels[index]
            pixels[index] = Color.rgb(lift(Color.red(color)), lift(Color.green(color)), lift(Color.blue(color)))
        }
        return Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888).also {
            it.setPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        }
    }

    private fun blackAndWhite(bitmap: Bitmap, params: FilterParams): Bitmap {
        val balanced = grayWorldWhiteBalance(bitmap)
        blackAndWhiteOpenCv(balanced, params.threshold, params.denoise)?.let { return sharpen(it, .6f * params.sharpenScale) }
        return blackAndWhiteFallback(balanced, params)
    }

    /**
     * Local adaptive threshold (Gaussian) so uneven lighting and soft shadows no longer
     * collapse into black blotches like a global threshold does. Block size follows the
     * image resolution; ink keeps the original near-black tone of 24. The bias controls
     * stroke weight: a higher value keeps strokes thinner and cleaner, a lower value
     * picks up fainter strokes (at the cost of noise). A median pass before binarization
     * removes isolated specks from paper grain without eating stroke edges.
     */
    private fun blackAndWhiteOpenCv(bitmap: Bitmap, bias: Float, denoiseKernel: Int): Bitmap? = withOpenCvMat(bitmap) { source ->
        val gray = Mat()
        val binary = Mat()
        val remapped = Mat()
        var alpha = Mat()
        val channels = mutableListOf<Mat>()
        val output = Mat()
        try {
            Imgproc.cvtColor(source, gray, Imgproc.COLOR_RGBA2GRAY)
            // Kernel 1 is a no-op, so denoising can be turned off entirely.
            if (denoiseKernel >= 3) Imgproc.medianBlur(gray, gray, if (denoiseKernel % 2 == 0) denoiseKernel + 1 else denoiseKernel)
            val requested = (min(gray.cols(), gray.rows()) / 16).coerceIn(25, 101)
            val blockSize = if (requested % 2 == 0) requested + 1 else requested
            Imgproc.adaptiveThreshold(gray, binary, 255.0, Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C, Imgproc.THRESH_BINARY, blockSize, bias.toDouble())
            // Map 0 -> 24 (ink) and 255 -> 255 (paper) to preserve the original tone.
            binary.convertTo(remapped, CvType.CV_8UC1, 231.0 / 255.0, 24.0)
            alpha = Mat(gray.size(), CvType.CV_8UC1, Scalar(255.0))
            listOf(remapped, remapped, remapped, alpha).forEach(channels::add)
            Core.merge(channels, output)
            matToBitmap(output)
        } finally {
            channels.clear()
            listOf(gray, binary, remapped, alpha, output).forEach(Mat::release)
        }
    }

    private fun blackAndWhiteFallback(bitmap: Bitmap, params: FilterParams): Bitmap {
        val out = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        var sum = 0L
        pixels.forEach { color ->
            sum += ((Color.red(color) * 0.299f) + (Color.green(color) * 0.587f) + (Color.blue(color) * 0.114f)).roundToInt()
        }
        // Global-threshold stand-in for the adaptive bias: the default 12 maps to the original -8 offset.
        val biasOffset = 4 - params.threshold.toInt()
        val threshold = ((sum / pixels.size).toInt() + biasOffset).coerceIn(96, 190)
        pixels.indices.forEach { index ->
            val color = pixels[index]
            val lum = ((Color.red(color) * 0.299f) + (Color.green(color) * 0.587f) + (Color.blue(color) * 0.114f)).roundToInt()
            val value = if (lum > threshold) 255 else 24
            pixels[index] = Color.rgb(value, value, value)
        }
        out.setPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        return sharpen(out, .6f * params.sharpenScale)
    }

    fun adjust(bitmap: Bitmap?, brightness: Float, contrast: Float, saturation: Float): Bitmap? {
        if (bitmap == null) return null
        val out = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = AndroidCanvas(out)
        val matrix = ColorMatrix()
        matrix.setSaturation(saturation)
        val scale = contrast
        val translate = (-0.5f * scale + 0.5f + brightness) * 255f
        val contrastMatrix = ColorMatrix(floatArrayOf(scale, 0f, 0f, 0f, translate, 0f, scale, 0f, 0f, translate, 0f, 0f, scale, 0f, translate, 0f, 0f, 0f, 1f, 0f))
        matrix.postConcat(contrastMatrix)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.colorFilter = ColorMatrixColorFilter(matrix)
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        return out
    }

    fun watermark(bitmap: Bitmap, text: String): Bitmap {
        val out = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = AndroidCanvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(72, 15, 167, 160)
            textSize = (bitmap.width * .08f).coerceAtLeast(44f)
            isFakeBoldText = true
            textAlign = Paint.Align.CENTER
        }
        canvas.rotate(-28f, bitmap.width / 2f, bitmap.height / 2f)
        canvas.drawText(text, bitmap.width / 2f, bitmap.height / 2f, paint)
        return out
    }

    fun addSignature(bitmap: Bitmap): Bitmap {
        val out = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = AndroidCanvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(20, 24, 32)
            strokeWidth = (bitmap.width * .004f).coerceAtLeast(4f)
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
        }
        val y = bitmap.height * .86f
        val left = bitmap.width * .52f
        val right = bitmap.width * .84f
        canvas.drawLine(left, y, right, y, paint)
        paint.style = Paint.Style.FILL
        paint.textSize = (bitmap.width * .035f).coerceAtLeast(28f)
        canvas.drawText("Signed", left, y - 18f, paint)
        return out
    }

    fun scanQr(bitmap: Bitmap): String? {
        val scanner = BarcodeScanning.getClient()
        return runCatching {
            val image = InputImage.fromBitmap(bitmap, 0)
            val barcodes = Tasks.await(scanner.process(image))
            barcodes.firstOrNull()?.rawValue
        }.getOrNull()
    }
}

object BitmapFactoryCompat {
    fun decode(path: String): Bitmap? = android.graphics.BitmapFactory.decodeFile(path)

    fun decodeSampled(path: String, maxDimension: Int): Bitmap? {
        val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        android.graphics.BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val options = android.graphics.BitmapFactory.Options().apply {
            inSampleSize = bitmapSampleSize(bounds.outWidth, bounds.outHeight, maxDimension)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return android.graphics.BitmapFactory.decodeFile(path, options)
    }
}

@VisibleForTesting
fun bitmapSampleSize(width: Int, height: Int, maxDimension: Int): Int {
    if (width <= 0 || height <= 0 || maxDimension <= 0) return 1
    return ceil(maxOf(width, height).toDouble() / maxDimension.toDouble()).toInt().coerceAtLeast(1)
}

@VisibleForTesting
fun formatSize(size: Long): String {
    if (size <= 0) return "0 KB"
    val kb = size / 1024.0
    return if (kb < 1024) "${kb.roundToInt()} KB" else "${(kb / 1024.0 * 10).roundToInt() / 10.0} MB"
}

fun formatDate(time: Long): String {
    val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault())
    return formatter.format(Instant.ofEpochMilli(time))
}

fun shareFile(
    context: Context,
    doc: Document,
    targetPackage: String? = null,
    chooserTitle: String = "Share ${doc.title}",
) {
    val file = File(doc.exportPath)
    if (!file.exists()) {
        Toast.makeText(context, localized(context, "File is no longer available", "文件已不存在"), Toast.LENGTH_SHORT).show()
        AppLogger.w("Share", "Missing file for document ${doc.id}: ${doc.exportPath}")
        return
    }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = mimeTypeFor(doc.type)
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_TITLE, doc.title)
        putExtra(Intent.EXTRA_SUBJECT, doc.title)
        clipData = ClipData.newUri(context.contentResolver, doc.title, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        if (targetPackage != null) setPackage(targetPackage)
    }
    runCatching {
        if (targetPackage != null) {
            context.grantUriPermission(targetPackage, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, chooserTitle))
        AppLogger.i("Share", "Share document ${doc.id} target=${targetPackage ?: "system"} mime=${intent.type}")
    }.onFailure { error ->
        AppLogger.e("Share", "Unable to share to ${targetPackage ?: "system"}", error)
        if (targetPackage != null) {
            Toast.makeText(context, localized(context, "Target app is unavailable. Opening system share.", "目标应用不可用，正在打开系统分享"), Toast.LENGTH_SHORT).show()
            shareFile(context, doc, targetPackage = null, chooserTitle = chooserTitle)
        } else {
            Toast.makeText(context, localized(context, "No compatible sharing app found", "未找到可用的分享应用"), Toast.LENGTH_SHORT).show()
        }
    }
}

fun saveToGallery(context: Context, doc: Document) {
    val file = File(doc.exportPath)
    val values = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, file.name)
        put(MediaStore.MediaColumns.MIME_TYPE, mimeTypeFor(doc.type))
        put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOCUMENTS + "/ClearScan")
    }
    val uri = context.contentResolver.insert(MediaStore.Files.getContentUri("external"), values)
    if (uri != null) {
        context.contentResolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } }
        Toast.makeText(context, localized(context, "Saved to Files", "已保存到文件"), Toast.LENGTH_SHORT).show()
    }
}

fun printDocument(context: Context, doc: Document) {
    val printManager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
    val webView = WebView(context)
    webView.layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
    val file = File(doc.exportPath)
    webView.webViewClient = object : WebViewClient() {
        override fun onPageFinished(view: WebView, url: String) {
            printManager.print(doc.title, view.createPrintDocumentAdapter(doc.title), PrintAttributes.Builder().build())
        }
    }
    webView.loadDataWithBaseURL(null, "<html><body><h2>${doc.title}</h2><p>${file.name}</p><p>ClearScan document ready for printing.</p></body></html>", "text/html", "utf-8", null)
}

fun mimeTypeFor(type: String): String = when (type.uppercase()) {
    "PDF" -> "application/pdf"
    "PNG" -> "image/png"
    "WEBP" -> "image/webp"
    "BMP" -> "image/bmp"
    else -> "image/jpeg"
}

fun copyLogsToClipboard(context: Context, logs: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("ClearScan Logs", logs))
    AppLogger.i("Log", "Logs copied to clipboard")
    Toast.makeText(context, localized(context, "Logs copied", "日志已复制"), Toast.LENGTH_SHORT).show()
}

fun shareLogFile(context: Context) {
    val file = AppLogger.file(context)
    if (!file.exists()) file.writeText("")
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_TEXT, AppLogger.read().takeLast(12_000))
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    AppLogger.i("Log", "Share log file")
    context.startActivity(Intent.createChooser(intent, localized(context, "Share ClearScan logs", "分享 ClearScan 日志")))
}

fun localized(context: Context, english: String, chinese: String): String =
    if (context.getSharedPreferences("clearscan-settings", Context.MODE_PRIVATE).getString("language", "English") == "中文") chinese else english
