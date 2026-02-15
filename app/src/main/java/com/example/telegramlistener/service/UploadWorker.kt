package com.example.telegramlistener.service

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.telegramlistener.data.repo.EventRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.delay

@HiltWorker
class UploadWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val repository: EventRepository
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        // Simple retry logic or just run once?
        // We'll try to sync. If it fails, we return Retry.
        
        return try {
            val success = repository.syncEvents()
            if (success) Result.success() else Result.retry()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
