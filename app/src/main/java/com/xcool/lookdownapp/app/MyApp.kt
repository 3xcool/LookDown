package com.xcool.lookdownapp.app

import android.app.Application
import androidx.multidex.MultiDexApplication
import dagger.hilt.android.HiltAndroidApp



/**
 * @author André Filgueiras on 28/11/2020
 */
@HiltAndroidApp
class MyApp : MultiDexApplication() {}