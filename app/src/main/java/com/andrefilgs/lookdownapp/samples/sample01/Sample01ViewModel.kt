package com.andrefilgs.lookdownapp.samples.sample01

import androidx.lifecycle.*
import com.xcool.coroexecutor.core.Executor
import com.andrefilgs.lookdown_android.LDGlobals.LD_DEFAULT_DRIVER
import com.andrefilgs.lookdown_android.LDGlobals.LD_DEFAULT_FOLDER
import com.andrefilgs.lookdown_android.LookDown
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
  private val lookDown: LookDown,
  executor: Executor,
  ): BaseViewModel(executor) {
  
  private val filename = "takeatour"
  private val extension = ".mp4"
  private val driver = LD_DEFAULT_DRIVER
  private val folder = LD_DEFAULT_FOLDER
  
  private val _loading : MutableLiveData<Boolean> = MutableLiveData()
  val loading : LiveData<Boolean> = _loading
  
  private val _feedback : MutableLiveData<String> = MutableLiveData()
  val feedback : LiveData<String> = _feedback
  
  
  val ldDownload: LiveData<LDDownload> = Transformations.map(lookDown.ldDownloadLiveData) { it }
  
  var jobsList :MutableMap<String, Job> = mutableMapOf()
  
  
  init {
    lookDown.apply {
      setDriver(driver)
      setFolder(folder)
      setFileExtension(extension)
      activateLogs()
    }
  }
  
  
  fun setChunkSize(chunkSize:Int){
    lookDown.setChunkSize(chunkSize)
  }
  
  fun stopDownload() {
    baseCoroutineScope.launch {
      for((key, value) in jobsList){
        AppLogger.log("Cancelling $key")
        value.cancel()
        ldDownload.value?.let{
          it.setStateAfterDownloadStops()
          lookDown.updateLDDownload(it)
        }
      }
      jobsList.clear()
    }
  }
  
  @ExperimentalCoroutinesApi
  fun deleteFile(){
    baseCoroutineScope.launch {
      _loading.value = true
      withContext(Dispatchers.IO){
        val res = lookDown.deleteFile(filename = filename, driver= LD_DEFAULT_DRIVER, folder= LD_DEFAULT_FOLDER, fileExtension= extension)
        withContext(Dispatchers.Main) {
          if(res.orDefault()){
            _feedback.value = "File deleted"
            lookDown.updateLDDownload(LDDownload(progress = 0, state= LDDownloadState.Empty))
          }else{
            _feedback.value = "File not deleted"
          }
        }
      }
      _loading.value = false
    }
  }
  
  
  @ExperimentalCoroutinesApi
  @InternalCoroutinesApi
  fun downloadWithFlow(url:String, resume:Boolean){
    val job = baseCoroutineScope.launch(Dispatchers.IO) {
      // LookDownLite.download(context, url, filename, extension, null, driver, folder, resume)
      lookDown.download(url= url,filename= filename, fileExtension= extension, resume=resume)
    }
    jobsList[filename] = job
  }
  
}