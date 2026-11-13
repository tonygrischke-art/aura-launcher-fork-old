package com.aetheria.aura.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

@Entity(tableName = "conversations")
data class Conversation(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val status: String = "IDLE" // IDLE, RUNNING, WAITING, DONE, FAILED
) {
    companion object {
        const val STATUS_IDLE = "IDLE"
        const val STATUS_RUNNING = "RUNNING"
        const val STATUS_WAITING = "WAITING"
        const val STATUS_DONE = "DONE"
        const val STATUS_FAILED = "FAILED"
    }
}