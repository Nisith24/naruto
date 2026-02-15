package com.example.telegramlistener.di

import android.content.Context
import androidx.room.Room
import com.example.telegramlistener.data.local.AppDatabase
import com.example.telegramlistener.data.local.EventDao
import com.example.telegramlistener.data.remote.TelegramApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        return OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "telegram_listener.db"
        ).build()
    }

    @Provides
    fun provideEventDao(database: AppDatabase): EventDao {
        return database.eventDao()
    }

    @Provides
    @Singleton
    fun provideTelegramApi(okHttpClient: OkHttpClient): TelegramApi {
        // Placeholder base URL - needs to be injected with the token in real usage
        // or constructed dynamically. For now, we'll put a placeholder and
        // handle the full URL construction carefully or use an interceptor.
        // Actually, best practice: https://api.telegram.org/bot<TOKEN>/
        // I'll make the base URL generic and add the token in the MonitorService config later.
        
        return Retrofit.Builder()
            .baseUrl("https://api.telegram.org/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(TelegramApi::class.java)
    }
}
