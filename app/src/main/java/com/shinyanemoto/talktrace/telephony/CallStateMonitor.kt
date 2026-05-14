package com.shinyanemoto.talktrace.telephony

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class TalkTraceCallState(
    val displayLabel: String,
    val logLabel: String,
) {
    Idle(displayLabel = "待受中", logLabel = "IDLE"),
    Ringing(displayLabel = "着信中", logLabel = "RINGING"),
    Offhook(displayLabel = "通話中", logLabel = "OFFHOOK"),
    NoPermission(displayLabel = "権限なし", logLabel = "NO_PERMISSION"),
    Unsupported(displayLabel = "未対応", logLabel = "UNSUPPORTED"),
}

class CallStateMonitor(private val context: Context) {
    private val appContext = context.applicationContext
    private val telephonyManager = appContext.getSystemService(TelephonyManager::class.java)
    private val isTelephonySupported =
        appContext.packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY) &&
            telephonyManager != null

    private val _state = MutableStateFlow(
        if (isTelephonySupported) TalkTraceCallState.NoPermission else TalkTraceCallState.Unsupported,
    )
    val state: StateFlow<TalkTraceCallState> = _state.asStateFlow()

    private var isRegistered = false

    private val telephonyCallback =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            object : TelephonyCallback(), TelephonyCallback.CallStateListener {
                override fun onCallStateChanged(state: Int) {
                    publishState(state)
                }
            }
        } else {
            null
        }

    @Suppress("DEPRECATION")
    private val phoneStateListener = object : PhoneStateListener() {
        @Suppress("DEPRECATION")
        override fun onCallStateChanged(state: Int, phoneNumber: String?) {
            publishState(state)
        }
    }

    fun refresh() {
        if (!isTelephonySupported) {
            setState(TalkTraceCallState.Unsupported)
            return
        }

        if (!hasPhoneStatePermission()) {
            unregister()
            setState(TalkTraceCallState.NoPermission)
            return
        }

        registerIfNeeded()
        @Suppress("DEPRECATION")
        publishState(telephonyManager?.callState ?: TelephonyManager.CALL_STATE_IDLE)
    }

    fun stop() {
        unregister()
    }

    private fun registerIfNeeded() {
        if (isRegistered || telephonyManager == null) {
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            telephonyManager.registerTelephonyCallback(
                appContext.mainExecutor,
                telephonyCallback ?: return,
            )
        } else {
            @Suppress("DEPRECATION")
            telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_CALL_STATE)
        }
        isRegistered = true
    }

    private fun unregister() {
        if (!isRegistered || telephonyManager == null) {
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            telephonyManager.unregisterTelephonyCallback(telephonyCallback ?: return)
        } else {
            @Suppress("DEPRECATION")
            telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_NONE)
        }
        isRegistered = false
    }

    private fun publishState(state: Int) {
        val mappedState = when (state) {
            TelephonyManager.CALL_STATE_RINGING -> TalkTraceCallState.Ringing
            TelephonyManager.CALL_STATE_OFFHOOK -> TalkTraceCallState.Offhook
            else -> TalkTraceCallState.Idle
        }
        setState(mappedState)
    }

    private fun setState(newState: TalkTraceCallState) {
        if (_state.value == newState) {
            return
        }

        _state.value = newState
        Log.d(LOG_TAG, "TalkTrace CallState: ${newState.logLabel}")
    }

    private fun hasPhoneStatePermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.READ_PHONE_STATE,
        ) == PackageManager.PERMISSION_GRANTED
    }

    companion object {
        private const val LOG_TAG = "TalkTrace"
    }
}
