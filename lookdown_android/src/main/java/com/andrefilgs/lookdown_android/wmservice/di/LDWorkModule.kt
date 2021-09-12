package com.andrefilgs.lookdown_android.wmservice.di

import android.content.Context
import androidx.work.WorkManager
import com.google.gson.Gson
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

// @Module
// @InstallIn(SingletonComponent::class)
// class LDWorkModule {
//
//   @Provides
//   @Singleton
//   fun providesGson(): Gson {
//     return Gson()
//   }
//
//
//
//   @Provides
//   @Singleton
//   fun providesWorkManager(@ApplicationContext context: Context): WorkManager {
//     return WorkManager.getInstance(context)
//   }
//
// }