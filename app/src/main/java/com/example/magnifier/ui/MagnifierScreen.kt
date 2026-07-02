package com.example.magnifier.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.FilterBAndW
import androidx.compose.material.icons.filled.FlashlightOff
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
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
    val lifecycleOwner = LocalLifecycleOwner.current
    val view = LocalView.current

    // Hide system bars for this screen.
    DisposableEffect(view) {
        val window = (view.context as? android.app.Activity)?.window
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

        BottomControlBar(
            modifier = Modifier.align(Alignment.BottomCenter),
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
private fun BottomControlBar(
    modifier: Modifier = Modifier,
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
    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(120.dp)
            .background(Color.Black.copy(alpha = 0.75f))
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
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
            ControlButton(
                icon = if (isTorchOn) Icons.Default.FlashlightOff else Icons.Default.FlashlightOn,
                label = if (isTorchOn) "关灯" else "开灯",
                enabled = !isFrozen,
                onClick = onTorchToggle
            )
            ControlButton(
                icon = if (isFrozen) Icons.Default.Videocam else Icons.Default.CameraAlt,
                label = if (isFrozen) "解冻" else "冻结",
                onClick = onFreezeToggle
            )
            ControlButton(
                icon = Icons.Default.Save,
                label = "保存",
                enabled = isFrozen,
                onClick = onSave
            )
            ControlButton(
                icon = Icons.Default.FilterBAndW,
                label = when (filterMode) {
                    CameraViewModel.FilterMode.NORMAL -> "正常"
                    CameraViewModel.FilterMode.HIGH_CONTRAST -> "黑白"
                    CameraViewModel.FilterMode.YELLOW_BLACK -> "黄黑"
                },
                enabled = isFrozen,
                onClick = onFilter
            )
        }
    }
}

@Composable
private fun ControlButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            },
            enabled = enabled,
            modifier = Modifier.size(64.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (enabled) Color.White else Color(0x80FFFFFF),
                modifier = Modifier.size(32.dp)
            )
        }
        Text(
            text = label,
            color = if (enabled) Color.White else Color(0x80FFFFFF),
            fontSize = 16.sp
        )
    }
}
