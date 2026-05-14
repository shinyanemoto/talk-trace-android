package com.shinyanemoto.talktrace

import android.Manifest
import android.app.Activity
import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.pm.PackageInfoCompat
import com.shinyanemoto.talktrace.data.RecordingItem
import com.shinyanemoto.talktrace.telephony.TalkTraceCallState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class Screen {
    Home,
    Recordings,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TalkTraceApp(
    viewModel: MainViewModel,
    pendingAutoStartRecordingSource: String? = null,
    onAutoStartHandled: () -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var currentScreen by rememberSaveable { mutableStateOf(Screen.Home) }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = viewModel::onPermissionsResult,
    )
    val phoneStatePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = viewModel::onPhoneStatePermissionResult,
    )

    LaunchedEffect(uiState.statusMessage) {
        val message = uiState.statusMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.clearStatusMessage()
    }

    LaunchedEffect(pendingAutoStartRecordingSource) {
        val source = pendingAutoStartRecordingSource ?: return@LaunchedEffect
        viewModel.handleAutoStartRecording(source)
        onAutoStartHandled()
    }

    LaunchedEffect(uiState.callState, uiState.hasPhoneStatePermission, uiState.hasNotificationPermission) {
        if (
            uiState.callState == TalkTraceCallState.Offhook &&
            uiState.hasPhoneStatePermission &&
            !uiState.hasNotificationPermission
        ) {
            viewModel.showStatusMessage("通話中ですが、通知権限がないため録音開始の提案通知を表示できません。")
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(if (currentScreen == Screen.Home) "TalkTrace" else "録音ファイル")
                },
                navigationIcon = {
                    if (currentScreen == Screen.Recordings) {
                        IconButton(onClick = { currentScreen = Screen.Home }) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = "ホームへ戻る",
                            )
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    ) { innerPadding ->
        when (currentScreen) {
            Screen.Home -> HomeScreen(
                uiState = uiState,
                innerPadding = innerPadding,
                onOpenRecordings = {
                    viewModel.refreshRecordings()
                    currentScreen = Screen.Recordings
                },
                onStartRecording = viewModel::startRecording,
                onStopRecording = viewModel::stopRecording,
                onRequestPermission = {
                    permissionLauncher.launch(requiredPermissions())
                },
                onRequestPhoneStatePermission = {
                    phoneStatePermissionLauncher.launch(Manifest.permission.READ_PHONE_STATE)
                },
                onRequestAddTile = {
                    requestTileAddition(
                        activity = it,
                        onMessage = viewModel::showStatusMessage,
                    )
                },
            )

            Screen.Recordings -> RecordingsScreen(
                uiState = uiState,
                innerPadding = innerPadding,
                onRefresh = viewModel::refreshRecordings,
                onTogglePlayback = viewModel::togglePlayback,
                onShare = { recording, context ->
                    context.startActivity(
                        Intent.createChooser(
                            viewModel.buildShareIntent(recording),
                            "録音ファイルを共有",
                        ),
                    )
                },
                onDelete = viewModel::deleteRecording,
            )
        }
    }
}

