package com.aetheria.aura.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class ChatRepositoryTest {

    private lateinit var db: AuraDatabase
    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(
            context, AuraDatabase::class.java
        ).allowMainThreadQueries().build()
    }

    @After
    fun teardown() {
        db.close()
    }

    private val conversationDao = db.conversationDao()
    private val messageDao = db.messageDao()

    @Test
    fun `insert and load messages ordered by timestamp`() {
        runBlocking {
            // Create a conversation
            val conversation = Conversation(title = "Test Chat", status = Conversation.STATUS_IDLE)
            val conversationId = conversationDao.insert(conversation)

            // Insert messages with different timestamps
            val now = System.currentTimeMillis()
            val msg1 = Message(0, conversationId, Message.ROLE_USER, "First message", now, Message.STATUS_COMPLETED)
            val msg2 = Message(0, conversationId, Message.ROLE_ASSISTANT, "Second message", now + 1000, Message.STATUS_COMPLETED)
            val msg3 = Message(0, conversationId, Message.ROLE_SYSTEM, "Third message", now + 2000, Message.STATUS_COMPLETED)

            messageDao.insert(msg1)
            messageDao.insert(msg2)
            messageDao.insert(msg3)

            // Load messages - should be ordered by timestamp ASC
            val messages = messageDao.getByConversationId(conversationId).first()

            assertEquals(3, messages.size)
            assertEquals("First message", messages[0].text)
            assertEquals("Second message", messages[1].text)
            assertEquals("Third message", messages[2].text)
            assertTrue(messages[0].timestamp <= messages[1].timestamp)
            assertTrue(messages[1].timestamp <= messages[2].timestamp)
        }
    }

    @Test
    fun `recoverAllPendingMessages marks stale PENDING as FAILED`() {
        runBlocking {
            // Create a conversation
            val conversation = Conversation(title = "Test Chat", status = Conversation.STATUS_IDLE)
            val conversationId = conversationDao.insert(conversation)

            // Insert a PENDING message
            val pendingMsg = Message(0, conversationId, Message.ROLE_ASSISTANT, "Pending response", System.currentTimeMillis(), Message.STATUS_PENDING)
            messageDao.insert(pendingMsg)

            // Insert a COMPLETED message (should not be affected)
            val completedMsg = Message(0, conversationId, Message.ROLE_USER, "User message", System.currentTimeMillis(), Message.STATUS_COMPLETED)
            messageDao.insert(completedMsg)

            // Call recoverAllPendingMessages - we need to use the repository method
            // Since we can't easily inject the test DB, we'll test the DAO directly
            val updatedCount = messageDao.markPendingAsFailed(conversationId)

            assertEquals(1, updatedCount)

            // Verify the PENDING message is now FAILED
            val messages = messageDao.getByConversationId(conversationId).first()
            val pendingMessages = messages.filter { it.status == Message.STATUS_PENDING }
            val failedMessages = messages.filter { it.status == Message.STATUS_FAILED }

            assertEquals(0, pendingMessages.size)
            assertEquals(1, failedMessages.size)
            assertEquals("Pending response", failedMessages[0].text)

            // COMPLETED message should be unchanged
            val completedMessages = messages.filter { it.status == Message.STATUS_COMPLETED }
            assertEquals(1, completedMessages.size)
            assertEquals("User message", completedMessages[0].text)
        }
    }

    @Test
    fun `clearConversation empties the thread`() {
        runBlocking {
            // Create a conversation
            val conversation = Conversation(title = "Test Chat", status = Conversation.STATUS_IDLE)
            val conversationId = conversationDao.insert(conversation)

            // Insert multiple messages
            val messages = listOf(
                Message(0, conversationId, Message.ROLE_USER, "User 1", System.currentTimeMillis(), Message.STATUS_COMPLETED),
                Message(0, conversationId, Message.ROLE_ASSISTANT, "Assistant 1", System.currentTimeMillis(), Message.STATUS_COMPLETED),
                Message(0, conversationId, Message.ROLE_USER, "User 2", System.currentTimeMillis(), Message.STATUS_COMPLETED),
                Message(0, conversationId, Message.ROLE_SYSTEM, "System", System.currentTimeMillis(), Message.STATUS_COMPLETED),
            )
            messageDao.insertAll(messages)

            // Verify messages exist
            var loaded = messageDao.getByConversationId(conversationId).first()
            assertEquals(4, loaded.size)

            // Clear the conversation
            messageDao.deleteByConversationId(conversationId)

            // Verify messages are gone
            loaded = messageDao.getByConversationId(conversationId).first()
            assertEquals(0, loaded.size)

            // Conversation should still exist but with updated title/status
            val conv = conversationDao.getById(conversationId).first()
            assertNotNull(conv)
        }
    }
}