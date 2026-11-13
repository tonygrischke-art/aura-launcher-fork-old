package com.aetheria.aura.db

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class ChatRepository(private val context: Context) {
    private val db = AuraDatabase.getInstance(context)
    private val conversationDao = db.conversationDao()
    private val messageDao = db.messageDao()

    // Get all conversations as Flow
    fun getAllConversations(): Flow<List<Conversation>> = conversationDao.getAll()

    // Get active conversations (RUNNING, WAITING)
    fun getActiveConversations(): Flow<List<Conversation>> = conversationDao.getActive()

    // Get last active conversation
    suspend fun getLastActiveConversation(): Conversation? = conversationDao.getLastActive()

    // Create a new conversation
    suspend fun createConversation(title: String = ""): Conversation {
        val conversation = Conversation(
            title = title,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            status = Conversation.STATUS_IDLE
        )
        val id = conversationDao.insert(conversation)
        return conversation.copy(id = id)
    }

    // Get or create the default conversation (last active or new)
    suspend fun getOrCreateDefaultConversation(): Conversation {
        return getLastActiveConversation() ?: createConversation("New chat")
    }

    // Update conversation title
    suspend fun updateConversationTitle(conversationId: Long, title: String) {
        conversationDao.updateTitle(conversationId, title, System.currentTimeMillis())
    }

    // Update conversation status
    suspend fun updateConversationStatus(conversationId: Long, status: String) {
        conversationDao.updateStatus(conversationId, status, System.currentTimeMillis())
    }

    // Delete a conversation
    suspend fun deleteConversation(conversationId: Long) {
        conversationDao.delete(conversationId)
    }

    // Get messages for a conversation
    fun getMessages(conversationId: Long): Flow<List<Message>> = messageDao.getByConversationId(conversationId)

    // Add a user message
    suspend fun addUserMessage(conversationId: Long, text: String): Message {
        val message = Message(
            conversationId = conversationId,
            role = Message.ROLE_USER,
            text = text,
            timestamp = System.currentTimeMillis(),
            status = Message.STATUS_COMPLETED
        )
        val id = messageDao.insert(message)
        return message.copy(id = id)
    }

    // Add a pending assistant message (streaming)
    suspend fun addPendingAssistantMessage(conversationId: Long): Message {
        val message = Message(
            conversationId = conversationId,
            role = Message.ROLE_ASSISTANT,
            text = "",
            timestamp = System.currentTimeMillis(),
            status = Message.STATUS_PENDING
        )
        val id = messageDao.insert(message)
        return message.copy(id = id)
    }

    // Update streaming assistant message
    suspend fun updateAssistantMessage(messageId: Long, text: String) {
        messageDao.updateTextAndStatus(messageId, text, Message.STATUS_PENDING)
    }

    // Complete assistant message
    suspend fun completeAssistantMessage(messageId: Long, text: String) {
        messageDao.updateTextAndStatus(messageId, text, Message.STATUS_COMPLETED)
    }

    // Fail assistant message
    suspend fun failAssistantMessage(messageId: Long, errorText: String) {
        messageDao.updateTextAndStatus(messageId, errorText, Message.STATUS_FAILED)
    }

    // Add a system message
    suspend fun addSystemMessage(conversationId: Long, text: String): Message {
        val message = Message(
            conversationId = conversationId,
            role = Message.ROLE_SYSTEM,
            text = text,
            timestamp = System.currentTimeMillis(),
            status = Message.STATUS_COMPLETED
        )
        val id = messageDao.insert(message)
        return message.copy(id = id)
    }

    // Mark all pending messages as failed (on app restart)
    suspend fun recoverPendingMessages(conversationId: Long) {
        messageDao.markPendingAsFailed(conversationId)
    }

    // Recover all pending messages across all conversations
    suspend fun recoverAllPendingMessages() = withContext(Dispatchers.IO) {
        val conversations = conversationDao.getAll().first()
        for (conversation in conversations) {
            recoverPendingMessages(conversation.id)
        }
    }

    // Get conversation with messages combined (for UI)
    fun getConversationWithMessages(conversationId: Long): Flow<Pair<Conversation, List<Message>>> {
        return combine(
            conversationDao.getById(conversationId)
                .map { it ?: Conversation(id = conversationId) },
            messageDao.getByConversationId(conversationId)
        ) { conversation, messages ->
            Pair(conversation, messages)
        }
    }

    // Build history for LLM (last 12 turns / 6000 chars)
    suspend fun buildHistoryForLlm(conversationId: Long): List<Pair<String, String>> = withContext(Dispatchers.IO) {
        val messages = messageDao.getByConversationId(conversationId).first()
        val history = mutableListOf<Pair<String, String>>()
        var chars = 0
        for (msg in messages.asReversed()) {
            if (msg.role == Message.ROLE_SYSTEM || msg.text.isBlank()) continue
            chars += msg.text.length
            if (history.size >= 12 || chars > 6000) break
            val role = when (msg.role) {
                Message.ROLE_USER -> "user"
                Message.ROLE_ASSISTANT -> "assistant"
                else -> "system"
            }
            history.add(0, role to msg.text)
        }
        history
    }

    // Convert messages to NimClient format
    suspend fun getNimMessages(conversationId: Long): List<com.aetheria.aura.nim.NimChatMessage> = withContext(Dispatchers.IO) {
        val messages = messageDao.getByConversationId(conversationId).first()
        messages.filter { it.role != Message.ROLE_SYSTEM && it.text.isNotBlank() }
            .map { com.aetheria.aura.nim.NimChatMessage(it.role, it.text) }
    }

    // Clear all messages in a conversation
    suspend fun clearConversation(conversationId: Long) {
        messageDao.deleteByConversationId(conversationId)
        conversationDao.updateTitle(conversationId, "New chat", System.currentTimeMillis())
        conversationDao.updateStatus(conversationId, Conversation.STATUS_IDLE, System.currentTimeMillis())
    }
}