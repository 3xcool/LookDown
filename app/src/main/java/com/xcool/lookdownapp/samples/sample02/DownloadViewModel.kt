package com.xcool.lookdownapp.samples.sample02

import android.content.Context
import androidx.hilt.Assisted
import androidx.hilt.lifecycle.ViewModelInject
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import com.xcool.coroexecutor.core.CoroUtil
import com.xcool.coroexecutor.core.Executor
import com.xcool.coroexecutor.core.ExecutorSchema
import com.xcool.lookdownapp.app.AppLogger
import com.xcool.lookdownapp.samples.sample02.model.Download
import com.xcool.lookdownapp.samples.sample02.model.DownloadState
import com.xcool.lookdownapp.utils.BaseViewModel
import com.xcool.lookdownapp.utils.Event
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

@ExperimentalCoroutinesApi
class DownloadViewModel @ViewModelInject constructor(
  @ApplicationContext context: Context,
  private val executor: Executor,
  @Assisted private val state: SavedStateHandle
  ): BaseViewModel(executor) {
  
  private val delayTime = 1000L
  
  private var _downloadInfo = MutableLiveData<Event<Download>>()
  var downloadInfo: LiveData<Event<Download>> = _downloadInfo
  
  private var _schema = MutableStateFlow<ExecutorSchema>(ExecutorSchema.Queue)
  var schema: StateFlow<ExecutorSchema> = _schema
  
  
  private var workingJobList: MutableMap<String, Triple<ExecutorSchema, Job, Download>> = mutableMapOf() //avoid using Conflate for download
  
  
  private fun removeJob(download: Download) {
    workingJobList.remove(download.id)
  }
  
  fun setExecutorSchema(schema: ExecutorSchema) {
    this._schema.value = schema
    launch(CoroUtil.MainThread, this.schema.value) {
      _schema.value = schema
    }
  }
  
  fun stopDownload(download: Download) {
    baseCoroutineScope.launch {
      workingJobList[download.id]?.let {
        it.second.cancelAndJoin()
        download.state = DownloadState.Paused
        _downloadInfo.value = Event(download)
      }
    }
  }
  
  fun stopAllDownload() {
    launch(ExecutorSchema.Concurrent) {
      workingJobList.forEach { map ->
        map.value.third.state = DownloadState.Paused
        updateDownloadInfo(map.value.third)
        map.value.second.cancel()
      }
    }
  }
  
  fun deleteDownload(download: Download) {
    baseCoroutineScope.launch {
      download.updateProgress(0)
      updateDownloadInfo(download)
    }
  }
  
  fun startDownload(download: Download) {
    download.state = DownloadState.Queued
    _downloadInfo.value = Event(download)
    val job = launch(schema.value) {
      withContext(Dispatchers.IO) {
        AppLogger.log("Start download file ${download.title} with progress ${download.progress}")
        download.state = DownloadState.Downloading
        workProgress(download)
        AppLogger.log("Finished download file ${download.title} with progress ${download.progress}")
      }
      
    }
    handleConflate(schema.value)
    workingJobList[download.id] = Triple(schema.value, job, download)
    
    baseCoroutineScope.launch {
      job.join()
      removeJob(download) //remove job after finish
    }
  }
  
  private fun handleConflate(schema: ExecutorSchema) {
    //Conflate will kill any working progress, so avoid use it for download
    //Manually stopping other downloads
    if(schema == ExecutorSchema.Conflated){
      workingJobList.filter { map -> map.value.first == ExecutorSchema.Conflated }
        .forEach { map->
          if(map.value.third.state == DownloadState.Downloading){
            map.value.second.cancel()
            map.value.third.state = DownloadState.Paused
            _downloadInfo.value = Event(map.value.third)
          }
        }
    }
  }
  
  private suspend fun workProgress(download: Download) {
    download.state = DownloadState.Downloading
    updateDownloadInfo(download)
    for (i in download.progress..100 step 10) {
      download.updateProgress(i)
      AppLogger.log("Downloading ${download.title} with progress ${download.progress}...")
      updateDownloadInfo(download)
      delay(delayTime)
    }
  }
  
  private suspend fun updateDownloadInfo(download: Download) {
    withContext(Dispatchers.Main) {
      _downloadInfo.value = Event(download)
    }
  }
  
  
}