package com.andrefilgs.lookdownapp.samples.sample01

import android.content.Context
import androidx.lifecycle.*
import com.xcool.coroexecutor.core.Executor
import com.andrefilgs.lookdown_android.LDGlobals.LD_DEFAULT_DRIVER
import com.andrefilgs.lookdown_android.LDGlobals.LD_DEFAULT_FOLDER
import com.andrefilgs.lookdown_android.LookDownLite
import com.andrefilgs.lookdown_android.domain.LDDownloadState
import com.andrefilgs.lookdown_android.domain.LDDownload
import com.andrefilgs.lookdownapp.app.AppLogger
import com.andrefilgs.lookdownapp.utils.BaseViewModel
import com.andrefilgs.lookdown_android.utils.orDefault
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import javax.inject.Inject


/**
 * @author André Filgueiras on 28/11/2020
 *
 */

@HiltViewModel
class Sample01ViewModel @Inject constructor(
  private val executor: Executor,
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
            it.setStateAfterDownloadStops()
            LookDownLite.updateLDDownload(it)
          }
        }else{
          LookDownLite.cancelAllDownloads()  //using this work around to cancel
          ldDownload.value?.let{
            it.setStateAfterDownloadStops()
            ldDownload.value = it
          }
        }
      }
      jobsList.clear()
    }
  }
  
  fun deleteFile(context:Context){
    baseCoroutineScope.launch {
      _loading.value = true
      withContext(Dispatchers.IO){
        val res = LookDownLite.deleteFile(context, LD_DEFAULT_DRIVER, LD_DEFAULT_FOLDER, filename, extension)
        // val res = LookDownUtil.deleteFile(context, LDConstants.LD_DEFAULT_DRIVER, LDConstants.LD_DEFAULT_FOLDER, filename, extension+LDConstants.LD_TEMP_EXT) //for temporary file
        withContext(Dispatchers.Main) {
          if(res.orDefault()){
            _feedback.value = "File deleted"
            ldDownload.value = LDDownload(progress = 0, state= LDDownloadState.Empty)
          }else{
            _feedback.value = "File not deleted"
          }
        }
      }
      _loading.value = false
    }
  }
  
  
  fun download(context:Context, url:String, withResume:Boolean){
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
  fun downloadWithFlow(context:Context, url:String, withResume:Boolean){
    val job = baseCoroutineScope.launch(Dispatchers.IO) {
      LookDownLite.download(context, url, filename, extension, null, driver, folder, withResume)
    }
    jobsList[filename] = job
  }
  
}