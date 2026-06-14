package com.example.data.remote

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.example.data.local.MessageEntity
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOf

class FirestoreCollaborationManager {
    
    private val db: FirebaseFirestore? by lazy {
        try {
            FirebaseFirestore.getInstance()
        } catch (e: Exception) {
            Log.e("Collaboration", "Firebase Firestore is not available: ${e.message}")
            null
        }
    }

    fun isFirebaseAvailable(): Boolean {
        return db != null
    }

    fun shareChatSession(conversationId: Long, messages: List<MessageEntity>): String? {
        val firestore = db ?: return null
        return try {
            val sessionId = firestore.collection("shares").document().id
            val sessionData = hashMapOf(
                "conversationId" to conversationId,
                "createdAt" to System.currentTimeMillis()
            )
            firestore.collection("shares").document(sessionId).set(sessionData)
            
            val messagesCollection = firestore.collection("shares").document(sessionId).collection("messages")
            messages.forEach { message ->
                messagesCollection.add(message)
            }
            
            sessionId
        } catch (e: Exception) {
            Log.e("Collaboration", "Error sharing chat session: ${e.message}")
            null
        }
    }

    fun listenToMessages(sessionId: String): Flow<List<MessageEntity>> {
        val firestore = db ?: return flowOf(emptyList())
        return callbackFlow {
            val subscription = firestore.collection("shares").document(sessionId).collection("messages")
                .addSnapshotListener { snapshot, e ->
                    if (e != null) {
                        close(e)
                        return@addSnapshotListener
                    }
                    if (snapshot != null) {
                        val messages = snapshot.documents.mapNotNull { doc ->
                            doc.toObject(MessageEntity::class.java)?.copy(id = doc.id.hashCode().toLong())
                        }
                        trySend(messages)
                    }
                }
            awaitClose { subscription.remove() }
        }
    }
}
