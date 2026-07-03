package com.example.magnifier.ui

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemGestures
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterBAndW
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.calculateZoom
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.LifecycleOwner
import com.example.magnifier.camera.CameraViewModel
import kotlin.math.roundToInt

@Composable
fun MagnifierScreen(viewModel: CameraViewModel) {
    val context = LocalContext.current
    val activity = context as? Activity
    val lifecycleOwner = LocalLifecycleOwner.current
    val view = LocalView.current
    val density = LocalDensity.current

    DisposableEffect(view) {
        val window = (view.context as? Activity)?.window
        window?.let {
            WindowCompat.setDecorFitsSystemWindows(it, false)
            WindowInsetsControllerCompat(it, it.decorView).apply {
                hide(WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.navigationBars())
                systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        }
        onDispose { }
    }

    val gestureInsets = WindowInsets.systemGestures.asPaddingValues()
    val topInset = with(density) { gestureInsets.calculateTopPadding() }
    val bottomInset = with(density) { gestureInsets.calculateBottomPadding() }
    val topSafePadding = topInset + 16.dp
    val bottomSafePadding = bottomInset + 16.dp

    val maxZoom by viewModel.maxZoom.collectAsState()
    val currentZoom by viewModel.currentZoom.collectAsState()
    val isTorchOn by viewModel.isTorchOn.collectAsState()
    val isFrozen by viewModel.isFrozen.collectAsState()
    val frozenBitmap by viewModel.frozenBitmap.collectAsState()
    val focusPoint by viewModel.focusPoint.collectAsState()
    val filterMode by viewModel.filterMode.collectAsState()

    val previewView = remember {
        androidx.camera.view.PreviewView(context).apply {
            implementationMode = androidx.camera.view.PreviewView.ImplementationMode.COMPATIBLE
            scaleType = androidx.camera.view.PreviewView.ScaleType.FILL_CENTER
        }
    }

    DisposableEffect(previewView, lifecycleOwner) {
        viewModel.bindCamera(lifecycleOwner as LifecycleOwner, previewView)
        onDispose { viewModel.unbindCamera() }
    }

    DisposableEffect(filterMode, isFrozen, previewView) {
        if (!isFrozen && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            previewView.setRenderEffect(renderEffectForMode(filterMode))
        } else {
            previewView.setRenderEffect(null)
        }
        onDispose { previewView.setRenderEffect(null) }
    }

    MaterialTheme(colorScheme = darkColorScheme()) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            if (!isFrozen) {
                AndroidView(
                    factory = { previewView },
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTapAndPinchGestures(
                                onTap = { offset ->
                                    viewModel.focus(offset.x, offset.y, previewView)
                                },
                                onPinch = { zoomDelta ->
                                    viewModel.applyPinchZoom(zoomDelta)
                                }
                            )
                        }
                )
            } else {
                frozenBitmap?.let { bitmap ->
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        colorFilter = viewModel.colorFilter(),
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                scaleX = currentZoom
                                scaleY = currentZoom
                                transformOrigin = TransformOrigin.Center
                            }
                            .pointerInput(Unit) {
                                detectTapAndPinchGestures(
                                    onTap = { /* no focus when frozen */ },
                                    onPinch = { zoomDelta ->
                                        viewModel.applyPinchZoom(zoomDelta)
                                    }
                                )
                            }
                    )
                }
            }

            focusPoint?.let { point ->
                FocusBox(point = point)
            }

            DarkIconButton(
                icon = Icons.Default.Close,
                onClick = {
                    if (isFrozen) {
                        viewModel.unfreeze()
                    } else {
                        activity?.finish()
                    }
                },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = topSafePadding, end = 16.dp)
            )

            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(bottom = bottomSafePadding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                DarkCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp)
                    ) {
                        Text(
                            text = "%.1fx".format(currentZoom),
                            color = Color.White,
                            fontSize = 24.sp,
                            modifier = Modifier.width(72.dp)
                        )
                        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
                            Slider(
                                value = currentZoom,
                                onValueChange = viewModel::setZoom,
                                valueRange = 1f..maxZoom.coerceAtLeast(1f),
                                steps = ((maxZoom.coerceAtLeast(1f) - 1f) * 10).roundToInt().coerceAtLeast(0),
                                colors = SliderDefaults.colors(
                                    thumbColor = Color.White,
                                    activeTrackColor = Color.White,
                                    inactiveTrackColor = Color.White.copy(alpha = 0.4f)
                                ),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                DarkCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 8.dp)
                    ) {
                        DarkControlButton(
                            icon = Icons.Default.FlashlightOn,
                            label = "灯光",
                            enabled = !isFrozen,
                            active = isTorchOn,
                            onClick = viewModel::toggleTorch
                        )
                        DarkControlButton(
                            icon = Icons.Default.CameraAlt,
                            label = "冻结",
                            active = isFrozen,
                            onClick = { if (isFrozen) viewModel.unfreeze() else viewModel.freeze() }
                        )
                        DarkControlButton(
                            icon = Icons.Default.Save,
                            label = "保存",
                            enabled = isFrozen,
                            onClick = viewModel::saveBitmap
                        )
                        DarkControlButton(
                            icon = Icons.Default.FilterBAndW,
                            label = "模式",
                            onClick = viewModel::nextFilter
                        )
                    }
                }
            }
        }
    }
}

