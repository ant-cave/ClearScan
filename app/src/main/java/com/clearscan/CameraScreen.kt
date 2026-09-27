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
import android.net.Uri
import android.os.Bundle
import android.os.Environment
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
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
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
import androidx.compose.material.icons.filled.AutoStories
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
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.RotateLeft
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SwapHoriz
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
import androidx.compose.ui.graphics.graphicsLayer
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
import androidx.compose.ui.unit.IntOffset
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
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

@Composable
fun CameraScreen(state: UiState, model: ClearScanViewModel) {
    val context = LocalContext.current
    val settings = state.settings
    val imageCapture = remember(settings.cameraResolution) {
        // Cap the still-capture resolution instead of taking the sensor's full
        // output: "High" tops out at 12MP-class 4:3 (4032x3024) and "Balanced"
        // at 5MP-class (2560x1920). Full-sensor 50MP+ captures were the main
        // source of shutter-to-image latency; quality for document scanning is
        // unaffected at these sizes (>300 DPI on A4).
        val maxEdge = if (settings.cameraResolution == "High") 4032 else 2560
        val resolutionSelector = ResolutionSelector.Builder()
            .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
            .setResolutionStrategy(
                ResolutionStrategy(
                    android.util.Size(maxEdge, maxEdge * 3 / 4),
                    ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER,
                ),
            )
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
    var flashVisible by remember { mutableStateOf(false) }
    var zoomRatio by remember { mutableFloatStateOf(1f) }
    val flashAlpha by animateFloatAsState(if (flashVisible) 1f else 0f, animationSpec = tween(durationMillis = 300), label = "flash")
    LaunchedEffect(flashVisible) {
        if (flashVisible) {
            kotlinx.coroutines.delay(300)
            flashVisible = false
        }
    }
    LaunchedEffect(zoomRatio) {
        boundCamera?.cameraInfo?.let { info ->
            val zoomState = info.zoomState.value
            val minZoom = zoomState?.minZoomRatio ?: 1f
            val maxZoom = zoomState?.maxZoomRatio ?: 5f
            boundCamera?.cameraControl?.setZoomRatio(zoomRatio.coerceIn(minZoom, maxZoom))
        }
    }
    val analyzer = remember(state.scanMode) {
        DocumentFrameAnalyzer(detectionProfileFor(state.scanMode), model::onLiveDocumentFrame)
    }
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
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
    val scanModes = listOf(
        ScanMode.Document to tr(settings, "Document", "文档"),
        ScanMode.IdCard to tr(settings, "ID", "证件"),
        ScanMode.Book to tr(settings, "Book", "书籍"),
    )
    Column(Modifier.fillMaxSize().background(ComposeColor.Black).statusBarsPadding()) {
        // === 顶栏：紧凑 48dp ===
        Row(Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = model::back, modifier = Modifier.size(40.dp)) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = ComposeColor.White, modifier = Modifier.size(22.dp)) }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(
                    enabled = boundCamera?.cameraInfo?.hasFlashUnit() == true,
                    onClick = {
                        flashMode = when (flashMode) {
                            ImageCapture.FLASH_MODE_AUTO -> ImageCapture.FLASH_MODE_ON
                            ImageCapture.FLASH_MODE_ON -> ImageCapture.FLASH_MODE_OFF
                            else -> ImageCapture.FLASH_MODE_AUTO
                        }
                    },
                    modifier = Modifier.size(40.dp),
                ) {
                    Icon(
                        when (flashMode) {
                            ImageCapture.FLASH_MODE_ON -> Icons.Default.FlashOn
                            ImageCapture.FLASH_MODE_OFF -> Icons.Default.FlashOff
                            else -> Icons.Default.FlashAuto
                        },
                        null,
                        tint = if (boundCamera?.cameraInfo?.hasFlashUnit() == true) ComposeColor.White else Muted,
                        modifier = Modifier.size(22.dp),
                    )
                }
                IconButton(onClick = { lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK }, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Default.Cameraswitch, null, tint = ComposeColor.White, modifier = Modifier.size(22.dp))
                }
                IconButton(onClick = { settingsOpen = true }, modifier = Modifier.size(40.dp)) { Icon(Icons.Default.Settings, null, tint = ComposeColor.White, modifier = Modifier.size(22.dp)) }
            }
        }
        // === 相机预览 + 右侧缩放滑块 ===
        Box(Modifier.weight(1f).fillMaxWidth().background(ComposeColor(0xFF1A1A1A)), contentAlignment = Alignment.Center) {
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
                    val color = ComposeColor.White.copy(alpha = .35f)
                    drawLine(color, Offset(size.width / 3f, 0f), Offset(size.width / 3f, size.height), 1f)
                    drawLine(color, Offset(size.width * 2f / 3f, 0f), Offset(size.width * 2f / 3f, size.height), 1f)
                    drawLine(color, Offset(0f, size.height / 3f), Offset(size.width, size.height / 3f), 1f)
                    drawLine(color, Offset(0f, size.height * 2f / 3f), Offset(size.width, size.height * 2f / 3f), 1f)
                }
                // 右侧竖向缩放滑块
                if (boundCamera?.cameraInfo?.hasFlashUnit() == true || boundCamera != null) {
                    Column(
                        Modifier
                            .align(Alignment.CenterEnd)
                            .padding(end = 8.dp)
                            .width(36.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(ComposeColor(0x66000000))
                            .padding(vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(Icons.Default.Add, null, tint = ComposeColor.White.copy(alpha = 0.7f), modifier = Modifier.size(18.dp))
                        Spacer(Modifier.height(4.dp))
                        Box(
                            Modifier
                                .width(2.dp)
                                .height(120.dp)
                                .clip(RoundedCornerShape(1.dp))
                                .background(ComposeColor(0x44FFFFFF))
                        ) {
                            val maxZoom = boundCamera?.cameraInfo?.zoomState?.value?.maxZoomRatio ?: 10f
                            val fraction = ((zoomRatio - 1f) / (maxZoom - 1f)).coerceIn(0f, 1f)
                            Box(
                                Modifier
                                    .align(Alignment.BottomCenter)
                                    .offset(y = with(LocalDensity.current) { -(fraction * 120f).dp })
                                    .size(14.dp)
                                    .clip(CircleShape)
                                    .background(ComposeColor.White)
                                    .pointerInput(Unit) {
                                        detectDragGestures { change, drag ->
                                            change.consume()
                                            val maxZoom2 = boundCamera?.cameraInfo?.zoomState?.value?.maxZoomRatio ?: 10f
                                            val delta = -drag.y / 120f * (maxZoom2 - 1f)
                                            zoomRatio = (zoomRatio + delta).coerceIn(1f, maxZoom2)
                                        }
                                    }
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Icon(Icons.Default.Close, null, tint = ComposeColor.White.copy(alpha = 0.7f), modifier = Modifier.size(18.dp))
                    }
                }
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    DocumentOnTable()
                    Spacer(Modifier.height(16.dp))
                    Text(tr(settings, "Allow camera access to scan real documents", "允许相机权限后即可扫描真实文档"), color = ComposeColor.White, fontSize = 15.sp)
                }
            }
            // 闪白动画
            if (flashAlpha > 0.01f) {
                Box(Modifier.fillMaxSize().background(ComposeColor.White.copy(alpha = flashAlpha)))
            }
            if (state.captureMessage != null) {
                Text(state.captureMessage, color = ComposeColor.White, textAlign = TextAlign.Center, modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp).clip(RoundedCornerShape(8.dp)).background(ComposeColor(0x99000000)).padding(horizontal = 14.dp, vertical = 8.dp), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        // === 底部面板（CamScanner 风格：模式芯片+单多页 → 快门行 → 提示） ===
        Column(
            Modifier
                .fillMaxWidth()
                .background(ComposeColor(0xFF0A0A0A))
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // ① 顶部控制行：左侧模式芯片（文档/证件/书籍）+ 右侧单页/多页分段开关
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp, bottom = 6.dp, start = 14.dp, end = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 模式芯片横排（占满剩余宽度，保持均匀铺开）
                Row(
                    Modifier.weight(1f),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    scanModes.forEach { (mode, label) ->
                        val selected = mode == state.scanMode
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .clickable {
                                    if (state.draftPages.isNotEmpty() && mode != state.scanMode) pendingMode = mode else model.changeScanMode(mode)
                                }
                                .padding(horizontal = 14.dp, vertical = 4.dp),
                        ) {
                            Box(
                                Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(13.dp))
                                    .background(if (selected) Teal else ComposeColor.White.copy(alpha = 0.08f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    when (mode) {
                                        ScanMode.IdCard -> Icons.Outlined.Badge
                                        ScanMode.Book -> Icons.Default.AutoStories
                                        else -> Icons.Default.Description
                                    },
                                    null,
                                    tint = if (selected) ComposeColor.White else ComposeColor.White.copy(alpha = 0.75f),
                                    modifier = Modifier.size(22.dp),
                                )
                            }
                            Spacer(Modifier.height(5.dp))
                            Text(
                                label,
                                color = if (selected) ComposeColor.White else ComposeColor.White.copy(alpha = 0.5f),
                                fontSize = 11.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            )
                        }
                    }
                }
                // 单页 / 多页 分段开关（与模式平级，置于右侧，已从文档模式解耦）
                Row(
                    Modifier
                        .clip(RoundedCornerShape(18.dp))
                        .background(ComposeColor.White.copy(alpha = 0.08f))
                        .padding(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    listOf(
                        DocumentCaptureMode.Single to tr(settings, "Single", "单页"),
                        DocumentCaptureMode.Multi to tr(settings, "Multi", "多页"),
                    ).forEach { (mode, label) ->
                        val selected = mode == state.documentCaptureMode
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (selected) Teal else ComposeColor.Transparent)
                                .clickable { model.changeDocumentCaptureMode(mode) }
                                .padding(horizontal = 12.dp, vertical = 5.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                label,
                                color = if (selected) ComposeColor.White else ComposeColor.White.copy(alpha = 0.6f),
                                fontSize = 12.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            )
                        }
                    }
                }
            }
            // ② 快门行：相册导入 | 白色快门+细环 | 批量页数
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 40.dp)
                    .height(92.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 左：相册导入
                Box(
                    Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(ComposeColor.White.copy(alpha = 0.08f))
                        .clickable { pickImage.launch("image/*") },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Default.PhotoLibrary, null, tint = ComposeColor.White, modifier = Modifier.size(22.dp))
                }
                // 中：快门（外细环 + 白色实心圆）
                Box(contentAlignment = Alignment.Center) {
                    Box(
                        Modifier
                            .size(86.dp)
                            .clip(CircleShape)
                            .border(2.dp, ComposeColor.White.copy(alpha = 0.9f), CircleShape)
                    )
                    Box(
                        Modifier
                            .size(70.dp)
                            .clip(CircleShape)
                            .background(ComposeColor.White)
                            .clickable {
                                if (hasCameraPermission) {
                                    flashVisible = true
                                    takeRealPhoto(context, imageCapture, model, settings)
                                } else permission.launch(Manifest.permission.CAMERA)
                            },
                    )
                }
                // 右：批量页数（已拍 Teal 高亮 + 红色角标） / 空态拍照
                Box(
                    Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (state.draftPages.isNotEmpty()) Teal else ComposeColor.White.copy(alpha = 0.08f))
                        .clickable {
                            if (state.draftPages.isNotEmpty()) {
                                model.finishScanSession()
                            } else {
                                if (hasCameraPermission) {
                                    flashVisible = true
                                    takeRealPhoto(context, imageCapture, model, settings)
                                } else permission.launch(Manifest.permission.CAMERA)
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.DocumentScanner,
                        null,
                        tint = if (state.draftPages.isNotEmpty()) ComposeColor.White else ComposeColor.White.copy(alpha = 0.5f),
                        modifier = Modifier.size(22.dp),
                    )
                    if (state.draftPages.isNotEmpty()) {
                        Box(
                            Modifier
                                .align(Alignment.TopEnd)
                                .offset(x = 6.dp, y = (-6).dp)
                                .size(18.dp)
                                .clip(CircleShape)
                                .background(ComposeColor(0xFFE53935))
                                .border(1.5.dp, ComposeColor(0xFF0A0A0A), CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("${state.draftPages.size}", color = ComposeColor.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            // ③ 单页/多页已上移至顶部控制行，此处仅保留间距
            Spacer(Modifier.height(10.dp))
            // ④ 底部提示
            Text(
                if (state.draftPages.isNotEmpty()) {
                    tr(settings, "${state.draftPages.size} pages captured", "已拍摄 ${state.draftPages.size} 页，点击右侧按钮完成")
                } else {
                    tr(settings, "Place the document inside the frame", "将文档置于取景框内")
                },
                color = ComposeColor.White.copy(alpha = 0.45f),
                fontSize = 11.sp,
                modifier = Modifier.padding(bottom = 10.dp),
            )
        }
    }
    if (settingsOpen) AlertDialog(
        onDismissRequest = { settingsOpen = false },
        title = { Text(tr(settings, "Camera settings", "相机设置")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                SettingToggle(tr(settings, "Composition grid", "构图网格"), settings.cameraGrid) { model.updateSettings(settings.copy(cameraGrid = it)) }
                SettingToggle(tr(settings, "Automatic enhancement", "自动增强"), settings.cameraEnhance) { model.updateSettings(settings.copy(cameraEnhance = it)) }
                Text(tr(settings, "Resolution: ${if (settings.cameraResolution == "High") "High (~12MP)" else "Balanced (~5MP)"}", "分辨率：${if (settings.cameraResolution == "High") "高画质 · 约1200万" else "均衡 · 约500万"}"))
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
