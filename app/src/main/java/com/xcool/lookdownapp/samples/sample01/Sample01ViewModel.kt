package com.xcool.lookdownapp.samples.sample01

import android.content.Context
import androidx.hilt.Assisted
import androidx.hilt.lifecycle.ViewModelInject
import androidx.lifecycle.*
import com.xcool.coroexecutor.core.Executor
import com.xcool.lookdown.LDGlobals
import com.xcool.lookdown.LDGlobals.LD_DEFAULT_DRIVER
import com.xcool.lookdown.LDGlobals.LD_DEFAULT_FOLDER
import com.xcool.lookdown.LookDownLite
import com.xcool.lookdown.model.LDDownloadState
import com.xcool.lookdown.model.LDDownload
import com.xcool.lookdownapp.app.AppLogger
import com.xcool.lookdownapp.utils.BaseViewModel
import com.xcool.lookdownapp.utils.orDefault
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*


/**
 * @author André Filgueiras on 28/11/2020
 *
 */

class Sample01ViewModel @ViewModelInject constructor(
  @ApplicationContext val context: Context,
  private val executor: Executor,
  @Assisted private val state: SavedStateHandle
  ): BaseViewModel(executor) {
  
  private val filename = "takeatour"
  private val extension = ".mp4"
  private val driver = LD_DEFAULT_DRIVER
  private val folder = LD_DEFAULT_FOLDER
  
  private val _loading : MutableLiveData<Boolean> = MutableLiveData()
  val loading : LiveData<Boolean> = _loading
  
  private val _feedback : MutableLiveData<String> = MutableLiveData()
  val feedback : LiveData<String> = _feedback
  
  val ldDownload: MutableLiveData<LDDownload> = MutableLiveData()  //simple one
  
  val ldDownloadFlow: LiveData<LDDownload> = Transformations.map(LookDownLite.ldDownloadLiveData) { it }
  
  var jobsList :MutableMap<String, Job> = mutableMapOf()
  
  fun setChunkSize(chunkSize:Int){
    LookDownLite.chunkSize = chunkSize
  }
  
  fun stopAllDownloads(checked: Boolean) {
    baseCoroutineScope.launch {
      for((key, value) in jobsList){
        AppLogger.log("Cancelling $key")
        value.cancel()
        if(checked){
          ldDownloadFlow.value?.let{
            it.setStateAfterStopDownload()
            LookDownLite.updateLDDownload(it)
          }
        }else{
          LookDownLite.cancelAllDownloads()  //using this work around to cancel
          ldDownload.value?.let{
            it.setStateAfterStopDownload()
            ldDownload.value = it
          }
        }
      }
      jobsList.clear()
    }
  }
  
  fun deleteFile(){
    baseCoroutineScope.launch {
      _loading.value = true
      withContext(Dispatchers.IO){
        val res = LookDownLite.deleteFile(context, LD_DEFAULT_DRIVER, LD_DEFAULT_FOLDER, filename, extension)
        // val res = LookDownUtil.deleteFile(context, LDConstants.LD_DEFAULT_DRIVER, LDConstants.LD_DEFAULT_FOLDER, filename, extension+LDConstants.LD_TEMP_EXT) //for temporary file
        withContext(Dispatchers.Main) {
          if(res.orDefault()){
            _feedback.value = "File deleted"
            ldDownload.value = LDDownload(progress = 0, state=LDDownloadState.Empty)
          }else{
            _feedback.value = "File not deleted"
          }
        }
      }
      _loading.value = false
    }
  }
  
  
  fun download(url:String, withResume:Boolean){
    val job = baseCoroutineScope.launch(Dispatchers.IO) {
      withContext(Dispatchers.Main){ _loading.value = true}
      val res = LookDownLite.downloadSimple(context, url, filename, extension, driver, folder, withResume)
      withContext(Dispatchers.Main) {
        ldDownload.value = res
        _loading.value = false
      }
    }
    jobsList[filename] = job
  }
  
  
  @ExperimentalCoroutinesApi
  @InternalCoroutinesApi
  fun downloadWithFlow(url:String, withResume:Boolean){
    val job = baseCoroutineScope.launch(Dispatchers.IO) {
      LookDownLite.download(context, url, filename, extension, null, driver, folder, withResume)
    }
    jobsList[filename] = job
  }
  
}