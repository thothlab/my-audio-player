package tech.thothlab.dombra.androidApp

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import tech.thothlab.dombra.di.AppGraph
import tech.thothlab.dombra.di.createAndroidAppGraph
import tech.thothlab.dombra.ui.DombraApp

class AppActivity : ComponentActivity() {

    private val uiScope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
    private lateinit var graph: AppGraph

    // Путь выбранной в онбординге папки (для отображения «Выбрано: …»).
    private val pickedFolder = mutableStateOf<String?>(null)

    private val pickFolder = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri: Uri? ->
        if (uri != null) {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            val name = uri.lastPathSegment ?: "Папка"
            pickedFolder.value = name.substringAfterLast(':').ifEmpty { name }
            uiScope.launch { graph.importTree(uri.toString(), name) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        graph = createAndroidAppGraph(this)
        setContent {
            DombraApp(
                graph = graph,
                onPickFolder = { pickFolder.launch(null) },
                onThemeChanged = { ThemeChanged(it) },
                pickedFolder = pickedFolder.value,
            )
        }
    }

    override fun onDestroy() {
        uiScope.cancel()
        super.onDestroy()
    }
}

@Composable
private fun ThemeChanged(isDark: Boolean) {
    val view = LocalView.current
    LaunchedEffect(isDark) {
        val window = (view.context as Activity).window
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = isDark
            isAppearanceLightNavigationBars = isDark
        }
    }
}
