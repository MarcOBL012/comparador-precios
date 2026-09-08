package pe.com.comparadorprecios.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import pe.com.comparadorprecios.util.ImageEncoding
import pe.com.comparadorprecios.util.ThumbnailEncoder
import java.io.File
import java.util.concurrent.Executor

/** Pantalla de escaneo — captura puntual con CameraX (no streaming a IA). */
@Composable
fun ScanScreen(
    onImageCaptured: (dataUri: String, thumbnail: ByteArray?) -> Unit,
    onError: (String) -> Unit,
) {
    val context = LocalContext.current
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasPermission = granted }
    LaunchedEffect(Unit) {
        if (!hasPermission) launcher.launch(Manifest.permission.CAMERA)
    }
    if (!hasPermission) {
        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Text("Necesitamos acceso a la cámara para escanear el producto.")
        }
        return
    }
    val lifecycle = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context) }
    val imageCapture = remember { ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build() }
    var capturing by remember { mutableStateOf(false) }

    LaunchedEffect(previewView) {
        val provider = ProcessCameraProvider.getInstance(context).get()
        val preview = Preview.Builder().build().also {
            it.setSurfaceProvider(previewView.surfaceProvider)
        }
        provider.unbindAll()
        provider.bindToLifecycle(lifecycle, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
        ExtendedFloatingActionButton(
            onClick = {
                if (capturing) return@ExtendedFloatingActionButton
                capturing = true
                capturePhoto(context, imageCapture, onImageCaptured, onError) { capturing = false }
            },
            modifier = Modifier.align(Alignment.BottomCenter).padding(24.dp),
            icon = { Icon(Icons.Filled.PhotoCamera, contentDescription = null) },
            text = { Text(if (capturing) "Capturando…" else "Escanear producto") },
        )
    }
}

private fun capturePhoto(
    context: Context,
    imageCapture: ImageCapture,
    onImageCaptured: (String, ByteArray?) -> Unit,
    onError: (String) -> Unit,
    onDone: () -> Unit,
) {
    val file = File.createTempFile("scan_", ".jpg", context.cacheDir)
    val options = ImageCapture.OutputFileOptions.Builder(file).build()
    val executor: Executor = ContextCompat.getMainExecutor(context)
    imageCapture.takePicture(
        options,
        executor,
        object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                try {
                    val bytes = file.readBytes()
                    val bitmap = ImageEncoding.decode(bytes)
                    val payload = if (bitmap != null) {
                        ImageEncoding.compressToLimit(bitmap)
                    } else {
                        bytes
                    }
                    val dataUri = ImageEncoding.toDataUri(payload)
                    if (ImageEncoding.exceedsLimit(dataUri)) {
                        onError("La foto es demasiado grande incluso comprimida. Acércate menos o baja la resolución.")
                    } else {
                        val thumbnail = if (bitmap != null) {
                            runCatching { ThumbnailEncoder.encode(bitmap) }.getOrNull()
                        } else {
                            null
                        }
                        onImageCaptured(dataUri, thumbnail)
                    }
                } catch (e: Exception) {
                    onError("No se pudo procesar la foto. Reintenta.")
                } finally {
                    file.delete()
                    onDone()
                }
            }

            override fun onError(exception: ImageCaptureException) {
                file.delete()
                onError("No se pudo tomar la foto. Reintenta.")
                onDone()
            }
        },
    )
}

/** Abre una búsqueda web manual (pantalla de baja confianza) en el navegador. */
fun openWebSearch(context: Context, baseSearchUrl: String, query: String) {
    val intent = android.content.Intent(
        android.content.Intent.ACTION_VIEW,
        Uri.parse(baseSearchUrl + Uri.encode(query)),
    )
    context.startActivity(intent)
}
