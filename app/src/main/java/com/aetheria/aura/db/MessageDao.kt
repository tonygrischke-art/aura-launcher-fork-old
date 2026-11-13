package com.aetheria.aura.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(message: Message): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(messages: List<Message>): List<Long>

    @Update
    suspend fun update(message: Message): Int

    @Update
    suspend fun updateAll(messages: List<Message>): Int

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY timestamp ASC")
    fun getByConversationId(conversationId: Long): Flow<List<Message>>

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId AND status = :status ORDER BY timestamp ASC")
    fun getByConversationIdAndStatus(conversationId: Long, status: String): Flow<List<Message>>

    @Query("SELECT * FROM messages WHERE id = :id")
    suspend fun getById(id: Long): Message?

    @Query("DELETE FROM messages WHERE id = :id")
    suspend fun delete(id: Long): Int

    @Query("DELETE FROM messages WHERE conversationId = :conversationId")
    suspend fun deleteByConversationId(conversationId: Long): Int

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId AND status = 'PENDING' ORDER BY timestamp ASC")
    suspend fun getPendingMessages(conversationId: Long): List<Message>

    @Query("UPDATE messages SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: Long, status: String): Int

    @Query("UPDATE messages SET text = :text, status = :status WHERE id = :id")
    suspend fun updateTextAndStatus(id: Long, text: String, status: String): Int

    @Query("UPDATE messages SET status = 'FAILED' WHERE conversationId = :conversationId AND status = 'PENDING'")
    suspend fun markPendingAsFailed(conversationId: Long): Int

    @Query("SELECT * FROM messages ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLastMessage(): Message?
}