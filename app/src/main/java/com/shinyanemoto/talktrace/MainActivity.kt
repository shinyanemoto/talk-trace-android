package com.shinyanemoto.talktrace

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.shinyanemoto.talktrace.ui.theme.TalkTraceTheme

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()
    private var pendingAutoStartRecordingSource by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        pendingAutoStartRecordingSource = extractAutoStartRecordingSource(intent)

        setContent {
            TalkTraceTheme {
                TalkTraceApp(
                    viewModel = viewModel,
                    pendingAutoStartRecordingSource = pendingAutoStartRecordingSource,
                    onAutoStartHandled = {
                        pendingAutoStartRecordingSource = null
                        intent?.removeExtra(EXTRA_AUTO_START_RECORDING_SOURCE)
                    },
                )
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingAutoStartRecordingSource = extractAutoStartRecordingSource(intent)
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshPermissionState()
        viewModel.refreshRecordings()
    }

    companion object {
        const val EXTRA_AUTO_START_RECORDING_SOURCE = "auto_start_recording_source"
        const val AUTO_START_SOURCE_TILE = "tile"
        const val AUTO_START_SOURCE_CALL_PROMPT = "call_prompt"
    }

    private fun extractAutoStartRecordingSource(intent: android.content.Intent?): String? {
        return intent?.getStringExtra(EXTRA_AUTO_START_RECORDING_SOURCE)
    }
}