@Composable
private fun HomeScreen(
    uiState: MainUiState,
    innerPadding: PaddingValues,
    onOpenRecordings: () -> Unit,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onRequestPermission: () -> Unit,
    onRequestPhoneStatePermission: () -> Unit,
    onRequestAddTile: (Activity) -> Unit,
) {
    val activity = LocalContext.current.findActivity()
    val context = LocalContext.current
    val packageInfo = remember(context) {
        context.packageManager.getPackageInfo(context.packageName, 0)
    }
    val versionLabel = remember(packageInfo) {
        "Version ${packageInfo.versionName} (${PackageInfoCompat.getLongVersionCode(packageInfo)})"
    }
    val buildTimeLabel = remember(context) {
        "Build ${context.getString(R.string.build_time)}"
    }
    val showAudioRationale = activity?.let {
        ActivityCompat.shouldShowRequestPermissionRationale(
            it,
            Manifest.permission.RECORD_AUDIO,
        )
    } ?: false
    val showNotificationRationale = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        activity?.let {
            ActivityCompat.shouldShowRequestPermissionRationale(
                it,
                Manifest.permission.POST_NOTIFICATIONS,
            )
        } ?: false
    } else {
        false
    }
    val showPhoneStateRationale = activity?.let {
        ActivityCompat.shouldShowRequestPermissionRationale(
            it,
            Manifest.permission.READ_PHONE_STATE,
        )
    } ?: false
    val missingPermissionMessage = when {
        !uiState.hasAudioPermission && !uiState.hasNotificationPermission ->
            "録音開始にはマイク権限と通知権限が必要です。通知はバックグラウンド録音の停止操作に使います。"

        !uiState.hasAudioPermission ->
            if (showAudioRationale) {
                "録音を始めるにはマイク権限が必要です。自分の発話だけを保存するために利用します。"
            } else {
                "初回録音前にマイク権限を許可してください。許可がないと録音開始できません。"
            }

        !uiState.hasNotificationPermission ->
            if (showNotificationRationale) {
                "通知権限が必要です。録音中通知と通話中の録音提案通知を表示します。"
            } else {
                "バックグラウンド録音や通話中の録音提案には通知権限が必要です。"
            }

        else -> null
    }
    val callPromptStatusLabel = when {
        !uiState.hasPhoneStatePermission -> "無効"
        !uiState.hasNotificationPermission -> "通知権限なし"
        uiState.isCallRecordingPromptVisible -> "表示中"
        else -> "非表示"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
            ),
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = "自分の声を記録して、会話後の振り返りに使う発話ログアプリです。",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "TalkTrace はマイク入力を録音します。Android の制限上、相手側の通話音声取得は想定していません。",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "通話状態: ${uiState.callState.displayLabel}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "録音提案通知: $callPromptStatusLabel",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = "通常電話の待受中・着信中・通話中を検知し、通話中は録音開始を提案します。",
                    style = MaterialTheme.typography.bodyMedium,
                )

                if (uiState.callState == TalkTraceCallState.NoPermission && uiState.isTelephonySupported) {
                    FilledTonalButton(
                        onClick = onRequestPhoneStatePermission,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("通話状態の権限を許可")
                    }

                    Text(
                        text = if (showPhoneStateRationale) {
                            "READ_PHONE_STATE は通常電話の通話状態だけを検知するために使います。電話番号や通話履歴は取得しません。"
                        } else {
                            "通常電話の状態を検知するには権限が必要です。録音機能とは独立しており、拒否しても録音は使えます。"
                        },
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }

        if (uiState.isRecording) {
            AssistChip(
                onClick = {},
                label = { Text("録音中") },
                leadingIcon = {
                    Icon(imageVector = Icons.Default.Mic, contentDescription = null)
                },
            )

            Text(
                text = formatDuration(uiState.recordingElapsedMillis),
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Bold,
            )

            Text(
                text = "バックグラウンドで録音中です。\n通知からも停止できます。",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )

            Button(
                onClick = onStopRecording,
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(vertical = 18.dp),
            ) {
                Text("録音停止")
            }
        } else {
            Text(
                text = "録音開始を押すと、自分の声の記録を保存します。",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )

            Button(
                onClick = onStartRecording,
                enabled = uiState.hasAudioPermission && uiState.hasNotificationPermission,
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(vertical = 18.dp),
            ) {
                Text("録音開始")
            }

            if (missingPermissionMessage != null) {
                FilledTonalButton(
                    onClick = onRequestPermission,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("必要な権限を許可")
                }

                Text(
                    text = missingPermissionMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
            }
        }

        OutlinedButton(
            onClick = onOpenRecordings,
            enabled = !uiState.isRecording,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(imageVector = Icons.Default.FolderOpen, contentDescription = null)
            Text(" 録音一覧を見る")
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && activity != null) {
            FilledTonalButton(
                onClick = { onRequestAddTile(activity) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("クイック設定に追加")
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = versionLabel,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = buildTimeLabel,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun RecordingsScreen(
    uiState: MainUiState,
    innerPadding: PaddingValues,
    onRefresh: () -> Unit,
    onTogglePlayback: (RecordingItem) -> Unit,
    onShare: (RecordingItem, Context) -> Unit,
    onDelete: (RecordingItem) -> Unit,
) {
    val context = LocalContext.current
    var pendingDelete by remember { mutableStateOf<RecordingItem?>(null) }

    LaunchedEffect(Unit) {
        onRefresh()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding),
    ) {
        if (uiState.recordings.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "まだ録音ファイルがありません。\nホーム画面から自分の声を記録してください。",
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(
                    items = uiState.recordings,
                    key = { it.file.absolutePath },
                ) { recording ->
                    RecordingCard(
                        recording = recording,
                        isPlaying = uiState.currentlyPlayingPath == recording.file.absolutePath,
                        onTogglePlayback = { onTogglePlayback(recording) },
                        onShare = { onShare(recording, context) },
                        onDelete = { pendingDelete = recording },
                    )
                }
            }
        }
    }

    pendingDelete?.let { recording ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("録音ファイルを削除") },
            text = { Text("${recording.fileName} を削除しますか？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete(recording)
                        pendingDelete = null
                    },
                ) {
                    Text("削除する")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text("キャンセル")
                }
            },
        )
    }
}

