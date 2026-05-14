package com.shinyanemoto.talktrace

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.shinyanemoto.talktrace.ui.theme.TalkTraceTheme

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()
    private var shouldStartRecordingFromTile: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        shouldStartRecordingFromTile =
            intent?.getBooleanExtra(EXTRA_START_RECORDING_FROM_TILE, false) == true

        setContent {
            TalkTraceTheme {
                TalkTraceApp(
                    viewModel = viewModel,
                    startRecordingFromTile = shouldStartRecordingFromTile,
                    onTileLaunchHandled = {
                        shouldStartRecordingFromTile = false
                        intent?.removeExtra(EXTRA_START_RECORDING_FROM_TILE)
                    },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshPermissionState()
        viewModel.refreshRecordings()
    }

    companion object {
        const val EXTRA_START_RECORDING_FROM_TILE = "start_recording_from_tile"
    }
}
