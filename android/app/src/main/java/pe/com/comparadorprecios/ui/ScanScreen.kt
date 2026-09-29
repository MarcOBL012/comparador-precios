package pe.com.comparadorprecios.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pe.com.comparadorprecios.capture.CaptureAction
import pe.com.comparadorprecios.capture.CaptureAdvisor
import pe.com.comparadorprecios.capture.CaptureConditions
import pe.com.comparadorprecios.capture.CaptureConditionsMonitor
import pe.com.comparadorprecios.capture.Haptics
import pe.com.comparadorprecios.capture.Lighting
import pe.com.comparadorprecios.capture.Steadiness
import pe.com.comparadorprecios.context.ScanPolicy
import pe.com.comparadorprecios.util.ImageEncoding
import pe.com.comparadorprecios.util.ThumbnailEncoder
import java.io.File
import java.util.concurrent.Executor
import java.util.concurrent.Executors

/** Pantalla de escaneo — captura puntual con CameraX (no streaming a IA). */
@Composable
fun ScanScreen(
    policy: ScanPolicy,
    selectedCategory: String?,
    onCategoryChange: (String?) -> Unit,
    pendingCount: Int,
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
    // Decodificar y comprimir una foto de 12 MP en el hilo principal congela la UI en gama baja (ANR).
    val processingExecutor = remember { Executors.newSingleThreadExecutor() }
    DisposableEffect(Unit) {
        onDispose { processingExecutor.shutdown() }
    }

    var camera by remember { mutableStateOf<Camera?>(null) }
    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    LaunchedEffect(previewView) {
        val provider = ProcessCameraProvider.getInstance(context).get()
        val preview = Preview.Builder().build().also {
            it.setSurfaceProvider(previewView.surfaceProvider)
        }
        provider.unbindAll()
        camera = provider.bindToLifecycle(lifecycle, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture)
        cameraProvider = provider
    }
    // La cámara queda atada al lifecycle de la Activity, no al de este composable: si se sale de
    // esta pantalla (p.ej. justo después de capturar, mientras se analiza con Gemini) con el
    // flash prendido, apagar solo el torch no basta — una última lectura del sensor de luz puede
    // llegar justo al cerrar y volver a prenderlo antes de que el composable termine de
    // desmontarse. unbindAll() cierra la sesión de cámara por completo: ya no hay flash que prender.
    DisposableEffect(cameraProvider) {
        onDispose { cameraProvider?.unbindAll() }
    }

    // Asistente de captura (Taller 2): acelerómetro + sensor de luz deciden en conjunto si el
    // teléfono está listo para la foto, y accionan flash + vibración solos, sin tocar la pantalla.
    val captureConditionsMonitor = remember { CaptureConditionsMonitor(context) }
    val captureConditions by remember { captureConditionsMonitor.observe() }
        .collectAsStateWithLifecycle(initialValue = CaptureConditions(Steadiness.STEADY, Lighting.BRIGHT))
    val captureAction = CaptureAdvisor.decide(captureConditions)
    val captureAssistEnabled = captureConditionsMonitor.hasBothSensors

    LaunchedEffect(captureAction, camera) {
        if (captureAssistEnabled) {
            camera?.cameraControl?.enableTorch(captureAction == CaptureAction.READY_DARK)
        }
    }
    LaunchedEffect(captureAction) {
        if (captureAssistEnabled && captureAction != CaptureAction.WAIT_STEADY) {
            Haptics.vibrateReady(context)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
        Column(
            modifier = Modifier.align(Alignment.TopCenter).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ContextNotices(notices = policy.notices)
            if (captureAssistEnabled) {
                ContextNotices(
                    notices = listOfNotNull(captureAdvisoryNotice(captureAction)),
                    urgent = captureAction == CaptureAction.WAIT_STEADY,
                )
            }
            if (pendingCount > 0) {
                ContextNotices(
                    notices = listOf(
                        if (pendingCount == 1) "1 escaneo pendiente por procesar."
                        else "$pendingCount escaneos pendientes por procesar."
                    ),
                )
            }
        }
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f))))
                .padding(top = 32.dp, bottom = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "¿Qué vas a escanear?",
                color = Color.White,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(8.dp))
            CategoryChips(selectedId = selectedCategory, onSelect = onCategoryChange)
            Spacer(Modifier.height(16.dp))
            ShutterButton(
                enabled = !capturing,
                capturing = capturing,
                offline = policy.offline,
                onClick = {
                    capturing = true
                    capturePhoto(
                        context = context,
                        imageCapture = imageCapture,
                        maxImageSidePx = policy.maxImageSidePx,
                        processingExecutor = processingExecutor,
                        onImageCaptured = onImageCaptured,
                        onError = onError,
                    ) { capturing = false }
                },
            )
            Spacer(Modifier.height(8.dp))
            Text(
                when {
                    capturing -> "Capturando…"
                    policy.offline -> "Sin conexión: la foto quedará pendiente"
                    else -> "Enfoca la marca y el nombre del producto"
                },
                color = Color.White.copy(alpha = 0.9f),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

/** Texto para la etapa ADAPTACIÓN del asistente de captura; `null` = todo bien, sin nada que avisar. */
private fun captureAdvisoryNotice(action: CaptureAction): String? = when (action) {
    CaptureAction.WAIT_STEADY -> "Sostén firme el teléfono para una foto nítida."
    CaptureAction.READY_DARK -> "Poca luz: flash activado automáticamente."
    CaptureAction.READY_BRIGHT -> null
}

/** Elegir la categoría antes de la foto afina qué tiendas se consultan; "Automático" deja decidir a la IA. */
@Composable
private fun CategoryChips(selectedId: String?, onSelect: (String?) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(PRODUCT_CATEGORIES, key = { it.id ?: "auto" }) { category ->
            val selected = category.id == selectedId
            FilterChip(
                selected = selected,
                onClick = { onSelect(category.id) },
                label = { Text(category.label) },
                leadingIcon = { Icon(category.icon, contentDescription = null, modifier = Modifier.size(18.dp)) },
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = Color.White.copy(alpha = 0.92f),
                    labelColor = MaterialTheme.colorScheme.onSurface,
                    iconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    selectedContainerColor = MaterialTheme.colorScheme.tertiary,
                    selectedLabelColor = MaterialTheme.colorScheme.onTertiary,
                    selectedLeadingIconColor = MaterialTheme.colorScheme.onTertiary,
                ),
                border = null,
            )
        }
    }
}

