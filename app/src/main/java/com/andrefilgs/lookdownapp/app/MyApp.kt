package com.andrefilgs.lookdownapp.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.multidex.MultiDexApplication
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject


/**
 * @author André Filgueiras on 28/11/2020
 */
@HiltAndroidApp
class MyApp : MultiDexApplication() { //}, Configuration.Provider {
  
  // @Inject
  // lateinit var hiltWorkerFactory: HiltWorkerFactory
  
  // override fun getWorkManagerConfiguration(): Configuration {
  //   return Configuration.Builder()
  //     .setWorkerFactory(hiltWorkerFactory)
  //     .setMinimumLoggingLevel(android.util.Log.INFO)
  //     .build()
  //   // return myConfiguration
  // }

}