package com.xcool.lookdownapp.samples.sample01

import android.content.Context
import androidx.hilt.Assisted
import androidx.hilt.lifecycle.ViewModelInject
import androidx.lifecycle.*
import com.xcool.lookdown.LookDownConstants
import com.xcool.lookdown.LookDownConstants.LD_DEFAULT_DRIVER
import com.xcool.lookdown.LookDownConstants.LD_DEFAULT_FOLDER
import com.xcool.lookdown.LookDownUtil
import com.xcool.lookdown.model.DownloadState
import com.xcool.lookdown.model.LDDownload
import com.xcool.lookdownapp.app.AppLogger
import com.xcool.lookdownapp.utils.BaseViewModel
import com.xcool.lookdownapp.utils.orDefault
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*


/**
 * @author André Filgueiras on 28/11/2020
 */
class Sample01ViewModel @ViewModelInject constructor(
  @ApplicationContext val context: Context,
  @Assisted private val state: SavedStateHandle
  ): BaseViewModel() {
  
  private val filename = "takeatour"
  private val extension = ".mp4"
  private val driver = LD_DEFAULT_DRIVER
  private val folder = LD_DEFAULT_FOLDER
  
  private val _loading : MutableLiveData<Boolean> = MutableLiveData()
  val loading : LiveData<Boolean> = _loading
  
  private val _feedback : MutableLiveData<String> = MutableLiveData()
  val feedback : LiveData<String> = _feedback
  
  val ldDownload: MutableLiveData<LDDownload> = MutableLiveData()  //simple one
  
  val ldDownloadFlow: LiveData<LDDownload> = Transformations.map(LookDownUtil.ldDownloadLiveData) { it }
  
  var jobsList :MutableMap<String, Job> = mutableMapOf()
  
  fun setChunkSize(chunkSize:Int){
    LookDownUtil.chunkSize = chunkSize
  }
  
  fun stopAllDownloads(checked: Boolean) {
    baseCoroutineScope.launch {
      for((key, value) in jobsList){
        AppLogger.log("Cancelling $key")
        value.cancel()
        if(checked){
          ldDownloadFlow.value?.let{
            it.state = DownloadState.Paused
            LookDownUtil.updateLDDownload(it)
          }
        }else{
          LookDownUtil.cancelAllDownloads()  //using this work around to cancel
          ldDownload.value?.let{
            it.state = DownloadState.Paused
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
        val res = LookDownUtil.deleteFile(context, LookDownConstants.LD_DEFAULT_DRIVER, LookDownConstants.LD_DEFAULT_FOLDER, "$filename$extension")
        withContext(Dispatchers.Main) {
          _feedback.value = if(res.orDefault()) "File deleted" else "File not deleted"
          ldDownload.value = LDDownload(state=DownloadState.Empty, progress = 0)
        }
      }
      _loading.value = false
    }
  }
  
  
  fun download(url:String, withResume:Boolean){
    val job = baseCoroutineScope.launch(Dispatchers.IO) {
      withContext(Dispatchers.Main){ _loading.value = true}
      val res = LookDownUtil.download(context, url, filename, extension, driver,  folder, withResume)
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
      LookDownUtil.downloadWithFlow(context, url, filename, extension, driver, folder, withResume)
    }
    jobsList[filename] = job
  }
  
}