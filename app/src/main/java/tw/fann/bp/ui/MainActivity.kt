package tw.fann.bp.ui

import android.content.Intent
import android.os.Bundle
import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import tw.fann.bp.R
import tw.fann.bp.data.FailedPhotoStore
import tw.fann.bp.data.ReadingRepository

class MainActivity : ComponentActivity() {

    private lateinit var repo: ReadingRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // App 固定淺色主題，系統列圖示也固定用深色，避免深色模式下看不到
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        repo = ReadingRepository(this)
        if (intent.getBooleanExtra(EXTRA_PIN_SHORTCUT, false)) requestHomeShortcut()
        setContent {
            BpTheme {
                Surface(Modifier.fillMaxSize()) { BpApp(repo, FailedPhotoStore(this)) }
            }
        }
    }

    /** 請桌面加一個捷徑（系統會跳確認視窗）。由 adb 帶 extra 觸發，App 內沒有入口。 */
    private fun requestHomeShortcut() {
        val shortcut = ShortcutInfoCompat.Builder(this, "home")
            .setShortLabel(getString(R.string.app_name))
            .setIcon(IconCompat.createWithResource(this, R.mipmap.ic_launcher))
            .setIntent(Intent(this, MainActivity::class.java).setAction(Intent.ACTION_MAIN))
            .build()
        ShortcutManagerCompat.requestPinShortcut(this, shortcut, null)
    }

    override fun onDestroy() {
        repo.close()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_PIN_SHORTCUT = "pin_shortcut"
    }
}
