package tw.fann.bp.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.util.Size
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.lifecycle.awaitInstance
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import tw.fann.bp.data.PhotoLoader

/** 拍照畫面：按下快門的時間就是這筆量測的時間。 */
@Composable
fun CameraScreen(onCaptured: (Bitmap, Long) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val askPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    LaunchedEffect(Unit) { if (!granted) askPermission.launch(Manifest.permission.CAMERA) }

    if (!granted) {
        Column(
            Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
        ) {
            Text("需要使用相機才能拍血壓計", style = MaterialTheme.typography.headlineMedium)
            BigButton("允許使用相機", { askPermission.launch(Manifest.permission.CAMERA) })
            BigButton("返回", onBack, outlined = true)
        }
        return
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }
    val imageCapture = remember {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            // 不需要上億像素，2K 左右就夠辨識，也避免記憶體爆掉
            .setResolutionSelector(
                ResolutionSelector.Builder().setResolutionStrategy(
                    ResolutionStrategy(Size(2048, 1536), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER)
                ).build()
            )
            .build()
    }
    var busy by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val provider = ProcessCameraProvider.awaitInstance(context)
        val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
        provider.unbindAll()
        provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture)
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView({ previewView }, Modifier.fillMaxSize())

        // 對準框
        Column(
            Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "螢幕對準白框",
                color = Color.White,
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.background(Color(0x99000000), RoundedCornerShape(12.dp)).padding(12.dp),
            )
            Box(
                Modifier
                    .padding(top = 32.dp)
                    .fillMaxWidth(0.7f)
                    .aspectRatio(0.8f)
                    .border(BorderStroke(5.dp, Color.White), RoundedCornerShape(16.dp))
            )
        }

        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .safeDrawingPadding()
                .padding(horizontal = 16.dp, vertical = 24.dp),
        ) {
            OutlinedButton(
                onClick = onBack,
                modifier = Modifier.align(Alignment.CenterStart).size(width = 100.dp, height = 72.dp),
                border = BorderStroke(2.dp, Color.White),
            ) { Text("返回", color = Color.White, style = MaterialTheme.typography.labelLarge) }

            Button(
                onClick = {
                    if (busy) return@Button
                    busy = true
                    val takenAt = System.currentTimeMillis()
                    imageCapture.takePicture(
                        ContextCompat.getMainExecutor(context),
                        object : ImageCapture.OnImageCapturedCallback() {
                            override fun onCaptureSuccess(image: ImageProxy) {
                                val bitmap = image.use {
                                    PhotoLoader.scaleDown(PhotoLoader.rotate(it.toBitmap(), it.imageInfo.rotationDegrees))
                                }
                                busy = false
                                onCaptured(bitmap, takenAt)
                            }

                            override fun onError(e: ImageCaptureException) {
                                busy = false
                                Toast.makeText(context, "拍照失敗，請再試一次", Toast.LENGTH_LONG).show()
                            }
                        },
                    )
                },
                modifier = Modifier.align(Alignment.Center).size(112.dp),
                shape = CircleShape,
                border = BorderStroke(6.dp, Color(0xFFCCCCCC)),
                colors = ButtonDefaults.buttonColors(containerColor = Color.White),
            ) { Text("拍", color = Color.Black, style = MaterialTheme.typography.headlineLarge) }
        }
    }
}
