package com.wrbug.polymarketbot.util

import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

fun createClient(): OkHttpClient.Builder =
    OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
