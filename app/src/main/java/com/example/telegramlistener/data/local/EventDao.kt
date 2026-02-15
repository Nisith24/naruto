package com.example.telegramlistener.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query

@Dao
interface EventDao {
    @Insert
    suspend fun insert(event: Event)

    @Query("SELECT * FROM events ORDER BY timestamp ASC LIMIT :limit")
    suspend fun getOldestEvents(limit: Int): List<Event>

    @Delete
    suspend fun delete(events: List<Event>)

    @Query("SELECT COUNT(*) FROM events")
    suspend fun getEventCount(): Int

    @Query("DELETE FROM events")
    suspend fun deleteAllEvents()
}
