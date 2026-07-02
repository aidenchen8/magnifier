package com.example.magnifier.camera

import android.app.Application
import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import android.util.Log
import android.widget.Toast
import androidx.camera.core.Camera
import androidx.camera.core.CameraControl
import androidx.camera.core.CameraInfo
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.core.ZoomState
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.Observer
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

class CameraViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application
    private val mainExecutor = ContextCompat.getMainExecutor(app)
    private val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

    private var cameraProvider: ProcessCameraProvider? = null
    private var previewUseCase: Preview? = null
    private var imageCapture: ImageCapture? = null
    private var camera: Camera? = null
    private var zoomObserver: Observer<ZoomState>? = null

    private val _maxZoom = MutableStateFlow(1f)
    val maxZoom: StateFlow<Float> = _maxZoom.asStateFlow()

    private val _currentZoom = MutableStateFlow(1f)
    val currentZoom: StateFlow<Float> = _currentZoom.asStateFlow()

    private val _isTorchOn = MutableStateFlow(false)
    val isTorchOn: StateFlow<Boolean> = _isTorchOn.asStateFlow()

    private val _isFrozen = MutableStateFlow(false)
    val isFrozen: StateFlow<Boolean> = _isFrozen.asStateFlow()

    private val _frozenBitmap = MutableStateFlow<Bitmap?>(null)
    val frozenBitmap: StateFlow<Bitmap?> = _frozenBitmap.asStateFlow()

    private val _focusPoint = MutableStateFlow<Offset?>(null)
    val focusPoint: StateFlow<Offset?> = _focusPoint.asStateFlow()

    private val _filterMode = MutableStateFlow(FilterMode.NORMAL)
    val filterMode: StateFlow<FilterMode> = _filterMode.asStateFlow()

    enum class FilterMode {
        NORMAL,
        HIGH_CONTRAST,
        YELLOW_BLACK
    }

    fun bindCamera(lifecycleOwner: LifecycleOwner, previewView: PreviewView) {
        if (cameraProvider != null) return

        val future = ProcessCameraProvider.getInstance(app)
        future.addListener({
            try {
                val provider = future.get()
                cameraProvider = provider
                provider.unbindAll()

                val preview = Preview.Builder().build()
                preview.setSurfaceProvider(previewView.getSurfaceProvider())
                previewUseCase = preview

                val capture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .build()
                imageCapture = capture

                val cam = provider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    capture
                )
                camera = cam
                observeZoom(cam.cameraInfo)
                setTorchInternal(_isTorchOn.value)
            } catch (e: Exception) {
                Log.e(TAG, "Camera binding failed", e)
                toast("Camera initialization failed")
            }
        }, mainExecutor)
    }

    private fun observeZoom(cameraInfo: CameraInfo) {
        val observer = Observer<ZoomState> { state ->
            _maxZoom.value = state.maxZoomRatio
            _currentZoom.value = state.zoomRatio.coerceIn(1f, state.maxZoomRatio)
        }
        zoomObserver = observer
        cameraInfo.zoomState.observeForever(observer)
    }

    fun unbindCamera() {
        zoomObserver?.let { observer ->
            camera?.cameraInfo?.zoomState?.removeObserver(observer)
        }
        zoomObserver = null
        cameraProvider?.unbindAll()
        cameraProvider = null
        camera = null
        previewUseCase = null
        imageCapture = null
    }

    fun setZoom(ratio: Float) {
        val cam = camera ?: return
        val max = cam.cameraInfo.zoomState.value?.maxZoomRatio ?: _maxZoom.value
        val clamped = ratio.coerceIn(1f, max)
        cam.cameraControl.setZoomRatio(clamped)
    }

    fun toggleTorch() {
        val newState = !_isTorchOn.value
        _isTorchOn.value = newState
        setTorchInternal(newState)
    }

    private fun setTorchInternal(on: Boolean) {
        camera?.cameraControl?.enableTorch(on)
            ?.addListener({ /* ignore result */ }, mainExecutor)
    }

    fun focus(x: Float, y: Float, previewView: PreviewView) {
        if (_isFrozen.value) return
        val cam = camera ?: return
        val factory = previewView.meteringPointFactory
        val point = factory.createPoint(x, y)
        val action = FocusMeteringAction.Builder(
            point,
            FocusMeteringAction.FLAG_AF or
                FocusMeteringAction.FLAG_AE or
                FocusMeteringAction.FLAG_AWB
        )
            .setAutoCancelDuration(2, TimeUnit.SECONDS)
            .build()

        _focusPoint.value = Offset(x, y)
        cam.cameraControl.startFocusAndMetering(action)
            .addListener({
                viewModelScope.launch {
                    delay(700)
                    _focusPoint.value = null
                }
            }, mainExecutor)
    }

    fun freeze() {
        if (_isFrozen.value) return
        val capture = imageCapture ?: return
        _isFrozen.value = true

        val file = File(app.cacheDir, "frozen.jpg")
        val outputOptions = ImageCapture.OutputFileOptions.Builder(file).build()
        capture.takePicture(
            outputOptions,
            mainExecutor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    viewModelScope.launch(Dispatchers.IO) {
                        val bitmap = BitmapFactory.decodeFile(file.absolutePath)
                        withContext(Dispatchers.Main) {
                            if (bitmap != null) {
                                _frozenBitmap.value = bitmap.rotateIfNeeded(file.absolutePath)
                            } else {
                                _isFrozen.value = false
                                toast("Freeze failed")
                            }
                        }
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    _isFrozen.value = false
                    toast("Freeze failed: ${exception.message}")
                }
            }
        )
    }

    fun unfreeze() {
        val bitmap = _frozenBitmap.value
        _frozenBitmap.value = null
        _isFrozen.value = false
        bitmap?.let { if (!it.isRecycled) it.recycle() }
    }

    fun saveBitmap() {
        val bitmap = _frozenBitmap.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val resolver = app.contentResolver
            val values = ContentValues().apply {
                put(
                    MediaStore.Images.Media.DISPLAY_NAME,
                    "magnifier_${System.currentTimeMillis()}.jpg"
                )
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(
                    MediaStore.Images.Media.RELATIVE_PATH,
                    android.os.Environment.DIRECTORY_PICTURES + "/Magnifier"
                )
            }

            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            if (uri != null) {
                try {
                    resolver.openOutputStream(uri)?.use { output ->
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 95, output)
                    }
                    withContext(Dispatchers.Main) { toast("Saved to Pictures/Magnifier") }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) { toast("Save failed: ${e.message}") }
                }
            } else {
                withContext(Dispatchers.Main) { toast("Save failed") }
            }
        }
    }

    fun nextFilter() {
        _filterMode.value = when (_filterMode.value) {
            FilterMode.NORMAL -> FilterMode.HIGH_CONTRAST
            FilterMode.HIGH_CONTRAST -> FilterMode.YELLOW_BLACK
            FilterMode.YELLOW_BLACK -> FilterMode.NORMAL
        }
    }

    fun colorFilter(): ColorFilter? = when (_filterMode.value) {
        FilterMode.NORMAL -> null
        FilterMode.HIGH_CONTRAST -> ColorFilter.colorMatrix(
            ColorMatrix(
                floatArrayOf(
                    -1f, 0f, 0f, 0f, 255f,
                    0f, -1f, 0f, 0f, 255f,
                    0f, 0f, -1f, 0f, 255f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
        )
        FilterMode.YELLOW_BLACK -> ColorFilter.colorMatrix(
            ColorMatrix(
                floatArrayOf(
                    0.299f, 0.587f, 0.114f, 0f, 0f,
                    0.299f, 0.587f, 0.114f, 0f, 0f,
                    0f, 0f, 0f, 0f, 0f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
        )
    }

    override fun onCleared() {
        super.onCleared()
        unbindCamera()
        val bitmap = _frozenBitmap.value
        _frozenBitmap.value = null
        bitmap?.let { if (!it.isRecycled) it.recycle() }
    }

    private fun Bitmap.rotateIfNeeded(path: String): Bitmap {
        val rotation = ExifInterface(path).rotationDegrees
        return if (rotation == 0) {
            this
        } else {
            val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
            Bitmap.createBitmap(this, 0, 0, width, height, matrix, true).also {
                if (it != this && !this.isRecycled) recycle()
            }
        }
    }

    private fun toast(message: String) {
        Toast.makeText(app, message, Toast.LENGTH_SHORT).show()
    }

    companion object {
        private const val TAG = "CameraViewModel"
    }
}
