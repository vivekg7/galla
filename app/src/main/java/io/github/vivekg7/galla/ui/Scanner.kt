package io.github.vivekg7.galla.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.Uri
import android.provider.Settings
import android.view.HapticFeedbackConstants
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.mlkit.vision.MlKitAnalyzer
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode

/** The same code seen again within this window is the same item still in view, not a second one. */
private const val SAME_CODE_GAP_MS = 1500L

/**
 * The camera with ML Kit's bundled barcode model: it reads codes with no
 * internet and no Play-services download. Calls [onCode] once per item put in
 * front of it, with a beep and a buzz; nothing while [paused].
 */
@Composable
fun Scanner(
    onCode: (String) -> Unit,
    modifier: Modifier = Modifier,
    paused: Boolean = false,
) {
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val activity = LocalActivity.current

    // After a second "Don't allow" Android stops asking; only the app's settings page can grant it then.
    var blocked by remember { mutableStateOf(false) }
    val ask =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
            granted = ok
            blocked = !ok && activity != null && !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.CAMERA)
        }
    LaunchedEffect(Unit) { if (!granted) ask.launch(Manifest.permission.CAMERA) }
    // Back from the settings page with the camera allowed.
    LifecycleResumeEffect(Unit) {
        granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        onPauseOrDispose {}
    }

    Box(modifier.clip(RoundedCornerShape(16.dp)).background(Color.Black), contentAlignment = Alignment.Center) {
        if (!granted) {
            Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Galla needs the camera to scan barcodes.", color = Color.White, textAlign = TextAlign.Center)
                Button(
                    onClick = {
                        if (blocked) {
                            context.startActivity(
                                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
                            )
                        } else {
                            ask.launch(Manifest.permission.CAMERA)
                        }
                    },
                    Modifier.padding(top = 8.dp),
                ) { Text(if (blocked) "Open settings" else "Allow camera") }
            }
        } else {
            Camera(onCode, paused)
        }
    }
}

@Composable
private fun Camera(
    onCode: (String) -> Unit,
    paused: Boolean,
) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val view = LocalView.current
    val currentOnCode by rememberUpdatedState(onCode)
    val currentPaused by rememberUpdatedState(paused)
    var torch by remember { mutableStateOf(false) }

    val scanner =
        remember {
            BarcodeScanning.getClient(
                BarcodeScannerOptions
                    .Builder()
                    .setBarcodeFormats(
                        Barcode.FORMAT_EAN_13,
                        Barcode.FORMAT_EAN_8,
                        Barcode.FORMAT_UPC_A,
                        Barcode.FORMAT_UPC_E,
                        Barcode.FORMAT_CODE_128,
                        Barcode.FORMAT_CODE_39,
                        Barcode.FORMAT_CODE_93,
                        Barcode.FORMAT_ITF,
                        Barcode.FORMAT_QR_CODE,
                    ).build(),
            )
        }
    val beep = remember { runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, 70) }.getOrNull() }
    val controller =
        remember {
            LifecycleCameraController(context).apply {
                cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                setEnabledUseCases(CameraController.IMAGE_ANALYSIS)
                var last = ""
                var lastAt = 0L
                val main = ContextCompat.getMainExecutor(context)
                setImageAnalysisAnalyzer(
                    main,
                    MlKitAnalyzer(listOf(scanner), ImageAnalysis.COORDINATE_SYSTEM_ORIGINAL, main) { result ->
                        val code = result.getValue(scanner)?.firstNotNullOfOrNull { it.rawValue?.trim()?.ifEmpty { null } } ?: return@MlKitAnalyzer
                        val now = System.currentTimeMillis()
                        val repeat = code == last && now - lastAt < SAME_CODE_GAP_MS
                        // Every sighting extends the window: an item held still is counted once.
                        last = code
                        lastAt = now
                        if (repeat || currentPaused) return@MlKitAnalyzer
                        beep?.startTone(ToneGenerator.TONE_PROP_BEEP, 120)
                        view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                        currentOnCode(code)
                    },
                )
            }
        }
    DisposableEffect(owner) {
        controller.bindToLifecycle(owner)
        onDispose { controller.unbind() }
    }
    DisposableEffect(Unit) {
        onDispose {
            scanner.close()
            beep?.release()
        }
    }

    Box(Modifier.fillMaxSize()) {
        AndroidView(
            factory = {
                PreviewView(it).apply {
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                    this.controller = controller
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
        // Where to hold the barcode.
        Box(
            Modifier
                .align(Alignment.Center)
                .fillMaxWidth(0.75f)
                .height(2.dp)
                .background(if (paused) Color.Gray else Color.Red),
        )
        FilledTonalIconButton(
            onClick = {
                torch = !torch
                controller.enableTorch(torch)
            },
            modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
            colors =
                if (torch) {
                    IconButtonDefaults.filledTonalIconButtonColors(containerColor = Color(0xFFFFD54F))
                } else {
                    IconButtonDefaults.filledTonalIconButtonColors()
                },
        ) { Icon(Icons.Flash, "Torch") }
    }
}

/** A full-screen scanner for one code: a product's barcode field, or restocking from the shelf. */
@Composable
fun ScanDialog(
    title: String,
    onCode: (String) -> Unit,
    onDismiss: () -> Unit,
    paused: Boolean = false,
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = Color.Black) {
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxWidth().padding(8.dp)) {
                    IconButton(onClick = onDismiss, colors = IconButtonDefaults.iconButtonColors(contentColor = Color.White)) {
                        Icon(Icons.Close, "Close")
                    }
                    Text(
                        title,
                        Modifier.align(Alignment.Center),
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                Scanner(
                    onCode,
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(12.dp)
                        .border(1.dp, Color.DarkGray, RoundedCornerShape(16.dp)),
                    paused = paused,
                )
                Text(
                    "Hold the barcode across the red line",
                    Modifier.fillMaxWidth().padding(16.dp),
                    color = Color.LightGray,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
