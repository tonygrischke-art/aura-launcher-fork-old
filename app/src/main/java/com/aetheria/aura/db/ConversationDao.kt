package com.aetheria.aura.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ConversationDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(conversation: Conversation): Long

    @Update
    suspend fun update(conversation: Conversation): Int

    @Query("SELECT * FROM conversations ORDER BY updatedAt DESC")
    fun getAll(): Flow<List<Conversation>>

    @Query("SELECT * FROM conversations WHERE id = :id")
    fun getById(id: Long): Flow<Conversation?>

    @Query("SELECT * FROM conversations WHERE status IN ('RUNNING', 'WAITING') ORDER BY updatedAt DESC")
    fun getActive(): Flow<List<Conversation>>

    @Query("DELETE FROM conversations WHERE id = :id")
    suspend fun delete(id: Long): Int

    @Query("UPDATE conversations SET status = :status, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateStatus(id: Long, status: String, updatedAt: Long): Int

    @Query("UPDATE conversations SET title = :title, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateTitle(id: Long, title: String, updatedAt: Long): Int

    @Query("SELECT * FROM conversations ORDER BY updatedAt DESC LIMIT 1")
    suspend fun getLastActive(): Conversation?
}