/**
 * Sin conexión el disparador sigue activo (requisito del modo SIN CONEXIÓN): solo cambia el color
 * del aro y el ícono, para que se note que la foto se guardará en vez de procesarse al instante.
 */
@Composable
private fun ShutterButton(enabled: Boolean, capturing: Boolean, offline: Boolean, onClick: () -> Unit) {
    val accent = if (offline) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.tertiary
    val ring = if (enabled) accent else Color.White.copy(alpha = 0.4f)
    Box(
        modifier = Modifier
            .size(84.dp)
            .border(4.dp, ring, CircleShape)
            .padding(8.dp)
            .background(if (enabled) Color.White else Color.White.copy(alpha = 0.5f), CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        when {
            capturing -> CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
            else -> Icon(
                if (offline) Icons.Filled.CloudOff else Icons.Filled.PhotoCamera,
                contentDescription = if (offline) "Tomar foto y guardar como pendiente" else "Escanear producto",
                tint = accent,
                modifier = Modifier.size(32.dp),
            )
        }
    }
}

private fun capturePhoto(
    context: Context,
    imageCapture: ImageCapture,
    maxImageSidePx: Int,
    processingExecutor: Executor,
    onImageCaptured: (String, ByteArray?) -> Unit,
    onError: (String) -> Unit,
    onDone: () -> Unit,
) {
    val file = File.createTempFile("scan_", ".jpg", context.cacheDir)
    val options = ImageCapture.OutputFileOptions.Builder(file).build()
    val mainExecutor: Executor = ContextCompat.getMainExecutor(context)
    imageCapture.takePicture(
        options,
        processingExecutor,
        object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                try {
                    val bytes = file.readBytes()
                    val bitmap = ImageEncoding.decode(bytes)
                    val payload = if (bitmap != null) {
                        ImageEncoding.compressToLimit(bitmap, maxSidePx = maxImageSidePx)
                    } else {
                        bytes
                    }
                    val dataUri = ImageEncoding.toDataUri(payload)
                    if (ImageEncoding.exceedsLimit(dataUri)) {
                        mainExecutor.execute {
                            onError("La foto es demasiado grande incluso comprimida. Acércate menos o baja la resolución.")
                        }
                    } else {
                        val thumbnail = if (bitmap != null) {
                            runCatching { ThumbnailEncoder.encode(bitmap) }.getOrNull()
                        } else {
                            null
                        }
                        mainExecutor.execute { onImageCaptured(dataUri, thumbnail) }
                    }
                } catch (e: Exception) {
                    mainExecutor.execute { onError("No se pudo procesar la foto. Reintenta.") }
                } finally {
                    file.delete()
                    mainExecutor.execute(onDone)
                }
            }

            override fun onError(exception: ImageCaptureException) {
                file.delete()
                mainExecutor.execute {
                    onError("No se pudo tomar la foto. Reintenta.")
                    onDone()
                }
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