@Composable
private fun RecordingCard(
    recording: RecordingItem,
    isPlaying: Boolean,
    onTogglePlayback: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = recording.fileName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "録音日時: ${formatRecordedAt(recording.recordedAtMillis)}",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = "録音時間: ${formatDuration(recording.durationMillis)}",
                style = MaterialTheme.typography.bodyMedium,
            )

            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilledTonalButton(
                    onClick = onTogglePlayback,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(
                        imageVector = if (isPlaying) {
                            Icons.Default.PauseCircle
                        } else {
                            Icons.Default.PlayCircle
                        },
                        contentDescription = null,
                    )
                    Text(if (isPlaying) " 再生停止" else " 再生")
                }

                OutlinedButton(
                    onClick = onShare,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(imageVector = Icons.Default.Share, contentDescription = null)
                    Text(" 共有")
                }

                OutlinedButton(
                    onClick = onDelete,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(imageVector = Icons.Default.Delete, contentDescription = null)
                    Text(" 削除")
                }
            }
        }
    }
}

private fun formatDuration(durationMillis: Long): String {
    val totalSeconds = durationMillis.coerceAtLeast(0L) / 1_000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}

private fun formatRecordedAt(recordedAtMillis: Long): String {
    val formatter = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault())
    return formatter.format(Date(recordedAtMillis))
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private fun requiredPermissions(): Array<String> {
    return buildList {
        add(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }.toTypedArray()
}

private fun requestTileAddition(
    activity: Activity,
    onMessage: (String) -> Unit,
) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        onMessage("この端末ではアプリ内からのタイル追加に対応していません。")
        return
    }

    val statusBarManager = activity.getSystemService(StatusBarManager::class.java)
    val componentName = ComponentName(activity, com.shinyanemoto.talktrace.recording.TalkTraceTileService::class.java)
    val icon = Icon.createWithResource(activity, R.drawable.ic_tile_talktrace)

    statusBarManager.requestAddTileService(
        componentName,
        "TalkTrace",
        icon,
        activity.mainExecutor,
    ) { result ->
        val message = when (result) {
            StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED ->
                "クイック設定に TalkTrace を追加しました。"

            StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED ->
                "TalkTrace タイルはすでに追加されています。"

            StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_NOT_ADDED ->
                "タイル追加はキャンセルされました。"

            else ->
                "クイック設定タイルを追加できませんでした。通知パネルの編集画面から追加を試してください。"
        }
        onMessage(message)
    }
}
