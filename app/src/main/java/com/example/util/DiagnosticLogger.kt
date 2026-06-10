package com.example.util

import android.util.Log
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

object DiagnosticLogger {
    private const val TAG = "AI_DIAGNOSTIC"

    private val _messageBus = MutableSharedFlow<Long>(extraBufferCapacity = 10)
    val messageBus = _messageBus.asSharedFlow()

    private val _connectionStatus = MutableStateFlow("Disconnected")
    val connectionStatus = _connectionStatus.asStateFlow()

    fun logRequest(prompt: String) {
        Log.i(TAG, "Request Initiated: [${prompt.take(50)}...]")
        _connectionStatus.value = "Connecting..."
    }

    fun logStreamingStart() {
        Log.i(TAG, "Streaming Started from backend")
        _connectionStatus.value = "Connected"
    }

    fun logStreamChunk(chunk: String) {
        Log.i(TAG, "Received chunk: $chunk")
        _messageBus.tryEmit(System.currentTimeMillis())
    }

    fun logComplete() {
        Log.i(TAG, "Response Complete")
        _connectionStatus.value = "Disconnected"
    }
    
    fun logError(error: String) {
        Log.e(TAG, "Network/API Error: $error")
        _connectionStatus.value = "Disconnected"
    }
}
