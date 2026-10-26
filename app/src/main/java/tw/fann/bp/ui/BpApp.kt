package tw.fann.bp.ui

import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tw.fann.bp.data.FailedPhotoStore
import tw.fann.bp.data.PhotoLoader
import tw.fann.bp.data.Reading
import tw.fann.bp.data.ReadingRepository
import tw.fann.bp.ocr.BpReader

sealed interface Screen {
    data object Home : Screen
    data object Camera : Screen
    data class Saved(val reading: Reading) : Screen
    data class Retry(val photo: Bitmap) : Screen
    data class Edit(val reading: Reading) : Screen
    data object History : Screen
}

@Composable
fun BpApp(repo: ReadingRepository, failedPhotos: FailedPhotoStore) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var screen by remember { mutableStateOf<Screen>(Screen.Home) }
    var readings by remember { mutableStateOf(repo.all()) }
    var analyzing by remember { mutableStateOf(false) }

    fun refresh() = scope.launch { readings = withContext(Dispatchers.IO) { repo.all() } }

    /** 拍完 / 選完照片立刻分析：讀到就直接存檔，讀不到就請使用者重拍。 */
    fun analyze(photo: Bitmap, takenAt: Long) {
        analyzing = true
        scope.launch {
            val values = runCatching { BpReader.read(photo) }.getOrNull()
            if (values != null) {
                val saved = withContext(Dispatchers.IO) {
                    val id = repo.save(takenAt, values.systolic, values.diastolic, values.pulse, photo)
                    repo.all().also { readings = it }.first { it.id == id }
                }
                screen = Screen.Saved(saved)
            } else {
                withContext(Dispatchers.IO) { failedPhotos.save(photo, takenAt) }
                screen = Screen.Retry(photo)
            }
            analyzing = false
        }
    }

    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val loaded = runCatching {
            PhotoLoader.load({ context.contentResolver.openInputStream(uri) ?: error("無法開啟照片") })
        }.getOrElse {
            Toast.makeText(context, "這張照片讀不到", Toast.LENGTH_LONG).show()
            return@rememberLauncherForActivityResult
        }
        analyze(loaded.bitmap, loaded.takenAt ?: System.currentTimeMillis())
    }

    BackHandler(enabled = screen != Screen.Home && !analyzing) { screen = Screen.Home }

    Box(Modifier.fillMaxSize()) {
        when (val s = screen) {
            Screen.Home -> HomeScreen(
                readings = readings,
                onCamera = { screen = Screen.Camera },
                onGallery = { pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                onHistory = { screen = Screen.History },
            )

            Screen.Camera -> CameraScreen(
                onCaptured = { photo, takenAt -> analyze(photo, takenAt) },
                onBack = { screen = Screen.Home },
            )

            is Screen.Saved -> SavedScreen(
                reading = s.reading,
                onEdit = { screen = Screen.Edit(s.reading) },
                onDone = { screen = Screen.Home },
            )

            is Screen.Retry -> RetryScreen(
                photo = s.photo,
                onRetake = { screen = Screen.Camera },
                onHome = { screen = Screen.Home },
            )

            is Screen.Edit -> EditScreen(
                reading = s.reading,
                onSave = { sys, dia, pulse ->
                    scope.launch {
                        val id = s.reading.id
                        val saved = withContext(Dispatchers.IO) {
                            repo.update(id, sys, dia, pulse)
                            repo.all().also { readings = it }.first { it.id == id }
                        }
                        screen = Screen.Saved(saved)
                    }
                },
                onCancel = { screen = Screen.Home },
            )

            Screen.History -> HistoryScreen(
                readings = readings,
                onEdit = { screen = Screen.Edit(it) },
                onDelete = { r ->
                    scope.launch {
                        withContext(Dispatchers.IO) { repo.delete(r) }
                        refresh()
                    }
                },
                onBack = { screen = Screen.Home },
            )
        }

        if (analyzing) {
            Box(
                Modifier.fillMaxSize().background(Color(0xCC000000)),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(24.dp)) {
                    CircularProgressIndicator(Modifier.size(80.dp), color = Color.White, strokeWidth = 8.dp)
                    Text("正在讀取數字…", color = Color.White, style = MaterialTheme.typography.headlineMedium)
                }
            }
        }
    }
}
