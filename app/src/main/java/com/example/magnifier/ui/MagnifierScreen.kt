package com.example.magnifier.ui

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.material.icons.filled.FlashlightOff
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Videocam
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
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
                            detectTapGestures { offset ->
                                viewModel.focus(offset.x, offset.y, previewView)
                            }
                        }
                )
            } else {
                frozenBitmap?.let { bitmap ->
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        colorFilter = viewModel.colorFilter(),
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            focusPoint?.let { point ->
                FocusBox(point = point)
            }

            GlassIconButton(
                icon = Icons.Default.Close,
                onClick = { activity?.finish() },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = topSafePadding, end = 16.dp)
            )

            GlassBottomPanel(
                modifier = Modifier.align(Alignment.BottomCenter),
                bottomPadding = bottomSafePadding,
                isFrozen = isFrozen,
                isTorchOn = isTorchOn,
                currentZoom = currentZoom,
                maxZoom = maxZoom,
                filterMode = filterMode,
                onZoomChange = viewModel::setZoom,
                onTorchToggle = viewModel::toggleTorch,
                onFreezeToggle = { if (isFrozen) viewModel.unfreeze() else viewModel.freeze() },
                onSave = viewModel::saveBitmap,
                onFilter = viewModel::nextFilter
            )
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
private fun GlassBottomPanel(
    modifier: Modifier = Modifier,
    bottomPadding: androidx.compose.ui.unit.Dp,
    isFrozen: Boolean,
    isTorchOn: Boolean,
    currentZoom: Float,
    maxZoom: Float,
    filterMode: CameraViewModel.FilterMode,
    onZoomChange: (Float) -> Unit,
    onTorchToggle: () -> Unit,
    onFreezeToggle: () -> Unit,
    onSave: () -> Unit,
    onFilter: () -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = bottomPadding)
            .height(120.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.22f),
                        Color.White.copy(alpha = 0.10f)
                    )
                )
            )
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = 0.35f),
                shape = RoundedCornerShape(28.dp)
            )
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxSize()
        ) {
            if (!isFrozen) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().height(32.dp)
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
                            onValueChange = onZoomChange,
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

            Row(
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().weight(1f)
            ) {
                GlassIconButton(
                    icon = if (isTorchOn) Icons.Default.FlashlightOff else Icons.Default.FlashlightOn,
                    label = if (isTorchOn) "关灯" else "开灯",
                    enabled = !isFrozen,
                    onClick = onTorchToggle
                )
                GlassIconButton(
                    icon = if (isFrozen) Icons.Default.Videocam else Icons.Default.CameraAlt,
                    label = if (isFrozen) "解冻" else "冻结",
                    onClick = onFreezeToggle
                )
                GlassIconButton(
                    icon = Icons.Default.Save,
                    label = "保存",
                    enabled = isFrozen,
                    onClick = onSave
                )
                GlassIconButton(
                    icon = Icons.Default.FilterBAndW,
                    label = when (filterMode) {
                        CameraViewModel.FilterMode.NORMAL -> "正常"
                        CameraViewModel.FilterMode.HIGH_CONTRAST -> "黑白"
                        CameraViewModel.FilterMode.YELLOW_BLACK -> "黄黑"
                    },
                    onClick = onFilter
                )
            }
        }
    }
}

@Composable
private fun GlassIconButton(
    icon: ImageVector,
    label: String? = null,
    enabled: Boolean = true,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val alpha = if (enabled) 1f else 0.4f
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.alpha(alpha)
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.28f),
                            Color.White.copy(alpha = 0.10f)
                        )
                    )
                )
                .border(
                    width = 1.dp,
                    color = Color.White.copy(alpha = 0.35f),
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
        if (label != null) {
            Text(
                text = label,
                color = Color.White,
                fontSize = 16.sp,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}
