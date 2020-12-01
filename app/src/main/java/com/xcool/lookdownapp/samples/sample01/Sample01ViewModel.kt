package com.xcool.lookdownapp.samples.sample01

import android.content.Context
import androidx.hilt.Assisted
import androidx.hilt.lifecycle.ViewModelInject
import androidx.lifecycle.*
import com.xcool.lookdown.LookDownConstants
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
        AppLogger.log("Set keep Running false")
        LookDownUtil.keepRunning = false
        AppLogger.log("Cancelling job with key $key")
        value.cancel()
        AppLogger.log("After cancel and join with key $key")
        if(checked){
          ldDownloadFlow.value?.let{
            it.state = DownloadState.Paused
            LookDownUtil.updateLDDownload(it)
          }
        }else{
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
        val res = LookDownUtil.deleteFile(context, LookDownUtil.driver, LookDownConstants.LD_DEFAULT_FOLDER, "takeatour.mp4" )
        withContext(Dispatchers.Main) {
          _feedback.value = if(res.orDefault()) "File deleted" else "File not deleted"
          ldDownload.value = LDDownload(state=DownloadState.Empty, progress = 0)
        }
      }
      _loading.value = false
    }
  }
  
  fun dummy(){
    val job = baseCoroutineScope.launch(Dispatchers.IO) {
      while(LookDownUtil.counter < 100){
        LookDownUtil.dummy()
        delay(1000L)
      }
    }
    jobsList["dummy"] = job
  }
  
  fun download(url:String){
    val job = baseCoroutineScope.launch(Dispatchers.IO) {
      // withContext(Dispatchers.Main){ _loading.value = true}
      LookDownUtil.download(context, url, "takeatour", ".mp4", folder = null, true)
      // withContext(Dispatchers.Main) { ldDownload.value = res}
      // withContext(Dispatchers.Main){ _loading.value = false}
    }
    jobsList["takeatour"] = job
  }
  
  
  @ExperimentalCoroutinesApi
  @InternalCoroutinesApi
  fun downloadFlow(url:String){
    val job = baseCoroutineScope.launch(Dispatchers.IO) {
      LookDownUtil.downloadFlow(context, url, "takeatour", ".mp4", folder = null, true)
    }
    jobsList["takeatour"] = job
  }
  
}