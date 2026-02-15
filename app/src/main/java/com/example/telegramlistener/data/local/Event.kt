package com.example.telegramlistener.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "events")
data class Event(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,
    val payload: String,
    val timestamp: Long = System.currentTimeMillis()
)
