package com.andrefilgs.lookdownapp.samples.sample02

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Transformations
import com.xcool.coroexecutor.core.CoroUtil
import com.xcool.coroexecutor.core.Executor
import com.xcool.coroexecutor.core.ExecutorSchema
import com.andrefilgs.lookdown_android.LDGlobals
import com.andrefilgs.lookdown_android.LookDown
import com.andrefilgs.lookdown_android.domain.LDDownload
import com.andrefilgs.lookdown_android.domain.LDDownloadState
import com.andrefilgs.lookdownapp.app.AppLogger
import com.andrefilgs.lookdownapp.samples.sample02.model.LDDownloadUtils
import com.andrefilgs.lookdownapp.utils.BaseViewModel
import com.andrefilgs.lookdownapp.utils.Event
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

//Transformations: https://proandroiddev.com/livedata-transformations-4f120ac046fc



@ExperimentalCoroutinesApi
@HiltViewModel
class DownloadViewModel @Inject constructor(
  private val lookDown: LookDown,
  executor: Executor,
  ): BaseViewModel(executor) {
  
  companion object{
    const val KEY_POSITION ="position"
    val driver = LDGlobals.LD_DEFAULT_DRIVER
    val folder = LDGlobals.LD_DEFAULT_FOLDER
  }
  
  
  
  private val workDelay = 1000L
  
  //Executor schema
  private var _schema = MutableStateFlow<ExecutorSchema>(ExecutorSchema.Queue)
  var schema: StateFlow<ExecutorSchema> = _schema
  
  private var _list = MutableLiveData<Event<MutableList<LDDownload>>>()
  var list: LiveData<Event<MutableList<LDDownload>>> = _list
  

  //LookDown LiveData
  val ldDownload: LiveData<Event<LDDownload>> = Transformations.map(lookDown.ldDownloadLiveData) {
    Event(it)
  }
  
  //Turn around for Conflate
  private var workingJobList: MutableMap<String, Triple<ExecutorSchema, Job, LDDownload>> = mutableMapOf()
  
  
  fun buildList(context:Context){
    baseCoroutineScope.launch(Dispatchers.IO) {
      val list = LDDownloadUtils.checkFileExists(context, driver, folder, buildFakeLDDownloadList())
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
      }
      download.setStateAfterDownloadStops()
      download.params?.set(KEY_POSITION, position.toString())
      lookDown.updateLDDownload(download)
    }
  }
  
  fun stopAllDownload() {
    launch(ExecutorSchema.Concurrent) {
      workingJobList.forEach { map ->
        map.value.third.setStateAfterDownloadStops()
        lookDown.updateLDDownload(map.value.third)
        map.value.second.cancel()
      }
    }
  }
  
  fun deleteDownload(download: LDDownload, position: Int) {
    launch(ExecutorSchema.Concurrent) {
      val res = lookDown.deleteFile(download.filename!!, download.fileExtension!!)
      if(!res){
        download.state = LDDownloadState.Error("File not deleted")
      }else{
        download.updateProgress(0)
        download.params?.set(KEY_POSITION, position.toString())
      }
      lookDown.updateLDDownload(download)
    }
  }
  


  
  @InternalCoroutinesApi
  fun startDownload(download: LDDownload, position:Int){
    baseCoroutineScope.launch {
      //force QUEUE state to avoid collision and Update UI.
      download.state = LDDownloadState.Queued
      download.params = mutableMapOf(Pair(KEY_POSITION, position.toString()))
      lookDown.updateLDDownload(download)
  
      val job = launch(schema.value) {
        withContext(Dispatchers.IO) {
          AppLogger.log("Start download file ${download.title} with progress ${download.progress}")
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
            lookDown.updateLDDownload(map.value.third)
          }
        }
    }
  }
  
  
  @InternalCoroutinesApi
  private suspend fun downloadFile(ldDownload: LDDownload){
    if(ldDownload.validateInfoForDownloading()){
      lookDown.download(ldDownload)
    }else{
      ldDownload.state = LDDownloadState.Error("Missing download info")
      lookDown.updateLDDownload(ldDownload)
    }
  }
  
  
}