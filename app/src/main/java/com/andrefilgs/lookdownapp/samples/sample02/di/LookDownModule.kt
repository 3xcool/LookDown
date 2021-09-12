package com.andrefilgs.lookdownapp.samples.sample02.di

import android.content.Context
import androidx.lifecycle.LifecycleOwner
import com.andrefilgs.lookdown_android.LDGlobals
import com.andrefilgs.lookdown_android.LookDown
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object LookDownModule {
  
  
  
  @ExperimentalCoroutinesApi
  @Provides
  @Singleton
  fun providesLookDownBuilder(@ApplicationContext context:Context): LookDown.Builder {
    val builder = LookDown.Builder(context)
    return builder.apply {
      setChunkSize(4096)
      setFileExtension(LDGlobals.LD_VIDEO_MP4_EXT)
      setDriver(1) //0 = Sandbox (Where the app is installed), 1 = Internal (Phone), 2 = SD Card
      setFolder(LDGlobals.LD_DEFAULT_FOLDER)
      setForceResume(true)
      setTimeout(5000)
      setConnectTimeout(5000)
      setProgressRenderDelay(500L)
      setLogTag("LookDown")
      // setWithService(true) //todo 1000
      activateLogs()
    }
  }
  
  @Provides
  @Singleton
  fun providesLookDown(builder: LookDown.Builder):LookDown{
    return builder.build()
  }
  
  
}