private suspend fun PointerInputScope.detectTapAndPinchGestures(
    onTap: (Offset) -> Unit,
    onPinch: (Float) -> Unit
) {
    awaitPointerEventScope {
        while (true) {
            val down = awaitFirstDown()
            val initialPosition = down.position
            val initialTime = down.uptimeMillis

            var isPinch = false
            var pinchStarted = false
            var event: PointerEvent

            do {
                event = awaitPointerEvent()
                val pressedCount = event.changes.count { it.pressed }

                if (pressedCount >= 2) {
                    if (!isPinch) {
                        isPinch = true
                        pinchStarted = false
                    } else {
                        val zoom = event.calculateZoom()
                        if (pinchStarted) {
                            onPinch(zoom)
                        }
                        pinchStarted = true
                    }
                }
            } while (event.changes.any { it.pressed })

            if (!isPinch) {
                val change = event.changes.firstOrNull()
                val duration = (change?.uptimeMillis ?: initialTime) - initialTime
                val finalPosition = change?.position ?: initialPosition
                val distance = (finalPosition - initialPosition).getDistance()
                if (duration < viewConfiguration.longPressTimeoutMillis &&
                    distance < viewConfiguration.touchSlop
                ) {
                    onTap(initialPosition)
                }
            }
        }
    }
}

private fun renderEffectForMode(mode: CameraViewModel.FilterMode): android.graphics.RenderEffect? {
    if (mode == CameraViewModel.FilterMode.NORMAL) return null
    val matrix = android.graphics.ColorMatrix()
    when (mode) {
        CameraViewModel.FilterMode.HIGH_CONTRAST -> matrix.set(
            floatArrayOf(
                -1f, 0f, 0f, 0f, 255f,
                0f, -1f, 0f, 0f, 255f,
                0f, 0f, -1f, 0f, 255f,
                0f, 0f, 0f, 1f, 0f
            )
        )
        CameraViewModel.FilterMode.YELLOW_BLACK -> matrix.set(
            floatArrayOf(
                0.299f, 0.587f, 0.114f, 0f, 0f,
                0.299f, 0.587f, 0.114f, 0f, 0f,
                0f, 0f, 0f, 0f, 0f,
                0f, 0f, 0f, 1f, 0f
            )
        )
        else -> return null
    }
    return android.graphics.RenderEffect.createColorFilterEffect(
        android.graphics.ColorMatrixColorFilter(matrix)
    )
}

@Composable
private fun FocusBox(point: Offset) {
    val density = LocalDensity.current
    val half = with(density) { 32.dp.toPx() }.roundToInt()
    Box(
        modifier = Modifier
            .offset {
                IntOffset(
                    x = (point.x - half).roundToInt(),
                    y = (point.y - half).roundToInt()
                )
            }
            .size(64.dp)
            .border(width = 3.dp, color = Color.Yellow)
    )
}

@Composable
private fun DarkCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(Color.Black.copy(alpha = 0.75f))
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = 0.15f),
                shape = RoundedCornerShape(24.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

@Composable
private fun DarkControlButton(
    icon: ImageVector,
    label: String,
    enabled: Boolean = true,
    active: Boolean = false,
    onClick: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val alpha = if (enabled) 1f else 0.4f
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.alpha(alpha)
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(
                    if (active) Color.White.copy(alpha = 0.25f)
                    else Color.Black.copy(alpha = 0.4f)
                )
                .border(
                    width = 1.dp,
                    color = Color.White.copy(alpha = 0.2f),
                    shape = CircleShape
                )
                .clickable(enabled = enabled) {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onClick()
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = Color.White,
                modifier = Modifier.size(32.dp)
            )
        }
        Text(
            text = label,
            color = Color.White,
            fontSize = 16.sp,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}

@Composable
private fun DarkIconButton(
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    Box(
        modifier = modifier
            .size(56.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.75f))
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = 0.15f),
                shape = CircleShape
            )
            .clickable {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(28.dp)
        )
    }
}
