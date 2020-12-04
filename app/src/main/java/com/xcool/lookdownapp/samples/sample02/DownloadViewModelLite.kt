package com.xcool.lookdownapp.samples.sample02

import android.content.Context
import androidx.hilt.Assisted
import androidx.hilt.lifecycle.ViewModelInject
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.Transformations
import com.xcool.coroexecutor.core.CoroUtil
import com.xcool.coroexecutor.core.Executor
import com.xcool.coroexecutor.core.ExecutorSchema
import com.xcool.lookdown.LDGlobals
import com.xcool.lookdown.LookDown
import com.xcool.lookdown.LookDownLite
import com.xcool.lookdown.model.LDDownload
import com.xcool.lookdown.model.LDDownloadState
import com.xcool.lookdownapp.app.AppLogger
import com.xcool.lookdownapp.samples.sample02.model.LDDownloadUtils
import com.xcool.lookdownapp.utils.BaseViewModel
import com.xcool.lookdownapp.utils.Event
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

//Transformations: https://proandroiddev.com/livedata-transformations-4f120ac046fc

@ExperimentalCoroutinesApi
class DownloadViewModelLite @ViewModelInject constructor(
  @ApplicationContext val context: Context,
  executor: Executor,
  @Assisted private val state: SavedStateHandle
  ): BaseViewModel(executor) {
  
  companion object{
    const val KEY_POSITION ="position"
    val driver = LDGlobals.LD_DEFAULT_DRIVER
    val folder = LDGlobals.LD_DEFAULT_FOLDER
  }
  
  
  private val delayTime = 1000L
  
  //Simulating download
  private var _workProgress = MutableLiveData<Event<LDDownload>>()
  var workProgress: LiveData<Event<LDDownload>> = _workProgress
  
  //Executor schema
  private var _schema = MutableStateFlow<ExecutorSchema>(ExecutorSchema.Queue)
  var schema: StateFlow<ExecutorSchema> = _schema
  
  private var _list = MutableLiveData<Event<List<LDDownload>>>()
  var list: LiveData<Event<List<LDDownload>>> = _list
  
  //LookDown LiveData
  val ldDownloadFlow: LiveData<Event<LDDownload>> = Transformations.map(LookDownLite.ldDownloadLiveData) { Event(it) }
  
  //Turn around for Conflate
  private var workingJobList: MutableMap<String, Triple<ExecutorSchema, Job, LDDownload>> = mutableMapOf()
  
  
  fun buildList(){
    baseCoroutineScope.launch(Dispatchers.IO) {
      val list = LDDownloadUtils.checkFileExists(context, driver, folder, LDDownloadUtils.buildFakeLDDownloadList())
      withContext(Dispatchers.Main){
        _list.value = Event(list)
      }
    }
  }
  
  private fun removeJob(download: LDDownload) {
    workingJobList.remove(download.id)
  }
  
  fun setExecutorSchema(schema: ExecutorSchema) {
    this._schema.value = schema
    launch(CoroUtil.MainThread, this.schema.value) {
      _schema.value = schema
    }
  }
  
  fun stopDownload(download: LDDownload, position: Int) {
    baseCoroutineScope.launch {
      workingJobList[download.id]?.let {
        it.second.cancelAndJoin()
        download.setStateAfterStopDownload()
        download.params?.set(KEY_POSITION, position.toString())
        LookDownLite.updateLDDownload(download)
      }
    }
  }
  
  fun stopAllDownload() {
    launch(ExecutorSchema.Concurrent) {
      workingJobList.forEach { map ->
        map.value.third.setStateAfterStopDownload()
        LookDownLite.updateLDDownload(map.value.third)
        map.value.second.cancel()
      }
    }
  }
  
  fun deleteDownload(download: LDDownload, position: Int) {
    launch(ExecutorSchema.Concurrent) {
      val res = LookDownLite.deleteFile(context, driver, folder, download.filename!!, download.fileExtension!!)
      if(!res){
        download.state = LDDownloadState.Error("File not deleted")
      }else{
        download.updateProgress(0)
        download.params?.set(KEY_POSITION, position.toString())
      }
      LookDownLite.updateLDDownload(download)
    }
  }
  


  
  @InternalCoroutinesApi
  fun startDownload(download: LDDownload, position:Int){
    baseCoroutineScope.launch {
      download.state = LDDownloadState.Queued
      download.params = mutableMapOf(Pair(KEY_POSITION, position.toString()))
      LookDownLite.updateLDDownload(download)
  
      val job = launch(schema.value) {
        withContext(Dispatchers.IO) {
          AppLogger.log("Start download file ${download.title} with progress ${download.progress}")
          // simulateWorkProgress(download)
          downloadFile(download)
          AppLogger.log("Finished download file ${download.title} with progress ${download.progress}")
        }
      }
      handleConflate(schema.value)
      workingJobList[download.id] = Triple(schema.value, job, download)
      
      job.join()
      removeJob(download) //remove job after finish
    }
  }
  

  
  private suspend fun handleConflate(schema: ExecutorSchema) {
    //Conflate will kill any working progress, so avoid use it for download
    //Manually stopping other downloads
    if(schema == ExecutorSchema.Conflated){
      workingJobList.filter { map -> map.value.first == ExecutorSchema.Conflated }
        .forEach { map->
          if(map.value.third.state == LDDownloadState.Downloading){
            map.value.second.cancel()
            map.value.third.state = LDDownloadState.Paused
            LookDownLite.updateLDDownload(map.value.third)
          }
        }
    }
  }
  
  
  @InternalCoroutinesApi
  private suspend fun downloadFile(download: LDDownload){
    if(download.validateInfoForDownloading()){
      download.state = LDDownloadState.Downloading
      LookDownLite.updateLDDownload(download)
      LookDownLite.download(this.context, download, driver, folder, true, null) //passing ldDownload or
      
      // LookDownLite.downloadWithFlow(this.context, download.url!!, download.filename!!, download.fileExtension!!, download.id,
      //                               driver = driver, folder = folder, resumeDownload = true, params = download.params, title = download.title)
    }else{
      download.state = LDDownloadState.Error("Missing download info")
      LookDownLite.updateLDDownload(download)
      
      
      
    }
  }
  
  
  private suspend fun simulateWorkProgress(download: LDDownload) {
    download.state = LDDownloadState.Downloading
    updateDownloadInfo(download)
    for (i in download.progress..100 step 10) {
      download.updateProgress(i)
      AppLogger.log("Downloading ${download.title} with progress ${download.progress}...")
      updateDownloadInfo(download)
      delay(delayTime)
    }
  }
  
  private suspend fun updateDownloadInfo(download: LDDownload) {
    withContext(Dispatchers.Main) {
      _workProgress.value = Event(download)
    }
  }
  
  
}