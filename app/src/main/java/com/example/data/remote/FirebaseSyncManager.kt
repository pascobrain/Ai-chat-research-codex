package com.example.data.remote

import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class SyncStatus {
    OFFLINE, SYNCING, SYNCED, ERROR, NOT_CONFIGURED
}

object FirebaseSyncManager {
    private val _syncStatus = MutableStateFlow(SyncStatus.NOT_CONFIGURED)
    val syncStatus: StateFlow<SyncStatus> = _syncStatus.asStateFlow()

    private var db: FirebaseFirestore? = null
    private var auth: FirebaseAuth? = null

    fun initialize(context: Context) {
        try {
            if (FirebaseApp.getApps(context).isEmpty()) {
                // To actually use Firebase, users need to provide google-services.json at build time
                // or supply explicit options here. If not configured, this falls back safely.
                Log.w("FirebaseSync", "Firebase not fully configured. Using mock sync status or offline mode.")
                _syncStatus.value = SyncStatus.NOT_CONFIGURED
                return
            }
            
            db = FirebaseFirestore.getInstance()
            auth = FirebaseAuth.getInstance()

            // Explicitly configure Firestore for offline persistence/caching
            val settings = com.google.firebase.firestore.FirebaseFirestoreSettings.Builder()
                .setLocalCacheSettings(
                    com.google.firebase.firestore.PersistentCacheSettings.newBuilder()
                        .setSizeBytes(com.google.firebase.firestore.FirebaseFirestoreSettings.CACHE_SIZE_UNLIMITED)
                        .build()
                )
                .build()
            db?.firestoreSettings = settings
            
            // Listen to auth
            auth?.addAuthStateListener { firebaseAuth ->
                if (firebaseAuth.currentUser != null) {
                    _syncStatus.value = SyncStatus.SYNCED
                } else {
                    _syncStatus.value = SyncStatus.OFFLINE
                }
            }

        } catch (e: Exception) {
            Log.e("FirebaseSync", "Firebase Initialization failed: ${e.message}")
            _syncStatus.value = SyncStatus.NOT_CONFIGURED
        }
    }

    fun syncChatToCloud(chatId: String, data: Map<String, Any>) {
        if (db == null || auth?.currentUser == null) return
        
        _syncStatus.value = SyncStatus.SYNCING
        db?.collection("users")?.document(auth!!.currentUser!!.uid)
            ?.collection("chats")?.document(chatId)
            ?.set(data)
            ?.addOnSuccessListener {
                _syncStatus.value = SyncStatus.SYNCED
            }
            ?.addOnFailureListener {
                _syncStatus.value = SyncStatus.ERROR
            }
    }
}
