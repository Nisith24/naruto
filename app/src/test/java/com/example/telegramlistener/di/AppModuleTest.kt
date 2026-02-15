package com.example.telegramlistener.di

import org.junit.Assert.assertEquals
import org.junit.Test

class AppModuleTest {

    @Test
    fun `provideOkHttpClient should have correct timeouts for long polling`() {
        // Given
        val appModule = AppModule

        // When
        val client = appModule.provideOkHttpClient()

        // Then
        // 60 seconds * 1000 = 60000 ms
        assertEquals("Read timeout should be 60 seconds", 60000, client.readTimeoutMillis)
        assertEquals("Connect timeout should be 30 seconds", 30000, client.connectTimeoutMillis)
        assertEquals("Write timeout should be 30 seconds", 30000, client.writeTimeoutMillis)
    }
}
